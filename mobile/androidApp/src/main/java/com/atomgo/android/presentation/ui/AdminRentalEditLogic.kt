package com.atomgo.android.presentation.ui

import com.atomgo.shared.api.AdminBikeResponse
import java.time.LocalDate

internal const val AdminRentalEditEndBeforeStartMessage = "Дата окончания не может быть раньше даты начала"

internal data class AdminRentalPaymentDayOption(
    val value: Int,
    val label: String
)

internal val AdminRentalPaymentDayOptions = listOf(
    AdminRentalPaymentDayOption(1, "пн"),
    AdminRentalPaymentDayOption(2, "вт"),
    AdminRentalPaymentDayOption(3, "ср"),
    AdminRentalPaymentDayOption(4, "чт"),
    AdminRentalPaymentDayOption(5, "пт"),
    AdminRentalPaymentDayOption(6, "сб"),
    AdminRentalPaymentDayOption(7, "вс")
)

internal data class AdminRentalEditInitialState(
    val clientId: String,
    val bikeId: String,
    val login: String,
    val password: String,
    val periodStart: String,
    val periodEnd: String,
    val videoUrl: String,
    val contractUrl: String,
    val comment: String,
    val paymentDay: Int
)

internal fun adminRentalEditInitialState(
    details: AdminRentalPreview,
    bikes: List<AdminBikeResponse>
): AdminRentalEditInitialState {
    return AdminRentalEditInitialState(
        clientId = details.clientId.trim(),
        bikeId = resolveRentalEditBikeId(details, bikes),
        login = details.clientLogin.orEmpty(),
        password = details.clientPassword.orEmpty(),
        periodStart = details.periodStart,
        periodEnd = details.periodEnd.orEmpty(),
        videoUrl = details.videoUrl.orEmpty(),
        contractUrl = details.contractUrl.orEmpty(),
        comment = details.comment.orEmpty(),
        paymentDay = normalizedRentalPaymentDay(details.paymentDay, details.periodStart)
    )
}

internal fun adminRentalEditValidationError(periodStart: String, periodEnd: String): String? {
    val start = parseRentalEditDate(periodStart.trim()) ?: return null
    val end = parseRentalEditDate(periodEnd.trim()) ?: return null
    return if (end.isBefore(start)) AdminRentalEditEndBeforeStartMessage else null
}

private fun resolveRentalEditBikeId(
    details: AdminRentalPreview,
    bikes: List<AdminBikeResponse>
): String {
    val explicitBikeId = details.bikeId.trim()
    if (explicitBikeId.isNotEmpty()) return explicitBikeId

    val bikeModel = details.bikeModel.trim()
    if (bikeModel.isEmpty()) return ""

    return bikes.firstOrNull { it.bikeModel.trim() == bikeModel }?.bikeId
        ?: bikes.firstOrNull { it.bikeModel.trim().equals(bikeModel, ignoreCase = true) }?.bikeId
        ?: ""
}

private fun parseRentalEditDate(value: String): LocalDate? {
    if (value.isEmpty()) return null
    return runCatching { LocalDate.parse(value) }.getOrNull()
}

internal fun normalizedRentalPaymentDay(paymentDay: Int, periodStart: String): Int {
    if (paymentDay in 1..7) return paymentDay
    return parseRentalEditDate(periodStart)?.dayOfWeek?.value ?: LocalDate.now().dayOfWeek.value
}

internal fun todayRentalPaymentDay(): Int = LocalDate.now().dayOfWeek.value
