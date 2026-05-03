package com.atomgo.backend.infra

import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
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

class PaymentService(private val store: InMemoryStore) {

    private data class BillingTerms(
        val rentalStartDate: LocalDate,
        val weeklyRateRub: Int
    )

    fun createPayment(clientId: String, paymentType: PaymentType, now: LocalDate = LocalDate.now()): PaymentRecord {
        store.clients.firstOrNull { it.id == clientId }
            ?: throw IllegalArgumentException("Client not found")

        val terms = resolveBillingTerms(clientId = clientId, asOf = now)

        val debt = LedgerCalculator.debtRub(
            clientId = clientId,
            rentalStartDate = terms.rentalStartDate,
            weeklyRateRub = terms.weeklyRateRub,
            entries = store.ledger,
            asOf = now
        )
        val amount = PricingRules.amountForType(paymentType, terms.weeklyRateRub, debt)

        if (amount <= 0) {
            throw IllegalStateException("Amount is zero. Nothing to pay.")
        }

        val paymentId = UUID.randomUUID().toString()
        val idempotenceKey = UUID.randomUUID().toString()
        val confirmationUrl = "https://yookassa.ru/pay/$paymentId"

        val payment = PaymentRecord(
            id = paymentId,
            clientId = clientId,
            paymentType = paymentType,
            amountRub = amount,
            confirmationUrl = confirmationUrl,
            idempotenceKey = idempotenceKey,
            status = PaymentStatus.NEW
        )

        store.payments += payment
        return payment
    }

    fun applyWebhook(
        event: String,
        providerPaymentId: String,
        localPaymentId: String?,
        now: LocalDate = LocalDate.now()
    ): WebhookResult {
        val eventFingerprint = "$event:$providerPaymentId"
        if (!store.processedWebhookEvents.add(eventFingerprint)) {
            return WebhookResult(
                applied = false,
                message = "Duplicate webhook ignored"
            )
        }

        if (localPaymentId == null) {
            return WebhookResult(applied = false, message = "No local_payment_id in metadata")
        }

        val payment = store.payments.firstOrNull { it.id == localPaymentId }
            ?: return WebhookResult(applied = false, message = "Payment not found")

        payment.providerPaymentId = providerPaymentId

        if (event == "payment.succeeded") {
            if (payment.status == PaymentStatus.SUCCEEDED) {
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
                note = "YooKassa payment succeeded"
            )
            return buildWebhookResponse(payment, now, applied = true, "Payment applied")
        }

        if (event == "payment.canceled") {
            payment.status = PaymentStatus.CANCELED
            return buildWebhookResponse(payment, now, applied = true, "Payment canceled")
        }

        return WebhookResult(applied = false, message = "Unsupported event: $event")
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
            asOf = now
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
                rental.startDate <= asOf && (rental.endDate == null || !rental.endDate.isBefore(asOf))
            }
            ?: clientRentals.first()

        val bike = store.bikes.firstOrNull { it.id == activeRental.bikeId }
            ?: throw IllegalStateException("Bike not found for active rental")

        return BillingTerms(
            rentalStartDate = activeRental.startDate,
            weeklyRateRub = bike.weeklyRateRub
        )
    }
}
