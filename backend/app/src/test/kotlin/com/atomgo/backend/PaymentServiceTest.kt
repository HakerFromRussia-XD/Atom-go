package com.atomgo.backend

import com.atomgo.backend.domain.PaymentStatus
import com.atomgo.backend.domain.PaymentType
import com.atomgo.backend.domain.AdminTaxMode
import com.atomgo.backend.domain.ClientPhone
import com.atomgo.backend.domain.FiscalizationStatus
import com.atomgo.backend.infra.FiscalizationConfig
import com.atomgo.backend.infra.InMemoryStore
import com.atomgo.backend.infra.PaymentProvider
import com.atomgo.backend.infra.PaymentService
import com.atomgo.backend.infra.ProviderCreatePaymentRequest
import com.atomgo.backend.infra.ProviderPaymentInfo
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PaymentServiceTest {

    @Test
    fun `create payment should bind payment to active rental`() {
        val provider = FakePaymentProvider()
        val store = InMemoryStore.seed()
        val service = PaymentService(store, provider)

        val payment = service.createPayment("client-001", PaymentType.WEEK, now = LocalDate.now())

        assertEquals(PaymentStatus.PENDING, payment.status)
        assertEquals("rental-001", payment.rentalId)
        assertTrue(payment.confirmationUrl.contains(payment.id))
        assertNotNull(payment.providerPaymentId)
        assertEquals(AdminTaxMode.SELF_EMPLOYED, payment.taxMode)
        assertEquals(FiscalizationStatus.NPD_RECEIPT_PENDING, payment.fiscalizationStatus)
        assertEquals(null, provider.lastRequest?.receipt)
        assertEquals(AdminTaxMode.SELF_EMPLOYED, provider.lastTaxMode)
    }

    @Test
    fun `individual entrepreneur payment should pass receipt to provider`() {
        val provider = FakePaymentProvider()
        val store = InMemoryStore.seed()
        store.rentals[0] = store.rentals[0].copy(taxMode = AdminTaxMode.INDIVIDUAL_ENTREPRENEUR)
        store.clients[0].phones += ClientPhone(label = "Email", number = "ip-client@example.com")
        val service = PaymentService(
            store,
            provider,
            FiscalizationConfig(
                taxMode = AdminTaxMode.INDIVIDUAL_ENTREPRENEUR,
                yooKassaTaxSystemCode = 1,
                yooKassaVatCode = 1,
                yooKassaPaymentMode = "full_payment",
                yooKassaPaymentSubject = "service"
            )
        )

        val payment = service.createPayment("client-001", PaymentType.WEEK, now = LocalDate.now())

        val receipt = provider.lastRequest?.receipt
        assertNotNull(receipt)
        assertEquals("ip-client@example.com", receipt.customerEmail)
        assertEquals(1, receipt.taxSystemCode)
        assertEquals(1, receipt.vatCode)
        assertEquals("full_payment", receipt.paymentMode)
        assertEquals("service", receipt.paymentSubject)
        assertEquals(AdminTaxMode.INDIVIDUAL_ENTREPRENEUR, payment.taxMode)
        assertEquals(FiscalizationStatus.YOOKASSA_RECEIPT_PENDING, payment.fiscalizationStatus)
        assertEquals(AdminTaxMode.INDIVIDUAL_ENTREPRENEUR, provider.lastTaxMode)
    }

    @Test
    fun `individual entrepreneur payment should require receipt email`() {
        val provider = FakePaymentProvider()
        val store = InMemoryStore.seed()
        store.rentals[0] = store.rentals[0].copy(taxMode = AdminTaxMode.INDIVIDUAL_ENTREPRENEUR)
        val service = PaymentService(store, provider)

        val error = kotlin.runCatching {
            service.createPayment("client-001", PaymentType.WEEK, now = LocalDate.now())
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("Client email is required for YooKassa receipt", error.message)
    }

    @Test
    fun `individual entrepreneur payment should mark fiscalization not configured when provider omits receipt registration`() {
        val provider = FakePaymentProvider(receiptRegistration = null)
        val store = InMemoryStore.seed()
        store.rentals[0] = store.rentals[0].copy(taxMode = AdminTaxMode.INDIVIDUAL_ENTREPRENEUR)
        store.clients[0].phones += ClientPhone(label = "Email", number = "ip-client@example.com")
        val service = PaymentService(store, provider)

        val payment = service.createPayment("client-001", PaymentType.DAY, now = LocalDate.now())

        assertEquals(FiscalizationStatus.FISCALIZATION_NOT_CONFIGURED, payment.fiscalizationStatus)
    }

    @Test
    fun `individual entrepreneur payment should reuse saved receipt email across rentals`() {
        val provider = FakePaymentProvider()
        val store = InMemoryStore.seed()
        store.rentals[0] = store.rentals[0].copy(taxMode = AdminTaxMode.INDIVIDUAL_ENTREPRENEUR)
        store.clients[0].phones += ClientPhone(label = "Email", number = "13romaroma13@gmail.com")
        val service = PaymentService(store, provider)

        service.createPayment("client-001", PaymentType.DAY, now = LocalDate.now())
        val firstReceipt = provider.lastRequest?.receipt
        assertNotNull(firstReceipt)
        assertEquals("13romaroma13@gmail.com", firstReceipt.customerEmail)

        store.rentals[0] = store.rentals[0].copy(endDate = LocalDate.now().minusDays(1))
        store.rentals += store.rentals[0].copy(
            id = "rental-ip-new",
            startDate = LocalDate.now(),
            endDate = null,
            taxMode = AdminTaxMode.INDIVIDUAL_ENTREPRENEUR
        )

        service.createPayment("client-001", PaymentType.DAY, now = LocalDate.now())
        val secondReceipt = provider.lastRequest?.receipt
        assertNotNull(secondReceipt)
        assertEquals("13romaroma13@gmail.com", secondReceipt.customerEmail)
        assertTrue(store.clients[0].phones.any { it.number == "13romaroma13@gmail.com" })
    }

    @Test
    fun `success webhook should create one rental-scoped ledger entry`() {
        val provider = FakePaymentProvider()
        val store = InMemoryStore.seed()
        val service = PaymentService(store, provider)
        val payment = service.createPayment("client-001", PaymentType.DAY, now = LocalDate.now())
        provider.setStatus(payment, PaymentStatus.SUCCEEDED)

        val first = service.applyWebhook(
            event = "payment.succeeded",
            providerPaymentId = payment.providerPaymentId!!,
            localPaymentId = payment.id,
            providerStatusFromWebhook = PaymentStatus.SUCCEEDED,
            amountRubFromWebhook = payment.amountRub
        )
        val second = service.applyWebhook(
            event = "payment.succeeded",
            providerPaymentId = payment.providerPaymentId!!,
            localPaymentId = payment.id,
            providerStatusFromWebhook = PaymentStatus.SUCCEEDED,
            amountRubFromWebhook = payment.amountRub
        )

        assertEquals(true, first.applied)
        assertEquals(false, second.applied)
        val ledgerEntries = store.ledger.filter { it.sourceId == payment.id }
        assertEquals(1, ledgerEntries.size)
        assertEquals(payment.rentalId, ledgerEntries.single().rentalId)
    }

    @Test
    fun `canceled webhook should not create ledger entry`() {
        val provider = FakePaymentProvider()
        val store = InMemoryStore.seed()
        val service = PaymentService(store, provider)
        val payment = service.createPayment("client-001", PaymentType.WEEK, now = LocalDate.now())
        provider.setStatus(payment, PaymentStatus.CANCELED)

        val result = service.applyWebhook(
            event = "payment.canceled",
            providerPaymentId = payment.providerPaymentId!!,
            localPaymentId = payment.id,
            providerStatusFromWebhook = PaymentStatus.CANCELED,
            amountRubFromWebhook = payment.amountRub
        )

        assertEquals(true, result.applied)
        assertEquals(PaymentStatus.CANCELED, payment.status)
        assertTrue(store.ledger.none { it.sourceId == payment.id })
    }

    @Test
    fun `webhook with mismatched amount should not apply money`() {
        val provider = FakePaymentProvider()
        val store = InMemoryStore.seed()
        val service = PaymentService(store, provider)
        val payment = service.createPayment("client-001", PaymentType.WEEK, now = LocalDate.now())
        provider.setStatus(payment, PaymentStatus.SUCCEEDED, amountRub = payment.amountRub + 10)

        val result = service.applyWebhook(
            event = "payment.succeeded",
            providerPaymentId = payment.providerPaymentId!!,
            localPaymentId = payment.id,
            providerStatusFromWebhook = PaymentStatus.SUCCEEDED,
            amountRubFromWebhook = payment.amountRub + 10
        )

        assertEquals(false, result.applied)
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertTrue(store.ledger.none { it.sourceId == payment.id })
    }

    @Test
    fun `status polling should apply success when webhook did not arrive`() {
        val provider = FakePaymentProvider()
        val store = InMemoryStore.seed()
        val service = PaymentService(store, provider)
        val payment = service.createPayment("client-001", PaymentType.WEEK, now = LocalDate.now())
        provider.setStatus(payment, PaymentStatus.SUCCEEDED)

        val result = service.refreshPaymentStatus(payment.id)

        assertEquals(true, result.applied)
        assertEquals(PaymentStatus.SUCCEEDED, result.payment.status)
        assertEquals(1, store.ledger.count { it.sourceId == payment.id })
    }
}

private class FakePaymentProvider(
    private val receiptRegistration: String? = "pending"
) : PaymentProvider {
    private val payments = mutableMapOf<String, ProviderPaymentInfo>()
    var lastRequest: ProviderCreatePaymentRequest? = null
    var lastTaxMode: AdminTaxMode? = null

    override fun createPayment(request: ProviderCreatePaymentRequest, taxMode: AdminTaxMode): ProviderPaymentInfo {
        lastRequest = request
        lastTaxMode = taxMode
        val providerId = "provider-${request.localPaymentId}"
        val info = ProviderPaymentInfo(
            providerPaymentId = providerId,
            status = PaymentStatus.PENDING,
            amountRub = request.amountRub,
            confirmationUrl = "https://example.test/pay/${request.localPaymentId}",
            localPaymentId = request.localPaymentId,
            clientId = request.clientId,
            rentalId = request.rentalId,
            paymentType = PaymentType.toApi(request.paymentType),
            receiptRegistration = receiptRegistration
        )
        payments[providerId] = info
        return info
    }

    override fun fetchPayment(providerPaymentId: String, taxMode: AdminTaxMode): ProviderPaymentInfo? {
        return payments[providerPaymentId]
    }

    fun setStatus(payment: com.atomgo.backend.domain.PaymentRecord, status: PaymentStatus, amountRub: Int = payment.amountRub) {
        val providerId = payment.providerPaymentId ?: error("No provider id")
        val current = payments[providerId] ?: error("No fake provider payment")
        payments[providerId] = current.copy(status = status, amountRub = amountRub)
    }
}
