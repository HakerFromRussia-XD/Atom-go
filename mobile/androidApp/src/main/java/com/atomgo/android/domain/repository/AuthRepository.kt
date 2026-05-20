package com.atomgo.android.domain.repository

import com.atomgo.android.domain.model.AuthorizedSession
import com.atomgo.android.domain.model.LoginRememberState

interface AuthRepository {
    suspend fun login(login: String, password: String): AuthorizedSession
    fun readRememberState(): LoginRememberState
    fun setRememberMe(enabled: Boolean)
    fun saveCredentials(login: String, password: String)
    fun clearCredentials()
}
