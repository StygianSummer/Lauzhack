package tea.server.me

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import tea.model.Profile
import tea.model.TeaJson
import tea.server.db.Profiles
import tea.server.db.Users
import tea.server.db.toOdt
import tea.server.unauthorized

/** Shared user/profile helpers (all run inside a transaction). */
object UserData {
    fun user(userId: String): ResultRow =
        Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: throw unauthorized()

    /** Stored profile, or one built from the users row when none was saved yet. */
    fun profile(user: ResultRow): Profile {
        val json = Profiles.selectAll().where { Profiles.userId eq user[Users.id] }.singleOrNull()?.get(Profiles.json)
        val stored = json?.let { runCatching { TeaJson.decodeFromString(Profile.serializer(), it) }.getOrNull() }
        return stored ?: Profile(
            language = user[Users.language],
            country = user[Users.country],
            firstName = user[Users.firstName],
            lastName = user[Users.lastName],
            displayName = user[Users.displayName],
        )
    }

    fun saveProfile(userId: String, profile: Profile, now: Instant) {
        val json = TeaJson.encodeToString(Profile.serializer(), profile)
        val updated = Profiles.update({ Profiles.userId eq userId }) {
            it[Profiles.json] = json
            it[updatedAt] = now.toOdt()
        }
        if (updated == 0) {
            Profiles.insert {
                it[Profiles.userId] = userId
                it[Profiles.json] = json
                it[updatedAt] = now.toOdt()
            }
        }
    }

    /** "First L." from the names, else "Learner" (leaderboard fallback). */
    fun displayName(user: ResultRow): String {
        user[Users.displayName]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val first = user[Users.firstName]?.trim()?.takeIf { it.isNotEmpty() } ?: return "Learner"
        val last = user[Users.lastName]?.trim()?.firstOrNull()
        return if (last != null) "$first ${last.uppercaseChar()}." else first
    }
}
