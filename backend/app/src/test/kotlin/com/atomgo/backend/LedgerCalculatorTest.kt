package com.atomgo.backend

import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerCalculatorTest {

    @Test
    fun `debt should follow weekly charges with adjustments`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 3000, Instant.now()),
            LedgerEntry("p2", "c1", LedgerType.PAYMENT, -1, 3000, Instant.now()),
            LedgerEntry("p3", "c1", LedgerType.PAYMENT, -1, 3000, Instant.now()),
            LedgerEntry("a1", "c1", LedgerType.ADJUSTMENT, -1, 1000, Instant.now()),
            LedgerEntry("a2", "c1", LedgerType.ADJUSTMENT, -1, 500, Instant.now())
        )

        // На 2026-01-25 прошло 24 дня, значит начислено 4 недели = 12000.
        // Эффективно заплачено: 9000 + корректировка -1500 => в итоге долг 1500.
        val debt = LedgerCalculator.debtRub(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-01-01"),
            weeklyRateRub = 3000,
            entries = entries,
            asOf = LocalDate.parse("2026-01-25")
        )
        assertEquals(1500, debt)
    }

    @Test
    fun `billing projection should add negative adjustment to paid days`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 7000, Instant.now(), rentalId = "r1"),
            LedgerEntry("a1", "c1", LedgerType.ADJUSTMENT, -1, 1000, Instant.now(), rentalId = "r1")
        )

        val projection = LedgerCalculator.billingProjection(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-01"),
            weeklyRateRub = 3500,
            entries = entries,
            asOf = LocalDate.parse("2026-05-08"),
            rentalId = "r1"
        )

        assertEquals(0, projection.debtRub)
        assertEquals(4500, projection.balanceRub)
        assertEquals(LocalDate.parse("2026-05-17"), projection.paidUntilDate)
        assertEquals("Оплачено еще на 9 дн.", projection.statusText)
    }

    @Test
    fun `billing projection should show debt days from money amount`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 3500, Instant.now(), rentalId = "r1")
        )

        val projection = LedgerCalculator.billingProjection(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-01"),
            weeklyRateRub = 3500,
            entries = entries,
            asOf = LocalDate.parse("2026-05-08"),
            rentalId = "r1"
        )

        assertEquals(3500, projection.debtRub)
        assertEquals(0, projection.balanceRub)
        assertEquals("Долг за 7 дн.", projection.statusText)
    }

    // --- finalDebtOnClosure ---

    /**
     * Эталонный пример из docs/14_rental_lifecycle.md §3 и
     * docs/02_money_and_debt_rules.md §5.
     * Ставка 3500/нед, dailyRate=500. Старт пн, возврат через 9 дней,
     * оплатил 3500 — covered 7, used 9, overdue 2 → 1000₽ финальный долг.
     */
    @Test
    fun `finalDebtOnClosure should match spec example with two days overrun`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 3500, Instant.now(), rentalId = "r1")
        )
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-13"),
            weeklyRateRub = 3500,
            entries = entries,
            rentalId = "r1"
        )
        assertEquals(1000, finalDebt)
    }

    @Test
    fun `finalDebtOnClosure should be zero when fully covered`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 7000, Instant.now(), rentalId = "r1")
        )
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-13"),
            weeklyRateRub = 3500,
            entries = entries,
            rentalId = "r1"
        )
        assertEquals(0, finalDebt)
    }

    @Test
    fun `finalDebtOnClosure should be zero when returned same day as start`() {
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-04"),
            weeklyRateRub = 3500,
            entries = emptyList(),
            rentalId = "r1"
        )
        assertEquals(0, finalDebt)
    }

    @Test
    fun `finalDebtOnClosure should be zero with no payment and zero used days`() {
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-04"),
            weeklyRateRub = 3500,
            entries = emptyList()
        )
        assertEquals(0, finalDebt)
    }

    /**
     * Уменьшающая корректировка должна снижать финальный долг.
     * dailyRate=500, used=9, paid=3500, covered=7, overdue=2 → gross 1000.
     * Корректировка -500 (direction=-1) → net adjustment = -500.
     * final = max(0, 1000 + (-500)) = 500.
     */
    @Test
    fun `finalDebtOnClosure should apply reducing adjustment`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 3500, Instant.now(), rentalId = "r1"),
            LedgerEntry("a1", "c1", LedgerType.ADJUSTMENT, -1, 500, Instant.now(), rentalId = "r1")
        )
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-13"),
            weeklyRateRub = 3500,
            entries = entries,
            rentalId = "r1"
        )
        assertEquals(500, finalDebt)
    }

    @Test
    fun `finalDebtOnClosure should apply increasing adjustment`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 3500, Instant.now(), rentalId = "r1"),
            LedgerEntry("a1", "c1", LedgerType.ADJUSTMENT, 1, 300, Instant.now(), rentalId = "r1")
        )
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-13"),
            weeklyRateRub = 3500,
            entries = entries,
            rentalId = "r1"
        )
        assertEquals(1300, finalDebt)
    }

    @Test
    fun `finalDebtOnClosure should clamp huge reducing adjustment to zero`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 3500, Instant.now(), rentalId = "r1"),
            LedgerEntry("a1", "c1", LedgerType.ADJUSTMENT, -1, 10_000, Instant.now(), rentalId = "r1")
        )
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-13"),
            weeklyRateRub = 3500,
            entries = entries,
            rentalId = "r1"
        )
        assertEquals(0, finalDebt)
    }

    @Test
    fun `finalDebtOnClosure should ignore ledger of other clientRental`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 3500, Instant.now(), rentalId = "r1"),
            // Платёж по другой клиентской аренде — не должен учитываться.
            LedgerEntry("p2", "c1", LedgerType.PAYMENT, -1, 10_000, Instant.now(), rentalId = "rOTHER")
        )
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-13"),
            weeklyRateRub = 3500,
            entries = entries,
            rentalId = "r1"
        )
        assertEquals(1000, finalDebt)
    }

    @Test
    fun `finalDebtOnClosure should return zero for zero or negative weekly rate`() {
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-04"),
            rentalEndDate = LocalDate.parse("2026-05-13"),
            weeklyRateRub = 0,
            entries = emptyList()
        )
        assertEquals(0, finalDebt)
    }

    /**
     * Дробная ставка: weekly=3000 → daily=428.57…. used=10 дней, paid=0
     * → overdue=10, gross = round(10 * 428.57…) = 4286.
     */
    @Test
    fun `finalDebtOnClosure should round non-divisible daily rate`() {
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-01"),
            rentalEndDate = LocalDate.parse("2026-05-11"),
            weeklyRateRub = 3000,
            entries = emptyList()
        )
        assertEquals(4286, finalDebt)
    }

    @Test
    fun `finalDebtOnClosure should return zero when end is before start`() {
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-10"),
            rentalEndDate = LocalDate.parse("2026-05-04"),
            weeklyRateRub = 3500,
            entries = emptyList()
        )
        assertEquals(0, finalDebt)
    }
}
