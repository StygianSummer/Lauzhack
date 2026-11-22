package tea.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import tea.app.AppState
import tea.app.FakePlatformInfo
import tea.app.Screen
import tea.content.ContentRepository
import tea.content.Placeholders
import tea.engine.ItemMode
import tea.engine.Placement
import tea.engine.PlacementState
import tea.engine.Scaffold
import tea.model.Item
import tea.model.Lesson
import tea.model.StatusSection
import tea.model.StepKey
import tea.model.Support
import tea.model.ReadinessSection
import tea.net.ApiClient
import tea.store.InMemoryKeyValueStore
import tea.store.LocalState
import tea.ui.screens.AppRoot
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.practice.PracticeController
import tea.ui.screens.practice.PracticeRun
import tea.ui.screens.practice.shuffledActions

/**
 * Clicks through the REAL app (AppRoot over a real AppState, bundled content from Compose
 * resources, Ktor/OkHttp client) with Compose UI testing, and writes a screenshot per step to
 * `build/screenshots/ui-*.png`.
 *
 * - [guestOfflineFlow] always runs (backend on a closed port): language, email offline → guest,
 *   onboarding with Skip, terms, placement in several modes to the result, the lesson sections,
 *   one full practice item at PROMPTED support (wrong then right choice), settings (dark mode,
 *   large text, French), Back navigation, the offline banner and the debug footer.
 * - [registeredFlow] needs a live server (`TEA_LIVE_LOG` = its console log, see LiveServerTest):
 *   email → code → name → password (breached, then good) → … → readiness press → practice email →
 *   deep link `#/reveal/<token>` → Reveal → Home debrief.
 */
@OptIn(ExperimentalTestApi::class)
class AppUiFlowTest {
    private val outDir = File("build/screenshots").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var savedLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun frenchSystem() {
        // The team's machines run a French locale: the app must still render English when the
        // learner picked English (plural rules included).
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
    }

    @AfterTest
    fun cleanup() {
        scope.cancel()
        Locale.setDefault(savedLocale)
    }

    private fun newApp(api: String, platform: FakePlatformInfo = FakePlatformInfo(locale = "en-GB", envApi = api)) = AppState(
        platform = platform,
        local = LocalState(InMemoryKeyValueStore()),
        content = ContentRepository(),
        scope = scope,
        apiFactory = { b, t -> ApiClient(b, t) },
        flushDelayMs = 20,
    )

    // ---- helpers --------------------------------------------------------------------------------

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        // A dialog is a second root: capture the top-most one.
        val img = onAllNodes(isRoot()).onLast().captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(outDir, "ui-$name.png"))
    }

    private fun ComposeUiTest.waitText(text: String, timeoutMs: Long = 15_000, substring: Boolean = false) {
        waitUntil(timeoutMillis = timeoutMs) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.node(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        onAllNodesWithText(text, substring = substring).let { all ->
            // Prefer the clickable one when the text appears twice (e.g. a heading and a button).
            val n = all.fetchSemanticsNodes().size
            check(n > 0) { "no node with text '$text'" }
            (0 until n).map { all[it] }.firstOrNull { runCatching { it.assert(hasClickAction()) }.isSuccess } ?: all[0]
        }

    private fun ComposeUiTest.click(text: String, substring: Boolean = false) {
        waitText(text, substring = substring)
        node(text, substring).apply { runCatching { performScrollTo() } }.performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.type(index: Int, text: String) {
        val fields = onAllNodes(hasSetTextAction())
        fields[index].apply { runCatching { performScrollTo() } }.performTextInput(text)
        waitForIdle()
    }

    private fun ComposeUiTest.waitScreen(app: AppState, timeoutMs: Long = 15_000, cond: (Screen) -> Boolean) {
        waitUntil(timeoutMillis = timeoutMs) { cond(app.screen.value) }
        waitForIdle()
    }

    /** Text of the region a hotspot target points to, as EmailView renders it. */
    private fun regionText(item: Item, target: String): String {
        val e = item.email!!
        return when {
            target == "from_name" -> e.fromName
            target == "from_address" -> "<${e.fromAddress}>"
            target == "reply_to" -> e.replyTo!!
            target == "subject" -> e.subject
            target == "link" -> e.body.first { it.t == "link" }.v
            target.startsWith("body:") -> e.body[target.removePrefix("body:").toInt()].v
            target.startsWith("attachment:") -> e.attachments[target.removePrefix("attachment:").toInt()]
            else -> error("unknown target $target")
        }
    }

    private fun filled(app: AppState, item: Item) = Placeholders.fill(item, app.profile.value, app.email.value)

    /**
     * Placement through the UI: right answers on items up to [knownLevel] (CERTAIN), "I don't know
     * yet" above. Screenshots the first item of each mode. Returns the modes seen.
     */
    private fun ComposeUiTest.placement(app: AppState, pool: List<Item>, knownLevel: Int): Set<ItemMode> {
        val seen = mutableSetOf<ItemMode>()
        var guard = 0
        while (app.screen.value == Screen.Placement) {
            val state = app.load(LearnKeys.PLACEMENT_STATE, PlacementState.serializer()) ?: break
            val step = Placement.next(state, pool) ?: break
            val item = filled(app, step.item)
            val answered = state.answers.size
            if (step.item.kind == "email") waitText(item.email!!.subject) else waitForIdle()
            val known = step.item.kind != "email" || step.item.difficulty <= knownLevel
            if (!known) {
                click("I don't know yet")
            } else {
                when (step.mode) {
                    ItemMode.VERDICT, ItemMode.FULL -> click(if (item.isScam) "Trying to trick me" else "Fine")
                    ItemMode.TAP_HOTSPOT -> click(regionText(item, item.hotspots.first { it.redFlag && it.step == StepKey.WHO }.target))
                    ItemMode.LINK -> click(item.linkQuestion!!.options[item.linkQuestion!!.correct])
                    ItemMode.EMOTION -> item.steps.getValue(StepKey.EMOTION).let { click(it.choices[it.correct]) }
                    ItemMode.ACTION -> click(item.actions.first { it.isSafe }.label)
                    ItemMode.NEWS -> click(if (item.news!!.real) "Yes, forward it" else "No, don't forward it")
                    ItemMode.IMAGE -> click(if (item.image!!.aiGenerated) "Made by a computer" else "Real photo")
                }
                click("Certain")
                if (step.mode !in seen) shot("placement-${step.mode.name.lowercase()}")
                seen += step.mode
                click(if (step.mode == ItemMode.TAP_HOTSPOT) "That's what I see" else "Continue")
            }
            waitUntil(timeoutMillis = 10_000) {
                app.screen.value != Screen.Placement ||
                    (app.load(LearnKeys.PLACEMENT_STATE, PlacementState.serializer())?.answers?.size ?: 0) > answered
            }
            check(++guard < 30) { "placement does not end" }
        }
        return seen
    }

    private fun lessonOf(app: AppState): Lesson = runBlocking { app.content.lesson("email-01", "en")!! }

    /** Rules → Tools → Attacks (give up the hunt) → Status → the Practice section. */
    private fun ComposeUiTest.lessonUntilPractice(app: AppState, lesson: Lesson) {
        waitScreen(app) { it is Screen.Lesson }
        shot("lesson-0-rules")
        click("Continue") // rules
        waitScreen(app) { it == Screen.Lesson("email-01", 1) }
        shot("lesson-1-tools")
        click("Continue")
        waitScreen(app) { it == Screen.Lesson("email-01", 2) }
        shot("lesson-2-attacks")
        // 0 misses must read "0 wrong guesses" (English plural rule, even on a French system).
        waitText("0 wrong guesses", substring = true)
        click("Show me the rest")
        shot("lesson-2-attacks-revealed")
        click("Continue")
        waitScreen(app) { it == Screen.Lesson("email-01", 3) }
        val status = lesson.sections.filterIsInstance<StatusSection>().single()
        click(status.levels[1].label)
        shot("lesson-3-status")
        click("Continue")
        waitScreen(app) { it == Screen.Lesson("email-01", 4) }
    }

    /**
     * One full practice item through the UI: WORKED → "Got it", PROMPTED → a wrong choice first
     * (struck out + Cat's hint), then the right one, "Next step"; then verdict, a safe action,
     * "Check my answer", feedback, next. Returns the support the item was shown with.
     */
    private fun ComposeUiTest.practiceItem(app: AppState, pool: List<Item>, owner: String, shoot: Boolean): Map<StepKey, Support> {
        val run = app.load(LearnKeys.practiceRun(owner), PracticeRun.serializer())!!
        val raw = pool.first { it.id == run.currentItemId }
        val item = filled(app, raw)
        val support = Scaffold.supportMap(raw, app.mastery.value)
        waitText(item.email!!.subject)
        var wrongShown = false
        for (step in StepKey.entries) {
            val content = item.steps[step] ?: continue
            when (support.getValue(step)) {
                Support.WORKED -> click("Got it")
                Support.PROMPTED -> {
                    if (shoot && !wrongShown) {
                        val wrong = content.choices.indices.first { it != content.correct }
                        click(content.choices[wrong])
                        waitText("Not that one. Have another go.", substring = true)
                        if (shoot) shot("practice-prompted-wrong")
                        wrongShown = true
                    }
                    click(content.choices[content.correct])
                    if (shoot) shot("practice-prompted-right-${step.name.lowercase()}")
                    click("Next step")
                }
                else -> Unit
            }
        }
        click(if (item.isScam) "Trying to trick me" else "Fine")
        val safe = shuffledActions(item).first { it.isSafe }
        click(safe.label)
        if (shoot) shot("practice-before-check")
        click("Check my answer")
        waitText(if (item.heldOut) "See how you did" else "Next email")
        if (shoot) shot("practice-feedback")
        click(if (item.heldOut) "See how you did" else "Next email")
        return support
    }

    // ---- tests ------------------------------------------------------------------------------------

    @Test
    fun guestOfflineFlow() = runComposeUiTest(testTimeout = 4.minutes) {
        val app = newApp("http://127.0.0.1:9")
        setContent { AppRoot(app) }
        waitUntil(timeoutMillis = 10_000) { !app.online.value }

        // 1. Language: English picked from the platform locale even on a French JVM.
        waitText("Choose your language")
        shot("01-language")
        waitText("127.0.0.1:9", substring = true) // debug footer: backend
        waitText("offline", substring = true)     // debug footer: status
        click("Continue")

        // 2. Email: offline → guest.
        waitScreen(app) { it == Screen.Email }
        shot("02-email-offline")
        click("Continue without an account")

        // 3. Onboarding (two questions): text size answered, past experience skipped.
        waitScreen(app) { it == Screen.Onboarding(0) }
        click("No, keep it as it is")
        shot("03-onboarding-large-text")
        click("Continue")
        waitScreen(app) { it == Screen.Onboarding(1) }
        shot("03-onboarding-past")
        click("Skip")

        // 4. Terms.
        waitScreen(app) { it == Screen.Terms }
        click("Read the terms")
        shot("04-terms-open")
        click("I accept the terms of use")
        click("Start")

        // 5. Registration lands on Home; the placement test is an optional card there.
        waitScreen(app) { it == Screen.Home }
        waitText("Boardroom crisis")
        click("Start the test")
        waitScreen(app) { it == Screen.PlacementIntro }
        waitText("Two emails. About a minute. No score.")
        shot("05-placement-intro")
        click("Start")
        waitScreen(app) { it == Screen.Placement }
        val pool = runBlocking { app.content.items("en") }
        val modes = placement(app, pool, knownLevel = 2)
        // The quick test: one scam/legit pair at difficulty 2, nothing else.
        assertEquals(setOf(ItemMode.VERDICT), modes)
        waitScreen(app) { it == Screen.PlacementResult }
        assertEquals(2, app.load(LearnKeys.PLACEMENT_SEEN, LearnKeys.STRING_SET)?.size)
        waitText("You know the basics")
        shot("06-placement-result")
        click("Start lesson 1")

        // 6. Lesson sections, then one practice item at PROMPTED, then Back.
        val lesson = lessonOf(app)
        lessonUntilPractice(app, lesson)
        shot("07-practice-first")
        val owner = PracticeController.lessonOwner("email-01")
        val supports = mutableListOf<Map<StepKey, Support>>()
        supports += practiceItem(app, pool, owner, shoot = true)
        assertTrue(supports[0].values.any { it == Support.PROMPTED }, "first item has a PROMPTED step: ${supports[0]}")
        shot("07-practice-second")

        // Back from the lesson goes to the previous lesson section, then Home.
        onNodeWithContentDescription("Back").performClick()
        waitScreen(app) { it == Screen.Lesson("email-01", 3) }
        app.navigateRoot(Screen.Home)
        waitScreen(app) { it == Screen.Home }
        waitText("Spot a phishing email")
        shot("08-home-guest-offline")

        // 7. Settings: dark mode + large text apply app-wide; French switches the UI.
        click("Settings")
        waitScreen(app) { it == Screen.Settings }
        click("Dark")
        click("Bigger text")
        shot("09-settings-dark-large")
        assertEquals(true, app.profile.value.darkMode)
        assertTrue(app.profile.value.largeText)
        onNodeWithContentDescription("Back").performClick()
        waitScreen(app) { it == Screen.Home }
        shot("10-home-dark-large")
        app.navigate(Screen.Lesson("email-01", 4))
        waitScreen(app) { it is Screen.Lesson }
        shot("11-practice-dark-large")
        app.back()
        click("Settings")
        click("Français")
        waitText("Langue")
        shot("12-settings-french")
        assertEquals("fr", app.lang.value)
    }

    /** Regression: an unknown or deleted token must not say "this was the practice email you asked for". */
    @Test
    fun revealOfUnknownTokenMakesNoClaim() = runComposeUiTest {
        // Rendered without the app's language wrapper: pin English so the asserted copy matches.
        Locale.setDefault(Locale.UK)
        setContent { tea.ui.theme.TeaTheme(dark = false) { tea.ui.screens.reveal.RevealContent(null, notFound = true, online = true, onPractise = {}) } }
        waitText("We could not find this practice email", substring = true)
        assertTrue(onAllNodesWithText("We only stored", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText("Got you this time", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun registeredFlow() = runComposeUiTest(testTimeout = 6.minutes) {
        val log = System.getenv("TEA_LIVE_LOG")?.let(::File)
        if (log == null) {
            println("[ui] registeredFlow skipped: set TEA_LIVE_LOG (and run the server)")
            return@runComposeUiTest
        }
        val api = System.getenv("TEA_LIVE_API") ?: "http://localhost:8080"
        val platform = FakePlatformInfo(locale = "en-GB", envApi = api)
        val app = newApp(api, platform)
        setContent { AppRoot(app) }
        waitUntil(timeoutMillis = 10_000) { app.online.value }
        fun mailText(email: String, marker: String): String? {
            val text = log.readText()
            var at = text.indexOf("To: $email")
            var found: String? = null
            while (at >= 0) {
                val chunk = text.substring(at, minOf(text.length, at + 1500))
                if (marker in chunk) found = chunk
                at = text.indexOf("To: $email", at + 1)
            }
            return found
        }
        val email = "ui-${System.currentTimeMillis()}@mail.example"

        click("Continue")
        waitScreen(app) { it == Screen.Email }
        type(0, email)
        click("Send me a code")
        waitScreen(app) { it is Screen.Code }
        waitUntil(timeoutMillis = 10_000) { mailText(email, "code") != null }
        val code = Regex("\\b(\\d{6})\\b").find(mailText(email, "code")!!)!!.groupValues[1]
        shot("r01-code")
        type(0, code) // auto-advances on the 6th digit
        waitScreen(app) { it == Screen.Name }
        type(0, "Uma")
        type(1, "Ito")
        shot("r02-name")
        click("Continue")
        waitScreen(app) { it == Screen.Password }
        type(0, "password1234")
        click("Save password")
        waitText("This password appears in known data breaches. Please choose another one.")
        shot("r03-password-breached")
        type(0, "Kettle whistles at 7 near the lake")
        click("Save password")
        waitScreen(app) { it == Screen.Onboarding(0) }
        repeat(2) { i ->
            waitScreen(app) { it == Screen.Onboarding(i) }
            click("Skip")
        }
        waitScreen(app) { it == Screen.Terms }
        click("I accept the terms of use")
        click("Start")
        waitScreen(app) { it == Screen.Home }
        click("Start the test")
        waitScreen(app) { it == Screen.PlacementIntro }
        click("Start")
        val pool = runBlocking { app.content.items("en") }
        placement(app, pool, knownLevel = 2)
        waitScreen(app) { it == Screen.PlacementResult }
        click("Start lesson 1")
        val lesson = lessonOf(app)
        lessonUntilPractice(app, lesson)

        // The whole practice section through the UI; support must fade.
        val owner = PracticeController.lessonOwner("email-01")
        val supports = mutableListOf<Map<StepKey, Support>>()
        var guard = 0
        while (app.load(LearnKeys.practiceRun(owner), PracticeRun.serializer())?.finished != true) {
            supports += practiceItem(app, pool, owner, shoot = false)
            check(++guard < 20)
        }
        println("[ui] practice supports: " + supports.joinToString(" | ") { s -> StepKey.entries.joinToString(",") { s.getValue(it).name.take(1) } })
        assertTrue(supports.last().values.all { it == Support.FADED }, "held-out item fully faded")
        waitText("Practice done")
        shot("r04-summary")
        click("Continue")
        waitScreen(app) { it == Screen.Lesson("email-01", 5) }
        val readiness = lesson.sections.filterIsInstance<ReadinessSection>().single()
        waitText(readiness.button)
        shot("r05-readiness")
        click(readiness.button)
        waitText(readiness.done, timeoutMs = 10_000)
        shot("r06-readiness-done")

        // The practice email arrives (server delay), its link opens the Reveal screen via the hash.
        waitUntil(timeoutMillis = 150_000) { mailText(email, "/f/c/") != null }
        val token = Regex("/f/c/([A-Za-z0-9_-]{20,100})").find(mailText(email, "/f/c/")!!)!!.groupValues[1]
        val conn = java.net.URI("$api/f/c/$token").toURL().openConnection() as java.net.HttpURLConnection
        conn.instanceFollowRedirects = false
        val location = conn.getHeaderField("Location")
        assertEquals(302, conn.responseCode)
        platform.userSetsHash(location.substringAfter('#'))
        waitScreen(app) { it == Screen.Reveal(token) }
        waitText("Practice email")
        shot("r07-reveal")
        app.navigateRoot(Screen.Home)
        waitText("You clicked the practice email", timeoutMs = 10_000)
        shot("r08-home-caught")

        // Leaderboard shows the learner with the same XP as the device.
        click("Leaderboard")
        waitScreen(app) { it == Screen.Leaderboard }
        waitText("All time")
        shot("r09-leaderboard-first")
        waitText("Uma I. (You)", substring = true)
        waitText("${app.xp.value} XP")
        // Only one row is "You", even when another learner has the same name and XP.
        assertEquals(1, onAllNodesWithText("(You)", substring = true).fetchSemanticsNodes().size)
        shot("r09-leaderboard")
        onNodeWithContentDescription("Back").performClick()

        // What we know about you (server part).
        click("What we know about you")
        waitScreen(app) { it == Screen.About }
        waitText("Device settings", substring = true)
        shot("r10-about")
        onNodeWithContentDescription("Back").performClick()

        // Settings: practice emails can be paused; the server agrees.
        click("Settings")
        waitScreen(app) { it == Screen.Settings }
        waitText("Paused")
        click("Paused")
        waitUntil(timeoutMillis = 10_000) { runBlocking { (app.practiceSettings() as? tea.net.ApiResult.Ok)?.value?.practiceEmails } == "paused" }
        shot("r11-settings-paused")

        // Delete my account: confirm dialog → back to the Language screen, nothing left locally.
        click("Delete my account")
        waitText("Delete your account?")
        shot("r12-delete-dialog")
        click("Delete")
        waitScreen(app) { it == Screen.Language }
        assertTrue(app.local.session == null && app.local.attempts.isEmpty())
        shot("r13-after-delete")
    }
}
