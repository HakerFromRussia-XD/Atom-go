package com.atomgo.backend.infra

import com.atomgo.backend.domain.AppUser
import com.atomgo.backend.domain.ClientAccount
import com.atomgo.backend.domain.Role
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgresStateStoreTest {

    @Test
    fun `structured postgres state should survive restart`() {
        val config = postgresConfig()
        assumeTrue(canConnect(config), "Skipping: PostgreSQL is not available for integration test")

        val schema = "atomgo_test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        createSchema(config, schema)

        try {
            val schemaUrl = withCurrentSchema(config.jdbcUrl, schema)
            val firstStore = PostgresStateStore.createForTests(
                jdbcUrl = schemaUrl,
                dbUser = config.user,
                dbPassword = config.password
            )

            val firstState = firstStore.loadOrInitialize(InMemoryStore.seed())
            val addedClientId = "client-persist-${UUID.randomUUID().toString().take(8)}"
            val addedLogin = "persist.${UUID.randomUUID().toString().take(6)}"
            firstState.clients += ClientAccount(
                id = addedClientId,
                fullName = "Persist Test Client",
                address = "Moscow, Test 1",
                passportData = "1111 222333",
                phones = mutableListOf()
            )
            firstState.users += AppUser(
                id = "user-persist-${UUID.randomUUID().toString().take(8)}",
                login = addedLogin,
                password = "secret123",
                role = Role.CLIENT,
                clientId = addedClientId
            )
            firstStore.save(firstState)

            val restartedStore = PostgresStateStore.createForTests(
                jdbcUrl = schemaUrl,
                dbUser = config.user,
                dbPassword = config.password
            )
            val restoredState = restartedStore.loadOrInitialize(InMemoryStore.seed())

            assertTrue(restoredState.clients.any { it.id == addedClientId })
            assertTrue(restoredState.users.any { it.clientId == addedClientId && it.login == addedLogin })

            DriverManager.getConnection(schemaUrl, config.user, config.password).use { connection ->
                connection.prepareStatement(
                    """
                    SELECT full_name
                    FROM clients_view
                    WHERE id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, addedClientId)
                    statement.executeQuery().use { rs ->
                        assertTrue(rs.next())
                        assertEquals("Persist Test Client", rs.getString("full_name"))
                    }
                }
            }
        } finally {
            dropSchema(config, schema)
        }
    }

    @Test
    fun `legacy payload without bikeId should migrate to normalized state`() {
        val config = postgresConfig()
        assumeTrue(canConnect(config), "Skipping: PostgreSQL is not available for integration test")

        val schema = "atomgo_test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        createSchema(config, schema)

        try {
            val schemaUrl = withCurrentSchema(config.jdbcUrl, schema)
            insertLegacyPayload(schemaUrl, config)

            val store = PostgresStateStore.createForTests(
                jdbcUrl = schemaUrl,
                dbUser = config.user,
                dbPassword = config.password
            )
            val loaded = store.loadOrInitialize(InMemoryStore.seed())

            val migratedRental = loaded.rentals.firstOrNull { it.id == "rental-legacy-001" }
            assertTrue(migratedRental != null)
            assertEquals("legacy-bike-rental-legacy-001", migratedRental?.bikeId)

            val migratedBike = loaded.bikes.firstOrNull { it.id == migratedRental?.bikeId }
            assertTrue(migratedBike != null)
            assertEquals("Monster Legacy", migratedBike?.model)
            assertEquals(3200, migratedBike?.weeklyRateRub)
        } finally {
            dropSchema(config, schema)
        }
    }

    private data class PostgresConfig(
        val jdbcUrl: String,
        val user: String,
        val password: String
    )

    private fun postgresConfig(): PostgresConfig {
        val rawUrl = System.getenv("ATOMGO_DB_URL")
            ?: System.getenv("DATABASE_URL")
            ?: "jdbc:postgresql://127.0.0.1:5432/atomgo"
        val normalizedUrl = when {
            rawUrl.startsWith("jdbc:") -> rawUrl
            rawUrl.startsWith("postgres://") -> "jdbc:${rawUrl.replaceFirst("postgres://", "postgresql://")}"
            rawUrl.startsWith("postgresql://") -> "jdbc:$rawUrl"
            else -> rawUrl
        }

        val user = System.getenv("ATOMGO_DB_USER")
            ?: System.getenv("PGUSER")
            ?: System.getProperty("user.name")
        val password = System.getenv("ATOMGO_DB_PASSWORD")
            ?: System.getenv("PGPASSWORD")
            ?: ""
        return PostgresConfig(jdbcUrl = normalizedUrl, user = user, password = password)
    }

    private fun canConnect(config: PostgresConfig): Boolean {
        return try {
            DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SELECT 1")
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun withCurrentSchema(jdbcUrl: String, schema: String): String {
        val separator = if (jdbcUrl.contains("?")) "&" else "?"
        return "$jdbcUrl${separator}currentSchema=$schema"
    }

    private fun createSchema(config: PostgresConfig, schema: String) {
        DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE SCHEMA IF NOT EXISTS \"$schema\"")
            }
        }
    }

    private fun insertLegacyPayload(schemaUrl: String, config: PostgresConfig) {
        val payload = """
            {
              "users": [
                {"id":"admin-001","login":"admin","password":"admin123","role":"ADMIN","clientId":null},
                {"id":"user-client-001","login":"client1","password":"client123","role":"CLIENT","clientId":"client-001"}
              ],
              "clients": [
                {
                  "id":"client-001",
                  "fullName":"Legacy Client",
                  "address":"Legacy Address",
                  "passportData":"1234 567890",
                  "phones": [],
                  "bikeModel":"Monster Legacy",
                  "bikeAvatarUrl":"https://example.com/bikes/legacy.png",
                  "weeklyRateRub":3200
                }
              ],
              "rentals": [
                {
                  "id":"rental-legacy-001",
                  "clientId":"client-001",
                  "startDate":"2026-05-01",
                  "endDate":null,
                  "bikeModel":"Monster Legacy",
                  "bikeAvatarUrl":"https://example.com/bikes/legacy.png",
                  "videoUrl":"https://youtube.com/watch?v=legacy",
                  "contractUrl":"https://drive.google.com/file/d/legacy/view",
                  "comment":"legacy rental payload"
                }
              ],
              "ledger": [],
              "payments": [],
              "sessions": {},
              "processedWebhookEvents": []
            }
        """.trimIndent()

        DriverManager.getConnection(schemaUrl, config.user, config.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS atomgo_app_state (
                        id SMALLINT PRIMARY KEY,
                        payload JSONB NOT NULL,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """.trimIndent()
                )
            }

            connection.prepareStatement(
                """
                INSERT INTO atomgo_app_state (id, payload, updated_at)
                VALUES (1, CAST(? AS JSONB), NOW())
                ON CONFLICT (id) DO UPDATE
                SET payload = EXCLUDED.payload,
                    updated_at = NOW()
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, payload)
                statement.executeUpdate()
            }
        }
    }

    private fun dropSchema(config: PostgresConfig, schema: String) {
        DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""DROP SCHEMA IF EXISTS "$schema" CASCADE""")
            }
        }
    }
}
