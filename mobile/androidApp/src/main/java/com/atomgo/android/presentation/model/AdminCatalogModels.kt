package com.atomgo.android.presentation.model

internal enum class AdminRentFilter {
    All,
    SoonReturn,
    Debtors,
    Mine
}

internal enum class AdminClientFilter {
    All,
    Debtors,
    Active
}

internal enum class AdminBikeFilter {
    All,
    Free,
    Rented
}

internal enum class AdminHomeTab {
    Rents,
    Clients,
    Bikes
}

internal data class AdminFilterCounters(
    val all: Int,
    val soonReturn: Int,
    val debtors: Int,
    val mine: Int
)

internal data class AdminClientFilterCounters(
    val all: Int,
    val debtors: Int,
    val active: Int
)

internal data class AdminBikeFilterCounters(
    val all: Int,
    val free: Int,
    val rented: Int
)

internal fun normalizedPipelineStatus(value: String?): String = value.orEmpty().trim().lowercase()
