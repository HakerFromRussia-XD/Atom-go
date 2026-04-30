package com.atomgo.backend.infra

import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.UserSession
import java.util.UUID

class AuthService(private val store: InMemoryStore) {

    fun login(login: String, password: String): Pair<String, UserSession>? {
        val user = store.users.firstOrNull { it.login == login && it.password == password } ?: return null
        val token = UUID.randomUUID().toString()
        val session = UserSession(userId = user.id, role = user.role, clientId = user.clientId)
        store.sessions[token] = session
        return token to session
    }

    fun resolveSession(authorizationHeader: String?): UserSession? {
        val token = extractBearer(authorizationHeader) ?: return null
        return store.sessions[token]
    }

    private fun extractBearer(header: String?): String? {
        if (header == null) return null
        val prefix = "Bearer "
        if (!header.startsWith(prefix, ignoreCase = true)) return null
        return header.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
    }

    fun roleToApi(role: Role): String = when (role) {
        Role.ADMIN -> "admin"
        Role.CLIENT -> "client"
    }
}
