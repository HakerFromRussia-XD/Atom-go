package com.atomgo.android.domain.model

data class LoginRememberState(
    val rememberMe: Boolean,
    val login: String,
    val password: String
)
