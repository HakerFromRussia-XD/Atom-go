package com.atomgo.android.presentation.ui

import androidx.compose.ui.graphics.Color
import com.atomgo.android.AppDesign
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminClientSummaryResponse
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class BikeCatalogRuntimeSnapshot(
    val borderColor: Color,
    val subtitle: String
)

internal fun bikeCatalogRuntimeSnapshot(
    bike: AdminBikeResponse,
    rentals: List<AdminClientSummaryResponse>
): BikeCatalogRuntimeSnapshot {
    return bikeCatalogRuntimeSnapshots(rentals)[normalizeCatalogSearchText(bike.bikeModel)]
        ?: freeBikeCatalogRuntimeSnapshot()
}

internal fun bikeCatalogRuntimeSnapshots(
    rentals: List<AdminClientSummaryResponse>
): Map<String, BikeCatalogRuntimeSnapshot> {
    return rentals
        .asSequence()
        .filter { it.rentalIsActive }
        .groupBy { normalizeCatalogSearchText(it.bikeModel) }
        .mapValues { (_, activeRentals) -> activeBikeCatalogRuntimeSnapshot(activeRentals) }
}

internal fun freeBikeCatalogRuntimeSnapshot(): BikeCatalogRuntimeSnapshot {
    return BikeCatalogRuntimeSnapshot(
        borderColor = AppDesign.IdlePurple,
        subtitle = "-"
    )
}

private fun activeBikeCatalogRuntimeSnapshot(activeRentals: List<AdminClientSummaryResponse>): BikeCatalogRuntimeSnapshot {
    val hasSoonReturn = activeRentals.any { it.rentalPipelineStatus == "soon_return" }
    val borderColor = if (hasSoonReturn) AppDesign.WarningYellow else AppDesign.PaidGreen
    val activeRental = activeRentals.first()
    val clientName = activeRental.fullName.trim().ifEmpty { "Клиент" }
    val subtitle = if (activeRental.rentalPipelineStatus == "soon_return") {
        "$clientName · вернут в течении нед."
    } else {
        val paidUntil = shortPaidUntilText(activeRental.paidUntil)
        if (paidUntil != null) "$clientName · до $paidUntil" else "$clientName · долгосрочно"
    }

    return BikeCatalogRuntimeSnapshot(
        borderColor = borderColor,
        subtitle = subtitle
    )
}

internal fun clientCatalogSubtitle(client: AdminClientSummaryResponse): String {
    if (client.rentalIsActive) {
        val model = normalizeCatalogBikeModel(client.bikeModel)
        val paidUntil = shortPaidUntilText(client.paidUntil)
        return when {
            paidUntil != null && model.isNotEmpty() -> "$model · до $paidUntil"
            paidUntil != null -> "до $paidUntil"
            model.isNotEmpty() -> model
            else -> "Активная аренда"
        }
    }
    val model = normalizeCatalogBikeModel(client.bikeModel)
    return if (model.isEmpty()) "-" else model
}

internal fun clientCatalogSubtitleFromDetails(details: AdminClientDetailsResponse): String {
    val model = normalizeCatalogBikeModel(details.bikeModel)
    val paidUntil = shortPaidUntilText(details.paidUntil)
    return when {
        paidUntil != null && model.isNotEmpty() -> "$model · до $paidUntil"
        paidUntil != null -> "до $paidUntil"
        model.isNotEmpty() -> model
        else -> "—"
    }
}

internal fun clientTotalDebtRub(client: AdminClientSummaryResponse): Int {
    return client.debtRub.coerceAtLeast(0) + client.carriedDebtRub.coerceAtLeast(0)
}

internal fun normalizeCatalogBikeModel(rawValue: String): String {
    val value = rawValue.trim()
    return if (value.isEmpty() || value == "-") "" else value
}

internal fun normalizeCatalogSearchText(value: String): String = value.trim().lowercase()

internal fun shortPaidUntilText(paidUntilRaw: String?): String? {
    val value = paidUntilRaw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val parts = value.split("-")
    if (parts.size != 3) return null
    val day = parts[2].toIntOrNull() ?: return null
    val monthIndex = (parts[1].toIntOrNull() ?: return null) - 1
    val month = ruShortMonths.getOrNull(monthIndex) ?: return null
    return "$day $month"
}

internal fun formatRubAmount(value: Int): String {
    val raw = value.toString()
    val signOffset = if (raw.startsWith("-")) 1 else 0
    val digitCount = raw.length - signOffset
    if (digitCount <= 3) return raw

    val firstGroupSize = digitCount % 3
    return buildString(raw.length + digitCount / 3) {
        if (signOffset == 1) append('-')
        var index = signOffset
        if (firstGroupSize > 0) {
            append(raw, index, index + firstGroupSize)
            index += firstGroupSize
            if (index < raw.length) append(' ')
        }
        while (index < raw.length) {
            append(raw, index, index + 3)
            index += 3
            if (index < raw.length) append(' ')
        }
    }
}

internal val ruShortMonths = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

internal fun formatShortRuDate(value: String): String {
    return runCatching {
        val parts = value.trim().split("-")
        if (parts.size != 3) return value
        val day = parts[2].toInt()
        val month = ruShortMonths[parts[1].toInt() - 1]
        "${if (day < 10) "0$day" else "$day"} $month"
    }.getOrDefault(value)
}

internal fun formatLongRuDate(value: String): String {
    return runCatching {
        val parts = value.trim().split("-")
        if (parts.size != 3) return value
        val day = parts[2].toInt()
        val month = ruShortMonths[parts[1].toInt() - 1]
        val year = parts[0]
        "${if (day < 10) "0$day" else "$day"} $month $year"
    }.getOrDefault(value)
}

internal fun formatJournalDateLabel(value: String): String {
    val datePart = value.trim().take(10)
    if (datePart.count { it == '-' } == 2) {
        return formatShortRuDate(datePart)
    }
    return "—"
}

internal fun ledgerOperationLabel(type: String, paymentMethod: String? = null): String {
    if (type.trim().equals("payment", ignoreCase = true) && paymentMethod?.trim() == "cash") {
        return "Наличные"
    }
    return when (type.trim().lowercase()) {
        "payment" -> "Оплата"
        "adjustment" -> "Корректировка"
        "charge" -> "Начисление аренды"
        else -> type.trim().ifEmpty { "—" }
    }
}

internal fun rentStatus(item: AdminClientSummaryResponse): RentStatusPill {
    if (!item.rentalIsActive) {
        return RentStatusPill(
            title = "У меня",
            value = "—",
            color = AppDesign.DarkControl,
            widthDp = 108
        )
    }

    if (item.debtRub > 0) {
        return RentStatusPill(
            title = "Долг",
            value = money(item.debtRub),
            color = AppDesign.DangerRed,
            widthDp = 108
        )
    }

    return RentStatusPill(
        title = "Оплачено на",
        value = paidDaysText(item),
        color = AppDesign.PaidGreen,
        widthDp = 108
    )
}

internal fun avatarBorderColor(item: AdminClientSummaryResponse): Color {
    return when (item.rentalPipelineStatus.orEmpty().trim().lowercase()) {
        "in_stock", "mine" -> AppDesign.IdlePurple
        "soon_return" -> AppDesign.WarningYellow
        "long_term" -> AppDesign.PaidGreen
        else -> if (item.rentalIsActive) {
            AppDesign.PaidGreen
        } else {
            AppDesign.IdlePurple
        }
    }
}

internal fun paidDaysText(item: AdminClientSummaryResponse): String {
    val daysFromStatus = Regex("\\d+").find(item.statusText.lowercase())?.value?.toIntOrNull()
    if (daysFromStatus != null) {
        return dayWord(daysFromStatus)
    }
    val paidUntil = item.paidUntil?.trim().orEmpty()
    if (paidUntil.isNotEmpty()) {
        val daysFromDate = runCatching {
            ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(paidUntil)).coerceAtLeast(0).toInt()
        }.getOrNull()
        if (daysFromDate != null) {
            return dayWord(daysFromDate)
        }
    }
    return "—"
}

internal fun dayWord(days: Int): String {
    val mod10 = days % 10
    val mod100 = days % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "$days день"
        mod10 in 2..4 && mod100 !in 12..14 -> "$days дня"
        else -> "$days дней"
    }
}

internal fun signedRub(value: Int): String {
    return when {
        value > 0 -> "+${money(value)}"
        value < 0 -> "-${money(kotlin.math.abs(value))}"
        else -> money(0)
    }
}
