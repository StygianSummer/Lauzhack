package tea.server.me

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import tea.model.SettingsRequest
import tea.model.SettingsResponse
import tea.server.auth.requireUser
import tea.server.badRequest
import tea.server.db.Users
import tea.server.db.toOdt
import tea.server.field.cancelPendingSends
import tea.server.field.hasPendingSend
import tea.server.field.practiceEmailsStatus
import tea.server.services

private fun settingsOf(userId: String): SettingsResponse {
    val user = UserData.user(userId)
    return SettingsResponse(practiceEmails = practiceEmailsStatus(user), pending = hasPendingSend(userId))
}

fun Route.settingsRoutes() {
    get("/me/settings") {
        val s = call.services
        val session = call.requireUser()
        call.respond(s.db.tx { settingsOf(session.userId) })
    }

    /**
     * practice_emails: "allowed" clears pause/stop (it does not opt in: only the end-of-lesson
     * press does); "pause" / "stop" cancel pending sends; "stop" also withdraws the opt-in.
     */
    put("/me/settings") {
        val s = call.services
        val session = call.requireUser()
        val req = call.receive<SettingsRequest>()
        val now = s.clock.now()
        val result = s.db.tx {
            when (req.practiceEmails) {
                null -> Unit
                "allowed" -> Users.update({ Users.id eq session.userId }) {
                    it[practiceEmailsPausedAt] = null
                    it[practiceEmailsStoppedAt] = null
                }
                "pause" -> {
                    Users.update({ Users.id eq session.userId }) { it[practiceEmailsPausedAt] = now.toOdt() }
                    cancelPendingSends(session.userId, now)
                }
                "stop" -> {
                    Users.update({ Users.id eq session.userId }) {
                        it[practiceEmailsStoppedAt] = now.toOdt()
                        it[practiceEmailsAllowed] = false
                    }
                    cancelPendingSends(session.userId, now)
                }
                else -> return@tx null
            }
            settingsOf(session.userId)
        } ?: throw badRequest("invalid_setting", "practice_emails must be allowed, pause or stop.")
        call.respond(result)
    }
}
