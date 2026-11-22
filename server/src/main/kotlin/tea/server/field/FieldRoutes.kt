package tea.server.field

import kotlin.time.Duration.Companion.seconds
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tea.model.AdminCancelRequest
import tea.model.EventTypes
import tea.model.GotchaResponse
import tea.model.ReadinessRequest
import tea.model.RevealResponse
import tea.server.ApiException
import tea.server.Crypto
import tea.server.auth.requireUser
import tea.server.badRequest
import tea.server.db.FieldSends
import tea.server.db.Users
import tea.server.db.emitEvent
import tea.server.db.toOdt
import tea.server.db.toKInstant
import tea.server.notFound
import tea.server.services

/** Tokens are 43 base64url chars; anything else is rejected before touching the database. */
private val TOKEN = Regex("^[A-Za-z0-9_-]{20,100}$")

fun Route.fieldRoutes() {
    post("/me/readiness") {
        val s = call.services
        val session = call.requireUser()
        val req = call.receive<ReadinessRequest>()
        val lessonId = req.lessonId.trim()
        if (!s.field.isValidLessonId(lessonId)) throw badRequest("invalid_lesson_id", "Unknown lesson.")
        call.respond(s.field.readiness(session.userId, lessonId))
    }

    /** "Got a suspicious email?": marks the latest sent, unreported send as reported. Always 204. */
    post("/me/field/report") {
        val s = call.services
        val session = call.requireUser()
        val now = s.clock.now()
        s.db.tx {
            val row = FieldSends.selectAll()
                .where { (FieldSends.userId eq session.userId) and FieldSends.sentAt.isNotNull() and FieldSends.reportedAt.isNull() }
                .orderBy(FieldSends.sentAt, SortOrder.DESC)
                .limit(1).singleOrNull() ?: return@tx
            FieldSends.update({ FieldSends.id eq row[FieldSends.id] }) { it[reportedAt] = now.toOdt() }
            emitEvent(
                session.userId,
                EventTypes.FIELD_REPORTED,
                fieldPayload(row[FieldSends.id], row[FieldSends.templateId], row[FieldSends.lessonId]),
                now,
            )
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/me/field/sends/{sendId}") {
        val s = call.services
        val session = call.requireUser()
        val sendId = call.parameters["sendId"].orEmpty()
        val found = s.db.tx {
            val row = FieldSends.selectAll()
                .where { (FieldSends.id eq sendId) and (FieldSends.userId eq session.userId) }
                .singleOrNull()
                ?.takeIf { it[FieldSends.clickedAt] != null }
                ?: return@tx null
            val user = Users.selectAll().where { Users.id eq session.userId }.singleOrNull() ?: return@tx null
            val email = user[Users.email]
            val firstName = (user[Users.firstName] ?: tea.server.me.UserData.profile(user).firstName)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: email.substringBefore('@')
            Triple(row, user[Users.language], firstName)
        } ?: throw notFound("Unknown practice email.")
        val row = found.first
        val template = s.templates.load(row[FieldSends.templateId], found.second)
        val rendered = s.templates.render(template, found.third, "(practice link)", "(settings)")
        call.respond(
            GotchaResponse(
                sendId = row[FieldSends.id],
                lessonId = row[FieldSends.lessonId],
                templateId = row[FieldSends.templateId],
                clickedAt = row[FieldSends.clickedAt]!!.toKInstant(),
                subject = rendered.subject,
                text = rendered.text,
                steps = template.reveal.steps,
                hints = template.reveal.hints,
            ),
        )
    }

    /** The one link inside the practice email. Records the click once, then opens the in-app Reveal. */
    get("/f/c/{token}") {
        val s = call.services
        val token = call.parameters["token"].orEmpty()
        if (TOKEN.matches(token)) {
            val hash = Crypto.sha256Hex(token)
            val now = s.clock.now()
            s.db.tx {
                val row = FieldSends.selectAll().where { FieldSends.tokenHash eq hash }.singleOrNull() ?: return@tx
                if (row[FieldSends.clickedAt] == null) {
                    FieldSends.update({ FieldSends.id eq row[FieldSends.id] }) {
                        it[clickedAt] = now.toOdt()
                        it[remindAt] = (now + s.config.fieldRemindDelayS.coerceAtLeast(0).seconds).toOdt()
                    }
                    emitEvent(
                        row[FieldSends.userId],
                        EventTypes.FIELD_CLICKED,
                        fieldPayload(row[FieldSends.id], row[FieldSends.templateId], row[FieldSends.lessonId]),
                        now,
                    )
                }
            }
        }
        call.respondRedirect(s.config.appPublicUrl + "#/reveal/" + token, permanent = false)
    }

    get("/field/reveal/{token}") {
        val s = call.services
        val token = call.parameters["token"].orEmpty()
        if (!TOKEN.matches(token)) throw notFound("Unknown practice email.")
        val hash = Crypto.sha256Hex(token)
        val found = s.db.tx {
            val row = FieldSends.selectAll().where { FieldSends.tokenHash eq hash }.singleOrNull() ?: return@tx null
            val lang = Users.selectAll().where { Users.id eq row[FieldSends.userId] }.singleOrNull()?.get(Users.language) ?: "en"
            row[FieldSends.templateId] to lang
        } ?: throw notFound("Unknown practice email.")
        val t = s.templates.load(found.first, found.second)
        call.respond(RevealResponse(templateId = found.first, steps = t.reveal.steps, hints = t.reveal.hints))
    }

    /** Demo reset: cancels the user's pending sends (a cancelled send no longer blocks a new press). */
    post("/admin/field/cancel") {
        val s = call.services
        val key = call.request.headers["X-Admin-Key"].orEmpty()
        if (s.config.adminKey.isBlank()) throw ApiException(HttpStatusCode.Forbidden, "admin_disabled", "Set ADMIN_KEY to use admin endpoints.")
        if (!Crypto.constantTimeEquals(key, s.config.adminKey)) throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Wrong admin key.")
        val req = call.receive<AdminCancelRequest>()
        val now = s.clock.now()
        s.db.tx { cancelPendingSends(req.userId, now) }
        call.respond(HttpStatusCode.NoContent)
    }
}
