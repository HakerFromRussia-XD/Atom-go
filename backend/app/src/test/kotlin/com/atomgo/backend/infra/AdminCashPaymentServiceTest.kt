package com.atomgo.backend.infra

import com.atomgo.backend.domain.LedgerType
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminCashPaymentServiceTest {

    @Test
    fun `active client cash payment records rental-scoped payment and recalculates debt`() {
        val today = LocalDate.now()
        val store = InMemoryStore.seed()
        store.ledger.clear()
        store.bikes[0] = store.bikes[0].copy(weeklyRateRub = 3500)
        store.clientRentals[0] = store.clientRentals[0].copy(startDate = today, endDate = null)
        val service = AdminCashPaymentService(store)

        val result = service.recordForActiveClient(
            clientId = "client-001",
            adminId = "admin-001",
            amountRub = 1000,
            comment = "paid at office",
            today = today,
            createdAt = Instant.parse("2026-05-24T10:00:00Z")
        )

        assertNotNull(result)
        assertEquals("client-001", result.clientId)
        assertEquals(2500, result.debtRub)
        assertEquals(1000, result.totalPaidRub)
        assertEquals(0, result.totalAdjustmentRub)

        val entry = store.ledger.single()
        assertEquals(LedgerType.PAYMENT, entry.type)
        assertEquals(-1, entry.direction)
        assertEquals(1000, entry.amountRub)
        assertEquals("client-rental-rental-001", entry.rentalId)
        assertEquals("cash: paid at office", entry.note)
        assertTrue(AdminCashPaymentService.isCashLedgerEntry(entry))
    }

    @Test
    fun `client cash payment without active rental does not create ledger entry`() {
        val today = LocalDate.now()
        val store = InMemoryStore.seed()
        store.ledger.clear()
        store.clientRentals[0] = store.clientRentals[0].copy(endDate = today)
        val service = AdminCashPaymentService(store)

        val result = service.recordForActiveClient(
            clientId = "client-001",
            adminId = "admin-001",
            amountRub = 1000,
            comment = null,
            today = today
        )

        assertNull(result)
        assertEquals(0, store.ledger.size)
    }

    @Test
    fun `closed client rental cash payment reduces final closed-rental debt`() {
        val today = LocalDate.now()
        val store = InMemoryStore.seed()
        store.ledger.clear()
        store.bikes[0] = store.bikes[0].copy(weeklyRateRub = 3500)
        store.clientRentals[0] = store.clientRentals[0].copy(
            startDate = today.minusDays(9),
            endDate = today
        )
        val service = AdminCashPaymentService(store)

        val result = service.recordForClientRental(
            clientRentalId = "client-rental-rental-001",
            adminId = "admin-001",
            amountRub = 1000,
            comment = null,
            today = today
        )

        assertNotNull(result)
        assertEquals(3500, result.debtRub)
        assertEquals(1000, result.totalPaidRub)
        assertEquals(0, result.totalAdjustmentRub)
        assertEquals("cash", store.ledger.single().note)
    }

    @Test
    fun `cash payment rejects non-positive amount without mutating ledger`() {
        val store = InMemoryStore.seed()
        store.ledger.clear()
        val service = AdminCashPaymentService(store)

        assertFailsWith<IllegalArgumentException> {
            service.recordForClientRental(
                clientRentalId = "client-rental-rental-001",
                adminId = "admin-001",
                amountRub = 0,
                comment = null
            )
        }

        assertEquals(0, store.ledger.size)
    }

    @Test
    fun `cash payment ignores another admin rental`() {
        val store = InMemoryStore.seed()
        store.ledger.clear()
        val service = AdminCashPaymentService(store)

        val result = service.recordForClientRental(
            clientRentalId = "client-rental-rental-001",
            adminId = "admin-ip-001",
            amountRub = 1000,
            comment = null
        )

        assertNull(result)
        assertEquals(0, store.ledger.size)
    }
}
