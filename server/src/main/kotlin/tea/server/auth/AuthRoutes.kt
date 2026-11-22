package tea.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import tea.model.EventTypes
import tea.model.LoginRequest
import tea.model.LoginResponse
import tea.model.PasswordAttemptRequest
import tea.model.PasswordAttemptResponse
import tea.model.StartRequest
import tea.model.VerifyRequest
import tea.model.VerifyResponse
import tea.server.ApiException
import tea.server.Crypto
import tea.server.auth.RateLimiter.Rule
import tea.server.badRequest
import tea.server.clientIp
import tea.server.db.EmailCodes
import tea.server.db.PasswordFindings
import tea.server.db.Sessions
import tea.server.db.Users
import tea.server.db.emitEvent
import tea.server.db.newId
import tea.server.db.toKInstant
import tea.server.db.toOdt
import tea.server.mail.VerificationMail
import tea.server.me.UserData
import tea.server.password.PasswordVerdict
import tea.server.rateLimited
import tea.server.services

private val log = LoggerFactory.getLogger("tea.server.auth")

private sealed interface VerifyOutcome {
    data class Ok(val token: String, val userId: String, val hasPassword: Boolean) : VerifyOutcome
    data class Fail(val code: String, val message: String) : VerifyOutcome
}

fun Route.authRoutes() = route("/auth") {

    post("/start") {
        val s = call.services
        val req = call.receive<StartRequest>()
        val email = Codes.normaliseEmail(req.email) ?: throw badRequest("invalid_email", "This does not look like an email address.")
        val ip = call.clientIp()
        if (!s.limiter.allow(
                Rule("start:min:$email", 1, 1.minutes),
                Rule("start:hour:$email", 5, 1.hours),
                Rule("start:ip:$ip", 20, 1.hours),
            )
        ) throw rateLimited()

        val language = Codes.normaliseLanguage(req.language)
        val code = Codes.newCode()
        val now = s.clock.now()
        s.db.tx {
            val existing = Users.selectAll().where { Users.email eq email }.singleOrNull()
            if (existing == null) {
                val userId = newId()
                Users.insert {
                    it[id] = userId
                    it[Users.email] = email
                    it[Users.language] = language
                    it[createdAt] = now.toOdt()
                }
                emitEvent(userId, EventTypes.REGISTERED, buildJsonObject { put("language", JsonPrimitive(language)) }, now)
            }
            EmailCodes.insert {
                it[id] = newId()
                it[EmailCodes.email] = email
                it[codeHash] = Codes.hash(s.codePepper, email, code)
                it[expiresAt] = (now + Codes.VALID_MINUTES.minutes).toOdt()
                it[createdAt] = now.toOdt()
            }
        }
        val mail = VerificationMail.render(language, code)
        try {
            s.mailer.send(email, mail.subject, mail.text, null)
        } catch (e: Exception) {
            log.error("verification mail could not be sent: {}", e.javaClass.simpleName)
            throw ApiException(HttpStatusCode.ServiceUnavailable, "mail_failed", "We could not send the email. Please try again later.")
        }
        call.respond(HttpStatusCode.NoContent)
    }

    post("/verify") {
        val s = call.services
        val req = call.receive<VerifyRequest>()
        val email = Codes.normaliseEmail(req.email) ?: throw badRequest("invalid_email", "This does not look like an email address.")
        val code = req.code.filter(Char::isDigit)
        val now = s.clock.now()
        val outcome = s.db.tx {
            val row = EmailCodes.selectAll()
                .where { (EmailCodes.email eq email) and EmailCodes.usedAt.isNull() }
                .orderBy(EmailCodes.createdAt, SortOrder.DESC)
                .limit(1).singleOrNull()
                ?: return@tx VerifyOutcome.Fail("invalid_code", "This code is not valid. Ask for a new one.")
            val codeId = row[EmailCodes.id]
            if (row[EmailCodes.expiresAt].toKInstant() <= now) {
                return@tx VerifyOutcome.Fail("code_expired", "This code has expired. Ask for a new one.")
            }
            if (row[EmailCodes.tries] >= Codes.MAX_TRIES) {
                return@tx VerifyOutcome.Fail("too_many_tries", "Too many wrong tries. Ask for a new code.")
            }
            val devOverride = s.config.devVerifyCode.isNotBlank() && code == s.config.devVerifyCode
            if (!devOverride && (code.length != Codes.LENGTH || !Crypto.constantTimeEquals(Codes.hash(s.codePepper, email, code), row[EmailCodes.codeHash]))) {
                EmailCodes.update({ EmailCodes.id eq codeId }) { it[tries] = row[EmailCodes.tries] + 1 }
                return@tx VerifyOutcome.Fail("invalid_code", "This code is not right. Check it and try again.")
            }
            EmailCodes.update({ EmailCodes.id eq codeId }) { it[usedAt] = now.toOdt() }
            val user = Users.selectAll().where { Users.email eq email }.singleOrNull()
                ?: return@tx VerifyOutcome.Fail("invalid_code", "This code is not valid. Ask for a new one.")
            val userId = user[Users.id]
            if (user[Users.emailVerifiedAt] == null) {
                val provider = Codes.provider(email)
                Users.update({ Users.id eq userId }) { it[emailVerifiedAt] = now.toOdt() }
                UserData.saveProfile(userId, UserData.profile(user).copy(emailProvider = provider), now)
                emitEvent(userId, EventTypes.EMAIL_VERIFIED, buildJsonObject { put("provider", JsonPrimitive(provider)) }, now)
            }
            VerifyOutcome.Ok(createSession(userId, now), userId, user[Users.passwordHash] != null)
        }
        when (outcome) {
            is VerifyOutcome.Fail -> throw badRequest(outcome.code, outcome.message)
            is VerifyOutcome.Ok -> call.respond(VerifyResponse(outcome.token, outcome.userId, outcome.hasPassword))
        }
    }

    post("/password/attempt") {
        val s = call.services
        val session = call.requireUser()
        val req = call.receive<PasswordAttemptRequest>()
        val chars = req.password.toCharArray()
        try {
            val user = s.db.tx { UserData.user(session.userId) }
            val verdict = s.analyzer.analyse(chars, user[Users.firstName], Codes.localPart(user[Users.email]))
            when (verdict) {
                // Rejected: answer with the reason, store nothing.
                is PasswordVerdict.Rejected -> call.respond(PasswordAttemptResponse(accepted = false, reason = verdict.reason))
                is PasswordVerdict.Accepted -> {
                    val hash = PasswordHasher.hash(chars)
                    val f = verdict.findings
                    val now = s.clock.now()
                    s.db.tx {
                        Users.update({ Users.id eq session.userId }) { it[passwordHash] = hash }
                        val values: (org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>) -> Unit = {
                            it[PasswordFindings.length] = f.length
                            it[PasswordFindings.classes] = f.classes
                            it[PasswordFindings.score] = f.score
                            it[PasswordFindings.pwned] = f.pwned
                            it[PasswordFindings.pwnedCount] = f.pwnedCount
                            it[PasswordFindings.containsName] = f.containsName
                            it[PasswordFindings.containsEmail] = f.containsEmail
                            it[PasswordFindings.containsYear] = f.containsYear
                            it[PasswordFindings.keyboardPattern] = f.keyboardPattern
                            it[PasswordFindings.analysedAt] = now.toOdt()
                        }
                        val n = PasswordFindings.update({ PasswordFindings.userId eq session.userId }) { values(it) }
                        if (n == 0) PasswordFindings.insert { it[userId] = session.userId; values(it) }
                    }
                    call.respond(PasswordAttemptResponse(accepted = true))
                }
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    post("/login") {
        val s = call.services
        val req = call.receive<LoginRequest>()
        val email = Codes.normaliseEmail(req.email) ?: throw badRequest("invalid_email", "This does not look like an email address.")
        if (!s.limiter.allow(Rule("login:hour:$email", 10, 1.hours))) throw rateLimited()
        val chars = req.password.toCharArray()
        try {
            val user = s.db.tx { Users.selectAll().where { Users.email eq email }.singleOrNull() }
            val hash = user?.get(Users.passwordHash)
            if (user == null || hash == null || !PasswordHasher.verify(chars, hash)) {
                throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Email or password is not right.")
            }
            val userId = user[Users.id]
            val token = s.db.tx { createSession(userId, s.clock.now()) }
            call.respond(LoginResponse(token = token, userId = userId))
        } finally {
            chars.fill('\u0000')
        }
    }

    post("/logout") {
        val s = call.services
        val session = call.requireUser()
        s.db.tx {
            Sessions.update({ Sessions.id eq session.sessionId }) { it[revokedAt] = s.clock.now().toOdt() }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
