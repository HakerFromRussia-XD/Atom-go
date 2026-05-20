package com.atomgo.android

import com.atomgo.android.domain.repository.ClientRepository
import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.ClientPaymentPresetsResponse
import com.atomgo.shared.api.CreatePaymentResponse
import com.atomgo.shared.api.PaymentStatusResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun fetchClientDashboard_returnsRepositoryValue() = runTest {
        val expected = ClientDashboardResponse(
            clientId = "client-1",
            bikeModel = "Test bike",
            bikeAvatarUrl = "",
            rentalStart = "2026-01-01",
            paidUntil = "2026-12-31",
            debtRub = 0,
            balanceRub = 100,
            totalAdjustmentRub = 0,
            presets = ClientPaymentPresetsResponse(
                dayRub = 100,
                weekRub = 700,
                twoWeeksRub = 1300,
                monthRub = 2500,
                debtExactRub = 0
            ),
            taxMode = "nps",
            requiresReceiptEmail = false
        )
        val vm = ClientHomeViewModel(
            clientRepository = object : ClientRepository {
                override suspend fun fetchDashboard(accessToken: String): ClientDashboardResponse = expected
                override suspend fun createPayment(accessToken: String, paymentType: String, receiptEmail: String?): CreatePaymentResponse {
                    error("unused")
                }
                override suspend fun fetchPaymentStatus(accessToken: String, paymentId: String): PaymentStatusResponse {
                    error("unused")
                }
            }
        )

        var actual: ClientDashboardResponse? = null
        vm.fetchClientDashboard("token") { result ->
            actual = result.getOrNull()
        }

        advanceUntilIdle()

        assertNotNull(actual)
        assertEquals("Test bike", actual?.bikeModel)
        assertEquals("2026-12-31", actual?.paidUntil)
    }
}
