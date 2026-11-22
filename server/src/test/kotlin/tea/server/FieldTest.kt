package tea.server

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import tea.model.AdminCancelRequest
import tea.model.ApiError
import tea.model.GotchaResponse
import tea.model.ReadinessRequest
import tea.model.ReadinessResponse
import tea.model.RevealResponse
import tea.model.SettingsRequest
import tea.model.SettingsResponse
import tea.model.StepKey
import tea.model.StoredEvent
import tea.server.db.FieldSends
import tea.server.db.Users
import tea.server.db.toOdt

class FieldTest {
    private suspend fun TestCtx.press(token: String, lesson: String = "email-01") =
        decode<ReadinessResponse>(post("/me/readiness", token, ReadinessRequest(lesson)).bodyText())

    private fun TestCtx.clickToken(): String {
        val m = mailer.sent.last()
        return Regex("https://api\\.example/f/c/([A-Za-z0-9_-]+)").find(m.text)!!.groupValues[1]
    }

    private suspend fun TestCtx.fieldEvents(token: String) =
        decode<List<StoredEvent>>(get("/me/events?type=field_*", token).bodyText()).map { it.type }

    @Test
    fun noPressNoEmail() = teaTest {
        val s = register()
        acceptTerms(s.token)
        mailer.sent.clear()
        clock.advance(10_000.seconds)
        assertEquals(0, services.field.tick())
        assertTrue(mailer.sent.isEmpty())
        assertEquals(SettingsResponse("off", false), decode<SettingsResponse>(get("/me/settings", s.token).bodyText()))
    }

    @Test
    fun pressNeedsTermsThenSendsExactlyOneEmail() = teaTest {
        val s = register()
        put("/me/profile", s.token, tea.model.Profile(firstName = "Jane"))
        assertEquals(ReadinessResponse(false, "terms_missing"), press(s.token))
        acceptTerms(s.token)
        mailer.sent.clear()

        assertEquals(ReadinessResponse(true, null), press(s.token))
        assertEquals(SettingsResponse("allowed", true), decode<SettingsResponse>(get("/me/settings", s.token).bodyText()))
        // the response body never carries a time
        assertFalse("scheduled_at" in post("/me/readiness", s.token, ReadinessRequest("email-01")).bodyText())

        assertEquals(1, services.field.tick())
        assertEquals(0, services.field.tick())
        val mail = mailer.sent.single()
        assertEquals("jane.doe@mail.example", mail.to)
        assertTrue("Hi Jane," in mail.text)
        assertTrue("https://app.example/tea/#/settings" in mail.text)
        assertTrue(mail.html?.contains("<img") != true)
        // same lesson again → already_done, still one email
        assertEquals(ReadinessResponse(false, "already_done"), press(s.token))
        clock.advance(100.seconds)
        services.field.tick()
        assertEquals(1, mailer.sent.size)
        assertEquals(SettingsResponse("allowed", false), decode<SettingsResponse>(get("/me/settings", s.token).bodyText()))
        // only the hash of the token is stored
        val token = clickToken()
        services.db.txBlocking {
            val row = FieldSends.selectAll().single()
            assertEquals(Crypto.sha256Hex(token), row[FieldSends.tokenHash])
        }
    }

    @Test
    fun notVerifiedIsRefused() = teaTest {
        // a user row exists after /auth/start, but without verification there is no session;
        // check the rule directly through the scheduler.
        post("/auth/start", null, tea.model.StartRequest("x@y.example"))
        val userId = services.db.txBlocking { Users.selectAll().single()[Users.id] }
        assertEquals(ReadinessResponse(false, "not_verified"), services.field.readiness(userId, "email-01"))
    }

    @Test
    fun pauseCancelsPendingAndStopsFutureSends() = teaTest {
        val s = register()
        acceptTerms(s.token)
        mailer.sent.clear()
        assertTrue(press(s.token).scheduled)
        val paused = decode<SettingsResponse>(put("/me/settings", s.token, SettingsRequest("pause")).bodyText())
        assertEquals(SettingsResponse("paused", false), paused)
        clock.advance(100.seconds)
        assertEquals(0, services.field.tick())
        assertTrue(mailer.sent.isEmpty())
        assertEquals(listOf("field_cancelled", "field_scheduled"), fieldEvents(s.token))
        assertEquals(ReadinessResponse(false, "paused"), press(s.token))

        // allowed again → a new press may schedule again (the cancelled one never went out)
        assertEquals(SettingsResponse("allowed", false), decode<SettingsResponse>(put("/me/settings", s.token, SettingsRequest("allowed")).bodyText()))
        assertTrue(press(s.token).scheduled)
        val stopped = decode<SettingsResponse>(put("/me/settings", s.token, SettingsRequest("stop")).bodyText())
        assertEquals(SettingsResponse("stopped", false), stopped)
        assertEquals(0, services.field.tick())
        assertEquals(ReadinessResponse(false, "stopped"), press(s.token))
        val bad = put("/me/settings", s.token, SettingsRequest("whatever"))
        assertEquals("invalid_setting", decode<ApiError>(bad.bodyText()).error)
    }

    @Test
    fun clickRedirectsRevealsAndIsRecordedOnce() = teaTest {
        val s = register()
        acceptTerms(s.token)
        press(s.token)
        services.field.tick()
        val token = clickToken()

        val r = get("/f/c/$token")
        assertEquals(HttpStatusCode.Found, r.status)
        assertEquals("https://app.example/tea/#/reveal/$token", r.headers[HttpHeaders.Location])
        get("/f/c/$token")
        assertEquals(listOf("field_clicked", "field_sent", "field_scheduled"), fieldEvents(s.token))
        services.db.txBlocking { assertNotNull(FieldSends.selectAll().single()[FieldSends.clickedAt]) }

        val reveal = decode<RevealResponse>(get("/field/reveal/$token").bodyText())
        assertEquals("practice-email-01", reveal.templateId)
        assertEquals(StepKey.entries.toSet(), reveal.steps.keys)
        assertEquals(HttpStatusCode.NotFound, get("/field/reveal/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").status)
    }

    @Test
    fun clickedSendHasOwnAuthenticatedGotchaResponseWithoutToken() = teaTest {
        val s = register("jane.doe@mail.example")
        val other = register("other@example.test")
        acceptTerms(s.token)
        press(s.token)
        services.field.tick()
        val sendId = services.db.txBlocking { FieldSends.selectAll().single()[FieldSends.id] }

        assertEquals(HttpStatusCode.Unauthorized, get("/me/field/sends/$sendId").status)
        assertEquals(HttpStatusCode.NotFound, get("/me/field/sends/$sendId", other.token).status)
        assertEquals(HttpStatusCode.NotFound, get("/me/field/sends/$sendId", s.token).status)

        val token = clickToken()
        get("/f/c/$token")
        val r = get("/me/field/sends/$sendId", s.token)
        assertEquals(HttpStatusCode.OK, r.status)
        val response = decode<GotchaResponse>(r.bodyText())
        assertEquals(sendId, response.sendId)
        assertEquals("email-01", response.lessonId)
        assertTrue("(practice link)" in response.text)
        assertTrue("(settings)" in response.text)
        assertFalse(token in response.text)
        assertFalse(token in response.subject)
        val clicked = decode<List<StoredEvent>>(get("/me/events?type=field_*", s.token).bodyText()).first()
        assertEquals("email-01", clicked.payload["lesson_id"]?.toString()?.trim('"'))
    }

    @Test
    fun clickedSendGetsOneReminderWithLessonLink() = teaTest(
        contentDir = contentDir(mapOf(
            "field/lesson-reminder-01.en.json" to """
                {"id":"lesson-reminder-01","subject":"Lesson reminder",
                 "text":"Continue here: {{lesson_url}}; manage: {{settings_url}}"}
            """.trimIndent(),
            "lessons/email-01/lesson.en.json" to "{}",
        )),
        fieldRemindDelayS = 10,
    ) {
        val s = register()
        mailer.sent.clear()
        acceptTerms(s.token)
        press(s.token)
        services.field.tick()
        get("/f/c/${clickToken()}")
        services.db.txBlocking {
            assertNotNull(FieldSends.selectAll().single()[FieldSends.remindAt])
            assertEquals(null, FieldSends.selectAll().single()[FieldSends.remindedAt])
        }

        clock.advance(9.seconds)
        assertEquals(0, services.field.tick())
        clock.advance(1.seconds)
        assertEquals(1, services.field.tick())
        assertEquals(2, mailer.sent.size)
        val reminder = mailer.sent.last()
        assertEquals("Lesson reminder", reminder.subject)
        assertTrue("https://app.example/tea/#/lesson/email-01/0" in reminder.text)
        assertTrue("https://app.example/tea/#/settings" in reminder.text)
        assertEquals(0, services.field.tick())
        assertEquals(2, mailer.sent.size)
        assertEquals(
            listOf("field_reminded", "field_clicked", "field_sent", "field_scheduled"),
            fieldEvents(s.token),
        )
        services.db.txBlocking { assertNotNull(FieldSends.selectAll().single()[FieldSends.remindedAt]) }
    }

    @Test
    fun reportOrCancellationBeforeReminderSuppressesIt() = teaTest(
        contentDir = contentDir(mapOf(
            "field/lesson-reminder-01.en.json" to """
                {"id":"lesson-reminder-01","subject":"Lesson reminder","text":"{{lesson_url}}"}
            """.trimIndent(),
            "lessons/email-01/lesson.en.json" to "{}",
        )),
        fieldRemindDelayS = 10,
    ) {
        val s = register()
        mailer.sent.clear()
        acceptTerms(s.token)
        press(s.token)
        services.field.tick()
        get("/f/c/${clickToken()}")
        client.post("/me/field/report") { bearerAuth(s.token) }
        clock.advance(10.seconds)
        assertEquals(0, services.field.tick())
        assertEquals(1, mailer.sent.size)

        val second = register("second@example.test")
        mailer.sent.removeAll { it.to == "second@example.test" }
        acceptTerms(second.token)
        press(second.token)
        services.field.tick()
        get("/f/c/${clickToken()}")
        services.db.txBlocking {
            FieldSends.update({ FieldSends.userId eq second.userId }) {
                it[FieldSends.cancelledAt] = clock.now().toOdt()
            }
        }
        clock.advance(10.seconds)
        assertEquals(0, services.field.tick())
        assertEquals(2, mailer.sent.size)
    }

    @Test
    fun reportMarksLatestSentSend() = teaTest {
        val s = register()
        acceptTerms(s.token)
        // nothing sent yet: still 204, nothing recorded
        assertEquals(HttpStatusCode.NoContent, client.post("/me/field/report") { bearerAuth(s.token) }.status)
        press(s.token)
        services.field.tick()
        assertEquals(HttpStatusCode.NoContent, client.post("/me/field/report") { bearerAuth(s.token) }.status)
        assertEquals(HttpStatusCode.NoContent, client.post("/me/field/report") { bearerAuth(s.token) }.status)
        assertEquals(listOf("field_reported", "field_sent", "field_scheduled"), fieldEvents(s.token))
    }

    @Test
    fun templatesComeFromContentDirWithLanguageFallback() = teaTest(
        contentDir = contentDir(mapOf(
            "field/practice-email-01.en.json" to """
                {"id":"practice-email-01","subject":"Your parcel","text":"Hello {{first_name}}, see {{click_url}} - {{settings_url}}",
                 "html":"<p>Hello {{first_name}}</p>","reveal":{"steps":{"who":"W","emotion":"E","ask":"A","verify":"V"},"hints":["h1"]}}
            """.trimIndent(),
        )),
    ) {
        val s = register(language = "es")
        put("/me/profile", s.token, tea.model.Profile(language = "es", firstName = "<b>Ana</b>"))
        acceptTerms(s.token)
        press(s.token)
        services.field.tick()
        val m = mailer.sent.last()
        assertEquals("Your parcel", m.subject)
        assertEquals("<p>Hello &lt;b&gt;Ana&lt;/b&gt;</p>", m.html)
        val reveal = decode<RevealResponse>(get("/field/reveal/${clickToken()}").bodyText())
        assertEquals(listOf("h1"), reveal.hints)
    }

    @Test
    fun adminCancel() = teaTest {
        val s = register()
        acceptTerms(s.token)
        press(s.token)
        val forbidden = client.post("/admin/field/cancel") { header("X-Admin-Key", "wrong"); json(AdminCancelRequest(s.userId)) }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        val ok = client.post("/admin/field/cancel") { header("X-Admin-Key", "admin-secret"); json(AdminCancelRequest(s.userId)) }
        assertEquals(HttpStatusCode.NoContent, ok.status)
        assertEquals(0, services.field.tick())
        services.db.txBlocking { assertNotNull(FieldSends.selectAll().where { FieldSends.userId eq s.userId }.single()[FieldSends.cancelledAt]) }
    }

    @Test
    fun schedulerCoroutineSendsOnItsOwn() = teaTest(startScheduler = true) {
        val s = register()
        acceptTerms(s.token)
        mailer.sent.clear()
        assertTrue(press(s.token).scheduled)
        var waited = 0
        while (mailer.sent.isEmpty() && waited < 100) { delay(100); waited++ }
        assertEquals(1, mailer.sent.size)
    }
}
