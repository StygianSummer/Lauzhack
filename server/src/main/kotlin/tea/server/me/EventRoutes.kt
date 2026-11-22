package tea.server.me

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tea.model.AppEvent
import tea.model.EventTypes
import tea.model.StoredEvent
import tea.model.TERMS_VERSION
import tea.model.TeaJson
import tea.server.auth.requireUser
import tea.server.badRequest
import tea.server.db.Events
import tea.server.db.Users
import tea.server.db.emitEvent
import tea.server.db.toKInstant
import tea.server.db.toOdt
import tea.server.services

private val TYPE = Regex("^[a-z][a-z0-9_]{0,63}$")
private const val MAX_PAYLOAD = 20_000

/** String field of an event payload, or null. */
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Has this user already got an event of [type] whose payload has lesson_id == [lessonId]? */
fun hasLessonEvent(userId: String, type: String, lessonId: String): Boolean =
    Events.select(Events.payloadJson).where { (Events.userId eq userId) and (Events.type eq type) }
        .any { row ->
            runCatching { TeaJson.decodeFromString(JsonObject.serializer(), row[Events.payloadJson]) }
                .getOrNull()?.str("lesson_id") == lessonId
        }

/** Stores client events; returns how many were stored (the rest were duplicates). Call inside a transaction. */
fun storeClientEvents(userId: String, batch: List<AppEvent>, now: Instant): Int {
    val ids = batch.map { it.clientId }.distinct()
    val seen = Events.select(Events.clientId).where { Events.clientId inList ids }
        .mapNotNull { it[Events.clientId] }.toMutableSet()
    var stored = 0
    for (e in batch) {
        if (!seen.add(e.clientId)) continue
        if (e.type == EventTypes.PRACTICE_EMAIL_OPTED_IN) {
            val lesson = e.payload.str("lesson_id") ?: ""
            if (hasLessonEvent(userId, e.type, lesson)) continue
        }
        // A client clock in the future is clamped to now.
        val at = if (e.createdAt > now) now else e.createdAt
        emitEvent(userId, e.type, e.payload, at, clientId = e.clientId)
        stored++
        if (e.type == EventTypes.TERMS_ACCEPTED) {
            val version = e.payload.str("version")?.take(32) ?: TERMS_VERSION
            Users.update({ Users.id eq userId }) {
                it[termsAcceptedAt] = at.toOdt()
                it[termsVersion] = version
            }
        }
    }
    return stored
}

fun Route.eventRoutes() {
    post("/me/events") {
        val s = call.services
        val session = call.requireUser()
        val batch = call.receive<List<AppEvent>>()
        if (batch.size > MAX_BATCH) throw badRequest("too_many", "At most $MAX_BATCH events per request.")
        for (e in batch) {
            if (e.type.startsWith(EventTypes.FIELD_PREFIX) && e.type != EventTypes.FIELD_GOTCHA) {
                throw badRequest("forbidden_event_type", "Events starting with field_ are emitted by the server only.")
            }
            if (e.type == EventTypes.FIELD_GOTCHA) {
                val answer = e.payload.str("answer")
                val reason = e.payload.str("reason")
                if (e.payload.str("send_id").isNullOrBlank() ||
                    e.payload.str("lesson_id").isNullOrBlank() ||
                    answer !in setOf("yes", "no") ||
                    reason != null && reason !in setOf("who", "emotion", "ask", "verify")
                ) {
                    throw badRequest("invalid_field_gotcha", "Gotcha events need a send, lesson, and valid answer.")
                }
            }
            if (!TYPE.matches(e.type)) throw badRequest("invalid_event_type", "Event types are lowercase snake_case.")
            if (e.clientId.isBlank() || e.clientId.length > 64) throw badRequest("invalid_client_id", "Every event needs a client_id.")
            if (e.payload.toString().length > MAX_PAYLOAD) throw badRequest("too_large", "Event payload is too large.")
        }
        val now = s.clock.now()
        s.db.tx { storeClientEvents(session.userId, batch, now) }
        call.respond(HttpStatusCode.NoContent)
    }

    /** `?type=field_*` matches by prefix; `?type=x` exactly; no type = all. Newest first, max 500. */
    get("/me/events") {
        val s = call.services
        val session = call.requireUser()
        val type = call.request.queryParameters["type"]?.trim()?.takeIf { it.isNotEmpty() }
        val match: (String) -> Boolean = when {
            type == null -> { _ -> true }
            type.endsWith("*") -> type.dropLast(1).let { prefix -> { t: String -> t.startsWith(prefix) } }
            else -> { t: String -> t == type }
        }
        val events = s.db.tx {
            Events.selectAll().where { Events.userId eq session.userId }
                .orderBy(Events.createdAt to SortOrder.DESC, Events.id to SortOrder.DESC)
                .filter { match(it[Events.type]) }
                .take(500)
                .map {
                    StoredEvent(
                        type = it[Events.type],
                        payload = TeaJson.decodeFromString(JsonObject.serializer(), it[Events.payloadJson]),
                        createdAt = it[Events.createdAt].toKInstant(),
                    )
                }
        }
        call.respond(events)
    }
}
