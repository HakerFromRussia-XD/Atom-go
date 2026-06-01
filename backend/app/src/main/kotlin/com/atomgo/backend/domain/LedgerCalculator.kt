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

    fun chargeDueRub(
        startDate: LocalDate,
        asOf: LocalDate,
        weeklyRateRub: Int,
        paymentDay: Int = startDate.dayOfWeek.value
    ): Int {
        if (asOf.isBefore(startDate) || weeklyRateRub <= 0) return 0
        val normalizedPaymentDay = normalizedPaymentDay(startDate, paymentDay)
        val startDay = startDate.dayOfWeek.value
        val daysUntilFirstPayment = (normalizedPaymentDay - startDay + 7) % 7
        if (daysUntilFirstPayment == 0) {
            return dueWeeksCount(startDate, asOf) * weeklyRateRub
        }

        val firstPaymentDate = startDate.plusDays(daysUntilFirstPayment.toLong())
        val transitionCharge = daysUntilFirstPayment * PricingRules.dayAmount(weeklyRateRub)
        if (asOf.isBefore(firstPaymentDate)) {
            return transitionCharge
        }

        return dueWeeksCount(firstPaymentDate, asOf) * weeklyRateRub
    }

    private fun LedgerEntry.belongsTo(clientId: String, rentalId: String?): Boolean {
        if (this.clientId != clientId) return false
        // Раньше fallback `this.rentalId == null` включал legacy-entries без
        // rentalId в долг любой аренды этого клиента. Но carriedDebt-операции
        // (admin списание/приём оплаты перенесённого долга) тоже создают entries
        // с rentalId == null, и они смешивались с расчётом активной аренды:
        // долг конкретной client_rental в /admin/rentals/{id} получался меньше,
        // чем в /admin/clients/{id}.rentals[].debtRub (где фильтрация
        // groupBy исключала null-rentalId entries). Теперь строго: entry
        // принадлежит конкретной client_rental ТОЛЬКО если её rentalId совпадает.
        // legacy-данные мигрируются через ensureClientRentalModel
        // (rentalId перепривязывается на новый ClientRentalRecord.id).
        return rentalId == null || this.rentalId == rentalId
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
        rentalId: String? = null,
        paymentDay: Int = rentalStartDate.dayOfWeek.value
    ): Int {
        val paid = totalPaidRub(entries, clientId, rentalId)
        val adjustment = totalAdjustmentRub(entries, clientId, rentalId)
        val due = chargeDueRub(rentalStartDate, asOf, weeklyRateRub, paymentDay) +
            transitionDebtExtraRub(rentalStartDate, asOf, weeklyRateRub, paymentDay, paid)
        val raw = due - paid + adjustment
        return raw.coerceAtLeast(0)
    }

    fun paidUntilDate(
        clientId: String,
        rentalStartDate: LocalDate,
        weeklyRateRub: Int,
        entries: List<LedgerEntry>,
        rentalId: String? = null,
        paymentDay: Int = rentalStartDate.dayOfWeek.value
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
        rentalId: String? = null,
        paymentDay: Int = rentalStartDate.dayOfWeek.value
    ): BillingProjection {
        val paidUntil = paidUntilDate(
            clientId = clientId,
            rentalStartDate = rentalStartDate,
            weeklyRateRub = weeklyRateRub,
            entries = entries,
            rentalId = rentalId,
            paymentDay = paymentDay
        )
        val debt = debtRub(
            clientId = clientId,
            rentalStartDate = rentalStartDate,
            weeklyRateRub = weeklyRateRub,
            entries = entries,
            asOf = asOf,
            rentalId = rentalId,
            paymentDay = paymentDay
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
     *   used_days      = days_between(start_date, end_date)
     *   used_charge    = used_days * day_amount
     *   final_debt     = max(0, used_charge - total_paid + net_adjustment)
     *
     * Где net_adjustment = totalAdjustmentRub (положительный увеличивает долг,
     * отрицательный уменьшает) для этой `ClientRentalRecord`.
     *
     * Пример из спеки: ставка 3500₽/нед, клиент взял в понедельник, вернул
     * через 9 дней, оплатил 3500₽ → day=500, used_charge=4500,
     * final = 4500 - 3500 = 1000.
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
        val usedDays = ChronoUnit.DAYS.between(rentalStartDate, rentalEndDate).toInt()
        val usedCharge = (usedDays * dailyRate).roundToInt()
        return (usedCharge - totalPaid + adjustment).coerceAtLeast(0)
    }

    private fun rubToDays(amountRub: Int, weeklyRateRub: Int): Int {
        val dailyRate = dailyRateRub(weeklyRateRub)
        if (dailyRate <= 0.0) return 0
        return (amountRub / dailyRate).roundToInt().coerceAtLeast(0)
    }

    private fun normalizedPaymentDay(startDate: LocalDate, paymentDay: Int): Int {
        return if (paymentDay in 1..7) paymentDay else startDate.dayOfWeek.value
    }

    private fun transitionDebtExtraRub(
        startDate: LocalDate,
        asOf: LocalDate,
        weeklyRateRub: Int,
        paymentDay: Int,
        paidRub: Int
    ): Int {
        if (asOf.isBefore(startDate) || weeklyRateRub <= 0 || paidRub < weeklyRateRub) return 0
        val normalizedPaymentDay = normalizedPaymentDay(startDate, paymentDay)
        val startDay = startDate.dayOfWeek.value
        val daysUntilFirstPayment = (normalizedPaymentDay - startDay + 7) % 7
        if (daysUntilFirstPayment == 0) return 0
        val firstPaymentDate = startDate.plusDays(daysUntilFirstPayment.toLong())
        if (asOf.isBefore(firstPaymentDate)) return 0
        return daysUntilFirstPayment * PricingRules.dayAmount(weeklyRateRub)
    }

    private fun dailyRateRub(weeklyRateRub: Int): Double {
        if (weeklyRateRub <= 0) return 0.0
        return weeklyRateRub / 7.0
    }

    private fun roundToTens(value: Double): Int {
        return (value / 10.0).roundToInt() * 10
    }
}
