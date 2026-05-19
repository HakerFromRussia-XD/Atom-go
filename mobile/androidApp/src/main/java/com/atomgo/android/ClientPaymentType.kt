package com.atomgo.android

enum class ClientPaymentType(
    val apiValue: String,
    val title: String
) {
    Day("day", "1 день"),
    Week("week", "1 неделя"),
    TwoWeeks("two_weeks", "2 недели"),
    Month("month", "1 месяц"),
    DebtExact("debt_exact", "Ровно долг")
}
