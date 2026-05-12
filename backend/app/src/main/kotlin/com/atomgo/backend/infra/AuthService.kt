package com.atomgo.backend.infra

import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.UserSession
import java.util.UUID

class AuthService(private val store: InMemoryStore) {

    fun login(login: String, password: String): Pair<String, UserSession>? {
        val adminUser = store.users.firstOrNull {
            it.role == Role.ADMIN && it.login == login && it.password == password
        }
        if (adminUser != null) {
            val token = UUID.randomUUID().toString()
            val session = UserSession(userId = adminUser.id, role = adminUser.role, clientId = adminUser.clientId)
            store.sessions[token] = session
            return token to session
        }

        val rentalByCredentials = store.rentals.firstOrNull { rental ->
            rental.clientId.isNotBlank() &&
                rental.clientLogin == login &&
                rental.clientPassword == password
        }
        if (rentalByCredentials != null) {
            val token = UUID.randomUUID().toString()
            val session = UserSession(
                userId = rentalByCredentials.id,
                role = Role.CLIENT,
                clientId = rentalByCredentials.clientId,
                rentalId = rentalByCredentials.id
            )
            store.sessions[token] = session
            return token to session
        }

        val clientUser = store.users.firstOrNull {
            it.role == Role.CLIENT && it.login == login && it.password == password
        } ?: return null

        val activeOrLatestRental = store.rentals
            .asSequence()
            .filter { it.clientId == clientUser.clientId }
            .sortedByDescending { it.startDate }
            .firstOrNull()

        val token = UUID.randomUUID().toString()
        val session = UserSession(
            userId = clientUser.id,
            role = clientUser.role,
            clientId = clientUser.clientId,
            rentalId = activeOrLatestRental?.id
        )
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
