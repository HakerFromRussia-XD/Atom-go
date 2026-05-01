package com.atomgo.backend

import com.atomgo.backend.domain.ClientAccount
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
        val client = ClientAccount(
            id = "c1",
            fullName = "Client",
            weeklyRateRub = 3000,
            rentalStartDate = LocalDate.parse("2026-01-01"),
            bikeModel = "Bike",
            bikeAvatarUrl = "",
            address = "Test address",
            passportData = "Test passport"
        )

        val entries = listOf(
            LedgerEntry("p1", "c1", LedgerType.PAYMENT, -1, 3000, Instant.now()),
            LedgerEntry("p2", "c1", LedgerType.PAYMENT, -1, 3000, Instant.now()),
            LedgerEntry("p3", "c1", LedgerType.PAYMENT, -1, 3000, Instant.now()),
            LedgerEntry("a1", "c1", LedgerType.ADJUSTMENT, -1, 1000, Instant.now()),
            LedgerEntry("a2", "c1", LedgerType.ADJUSTMENT, -1, 500, Instant.now())
        )

        // На 2026-01-25 прошло 24 дня, значит начислено 4 недели = 12000.
        // Эффективно заплачено: 9000 + корректировка -1500 => в итоге долг 1500.
        val debt = LedgerCalculator.debtRub(client, entries, LocalDate.parse("2026-01-25"))
        assertEquals(1500, debt)
    }
}
