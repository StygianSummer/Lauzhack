package tea.engine

import kotlin.time.Duration.Companion.days
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.Confidence
import tea.model.Context
import tea.model.MasteryState
import tea.model.Skill
import tea.model.StepKey
import tea.model.Support
import tea.model.TeaJson
import tea.model.Verdict

/** Shortcut breaker, traps, XP, review, streak, calibration. */
class RulesTest {
    // ---- shortcut breaker ----

    @Test
    fun shortcutBreakerDetectsAndClears() {
        var s = ShortcutStats()
        val urgentScam = Fx.email("u", scam = true, clues = listOf("urgency", "odd_sender"))
        val urgentScam2 = Fx.email("u2", scam = true, clues = listOf("urgency"))
        val plainScam = Fx.email("p", scam = true, clues = listOf("lookalike_link"))
        s = ShortcutBreaker.record(s, urgentScam, Verdict.TRICK)
        s = ShortcutBreaker.record(s, urgentScam2, Verdict.TRICK)
        s = ShortcutBreaker.record(s, urgentScam, Verdict.FINE) // not a TRICK verdict: only `seen`
        s = ShortcutBreaker.record(s, plainScam, Verdict.TRICK)
        assertNull(s.request, "only 3 TRICK verdicts")
        s = ShortcutBreaker.record(s, urgentScam2, Verdict.TRICK)
        // urgency in 3 of 4 TRICK verdicts (75% >= 70%), flagged 3 times
        assertEquals(ShortcutRequest("urgency"), s.request)
        assertEquals(4, s.seen["urgency"])

        val legitUrgent = Fx.email("lu", scam = false, clues = listOf("urgency"))
        val scamNoUrgency = Fx.email("sn", scam = true, clues = listOf("lookalike_link"))
        assertTrue(ShortcutBreaker.matchesRequest(s, legitUrgent))
        assertFalse(ShortcutBreaker.matchesRequest(s, scamNoUrgency), "legit first")
        s = ShortcutBreaker.record(s, legitUrgent, Verdict.TRICK)
        assertEquals(true, s.request?.legitShown)
        assertTrue(ShortcutBreaker.matchesRequest(s, scamNoUrgency))
        assertFalse(ShortcutBreaker.matchesRequest(s, legitUrgent))
        s = ShortcutBreaker.record(s, scamNoUrgency, Verdict.TRICK)
        assertNull(s.request)
        assertEquals(0, s.trickVerdicts)
    }

    @Test
    fun shortcutNeedsSeventyPercent() {
        var s = ShortcutStats()
        repeat(2) { s = ShortcutBreaker.record(s, Fx.email("p$it", clues = listOf("link")), Verdict.TRICK) }
        repeat(3) { s = ShortcutBreaker.record(s, Fx.email("u$it", clues = listOf("urgency")), Verdict.TRICK) }
        assertNull(s.request) // 3/5 = 60%
        assertNull(ShortcutBreaker.dominantClue(s))
    }

    // ---- traps ----

    @Test
    fun trapsOpenAfterTwoMissesAndClearOnThreeDays() {
        val scam = Fx.email("s", scam = true, clues = listOf("display_name"))
        var t = TrapState()
        t = Traps.record(t, scam, Verdict.FINE, Fx.TODAY)
        assertTrue(t.active.isEmpty())
        t = Traps.record(t, scam, Verdict.NOT_SURE, Fx.TODAY)
        assertEquals(listOf("misses_display_name"), Traps.keys(t))
        assertTrue(Traps.exercises(t, scam))
        assertFalse(Traps.exercises(t, Fx.email("o", clues = listOf("urgency"))))

        val d1 = LocalDate.parse("2026-11-22")
        val d2 = LocalDate.parse("2026-11-23")
        val d3 = LocalDate.parse("2026-11-24")
        t = Traps.record(t, scam, Verdict.TRICK, d1)
        t = Traps.record(t, scam, Verdict.TRICK, d1) // same day counts once
        t = Traps.record(t, scam, Verdict.TRICK, d2)
        assertEquals(1, t.active.size)
        assertEquals(2, t.active.single().rightDays.size)
        t = Traps.record(t, scam, Verdict.TRICK, d3)
        assertTrue(t.active.isEmpty())
        assertNull(t.misses["display_name"])
    }

    @Test
    fun falseAlarmTraps() {
        val legit = Fx.email("l", scam = false, clues = listOf("urgency"))
        var t = TrapState()
        repeat(2) { t = Traps.record(t, legit, Verdict.TRICK, Fx.TODAY) }
        assertEquals(listOf("flags_urgency"), Traps.keys(t))
        assertEquals(TrapKind.FALSE_ALARM, t.active.single().kind)
        assertTrue(Traps.labels(t).single().contains("urgency"))
        assertEquals(t, TeaJson.decodeFromString(TrapState.serializer(), TeaJson.encodeToString(TrapState.serializer(), t)))
    }

    // ---- XP ----

    private fun attempt(item: tea.model.Item, answers: AttemptAnswers, correct: Boolean, scaffold: Map<StepKey, Support>, hints: Int = 0) =
        Attempt("c", item.id, Context.PRACTICE, answers, correct, null, scaffold, hints, 1_000, Fx.NOW)

    @Test
    fun xpRules() {
        val item = Fx.email("x")
        val prompted = StepKey.entries.associateWith { Support.PROMPTED }
        val all = attempt(item, AttemptAnswers(Verdict.TRICK, "open-app", StepKey.entries.associateWith { 1 }), true, prompted)
        assertEquals(10 + 5 + 4 * 2, Xp.xpFor(all, item))
        val wrongStepUnsafe = attempt(item, AttemptAnswers(Verdict.TRICK, "click", mapOf(StepKey.WHO to 0)), true, prompted)
        assertEquals(10, Xp.xpFor(wrongStepUnsafe, item))
        val hinted = attempt(item, AttemptAnswers(Verdict.TRICK, "open-app", mapOf(StepKey.WHO to 1)), true, prompted, hints = 1)
        assertEquals(15, Xp.xpFor(hinted, item))
        val held = Fx.email("h", heldOut = true)
        assertEquals(30, Xp.xpFor(attempt(held, AttemptAnswers(Verdict.TRICK, "open-app"), true, emptyMap()), held))
        assertEquals(0, Xp.xpFor(attempt(held, AttemptAnswers(Verdict.TRICK, "click"), true, emptyMap()), held))
        assertEquals(0, Xp.xpFor(all.copy(context = Context.PLACEMENT), item))
    }

    @Test
    fun practiceOutcomeCarriesXpAndAttempt() {
        val item = Fx.email("x")
        val session = Practice.startSession(count = 3)
        val support = StepKey.entries.associateWith { Support.PROMPTED }
        val out = Practice.applyAnswer(
            item,
            PracticeAnswer(support, Verdict.TRICK, "open-app", 5_000, stepChoices = StepKey.entries.associateWith { 1 }),
            Fx.mastery(0.5), TrapState(), ShortcutStats(), session, Fx.NOW, Fx.TODAY, "client-1",
        )
        assertEquals(23, out.xp)
        assertEquals(23, out.session.xp)
        assertEquals("client-1", out.attempt.clientId)
        assertEquals(Context.PRACTICE, out.attempt.context)
        assertEquals(support, out.attempt.scaffold)
        assertTrue(out.attempt.correct)
        assertTrue(Ids.newClientId().length == 36)
    }

    // ---- review ----

    @Test
    fun reviewIntervals() {
        val m = MasteryState(Skill.WHO, p = 0.5, updatedAt = Fx.NOW)
        val passed = Review.update(m, Tally(3, 4), Fx.NOW)
        assertEquals(2.5, passed.reviewInterval)
        assertEquals(Fx.NOW + 2.5.days, passed.nextReviewAt)
        val twice = Review.update(passed, Tally(4, 4), Fx.NOW)
        assertEquals(6.25, twice.reviewInterval)
        val failed = Review.update(twice, Tally(2, 4), Fx.NOW)
        assertEquals(1.0, failed.reviewInterval)
        assertEquals(Fx.NOW + 1.days, failed.nextReviewAt)
        assertEquals(m, Review.update(m, Tally(), Fx.NOW))

        val map = Fx.mastery(0.5)
        val after = Review.endOfSession(map, mapOf(Skill.WHO to Tally(1, 1)), Fx.NOW)
        assertEquals(2.5, after.getValue(Skill.WHO).reviewInterval)
        assertEquals(map.getValue(Skill.ASK), after.getValue(Skill.ASK))
        assertTrue(Review.isDue(m.copy(nextReviewAt = Fx.NOW), Fx.NOW))
        assertFalse(Review.isDue(passed, Fx.NOW))
    }

    @Test
    fun endSessionUsesTally() {
        val item = Fx.email("x")
        val out = Practice.applyAnswer(
            item,
            PracticeAnswer(StepKey.entries.associateWith { Support.FADED }, Verdict.TRICK, "open-app", 5_000),
            Fx.mastery(0.9), TrapState(), ShortcutStats(), Practice.startSession(mastery = Fx.mastery(0.9)), Fx.NOW, Fx.TODAY, "c",
        )
        val after = Practice.endSession(out.session, out.mastery, Fx.NOW)
        assertEquals(2.5, after.getValue(Skill.VERDICT).reviewInterval)
        assertEquals(2.5, after.getValue(Skill.VERIFY).reviewInterval)
        assertNull(after.getValue(Skill.BREADTH_NEWS).nextReviewAt)
        val summary = Practice.summary(out.session, after, out.traps)
        assertEquals(0.9, summary.before.getValue(StepKey.WHO))
        assertTrue(summary.after.getValue(StepKey.WHO) > 0.9)
        assertEquals(1, summary.itemsAnswered)
    }

    // ---- streak ----

    @Test
    fun streakCountsConsecutiveDays() {
        val d = { s: String -> LocalDate.parse(s) }
        var s = StreakState()
        s = Streak.record(s, d("2026-11-21")); assertEquals(1, s.current)
        s = Streak.record(s, d("2026-11-21")); assertEquals(1, s.current)
        s = Streak.record(s, d("2026-11-22")); assertEquals(2, s.current)
        s = Streak.record(s, d("2026-11-23")); assertEquals(3, s.current)
        assertEquals(3, Streak.display(s, d("2026-11-24")))
        assertEquals(0, Streak.display(s, d("2026-11-25")))
        s = Streak.record(s, d("2026-11-26")); assertEquals(1, s.current)
        assertEquals(3, s.best)
        s = Streak.record(s, d("2026-11-20")) // clock went back: ignored
        assertEquals("2026-11-26", s.lastDay)
        // month boundary
        val m = Streak.record(Streak.record(StreakState(), d("2026-11-30")), d("2026-12-01"))
        assertEquals(2, m.current)
        assertEquals(d("2026-11-21"), Streak.localDay(Fx.NOW, TimeZone.UTC))
        assertEquals(s, TeaJson.decodeFromString(StreakState.serializer(), TeaJson.encodeToString(StreakState.serializer(), s)))
    }

    // ---- calibration ----

    @Test
    fun calibration() {
        assertEquals(0.0, Calibration.error(Confidence.CERTAIN, true))
        assertEquals(1.0, Calibration.error(Confidence.CERTAIN, false))
        val answers = listOf(
            ConfidenceAnswer(Confidence.CERTAIN, true),
            ConfidenceAnswer(Confidence.GUESSING, false),
            ConfidenceAnswer(null, false),
        )
        assertEquals(0.165, Calibration.mean(answers), 1e-9)
        assertEquals(0.0, Calibration.mean(emptyList()))
        assertFalse(Calibration.overconfident(5, 3, answers))
        assertTrue(Calibration.overconfident(4, 2, answers))
        assertTrue(Calibration.overconfident(null, 6, answers + ConfidenceAnswer(Confidence.CERTAIN, false)))
        assertNotNull(Calibration.score(Confidence.FAIRLY_SURE))
    }
}
