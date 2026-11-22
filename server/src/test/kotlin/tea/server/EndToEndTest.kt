package tea.server

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import tea.app.AppState
import tea.app.DeviceSnapshot
import tea.app.FakePlatformInfo
import tea.app.Routes
import tea.app.Screen
import tea.content.ContentRepository
import tea.engine.ItemMode
import tea.engine.PlacementStep
import tea.engine.PracticeAnswer
import tea.engine.PracticeStep
import tea.model.AttemptAnswers
import tea.model.Confidence
import tea.model.EventTypes
import tea.model.FieldTemplate
import tea.model.Item
import tea.model.PracticeSection
import tea.model.StepKey
import tea.model.Support
import tea.model.TeaJson
import tea.model.Verdict
import tea.net.ApiClient
import tea.net.ApiResult
import tea.server.db.Attempts
import tea.server.db.ClientSignals
import tea.server.db.Events
import tea.server.db.FieldSends
import tea.server.db.Mastery
import tea.server.db.PasswordFindings
import tea.server.db.Profiles
import tea.server.db.Users
import tea.server.mail.Mailer
import tea.store.InMemoryKeyValueStore
import tea.store.LocalState
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.learn.emitOnce
import tea.ui.screens.placement.PlacementController
import tea.ui.screens.practice.ItemAnswer
import tea.ui.screens.practice.PracticeController

/**
 * The definition of done (plan §16) end to end, with the REAL client classes (AppState, ApiClient
 * over OkHttp, the placement and practice controllers, the bundled `content/` folder) talking over
 * HTTP to a real Netty server on a free port (H2, recording mailer, fake HIBP, short field delay).
 * The UI itself is driven separately (shared jvmTest `AppUiFlowTest`).
 */
class EndToEndTest {
    private val contentDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "content") }.first { File(it, "languages.txt").isFile }

    /** Prints every mail like the ConsoleMailer does ("the server log") and keeps it. */
    private class RecordingMailer : Mailer {
        data class Sent(val to: String, val subject: String, val text: String)
        override val kind = "console"
        val sent = java.util.Collections.synchronizedList(mutableListOf<Sent>())
        override suspend fun send(to: String, subject: String, text: String, html: String?) {
            println("[server mail]\nTo: $to\nSubject: $subject\n\n$text\n")
            sent += Sent(to, subject, text)
        }
        fun to(email: String) = synchronized(sent) { sent.filter { it.to == email } }
    }

    private val mailer = RecordingMailer()
    private val breached = "password1234"
    private val good = "Kettle whistles at 7 near the lake"
    private val port = ServerSocket(0).use { it.localPort }
    private val base = "http://127.0.0.1:$port"
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private lateinit var services: Services
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun startServer() {
        val config = Config(
            port = port,
            databaseUrl = "jdbc:h2:mem:e2e-${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            appPublicUrl = "http://localhost:8081/",
            apiPublicUrl = base,
            fieldDelayMinS = 1,
            fieldDelayMaxS = 2,
            fieldPollInterval = 200.milliseconds,
            codePepper = "e2e",
            contentDir = contentDir.path,
            rateLimitsEnabled = false,
        )
        server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            services = module(config, mailer, Clock.System, FakePwned.of(breached))
        }.start(wait = false)
    }

    @AfterTest
    fun stop() {
        scope.cancel()
        server?.stop(100, 500)
    }

    private fun newApp(): AppState = AppState(
        platform = FakePlatformInfo(envApi = base),
        local = LocalState(InMemoryKeyValueStore()),
        content = ContentRepository(loadBytes = ::loadContent),
        scope = scope,
        apiFactory = { b, t -> ApiClient(b, t) },
        flushDelayMs = 20,
    )

    // ---- content straight from /content, like :shared:syncContent lays it out

    private val fileName = Regex("""^(.+)\.([a-z]{2,3})\.json$""")

    private fun loadContent(path: String): ByteArray {
        val rel = path.removePrefix("${ContentRepository.DEFAULT_ROOT}/")
        return when (rel) {
            "index.json" -> {
                val itemsDir = File(contentDir, "items")
                val items = itemsDir.walkTopDown().filter { it.isFile && it.parentFile.name != "img" }
                    .mapNotNull { f -> fileName.matchEntire(f.name)?.takeIf { !it.groupValues[1].startsWith("_") }?.let { f.parentFile.relativeTo(itemsDir).invariantSeparatorsPath + "/" + it.groupValues[1] } }
                    .distinct().sorted().toList()
                fun arr(xs: List<String>) = xs.joinToString(",", "[", "]") { "\"$it\"" }
                """{"items":${arr(items)},"lessons":["email-01"],"languages":["en","fr","es"]}""".encodeToByteArray()
            }
            "resources.json" -> File(contentDir, "resources/resources.json").readBytes()
            else -> File(contentDir, rel).readBytes()
        }
    }

    // ---- helpers

    private suspend fun waitFor(what: String, timeoutMs: Long = 10_000, cond: suspend () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            delay(50)
        }
    }

    private suspend fun flushed(app: AppState) {
        app.requestFlush()
        waitFor("sync queue to empty") { app.pendingSync.value == 0 }
    }

    private fun codeFor(email: String): String =
        Regex("\\b(\\d{6})\\b").find(mailer.to(email).last { it.subject.contains("code") }.text)!!.groupValues[1]

    private suspend fun register(app: AppState, email: String, first: String) {
        assertTrue(app.startEmail(email) is ApiResult.Ok)
        assertTrue(app.verifyCode(email, codeFor(email)) is ApiResult.Ok)
        app.updateProfile { it.copy(firstName = first, lastName = "Tester", displayName = "$first T.") }
    }

    private suspend fun quickAccount(email: String, first: String): AppState {
        val app = newApp()
        register(app, email, first)
        assertTrue((app.setPassword(good) as ApiResult.Ok).value.accepted)
        app.finishOnboarding()
        app.acceptTerms()
        waitFor("terms + signals uploaded") { app.flags.value.signalsSent }
        return app
    }

    /** The right answer for a placement step, given as CERTAIN and fast. */
    private fun placementAnswer(step: PlacementStep): ItemAnswer {
        val item = step.item
        val answers = when (step.mode) {
            ItemMode.VERDICT, ItemMode.FULL -> AttemptAnswers(verdict = if (item.isScam) Verdict.TRICK else Verdict.FINE)
            ItemMode.TAP_HOTSPOT -> AttemptAnswers(hotspotsTapped = listOf(item.hotspots.first { it.redFlag && it.step == StepKey.WHO }.id))
            ItemMode.LINK -> AttemptAnswers(linkChoice = item.linkQuestion!!.correct)
            ItemMode.EMOTION -> AttemptAnswers(stepChoices = mapOf(StepKey.EMOTION to item.steps.getValue(StepKey.EMOTION).correct))
            ItemMode.ACTION -> AttemptAnswers(actionId = item.actions.first { it.isSafe }.id)
            ItemMode.NEWS -> AttemptAnswers(newsForward = item.news!!.real)
            ItemMode.IMAGE -> AttemptAnswers(imageAi = item.image!!.aiGenerated)
        }
        return ItemAnswer(answers, Confidence.CERTAIN, ms = 4_000)
    }

    /** Right step choices, right verdict, a safe action, fast. */
    private fun practiceAnswer(step: PracticeStep): PracticeAnswer {
        val item = step.item
        val asked = step.support.filterValues { it == Support.PROMPTED }.keys
        return PracticeAnswer(
            support = step.support,
            verdict = if (item.isScam) Verdict.TRICK else Verdict.FINE,
            actionId = item.actions.first { it.isSafe }.id,
            ms = 6_000,
            stepChoices = asked.associateWith { item.steps.getValue(it).correct },
        )
    }

    private data class PlacementRun(val steps: List<PlacementStep>)

    /** Right answers on items up to [knownLevel], "I don't know yet" above (a learner of that level). */
    private fun runPlacement(app: AppState, pool: List<Item>, knownLevel: Int = 6): PlacementRun {
        val c = PlacementController(app, pool, Random(5))
        val steps = mutableListOf<PlacementStep>()
        while (true) {
            val step = c.current ?: break
            steps += step
            val known = step.item.kind != "email" || step.item.difficulty <= knownLevel
            c.answer(step, if (known) placementAnswer(step) else ItemAnswer(AttemptAnswers(), null, ms = 9_000, dontKnow = true))
            check(steps.size < 40) { "placement does not end" }
        }
        c.finish()
        return PlacementRun(steps)
    }

    private fun runLessonPractice(app: AppState, pool: List<Item>, lessonId: String, section: PracticeSection): List<PracticeStep> {
        app.emitOnce(LearnKeys.lessonStarted(lessonId), EventTypes.LESSON_STARTED, JsonObject(mapOf("lesson_id" to JsonPrimitive(lessonId))))
        val c = PracticeController(app, pool, PracticeController.lessonOwner(lessonId), section, lessonId = lessonId, random = Random(9))
        val shown = mutableListOf<PracticeStep>()
        while (!c.finished) {
            val step = c.current!!
            shown += step
            c.submit(practiceAnswer(step))
            c.advance()
            check(shown.size < 40) { "practice does not end" }
        }
        return shown
    }

    private fun clickStatus(token: String): Pair<Int, String?> {
        val conn = URI("$base/f/c/$token").toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        return conn.responseCode to conn.getHeaderField("Location")
    }

    private fun tokenIn(text: String): String = Regex("/f/c/([A-Za-z0-9_-]{20,100})").find(text)!!.groupValues[1]

    private fun eventTypes(userId: String): List<String> = services.db.txBlocking {
        Events.selectAll().where { Events.userId eq userId }.map { it[Events.type] }
    }

    // =============================================================================================

    @Test
    fun definitionOfDone() = runBlocking<Unit> {
        // ---- §16.6 first: a guest with the backend DOWN (nothing listens on the port yet)
        val guest = newApp()
        waitFor("offline detected") { !guest.online.value }
        assertTrue(guest.startEmail("guest@mail.example") is ApiResult.Offline)
        guest.continueAsGuest()
        guest.finishOnboarding()
        guest.acceptTerms()
        val guestPool = guest.content.items("en")
        val guestPlacement = runPlacement(guest, guestPool)
        val lesson = guest.content.lesson("email-01", "en")!!
        val section = lesson.sections.filterIsInstance<PracticeSection>().single()
        runLessonPractice(guest, guestPool, lesson.id, section)
        assertFalse(guest.loggedIn.value)
        val guestAttempts = guest.local.attempts.size
        assertEquals(guestPlacement.steps.size + section.count + 1, guestAttempts)
        assertTrue(guest.pendingSync.value > 0, "guest work is queued")

        // ---- the server comes back
        startServer()
        guest.retryConnection()
        waitFor("guest back online") { guest.online.value }

        // ---- §16.2 registration with the real classes
        val app = newApp()
        val email = "dod.learner@mail.example"
        assertTrue(app.startEmail(email) is ApiResult.Ok)
        assertEquals("invalid_code", (app.verifyCode(email, "000000") as ApiResult.Err).code)
        assertTrue(app.verifyCode(email, codeFor(email)) is ApiResult.Ok)
        assertEquals(Screen.Name, app.nextAfterSignIn())
        app.updateProfile { it.copy(firstName = "Dana", lastName = "Okafor", displayName = "Dana O.", country = "CH") }
        assertEquals(Screen.Password, app.nextAfterSignIn())
        val userId = app.session.value!!.userId!!
        assertEquals("too_short", (app.setPassword("short") as ApiResult.Ok).value.reason)
        assertEquals("breached", (app.setPassword(breached) as ApiResult.Ok).value.reason)
        assertEquals(0L, services.db.txBlocking { PasswordFindings.selectAll().where { PasswordFindings.userId eq userId }.count() },
            "a rejected password leaves no findings")
        assertTrue((app.setPassword(good) as ApiResult.Ok).value.accepted)
        assertEquals(Screen.Onboarding(0), app.nextAfterSignIn())
        app.updateProfile { it.copy(hereFor = "myself", channels = listOf("email"), selfRating = 3, dailyMinutes = 5) }
        app.skipOnboardingQuestion("age_range")
        app.finishOnboarding()
        assertEquals(Screen.Terms, app.nextAfterSignIn())
        app.updateDevice(DeviceSnapshot(dark = false, fontScale = 1f, widthDp = 1024))
        app.acceptTerms()
        waitFor("signals sent") { app.flags.value.signalsSent }
        // Registration now ends on Home, with the placement test as an optional card there
        // (AppState.nextAfterSignIn). The placement itself still runs, just below.
        assertEquals(Screen.Home, app.nextAfterSignIn())

        // ---- §16.3 placement
        val pool = app.content.items("en")
        val placement = runPlacement(app, pool, knownLevel = 2)
        val result = app.profile.value.placement!!
        val modes = placement.steps.map { it.mode }.toSet()
        val budgetS = placement.steps.sumOf { it.item.expectedMs } / 1000
        println("[e2e] placement: ${placement.steps.size} items, modes=$modes, expected time ~${budgetS}s, level=${result.level}, strength=${result.strength}, focus=${result.focus}")
        // The quick placement of the demo: one scam/legit pair at difficulty 2, both right → level 3.
        assertEquals(2, placement.steps.size, "placement length ${placement.steps.size}")
        assertEquals(setOf(ItemMode.VERDICT), modes)
        assertEquals(3, result.level)
        assertEquals(Screen.Home, app.nextAfterSignIn())

        // ---- §16.4 lesson 1 practice: support fades, held-out last, summary, lesson_completed
        val shown = runLessonPractice(app, pool, lesson.id, section)
        val regular = shown.filter { !it.heldOut }
        println("[e2e] practice support per item: " + shown.joinToString(" | ") { s -> s.item.id + " " + StepKey.entries.joinToString(",") { s.support.getValue(it).name.take(1) } })
        assertEquals(section.count, regular.size)
        assertTrue(shown.last().heldOut && shown.last().support.values.all { it == Support.FADED })
        for (step in StepKey.entries) {
            val levels = regular.map { it.support.getValue(step).ordinal }
            assertEquals(levels.sorted(), levels, "$step support never comes back up")
        }
        assertTrue(StepKey.entries.any { s -> regular.first().support[s] != regular.last().support[s] }, "support visibly fades")
        flushed(app)
        assertTrue(EventTypes.LESSON_COMPLETED in eventTypes(userId))

        // ---- leaderboard: server recomputation == client XP
        val board = (app.leaderboard("all") as ApiResult.Ok).value
        println("[e2e] leaderboard all: $board ; client xp=${app.xp.value}")
        val me = assertNotNull(board.me)
        assertEquals("Dana O.", me.displayName)
        assertEquals(app.xp.value, me.xp, "server XP (recomputed) == client XP")
        assertEquals(app.xp.value, (app.leaderboard("week") as ApiResult.Ok).value.me!!.xp)

        // ---- the guest registers: the queue syncs
        register(guest, "guest@mail.example", "Gus")
        flushed(guest)
        val guestId = guest.session.value!!.userId!!
        val stored = services.db.txBlocking { Attempts.selectAll().where { Attempts.userId eq guestId }.count() }
        assertEquals(guestAttempts.toLong(), stored, "guest attempts uploaded after registering")
        val guestEvents = eventTypes(guestId)
        assertTrue(guestEvents.containsAll(listOf(EventTypes.PLACEMENT_COMPLETED, EventTypes.LESSON_COMPLETED, EventTypes.TERMS_ACCEPTED)), "$guestEvents")
        assertEquals(guest.xp.value, (guest.leaderboard("all") as ApiResult.Ok).value.me!!.xp, "guest XP agrees after sync")
        assertNotNull(guest.api.getProfile().valueOrNull?.placement, "guest placement result reached the server")

        // ---- no press → no practice email (checked again at the end)
        val noPress = quickAccount("no.press@mail.example", "Nora")

        // ---- §16.5 the press → one practice email after the delay → click → reveal → caught
        val settingsBefore = (app.practiceSettings() as ApiResult.Ok).value
        assertEquals("off", settingsBefore.practiceEmails, "accepting the terms does not switch practice emails on")
        app.emit(EventTypes.PRACTICE_EMAIL_OPTED_IN, JsonObject(mapOf("lesson_id" to JsonPrimitive(lesson.id))))
        val pressedAt = System.currentTimeMillis()
        assertEquals(true, app.requestReadiness(lesson.id)?.scheduled)
        assertTrue((app.practiceSettings() as ApiResult.Ok).value.pending)
        assertEquals("already_done", app.requestReadiness(lesson.id)?.reason, "one email per lesson")
        waitFor("practice email") { mailer.to(email).any { "/f/c/" in it.text } }
        val waited = System.currentTimeMillis() - pressedAt
        assertTrue(waited >= 900, "sent after the configured delay, not at once ($waited ms)")
        val mail = mailer.to(email).single { "/f/c/" in it.text }
        val template = TeaJson.decodeFromString(FieldTemplate.serializer(), File(contentDir, "field/practice-email-01.en.json").readText())
        assertEquals(template.subject, mail.subject, "the template from content/field is used, not the built-in placeholder")
        assertTrue("Dana" in mail.text && "http://localhost:8081/#/settings" in mail.text)
        val token = tokenIn(mail.text)
        val (status, location) = clickStatus(token)
        assertEquals(302, status)
        assertEquals("http://localhost:8081/#/reveal/$token", location)
        assertEquals(Screen.Reveal(token), Routes.parse(location!!.substringAfter('#')))
        val reveal = (app.reveal(token) as ApiResult.Ok).value
        assertEquals(template.reveal.steps[StepKey.WHO], reveal.steps[StepKey.WHO])
        waitFor("caught") { app.refreshField(); app.field.value.status == "caught" }
        assertTrue(app.fieldClicked.value)

        // ---- report → dodged
        val dodger = quickAccount("dodger@mail.example", "Dodo")
        assertEquals(true, dodger.requestReadiness(lesson.id)?.scheduled)
        waitFor("practice email to the dodger") { mailer.to("dodger@mail.example").any { "/f/c/" in it.text } }
        assertTrue(dodger.reportSuspicious())
        assertEquals("dodged", dodger.field.value.status)
        assertFalse(dodger.fieldClicked.value)

        // ---- pause before the send → no email
        val pauser = quickAccount("pauser@mail.example", "Paula")
        assertEquals(true, pauser.requestReadiness(lesson.id)?.scheduled)
        val paused = (pauser.setPracticeEmails("pause") as ApiResult.Ok).value
        assertEquals("paused", paused.practiceEmails)
        assertFalse(paused.pending)
        assertEquals("paused", pauser.requestReadiness(lesson.id)?.reason)
        delay(3_000) // longer than FIELD_DELAY_MAX_S + a poll
        assertTrue(mailer.to("pauser@mail.example").none { "/f/c/" in it.text }, "pausing stops the send")
        assertTrue(EventTypes.FIELD_CANCELLED in eventTypes(pauser.session.value!!.userId!!))
        assertTrue(mailer.to("no.press@mail.example").none { "/f/c/" in it.text }, "no press, no email")
        assertTrue(eventTypes(noPress.session.value!!.userId!!).none { it.startsWith("field_") })

        // ---- §16.7 database content for the main learner
        flushed(app)
        services.db.txBlocking {
            val u = Users.selectAll().where { Users.id eq userId }.single()
            assertNotNull(u[Users.emailVerifiedAt])
            assertNotNull(u[Users.termsAcceptedAt])
            assertTrue(u[Users.practiceEmailsAllowed], "the press is the consent")
            assertTrue(u[Users.passwordHash]!!.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"))
            assertEquals(1L, Profiles.selectAll().where { Profiles.userId eq userId }.count())
            val f = PasswordFindings.selectAll().where { PasswordFindings.userId eq userId }.single()
            assertEquals(good.length, f[PasswordFindings.length])
            assertFalse(f[PasswordFindings.pwned])
            val signals = ClientSignals.selectAll().where { ClientSignals.userId eq userId }.single()
            val keys = TeaJson.parseToJsonElement(signals[ClientSignals.json]).let { (it as JsonObject).keys }
            val allowed = setOf("browser_family", "device_class", "touch", "locale", "timezone", "dark_mode", "reduced_motion", "font_scale")
            assertTrue(allowed.containsAll(keys), "device settings limited to §9.2.9: $keys")
            assertTrue(Mastery.selectAll().where { Mastery.userId eq userId }.count() >= 6)
            assertEquals(app.local.attempts.size.toLong(), Attempts.selectAll().where { Attempts.userId eq userId }.count())
            assertEquals(1L, FieldSends.selectAll().where { FieldSends.userId eq userId }.count())
            // No plaintext password anywhere in the database (H2 dump), accepted or rejected.
            val dump = (connection.connection as java.sql.Connection).createStatement().use { st ->
                st.executeQuery("SCRIPT").use { rs -> buildString { while (rs.next()) appendLine(rs.getString(1)) } }
            }
            assertFalse(good in dump, "accepted password not stored in plaintext")
            assertFalse(breached in dump, "rejected password not stored")
        }
        val types = eventTypes(userId).toSet()
        val expected = listOf(
            EventTypes.REGISTERED, EventTypes.EMAIL_VERIFIED, EventTypes.TERMS_ACCEPTED, EventTypes.ONBOARDING_COMPLETED,
            EventTypes.PLACEMENT_COMPLETED, EventTypes.LESSON_STARTED, EventTypes.PRACTICE_ITEM_ANSWERED,
            EventTypes.HELD_OUT_ANSWERED, EventTypes.LESSON_COMPLETED, EventTypes.PRACTICE_EMAIL_OPTED_IN,
            EventTypes.FIELD_SCHEDULED, EventTypes.FIELD_SENT, EventTypes.FIELD_CLICKED,
        )
        assertTrue(types.containsAll(expected), "missing events: ${expected - types}")
        assertEquals(1, eventTypes(userId).count { it == EventTypes.PRACTICE_EMAIL_OPTED_IN }, "opt-in recorded once")

        // "What we know about you" lists every kind of stored data.
        val about = (app.aboutRows() as ApiResult.Ok).value
        about.forEach { println("[e2e] about: ${it.field} = ${it.value}") }
        val fields = about.joinToString("\n") { it.field + ": " + it.value }
        for (needle in listOf("Email address", "Password", "Terms", "Practice emails", "Device settings", "Skill progress",
            "Answered exercises", "Activity record", "Placement result", "data breach")) {
            assertTrue(needle in fields, "About misses '$needle'")
        }
        assertTrue(about.none { good in it.value })

        // ---- delete my account removes every row
        assertTrue(app.deleteAccount())
        assertEquals(Screen.Language, app.screen.value)
        services.db.txBlocking {
            for ((name, count) in listOf(
                "users" to Users.selectAll().where { Users.id eq userId }.count(),
                "attempts" to Attempts.selectAll().where { Attempts.userId eq userId }.count(),
                "events" to Events.selectAll().where { Events.userId eq userId }.count(),
                "mastery" to Mastery.selectAll().where { Mastery.userId eq userId }.count(),
                "field_sends" to FieldSends.selectAll().where { FieldSends.userId eq userId }.count(),
                "password_findings" to PasswordFindings.selectAll().where { PasswordFindings.userId eq userId }.count(),
            )) assertEquals(0L, count, "$name rows left after delete")
        }
        assertNull(app.local.session)
    }
}
