package tea.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tea.model.Skill
import tea.model.StepKey
import tea.model.Support
import tea.model.Verdict

class BktScaffoldTest {
    private fun close(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-9, "expected $expected, got $actual")

    @Test
    fun updateFormula() {
        close(0.6857142857142857, Bkt.update(0.5, true, 0.5))
        close(0.26666666666666666, Bkt.update(0.5, false, 0.5))
        // edge cases stay in 0..1
        assertTrue(Bkt.update(0.0, false, 1.0) in 0.0..1.0)
        assertTrue(Bkt.update(1.0, true, 0.0) in 0.0..1.0)
    }

    @Test
    fun guessesByQuestionType() {
        val item = Fx.email("g")
        assertEquals(0.5, Bkt.guess(item, ItemMode.VERDICT))
        assertEquals(0.2, Bkt.guess(item, ItemMode.TAP_HOTSPOT))
        assertEquals(0.25, Bkt.guess(item, ItemMode.LINK))
        assertEquals(1.0 / 3, Bkt.guess(item, ItemMode.EMOTION))
        assertEquals(0.5, Bkt.guess(item, ItemMode.ACTION))
    }

    @Test
    fun priors() {
        assertEquals(listOf(0.10, 0.20, 0.35, 0.50, 0.65, 0.75, 0.85), (1..7).map { Bkt.prior(it) })
    }

    // ---- evidence rules through Practice.applyAnswer ----

    private val item = Fx.email("e1", scam = true, expectedMs = 10_000)
    private val promptedAll = StepKey.entries.associateWith { Support.PROMPTED }

    private fun apply(
        answer: PracticeAnswer,
        mastery: Map<Skill, tea.model.MasteryState> = Fx.mastery(0.5),
        session: PracticeSessionState = Practice.startSession(count = 8),
    ) = Practice.applyAnswer(item, answer, mastery, TrapState(), ShortcutStats(), session, Fx.NOW, Fx.TODAY, "id")

    private fun right(support: Map<StepKey, Support> = promptedAll, ms: Long = 5_000, hints: Set<StepKey> = emptySet()) =
        PracticeAnswer(
            support = support, verdict = Verdict.TRICK, actionId = "open-app", ms = ms,
            stepChoices = support.filterValues { it == Support.PROMPTED }.mapValues { 1 }, hintsUsed = hints,
        )

    @Test
    fun workedGivesNoEvidence() {
        val out = apply(right(support = StepKey.entries.associateWith { Support.WORKED }))
        for (step in StepKey.entries) {
            // No evidence (no attempt, no unaided, not in stepCorrect), only the learning transition.
            close(0.5 + 0.5 * Bkt.LEARN, out.mastery.getValue(step.skill).p)
            assertEquals(0, out.mastery.getValue(step.skill).attempts)
            assertEquals(0, out.mastery.getValue(step.skill).unaided)
        }
        assertTrue(out.stepCorrect.isEmpty())
        assertEquals(1, out.mastery.getValue(Skill.VERDICT).attempts) // verdict still counts
    }

    /** Regression (end-to-end run): a step under 0.35 stayed WORKED forever because WORKED gave no update at all. */
    @Test
    fun workedExamplesLeadToPrompted() {
        var mastery = Fx.mastery(0.2).mapValues { (_, m) -> m.copy(support = Scaffold.levelFor(m.p)) }
        val worked = StepKey.entries.associateWith { Support.WORKED }
        var rounds = 0
        while (mastery.getValue(Skill.EMOTION).support == Support.WORKED) {
            mastery = apply(right(support = worked), mastery).mastery
            assertTrue(++rounds <= 3, "still WORKED after $rounds worked examples")
        }
        assertEquals(2, rounds)
        assertEquals(Support.PROMPTED, mastery.getValue(Skill.EMOTION).support)
    }

    @Test
    fun rightAnswerRaisesAndCountsUnaided() {
        val out = apply(right())
        for (step in StepKey.entries) {
            val m = out.mastery.getValue(step.skill)
            close(Bkt.update(0.5, true, 1.0 / 3), m.p)
            assertEquals(1, m.attempts)
            assertEquals(1, m.unaided)
        }
    }

    @Test
    fun hintCountsAsWrong() {
        val out = apply(right(hints = setOf(StepKey.WHO)))
        val who = out.mastery.getValue(Skill.WHO)
        close(Bkt.update(0.5, false, 1.0 / 3), who.p)
        assertEquals(0, who.unaided)
        assertEquals(false, out.stepCorrect[StepKey.WHO])
        assertEquals(true, out.stepCorrect[StepKey.ASK])
    }

    @Test
    fun slowCountsAsWrongExceptFirstItem() {
        val first = apply(right(ms = 50_000))
        assertTrue(first.stepCorrect.values.all { it })
        val later = apply(right(ms = 50_000), session = first.session)
        assertTrue(later.stepCorrect.values.none { it })
        assertTrue(later.mastery.getValue(Skill.VERDICT).p < first.mastery.getValue(Skill.VERDICT).p)
    }

    @Test
    fun dontKnowCountsAsWrong() {
        val a = right().copy(stepChoices = mapOf(StepKey.WHO to null, StepKey.EMOTION to 1, StepKey.ASK to 1, StepKey.VERIFY to 1))
        val out = apply(a)
        assertEquals(false, out.stepCorrect[StepKey.WHO])
        val notSure = apply(right().copy(verdict = Verdict.NOT_SURE))
        assertTrue(notSure.mastery.getValue(Skill.VERDICT).p < 0.5)
        assertEquals(false, notSure.verdictCorrect)
    }

    @Test
    fun fadedStepsUseVerdictAndAction() {
        val faded = StepKey.entries.associateWith { Support.FADED }
        val out = apply(PracticeAnswer(support = faded, verdict = Verdict.TRICK, actionId = "click", ms = 5_000))
        assertEquals(true, out.stepCorrect[StepKey.WHO])
        assertEquals(false, out.stepCorrect[StepKey.VERIFY]) // unsafe action
        assertEquals(1, out.mastery.getValue(Skill.WHO).unaided)
    }

    @Test
    fun difficultyLevelMovesAfterTwoInARow() {
        var mastery = Fx.mastery(0.5, level = 3)
        var session = Practice.startSession(count = 8)
        fun step(verdict: Verdict, ms: Long) {
            val out = Practice.applyAnswer(
                item, right(ms = ms).copy(verdict = verdict), mastery, TrapState(), ShortcutStats(), session, Fx.NOW, Fx.TODAY, "x",
            )
            mastery = out.mastery; session = out.session
        }
        step(Verdict.TRICK, 5_000); assertEquals(3, mastery.getValue(Skill.VERDICT).level)
        step(Verdict.TRICK, 5_000); assertEquals(4, mastery.getValue(Skill.VERDICT).level)
        step(Verdict.TRICK, 15_000) // right but not fast: resets
        step(Verdict.TRICK, 5_000); assertEquals(4, mastery.getValue(Skill.VERDICT).level)
        step(Verdict.FINE, 5_000); step(Verdict.FINE, 5_000); assertEquals(3, mastery.getValue(Skill.VERDICT).level)
        // clamp
        mastery = Fx.mastery(0.5, level = 1)
        step(Verdict.FINE, 5_000); step(Verdict.FINE, 5_000); assertEquals(1, mastery.getValue(Skill.VERDICT).level)
    }

    @Test
    fun masteredRule() {
        val m = Fx.mastery(0.96).getValue(Skill.WHO)
        assertTrue(!m.mastered)
        assertTrue(m.copy(unaided = 3).mastered)
    }

    // ---- support level + hysteresis ----

    @Test
    fun supportLevels() {
        assertEquals(Support.WORKED, Scaffold.levelFor(0.34))
        assertEquals(Support.PROMPTED, Scaffold.levelFor(0.35))
        assertEquals(Support.HINT, Scaffold.levelFor(0.6))
        assertEquals(Support.FADED, Scaffold.levelFor(0.8))
    }

    @Test
    fun hysteresis() {
        assertEquals(Support.PROMPTED, Scaffold.supportFor(Support.WORKED, 0.35))
        assertEquals(Support.WORKED, Scaffold.supportFor(Support.WORKED, 0.34))
        assertEquals(Support.PROMPTED, Scaffold.supportFor(Support.PROMPTED, 0.31)) // inside the 0.05 band
        assertEquals(Support.WORKED, Scaffold.supportFor(Support.PROMPTED, 0.29))
        assertEquals(Support.FADED, Scaffold.supportFor(Support.FADED, 0.76))
        assertEquals(Support.HINT, Scaffold.supportFor(Support.FADED, 0.74))
        assertEquals(Support.PROMPTED, Scaffold.supportFor(Support.FADED, 0.5)) // several levels at once
        assertEquals(Support.FADED, Scaffold.supportFor(Support.WORKED, 0.9))
        assertEquals(Support.HINT, Scaffold.supportFor(Support.HINT, 0.79))
    }

    @Test
    fun supportMapUsesStoredSupportAndHeldOutIsFaded() {
        val mastery = Fx.mastery(0.5).toMutableMap()
        mastery[Skill.WHO] = mastery.getValue(Skill.WHO).copy(support = Support.HINT)
        val map = Scaffold.supportMap(Fx.email("s"), mastery)
        assertEquals(Support.HINT, map[StepKey.WHO])
        assertEquals(Support.PROMPTED, map[StepKey.ASK])
        val held = Scaffold.supportMap(Fx.email("h", heldOut = true), mastery)
        assertTrue(held.values.all { it == Support.FADED })
    }

    @Test
    fun supportIsStoredWithHysteresisAfterAnswers() {
        // p just above PROMPTED's threshold; a wrong answer drops p but support only falls below 0.30
        val mastery = Fx.mastery(0.36, support = Support.PROMPTED)
        val wrong = PracticeAnswer(
            support = promptedAll, verdict = Verdict.TRICK, actionId = "open-app", ms = 5_000,
            stepChoices = StepKey.entries.associateWith { 0 },
        )
        val out = apply(wrong, mastery = mastery)
        val who = out.mastery.getValue(Skill.WHO)
        assertTrue(who.p < 0.36)
        assertEquals(Scaffold.supportFor(Support.PROMPTED, who.p), who.support)
    }
}
