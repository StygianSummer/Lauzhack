package tea.server

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import tea.model.ApiError
import tea.model.HealthResponse
import tea.model.LoginRequest
import tea.model.LoginResponse
import tea.model.PasswordAttemptRequest
import tea.model.PasswordAttemptResponse
import tea.model.Profile
import tea.model.StartRequest
import tea.model.VerifyRequest
import tea.server.db.PasswordFindings
import tea.server.db.Users

class AuthTest {
    @Test
    fun healthReportsDbAndMailer() = teaTest {
        val h = decode<HealthResponse>(get("/health").bodyText())
        assertEquals("ok", h.status)
        assertTrue(h.db)
        assertEquals("console", h.mailer)
        assertFalse(h.ai)
    }

    @Test
    fun fullAuthFlow() = teaTest(pwned = FakePwned.of("password1234")) {
        val email = "jane.doe@mail.example"
        // start + verify (code captured by the fake mailer, localised)
        assertEquals(HttpStatusCode.NoContent, post("/auth/start", null, StartRequest(" Jane.Doe@Mail.example ", "fr")).status)
        val mail = mailer.sent.single()
        assertEquals(email, mail.to)
        assertEquals("Votre code ThisIsTea", mail.subject)
        assertTrue("ni votre mot de passe" in mail.text)

        val wrong = post("/auth/verify", null, VerifyRequest(email, "000000".takeIf { it != mailer.lastCodeFor(email) } ?: "111111"))
        assertEquals(HttpStatusCode.BadRequest, wrong.status)
        assertEquals("invalid_code", decode<ApiError>(wrong.bodyText()).error)

        val ok = post("/auth/verify", null, VerifyRequest(email, mailer.lastCodeFor(email)))
        assertEquals(HttpStatusCode.OK, ok.status)
        val session = decode<tea.model.VerifyResponse>(ok.bodyText())
        assertFalse(session.hasPassword)
        // code is single-use
        assertEquals(HttpStatusCode.BadRequest, post("/auth/verify", null, VerifyRequest(email, mailer.lastCodeFor(email))).status)
        // provider derived and stored
        val profile = decode<Profile>(get("/me/profile", session.token).bodyText())
        assertEquals("other", profile.emailProvider)
        assertEquals("fr", profile.language)

        // name first, so the analyzer can check it
        put("/me/profile", session.token, profile.copy(firstName = "Jane"))

        // too short: no HIBP call, nothing stored
        val short = decode<PasswordAttemptResponse>(post("/auth/password/attempt", session.token, PasswordAttemptRequest("short")).bodyText())
        assertEquals(PasswordAttemptResponse(false, "too_short"), short)
        assertTrue(pwned.calls.isEmpty())

        // breached: only the 40-hex SHA-1 reaches the client interface, nothing stored
        val breached = decode<PasswordAttemptResponse>(post("/auth/password/attempt", session.token, PasswordAttemptRequest("password1234")).bodyText())
        assertEquals(PasswordAttemptResponse(false, "breached"), breached)
        assertEquals(40, pwned.calls.single().length)
        services.db.txBlocking {
            assertEquals(0, PasswordFindings.selectAll().count())
            assertNull(Users.selectAll().single()[Users.passwordHash])
        }

        // accepted
        val good = decode<PasswordAttemptResponse>(post("/auth/password/attempt", session.token, PasswordAttemptRequest("Jane1987qwer!")).bodyText())
        assertEquals(PasswordAttemptResponse(true, null), good)
        services.db.txBlocking {
            val hash = Users.selectAll().single()[Users.passwordHash]!!
            assertTrue(hash.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"))
            assertFalse("Jane1987qwer!" in hash)
            val f = PasswordFindings.selectAll().where { PasswordFindings.userId eq session.userId }.single()
            assertEquals(13, f[PasswordFindings.length])
            assertEquals(4, f[PasswordFindings.classes])
            assertTrue(f[PasswordFindings.containsName])
            assertTrue(f[PasswordFindings.containsYear])
            assertEquals("qwerty_row", f[PasswordFindings.keyboardPattern])
        }

        // login
        val bad = post("/auth/login", null, LoginRequest(email, "nope-nope-nope"))
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
        assertEquals("invalid_credentials", decode<ApiError>(bad.bodyText()).error)
        val login = post("/auth/login", null, LoginRequest("JANE.DOE@mail.example", "Jane1987qwer!"))
        assertEquals(HttpStatusCode.OK, login.status)
        val token2 = decode<LoginResponse>(login.bodyText()).token
        assertEquals(session.userId, decode<LoginResponse>(login.bodyText()).userId)

        // verify again now says has_password
        post("/auth/start", null, StartRequest(email, "en"))
        val again = decode<tea.model.VerifyResponse>(post("/auth/verify", null, VerifyRequest(email, mailer.lastCodeFor(email))).bodyText())
        assertTrue(again.hasPassword)

        // logout revokes only that session
        assertEquals(HttpStatusCode.NoContent, client.post("/auth/logout") { bearerAuth(token2) }.status)
        val after = get("/me/profile", token2)
        assertEquals(HttpStatusCode.Unauthorized, after.status)
        assertEquals("unauthorized", decode<ApiError>(after.bodyText()).error)
        assertEquals(HttpStatusCode.OK, get("/me/profile", session.token).status)
    }

    @Test
    fun codeDiesAfterFiveWrongTries() = teaTest {
        val email = "a@b.example"
        post("/auth/start", null, StartRequest(email))
        val code = mailer.lastCodeFor(email)
        val wrong = if (code == "123456") "654321" else "123456"
        repeat(5) { assertEquals("invalid_code", decode<ApiError>(post("/auth/verify", null, VerifyRequest(email, wrong)).bodyText()).error) }
        assertEquals("too_many_tries", decode<ApiError>(post("/auth/verify", null, VerifyRequest(email, code)).bodyText()).error)
    }

    @Test
    fun codeExpiresAfterTenMinutes() = teaTest {
        val email = "a@b.example"
        post("/auth/start", null, StartRequest(email))
        clock.advance(601.seconds)
        assertEquals("code_expired", decode<ApiError>(post("/auth/verify", null, VerifyRequest(email, mailer.lastCodeFor(email))).bodyText()).error)
    }

    @Test
    fun invalidEmailAndBadBody() = teaTest {
        val r = post("/auth/start", null, StartRequest("not-an-email"))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals("invalid_email", decode<ApiError>(r.bodyText()).error)
        val bad = client.post("/auth/start") { json(mapOf("nope" to "x")) }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals("bad_request", decode<ApiError>(bad.bodyText()).error)
    }

    @Test
    fun startIsRateLimitedButANewCodeWorksAfterSixtySeconds() = teaTest(rateLimits = true) {
        val email = "a@b.example"
        assertEquals(HttpStatusCode.NoContent, post("/auth/start", null, StartRequest(email)).status)
        val limited = post("/auth/start", null, StartRequest(email))
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("rate_limited", decode<ApiError>(limited.bodyText()).error)
        clock.advance(61.seconds)
        assertEquals(HttpStatusCode.NoContent, post("/auth/start", null, StartRequest(email)).status)
        // the newest code is the one that works
        assertEquals(HttpStatusCode.OK, post("/auth/verify", null, VerifyRequest(email, mailer.lastCodeFor(email))).status)
        // 5 per hour per email
        repeat(3) { clock.advance(61.seconds); assertEquals(HttpStatusCode.NoContent, post("/auth/start", null, StartRequest(email)).status) }
        clock.advance(61.seconds)
        assertEquals(HttpStatusCode.TooManyRequests, post("/auth/start", null, StartRequest(email)).status)
    }

    @Test
    fun corsAllowsLocalhostAnyPortOnly() = teaTest {
        val ok = client.options("/auth/start") {
            header(HttpHeaders.Origin, "http://localhost:8081")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals("http://localhost:8081", ok.headers[HttpHeaders.AccessControlAllowOrigin])
        val no = client.options("/auth/start") {
            header(HttpHeaders.Origin, "https://evil.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertNull(no.headers[HttpHeaders.AccessControlAllowOrigin])
        assertNotNull(ok)
    }

    @Test
    fun devOverrideCodeVerifiesWhenEnabled() = teaTest(devVerifyCode = "426769") {
        val email = "dev@b.example"
        post("/auth/start", null, StartRequest(email))
        assertEquals(HttpStatusCode.OK, post("/auth/verify", null, VerifyRequest(email, "426769")).status)
    }

    @Test
    fun devOverrideCodeIsRejectedWhenDisabled() = teaTest {
        val email = "dev@b.example"
        post("/auth/start", null, StartRequest(email))
        if (mailer.lastCodeFor(email) != "426769") {
            assertEquals("invalid_code", decode<ApiError>(post("/auth/verify", null, VerifyRequest(email, "426769")).bodyText()).error)
        }
    }

    @Test
    fun devOverrideDefaultsOnOnlyWithoutSmtp() {
        assertEquals("426769", Config.fromEnv(emptyMap()).devVerifyCode)
        assertEquals("426769", Config.fromEnv(mapOf("DEV_VERIFY_CODE" to "")).devVerifyCode)
        assertEquals("", Config.fromEnv(mapOf("SMTP_PASSWORD" to "x")).devVerifyCode)
        assertEquals("", Config.fromEnv(mapOf("DEV_VERIFY_CODE" to "off")).devVerifyCode)
        assertEquals("111111", Config.fromEnv(mapOf("SMTP_PASSWORD" to "x", "DEV_VERIFY_CODE" to "111111")).devVerifyCode)
    }
}
