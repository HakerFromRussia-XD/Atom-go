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

    private fun LedgerEntry.belongsTo(clientId: String, rentalId: String?): Boolean {
        if (this.clientId != clientId) return false
        // Legacy records were created before rental_id existed. Keep them visible for the
        // active rental until we run a dedicated historical migration.
        return rentalId == null || this.rentalId == rentalId || this.rentalId == null
    }

    fun totalPaidRub(entries: List<LedgerEntry>, clientId: String, rentalId: String? = null): Int {
        return entries
            .asSequence()
            .filter { it.belongsTo(clientId, rentalId) }
            .filter { it.type == LedgerType.PAYMENT && it.direction == -1 }
            .sumOf { it.amountRub }
    }

    fun totalAdjustmentRub(entries: List<LedgerEntry>, clientId: String, rentalId: String? = null): Int {
        return entries
            .asSequence()
            .filter { it.belongsTo(clientId, rentalId) }
            .filter { it.type == LedgerType.ADJUSTMENT }
            .sumOf { it.amountRub * if (it.direction == -1) -1 else 1 }
    }

    fun debtRub(
        clientId: String,
        rentalStartDate: LocalDate,
        weeklyRateRub: Int,
        entries: List<LedgerEntry>,
        asOf: LocalDate,
        rentalId: String? = null
    ): Int {
        val due = chargeDueRub(rentalStartDate, asOf, weeklyRateRub)
        val paid = totalPaidRub(entries, clientId, rentalId)
        val adjustment = totalAdjustmentRub(entries, clientId, rentalId)
        val raw = due - paid + adjustment
        return raw.coerceAtLeast(0)
    }

    fun paidUntilDate(
        clientId: String,
        rentalStartDate: LocalDate,
        weeklyRateRub: Int,
        entries: List<LedgerEntry>,
        rentalId: String? = null
    ): LocalDate {
        if (weeklyRateRub <= 0) return rentalStartDate
        val paid = totalPaidRub(entries, clientId, rentalId)
        val adjustment = totalAdjustmentRub(entries, clientId, rentalId)
        val effectivePaid = (paid - adjustment).coerceAtLeast(0)
        val coveredWeeks = effectivePaid / weeklyRateRub
        val coveredDays = coveredWeeks * 7L
        return rentalStartDate.plusDays(coveredDays)
    }
}
