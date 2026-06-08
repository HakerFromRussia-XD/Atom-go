package com.atomgo.backend

import com.atomgo.backend.domain.LedgerCalculator
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.PricingRules
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
    fun `billing projection should add negative adjustment to full paid weeks`() {
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
        assertEquals(3500, projection.balanceRub)
        assertEquals(LocalDate.parse("2026-05-15"), projection.paidUntilDate)
        assertEquals("Оплачено еще на 7 дн.", projection.statusText)
    }

    @Test
    fun `billing projection paid until should use full weeks for active rental`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 25000, Instant.now(), rentalId = "r1"),
            LedgerEntry("p2", "c1", LedgerType.PAYMENT, -1, 3000, Instant.now(), rentalId = "r1"),
            LedgerEntry("a1", "c1", LedgerType.ADJUSTMENT, -1, 1100, Instant.now(), rentalId = "r1"),
            LedgerEntry("a2", "c1", LedgerType.ADJUSTMENT, 1, 25000, Instant.now(), rentalId = "r1"),
            LedgerEntry("a3", "c1", LedgerType.ADJUSTMENT, -1, 3000, Instant.now(), rentalId = "r1"),
            LedgerEntry("a4", "c1", LedgerType.ADJUSTMENT, -1, 22000, Instant.now(), rentalId = "r1"),
            LedgerEntry("a5", "c1", LedgerType.ADJUSTMENT, 1, 1000, Instant.now(), rentalId = "r1")
        )

        val projection = LedgerCalculator.billingProjection(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-04-02"),
            weeklyRateRub = 3000,
            entries = entries,
            asOf = LocalDate.parse("2026-06-04"),
            rentalId = "r1"
        )

        assertEquals(1900, projection.debtRub)
        assertEquals(LocalDate.parse("2026-06-04"), projection.paidUntilDate)
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

    @Test
    fun `payment day shift should recalculate unpaid debt from transition days and selected weekly boundary`() {
        data class Case(
            val label: String,
            val weeklyRateRub: Int,
            val asOf: String,
            val paymentDay: Int,
            val expectedDebtRub: Int
        )

        val cases = listOf(
            Case("3500 Wed change to Friday on 2026-05-27", 3500, "2026-05-27", 5, 1000),
            Case("3000 Wed change to Friday on 2026-05-27", 3000, "2026-05-27", 5, 860),
            Case("3500 Thu change to Friday still charges two transition days", 3500, "2026-05-28", 5, 1000),
            Case("3500 Friday boundary includes week plus two transition days", 3500, "2026-05-29", 5, 4500),
            Case("3500 Friday before Saturday boundary charges three transition days", 3500, "2026-05-29", 6, 1500),
            Case("3500 Saturday boundary includes week plus three transition days", 3500, "2026-05-30", 6, 5000),
            Case("3000 Thu change to Friday still charges two rounded transition days", 3000, "2026-05-28", 5, 860),
            Case("3000 Friday boundary includes week plus two rounded transition days", 3000, "2026-05-29", 5, 3860),
            Case("3000 Friday before Saturday boundary charges three rounded transition days", 3000, "2026-05-29", 6, 1290),
            Case("3000 Saturday boundary includes week plus three rounded transition days", 3000, "2026-05-30", 6, 4290),
            Case("3000 Friday before Sunday boundary charges four rounded transition days", 3000, "2026-05-29", 7, 1720),
            Case("3000 Friday before Monday boundary charges five rounded transition days", 3000, "2026-05-29", 1, 2150),
            Case("3000 Friday before Tuesday boundary charges six rounded transition days", 3000, "2026-05-29", 2, 2580)
        )

        cases.forEach { case ->
            val debt = LedgerCalculator.debtRub(
                clientId = "c1",
                rentalStartDate = LocalDate.parse("2026-05-27"),
                weeklyRateRub = case.weeklyRateRub,
                entries = emptyList(),
                asOf = LocalDate.parse(case.asOf),
                rentalId = "r1",
                paymentDay = case.paymentDay
            )

            assertEquals(case.expectedDebtRub, debt, case.label)
        }
    }

    @Test
    fun `payment day shift should recalculate one-week-paid debt across selected weekly ranges`() {
        assertPaymentDayDebtForRange(
            label = "3500 paid, Friday boundary day leaves two transition days as debt",
            weeklyRateRub = 3500,
            paidRub = 3500,
            paymentDay = 5,
            from = "2026-05-29",
            to = "2026-05-29",
            expectedDebtRub = 1000
        )
        assertPaymentDayDebtForRange(
            label = "3000 paid, Friday first week leaves two rounded transition days as debt",
            weeklyRateRub = 3000,
            paidRub = 3000,
            paymentDay = 5,
            from = "2026-05-29",
            to = "2026-06-04",
            expectedDebtRub = 860
        )
        assertPaymentDayDebtForRange(
            label = "3000 paid, Friday second week is week plus transition debt",
            weeklyRateRub = 3000,
            paidRub = 3000,
            paymentDay = 5,
            from = "2026-06-05",
            to = "2026-06-11",
            expectedDebtRub = 3860
        )
        assertPaymentDayDebtForRange(
            label = "3000 paid, Saturday second week is week plus three transition days",
            weeklyRateRub = 3000,
            paidRub = 3000,
            paymentDay = 6,
            from = "2026-06-06",
            to = "2026-06-12",
            expectedDebtRub = 4290
        )
        assertPaymentDayDebtForRange(
            label = "3000 paid, Saturday first week leaves three rounded transition days as debt",
            weeklyRateRub = 3000,
            paidRub = 3000,
            paymentDay = 6,
            from = "2026-05-30",
            to = "2026-06-05",
            expectedDebtRub = 1290
        )
    }

    @Test
    fun `payment day shift should show paid days before first selected boundary and debt on boundary`() {
        val paidOneWeek = paymentEntries(3000)
        val paidDaysByDate = listOf(
            "2026-05-27" to 7,
            "2026-05-28" to 6,
            "2026-05-29" to 5,
            "2026-05-30" to 4,
            "2026-05-31" to 3
        )

        paidDaysByDate.forEach { (asOf, expectedDaysLeft) ->
            val projection = LedgerCalculator.billingProjection(
                clientId = "c1",
                rentalStartDate = LocalDate.parse("2026-05-27"),
                weeklyRateRub = 3000,
                entries = paidOneWeek,
                asOf = LocalDate.parse(asOf),
                rentalId = "r1",
                paymentDay = 1
            )

            assertEquals(0, projection.debtRub, asOf)
            assertEquals("Оплачено еще на $expectedDaysLeft дн.", projection.statusText, asOf)
        }

        val debtOnMonday = LedgerCalculator.billingProjection(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-27"),
            weeklyRateRub = 3000,
            entries = paidOneWeek,
            asOf = LocalDate.parse("2026-06-01"),
            rentalId = "r1",
            paymentDay = 1
        )
        assertEquals(2150, debtOnMonday.debtRub)

        val paidOnMonday = LedgerCalculator.billingProjection(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-27"),
            weeklyRateRub = 3000,
            entries = paymentEntries(6000),
            asOf = LocalDate.parse("2026-06-01"),
            rentalId = "r1",
            paymentDay = 1
        )
        assertEquals(0, paidOnMonday.debtRub)
        assertEquals(LocalDate.parse("2026-06-08"), paidOnMonday.paidUntilDate)
        assertEquals("Оплачено еще на 7 дн.", paidOnMonday.statusText)
    }

    @Test
    fun `payment day shift should keep transition debt after two paid weeks`() {
        assertPaymentDayDebtForRange(
            label = "3500 paid two weeks, Friday second week leaves transition debt",
            weeklyRateRub = 3500,
            paidRub = 7000,
            paymentDay = 5,
            from = "2026-06-05",
            to = "2026-06-11",
            expectedDebtRub = 1000
        )
        assertPaymentDayDebtForRange(
            label = "3500 paid two weeks, Friday third week is week plus transition debt",
            weeklyRateRub = 3500,
            paidRub = 7000,
            paymentDay = 5,
            from = "2026-06-12",
            to = "2026-06-18",
            expectedDebtRub = 4500
        )
        assertPaymentDayDebtForRange(
            label = "3000 paid two weeks, Friday second week leaves rounded transition debt",
            weeklyRateRub = 3000,
            paidRub = 6000,
            paymentDay = 5,
            from = "2026-06-05",
            to = "2026-06-11",
            expectedDebtRub = 860
        )
        assertPaymentDayDebtForRange(
            label = "3000 paid two weeks, Friday third week is week plus rounded transition debt",
            weeklyRateRub = 3000,
            paidRub = 6000,
            paymentDay = 5,
            from = "2026-06-12",
            to = "2026-06-18",
            expectedDebtRub = 3860
        )
    }

    @Test
    fun `payment day equal to start weekday should keep old weekly schedule`() {
        val shifted = LedgerCalculator.debtRub(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-27"),
            weeklyRateRub = 3500,
            entries = emptyList(),
            asOf = LocalDate.parse("2026-06-03"),
            rentalId = "r1",
            paymentDay = 3
        )
        val old = LedgerCalculator.debtRub(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-27"),
            weeklyRateRub = 3500,
            entries = emptyList(),
            asOf = LocalDate.parse("2026-06-03"),
            rentalId = "r1"
        )

        assertEquals(old, shifted)
        assertEquals(7000, shifted)
    }

    @Test
    fun `soon return after payment day shift should count paid days and rounded overdue debt`() {
        assertSoonReturnProjectionByDate(
            label = "3500 paid one week, Friday payment day, soon return",
            weeklyRateRub = 3500,
            paidRub = 3500,
            cases = listOf(
                ProjectionCase("2026-05-29", expectedDebtRub = 0, expectedPaidDaysLeft = 5),
                ProjectionCase("2026-05-30", expectedDebtRub = 0, expectedPaidDaysLeft = 4),
                ProjectionCase("2026-05-31", expectedDebtRub = 0, expectedPaidDaysLeft = 3),
                ProjectionCase("2026-06-01", expectedDebtRub = 0, expectedPaidDaysLeft = 2),
                ProjectionCase("2026-06-02", expectedDebtRub = 0, expectedPaidDaysLeft = 1),
                ProjectionCase("2026-06-03", expectedDebtRub = 0, expectedPaidDaysLeft = 0),
                ProjectionCase("2026-06-04", expectedDebtRub = 500, expectedPaidDaysLeft = 0),
                ProjectionCase("2026-06-05", expectedDebtRub = 1000, expectedPaidDaysLeft = 0)
            )
        )

        assertSoonReturnProjectionByDate(
            label = "3000 paid one week, Friday payment day, soon return",
            weeklyRateRub = 3000,
            paidRub = 3000,
            cases = listOf(
                ProjectionCase("2026-05-29", expectedDebtRub = 0, expectedPaidDaysLeft = 5),
                ProjectionCase("2026-05-30", expectedDebtRub = 0, expectedPaidDaysLeft = 4),
                ProjectionCase("2026-05-31", expectedDebtRub = 0, expectedPaidDaysLeft = 3),
                ProjectionCase("2026-06-01", expectedDebtRub = 0, expectedPaidDaysLeft = 2),
                ProjectionCase("2026-06-02", expectedDebtRub = 0, expectedPaidDaysLeft = 1),
                ProjectionCase("2026-06-03", expectedDebtRub = 0, expectedPaidDaysLeft = 0),
                ProjectionCase("2026-06-04", expectedDebtRub = 430, expectedPaidDaysLeft = 0),
                ProjectionCase("2026-06-05", expectedDebtRub = 860, expectedPaidDaysLeft = 0)
            )
        )
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
    fun `finalDebtOnClosure should not lose reducing adjustment when closing by exact days`() {
        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 11_440, Instant.now(), rentalId = "r1"),
            LedgerEntry("a1", "c1", LedgerType.ADJUSTMENT, -1, 2_560, Instant.now(), rentalId = "r1")
        )
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-04-28"),
            rentalEndDate = LocalDate.parse("2026-05-29"),
            weeklyRateRub = 3500,
            entries = entries,
            rentalId = "r1"
        )
        assertEquals(1500, finalDebt)
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
     * Недельная ставка 3000 → общий dayAmount=430
     * (3000 / 7 с округлением до десятков вверх). used=10 дней, paid=0
     * → gross = 10 * 430 = 4300.
     */
    @Test
    fun `finalDebtOnClosure should use common rounded day amount for non-divisible weekly rate`() {
        val finalDebt = LedgerCalculator.finalDebtOnClosure(
            clientId = "c1",
            rentalStartDate = LocalDate.parse("2026-05-01"),
            rentalEndDate = LocalDate.parse("2026-05-11"),
            weeklyRateRub = 3000,
            entries = emptyList()
        )
        assertEquals(4300, finalDebt)
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

    private fun assertPaymentDayDebtForRange(
        label: String,
        weeklyRateRub: Int,
        paidRub: Int,
        paymentDay: Int,
        from: String,
        to: String,
        expectedDebtRub: Int
    ) {
        val start = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        var current = start
        while (!current.isAfter(end)) {
            val debt = LedgerCalculator.debtRub(
                clientId = "c1",
                rentalStartDate = LocalDate.parse("2026-05-27"),
                weeklyRateRub = weeklyRateRub,
                entries = paymentEntries(paidRub),
                asOf = current,
                rentalId = "r1",
                paymentDay = paymentDay
            )

            assertEquals(expectedDebtRub, debt, "$label at $current")
            current = current.plusDays(1)
        }
    }

    private data class ProjectionCase(
        val asOf: String,
        val expectedDebtRub: Int,
        val expectedPaidDaysLeft: Int
    )

    private fun assertSoonReturnProjectionByDate(
        label: String,
        weeklyRateRub: Int,
        paidRub: Int,
        cases: List<ProjectionCase>
    ) {
        cases.forEach { case ->
            val projection = LedgerCalculator.finalProjectionOnClosure(
                clientId = "c1",
                rentalStartDate = LocalDate.parse("2026-05-27"),
                rentalEndDate = LocalDate.parse(case.asOf),
                weeklyRateRub = weeklyRateRub,
                entries = paymentEntries(paidRub),
                rentalId = "r1"
            )

            assertEquals(case.expectedDebtRub, projection.debtRub, "$label debt at ${case.asOf}")
            assertEquals(LocalDate.parse("2026-06-03"), projection.paidUntilDate, "$label paid until at ${case.asOf}")
            if (case.expectedDebtRub == 0) {
                assertEquals(
                    "Оплачено еще на ${case.expectedPaidDaysLeft} дн.",
                    projection.statusText,
                    "$label status at ${case.asOf}"
                )
            } else {
                val dayAmount = PricingRules.dayAmount(weeklyRateRub)
                val expectedDebtDays = ((case.expectedDebtRub + dayAmount / 2) / dayAmount).coerceAtLeast(0)
                assertEquals(
                    "Долг за $expectedDebtDays дн.",
                    projection.statusText,
                    "$label status at ${case.asOf}"
                )
            }
        }
    }

    private fun paymentEntries(amountRub: Int): List<LedgerEntry> {
        if (amountRub <= 0) return emptyList()
        return listOf(
            LedgerEntry(
                id = "p-$amountRub",
                clientId = "c1",
                type = LedgerType.PAYMENT,
                direction = -1,
                amountRub = amountRub,
                createdAt = Instant.now(),
                rentalId = "r1"
            )
        )
    }
}
