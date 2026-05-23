package com.atomgo.android.presentation.ui

import com.atomgo.shared.api.AdminBikeResponse
import java.time.LocalDate

internal const val AdminRentalEditEndBeforeStartMessage = "Дата окончания не может быть раньше даты начала"

internal data class AdminRentalEditInitialState(
    val clientId: String,
    val bikeId: String,
    val login: String,
    val password: String,
    val periodStart: String,
    val periodEnd: String,
    val videoUrl: String,
    val contractUrl: String,
    val comment: String
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
        comment = details.comment.orEmpty()
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
