package com.atomgo.android.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminCatalogFormattersTest {

    @Test
    fun ledgerOperationLabel_marksCashPaymentSeparately() {
        assertEquals("Наличные", ledgerOperationLabel(type = "payment", paymentMethod = "cash"))
        assertEquals("Оплата", ledgerOperationLabel(type = "payment", paymentMethod = null))
        assertEquals("Корректировка", ledgerOperationLabel(type = "adjustment", paymentMethod = "cash"))
    }
}
