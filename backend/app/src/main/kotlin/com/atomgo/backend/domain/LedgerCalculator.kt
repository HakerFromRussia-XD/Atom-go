package com.atomgo.backend.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object LedgerCalculator {

    fun dueWeeksCount(startDate: LocalDate, asOf: LocalDate): Int {
        if (asOf.isBefore(startDate)) return 0
        val days = ChronoUnit.DAYS.between(startDate, asOf).toInt()
        return (days / 7) + 1
    }

    fun chargeDueRub(startDate: LocalDate, asOf: LocalDate, weeklyRateRub: Int): Int {
        return dueWeeksCount(startDate, asOf) * weeklyRateRub
    }

    fun totalPaidRub(entries: List<LedgerEntry>, clientId: String): Int {
        return entries
            .asSequence()
            .filter { it.clientId == clientId }
            .filter { it.type == LedgerType.PAYMENT && it.direction == -1 }
            .sumOf { it.amountRub }
    }

    fun totalAdjustmentRub(entries: List<LedgerEntry>, clientId: String): Int {
        return entries
            .asSequence()
            .filter { it.clientId == clientId }
            .filter { it.type == LedgerType.ADJUSTMENT }
            .sumOf { it.amountRub * if (it.direction == -1) -1 else 1 }
    }

    fun debtRub(client: ClientAccount, entries: List<LedgerEntry>, asOf: LocalDate): Int {
        val due = chargeDueRub(client.rentalStartDate, asOf, client.weeklyRateRub)
        val paid = totalPaidRub(entries, client.id)
        val adjustment = totalAdjustmentRub(entries, client.id)
        val raw = due - paid + adjustment
        return raw.coerceAtLeast(0)
    }

    fun paidUntilDate(client: ClientAccount, entries: List<LedgerEntry>): LocalDate {
        val paid = totalPaidRub(entries, client.id)
        val adjustment = totalAdjustmentRub(entries, client.id)
        val effectivePaid = (paid - adjustment).coerceAtLeast(0)
        val coveredWeeks = effectivePaid / client.weeklyRateRub
        val coveredDays = coveredWeeks * 7L
        return client.rentalStartDate.plusDays(coveredDays)
    }
}
