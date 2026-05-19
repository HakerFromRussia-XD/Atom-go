package com.atomgo.android

import androidx.compose.runtime.Immutable
import com.atomgo.shared.api.UserRole

@Immutable
data class AuthSession(
    val accessToken: String,
    val role: UserRole
)

sealed interface AppRoute {
    data object Launching : AppRoute
    data object Login : AppRoute
    data class ClientHome(val session: AuthSession) : AppRoute
    data class AdminHome(val session: AuthSession) : AppRoute
}
