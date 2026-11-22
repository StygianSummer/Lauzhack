package tea.server.me

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.AttemptsResponse
import tea.model.Confidence
import tea.model.Context
import tea.model.StepKey
import tea.model.Support
import tea.model.TeaJson
import tea.server.auth.requireUser
import tea.server.badRequest
import tea.server.db.Attempts
import tea.server.db.newId
import tea.server.db.toKInstant
import tea.server.db.toOdt
import tea.server.services

const val MAX_BATCH = 1000

private val scaffoldSerializer = MapSerializer(StepKey.serializer(), Support.serializer())

/** Enum → its JSON name (e.g. Context.PRACTICE → "practice"). */
internal fun <E> E.wire(serializer: kotlinx.serialization.KSerializer<E>): String =
    TeaJson.encodeToJsonElement(serializer, this).jsonPrimitive.content

internal fun <E> decodeWire(serializer: kotlinx.serialization.KSerializer<E>, value: String): E =
    TeaJson.decodeFromJsonElement(serializer, JsonPrimitive(value))

/** Rebuilds the shared Attempt from a stored row (used by the leaderboard XP). */
fun attemptFromRow(row: ResultRow): Attempt = Attempt(
    clientId = row[Attempts.clientId],
    itemId = row[Attempts.itemId],
    context = decodeWire(Context.serializer(), row[Attempts.context]),
    answers = TeaJson.decodeFromString(AttemptAnswers.serializer(), row[Attempts.answersJson]),
    correct = row[Attempts.correct],
    confidence = row[Attempts.confidence]?.let { decodeWire(Confidence.serializer(), it) },
    scaffold = TeaJson.decodeFromString(scaffoldSerializer, row[Attempts.scaffoldJson]),
    hintsUsed = row[Attempts.hintsUsed],
    ms = row[Attempts.ms],
    createdAt = row[Attempts.createdAt].toKInstant(),
)

fun Route.attemptRoutes() {
    post("/me/attempts") {
        val s = call.services
        val session = call.requireUser()
        val batch = call.receive<List<Attempt>>()
        if (batch.size > MAX_BATCH) throw badRequest("too_many", "At most $MAX_BATCH attempts per request.")
        if (batch.any { it.clientId.isBlank() || it.clientId.length > 64 }) throw badRequest("invalid_client_id", "Every attempt needs a client_id.")
        val result = s.db.tx {
            val ids = batch.map { it.clientId }.distinct()
            val existing = Attempts.select(Attempts.clientId).where { Attempts.clientId inList ids }
                .map { it[Attempts.clientId] }.toMutableSet()
            var stored = 0
            var duplicates = 0
            for (a in batch) {
                if (!existing.add(a.clientId)) { duplicates++; continue }
                Attempts.insert {
                    it[id] = newId()
                    it[clientId] = a.clientId
                    it[userId] = session.userId
                    it[itemId] = a.itemId.take(128)
                    it[context] = a.context.wire(Context.serializer())
                    it[answersJson] = TeaJson.encodeToString(AttemptAnswers.serializer(), a.answers)
                    it[correct] = a.correct
                    it[confidence] = a.confidence?.wire(Confidence.serializer())
                    it[scaffoldJson] = TeaJson.encodeToString(scaffoldSerializer, a.scaffold)
                    it[hintsUsed] = a.hintsUsed
                    it[ms] = a.ms
                    it[createdAt] = a.createdAt.toOdt()
                }
                stored++
            }
            AttemptsResponse(stored, duplicates)
        }
        call.respond(result)
    }
}
