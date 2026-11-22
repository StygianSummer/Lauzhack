package tea.server.me

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import tea.model.Profile
import tea.model.SignalsRequest
import tea.model.TeaJson
import tea.server.auth.requireUser
import tea.server.badRequest
import tea.server.db.ClientSignals
import tea.server.db.Users
import tea.server.db.newId
import tea.server.db.toOdt
import tea.server.services

/**
 * Device settings the server keeps (plan §9.2.9), with the key spellings it accepts.
 * Anything else in `json` is dropped before storage; values must be short primitives.
 */
val ALLOWED_SIGNAL_KEYS: Set<String> = setOf(
    "browser_family", "browser",
    "device_class",
    "touch", "pointer", "input",
    "locale",
    "timezone", "time_zone",
    "dark_mode", "prefers_dark", "color_scheme",
    "reduced_motion", "prefers_reduced_motion",
    "font_scale",
)

private const val MAX_PROFILE_JSON = 20_000

private fun String?.clean(max: Int): String? = this?.trim()?.take(max)?.takeIf { it.isNotEmpty() }

fun Route.profileRoutes() {
    get("/me/profile") {
        val s = call.services
        val session = call.requireUser()
        call.respond(s.db.tx { UserData.profile(UserData.user(session.userId)) })
    }

    put("/me/profile") {
        val s = call.services
        val session = call.requireUser()
        val incoming = call.receive<Profile>()
        if (TeaJson.encodeToString(Profile.serializer(), incoming).length > MAX_PROFILE_JSON) {
            throw badRequest("too_large", "The profile is too large.")
        }
        val now = s.clock.now()
        val saved = s.db.tx {
            val user = UserData.user(session.userId)
            val stored = UserData.profile(user)
            // email_provider is derived by the server from the verified address; the client cannot change it.
            val merged = incoming.copy(emailProvider = stored.emailProvider)
            UserData.saveProfile(session.userId, merged, now)
            Users.update({ Users.id eq session.userId }) {
                it[firstName] = merged.firstName.clean(100)
                it[lastName] = merged.lastName.clean(100)
                it[displayName] = merged.displayName.clean(100)
                it[language] = merged.language.clean(8) ?: user[Users.language]
                it[country] = merged.country.clean(16)
            }
            merged
        }
        call.respond(saved)
    }

    post("/me/signals") {
        val s = call.services
        val session = call.requireUser()
        val req = call.receive<SignalsRequest>()
        val kept = JsonObject(
            req.json.filter { (k, v) ->
                k in ALLOWED_SIGNAL_KEYS && v is JsonPrimitive && v.content.length <= 64
            },
        )
        val platform = req.platform.trim().lowercase().take(32).ifEmpty { "unknown" }
        val now = s.clock.now()
        s.db.tx {
            // Keep only the latest set: these settings only adapt the UI.
            ClientSignals.deleteWhere { ClientSignals.userId eq session.userId }
            ClientSignals.insert {
                it[id] = newId()
                it[userId] = session.userId
                it[ClientSignals.platform] = platform
                it[json] = TeaJson.encodeToString(JsonObject.serializer(), kept)
                it[createdAt] = now.toOdt()
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
