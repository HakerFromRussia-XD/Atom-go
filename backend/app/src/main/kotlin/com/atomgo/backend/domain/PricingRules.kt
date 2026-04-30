package com.atomgo.backend.domain

import kotlin.math.ceil

object PricingRules {

    fun ceilTo10(value: Double): Int {
        if (value <= 0.0) return 0
        return (ceil(value / 10.0) * 10.0).toInt()
    }

    fun dayAmount(weeklyRateRub: Int): Int = ceilTo10(weeklyRateRub / 7.0)

    fun weekAmount(weeklyRateRub: Int): Int = weeklyRateRub

    fun twoWeeksAmount(weeklyRateRub: Int): Int = weeklyRateRub * 2

    fun monthAmount(weeklyRateRub: Int): Int = weeklyRateRub * 4

    fun amountForType(type: PaymentType, weeklyRateRub: Int, debtRub: Int): Int = when (type) {
        PaymentType.DAY -> dayAmount(weeklyRateRub)
        PaymentType.WEEK -> weekAmount(weeklyRateRub)
        PaymentType.TWO_WEEKS -> twoWeeksAmount(weeklyRateRub)
        PaymentType.MONTH -> monthAmount(weeklyRateRub)
        PaymentType.DEBT_EXACT -> debtRub
    }
}
