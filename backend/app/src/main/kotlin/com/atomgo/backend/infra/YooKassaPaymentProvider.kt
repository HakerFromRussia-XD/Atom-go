package com.atomgo.backend.infra

import com.atomgo.backend.domain.PaymentStatus
import com.atomgo.backend.domain.PaymentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

interface PaymentProvider {
    fun createPayment(request: ProviderCreatePaymentRequest): ProviderPaymentInfo
    fun fetchPayment(providerPaymentId: String): ProviderPaymentInfo?
}

data class ProviderCreatePaymentRequest(
    val localPaymentId: String,
    val clientId: String,
    val rentalId: String,
    val paymentType: PaymentType,
    val amountRub: Int,
    val idempotenceKey: String,
    val description: String,
    val receipt: ProviderReceipt? = null
)

data class ProviderReceipt(
    val customerEmail: String,
    val taxSystemCode: Int,
    val vatCode: Int,
    val paymentMode: String,
    val paymentSubject: String
)

data class ProviderPaymentInfo(
    val providerPaymentId: String,
    val status: PaymentStatus,
    val amountRub: Int?,
    val confirmationUrl: String?,
    val localPaymentId: String?,
    val clientId: String?,
    val rentalId: String?,
    val paymentType: String?,
    val receiptRegistration: String? = null
)

class MockYooKassaPaymentProvider : PaymentProvider {
    override fun createPayment(request: ProviderCreatePaymentRequest): ProviderPaymentInfo {
        val providerPaymentId = "mock-${request.localPaymentId}"
        return ProviderPaymentInfo(
            providerPaymentId = providerPaymentId,
            status = PaymentStatus.PENDING,
            amountRub = request.amountRub,
            confirmationUrl = "https://example.test/yookassa-mock/${request.localPaymentId}",
            localPaymentId = request.localPaymentId,
            clientId = request.clientId,
            rentalId = request.rentalId,
            paymentType = PaymentType.toApi(request.paymentType),
            receiptRegistration = "pending"
        )
    }

    override fun fetchPayment(providerPaymentId: String): ProviderPaymentInfo? {
        return null
    }
}

class DisabledYooKassaPaymentProvider(private val reason: String) : PaymentProvider {
    override fun createPayment(request: ProviderCreatePaymentRequest): ProviderPaymentInfo {
        throw YooKassaException("YooKassa is not configured", 0, reason)
    }

    override fun fetchPayment(providerPaymentId: String): ProviderPaymentInfo? {
        throw YooKassaException("YooKassa is not configured", 0, reason)
    }
}

class YooKassaPaymentProvider private constructor(
    private val config: YooKassaConfig,
    private val json: Json,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) : PaymentProvider {

    override fun createPayment(request: ProviderCreatePaymentRequest): ProviderPaymentInfo {
        val returnUrl = "${config.publicBaseUrl}/api/v1/payments/${request.localPaymentId}/return"
        val payload = YooKassaCreatePaymentRequest(
            amount = YooKassaAmount(value = rubToDecimalString(request.amountRub), currency = "RUB"),
            capture = true,
            confirmation = YooKassaConfirmationRequest(type = "redirect", returnUrl = returnUrl),
            description = request.description.take(128),
            metadata = YooKassaMetadata(
                localPaymentId = request.localPaymentId,
                clientId = request.clientId,
                rentalId = request.rentalId,
                paymentType = PaymentType.toApi(request.paymentType)
            ),
            receipt = request.receipt?.toYooKassaReceipt(
                amountRub = request.amountRub,
                description = request.description.take(128)
            )
        )

        val httpRequest = baseRequest("${config.apiBaseUrl}/payments")
            .header("Idempotence-Key", request.idempotenceKey)
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(payload)))
            .build()

        val response = send(httpRequest)
        if (response.statusCode() !in 200..299) {
            throw YooKassaException("YooKassa create payment failed", response.statusCode(), response.body())
        }

        val created = json.decodeFromString<YooKassaPaymentResponse>(response.body())
        return created.toProviderInfo()
    }

    override fun fetchPayment(providerPaymentId: String): ProviderPaymentInfo? {
        val httpRequest = baseRequest("${config.apiBaseUrl}/payments/$providerPaymentId")
            .GET()
            .build()

        val response = send(httpRequest)
        if (response.statusCode() == 404) return null
        if (response.statusCode() !in 200..299) {
            throw YooKassaException("YooKassa fetch payment failed", response.statusCode(), response.body())
        }

        return json.decodeFromString<YooKassaPaymentResponse>(response.body()).toProviderInfo()
    }

    private fun baseRequest(url: String): HttpRequest.Builder {
        val credentials = "${config.shopId}:${config.secretKey}"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(StandardCharsets.UTF_8))
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Basic $encoded")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
    }

    private fun send(request: HttpRequest): HttpResponse<String> {
        return try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: HttpTimeoutException) {
            throw YooKassaException("YooKassa network timeout", 0, e.message ?: "HTTP timeout")
        } catch (e: java.io.IOException) {
            throw YooKassaException("YooKassa network error", 0, e.message ?: e.javaClass.simpleName)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw YooKassaException("YooKassa request interrupted", 0, e.message ?: "Interrupted")
        }
    }

    companion object {
        fun fromEnvironment(json: Json): PaymentProvider {
            val shopId = System.getenv("YOOKASSA_SHOP_ID")?.trim().orEmpty()
            val secretKey = System.getenv("YOOKASSA_SECRET_KEY")?.trim().orEmpty()
            val apiBaseUrl = System.getenv("YOOKASSA_API_BASE")?.trim()?.ifBlank { null } ?: "https://api.yookassa.ru/v3"
            val publicBaseUrl = System.getenv("YOOKASSA_PUBLIC_BASE_URL")?.trim()?.ifBlank { null }
            val useMock = System.getenv("YOOKASSA_USE_MOCK")?.equals("true", ignoreCase = true) == true

            if (useMock) {
                return MockYooKassaPaymentProvider()
            }

            val missingKeys = buildList {
                if (shopId.isBlank()) add("YOOKASSA_SHOP_ID")
                if (secretKey.isBlank()) add("YOOKASSA_SECRET_KEY")
                if (publicBaseUrl.isNullOrBlank()) add("YOOKASSA_PUBLIC_BASE_URL")
            }
            if (missingKeys.isNotEmpty()) {
                return DisabledYooKassaPaymentProvider(
                    reason = "Missing YooKassa config: ${missingKeys.joinToString()}"
                )
            }

            return YooKassaPaymentProvider(
                config = YooKassaConfig(
                    shopId = shopId,
                    secretKey = secretKey,
                    apiBaseUrl = apiBaseUrl.trimEnd('/'),
                    publicBaseUrl = publicBaseUrl!!.trimEnd('/')
                ),
                json = json
            )
        }
    }
}

data class YooKassaConfig(
    val shopId: String,
    val secretKey: String,
    val apiBaseUrl: String,
    val publicBaseUrl: String
)

class YooKassaException(message: String, val statusCode: Int, val responseBody: String) : RuntimeException(
    "$message: HTTP $statusCode"
)

@Serializable
private data class YooKassaCreatePaymentRequest(
    val amount: YooKassaAmount,
    val capture: Boolean,
    val confirmation: YooKassaConfirmationRequest,
    val description: String,
    val metadata: YooKassaMetadata,
    val receipt: YooKassaReceiptRequest? = null
)

@Serializable
private data class YooKassaAmount(
    val value: String,
    val currency: String
)

@Serializable
private data class YooKassaConfirmationRequest(
    val type: String,
    @SerialName("return_url")
    val returnUrl: String
)

@Serializable
private data class YooKassaMetadata(
    @SerialName("local_payment_id")
    val localPaymentId: String,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("rental_id")
    val rentalId: String,
    @SerialName("payment_type")
    val paymentType: String
)

@Serializable
private data class YooKassaReceiptRequest(
    val customer: YooKassaReceiptCustomer,
    val items: List<YooKassaReceiptItem>,
    @SerialName("tax_system_code")
    val taxSystemCode: Int
)

@Serializable
private data class YooKassaReceiptCustomer(
    val email: String
)

@Serializable
private data class YooKassaReceiptItem(
    val description: String,
    val quantity: String,
    val amount: YooKassaAmount,
    @SerialName("vat_code")
    val vatCode: Int,
    @SerialName("payment_mode")
    val paymentMode: String,
    @SerialName("payment_subject")
    val paymentSubject: String
)

private fun ProviderReceipt.toYooKassaReceipt(amountRub: Int, description: String): YooKassaReceiptRequest {
    return YooKassaReceiptRequest(
        customer = YooKassaReceiptCustomer(email = customerEmail),
        items = listOf(
            YooKassaReceiptItem(
                description = description,
                quantity = "1.00",
                amount = YooKassaAmount(value = rubToDecimalString(amountRub), currency = "RUB"),
                vatCode = vatCode,
                paymentMode = paymentMode,
                paymentSubject = paymentSubject
            )
        ),
        taxSystemCode = taxSystemCode
    )
}

@Serializable
private data class YooKassaPaymentResponse(
    val id: String,
    val status: String,
    val amount: YooKassaAmount? = null,
    val confirmation: YooKassaConfirmationResponse? = null,
    val metadata: YooKassaMetadataResponse? = null,
    @SerialName("receipt_registration")
    val receiptRegistration: String? = null
) {
    fun toProviderInfo(): ProviderPaymentInfo {
        return ProviderPaymentInfo(
            providerPaymentId = id,
            status = mapStatus(status),
            amountRub = amount?.value?.let(::decimalStringToRub),
            confirmationUrl = confirmation?.confirmationUrl,
            localPaymentId = metadata?.localPaymentId,
            clientId = metadata?.clientId,
            rentalId = metadata?.rentalId,
            paymentType = metadata?.paymentType,
            receiptRegistration = receiptRegistration
        )
    }
}

@Serializable
private data class YooKassaConfirmationResponse(
    val type: String? = null,
    @SerialName("confirmation_url")
    val confirmationUrl: String? = null
)

@Serializable
private data class YooKassaMetadataResponse(
    @SerialName("local_payment_id")
    val localPaymentId: String? = null,
    @SerialName("client_id")
    val clientId: String? = null,
    @SerialName("rental_id")
    val rentalId: String? = null,
    @SerialName("payment_type")
    val paymentType: String? = null
)

fun mapStatus(status: String): PaymentStatus = when (status.lowercase()) {
    "pending", "waiting_for_capture" -> PaymentStatus.PENDING
    "succeeded" -> PaymentStatus.SUCCEEDED
    "canceled" -> PaymentStatus.CANCELED
    "failed" -> PaymentStatus.FAILED
    else -> PaymentStatus.PENDING
}

fun rubToDecimalString(amountRub: Int): String {
    return BigDecimal(amountRub).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
}

fun decimalStringToRub(value: String): Int {
    return BigDecimal(value).setScale(0, RoundingMode.DOWN).intValueExact()
}
