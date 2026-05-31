package com.atomgo.backend.infra

import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.UserSession
import java.util.UUID

class AuthService(private val store: InMemoryStore) {

    private fun isVisibleClient(clientId: String?): Boolean {
        if (clientId.isNullOrBlank()) return false
        return store.clients.any { it.id == clientId && it.deletedAt == null }
    }

    private fun isVisibleBike(bikeId: String?): Boolean {
        if (bikeId.isNullOrBlank()) return false
        return store.bikes.any { it.id == bikeId && it.deletedAt == null }
    }

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

        val rentalByCredentials = store.clientRentals.firstOrNull { rental ->
            rental.deletedAt == null &&
                rental.clientId.isNotBlank() &&
                rental.clientLogin == login &&
                rental.clientPassword == password &&
                isVisibleClient(rental.clientId) &&
                isVisibleBike(rental.bikeId)
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
            it.role == Role.CLIENT &&
                it.login == login &&
                it.password == password &&
                isVisibleClient(it.clientId)
        } ?: return null

        val activeOrLatestClientRental = store.clientRentals
            .asSequence()
            .filter { it.clientId == clientUser.clientId }
            .filter { it.deletedAt == null }
            .filter { isVisibleBike(it.bikeId) }
            .sortedByDescending { it.startDate }
            .firstOrNull() ?: return null

        val token = UUID.randomUUID().toString()
        val session = UserSession(
            userId = clientUser.id,
            role = clientUser.role,
            clientId = clientUser.clientId,
            rentalId = activeOrLatestClientRental.id
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
