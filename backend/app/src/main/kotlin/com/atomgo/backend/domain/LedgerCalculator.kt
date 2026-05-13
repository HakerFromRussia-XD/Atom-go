package com.atomgo.backend.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class BillingProjection(
    val paidUntilDate: LocalDate,
    val debtRub: Int,
    val balanceRub: Int,
    val statusText: String
)

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
        val coveredDays = rubToDays(effectivePaid, weeklyRateRub)
        return rentalStartDate.plusDays(coveredDays.toLong())
    }

    fun billingProjection(
        clientId: String,
        rentalStartDate: LocalDate,
        weeklyRateRub: Int,
        entries: List<LedgerEntry>,
        asOf: LocalDate,
        rentalId: String? = null
    ): BillingProjection {
        val paidUntil = paidUntilDate(
            clientId = clientId,
            rentalStartDate = rentalStartDate,
            weeklyRateRub = weeklyRateRub,
            entries = entries,
            rentalId = rentalId
        )
        val debt = debtRub(
            clientId = clientId,
            rentalStartDate = rentalStartDate,
            weeklyRateRub = weeklyRateRub,
            entries = entries,
            asOf = asOf,
            rentalId = rentalId
        )

        if (debt > 0) {
            val debtDays = rubToDays(debt, weeklyRateRub)
            return BillingProjection(
                paidUntilDate = paidUntil,
                debtRub = debt,
                balanceRub = 0,
                statusText = "Долг за $debtDays дн."
            )
        }

        val daysLeft = ChronoUnit.DAYS.between(asOf, paidUntil).toInt().coerceAtLeast(0)
        return BillingProjection(
            paidUntilDate = paidUntil,
            debtRub = 0,
            balanceRub = roundToTens(daysLeft * dailyRateRub(weeklyRateRub)),
            statusText = "Оплачено еще на $daysLeft дн."
        )
    }

    /**
     * Финальный долг при закрытии клиентской аренды.
     *
     * Спецификация (docs/02_money_and_debt_rules.md §5, docs/14_rental_lifecycle.md §3):
     * долг считается строго по дням перерасхода, а не по неделям.
     *
     *   day_amount     = weekly_rate / 7
     *   covered_days   = floor(total_paid / day_amount)
     *   used_days      = days_between(start_date, end_date)
     *   overdue_days   = max(0, used_days - covered_days)
     *   final_debt     = max(0, overdue_days * day_amount + net_adjustment)
     *
     * Где net_adjustment = totalAdjustmentRub (положительный увеличивает долг,
     * отрицательный уменьшает) для этой `ClientRentalRecord`.
     *
     * Пример из спеки: ставка 3500₽/нед, клиент взял в понедельник, вернул
     * через 9 дней, оплатил 3500₽ → day=500, covered=7, used=9, overdue=2,
     * final = 1000.
     */
    fun finalDebtOnClosure(
        clientId: String,
        rentalStartDate: LocalDate,
        rentalEndDate: LocalDate,
        weeklyRateRub: Int,
        entries: List<LedgerEntry>,
        rentalId: String? = null
    ): Int {
        if (rentalEndDate.isBefore(rentalStartDate)) return 0
        val dailyRate = dailyRateRub(weeklyRateRub)
        if (dailyRate <= 0.0) return 0
        val totalPaid = totalPaidRub(entries, clientId, rentalId)
        val adjustment = totalAdjustmentRub(entries, clientId, rentalId)
        val coveredDays = (totalPaid / dailyRate).toInt().coerceAtLeast(0)
        val usedDays = ChronoUnit.DAYS.between(rentalStartDate, rentalEndDate).toInt()
        val overdueDays = (usedDays - coveredDays).coerceAtLeast(0)
        val gross = (overdueDays * dailyRate).roundToInt()
        return (gross + adjustment).coerceAtLeast(0)
    }

    private fun rubToDays(amountRub: Int, weeklyRateRub: Int): Int {
        val dailyRate = dailyRateRub(weeklyRateRub)
        if (dailyRate <= 0.0) return 0
        return (amountRub / dailyRate).roundToInt().coerceAtLeast(0)
    }

    private fun dailyRateRub(weeklyRateRub: Int): Double {
        if (weeklyRateRub <= 0) return 0.0
        return weeklyRateRub / 7.0
    }

    private fun roundToTens(value: Double): Int {
        return (value / 10.0).roundToInt() * 10
    }
}
