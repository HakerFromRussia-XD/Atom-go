package com.atomgo.android

import com.atomgo.android.presentation.logic.AdminCatalogFilterEngine
import com.atomgo.android.presentation.model.AdminBikeFilter
import com.atomgo.android.presentation.model.AdminClientFilter
import com.atomgo.android.presentation.model.AdminRentFilter
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientSummaryResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminCatalogFilterEngineTest {

    @Test
    fun derive_filtersSoonReturnAndCountsCorrectly() {
        val rents = listOf(
            rent("c1", "r1", "long_term", active = true, debt = 0),
            rent("c2", "r2", "soon_return", active = true, debt = 100),
            rent("c3", "r3", "in_stock", active = false, debt = 0)
        )

        val derived = AdminCatalogFilterEngine.derive(
            rents = rents,
            rentsSearch = "",
            rentsFilter = AdminRentFilter.SoonReturn,
            clientsCatalog = rents,
            clientsSearch = "",
            clientsFilter = AdminClientFilter.All,
            bikesCatalog = listOf(bike("Alpha", true), bike("Beta", false)),
            bikesSearch = "",
            bikesFilter = AdminBikeFilter.All
        )

        assertEquals(1, derived.filteredRents.size)
        assertEquals("r2", derived.filteredRents.first().rentalId)
        assertEquals(3, derived.rentCounters.all)
        assertEquals(1, derived.rentCounters.soonReturn)
        assertEquals(1, derived.rentCounters.debtors)
        assertEquals(1, derived.rentCounters.mine)
    }

    @Test
    fun derive_sortsBikesAndAppliesFreeFilter() {
        val derived = AdminCatalogFilterEngine.derive(
            rents = emptyList(),
            rentsSearch = "",
            rentsFilter = AdminRentFilter.All,
            clientsCatalog = emptyList(),
            clientsSearch = "",
            clientsFilter = AdminClientFilter.All,
            bikesCatalog = listOf(
                bike("Zeta", false),
                bike("Alpha", false),
                bike("Beta", true)
            ),
            bikesSearch = "",
            bikesFilter = AdminBikeFilter.Free
        )

        assertEquals(2, derived.filteredBikes.size)
        assertEquals("Alpha", derived.filteredBikes[0].bikeModel)
        assertEquals("Zeta", derived.filteredBikes[1].bikeModel)
    }

    private fun rent(
        clientId: String,
        rentalId: String,
        pipeline: String,
        active: Boolean,
        debt: Int
    ): AdminClientSummaryResponse = AdminClientSummaryResponse(
        clientId = clientId,
        rentalId = rentalId,
        clientLogin = "login-$clientId",
        fullName = "Client $clientId",
        bikeModel = "Bike $clientId",
        bikeAvatarUrl = "",
        statusText = "3 дня",
        paidUntil = "2026-12-31",
        rentalPipelineStatus = pipeline,
        rentalIsActive = active,
        debtRub = debt,
        profitRub = 0,
        totalAdjustmentRub = 0,
        carriedDebtRub = 0
    )

    private fun bike(model: String, rented: Boolean): AdminBikeResponse = AdminBikeResponse(
        bikeId = "bike-$model",
        bikeModel = model,
        weeklyRateRub = 3000,
        frameSerialNumber = "frame-$model",
        motorSerialNumber = "motor-$model",
        batterySerialNumber1 = "bat1-$model",
        batterySerialNumber2 = null,
        bikeIsInRental = rented
    )
}
