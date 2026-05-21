package com.atomgo.android.domain.repository

import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.CreatePaymentResponse
import com.atomgo.shared.api.PaymentStatusResponse
import com.atomgo.shared.api.UpdateClientReceiptEmailResponse

interface ClientRepository {
    suspend fun fetchDashboard(accessToken: String): ClientDashboardResponse
    suspend fun createPayment(accessToken: String, paymentType: String, receiptEmail: String?): CreatePaymentResponse
    suspend fun updateReceiptEmail(accessToken: String, email: String): UpdateClientReceiptEmailResponse
    suspend fun fetchPaymentStatus(accessToken: String, paymentId: String): PaymentStatusResponse
}
