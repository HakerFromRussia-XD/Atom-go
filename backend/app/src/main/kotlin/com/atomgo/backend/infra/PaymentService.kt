package com.atomgo.backend.infra

import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.AdminTaxMode
import com.atomgo.backend.domain.FiscalizationStatus
import com.atomgo.backend.domain.PaymentRecord
import com.atomgo.backend.domain.PaymentStatus
import com.atomgo.backend.domain.PaymentType
import com.atomgo.backend.domain.PricingRules
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class WebhookResult(
    val applied: Boolean,
    val message: String,
    val paymentId: String? = null,
    val clientId: String? = null,
    val debtRub: Int? = null
)

data class PaymentStatusResult(
    val payment: PaymentRecord,
    val applied: Boolean,
    val message: String,
    val debtRub: Int? = null
)

data class FiscalizationConfig(
    val taxMode: AdminTaxMode,
    val yooKassaTaxSystemCode: Int = 1,
    val yooKassaVatCode: Int = 1,
    val yooKassaPaymentMode: String = "full_payment",
    val yooKassaPaymentSubject: String = "service",
    val defaultCustomerEmail: String? = null
) {
    companion object {
        fun fromEnvironment(): FiscalizationConfig {
            val taxMode = when (System.getenv("ATOMGO_ADMIN_TAX_MODE")?.trim()?.lowercase()) {
                "ip", "individual_entrepreneur", "individual-entrepreneur" -> AdminTaxMode.INDIVIDUAL_ENTREPRENEUR
                else -> AdminTaxMode.SELF_EMPLOYED
            }
            return FiscalizationConfig(
                taxMode = taxMode,
                yooKassaTaxSystemCode = System.getenv("YOOKASSA_RECEIPT_TAX_SYSTEM_CODE")?.toIntOrNull() ?: 1,
                yooKassaVatCode = System.getenv("YOOKASSA_RECEIPT_VAT_CODE")?.toIntOrNull() ?: 1,
                yooKassaPaymentMode = System.getenv("YOOKASSA_RECEIPT_PAYMENT_MODE")?.trim()?.ifBlank { null } ?: "full_payment",
                yooKassaPaymentSubject = System.getenv("YOOKASSA_RECEIPT_PAYMENT_SUBJECT")?.trim()?.ifBlank { null } ?: "service",
                defaultCustomerEmail = System.getenv("YOOKASSA_RECEIPT_CUSTOMER_EMAIL")?.trim()?.ifBlank { null }
            )
        }
    }
}

class PaymentService(
    private val store: InMemoryStore,
    private val provider: PaymentProvider = MockYooKassaPaymentProvider(),
    private val fiscalizationConfig: FiscalizationConfig = FiscalizationConfig.fromEnvironment()
) {

    private data class BillingTerms(
        val rentalId: String,
        val rentalStartDate: LocalDate,
        val weeklyRateRub: Int,
        val clientName: String,
        val bikeModel: String,
        val taxMode: AdminTaxMode
    )

    fun createPayment(clientId: String, paymentType: PaymentType, now: LocalDate = LocalDate.now()): PaymentRecord {
        val client = store.clients.firstOrNull { it.id == clientId }
            ?: throw IllegalArgumentException("Client not found")

        val terms = resolveBillingTerms(clientId = clientId, asOf = now)

        val debt = LedgerCalculator.debtRub(
            clientId = clientId,
            rentalStartDate = terms.rentalStartDate,
            weeklyRateRub = terms.weeklyRateRub,
            entries = store.ledger,
            asOf = now,
            rentalId = terms.rentalId
        )
        val amount = PricingRules.amountForType(paymentType, terms.weeklyRateRub, debt)

        if (amount <= 0) {
            throw IllegalStateException("Amount is zero. Nothing to pay.")
        }

        val paymentId = UUID.randomUUID().toString()
        val idempotenceKey = UUID.randomUUID().toString()
        val description = "Atom Go: ${client.fullName}, ${terms.bikeModel}, ${PaymentType.toApi(paymentType)}"
        val receipt = buildProviderReceipt(terms.taxMode, client.phones.map { it.number })
        val providerPayment = provider.createPayment(
            ProviderCreatePaymentRequest(
                localPaymentId = paymentId,
                clientId = clientId,
                rentalId = terms.rentalId,
                paymentType = paymentType,
                amountRub = amount,
                idempotenceKey = idempotenceKey,
                description = description,
                receipt = receipt
            )
        )

        validateProviderPayment(paymentId, terms.rentalId, amount, providerPayment)

        val payment = PaymentRecord(
            id = paymentId,
            clientId = clientId,
            paymentType = paymentType,
            amountRub = amount,
            confirmationUrl = providerPayment.confirmationUrl.orEmpty(),
            idempotenceKey = idempotenceKey,
            status = providerPayment.status,
            providerPaymentId = providerPayment.providerPaymentId,
            rentalId = terms.rentalId,
            taxMode = terms.taxMode,
            fiscalizationStatus = fiscalizationStatusForCreatedPayment(
                taxMode = terms.taxMode,
                receipt = receipt,
                providerReceiptRegistration = providerPayment.receiptRegistration
            )
        )

        store.payments += payment
        return payment
    }

    private fun buildProviderReceipt(taxMode: AdminTaxMode, rawCustomerContacts: List<String>): ProviderReceipt? {
        if (taxMode != AdminTaxMode.INDIVIDUAL_ENTREPRENEUR) return null

        val customerEmail = rawCustomerContacts.firstNotNullOfOrNull(::normalizeReceiptEmail)
            ?: fiscalizationConfig.defaultCustomerEmail
            ?: throw IllegalStateException("Client email is required for YooKassa receipt")

        return ProviderReceipt(
            customerEmail = customerEmail,
            taxSystemCode = fiscalizationConfig.yooKassaTaxSystemCode,
            vatCode = fiscalizationConfig.yooKassaVatCode,
            paymentMode = fiscalizationConfig.yooKassaPaymentMode,
            paymentSubject = fiscalizationConfig.yooKassaPaymentSubject
        )
    }

    private fun fiscalizationStatusForCreatedPayment(
        taxMode: AdminTaxMode,
        receipt: ProviderReceipt?,
        providerReceiptRegistration: String?
    ): FiscalizationStatus = when {
        taxMode == AdminTaxMode.SELF_EMPLOYED -> FiscalizationStatus.NPD_RECEIPT_PENDING
        receipt == null -> FiscalizationStatus.FISCALIZATION_NOT_CONFIGURED
        providerReceiptRegistration.isNullOrBlank() -> FiscalizationStatus.FISCALIZATION_NOT_CONFIGURED
        else -> FiscalizationStatus.YOOKASSA_RECEIPT_PENDING
    }

    fun applyWebhook(
        event: String,
        providerPaymentId: String,
        localPaymentId: String?,
        providerStatusFromWebhook: PaymentStatus?,
        amountRubFromWebhook: Int?,
        now: LocalDate = LocalDate.now()
    ): WebhookResult {
        if (event != "payment.succeeded" && event != "payment.canceled") {
            return WebhookResult(applied = false, message = "Unsupported event: $event")
        }

        val eventFingerprint = "$event:$providerPaymentId"
        if (store.processedWebhookEvents.contains(eventFingerprint)) {
            return WebhookResult(applied = false, message = "Duplicate webhook ignored")
        }

        val payment = findPayment(localPaymentId = localPaymentId, providerPaymentId = providerPaymentId)
            ?: return WebhookResult(applied = false, message = "Payment not found")

        payment.providerPaymentId = providerPaymentId

        val providerInfo = provider.fetchPayment(providerPaymentId) ?: ProviderPaymentInfo(
            providerPaymentId = providerPaymentId,
            status = providerStatusFromWebhook ?: statusFromEvent(event),
            amountRub = amountRubFromWebhook,
            confirmationUrl = payment.confirmationUrl,
            localPaymentId = localPaymentId,
            clientId = payment.clientId,
            rentalId = payment.rentalId,
            paymentType = PaymentType.toApi(payment.paymentType)
        )

        val validationError = validateProviderPaymentOrMessage(payment, providerInfo)
        if (validationError != null) {
            return WebhookResult(applied = false, message = validationError, paymentId = payment.id, clientId = payment.clientId)
        }

        if (event == "payment.succeeded" && providerInfo.status != PaymentStatus.SUCCEEDED) {
            return buildWebhookResponse(payment, now, applied = false, "Provider payment is not succeeded")
        }

        val result = applyProviderStatus(payment, providerInfo.status, now)
        store.processedWebhookEvents += eventFingerprint
        return result
    }

    fun refreshPaymentStatus(paymentId: String, now: LocalDate = LocalDate.now()): PaymentStatusResult {
        val payment = store.payments.firstOrNull { it.id == paymentId }
            ?: throw IllegalArgumentException("Payment not found")

        val providerPaymentId = payment.providerPaymentId
        if (providerPaymentId.isNullOrBlank()) {
            return PaymentStatusResult(payment = payment, applied = false, message = "No provider payment id")
        }

        val providerInfo = provider.fetchPayment(providerPaymentId)
            ?: return PaymentStatusResult(payment = payment, applied = false, message = "Provider payment status unavailable")

        val validationError = validateProviderPaymentOrMessage(payment, providerInfo)
        if (validationError != null) {
            return PaymentStatusResult(payment = payment, applied = false, message = validationError)
        }

        val beforeStatus = payment.status
        val webhookResult = applyProviderStatus(payment, providerInfo.status, now)
        return PaymentStatusResult(
            payment = payment,
            applied = webhookResult.applied || beforeStatus != payment.status,
            message = webhookResult.message,
            debtRub = webhookResult.debtRub
        )
    }

    private fun applyProviderStatus(payment: PaymentRecord, status: PaymentStatus, now: LocalDate): WebhookResult {
        if (status == PaymentStatus.SUCCEEDED) {
            if (payment.status == PaymentStatus.SUCCEEDED || store.ledger.any { it.sourceId == payment.id }) {
                payment.status = PaymentStatus.SUCCEEDED
                return buildWebhookResponse(payment, now, applied = false, "Payment already applied")
            }

            payment.status = PaymentStatus.SUCCEEDED
            store.ledger += LedgerEntry(
                id = "ledger-${UUID.randomUUID()}",
                clientId = payment.clientId,
                type = LedgerType.PAYMENT,
                direction = -1,
                amountRub = payment.amountRub,
                createdAt = Instant.now(),
                sourceId = payment.id,
                note = "YooKassa payment succeeded",
                rentalId = payment.rentalId
            )
            return buildWebhookResponse(payment, now, applied = true, "Payment applied")
        }

        if (status == PaymentStatus.CANCELED || status == PaymentStatus.FAILED) {
            payment.status = status
            return buildWebhookResponse(payment, now, applied = true, "Payment ${status.name.lowercase()}")
        }

        payment.status = status
        return buildWebhookResponse(payment, now, applied = false, "Payment is ${status.name.lowercase()}")
    }

    private fun findPayment(localPaymentId: String?, providerPaymentId: String?): PaymentRecord? {
        if (!localPaymentId.isNullOrBlank()) {
            store.payments.firstOrNull { it.id == localPaymentId }?.let { return it }
        }
        if (!providerPaymentId.isNullOrBlank()) {
            store.payments.firstOrNull { it.providerPaymentId == providerPaymentId }?.let { return it }
        }
        return null
    }

    private fun validateProviderPayment(paymentId: String, rentalId: String, amountRub: Int, providerPayment: ProviderPaymentInfo) {
        if (providerPayment.localPaymentId != null && providerPayment.localPaymentId != paymentId) {
            throw IllegalStateException("YooKassa returned payment with wrong local_payment_id")
        }
        if (providerPayment.rentalId != null && providerPayment.rentalId != rentalId) {
            throw IllegalStateException("YooKassa returned payment with wrong rental_id")
        }
        if (providerPayment.amountRub != null && providerPayment.amountRub != amountRub) {
            throw IllegalStateException("YooKassa returned payment with wrong amount")
        }
    }

    private fun validateProviderPaymentOrMessage(payment: PaymentRecord, providerPayment: ProviderPaymentInfo): String? {
        if (providerPayment.localPaymentId != null && providerPayment.localPaymentId != payment.id) {
            return "Provider payment local_payment_id mismatch"
        }
        if (providerPayment.clientId != null && providerPayment.clientId != payment.clientId) {
            return "Provider payment client_id mismatch"
        }
        if (providerPayment.rentalId != null && providerPayment.rentalId != payment.rentalId) {
            return "Provider payment rental_id mismatch"
        }
        if (providerPayment.amountRub != null && providerPayment.amountRub != payment.amountRub) {
            return "Provider payment amount mismatch"
        }
        return null
    }

    private fun statusFromEvent(event: String): PaymentStatus = when (event) {
        "payment.succeeded" -> PaymentStatus.SUCCEEDED
        "payment.canceled" -> PaymentStatus.CANCELED
        else -> PaymentStatus.PENDING
    }

    private fun buildWebhookResponse(
        payment: PaymentRecord,
        now: LocalDate,
        applied: Boolean,
        message: String
    ): WebhookResult {
        store.clients.firstOrNull { it.id == payment.clientId }
            ?: return WebhookResult(applied = applied, message = message, paymentId = payment.id)

        val terms = try {
            resolveBillingTerms(clientId = payment.clientId, asOf = now)
        } catch (_: Throwable) {
            return WebhookResult(applied = applied, message = message, paymentId = payment.id, clientId = payment.clientId)
        }

        val debt = LedgerCalculator.debtRub(
            clientId = payment.clientId,
            rentalStartDate = terms.rentalStartDate,
            weeklyRateRub = terms.weeklyRateRub,
            entries = store.ledger,
            asOf = now,
            rentalId = terms.rentalId
        )
        return WebhookResult(
            applied = applied,
            message = message,
            paymentId = payment.id,
            clientId = payment.clientId,
            debtRub = debt
        )
    }

    private fun resolveBillingTerms(clientId: String, asOf: LocalDate): BillingTerms {
        val client = store.clients.firstOrNull { it.id == clientId }
            ?: throw IllegalStateException("Client not found")
        val clientRentals = store.rentals
            .asSequence()
            .filter { it.clientId == clientId }
            .sortedByDescending { it.startDate }
            .toList()
        if (clientRentals.isEmpty()) {
            throw IllegalStateException("Client has no rentals")
        }

        val activeRental = clientRentals
            .firstOrNull { rental ->
                rental.startDate <= asOf && (rental.endDate == null || rental.endDate.isAfter(asOf))
            }
            ?: clientRentals.first()

        val bike = store.bikes.firstOrNull { it.id == activeRental.bikeId }
            ?: throw IllegalStateException("Bike not found for active rental")

        return BillingTerms(
            rentalId = activeRental.id,
            rentalStartDate = activeRental.startDate,
            weeklyRateRub = bike.weeklyRateRub,
            clientName = client.fullName,
            bikeModel = bike.model,
            taxMode = activeRental.taxMode
        )
    }
}

    internal fun normalizeReceiptEmail(rawEmail: String?): String? {
        val email = rawEmail?.trim()?.lowercase().orEmpty()
        if (email.isBlank() || email.length > 254 || !email.contains("@")) return null
    val parts = email.split("@")
    if (parts.size != 2 || parts.any { it.isBlank() }) return null
    if (!parts[1].contains(".")) return null
    return email
}
