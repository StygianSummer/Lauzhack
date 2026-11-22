package tea.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import tea.content.ContentRepository
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.Context
import tea.model.EventTypes
import tea.model.MasteryState
import tea.model.Skill
import tea.net.ApiClient
import tea.net.ApiResult
import tea.store.InMemoryKeyValueStore
import tea.store.LocalState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * End-to-end against a real server. Skipped unless `TEA_LIVE_LOG` points at the server's console
 * log (the verification code is read from it):
 * ```
 * RATE_LIMITS=off ./gradlew :server:run > server.log 2>&1 &
 * TEA_LIVE_LOG=server.log ./gradlew :shared:jvmTest --tests tea.app.LiveServerTest
 * ```
 * [guestModeWithServerDown] always runs (nothing listens on port 9).
 */
class LiveServerTest {
    private val log = System.getenv("TEA_LIVE_LOG")?.let(::File)
    private val api = System.getenv("TEA_LIVE_API") ?: "http://localhost:8080"

    private fun newApp(scope: CoroutineScope, base: String, platform: FakePlatformInfo = FakePlatformInfo(envApi = base)): AppState =
        AppState(
            platform = platform,
            local = LocalState(InMemoryKeyValueStore()),
            content = ContentRepository(loadBytes = { error("unused") }),
            scope = scope,
            apiFactory = { b, t -> ApiClient(b, t) },
            clock = Clock.System,
        )

    private fun codeFor(email: String): String {
        val text = log!!.readText()
        val at = text.lastIndexOf("To: $email")
        check(at >= 0) { "no mail for $email in the server log" }
        return Regex("\\b(\\d{6})\\b").find(text, at)!!.groupValues[1]
    }

    private fun say(s: String) = println("[live] $s")

    @Test
    fun registrationEndToEnd() = runBlocking<Unit> {
        if (log == null) {
            println("[live] skipped: set TEA_LIVE_LOG")
            return@runBlocking
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val app = newApp(scope, api)
            assertTrue(app.api.health() is ApiResult.Ok, "server not reachable at $api")
            val email = "live-${System.currentTimeMillis()}@mail.example"

            assertTrue(app.startEmail(email) is ApiResult.Ok)
            delay(300)
            val bad = app.verifyCode(email, "000000")
            say("wrong code -> $bad")
            assertEquals("invalid_code", (bad as ApiResult.Err).code)
            val verified = app.verifyCode(email, codeFor(email))
            say("verify -> $verified")
            assertTrue(verified is ApiResult.Ok && app.loggedIn.value)
            assertEquals(Screen.Name, app.nextAfterSignIn())

            app.updateProfile { it.copy(firstName = "Live", lastName = "Tester", displayName = "Live T.", country = "CH") }
            assertEquals(Screen.Password, app.nextAfterSignIn())
            val short = app.setPassword("short")
            say("password 'short' -> $short")
            assertEquals("too_short", (short as ApiResult.Ok).value.reason)
            val breached = app.setPassword("password1234")
            say("password 'password1234' -> $breached (accepted only if HIBP was unreachable)")
            val good = "Tea-kettle sings at 7 o'clock 4815"
            if ((breached as ApiResult.Ok).value.accepted.not()) {
                assertEquals("breached", breached.value.reason)
                val ok = app.setPassword(good)
                say("password good -> $ok")
                assertTrue((ok as ApiResult.Ok).value.accepted)
            }
            assertEquals(Screen.Onboarding(0), app.nextAfterSignIn())

            app.updateProfile { it.copy(hereFor = "ngo", channels = listOf("email", "sms"), dailyMinutes = 10) }
            app.skipOnboardingQuestion("age_range")
            app.finishOnboarding()
            app.updateDevice(DeviceSnapshot(dark = false, fontScale = 1f, widthDp = 1024))
            app.acceptTerms()
            delay(1_500)
            assertEquals(0, app.pendingSync.value, "queue should be flushed")
            val profile = (app.api.getProfile() as ApiResult.Ok).value
            say("server profile -> first=${profile.firstName} hereFor=${profile.hereFor} provider=${profile.emailProvider}")
            assertEquals("Live", profile.firstName)
            assertTrue(app.flags.value.signalsSent)

            // learning data from the TeaApp side
            val now = app.now()
            app.recordAttempt(Attempt("live-a1-$now", "email-bank-login-01", Context.PRACTICE, AttemptAnswers(), true, ms = 4000, createdAt = now))
            app.saveMastery(listOf(MasteryState(Skill.WHO, 0.62, updatedAt = now)))
            app.emit(EventTypes.LESSON_STARTED, buildJsonObject { put("lesson_id", JsonPrimitive("email-01")) })
            delay(1_500)
            assertEquals(0, app.pendingSync.value)
            val mastery = (app.api.getMastery() as ApiResult.Ok).value
            assertEquals(0.62, mastery.single { it.skill == Skill.WHO }.p)

            val about = (app.aboutRows() as ApiResult.Ok).value
            about.forEach { say("about: ${it.field} = ${it.value}  (${it.purpose})") }
            assertTrue(about.size >= 8)

            val board = app.leaderboard("week")
            say("leaderboard -> $board")
            val settings = app.practiceSettings()
            say("settings before opt-in -> $settings")
            assertEquals("off", (settings as ApiResult.Ok).value.practiceEmails)
            val readiness = app.requestReadiness("email-01")
            say("readiness -> $readiness")
            assertEquals(true, readiness?.scheduled)
            val pending = (app.practiceSettings() as ApiResult.Ok).value
            say("settings after opt-in -> $pending")
            assertTrue(pending.pending)
            val paused = (app.setPracticeEmails("pause") as ApiResult.Ok).value
            say("pause -> $paused")
            assertEquals("paused", paused.practiceEmails)
            assertFalse(paused.pending)
            app.refreshField()
            say("field cache -> ${app.field.value}")
            val reveal = app.reveal("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            say("reveal unknown token -> $reveal")
            assertEquals(404, (reveal as ApiResult.Err).status)
            assertTrue(app.reportSuspicious())

            // sign out, sign back in with the password, then delete
            app.logout()
            assertFalse(app.loggedIn.value)
            if (!breached.value.accepted) {
                val login = app.login(email, good)
                say("login -> $login")
                assertTrue(login is ApiResult.Ok)
                assertEquals("Live", app.profile.value.firstName) // pulled back from the server
            } else {
                assertTrue(app.login(email, "password1234") is ApiResult.Ok)
            }
            assertTrue(app.deleteAccount())
            assertFalse(app.loggedIn.value)
            assertEquals(Screen.Language, app.screen.value)
            val after = app.login(email, good)
            say("login after delete -> $after")
            assertEquals("invalid_credentials", (after as ApiResult.Err).code)
        } finally {
            scope.cancel()
        }
    }

    /** Opt-in → practice email (console) → click its link → Reveal → Home debrief. Set TEA_LIVE_FIELD=1 (waits up to 2 min). */
    @Test
    fun practiceEmailClickToReveal() = runBlocking<Unit> {
        if (log == null || System.getenv("TEA_LIVE_FIELD") != "1") {
            println("[live] field test skipped: set TEA_LIVE_LOG and TEA_LIVE_FIELD=1")
            return@runBlocking
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val app = newApp(scope, api)
            val email = "field-${System.currentTimeMillis()}@mail.example"
            app.startEmail(email)
            delay(300)
            assertTrue(app.verifyCode(email, codeFor(email)) is ApiResult.Ok)
            app.updateProfile { it.copy(firstName = "Field") }
            app.acceptTerms()
            delay(1_000)
            assertEquals(true, app.requestReadiness("email-01")?.scheduled)
            // the scheduler sends 15-90 s later; the link in the mail is <API_PUBLIC_URL>/f/c/<token>
            var token: String? = null
            val linkRe = Regex("/f/c/([A-Za-z0-9_-]{20,100})")
            repeat(60) {
                if (token == null) {
                    val text = log!!.readText()
                    val at = text.lastIndexOf("To: $email")
                    if (at >= 0) token = linkRe.find(text, at)?.groupValues?.get(1)
                    if (token == null) delay(2_000)
                }
            }
            assertNotNull(token, "practice email not sent within 2 minutes")
            say("practice email token found")
            val click = java.net.URI("$api/f/c/$token").toURL().openConnection() as java.net.HttpURLConnection
            click.instanceFollowRedirects = false
            say("click -> ${click.responseCode} ${click.getHeaderField("Location")}")
            assertEquals(302, click.responseCode)
            assertTrue(click.getHeaderField("Location").endsWith("#/reveal/$token"))
            // the deep link maps to the Reveal screen
            assertEquals(Screen.Reveal(token!!), Routes.parse(click.getHeaderField("Location").substringAfter('#')))
            val reveal = app.reveal(token!!)
            say("reveal -> $reveal")
            assertTrue(reveal is ApiResult.Ok)
            delay(800)
            say("field cache -> ${app.field.value}")
            assertTrue(app.fieldClicked.value)
            assertEquals("caught", app.field.value.status)
            assertTrue(app.deleteAccount())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun guestModeWithServerDown() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val app = newApp(scope, "http://127.0.0.1:9")
            assertTrue(app.api.health() is ApiResult.Offline)
            assertFalse(app.online.value)
            assertTrue(app.startEmail("ada@mail.example") is ApiResult.Offline)
            app.continueAsGuest()
            app.finishOnboarding()
            app.acceptTerms()
            app.emit(EventTypes.LESSON_STARTED, buildJsonObject { put("lesson_id", JsonPrimitive("email-01")) })
            app.addXp(10)
            delay(800)
            assertFalse(app.loggedIn.value)
            assertEquals(3, app.pendingSync.value) // onboarding_completed, terms_accepted, lesson_started
            assertEquals(10, app.xp.value)
            assertNotNull(app.local.flags.termsVersion)
        } finally {
            scope.cancel()
        }
    }
}
