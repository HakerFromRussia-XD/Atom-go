package com.atomgo.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomgo.shared.api.AtomGoApiClient
import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminClientPhone
import com.atomgo.shared.api.AdminCreateBikeRequest
import com.atomgo.shared.api.AdminCreateClientRequest
import com.atomgo.shared.api.AdminCreateRentalRequest
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminUpdateBikeRequest
import com.atomgo.shared.api.AdminUpdateClientRequest
import com.atomgo.shared.api.AdminUpdateRentalRequest
import com.atomgo.shared.api.CreatePaymentResponse
import com.atomgo.shared.api.PaymentStatusResponse
import com.atomgo.shared.api.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    private val _route = MutableStateFlow<AppRoute>(AppRoute.Login)
    val route: StateFlow<AppRoute> = _route.asStateFlow()

    fun onAuthenticated(session: AuthSession) {
        _route.value = when (session.role) {
            UserRole.CLIENT -> AppRoute.ClientHome(session)
            UserRole.ADMIN -> AppRoute.AdminHome(session)
        }
    }

    fun logout(resetLogin: () -> Unit) {
        _route.value = AppRoute.Login
        resetLogin()
    }

    fun fetchClientDashboard(accessToken: String, onResult: (Result<ClientDashboardResponse>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                apiClient.fetchClientDashboard(accessToken)
            }.also(onResult)
        }
    }

    fun createClientPayment(
        accessToken: String,
        paymentType: String,
        receiptEmail: String?,
        onResult: (Result<CreatePaymentResponse>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                if (!receiptEmail.isNullOrBlank()) {
                    apiClient.updateClientReceiptEmail(accessToken = accessToken, email = receiptEmail)
                }
                apiClient.createPayment(accessToken = accessToken, paymentType = paymentType)
            }.also(onResult)
        }
    }

    fun refreshPaymentStatus(
        accessToken: String,
        paymentId: String,
        onResult: (Result<PaymentStatusResponse>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.fetchPaymentStatus(accessToken = accessToken, paymentId = paymentId)
            }.also(onResult)
        }
    }

    fun fetchAdminRents(accessToken: String, onResult: (Result<List<AdminClientSummaryResponse>>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                apiClient.fetchAdminRents(accessToken)
            }.also(onResult)
        }
    }

    fun createAdminClient(
        accessToken: String,
        fullName: String,
        address: String,
        passportData: String,
        phoneLabel: String,
        phoneNumber: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.createAdminClient(
                    accessToken = accessToken,
                    requestBody = AdminCreateClientRequest(
                        fullName = fullName,
                        address = address,
                        passportData = passportData,
                        phones = listOf(AdminClientPhone(label = phoneLabel, number = phoneNumber))
                    )
                )
                Unit
            }.also(onResult)
        }
    }

    fun createAdminBike(
        accessToken: String,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
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
                Unit
            }.also(onResult)
        }
    }

    fun createAdminRental(
        accessToken: String,
        clientId: String?,
        bikeId: String,
        login: String,
        password: String,
        periodStart: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.createAdminRental(
                    accessToken = accessToken,
                    requestBody = AdminCreateRentalRequest(
                        clientId = clientId,
                        bikeId = bikeId,
                        login = login,
                        password = password,
                        periodStart = periodStart
                    )
                )
                Unit
            }.also(onResult)
        }
    }

    fun fetchAdminClientDetails(
        accessToken: String,
        clientId: String,
        onResult: (Result<AdminClientDetailsResponse>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.fetchAdminClientDetails(accessToken = accessToken, clientId = clientId)
            }.also(onResult)
        }
    }

    fun deleteAdminRental(
        accessToken: String,
        rentalId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.deleteAdminRental(accessToken = accessToken, rentalId = rentalId)
                Unit
            }.also(onResult)
        }
    }

    fun deleteAdminClient(
        accessToken: String,
        clientId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.deleteAdminClient(accessToken = accessToken, clientId = clientId)
                Unit
            }.also(onResult)
        }
    }

    fun updateAdminClient(
        accessToken: String,
        clientId: String,
        fullName: String,
        address: String,
        passportData: String,
        phoneLabel: String,
        phoneNumber: String,
        comment: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.updateAdminClient(
                    accessToken = accessToken,
                    clientId = clientId,
                    requestBody = AdminUpdateClientRequest(
                        fullName = fullName,
                        address = address,
                        passportData = passportData,
                        phones = listOf(AdminClientPhone(label = phoneLabel, number = phoneNumber)),
                        comment = comment
                    )
                )
                Unit
            }.also(onResult)
        }
    }

    fun updateAdminBike(
        accessToken: String,
        bikeId: String,
        bikeModel: String,
        weeklyRateRub: Int,
        frameSerialNumber: String,
        motorSerialNumber: String,
        batterySerialNumber1: String,
        batterySerialNumber2: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.updateAdminBike(
                    accessToken = accessToken,
                    bikeId = bikeId,
                    requestBody = AdminUpdateBikeRequest(
                        bikeModel = bikeModel,
                        weeklyRateRub = weeklyRateRub,
                        frameSerialNumber = frameSerialNumber,
                        motorSerialNumber = motorSerialNumber,
                        batterySerialNumber1 = batterySerialNumber1,
                        batterySerialNumber2 = batterySerialNumber2
                    )
                )
                Unit
            }.also(onResult)
        }
    }

    fun updateAdminRental(
        accessToken: String,
        rentalId: String,
        bikeId: String,
        periodStart: String,
        periodEnd: String?,
        login: String?,
        password: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                apiClient.updateAdminRental(
                    accessToken = accessToken,
                    rentalId = rentalId,
                    requestBody = AdminUpdateRentalRequest(
                        bikeId = bikeId,
                        periodStart = periodStart,
                        periodEnd = periodEnd,
                        login = login,
                        password = password
                    )
                )
                Unit
            }.also(onResult)
        }
    }

    fun finishAdminRental(
        accessToken: String,
        rentalId: String,
        bikeId: String,
        periodStart: String,
        finishDate: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        updateAdminRental(
            accessToken = accessToken,
            rentalId = rentalId,
            bikeId = bikeId,
            periodStart = periodStart,
            periodEnd = finishDate,
            login = null,
            password = null,
            onResult = onResult
        )
    }

    fun startAdminRental(
        accessToken: String,
        clientId: String,
        bikeId: String,
        login: String,
        password: String,
        periodStart: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        createAdminRental(
            accessToken = accessToken,
            clientId = clientId,
            bikeId = bikeId,
            login = login,
            password = password,
            periodStart = periodStart,
            onResult = onResult
        )
    }

    override fun onCleared() {
        apiClient.close()
        super.onCleared()
    }
}
