package tea.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tea.model.AttemptAnswers
import tea.model.Confidence
import tea.model.Item
import tea.model.Skill
import tea.model.StepKey
import tea.model.TeaJson
import tea.model.Verdict

class PlacementTest {
    private val pool = Fx.placementPool()

    private fun answerFor(step: PlacementStep, right: Boolean): AttemptAnswers {
        val item = step.item
        return when (step.mode) {
            ItemMode.VERDICT -> AttemptAnswers(
                verdict = if (right) (if (item.isScam) Verdict.TRICK else Verdict.FINE) else Verdict.NOT_SURE,
            )
            ItemMode.TAP_HOTSPOT -> AttemptAnswers(
                hotspotsTapped = if (right) listOf("h-from") else Correctness.DECOYS,
            )
            ItemMode.LINK -> AttemptAnswers(linkChoice = if (right) 2 else 0)
            ItemMode.EMOTION -> AttemptAnswers(stepChoices = mapOf(StepKey.EMOTION to if (right) 1 else null))
            ItemMode.ACTION -> AttemptAnswers(actionId = if (right) "open-app" else "click")
            ItemMode.NEWS -> AttemptAnswers(newsForward = if (right) item.news!!.real else !item.news!!.real)
            ItemMode.IMAGE -> AttemptAnswers(imageAi = if (right) item.image!!.aiGenerated else !item.image!!.aiGenerated)
            ItemMode.FULL -> error("not in placement")
        }
    }

    private fun run(
        knows: (PlacementStep, Int) -> Boolean,
        ms: (PlacementStep) -> Long = { 5_000 },
        confidence: Confidence = Confidence.FAIRLY_SURE,
        items: List<Item> = pool,
        seed: Int = 1,
    ): Pair<PlacementState, List<PlacementStep>> {
        var s = Placement.start(items, Random(seed))
        val steps = mutableListOf<PlacementStep>()
        while (true) {
            val step = Placement.next(s, items) ?: break
            assertEquals(step, Placement.next(s, items), "next() must be idempotent")
            steps += step
            s = Placement.answer(s, step, answerFor(step, knows(step, steps.size - 1)), ms(step), confidence)
            assertTrue(steps.size < 40, "placement must end")
        }
        return s to steps
    }

    @Test
    fun allKnownReachesTopAndRunsAllPhases() {
        val (s, steps) = run({ _, _ -> true })
        assertEquals(7, s.searchLevel)
        assertEquals(6, s.level)
        assertEquals(3, s.probes)
        val phaseA = steps.filter { it.phase == PlacementPhase.A }
        assertEquals(listOf(2, 2, 5, 5, 6, 6), phaseA.map { it.item.difficulty })
        assertEquals(
            listOf(ItemMode.TAP_HOTSPOT, ItemMode.LINK, ItemMode.EMOTION, ItemMode.ACTION, ItemMode.NEWS, ItemMode.IMAGE),
            steps.filter { it.phase != PlacementPhase.A }.map { it.mode },
        )
        // phase B items are distinct and drawn from the level (6) first
        val phaseB = steps.filter { it.phase == PlacementPhase.B }
        assertEquals(4, phaseB.map { it.item.id }.toSet().size)
        assertEquals(6, phaseB.first().item.difficulty)
        // each pair is one scam + its twin
        phaseA.chunked(2).forEach { pair -> assertEquals(1, pair.count { it.item.isScam }) }
    }

    @Test
    fun noneKnownStopsAtLevelOneAfterWarmupRetry() {
        val (s, steps) = run({ _, _ -> false })
        assertEquals(1, s.level)
        assertTrue(s.warmupRetryUsed)
        assertEquals(2, s.probes)
        val phaseA = steps.filter { it.phase == PlacementPhase.A }.map { it.item.difficulty }
        assertEquals(listOf(2, 2, 2, 2, 1, 1), phaseA) // probe 1, warm-up retry at level 2, then level 1
    }

    @Test
    fun binarySearchFindsBoundary() {
        // knows levels 1..3
        val (s, steps) = run({ step, _ -> step.phase != PlacementPhase.A || step.item.difficulty <= 3 })
        assertEquals(4, s.level)
        assertEquals(4, s.probes)
        assertEquals(listOf(2, 5, 4, 3), steps.filter { it.phase == PlacementPhase.A }.map { it.item.difficulty }.chunked(2).map { it[0] })
        assertTrue(s.lo >= s.hi)
    }

    @Test
    fun warmupRetryReplacesFirstProbe() {
        // wrong only on the very first item
        val (s, steps) = run({ _, i -> i != 0 })
        assertTrue(s.warmupRetryUsed)
        val a = steps.filter { it.phase == PlacementPhase.A }
        assertEquals(2, a[2].item.difficulty) // second level-2 pair
        assertTrue(a[2].item.id.startsWith("pl2") && a[2].item.id !in setOf(a[0].item.id, a[1].item.id))
        assertEquals(6, s.level) // retry counted as known: search went on upward
    }

    @Test
    fun timeIgnoredOnFirstProbeOnly() {
        val (s, _) = run({ _, _ -> true }, ms = { 100_000 })
        // probe 1 known despite slow; every later probe slow → unknown
        assertEquals(3, s.level)
    }

    @Test
    fun guessingIsNeverKnown() {
        val (s, _) = run({ _, _ -> true }, confidence = Confidence.GUESSING)
        assertEquals(1, s.level)
    }

    @Test
    fun nearestLevelPreferLower() {
        val noLevel2 = pool.filter { it.difficulty != 2 || !it.usedFor("placement") }
        val s = Placement.start(noLevel2, Random(3))
        assertEquals(1, Placement.next(s, noLevel2)!!.item.difficulty)
    }

    @Test
    fun progressIsCapped() {
        val (s, _) = run({ _, _ -> true })
        assertEquals(0f, Placement.progress(Placement.start(pool, Random(1))))
        assertEquals(0.95f, Placement.progress(s))
    }

    @Test
    fun noPairsSkipsToPhaseB() {
        val noPairs = pool.filter { !it.usedFor("placement") || it.kind != "email" }
        val (s, steps) = run({ _, _ -> true }, items = noPairs)
        assertEquals(1, s.level)
        assertEquals(ItemMode.TAP_HOTSPOT, steps.first().mode)
    }

    @Test
    fun finishBuildsResultAndMastery() {
        val (s, _) = run({ step, _ -> step.phase != PlacementPhase.A || step.item.difficulty <= 3 })
        val out = Placement.finish(s, selfRating = 2, now = Fx.NOW)
        assertEquals(7, out.mastery.size)
        assertEquals(Skill.entries, out.mastery.map { it.skill })
        val bySkill = out.mastery.associateBy { it.skill }
        assertEquals(4, out.result.level)
        assertEquals(4, bySkill.getValue(Skill.VERDICT).level)
        assertEquals(Bkt.prior(4), bySkill.getValue(Skill.VERDICT).p)
        // phase B all right → every step above the prior, WHO and ASK got two updates via the link question
        for (step in StepKey.entries) assertTrue(out.result.stepP.getValue(step) > Bkt.prior(4))
        assertEquals(2, bySkill.getValue(Skill.WHO).attempts)
        assertEquals(1, bySkill.getValue(Skill.EMOTION).attempts)
        for (m in out.mastery) assertEquals(Scaffold.levelFor(m.p), m.support)
        for (skill in listOf(Skill.WHO, Skill.EMOTION, Skill.ASK, Skill.VERIFY, Skill.VERDICT)) {
            assertTrue(bySkill.getValue(skill).testedOut)
            assertEquals(Fx.NOW, bySkill.getValue(skill).nextReviewAt)
        }
        assertFalse(bySkill.getValue(Skill.BREADTH_NEWS).testedOut)
        assertEquals(mapOf("news" to true, "image" to true), out.result.breadth)
        assertEquals(0.5, out.result.hitRate) // scams at levels 2, 3 flagged; 5, 4 answered "not sure"
        assertEquals(0.0, out.result.falseAlarmRate) // wrong legit answers were NOT_SURE, not TRICK
        assertEquals(StepKey.WHO, out.result.strength) // two right updates
        assertTrue(out.seenIds.containsAll(listOf("pb4", "news-1", "img-1")))
        assertFalse(out.result.overconfident)
        assertEquals(s.answers.sumOf { it.ms }, out.result.totalMs)
    }

    @Test
    fun lowLevelNoTestedOutAndOverconfidence() {
        val (s, _) = run({ _, _ -> false })
        val out = Placement.finish(s, selfRating = 5, now = Fx.NOW)
        assertTrue(out.result.overconfident)
        assertTrue(out.mastery.none { it.testedOut })
        assertTrue(out.mastery.all { it.nextReviewAt == null })
        // wrong with CERTAIN alone also flags overconfidence
        val (s2, _) = run({ _, _ -> false }, confidence = Confidence.CERTAIN)
        assertTrue(Placement.finish(s2, selfRating = null, now = Fx.NOW).result.overconfident)
    }

    @Test
    fun hotspotRule() {
        val item = Fx.email("h", scam = true)
        assertTrue(Correctness.hotspot(item, listOf("h-from")))
        assertTrue(Correctness.hotspot(item, listOf("h-from", Correctness.DECOY_GREETING)))
        assertFalse(Correctness.hotspot(item, listOf("h-from", Correctness.DECOY_GREETING, Correctness.DECOY_SIGNATURE)))
        assertFalse(Correctness.hotspot(item, listOf("h-from", "h-link", Correctness.DECOY_SIGNATURE)))
        assertTrue(Correctness.hotspot(item, listOf("h-from", "h-urg"))) // red flag of another step: no penalty
        assertFalse(Correctness.hotspot(item, listOf("h-urg")))
        assertFalse(Correctness.hotspot(item, emptyList()))
    }

    @Test
    fun stateSurvivesJson() {
        val pool = pool
        var s = Placement.start(pool, Random(9))
        val step = Placement.next(s, pool)!!
        s = Placement.answer(s, step, answerFor(step, true), 4_000, Confidence.CERTAIN)
        val back = TeaJson.decodeFromString(PlacementState.serializer(), TeaJson.encodeToString(PlacementState.serializer(), s))
        assertEquals(s, back)
        assertEquals(Placement.next(s, pool), Placement.next(back, pool))
        assertNotNull(Placement.next(back, pool))
    }

    @Test
    fun emptyPoolIsDoneImmediately() {
        val s = Placement.start(emptyList(), Random(1))
        assertNull(Placement.next(s, emptyList()))
        val out = Placement.finish(s, null, Fx.NOW)
        assertEquals(1, out.result.level)
    }
}
