package com.atomgo.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomgo.android.domain.usecase.ClientUseCases
import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.CreatePaymentResponse
import com.atomgo.shared.api.PaymentStatusResponse
import com.atomgo.shared.api.UpdateClientReceiptEmailResponse
import kotlinx.coroutines.launch

class ClientHomeViewModel(
    private val clientUseCases: ClientUseCases
) : ViewModel() {

    fun fetchClientDashboard(accessToken: String, onResult: (Result<ClientDashboardResponse>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                clientUseCases.fetchDashboard(accessToken = accessToken)
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
                clientUseCases.createPayment(
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
                clientUseCases.fetchPaymentStatus(accessToken = accessToken, paymentId = paymentId)
            }.also(onResult)
        }
    }

    fun updateReceiptEmail(
        accessToken: String,
        email: String,
        onResult: (Result<UpdateClientReceiptEmailResponse>) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                clientUseCases.updateReceiptEmail(accessToken = accessToken, email = email)
            }.also(onResult)
        }
    }
}
