package com.atomgo.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.atomgo.android.presentation.model.AppRoute
import com.atomgo.android.presentation.model.AuthSession
import com.atomgo.shared.api.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel : ViewModel() {
    private val _route = MutableStateFlow<AppRoute>(AppRoute.Login)
    val route: StateFlow<AppRoute> = _route.asStateFlow()

    fun onAuthenticated(session: AuthSession) {
        _route.value = when (session.role) {
            UserRole.CLIENT -> AppRoute.ClientHome(session)
            UserRole.ADMIN -> AppRoute.AdminHome(session)
        }
    }

    fun logout(resetLogin: () -> Unit) {
        _route.value = AppRoute.Login
        resetLogin()
    }
}
