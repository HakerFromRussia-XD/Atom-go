package com.atomgo.android.presentation.ui

import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminRentalDetailsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdminRentalEditLogicTest {

    @Test
    fun initialState_usesEveryEditableFieldFromActiveRentalDetails() {
        val preview = AdminRentalPreview.fromDetails(
            details = rentalDetails(
                rentalId = "r-active",
                completedAt = null,
                rentalIsActive = true,
                password = "active-password",
                videoUrl = "https://video.example/active",
                contractUrl = "https://contract.example/active",
                comment = "active comment"
            )
        )

        val state = adminRentalEditInitialState(preview, bikes = emptyList())

        assertEquals(
            AdminRentalEditInitialState(
                clientId = "client-1",
                bikeId = "bike-1",
                login = "client-login",
                password = "active-password",
                periodStart = "2026-05-10",
                periodEnd = "",
                videoUrl = "https://video.example/active",
                contractUrl = "https://contract.example/active",
                comment = "active comment",
                paymentDay = 3
            ),
            state
        )
    }

    @Test
    fun initialState_usesEveryEditableFieldFromCompletedRentalDetails() {
        val preview = AdminRentalPreview.fromDetails(
            details = rentalDetails(
                rentalId = "r-completed",
                completedAt = "2026-05-28",
                rentalIsActive = false,
                password = "completed-password",
                videoUrl = "https://video.example/completed",
                contractUrl = "https://contract.example/completed",
                comment = "completed comment"
            )
        )

        val state = adminRentalEditInitialState(preview, bikes = emptyList())

        assertEquals(
            AdminRentalEditInitialState(
                clientId = "client-1",
                bikeId = "bike-1",
                login = "client-login",
                password = "completed-password",
                periodStart = "2026-05-10",
                periodEnd = "2026-05-28",
                videoUrl = "https://video.example/completed",
                contractUrl = "https://contract.example/completed",
                comment = "completed comment",
                paymentDay = 3
            ),
            state
        )
    }

    @Test
    fun initialState_resolvesBikeIdFromBikeModelWhenPreviewHasNoBikeId() {
        val preview = AdminRentalPreview(
            rentalId = "r-fallback",
            clientId = "client-1",
            clientName = "Client One",
            bikeId = "",
            bikeModel = "Ninebot E-bike Pro",
            bikeAvatarUrl = "",
            periodStart = "2026-05-10",
            periodEnd = null,
            debtRub = 0,
            totalPaidRub = 0,
            totalAdjustmentRub = 0,
            weeklyRateRub = 3000,
            paymentDay = 5,
            clientLogin = "client-login",
            clientPassword = "client-password",
            videoUrl = null,
            contractUrl = null,
            paidUntil = "2026-05-24",
            rentalPipelineStatus = "long_term",
            rentalIsActive = true,
            comment = null,
            clientRentalId = "cr-fallback",
            journalEntries = emptyList(),
            sourceLabel = "fallback"
        )

        val state = adminRentalEditInitialState(
            details = preview,
            bikes = listOf(
                bike("bike-other", "Other bike"),
                bike("bike-ninebot", "Ninebot E-bike Pro")
            )
        )

        assertEquals("bike-ninebot", state.bikeId)
        assertEquals(5, state.paymentDay)
    }

    @Test
    fun validation_onlyRejectsCompletionDateBeforeStartDate() {
        assertNull(adminRentalEditValidationError(periodStart = "", periodEnd = ""))
        assertNull(adminRentalEditValidationError(periodStart = "2026-05-10", periodEnd = ""))
        assertNull(adminRentalEditValidationError(periodStart = "2026-05-10", periodEnd = "2026-05-10"))
        assertNull(adminRentalEditValidationError(periodStart = "2026-05-10", periodEnd = "2026-05-11"))
        assertNull(adminRentalEditValidationError(periodStart = "not-a-date", periodEnd = "2026-05-01"))

        assertEquals(
            AdminRentalEditEndBeforeStartMessage,
            adminRentalEditValidationError(periodStart = "2026-05-10", periodEnd = "2026-05-09")
        )
    }

    @Test
    fun normalizedPaymentDay_fallsBackToStartWeekday() {
        assertEquals(3, normalizedRentalPaymentDay(paymentDay = 9, periodStart = "2026-05-27"))
        assertEquals(7, normalizedRentalPaymentDay(paymentDay = 7, periodStart = "2026-05-27"))
    }

    private fun rentalDetails(
        rentalId: String,
        completedAt: String?,
        rentalIsActive: Boolean,
        password: String,
        videoUrl: String,
        contractUrl: String,
        comment: String
    ): AdminRentalDetailsResponse = AdminRentalDetailsResponse(
        rentalId = rentalId,
        clientId = "client-1",
        clientFullName = "Client One",
        clientLogin = "client-login",
        clientPassword = password,
        bikeId = "bike-1",
        bikeModel = "Ninebot E-bike Pro",
        bikeAvatarUrl = "https://images.example/bike.png",
        weeklyRateRub = 3000,
        rentalStart = "2026-05-10",
        completedAt = completedAt,
        paidUntil = "2026-05-24",
        totalPaidRub = 6000,
        debtRub = 0,
        totalAdjustmentRub = 0,
        rentalPipelineStatus = "long_term",
        rentalIsActive = rentalIsActive,
        paymentDay = 3,
        journalEntries = emptyList(),
        videoUrl = videoUrl,
        contractUrl = contractUrl,
        comment = comment,
        clientRentalId = "client-rental-$rentalId"
    )

    private fun bike(id: String, model: String): AdminBikeResponse = AdminBikeResponse(
        bikeId = id,
        photoUrl = null,
        bikeModel = model,
        weeklyRateRub = 3000,
        frameSerialNumber = "frame-$id",
        motorSerialNumber = "motor-$id",
        batterySerialNumber1 = "battery-1-$id",
        batterySerialNumber2 = null,
        bikeIsInRental = false
    )
}
