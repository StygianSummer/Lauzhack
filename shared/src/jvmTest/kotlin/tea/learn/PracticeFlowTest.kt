package tea.learn

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import tea.app.FakeTeaApp
import tea.engine.Mastery
import tea.engine.PracticeAnswer
import tea.engine.PracticeStep
import tea.engine.StreakState
import tea.engine.Xp
import tea.model.Context
import tea.model.EventTypes
import tea.model.PracticeSection
import tea.model.Skill
import tea.model.StepKey
import tea.model.Support
import tea.model.Verdict
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.practice.PracticeController
import tea.ui.screens.practice.PracticeRun
import tea.ui.screens.practice.SummaryData

/** Headless run of a full lesson practice section (plan §9.5) on the real bundled content. */
class PracticeFlowTest {
    private val pool = TestContent.items()
    private val lesson = TestContent.lesson()
    private val section = lesson.sections.filterIsInstance<PracticeSection>().single()
    private val start = Instant.parse("2026-11-21T10:00:00Z")

    /** Every step at p = 0.36 (PROMPTED), as after a low placement. */
    private fun app(): FakeTeaApp {
        val mastery = Skill.entries.associateWith { Mastery.initial(it, start, p = if (it.step != null) 0.36 else 0.2, level = 2) }
        return FakeTeaApp(TestContent.repository(), clock = start, mastery = mastery)
    }

    /** A perfect answer: right choice on every step asked, right verdict, a safe action, no hints, fast. */
    private fun perfect(step: PracticeStep): PracticeAnswer {
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

    @Test
    fun promptsFadeAndTheHeldOutItemComesLastWithNoSupport() {
        val app = app()
        val c = PracticeController(app, pool, PracticeController.lessonOwner(lesson.id), section, lessonId = lesson.id, random = Random(7))
        val shown = mutableListOf<PracticeStep>()
        var guard = 0
        while (!c.finished) {
            val step = assertNotNull(c.current)
            shown += step
            c.submit(perfect(step))
            assertNotNull(c.lastOutcome)
            app.advance(30.seconds)
            c.advance()
            assertTrue(++guard < 30, "section does not end")
        }

        val regular = shown.filter { !it.heldOut }
        assertEquals(section.count, regular.size)
        assertTrue(shown.last().heldOut && shown.last().item.heldOut, "held-out item last")
        assertEquals(1, shown.count { it.heldOut })
        assertTrue(shown.last().support.values.all { it == Support.FADED }, "held-out all FADED")

        // Fading: no step ever gets more support than before, and every step ends FADED.
        assertTrue(regular.first().support.values.all { it == Support.PROMPTED }, "starts PROMPTED: ${regular.first().support}")
        for (step in StepKey.entries) {
            val levels = regular.map { it.support.getValue(step).ordinal }
            assertEquals(levels.sorted(), levels, "$step support never goes back up: $levels")
        }
        assertTrue(regular.last().support.values.all { it == Support.FADED }, "ends FADED: ${regular.last().support}")
        assertTrue(Mastery.stepP(app.mastery.value).values.all { it > 0.8 })

        // Writes.
        assertEquals(shown.size, app.attempts.size)
        assertTrue(app.attempts.all { it.context == Context.PRACTICE && it.correct })
        assertEquals(section.count, app.eventsOf(EventTypes.PRACTICE_ITEM_ANSWERED).size)
        assertEquals(1, app.eventsOf(EventTypes.HELD_OUT_ANSWERED).size)
        val completed = app.eventsOf(EventTypes.LESSON_COMPLETED).single()
        assertEquals(setOf("who", "emotion", "ask", "verify"), completed.payload["per_step"]!!.jsonObject.keys)
        val summary = assertNotNull(app.load(LearnKeys.PRACTICE_SUMMARY, SummaryData.serializer()))
        assertEquals(true, summary.heldOutCorrect)
        assertEquals(Xp.LESSON_COMPLETED, summary.lessonBonus)
        assertTrue(summary.after.getValue(StepKey.WHO) > summary.before.getValue(StepKey.WHO))
        assertEquals(summary.xp + Xp.LESSON_COMPLETED, app.xp.value)
        assertTrue(summary.xp >= section.count * (Xp.VERDICT + Xp.SAFE_ACTION) + Xp.HELD_OUT)
        assertEquals(1, app.load(LearnKeys.STREAK, StreakState.serializer())?.current)

        // Coming back to the lesson shows the summary, and lesson_completed is not sent twice.
        val again = PracticeController(app, pool, PracticeController.lessonOwner(lesson.id), section, lessonId = lesson.id)
        assertTrue(again.finished)
        assertNotNull(again.summary)
        val restarted = PracticeController(app, pool, PracticeController.lessonOwner(lesson.id), section, lessonId = lesson.id, restart = true)
        assertTrue(!restarted.finished && restarted.current != null)
        while (!restarted.finished) {
            restarted.submit(perfect(restarted.current!!))
            restarted.advance()
        }
        assertEquals(1, app.eventsOf(EventTypes.LESSON_COMPLETED).size)
    }

    @Test
    fun aReloadResumesTheSameItem() {
        val app = app()
        val owner = PracticeController.lessonOwner(lesson.id)
        val first = PracticeController(app, pool, owner, section, lessonId = lesson.id, random = Random(3))
        repeat(2) {
            first.submit(perfect(first.current!!))
            first.advance()
        }
        val onScreen = first.current!!.item.id

        val second = PracticeController(app, pool, owner, section, lessonId = lesson.id, random = Random(42))
        assertEquals(onScreen, second.current!!.item.id)
        assertEquals(2, second.practiceCount)
        assertEquals(2, app.load(LearnKeys.practiceRun(owner), PracticeRun.serializer())!!.session.shownCount)
    }

    @Test
    fun wrongAnswersKeepSupportAndOpenTraps() {
        val app = app()
        val c = PracticeController(app, pool, PracticeController.FREE_OWNER, PracticeSection(count = 6), random = Random(11))
        while (!c.finished) {
            val step = c.current!!
            val item = step.item
            c.submit(
                PracticeAnswer(
                    support = step.support,
                    verdict = if (item.isScam) Verdict.FINE else Verdict.TRICK,
                    actionId = item.actions.firstOrNull { it.isUnsafe }?.id ?: item.actions.first().id,
                    ms = 6_000,
                    stepChoices = step.support.filterValues { it == Support.PROMPTED }.keys
                        .associateWith { s -> (item.steps.getValue(s).correct + 1) % item.steps.getValue(s).choices.size },
                ),
            )
            c.advance()
        }
        val summary = assertNotNull(c.summary)
        assertEquals(false, summary.heldOutCorrect)
        assertTrue(summary.traps.isNotEmpty(), "wrong verdicts open traps")
        assertTrue(app.eventsOf(EventTypes.LESSON_COMPLETED).isEmpty(), "free practice is not a lesson")
        assertTrue(Mastery.stepP(app.mastery.value).values.all { it < 0.36 })
    }
}
