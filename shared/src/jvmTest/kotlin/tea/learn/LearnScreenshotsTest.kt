package tea.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import tea.app.FakeTeaApp
import tea.engine.ItemMode
import tea.engine.Mastery
import tea.engine.PracticeAnswer
import tea.model.LESSON_1_ID
import tea.model.PracticeSection
import tea.model.Profile
import tea.model.ReadinessResponse
import tea.model.Skill
import tea.model.StepKey
import tea.model.Support
import tea.model.Verdict
import tea.ui.Screenshots
import tea.ui.components.ScreenColumn
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.learn.LessonScreen
import tea.ui.screens.learn.PlacementIntroScreen
import tea.ui.screens.learn.PlacementResultScreen
import tea.ui.screens.learn.PracticeSummaryScreen
import tea.ui.screens.lesson.AttacksSectionView
import tea.ui.screens.lesson.HuntUi
import tea.ui.screens.placement.PlacementController
import tea.ui.screens.placement.PlacementRun
import tea.ui.screens.practice.FullItemUi
import tea.ui.screens.practice.ItemRenderer
import tea.ui.screens.practice.PracticeController
import tea.ui.screens.practice.PracticeRunner
import tea.ui.screens.practice.SingleItemUi
import tea.ui.screens.practice.rememberFilled
import tea.ui.theme.TeaTheme
import tea.model.AttacksSection
import tea.model.Confidence

/**
 * Renders every learning screen and state to `shared/build/screenshots/learn_*.png` with the real
 * content and a [FakeTeaApp], to look at them (they are not compared against references).
 */
class LearnScreenshotsTest {
    private val repo = TestContent.repository()
    private val pool = TestContent.items()
    private val lesson = TestContent.lesson()
    private val start = Instant.parse("2026-11-21T10:00:00Z")

    private fun app(p: Double = 0.36, debug: Boolean = false, profile: Profile = Profile(firstName = "Sam", country = "CH")) =
        FakeTeaApp(
            repo, profile = profile, debug = debug, clock = start, xp = 140,
            mastery = Skill.entries.associateWith { Mastery.initial(it, start, p = if (it.step != null) p else 0.3, level = 2) },
        )

    private fun shot(name: String, dark: Boolean = false, width: Int = 760, height: Int = 2200, content: @Composable () -> Unit) {
        val files = Screenshots.render("learn_$name", widthDp = width, heightDp = height, density = 1f, content = {
            TeaTheme(dark = dark) { content() }
        })
        assertTrue(files.all { it.length() > 0 })
    }

    // ---- placement ----

    @Test fun placementIntro() = shot("placement_intro", height = 900) { PlacementIntroScreen(app()) }

    private fun placementAt(mode: ItemMode): Pair<FakeTeaApp, PlacementController> {
        val app = app()
        val c = PlacementController(app, pool, Random(1))
        var n = 0
        while (c.current!!.mode != mode && n++ < 20) c.answer(c.current!!, Oracle.right(c.current!!))
        return app to c
    }

    @Test fun placementModes() {
        for (mode in listOf(ItemMode.VERDICT, ItemMode.TAP_HOTSPOT, ItemMode.LINK, ItemMode.EMOTION, ItemMode.ACTION, ItemMode.NEWS, ItemMode.IMAGE)) {
            val (app, c) = placementAt(mode)
            shot("placement_${mode.name.lowercase()}", height = 1500) { PlacementRun(app, c) }
        }
    }

    @Test fun placementAnsweredWithConfidence() {
        val (app, c) = placementAt(ItemMode.TAP_HOTSPOT)
        val step = c.current!!
        val ui = SingleItemUi(step.item, step.mode).apply {
            tap(step.item.hotspots.first { it.redFlag }.id)
            tap("decoy:greeting")
            confidence = Confidence.FAIRLY_SURE
        }
        shot("placement_tap_answered", height = 1500) {
            ScreenColumn { ItemRenderer(rememberFilled(app, step.item), step.mode, emptyMap(), {}, app::now, single = ui) }
        }
        shot("placement_tap_answered_compact", width = 380, height = 1900) {
            ScreenColumn { ItemRenderer(rememberFilled(app, step.item), step.mode, emptyMap(), {}, app::now, single = ui) }
        }
    }

    @Test fun placementResult() {
        val (app, c) = placementAt(ItemMode.IMAGE)
        while (c.current != null) c.answer(c.current!!, Oracle.right(c.current!!))
        c.finish()
        shot("placement_result", height = 1000) { PlacementResultScreen(app) }
        shot("placement_result_dark", dark = true, height = 1000) { PlacementResultScreen(app) }
    }

    // ---- lesson sections ----

    @Test fun lessonSections() {
        for (i in lesson.sections.indices) {
            val app = app()
            shot("lesson_${i}_${lesson.sections[i]::class.simpleName}", height = if (i == 2) 3400 else 2000) { LessonScreen(app, LESSON_1_ID, i) }
        }
        shot("lesson_0_dark", dark = true) { LessonScreen(app(), LESSON_1_ID, 0) }
        shot("lesson_0_compact", width = 380, height = 2600) { LessonScreen(app(), LESSON_1_ID, 0) }
        shot("lesson_1_compact", width = 380, height = 2000) { LessonScreen(app(), LESSON_1_ID, 1) }
    }

    @Test fun lessonAttacksStates() {
        val section = lesson.sections.filterIsInstance<AttacksSection>().single()
        val item = pool.first { it.id == section.hunt.itemId }
        val app = app()
        val partly = HuntUi(section.hunt).apply {
            tap("hs-from-address", item)
            tap("hs-greeting", item)
        }
        shot("lesson_attacks_partly", height = 3200) { ScreenColumn { AttacksSectionView(app, lesson, section, 3, partly) } }
        val done = HuntUi(section.hunt).apply {
            tap("hs-clock", item)
            giveUp()
        }
        app.fieldClicked.value = true
        app.save(LearnKeys.recovery(LESSON_1_ID), LearnKeys.INT_LIST, listOf(0, 2))
        shot("lesson_attacks_done_caught", height = 3600) { ScreenColumn { AttacksSectionView(app, lesson, section, 3, done) } }
        shot("lesson_attacks_done_dark_compact", dark = true, width = 380, height = 4800) {
            ScreenColumn { AttacksSectionView(app, lesson, section, 3, remember { done }) }
        }
    }

    @Test fun lessonStatusAndReadiness() {
        val rated = app().apply { updateProfile { it.copy(statusRating = "HIGH") } }
        shot("lesson_status_high", height = 1100) { LessonScreen(rated, LESSON_1_ID, 3) }
        val readiness = lesson.sections.lastIndex
        shot("lesson_readiness_offline", height = 1100) { LessonScreen(app().apply { online.value = false }, LESSON_1_ID, readiness) }
        shot("lesson_readiness_guest", height = 1100) {
            LessonScreen(app().apply { loggedIn.value = false; email.value = null }, LESSON_1_ID, readiness)
        }
        shot("lesson_readiness_scheduled", height = 1100) {
            LessonScreen(app().apply { save(LearnKeys.lessonReadiness(LESSON_1_ID), LearnKeys.STRING, "scheduled") }, LESSON_1_ID, readiness)
        }
        shot("lesson_readiness_paused", height = 1100) {
            LessonScreen(app().apply { save(LearnKeys.lessonReadiness(LESSON_1_ID), LearnKeys.STRING, "reason:paused") }, LESSON_1_ID, readiness)
        }
    }

    // ---- practice ----

    private fun controller(app: FakeTeaApp, section: PracticeSection = PracticeSection(count = 8)) =
        PracticeController(app, pool, PracticeController.lessonOwner(LESSON_1_ID), section, lessonId = LESSON_1_ID, random = Random(5))

    @Test fun practiceSupportLevels() {
        for ((name, p) in listOf("worked" to 0.2, "prompted" to 0.45, "hint" to 0.7, "faded" to 0.9)) {
            val app = app(p)
            val c = controller(app)
            shot("practice_$name", height = 2600) { PracticeRunner(app, c) {} }
        }
        val app = app(0.2)
        val c = controller(app)
        shot("practice_worked_compact_dark", dark = true, width = 380, height = 2800) { PracticeRunner(app, c) {} }
    }

    @Test fun practicePromptedWrongTwice() {
        val app = app(0.45)
        val c = controller(app)
        val step = c.current!!
        val content = step.item.steps.getValue(StepKey.WHO)
        val wrong = content.choices.indices.filter { it != content.correct }
        val once = FullItemUi(step.item, step.support).apply { choose(StepKey.WHO, wrong[0]) }
        shot("practice_prompted_wrong_once", height = 2400) {
            ScreenColumn { ItemRenderer(rememberFilled(app, step.item), ItemMode.FULL, step.support, {}, app::now, full = once) }
        }
        val twice = FullItemUi(step.item, step.support).apply { choose(StepKey.WHO, wrong[0]); choose(StepKey.WHO, wrong[1]) }
        shot("practice_prompted_revealed", height = 2400) {
            ScreenColumn { ItemRenderer(rememberFilled(app, step.item), ItemMode.FULL, step.support, {}, app::now, full = twice) }
        }
        val hint = app(0.7)
        val hc = controller(hint)
        val hs = hc.current!!
        val opened = FullItemUi(hs.item, hs.support).apply {
            openHint(StepKey.EMOTION)
            verdict = Verdict.TRICK
            actionId = hs.item.actions.first().id
        }
        shot("practice_hint_opened", height = 2600) {
            ScreenColumn { ItemRenderer(rememberFilled(hint, hs.item), ItemMode.FULL, hs.support, {}, hint::now, full = opened) }
        }
    }

    @Test fun practiceFeedback() {
        // Wrong verdict + unsafe action on a scam, debug build (resource card with "not yet checked").
        val app = app(0.9, debug = true)
        val c = controller(app)
        var step = c.current!!
        while (!step.item.isScam || step.item.actions.none { it.isUnsafe }) {
            c.submit(PracticeAnswer(step.support, if (step.item.isScam) Verdict.TRICK else Verdict.FINE, step.item.actions.first { it.isSafe }.id, 5000))
            c.advance()
            step = c.current!!
        }
        c.submit(
            PracticeAnswer(step.support, Verdict.FINE, step.item.actions.first { it.isUnsafe }.id, 5000, freeText = "The sender address looks odd and it rushes me"),
        )
        shot("practice_feedback_wrong_unsafe", height = 3000) { PracticeRunner(app, c) {} }

        // Right answer on a legit item, gentle tone, release build (no unverified links).
        val gentle = app(0.9, profile = Profile(firstName = "Sam", country = "CH", pastExperience = "lost_money"))
        val g = controller(gentle)
        var gs = g.current!!
        while (gs.item.isScam) {
            g.submit(PracticeAnswer(gs.support, Verdict.TRICK, gs.item.actions.first { it.isSafe }.id, 5000))
            g.advance()
            gs = g.current!!
        }
        g.submit(PracticeAnswer(gs.support, Verdict.FINE, gs.item.actions.first { it.isSafe }.id, 5000))
        shot("practice_feedback_right_legit_gentle", height = 2600) { PracticeRunner(gentle, g) {} }
    }

    @Test fun practiceHeldOutAndSummary() {
        val app = app(0.5)
        val c = controller(app)
        while (c.current?.heldOut == false) {
            val s = c.current!!
            c.submit(
                PracticeAnswer(
                    s.support,
                    if (s.item.isScam) Verdict.TRICK else Verdict.FINE,
                    s.item.actions.first { it.isSafe }.id,
                    5000,
                    stepChoices = s.support.filterValues { it == Support.PROMPTED }.keys.associateWith { k -> s.item.steps.getValue(k).correct },
                ),
            )
            c.advance()
        }
        shot("practice_held_out", height = 2600) { PracticeRunner(app, c) {} }
        val s = c.current!!
        c.submit(PracticeAnswer(s.support, Verdict.TRICK, s.item.actions.first { it.isSafe }.id, 5000))
        c.advance()
        shot("practice_summary_in_lesson", height = 2200) { LessonScreen(app, LESSON_1_ID, lesson.sections.indexOfFirst { it is PracticeSection }) }
        shot("practice_summary_screen", height = 2000) { PracticeSummaryScreen(app) }
        shot("practice_summary_screen_dark_compact", dark = true, width = 380, height = 2400) { PracticeSummaryScreen(app) }
    }

    @Test fun readinessPressed() {
        val app = app().apply { readiness = ReadinessResponse(false, "already_done") }
        shot("lesson_readiness_default", height = 1100) { LessonScreen(app, LESSON_1_ID, lesson.sections.lastIndex) }
    }
}
