package com.atomgo.android.data.repository

import com.atomgo.android.BackendConfig
import com.atomgo.android.domain.repository.AdminRepository
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminClientPhone
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminCreateBikeRequest
import com.atomgo.shared.api.AdminCreateClientRequest
import com.atomgo.shared.api.AdminCreateRentalRequest
import com.atomgo.shared.api.AdminRentalDetailsResponse
import com.atomgo.shared.api.AdminUpdateBikeRequest
import com.atomgo.shared.api.AdminUpdateClientRequest
import com.atomgo.shared.api.AdminUpdateRentalRequest
import com.atomgo.shared.api.AtomGoApiClient

class DefaultAdminRepository : AdminRepository {
    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    override suspend fun fetchRents(accessToken: String): List<AdminClientSummaryResponse> {
        return apiClient.fetchAdminRents(accessToken)
    }

    override suspend fun fetchClients(accessToken: String): List<AdminClientSummaryResponse> {
        return apiClient.fetchAdminClients(accessToken)
    }

    override suspend fun fetchBikes(accessToken: String): List<AdminBikeResponse> {
        return apiClient.fetchAdminBikes(accessToken)
    }

    override suspend fun createClient(
        accessToken: String,
        fullName: String,
        address: String,
        passportData: String,
        phoneLabel: String,
        phoneNumber: String
    ) {
        apiClient.createAdminClient(
            accessToken = accessToken,
            requestBody = AdminCreateClientRequest(
                fullName = fullName,
                address = address,
                passportData = passportData,
                phones = listOf(AdminClientPhone(label = phoneLabel, number = phoneNumber))
            )
        )
    }

    override suspend fun createBike(
        accessToken: String,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?
    ) {
        apiClient.createAdminBike(
            accessToken = accessToken,
            requestBody = AdminCreateBikeRequest(
                bikeModel = bikeModel,
                weeklyRateRub = weeklyRateRub,
                frameSerialNumber = frameSerialNumber,
                motorSerialNumber = motorSerialNumber,
                batterySerialNumber1 = batterySerialNumber1,
                batterySerialNumber2 = batterySerialNumber2
            )
        )
    }

    override suspend fun createRental(
        accessToken: String,
        clientId: String?,
        bikeId: String,
        login: String,
        password: String,
        periodStart: String,
        periodEnd: String?,
        videoUrl: String?,
        contractUrl: String?,
        comment: String?
    ) {
        apiClient.createAdminRental(
            accessToken = accessToken,
            requestBody = AdminCreateRentalRequest(
                clientId = clientId,
                bikeId = bikeId,
                login = login,
                password = password,
                periodStart = periodStart,
                periodEnd = periodEnd,
                videoUrl = videoUrl,
                contractUrl = contractUrl,
                comment = comment
            )
        )
    }

    override suspend fun fetchClientDetails(accessToken: String, clientId: String): AdminClientDetailsResponse {
        return apiClient.fetchAdminClientDetails(accessToken = accessToken, clientId = clientId)
    }

    override suspend fun fetchRentalDetails(accessToken: String, rentalId: String): AdminRentalDetailsResponse {
        return apiClient.fetchAdminRentalDetails(accessToken = accessToken, rentalId = rentalId)
    }

    override suspend fun deleteRental(accessToken: String, rentalId: String) {
        apiClient.deleteAdminRental(accessToken = accessToken, rentalId = rentalId)
    }

    override suspend fun deleteClient(accessToken: String, clientId: String) {
        apiClient.deleteAdminClient(accessToken = accessToken, clientId = clientId)
    }

    override suspend fun updateClient(
        accessToken: String,
        clientId: String,
        fullName: String,
        address: String,
        passportData: String,
        phones: List<AdminClientPhone>,
        comment: String?
    ) {
        apiClient.updateAdminClient(
            accessToken = accessToken,
            clientId = clientId,
            requestBody = AdminUpdateClientRequest(
                fullName = fullName,
                address = address,
                passportData = passportData,
                phones = phones,
                comment = comment
            )
        )
    }

    override suspend fun updateBike(
        accessToken: String,
        bikeId: String,
        photoUrl: String?,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?
    ) {
        apiClient.updateAdminBike(
            accessToken = accessToken,
            bikeId = bikeId,
            requestBody = AdminUpdateBikeRequest(
                photoUrl = photoUrl,
                bikeModel = bikeModel,
                weeklyRateRub = weeklyRateRub,
                frameSerialNumber = frameSerialNumber,
                motorSerialNumber = motorSerialNumber,
                batterySerialNumber1 = batterySerialNumber1,
                batterySerialNumber2 = batterySerialNumber2
            )
        )
    }

    override suspend fun updateRental(
        accessToken: String,
        rentalId: String,
        bikeId: String,
        periodStart: String,
        periodEnd: String?,
        login: String?,
        password: String?,
        videoUrl: String?,
        contractUrl: String?,
        comment: String?
    ) {
        apiClient.updateAdminRental(
            accessToken = accessToken,
            rentalId = rentalId,
            requestBody = AdminUpdateRentalRequest(
                bikeId = bikeId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                login = login,
                password = password,
                videoUrl = videoUrl,
                contractUrl = contractUrl,
                comment = comment
            )
        )
    }

    override suspend fun updateRentalPipelineStatus(accessToken: String, rentalId: String, pipelineStatus: String) {
        apiClient.updateAdminRentalPipelineStatus(
            accessToken = accessToken,
            rentalId = rentalId,
            pipelineStatus = pipelineStatus
        )
    }

    override suspend fun finishRentalByLifecycle(accessToken: String, rentalId: String) {
        apiClient.finishAdminRental(accessToken = accessToken, rentalId = rentalId)
    }

    override suspend fun startClientRentalInExisting(
        accessToken: String,
        rentalId: String,
        clientId: String,
        login: String,
        password: String,
        periodStart: String
    ) {
        apiClient.startAdminClientRentalInExisting(
            accessToken = accessToken,
            rentalId = rentalId,
            clientId = clientId,
            login = login,
            password = password,
            periodStart = periodStart
        )
    }

    override suspend fun adjustClientRentalDebt(
        accessToken: String,
        clientRentalId: String,
        amountRub: Int,
        sign: String,
        comment: String?
    ) {
        apiClient.adjustAdminClientRentalDebt(
            accessToken = accessToken,
            clientRentalId = clientRentalId,
            amountRub = amountRub,
            sign = sign,
            comment = comment
        )
    }

    override suspend fun adjustClientDebt(
        accessToken: String,
        clientId: String,
        amountRub: Int,
        sign: String,
        comment: String?
    ) {
        apiClient.adjustAdminClientDebt(
            accessToken = accessToken,
            clientId = clientId,
            amountRub = amountRub,
            sign = sign,
            comment = comment
        )
    }
}
