package tea.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import tea.model.TeaJson
import tea.server.Config
import tea.server.Crypto

/** Database handle: Hikari pool + Exposed. Schema is created/extended on start (create-missing). */
class Db private constructor(val database: Database, private val dataSource: HikariDataSource) : AutoCloseable {

    /** Runs a blocking JDBC transaction on the IO dispatcher. */
    suspend fun <T> tx(block: JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { transaction(database) { block() } }

    fun <T> txBlocking(block: JdbcTransaction.() -> T): T = transaction(database) { block() }

    fun ping(): Boolean = try {
        dataSource.connection.use { it.isValid(2) }
    } catch (e: Exception) {
        false
    }

    override fun close() = dataSource.close()

    companion object {
        private val log = LoggerFactory.getLogger(Db::class.java)

        fun connect(config: Config): Db {
            val url = config.databaseUrl.ifBlank {
                log.warn("DATABASE_URL is not set: using an in-memory H2 database (dev only, data is lost on restart)")
                "jdbc:h2:mem:tea-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
            }
            val hikari = HikariConfig().apply {
                jdbcUrl = url
                if (config.databaseUser.isNotBlank()) username = config.databaseUser
                if (config.databasePassword.isNotBlank()) password = config.databasePassword
                maximumPoolSize = 8
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                poolName = "tea-db"
            }
            val ds = HikariDataSource(hikari)
            val db = Db(Database.connect(ds), ds)
            db.txBlocking {
                @Suppress("DEPRECATION")
                SchemaUtils.createMissingTablesAndColumns(*allTables)
            }
            return db
        }
    }
}

/**
 * Time-ordered UUID (version 7) with a 12-bit per-millisecond counter, so ids sort in creation order
 * inside this process. Used as a tie-breaker when rows share a timestamp (e.g. events "newest first").
 */
fun newId(): String = UuidV7.next().toString()

private object UuidV7 {
    private var lastMs = 0L
    private var counter = 0

    @Synchronized
    fun next(): UUID {
        var ms = System.currentTimeMillis()
        if (ms <= lastMs) {
            ms = lastMs
            counter++
            if (counter > 0xFFF) { ms = lastMs + 1; counter = 0 }
        } else {
            counter = 0
        }
        lastMs = ms
        val msb = (ms shl 16) or (0x7L shl 12) or counter.toLong()
        val lsb = (Crypto.random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE // variant 10
        return UUID(msb, lsb)
    }
}

fun Instant.toOdt(): OffsetDateTime = OffsetDateTime.ofInstant(toJavaInstant(), ZoneOffset.UTC)
fun OffsetDateTime.toKInstant(): Instant = toInstant().toKotlinInstant()

/** Appends one event to the bus (call inside a transaction). Server-emitted events have no client id. */
fun emitEvent(
    userId: String,
    type: String,
    payload: JsonObject,
    at: Instant,
    clientId: String? = null,
) {
    Events.insert {
        it[id] = newId()
        it[Events.clientId] = clientId
        it[Events.userId] = userId
        it[Events.type] = type
        it[payloadJson] = TeaJson.encodeToString(JsonObject.serializer(), payload)
        it[createdAt] = at.toOdt()
    }
}
