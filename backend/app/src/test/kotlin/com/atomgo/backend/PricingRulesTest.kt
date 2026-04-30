package com.atomgo.backend

import com.atomgo.backend.domain.PricingRules
import kotlin.test.Test
import kotlin.test.assertEquals

class PricingRulesTest {

    @Test
    fun `round to tens should round up`() {
        assertEquals(440, PricingRules.ceilTo10(431.0))
        assertEquals(430, PricingRules.ceilTo10(430.0))
        assertEquals(0, PricingRules.ceilTo10(0.0))
    }

    @Test
    fun `day week two weeks and month amounts`() {
        val weekly = 3000
        assertEquals(430, PricingRules.dayAmount(weekly))
        assertEquals(3000, PricingRules.weekAmount(weekly))
        assertEquals(6000, PricingRules.twoWeeksAmount(weekly))
        assertEquals(12000, PricingRules.monthAmount(weekly))
    }
}
