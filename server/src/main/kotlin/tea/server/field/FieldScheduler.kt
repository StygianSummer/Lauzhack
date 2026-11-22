package tea.server.field

import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import tea.model.EventTypes
import tea.model.FIELD_TEMPLATE_ID
import tea.model.ReadinessResponse
import tea.server.Crypto
import tea.server.Services
import tea.server.auth.Codes
import tea.server.db.FieldSends
import tea.server.db.Users
import tea.server.db.emitEvent
import tea.server.db.newId
import tea.server.db.toOdt
import tea.server.me.UserData
import tea.server.me.hasLessonEvent

private val log = LoggerFactory.getLogger("tea.server.field")

fun fieldPayload(sendId: String, templateId: String, lessonId: String? = null): JsonObject = buildJsonObject {
    put("send_id", JsonPrimitive(sendId))
    put("template_id", JsonPrimitive(templateId))
    lessonId?.let { put("lesson_id", JsonPrimitive(it)) }
}

/** Cancels every pending (unsent, uncancelled) send of the user. Call inside a transaction. */
fun cancelPendingSends(userId: String, now: Instant): Int {
    val pending = FieldSends.selectAll()
        .where { (FieldSends.userId eq userId) and FieldSends.sentAt.isNull() and FieldSends.cancelledAt.isNull() }
        .toList()
    for (row in pending) cancelSend(row, now)
    return pending.size
}

private fun cancelSend(row: ResultRow, now: Instant) {
    FieldSends.update({ FieldSends.id eq row[FieldSends.id] }) { it[cancelledAt] = now.toOdt() }
    emitEvent(
        row[FieldSends.userId],
        EventTypes.FIELD_CANCELLED,
        fieldPayload(row[FieldSends.id], row[FieldSends.templateId], row[FieldSends.lessonId]),
        now,
    )
}

/** Is a practice email pending for this user (never says when)? Call inside a transaction. */
fun hasPendingSend(userId: String): Boolean =
    FieldSends.selectAll()
        .where { (FieldSends.userId eq userId) and FieldSends.sentAt.isNull() and FieldSends.cancelledAt.isNull() }
        .limit(1).any()

/** "off" | "allowed" | "paused" | "stopped" from a users row. */
fun practiceEmailsStatus(user: ResultRow): String = when {
    user[Users.practiceEmailsStoppedAt] != null -> "stopped"
    user[Users.practiceEmailsPausedAt] != null -> "paused"
    user[Users.practiceEmailsAllowed] -> "allowed"
    else -> "off"
}

/**
 * Part 5 (plan §10.6). The opt-in press schedules one send at a random moment; a coroutine polls
 * for due rows (so pending sends survive restarts) and sends them through the Mailer.
 */
class FieldScheduler(private val s: Services) {

    private val lessonIdPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun isValidLessonId(id: String) = lessonIdPattern.matches(id)

    private fun hasLesson(id: String): Boolean =
        isValidLessonId(id) && File(s.config.contentDir, "lessons/$id/lesson.en.json").isFile

    suspend fun readiness(userId: String, lessonId: String): ReadinessResponse {
        val now = s.clock.now()
        val min = s.config.fieldDelayMinS.coerceAtLeast(0)
        val max = s.config.fieldDelayMaxS.coerceAtLeast(min)
        val delayS = min + Crypto.random.nextInt(max - min + 1)
        var scheduledId: String? = null
        val response = s.db.tx {
            val user = UserData.user(userId)
            // 1. The press itself is recorded once per lesson (the client may have sent it already).
            if (!hasLessonEvent(userId, EventTypes.PRACTICE_EMAIL_OPTED_IN, lessonId)) {
                emitEvent(userId, EventTypes.PRACTICE_EMAIL_OPTED_IN, buildJsonObject { put("lesson_id", JsonPrimitive(lessonId)) }, now)
            }
            // 2. Checks; any failure → nothing else happens.
            val reason = when {
                user[Users.emailVerifiedAt] == null -> "not_verified"
                user[Users.termsAcceptedAt] == null -> "terms_missing"
                user[Users.practiceEmailsStoppedAt] != null -> "stopped"
                user[Users.practiceEmailsPausedAt] != null -> "paused"
                // One email per lesson: a pending or sent row blocks; a send cancelled before it went out does not.
                FieldSends.selectAll().where {
                    (FieldSends.userId eq userId) and (FieldSends.lessonId eq lessonId) and FieldSends.cancelledAt.isNull()
                }.limit(1).any() -> "already_done"
                else -> null
            }
            if (reason != null) return@tx ReadinessResponse(scheduled = false, reason = reason)
            // This press is the consent.
            Users.update({ Users.id eq userId }) { it[practiceEmailsAllowed] = true }
            // 3. Schedule.
            val sendId = newId()
            FieldSends.insert {
                it[id] = sendId
                it[FieldSends.userId] = userId
                it[templateId] = FIELD_TEMPLATE_ID
                it[scheduledAt] = (now + delayS.seconds).toOdt()
                it[FieldSends.lessonId] = lessonId
            }
            emitEvent(userId, EventTypes.FIELD_SCHEDULED, fieldPayload(sendId, FIELD_TEMPLATE_ID, lessonId), now)
            scheduledId = sendId
            ReadinessResponse(scheduled = true)
        }
        scheduledId?.let { log.info("field send {} scheduled in {} s", it, delayS) }
        return response
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        log.info("field scheduler started (poll every {})", s.config.fieldPollInterval)
        while (isActive) {
            try {
                tick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("field scheduler tick failed: {}", e.toString())
            }
            delay(s.config.fieldPollInterval)
        }
    }

    private data class Claimed(
        val sendId: String,
        val userId: String,
        val templateId: String,
        val lessonId: String,
        val email: String,
        val language: String,
        val firstName: String,
        val token: String,
    )

    private data class ReminderClaim(
        val sendId: String,
        val userId: String,
        val templateId: String,
        val lessonId: String,
        val email: String,
        val language: String,
        val firstName: String,
    )

    /** One pass: sends due practice emails and reminders. Returns how many mails went out. */
    suspend fun tick(): Int {
        val now = s.clock.now()
        val due = s.db.tx {
            FieldSends.selectAll()
                .where { FieldSends.sentAt.isNull() and FieldSends.cancelledAt.isNull() and (FieldSends.scheduledAt lessEq now.toOdt()) }
                .map { it[FieldSends.id] }
        }
        var sent = 0
        for (id in due) if (sendOne(id, now)) sent++
        val reminders = s.db.tx {
            FieldSends.selectAll()
                .where {
                    FieldSends.sentAt.isNotNull() and FieldSends.clickedAt.isNotNull() and
                        FieldSends.reportedAt.isNull() and FieldSends.cancelledAt.isNull() and
                        FieldSends.remindedAt.isNull() and FieldSends.remindAt.isNotNull() and
                        (FieldSends.remindAt lessEq now.toOdt())
                }
                .map { it[FieldSends.id] }
        }
        for (id in reminders) if (sendReminder(id, now)) sent++
        return sent
    }

    private suspend fun sendOne(sendId: String, now: Instant): Boolean {
        // Claim the row first (sent_at + token hash), so a crash can never cause a second mail.
        val claimed = s.db.tx {
            val row = FieldSends.selectAll()
                .where { (FieldSends.id eq sendId) and FieldSends.sentAt.isNull() and FieldSends.cancelledAt.isNull() }
                .singleOrNull() ?: return@tx null
            val userId = row[FieldSends.userId]
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
            if (user == null || practiceEmailsStatus(user) != "allowed") {
                cancelSend(row, now)
                return@tx null
            }
            val token = Crypto.newToken()
            FieldSends.update({ FieldSends.id eq sendId }) {
                it[sentAt] = now.toOdt()
                it[tokenHash] = Crypto.sha256Hex(token)
            }
            val email = user[Users.email]
            val profile = UserData.profile(user)
            val firstName = (user[Users.firstName] ?: profile.firstName)?.trim()?.takeIf { it.isNotEmpty() }
                ?: Codes.localPart(email)
            Claimed(sendId, userId, row[FieldSends.templateId], row[FieldSends.lessonId], email, user[Users.language], firstName, token)
        } ?: return false

        val template = s.templates.load(claimed.templateId, claimed.language)
        val clickUrl = s.config.apiPublicUrl.trimEnd('/') + "/f/c/" + claimed.token
        val settingsUrl = s.config.appPublicUrl + "#/settings"
        val mail = s.templates.render(template, claimed.firstName, clickUrl, settingsUrl)
        return try {
            s.mailer.send(claimed.email, mail.subject, mail.text, mail.html)
            s.db.tx {
                emitEvent(
                    claimed.userId,
                    EventTypes.FIELD_SENT,
                    fieldPayload(claimed.sendId, claimed.templateId, claimed.lessonId),
                    now,
                )
            }
            log.info("field send {} sent", claimed.sendId)
            true
        } catch (e: Exception) {
            log.error("field send {} failed ({}); retrying in 60 s", claimed.sendId, e.javaClass.simpleName)
            s.db.tx {
                FieldSends.update({ FieldSends.id eq claimed.sendId }) {
                    it[sentAt] = null
                    it[tokenHash] = null
                    it[scheduledAt] = (now + 60.seconds).toOdt()
                }
            }
            false
        }
    }

    private suspend fun sendReminder(sendId: String, now: Instant): Boolean {
        val claimed = s.db.tx {
            val eligible = (FieldSends.id eq sendId) and
                FieldSends.sentAt.isNotNull() and FieldSends.clickedAt.isNotNull() and
                FieldSends.reportedAt.isNull() and FieldSends.cancelledAt.isNull() and
                FieldSends.remindedAt.isNull() and FieldSends.remindAt.isNotNull() and
                (FieldSends.remindAt lessEq now.toOdt())
            val row = FieldSends.selectAll().where { eligible }.singleOrNull() ?: return@tx null
            val lessonId = row[FieldSends.lessonId]
            if (!hasLesson(lessonId)) {
                log.warn("field reminder {} skipped: invalid lesson id", sendId)
                return@tx null
            }
            val user = Users.selectAll().where { Users.id eq row[FieldSends.userId] }.singleOrNull() ?: return@tx null
            val updated = FieldSends.update({ eligible }) { it[remindedAt] = now.toOdt() }
            if (updated != 1) return@tx null
            val email = user[Users.email]
            val profile = UserData.profile(user)
            val firstName = (user[Users.firstName] ?: profile.firstName)?.trim()?.takeIf { it.isNotEmpty() }
                ?: Codes.localPart(email)
            ReminderClaim(
                sendId = sendId,
                userId = row[FieldSends.userId],
                templateId = row[FieldSends.templateId],
                lessonId = lessonId,
                email = email,
                language = user[Users.language],
                firstName = firstName,
            )
        } ?: return false

        val template = s.templates.load("lesson-reminder-01", claimed.language)
        val settingsUrl = s.config.appPublicUrl + "#/settings"
        val lessonUrl = s.config.appPublicUrl + "#/lesson/${claimed.lessonId}/0"
        val mail = s.templates.render(template, claimed.firstName, "", settingsUrl, lessonUrl)
        return try {
            s.mailer.send(claimed.email, mail.subject, mail.text, mail.html)
            s.db.tx {
                emitEvent(
                    claimed.userId,
                    EventTypes.FIELD_REMINDED,
                    fieldPayload(claimed.sendId, claimed.templateId, claimed.lessonId),
                    now,
                )
            }
            log.info("field reminder {} sent", claimed.sendId)
            true
        } catch (e: Exception) {
            log.error("field reminder {} failed ({}); retrying next poll", claimed.sendId, e.javaClass.simpleName)
            s.db.tx {
                FieldSends.update({ (FieldSends.id eq claimed.sendId) and (FieldSends.remindedAt eq now.toOdt()) }) {
                    it[remindedAt] = null
                }
            }
            false
        }
    }
}
