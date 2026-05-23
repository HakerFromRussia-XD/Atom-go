package com.atomgo.android

import com.atomgo.android.domain.repository.AdminRepository
import com.atomgo.android.domain.usecase.AdminUseCases
import com.atomgo.android.presentation.viewmodel.AdminHomeViewModel
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminClientPhone
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminRentalDetailsResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun fetchAdminRents_returnsRepositoryValue() = runTest {
        val expected = listOf(
            AdminClientSummaryResponse(
                clientId = "c1",
                rentalId = "r1",
                clientLogin = "login",
                fullName = "Иван Иванов",
                bikeModel = "Bike",
                bikeAvatarUrl = "",
                statusText = "3 дня",
                paidUntil = "2026-12-31",
                rentalPipelineStatus = "long_term",
                rentalIsActive = true,
                debtRub = 0,
                profitRub = 1000,
                totalAdjustmentRub = 0,
                carriedDebtRub = 0
            )
        )

        val vm = AdminHomeViewModel(
            adminUseCases = AdminUseCases(
                adminRepository = object : AdminRepository {
                override suspend fun fetchRents(accessToken: String): List<AdminClientSummaryResponse> = expected
                override suspend fun fetchClients(accessToken: String): List<AdminClientSummaryResponse> = emptyList()
                override suspend fun fetchBikes(accessToken: String): List<AdminBikeResponse> = emptyList()
                override suspend fun createClient(accessToken: String, fullName: String, address: String, passportData: String, phoneLabel: String, phoneNumber: String) = Unit
                override suspend fun createBike(accessToken: String, bikeModel: String, weeklyRateRub: Int, frameSerialNumber: String, motorSerialNumber: String, batterySerialNumber1: String, batterySerialNumber2: String?) = Unit
                override suspend fun createRental(accessToken: String, clientId: String?, bikeId: String, login: String, password: String, periodStart: String, periodEnd: String?, videoUrl: String?, contractUrl: String?, comment: String?) = Unit
                override suspend fun fetchClientDetails(accessToken: String, clientId: String): AdminClientDetailsResponse = error("unused")
                override suspend fun fetchRentalDetails(accessToken: String, rentalId: String): AdminRentalDetailsResponse = error("unused")
                override suspend fun deleteRental(accessToken: String, rentalId: String) = Unit
                override suspend fun deleteClient(accessToken: String, clientId: String) = Unit
                override suspend fun updateClient(accessToken: String, clientId: String, fullName: String, address: String, passportData: String, phones: List<AdminClientPhone>, comment: String?) = Unit
                override suspend fun updateBike(accessToken: String, bikeId: String, photoUrl: String?, bikeModel: String, weeklyRateRub: Int, frameSerialNumber: String, motorSerialNumber: String, batterySerialNumber1: String, batterySerialNumber2: String?) = Unit
                override suspend fun updateRental(accessToken: String, rentalId: String, bikeId: String, periodStart: String, periodEnd: String?, login: String?, password: String?, videoUrl: String?, contractUrl: String?, comment: String?) = Unit
                override suspend fun updateRentalPipelineStatus(accessToken: String, rentalId: String, pipelineStatus: String) = Unit
                override suspend fun finishRentalByLifecycle(accessToken: String, rentalId: String) = Unit
                override suspend fun startClientRentalInExisting(accessToken: String, rentalId: String, clientId: String, login: String, password: String, periodStart: String) = Unit
                override suspend fun adjustClientRentalDebt(accessToken: String, clientRentalId: String, amountRub: Int, sign: String, comment: String?) = Unit
                override suspend fun adjustClientDebt(accessToken: String, clientId: String, amountRub: Int, sign: String, comment: String?) = Unit
            }
            )
        )

        var actual: List<AdminClientSummaryResponse> = emptyList()
        vm.fetchAdminRents("token") { result ->
            actual = result.getOrNull().orEmpty()
        }

        advanceUntilIdle()

        assertEquals(1, actual.size)
        assertEquals("r1", actual.first().rentalId)
    }
}
