package com.atomgo.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomgo.android.data.repository.DefaultClientRepository
import com.atomgo.android.domain.repository.ClientRepository
import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.CreatePaymentResponse
import com.atomgo.shared.api.PaymentStatusResponse
import kotlinx.coroutines.launch

class ClientHomeViewModel(
    private val clientRepository: ClientRepository = DefaultClientRepository()
) : ViewModel() {

    fun fetchClientDashboard(accessToken: String, onResult: (Result<ClientDashboardResponse>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                clientRepository.fetchDashboard(accessToken)
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
                clientRepository.createPayment(
                    accessToken = accessToken,
                    paymentType = paymentType,
                    receiptEmail = receiptEmail
                )
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
                clientRepository.fetchPaymentStatus(accessToken = accessToken, paymentId = paymentId)
            }.also(onResult)
        }
    }
}
