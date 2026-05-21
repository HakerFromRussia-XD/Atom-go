package com.atomgo.android.domain.usecase

import com.atomgo.android.domain.model.AuthorizedSession
import com.atomgo.android.domain.model.LoginRememberState
import com.atomgo.android.domain.repository.AuthRepository

class AuthUseCases(
    private val authRepository: AuthRepository
) {
    suspend fun login(login: String, password: String): AuthorizedSession {
        return authRepository.login(login = login, password = password)
    }

    fun readRememberState(): LoginRememberState {
        return authRepository.readRememberState()
    }

    fun setRememberMe(enabled: Boolean) {
        authRepository.setRememberMe(enabled)
    }

    fun saveCredentials(login: String, password: String) {
        authRepository.saveCredentials(login = login, password = password)
    }

    fun clearCredentials() {
        authRepository.clearCredentials()
    }
}
