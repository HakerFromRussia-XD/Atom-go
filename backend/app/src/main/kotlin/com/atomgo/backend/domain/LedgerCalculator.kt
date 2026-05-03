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

    fun debtRub(
        clientId: String,
        rentalStartDate: LocalDate,
        weeklyRateRub: Int,
        entries: List<LedgerEntry>,
        asOf: LocalDate
    ): Int {
        val due = chargeDueRub(rentalStartDate, asOf, weeklyRateRub)
        val paid = totalPaidRub(entries, clientId)
        val adjustment = totalAdjustmentRub(entries, clientId)
        val raw = due - paid + adjustment
        return raw.coerceAtLeast(0)
    }

    fun paidUntilDate(
        clientId: String,
        rentalStartDate: LocalDate,
        weeklyRateRub: Int,
        entries: List<LedgerEntry>
    ): LocalDate {
        if (weeklyRateRub <= 0) return rentalStartDate
        val paid = totalPaidRub(entries, clientId)
        val adjustment = totalAdjustmentRub(entries, clientId)
        val effectivePaid = (paid - adjustment).coerceAtLeast(0)
        val coveredWeeks = effectivePaid / weeklyRateRub
        val coveredDays = coveredWeeks * 7L
        return rentalStartDate.plusDays(coveredDays)
    }
}
