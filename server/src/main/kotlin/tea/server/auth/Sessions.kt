package tea.server.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tea.server.Crypto
import tea.server.db.Sessions
import tea.server.db.newId
import tea.server.db.toOdt
import tea.server.services
import tea.server.unauthorized

data class UserSession(val userId: String, val sessionId: String)

/** Creates a session and returns the raw bearer token (only its SHA-256 is stored). */
fun JdbcTransaction.createSession(userId: String, now: Instant): String {
    val token = Crypto.newToken()
    Sessions.insert {
        it[id] = newId()
        it[Sessions.userId] = userId
        it[tokenHash] = Crypto.sha256Hex(token)
        it[createdAt] = now.toOdt()
        it[lastSeenAt] = now.toOdt()
    }
    return token
}

private fun ApplicationCall.bearerToken(): String? {
    val h = request.headers[HttpHeaders.Authorization] ?: return null
    if (!h.startsWith("Bearer ", ignoreCase = true)) return null
    return h.substring(7).trim().takeIf { it.isNotEmpty() }
}

/** Session from `Authorization: Bearer <token>`, or null. Updates last_seen_at. */
suspend fun ApplicationCall.optionalUser(): UserSession? {
    val token = bearerToken() ?: return null
    val s = services
    val hash = Crypto.sha256Hex(token)
    val now = s.clock.now()
    return s.db.tx {
        val row = Sessions.selectAll()
            .where { (Sessions.tokenHash eq hash) and Sessions.revokedAt.isNull() }
            .singleOrNull() ?: return@tx null
        val id = row[Sessions.id]
        Sessions.update({ Sessions.id eq id }) { it[lastSeenAt] = now.toOdt() }
        UserSession(row[Sessions.userId], id)
    }
}

suspend fun ApplicationCall.requireUser(): UserSession = optionalUser() ?: throw unauthorized()
