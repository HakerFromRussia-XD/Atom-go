package com.atomgo.backend.infra

import com.atomgo.backend.domain.AppUser
import com.atomgo.backend.domain.BikeAccount
import com.atomgo.backend.domain.ClientAccount
import com.atomgo.backend.domain.ClientPhone
import com.atomgo.backend.domain.LedgerEntry
import com.atomgo.backend.domain.LedgerType
import com.atomgo.backend.domain.PaymentRecord
import com.atomgo.backend.domain.PaymentStatus
import com.atomgo.backend.domain.PaymentType
import com.atomgo.backend.domain.RentalRecord
import com.atomgo.backend.domain.Role
import com.atomgo.backend.domain.UserSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

class PostgresStateStore private constructor(
    private val jdbcUrl: String,
    private val dbUser: String,
    private val dbPassword: String,
    private val json: Json
) {
    private val lock = Any()

    init {
        Class.forName("org.postgresql.Driver")
    }

    fun loadOrInitialize(seed: InMemoryStore): InMemoryStore = synchronized(lock) {
        withRetries("load backend state") {
            DriverManager.getConnection(jdbcUrl, dbUser, dbPassword).use { connection ->
                connection.autoCommit = false
                ensureSchema(connection)

                val state = when {
                    hasStructuredState(connection) -> readStructuredState(connection)
                    else -> {
                        val legacyPayload = readLegacyPayload(connection)
                        val restored = if (legacyPayload == null) {
                            seed
                        } else {
                            InMemoryStoreJsonMapper.decode(legacyPayload, json)
                        }
                        persistStructuredState(connection, restored)
                        upsertLegacyPayload(connection, InMemoryStoreJsonMapper.encode(restored, json))
                        restored
                    }
                }

                connection.commit()
                state
            }
        }
    }

    fun save(state: InMemoryStore) = synchronized(lock) {
        withRetries("save backend state") {
            DriverManager.getConnection(jdbcUrl, dbUser, dbPassword).use { connection ->
                connection.autoCommit = false
                ensureSchema(connection)
                persistStructuredState(connection, state)
                upsertLegacyPayload(connection, InMemoryStoreJsonMapper.encode(state, json))
                connection.commit()
            }
        }
    }

    private fun ensureSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_clients (
                    id TEXT PRIMARY KEY,
                    full_name TEXT NOT NULL,
                    address TEXT NOT NULL DEFAULT '',
                    passport_data TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_client_phones (
                    client_id TEXT NOT NULL REFERENCES atomgo_clients(id) ON DELETE CASCADE,
                    phone_order INT NOT NULL,
                    label TEXT NOT NULL,
                    number TEXT NOT NULL,
                    PRIMARY KEY (client_id, phone_order)
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_bikes (
                    id TEXT PRIMARY KEY,
                    photo_url TEXT,
                    model TEXT NOT NULL,
                    weekly_rate_rub INT NOT NULL CHECK (weekly_rate_rub > 0),
                    frame_serial_number TEXT NOT NULL,
                    motor_serial_number TEXT NOT NULL,
                    battery_serial_number_1 TEXT NOT NULL,
                    battery_serial_number_2 TEXT
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_rentals (
                    id TEXT PRIMARY KEY,
                    client_id TEXT NOT NULL REFERENCES atomgo_clients(id) ON DELETE CASCADE,
                    bike_id TEXT NOT NULL REFERENCES atomgo_bikes(id),
                    start_date DATE NOT NULL,
                    end_date DATE,
                    video_url TEXT,
                    contract_url TEXT,
                    comment TEXT
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_ledger_entries (
                    id TEXT PRIMARY KEY,
                    client_id TEXT NOT NULL REFERENCES atomgo_clients(id) ON DELETE CASCADE,
                    type TEXT NOT NULL,
                    direction SMALLINT NOT NULL CHECK (direction IN (-1, 1)),
                    amount_rub INT NOT NULL CHECK (amount_rub > 0),
                    created_at TIMESTAMPTZ NOT NULL,
                    note TEXT,
                    source_id TEXT
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_payments (
                    id TEXT PRIMARY KEY,
                    client_id TEXT NOT NULL REFERENCES atomgo_clients(id) ON DELETE CASCADE,
                    payment_type TEXT NOT NULL,
                    amount_rub INT NOT NULL CHECK (amount_rub > 0),
                    confirmation_url TEXT NOT NULL,
                    idempotence_key TEXT NOT NULL,
                    status TEXT NOT NULL,
                    provider_payment_id TEXT
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_users (
                    id TEXT PRIMARY KEY,
                    login TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL,
                    client_id TEXT REFERENCES atomgo_clients(id) ON DELETE SET NULL
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_sessions (
                    token TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    client_id TEXT
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS atomgo_processed_webhook_events (
                    event_id TEXT PRIMARY KEY
                )
                """.trimIndent()
            )
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

        ensureDebugViews(connection)
    }

    private fun ensureDebugViews(connection: Connection) {
        val dropStatements = listOf(
            "DROP VIEW IF EXISTS payments_view",
            "DROP VIEW IF EXISTS ledger_view",
            "DROP VIEW IF EXISTS rentals_view",
            "DROP VIEW IF EXISTS bikes_view",
            "DROP VIEW IF EXISTS clients_view",
            "DROP VIEW IF EXISTS user_credentials_view",
            "DROP VIEW IF EXISTS users_view"
        )

        val views = listOf(
            """
            CREATE OR REPLACE VIEW users_view AS
            SELECT
                1::smallint AS state_row_id,
                id,
                login,
                role,
                client_id
            FROM atomgo_users
            """.trimIndent(),
            """
            CREATE OR REPLACE VIEW user_credentials_view AS
            SELECT
                1::smallint AS state_row_id,
                u.id AS user_id,
                u.role AS role,
                u.login AS login,
                u.password AS password,
                u.client_id AS client_id,
                c.full_name AS full_name
            FROM atomgo_users u
            LEFT JOIN atomgo_clients c ON c.id = u.client_id
            """.trimIndent(),
            """
            CREATE OR REPLACE VIEW clients_view AS
            SELECT
                1::smallint AS state_row_id,
                c.id,
                c.full_name,
                c.address,
                c.passport_data,
                COALESCE(
                    jsonb_agg(
                        jsonb_build_object('label', p.label, 'number', p.number)
                        ORDER BY p.phone_order
                    ) FILTER (WHERE p.client_id IS NOT NULL),
                    '[]'::jsonb
                ) AS phones
            FROM atomgo_clients c
            LEFT JOIN atomgo_client_phones p ON p.client_id = c.id
            GROUP BY c.id, c.full_name, c.address, c.passport_data
            """.trimIndent(),
            """
            CREATE OR REPLACE VIEW bikes_view AS
            SELECT
                1::smallint AS state_row_id,
                id,
                photo_url,
                model,
                weekly_rate_rub,
                frame_serial_number,
                motor_serial_number,
                battery_serial_number_1,
                battery_serial_number_2
            FROM atomgo_bikes
            """.trimIndent(),
            """
            CREATE OR REPLACE VIEW rentals_view AS
            SELECT
                1::smallint AS state_row_id,
                id,
                client_id,
                bike_id,
                start_date,
                end_date,
                video_url,
                contract_url,
                comment
            FROM atomgo_rentals
            """.trimIndent(),
            """
            CREATE OR REPLACE VIEW ledger_view AS
            SELECT
                1::smallint AS state_row_id,
                id,
                client_id,
                type,
                direction,
                amount_rub,
                created_at,
                note,
                source_id
            FROM atomgo_ledger_entries
            """.trimIndent(),
            """
            CREATE OR REPLACE VIEW payments_view AS
            SELECT
                1::smallint AS state_row_id,
                id,
                client_id,
                payment_type,
                amount_rub,
                status,
                provider_payment_id,
                idempotence_key,
                confirmation_url
            FROM atomgo_payments
            """.trimIndent()
        )

        connection.createStatement().use { statement ->
            dropStatements.forEach { statement.execute(it) }
            views.forEach { statement.execute(it) }
        }
    }

    private fun hasStructuredState(connection: Connection): Boolean {
        connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM atomgo_users LIMIT 1)").use { statement ->
            statement.executeQuery().use { rs ->
                return rs.next() && rs.getBoolean(1)
            }
        }
    }

    private fun readLegacyPayload(connection: Connection): String? {
        connection.prepareStatement("SELECT to_regclass('public.atomgo_app_state') IS NOT NULL").use { statement ->
            statement.executeQuery().use { rs ->
                if (!rs.next() || !rs.getBoolean(1)) {
                    return null
                }
            }
        }

        connection.prepareStatement(
            "SELECT payload::text FROM atomgo_app_state WHERE id = 1 LIMIT 1"
        ).use { statement ->
            statement.executeQuery().use { rs ->
                return if (rs.next()) rs.getString(1) else null
            }
        }
    }

    private fun readStructuredState(connection: Connection): InMemoryStore {
        val clients = linkedMapOf<String, ClientAccount>()
        connection.prepareStatement(
            """
            SELECT id, full_name, address, passport_data
            FROM atomgo_clients
            ORDER BY id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    val client = ClientAccount(
                        id = rs.getString("id"),
                        fullName = rs.getString("full_name"),
                        address = rs.getString("address"),
                        passportData = rs.getString("passport_data"),
                        phones = mutableListOf()
                    )
                    clients[client.id] = client
                }
            }
        }

        connection.prepareStatement(
            """
            SELECT client_id, label, number
            FROM atomgo_client_phones
            ORDER BY client_id, phone_order
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    val clientId = rs.getString("client_id")
                    val client = clients[clientId] ?: continue
                    client.phones += ClientPhone(
                        label = rs.getString("label"),
                        number = rs.getString("number")
                    )
                }
            }
        }

        val bikes = mutableListOf<BikeAccount>()
        connection.prepareStatement(
            """
            SELECT
                id,
                photo_url,
                model,
                weekly_rate_rub,
                frame_serial_number,
                motor_serial_number,
                battery_serial_number_1,
                battery_serial_number_2
            FROM atomgo_bikes
            ORDER BY id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    bikes += BikeAccount(
                        id = rs.getString("id"),
                        photoUrl = rs.getString("photo_url"),
                        model = rs.getString("model"),
                        weeklyRateRub = rs.getInt("weekly_rate_rub"),
                        frameSerialNumber = rs.getString("frame_serial_number"),
                        motorSerialNumber = rs.getString("motor_serial_number"),
                        batterySerialNumber1 = rs.getString("battery_serial_number_1"),
                        batterySerialNumber2 = rs.getString("battery_serial_number_2")
                    )
                }
            }
        }

        val rentals = mutableListOf<RentalRecord>()
        connection.prepareStatement(
            """
            SELECT id, client_id, bike_id, start_date, end_date, video_url, contract_url, comment
            FROM atomgo_rentals
            ORDER BY start_date DESC, id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    rentals += RentalRecord(
                        id = rs.getString("id"),
                        clientId = rs.getString("client_id"),
                        bikeId = rs.getString("bike_id"),
                        startDate = rs.getDate("start_date").toLocalDate(),
                        endDate = rs.getDate("end_date")?.toLocalDate(),
                        videoUrl = rs.getString("video_url"),
                        contractUrl = rs.getString("contract_url"),
                        comment = rs.getString("comment")
                    )
                }
            }
        }

        val ledger = mutableListOf<LedgerEntry>()
        connection.prepareStatement(
            """
            SELECT id, client_id, type, direction, amount_rub, created_at, note, source_id
            FROM atomgo_ledger_entries
            ORDER BY created_at, id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    ledger += LedgerEntry(
                        id = rs.getString("id"),
                        clientId = rs.getString("client_id"),
                        type = enumValueOf<LedgerType>(rs.getString("type")),
                        direction = rs.getInt("direction"),
                        amountRub = rs.getInt("amount_rub"),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                        note = rs.getString("note"),
                        sourceId = rs.getString("source_id")
                    )
                }
            }
        }

        val payments = mutableListOf<PaymentRecord>()
        connection.prepareStatement(
            """
            SELECT id, client_id, payment_type, amount_rub, confirmation_url, idempotence_key, status, provider_payment_id
            FROM atomgo_payments
            ORDER BY id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    payments += PaymentRecord(
                        id = rs.getString("id"),
                        clientId = rs.getString("client_id"),
                        paymentType = enumValueOf<PaymentType>(rs.getString("payment_type")),
                        amountRub = rs.getInt("amount_rub"),
                        confirmationUrl = rs.getString("confirmation_url"),
                        idempotenceKey = rs.getString("idempotence_key"),
                        status = enumValueOf<PaymentStatus>(rs.getString("status")),
                        providerPaymentId = rs.getString("provider_payment_id")
                    )
                }
            }
        }

        val users = mutableListOf<AppUser>()
        connection.prepareStatement(
            """
            SELECT id, login, password, role, client_id
            FROM atomgo_users
            ORDER BY id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    users += AppUser(
                        id = rs.getString("id"),
                        login = rs.getString("login"),
                        password = rs.getString("password"),
                        role = enumValueOf<Role>(rs.getString("role")),
                        clientId = rs.getString("client_id")
                    )
                }
            }
        }

        val sessions = mutableMapOf<String, UserSession>()
        connection.prepareStatement(
            """
            SELECT token, user_id, role, client_id
            FROM atomgo_sessions
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    sessions[rs.getString("token")] = UserSession(
                        userId = rs.getString("user_id"),
                        role = enumValueOf<Role>(rs.getString("role")),
                        clientId = rs.getString("client_id")
                    )
                }
            }
        }

        val processedWebhookEvents = mutableSetOf<String>()
        connection.prepareStatement("SELECT event_id FROM atomgo_processed_webhook_events").use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    processedWebhookEvents += rs.getString("event_id")
                }
            }
        }

        return InMemoryStore(
            users = users,
            clients = clients.values.toMutableList(),
            bikes = bikes,
            rentals = rentals,
            ledger = ledger,
            payments = payments,
            sessions = sessions,
            processedWebhookEvents = processedWebhookEvents
        )
    }

    private fun persistStructuredState(connection: Connection, state: InMemoryStore) {
        clearStructuredState(connection)

        connection.prepareStatement(
            """
            INSERT INTO atomgo_clients (id, full_name, address, passport_data)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            state.clients.forEach { client ->
                statement.setString(1, client.id)
                statement.setString(2, client.fullName)
                statement.setString(3, client.address)
                statement.setString(4, client.passportData)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        connection.prepareStatement(
            """
            INSERT INTO atomgo_client_phones (client_id, phone_order, label, number)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            state.clients.forEach { client ->
                client.phones.forEachIndexed { index, phone ->
                    statement.setString(1, client.id)
                    statement.setInt(2, index)
                    statement.setString(3, phone.label)
                    statement.setString(4, phone.number)
                    statement.addBatch()
                }
            }
            statement.executeBatch()
        }

        connection.prepareStatement(
            """
            INSERT INTO atomgo_bikes (
                id,
                photo_url,
                model,
                weekly_rate_rub,
                frame_serial_number,
                motor_serial_number,
                battery_serial_number_1,
                battery_serial_number_2
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            state.bikes.forEach { bike ->
                statement.setString(1, bike.id)
                statement.setString(2, bike.photoUrl)
                statement.setString(3, bike.model)
                statement.setInt(4, bike.weeklyRateRub)
                statement.setString(5, bike.frameSerialNumber)
                statement.setString(6, bike.motorSerialNumber)
                statement.setString(7, bike.batterySerialNumber1)
                statement.setString(8, bike.batterySerialNumber2)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        connection.prepareStatement(
            """
            INSERT INTO atomgo_rentals (
                id,
                client_id,
                bike_id,
                start_date,
                end_date,
                video_url,
                contract_url,
                comment
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            state.rentals.forEach { rental ->
                statement.setString(1, rental.id)
                statement.setString(2, rental.clientId)
                statement.setString(3, rental.bikeId)
                statement.setObject(4, rental.startDate)
                statement.setObject(5, rental.endDate)
                statement.setString(6, rental.videoUrl)
                statement.setString(7, rental.contractUrl)
                statement.setString(8, rental.comment)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        connection.prepareStatement(
            """
            INSERT INTO atomgo_ledger_entries (
                id,
                client_id,
                type,
                direction,
                amount_rub,
                created_at,
                note,
                source_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            state.ledger.forEach { entry ->
                statement.setString(1, entry.id)
                statement.setString(2, entry.clientId)
                statement.setString(3, entry.type.name)
                statement.setInt(4, entry.direction)
                statement.setInt(5, entry.amountRub)
                statement.setTimestamp(6, Timestamp.from(entry.createdAt))
                statement.setString(7, entry.note)
                statement.setString(8, entry.sourceId)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        connection.prepareStatement(
            """
            INSERT INTO atomgo_payments (
                id,
                client_id,
                payment_type,
                amount_rub,
                confirmation_url,
                idempotence_key,
                status,
                provider_payment_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            state.payments.forEach { payment ->
                statement.setString(1, payment.id)
                statement.setString(2, payment.clientId)
                statement.setString(3, payment.paymentType.name)
                statement.setInt(4, payment.amountRub)
                statement.setString(5, payment.confirmationUrl)
                statement.setString(6, payment.idempotenceKey)
                statement.setString(7, payment.status.name)
                statement.setString(8, payment.providerPaymentId)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        connection.prepareStatement(
            """
            INSERT INTO atomgo_users (id, login, password, role, client_id)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            state.users.forEach { user ->
                statement.setString(1, user.id)
                statement.setString(2, user.login)
                statement.setString(3, user.password)
                statement.setString(4, user.role.name)
                statement.setString(5, user.clientId)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        connection.prepareStatement(
            """
            INSERT INTO atomgo_sessions (token, user_id, role, client_id)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            state.sessions.forEach { (token, session) ->
                statement.setString(1, token)
                statement.setString(2, session.userId)
                statement.setString(3, session.role.name)
                statement.setString(4, session.clientId)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        connection.prepareStatement(
            """
            INSERT INTO atomgo_processed_webhook_events (event_id)
            VALUES (?)
            """.trimIndent()
        ).use { statement ->
            state.processedWebhookEvents.forEach { eventId ->
                statement.setString(1, eventId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun clearStructuredState(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM atomgo_client_phones")
            statement.executeUpdate("DELETE FROM atomgo_rentals")
            statement.executeUpdate("DELETE FROM atomgo_ledger_entries")
            statement.executeUpdate("DELETE FROM atomgo_payments")
            statement.executeUpdate("DELETE FROM atomgo_sessions")
            statement.executeUpdate("DELETE FROM atomgo_processed_webhook_events")
            statement.executeUpdate("DELETE FROM atomgo_users")
            statement.executeUpdate("DELETE FROM atomgo_bikes")
            statement.executeUpdate("DELETE FROM atomgo_clients")
        }
    }

    private fun upsertLegacyPayload(connection: Connection, payload: String) {
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

    private inline fun <T> withRetries(action: String, block: () -> T): T {
        var lastError: Throwable? = null
        repeat(RETRY_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                if (attempt < RETRY_ATTEMPTS - 1) {
                    Thread.sleep(RETRY_DELAY_MS)
                }
            }
        }
        throw IllegalStateException("Failed to $action after $RETRY_ATTEMPTS attempts", lastError)
    }

    companion object {
        private const val RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 250L

        fun fromEnvironment(): PostgresStateStore {
            val urlRaw = System.getenv("ATOMGO_DB_URL")
                ?: System.getenv("DATABASE_URL")
                ?: "jdbc:postgresql://127.0.0.1:5432/atomgo"
            val user = System.getenv("ATOMGO_DB_USER")
                ?: System.getenv("PGUSER")
                ?: System.getProperty("user.name")
            val password = System.getenv("ATOMGO_DB_PASSWORD")
                ?: System.getenv("PGPASSWORD")
                ?: ""

            return create(jdbcUrl = urlRaw, dbUser = user, dbPassword = password)
        }

        internal fun createForTests(
            jdbcUrl: String,
            dbUser: String,
            dbPassword: String,
            json: Json = defaultJson()
        ): PostgresStateStore {
            return PostgresStateStore(
                jdbcUrl = normalizeJdbcUrl(jdbcUrl),
                dbUser = dbUser,
                dbPassword = dbPassword,
                json = json
            )
        }

        private fun normalizeJdbcUrl(rawUrl: String): String {
            if (rawUrl.startsWith("jdbc:")) return rawUrl
            if (rawUrl.startsWith("postgres://")) {
                return "jdbc:${rawUrl.replaceFirst("postgres://", "postgresql://")}" 
            }
            if (rawUrl.startsWith("postgresql://")) {
                return "jdbc:$rawUrl"
            }
            throw IllegalArgumentException("Unsupported DB url format: $rawUrl")
        }

        private fun create(jdbcUrl: String, dbUser: String, dbPassword: String): PostgresStateStore {
            return PostgresStateStore(
                jdbcUrl = normalizeJdbcUrl(jdbcUrl),
                dbUser = dbUser,
                dbPassword = dbPassword,
                json = defaultJson()
            )
        }

        private fun defaultJson(): Json {
            return Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }
        }
    }
}

private object InMemoryStoreJsonMapper {
    @Serializable
    private data class PersistedUser(
        val id: String,
        val login: String,
        val password: String,
        val role: String,
        val clientId: String? = null
    )

    @Serializable
    private data class PersistedClientPhone(
        val label: String,
        val number: String
    )

    @Serializable
    private data class PersistedClient(
        val id: String,
        val fullName: String,
        val address: String,
        val passportData: String,
        val phones: List<PersistedClientPhone>,
        val bikeModel: String? = null,
        val bikeAvatarUrl: String? = null,
        val weeklyRateRub: Int? = null
    )

    @Serializable
    private data class PersistedBike(
        val id: String,
        val photoUrl: String? = null,
        val model: String,
        val weeklyRateRub: Int,
        val frameSerialNumber: String,
        val motorSerialNumber: String,
        val batterySerialNumber1: String,
        val batterySerialNumber2: String? = null
    )

    @Serializable
    private data class PersistedRental(
        val id: String,
        val clientId: String,
        val bikeId: String? = null,
        val startDate: String,
        val endDate: String? = null,
        val bikeModel: String? = null,
        val bikeAvatarUrl: String? = null,
        val videoUrl: String? = null,
        val contractUrl: String? = null,
        val comment: String? = null
    )

    @Serializable
    private data class PersistedLedgerEntry(
        val id: String,
        val clientId: String,
        val type: String,
        val direction: Int,
        val amountRub: Int,
        val createdAt: String,
        val note: String? = null,
        val sourceId: String? = null
    )

    @Serializable
    private data class PersistedPayment(
        val id: String,
        val clientId: String,
        val paymentType: String,
        val amountRub: Int,
        val confirmationUrl: String,
        val idempotenceKey: String,
        val status: String,
        val providerPaymentId: String? = null
    )

    @Serializable
    private data class PersistedSession(
        val userId: String,
        val role: String,
        val clientId: String? = null
    )

    @Serializable
    private data class PersistedState(
        val users: List<PersistedUser>,
        val clients: List<PersistedClient>,
        val bikes: List<PersistedBike> = emptyList(),
        val rentals: List<PersistedRental>,
        val ledger: List<PersistedLedgerEntry>,
        val payments: List<PersistedPayment>,
        val sessions: Map<String, PersistedSession>,
        val processedWebhookEvents: Set<String>
    )

    fun encode(store: InMemoryStore, json: Json): String {
        val persisted = PersistedState(
            users = store.users.map {
                PersistedUser(
                    id = it.id,
                    login = it.login,
                    password = it.password,
                    role = it.role.name,
                    clientId = it.clientId
                )
            },
            clients = store.clients.map {
                PersistedClient(
                    id = it.id,
                    fullName = it.fullName,
                    address = it.address,
                    passportData = it.passportData,
                    phones = it.phones.map { phone ->
                        PersistedClientPhone(label = phone.label, number = phone.number)
                    }
                )
            },
            bikes = store.bikes.map {
                PersistedBike(
                    id = it.id,
                    photoUrl = it.photoUrl,
                    model = it.model,
                    weeklyRateRub = it.weeklyRateRub,
                    frameSerialNumber = it.frameSerialNumber,
                    motorSerialNumber = it.motorSerialNumber,
                    batterySerialNumber1 = it.batterySerialNumber1,
                    batterySerialNumber2 = it.batterySerialNumber2
                )
            },
            rentals = store.rentals.map {
                PersistedRental(
                    id = it.id,
                    clientId = it.clientId,
                    bikeId = it.bikeId,
                    startDate = it.startDate.toString(),
                    endDate = it.endDate?.toString(),
                    videoUrl = it.videoUrl,
                    contractUrl = it.contractUrl,
                    comment = it.comment
                )
            },
            ledger = store.ledger.map {
                PersistedLedgerEntry(
                    id = it.id,
                    clientId = it.clientId,
                    type = it.type.name,
                    direction = it.direction,
                    amountRub = it.amountRub,
                    createdAt = it.createdAt.toString(),
                    note = it.note,
                    sourceId = it.sourceId
                )
            },
            payments = store.payments.map {
                PersistedPayment(
                    id = it.id,
                    clientId = it.clientId,
                    paymentType = it.paymentType.name,
                    amountRub = it.amountRub,
                    confirmationUrl = it.confirmationUrl,
                    idempotenceKey = it.idempotenceKey,
                    status = it.status.name,
                    providerPaymentId = it.providerPaymentId
                )
            },
            sessions = store.sessions.mapValues { (_, value) ->
                PersistedSession(
                    userId = value.userId,
                    role = value.role.name,
                    clientId = value.clientId
                )
            },
            processedWebhookEvents = store.processedWebhookEvents.toSet()
        )
        return json.encodeToString(persisted)
    }

    fun decode(payload: String, json: Json): InMemoryStore {
        val persisted = json.decodeFromString<PersistedState>(payload)
        val legacyRateByClientId = persisted.clients.associate { it.id to (it.weeklyRateRub ?: 3000) }
        val legacyModelByClientId = persisted.clients.associate { it.id to it.bikeModel }
        val legacyPhotoByClientId = persisted.clients.associate { it.id to it.bikeAvatarUrl }

        val bikesById = linkedMapOf<String, BikeAccount>()
        persisted.bikes.forEach { bike ->
            bikesById[bike.id] = BikeAccount(
                id = bike.id,
                photoUrl = bike.photoUrl,
                model = bike.model,
                weeklyRateRub = bike.weeklyRateRub,
                frameSerialNumber = bike.frameSerialNumber,
                motorSerialNumber = bike.motorSerialNumber,
                batterySerialNumber1 = bike.batterySerialNumber1,
                batterySerialNumber2 = bike.batterySerialNumber2
            )
        }

        fun ensureLegacyBike(
            bikeId: String,
            clientId: String,
            rentalModel: String?,
            rentalPhoto: String?
        ) {
            if (bikesById.containsKey(bikeId)) return

            val model = rentalModel
                ?: legacyModelByClientId[clientId]
                ?: "Legacy Bike"
            val photoUrl = rentalPhoto ?: legacyPhotoByClientId[clientId]
            val weeklyRate = (legacyRateByClientId[clientId] ?: 3000).coerceAtLeast(1)

            bikesById[bikeId] = BikeAccount(
                id = bikeId,
                photoUrl = photoUrl,
                model = model,
                weeklyRateRub = weeklyRate,
                frameSerialNumber = "$bikeId-frame",
                motorSerialNumber = "$bikeId-motor",
                batterySerialNumber1 = "$bikeId-battery-1",
                batterySerialNumber2 = null
            )
        }

        val rentals = persisted.rentals.map { rental ->
            val resolvedBikeId = rental.bikeId?.takeIf { it.isNotBlank() } ?: "legacy-bike-${rental.id}"
            ensureLegacyBike(
                bikeId = resolvedBikeId,
                clientId = rental.clientId,
                rentalModel = rental.bikeModel,
                rentalPhoto = rental.bikeAvatarUrl
            )
            RentalRecord(
                id = rental.id,
                clientId = rental.clientId,
                bikeId = resolvedBikeId,
                startDate = LocalDate.parse(rental.startDate),
                endDate = rental.endDate?.let(LocalDate::parse),
                videoUrl = rental.videoUrl,
                contractUrl = rental.contractUrl,
                comment = rental.comment
            )
        }.toMutableList()

        return InMemoryStore(
            users = persisted.users.map {
                AppUser(
                    id = it.id,
                    login = it.login,
                    password = it.password,
                    role = enumValueOf<Role>(it.role),
                    clientId = it.clientId
                )
            }.toMutableList(),
            clients = persisted.clients.map {
                ClientAccount(
                    id = it.id,
                    fullName = it.fullName,
                    address = it.address,
                    passportData = it.passportData,
                    phones = it.phones.map { phone ->
                        ClientPhone(label = phone.label, number = phone.number)
                    }.toMutableList()
                )
            }.toMutableList(),
            bikes = bikesById.values.toMutableList(),
            rentals = rentals,
            ledger = persisted.ledger.map {
                LedgerEntry(
                    id = it.id,
                    clientId = it.clientId,
                    type = enumValueOf<LedgerType>(it.type),
                    direction = it.direction,
                    amountRub = it.amountRub,
                    createdAt = Instant.parse(it.createdAt),
                    note = it.note,
                    sourceId = it.sourceId
                )
            }.toMutableList(),
            payments = persisted.payments.map {
                PaymentRecord(
                    id = it.id,
                    clientId = it.clientId,
                    paymentType = enumValueOf<PaymentType>(it.paymentType),
                    amountRub = it.amountRub,
                    confirmationUrl = it.confirmationUrl,
                    idempotenceKey = it.idempotenceKey,
                    status = enumValueOf<PaymentStatus>(it.status),
                    providerPaymentId = it.providerPaymentId
                )
            }.toMutableList(),
            sessions = persisted.sessions.mapValues { (_, value) ->
                UserSession(
                    userId = value.userId,
                    role = enumValueOf<Role>(value.role),
                    clientId = value.clientId
                )
            }.toMutableMap(),
            processedWebhookEvents = persisted.processedWebhookEvents.toMutableSet()
        )
    }
}
