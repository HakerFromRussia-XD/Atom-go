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
}
