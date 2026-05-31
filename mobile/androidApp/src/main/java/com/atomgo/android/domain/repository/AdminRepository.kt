package com.atomgo.android.domain.repository

import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminClientPhone
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminRentalDetailsResponse

interface AdminRepository {
    suspend fun fetchRents(accessToken: String): List<AdminClientSummaryResponse>
    suspend fun fetchClients(accessToken: String): List<AdminClientSummaryResponse>
    suspend fun fetchBikes(accessToken: String): List<AdminBikeResponse>

    suspend fun createClient(
        accessToken: String,
        fullName: String,
        address: String,
        passportData: String,
        phoneLabel: String,
        phoneNumber: String
    )

    suspend fun createBike(
        accessToken: String,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?
    )

    suspend fun createRental(
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
    )

    suspend fun fetchClientDetails(accessToken: String, clientId: String): AdminClientDetailsResponse
    suspend fun fetchRentalDetails(accessToken: String, rentalId: String): AdminRentalDetailsResponse
    suspend fun deleteRental(accessToken: String, rentalId: String)
    suspend fun deleteClient(accessToken: String, clientId: String)

    suspend fun updateClient(
        accessToken: String,
        clientId: String,
        fullName: String,
        address: String,
        passportData: String,
        phones: List<AdminClientPhone>,
        comment: String?
    )

    suspend fun updateBike(
        accessToken: String,
        bikeId: String,
        photoUrl: String?,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?
    )

    suspend fun updateRental(
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
    )

    suspend fun updateRentalPipelineStatus(accessToken: String, rentalId: String, pipelineStatus: String)
    suspend fun finishRentalByLifecycle(accessToken: String, rentalId: String)

    suspend fun startClientRentalInExisting(
        accessToken: String,
        rentalId: String,
        clientId: String,
        login: String,
        password: String,
        periodStart: String
    )

    suspend fun adjustClientRentalDebt(
        accessToken: String,
        clientRentalId: String,
        amountRub: Int,
        sign: String,
        comment: String?
    )

    suspend fun recordClientRentalCashPayment(
        accessToken: String,
        clientRentalId: String,
        amountRub: Int,
        comment: String?
    )

    suspend fun adjustClientDebt(
        accessToken: String,
        clientId: String,
        amountRub: Int,
        sign: String,
        comment: String?
    )
}
