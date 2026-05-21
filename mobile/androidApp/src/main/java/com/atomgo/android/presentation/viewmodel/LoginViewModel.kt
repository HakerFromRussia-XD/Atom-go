package com.atomgo.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomgo.android.domain.usecase.AuthUseCases
import com.atomgo.android.presentation.model.AuthSession
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

class LoginViewModel(
    private val authUseCases: AuthUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onLoginChanged(value: String) = _uiState.update { it.copy(login = value) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value) }

    fun setRememberMe(enabled: Boolean) {
        _uiState.update { it.copy(rememberMe = enabled) }
        authUseCases.setRememberMe(enabled)
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
                val session = authUseCases.login(login = login, password = password)
                if (_uiState.value.rememberMe) {
                    authUseCases.saveCredentials(login = login, password = password)
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
        _uiState.value = initialState()
    }

    private fun initialState(): LoginUiState {
        val rememberState = authUseCases.readRememberState()
        return LoginUiState(
            login = rememberState.login,
            password = rememberState.password,
            rememberMe = rememberState.rememberMe
        )
    }
}
