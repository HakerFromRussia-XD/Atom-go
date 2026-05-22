package com.atomgo.android.data.repository

import android.app.Application
import com.atomgo.android.BackendConfig
import com.atomgo.android.domain.model.AuthorizedSession
import com.atomgo.android.domain.model.LoginRememberState
import com.atomgo.android.domain.repository.AuthRepository
import com.atomgo.shared.api.AtomGoApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultAuthRepository(
    application: Application
) : AuthRepository {
    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)
    private val apiClient = AtomGoApiClient(BackendConfig.BASE_URL)

    override suspend fun login(login: String, password: String): AuthorizedSession {
        val session = withContext(Dispatchers.IO) {
            apiClient.login(login, password)
        }
        return AuthorizedSession(accessToken = session.accessToken, role = session.role)
    }

    override fun readRememberState(): LoginRememberState {
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
        val login = prefs.getString(KEY_LOGIN, "").orEmpty().trim().ifBlank { DEFAULT_LOGIN }
        val password = prefs.getString(KEY_PASSWORD, "").orEmpty().ifBlank { DEFAULT_PASSWORD }
        return LoginRememberState(rememberMe = rememberMe, login = login, password = password)
    }

    override fun setRememberMe(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_ME, enabled).apply()
        if (!enabled) {
            clearCredentials()
        }
    }

    override fun saveCredentials(login: String, password: String) {
        prefs.edit().putString(KEY_LOGIN, login).putString(KEY_PASSWORD, password).apply()
    }

    override fun clearCredentials() {
        prefs.edit().remove(KEY_LOGIN).remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val PREFS_NAME = "atomgo_login"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
        private const val DEFAULT_LOGIN = "admin"
        private const val DEFAULT_PASSWORD = "admin123"
    }
}
