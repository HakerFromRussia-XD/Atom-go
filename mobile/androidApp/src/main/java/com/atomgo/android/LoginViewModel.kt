package com.atomgo.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atomgo.shared.api.AtomGoApiClient
import com.atomgo.shared.api.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val statusText: String = WAITING_STATUS,
    val isLoading: Boolean = false
) {
    companion object {
        const val WAITING_STATUS = "Статус: ожидание"
    }
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("atomgo_login", 0)
    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    private val _uiState = MutableStateFlow(
        LoginUiState(
            login = prefs.getString(KEY_LOGIN, "").orEmpty().takeIf { prefs.getBoolean(KEY_REMEMBER_ME, false) } ?: "",
            password = prefs.getString(KEY_PASSWORD, "").orEmpty().takeIf { prefs.getBoolean(KEY_REMEMBER_ME, false) } ?: "",
            rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
        )
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onLoginChanged(value: String) = _uiState.update { it.copy(login = value) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value) }

    fun setRememberMe(enabled: Boolean) {
        _uiState.update { it.copy(rememberMe = enabled) }
        prefs.edit().putBoolean(KEY_REMEMBER_ME, enabled).apply()
        if (!enabled) {
            prefs.edit().remove(KEY_LOGIN).remove(KEY_PASSWORD).apply()
        }
    }

    fun signIn(onAuthenticated: (AuthSession) -> Unit) {
        val state = _uiState.value
        val login = state.login.trim()
        val password = state.password
        if (login.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(statusText = "Статус: введите логин и пароль") }
            return
        }

        _uiState.update { it.copy(statusText = "Статус: выполняю вход...", isLoading = true) }
        viewModelScope.launch {
            try {
                val session = apiClient.login(login, password)
                if (_uiState.value.rememberMe) {
                    prefs.edit().putString(KEY_LOGIN, login).putString(KEY_PASSWORD, password).apply()
                }
                _uiState.update {
                    it.copy(
                        statusText = "Статус: вход выполнен, роль: ${session.role.name.lowercase()}\\nToken: ${session.accessToken.take(12)}...",
                        isLoading = false
                    )
                }
                onAuthenticated(AuthSession(session.accessToken, session.role))
            } catch (error: Exception) {
                _uiState.update { it.copy(statusText = "Статус: ошибка входа: ${error.message}", isLoading = false) }
            }
        }
    }

    fun resetForNextLogin() {
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
        _uiState.value = LoginUiState(
            login = if (rememberMe) prefs.getString(KEY_LOGIN, "").orEmpty() else "",
            password = if (rememberMe) prefs.getString(KEY_PASSWORD, "").orEmpty() else "",
            rememberMe = rememberMe
        )
    }

    override fun onCleared() {
        apiClient.close()
        super.onCleared()
    }

    companion object {
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
    }
}
