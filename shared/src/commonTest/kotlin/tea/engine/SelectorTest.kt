package tea.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tea.model.Item
import tea.model.MasteryState
import tea.model.Skill
import tea.model.StepKey
import tea.model.Support
import tea.model.TeaJson
import tea.model.Verdict

class SelectorTest {
    /** 6 seeds, each a scam + legit twin, mixed identities/asks/levels, plus one held-out. */
    private val pool: List<Item> = buildList {
        val asks = listOf("click_login", "give_code", "pay_fee")
        for (i in 0 until 6) {
            val d = i % 3 + 1
            add(Fx.email("s$i", scam = true, difficulty = d, identity = "id$i", ask = asks[i % 3]))
            add(Fx.email("l$i", scam = false, difficulty = d, twinOf = "s$i", identity = "id$i", ask = asks[i % 3]))
        }
        add(Fx.email("held", scam = true, difficulty = 6, heldOut = true, identity = "drive", ask = "click_login"))
    }

    private fun verdictFor(item: Item, right: Boolean) =
        if (right == item.isScam) Verdict.TRICK else Verdict.FINE

    private fun answer(step: PracticeStep, right: Boolean) = PracticeAnswer(
        support = step.support, verdict = verdictFor(step.item, right), actionId = "open-app", ms = 5_000,
        stepChoices = step.support.filterValues { it == Support.PROMPTED }.mapValues { 1 },
    )

    private class Run(
        var mastery: Map<Skill, MasteryState>,
        var session: PracticeSessionState,
        var traps: TrapState = TrapState(),
        var shortcut: ShortcutStats = ShortcutStats(),
        val steps: MutableList<PracticeStep> = mutableListOf(),
    )

    private fun play(
        session: PracticeSessionState,
        random: Random,
        mastery: Map<Skill, MasteryState> = Fx.mastery(0.5),
        right: (Int, PracticeStep) -> Boolean = { _, _ -> random.nextBoolean() },
    ): Run {
        val r = Run(mastery, session)
        while (true) {
            val step = Practice.next(pool, r.mastery, r.session, r.shortcut, r.traps, Fx.NOW, random) ?: break
            r.steps += step
            val out = Practice.applyAnswer(
                step.item, answer(step, right(r.steps.size - 1, step)), r.mastery, r.traps, r.shortcut, r.session,
                Fx.NOW, Fx.TODAY, "c${r.steps.size}",
            )
            r.mastery = out.mastery; r.session = out.session; r.traps = out.traps; r.shortcut = out.shortcut
            assertTrue(r.steps.size < 100)
        }
        return r
    }

    @Test
    fun neverSameSeedTwiceInARow() {
        repeat(30) { seed ->
            val r = play(Practice.startSession(count = 12), Random(seed))
            r.steps.zipWithNext().forEach { (a, b) ->
                assertTrue(a.item.seed != b.item.seed, "seed repeated: ${a.item.id} -> ${b.item.id}")
                assertFalse(a.item.slots.identity == b.item.slots.identity && a.item.slots.ask == b.item.slots.ask)
            }
        }
    }

    @Test
    fun heldOutComesLastWithAllFaded() {
        repeat(10) { seed ->
            val r = play(Practice.startSession(count = 5), Random(seed))
            val last = r.steps.last()
            assertTrue(last.heldOut)
            assertEquals("held", last.item.id)
            assertTrue(last.support.values.all { it == Support.FADED })
            assertTrue(r.steps.dropLast(1).none { it.item.heldOut })
            assertEquals(6, r.steps.size)
            assertEquals(SectionStatus.DONE, Selector.status(r.session))
            assertTrue(Practice.isOver(pool, r.mastery, r.session, r.shortcut, r.traps, Fx.NOW))
        }
    }

    @Test
    fun noHeldOutWhenNotRequested() {
        val r = play(Practice.startSession(count = 4, endWithHeldOut = false), Random(1))
        assertEquals(4, r.steps.size)
        assertTrue(r.steps.none { it.heldOut })
    }

    @Test
    fun sectionStatus() {
        val s = Practice.startSession(count = 2)
        assertEquals(SectionStatus.PRACTICE, Selector.status(s))
        assertEquals(SectionStatus.HELD_OUT, Selector.status(s.copy(practiceCount = 2)))
        assertEquals(SectionStatus.DONE, Selector.status(s.copy(practiceCount = 2, endWithHeldOut = false)))
        assertEquals(SectionStatus.DONE, Selector.status(s.copy(practiceCount = 2, heldOutDone = true)))
    }

    @Test
    fun legitShareSteersTheMix() {
        val scam = Fx.email("a", scam = true, difficulty = 1, identity = "x1", whoFlag = false)
        val legit = Fx.email("b", scam = false, difficulty = 1, identity = "x2", whoFlag = false)
        val two = listOf(scam, legit)
        val m = Fx.mastery(0.5)
        val lowLegit = Practice.startSession().copy(history = List(5) { HistoryEntry("old$it", true, legit = false) })
        val highLegit = Practice.startSession().copy(history = List(5) { HistoryEntry("old$it", true, legit = true) })
        repeat(20) { seed ->
            assertEquals("b", Selector.pick(two, m, lowLegit, ShortcutStats(), TrapState(), now = Fx.NOW, random = Random(seed))!!.id)
            assertEquals("a", Selector.pick(two, m, highLegit, ShortcutStats(), TrapState(), now = Fx.NOW, random = Random(seed))!!.id)
        }
        // over a long session the share settles between 30% and 60%
        val r = play(Practice.startSession(count = 10, endWithHeldOut = false), Random(4))
        val share = r.steps.count { !it.item.isScam }.toDouble() / r.steps.size
        assertTrue(share in 0.2..0.7, "legit share $share")
    }

    @Test
    fun wrongItemsComeBackAfterTwoOthers() {
        repeat(10) { seed ->
            val r = play(Practice.startSession(count = 8, endWithHeldOut = false), Random(seed)) { i, _ -> i != 0 }
            val firstId = r.steps[0].item.id
            // never offered again before 2 other items were shown
            assertTrue(r.steps.subList(1, 3).none { it.item.id == firstId })
        }
        // timing of the queue itself
        val item = pool.first { it.id == "s0" }
        var s = Practice.startSession(count = 8)
        val out = Practice.applyAnswer(
            item, PracticeAnswer(Scaffold.supportMap(item, Fx.mastery(0.5)), Verdict.FINE, "click", 5_000),
            Fx.mastery(0.5), TrapState(), ShortcutStats(), s, Fx.NOW, Fx.TODAY, "x",
        )
        s = out.session
        assertEquals(listOf(RetryEntry("s0", 3)), s.retryQueue)
        assertTrue(Selector.dueRetries(s).isEmpty())
        assertTrue(Selector.dueRetries(s.copy(shown = s.shown + "a")).isEmpty())
        val later = s.copy(shown = s.shown + "a" + "b", history = s.history + HistoryEntry("b", true, false))
        assertEquals(setOf("s0"), Selector.dueRetries(later))
        assertTrue(Selector.eligible(pool, later).any { it.id == "s0" })
        val notQueued = later.copy(retryQueue = emptyList())
        assertTrue(Selector.eligible(pool, notQueued).none { it.id == "s0" })
        assertEquals(4, Selector.score(item, Fx.mastery(0.5), later, ShortcutStats(), TrapState(), emptyList(), Fx.NOW) -
            Selector.score(item, Fx.mastery(0.5), notQueued, ShortcutStats(), TrapState(), emptyList(), Fx.NOW))
        // answered right on its return: leaves the queue
        val back = Practice.applyAnswer(
            item, PracticeAnswer(Scaffold.supportMap(item, Fx.mastery(0.5)), Verdict.TRICK, "open-app", 5_000),
            Fx.mastery(0.5), TrapState(), ShortcutStats(), later, Fx.NOW, Fx.TODAY, "y",
        )
        assertTrue(back.session.retryQueue.isEmpty())
    }

    @Test
    fun retryQueueIsDueAtNextSessionStart() {
        val first = play(Practice.startSession(count = 3, endWithHeldOut = false), Random(5)) { i, _ -> i != 2 }
        val wrongId = first.steps[2].item.id
        assertEquals(listOf(wrongId), first.session.retryQueue.map { it.itemId })
        val next = Practice.startSession(count = 3, previous = first.session)
        assertEquals(0, next.retryQueue.single().eligibleAt)
        assertEquals(first.session.history, next.history)
        assertEquals(setOf(wrongId), Selector.dueRetries(next))
        assertTrue(next.shown.isEmpty() && next.tally.isEmpty())
    }

    @Test
    fun weakestStepAndFocusBonus() {
        val trainsWho = Fx.email("w", scam = true, identity = "p", whoFlag = true)
        val noWho = Fx.email("n", scam = true, identity = "q", whoFlag = false)
        val mastery = Fx.mastery(0.6).toMutableMap()
        mastery[Skill.WHO] = mastery.getValue(Skill.WHO).copy(p = 0.2)
        val s = Practice.startSession()
        val a = Selector.score(trainsWho, mastery, s, ShortcutStats(), TrapState(), emptyList(), Fx.NOW)
        val b = Selector.score(noWho, mastery, s, ShortcutStats(), TrapState(), emptyList(), Fx.NOW)
        assertEquals(3, a - b)
        val withFocus = Selector.score(trainsWho, mastery, s, ShortcutStats(), TrapState(), listOf(StepKey.WHO), Fx.NOW)
        assertEquals(1, withFocus - a)
    }

    @Test
    fun difficultyReviewShortcutAndTrapBonuses() {
        val m = Fx.mastery(0.5, level = 3).toMutableMap()
        val s = Practice.startSession(placementLevel = 4)
        fun sc(item: Item, mm: Map<Skill, MasteryState> = m, sh: ShortcutStats = ShortcutStats(), t: TrapState = TrapState()) =
            Selector.score(item, mm, s, sh, t, emptyList(), Fx.NOW)
        val d3 = Fx.email("d3", difficulty = 3, whoFlag = false)
        val d2 = Fx.email("d2", difficulty = 2, whoFlag = false)
        val d5 = Fx.email("d5", difficulty = 5, whoFlag = false)
        assertEquals(2, sc(d3) - sc(d5))
        assertEquals(1, sc(d2) - sc(d5))
        // tested-out review due → +2 for difficulties below the placement level
        val due = m.toMutableMap().also { it[Skill.VERDICT] = it.getValue(Skill.VERDICT).copy(testedOut = true, nextReviewAt = Fx.NOW) }
        assertEquals(2, sc(d2, due) - sc(d2))
        assertEquals(0, sc(d5, due) - sc(d5))
        // shortcut request: legit with the clue first
        val legitUrgent = Fx.email("lu", scam = false, clues = listOf("urgency"), whoFlag = false)
        val req = ShortcutStats(request = ShortcutRequest("urgency"))
        assertEquals(3, sc(legitUrgent, sh = req) - sc(legitUrgent))
        // trap
        val traps = TrapState(active = listOf(Trap("urgency", TrapKind.FALSE_ALARM)))
        assertEquals(2, sc(legitUrgent, t = traps) - sc(legitUrgent))
    }

    @Test
    fun heldOutPrefersNewCombination() {
        val h1 = Fx.email("h1", heldOut = true, identity = "bank", ask = "click_login")
        val h2 = Fx.email("h2", heldOut = true, identity = "drive", ask = "click_login")
        val s = Practice.startSession().copy(shownCombos = listOf("bank×click_login"))
        repeat(10) { assertEquals("h2", Selector.heldOut(listOf(h1, h2), s, Random(it))!!.id) }
        assertNull(Selector.heldOut(listOf(h1), s.copy(seen = setOf("h1")), Random(1)))
    }

    @Test
    fun placementSeenItemsAreNotRepeated() {
        val s = Practice.startSession(count = 12, alreadySeen = pool.map { it.id }.filter { it.startsWith("s") }.toSet())
        val r = play(s, Random(3))
        assertTrue(r.steps.filter { !it.heldOut }.none { it.item.id.startsWith("s") })
    }

    @Test
    fun sessionStateSurvivesJson() {
        val r = play(Practice.startSession(count = 4), Random(8))
        val json = TeaJson.encodeToString(PracticeSessionState.serializer(), r.session)
        assertEquals(r.session, TeaJson.decodeFromString(PracticeSessionState.serializer(), json))
        assertEquals(r.traps, TeaJson.decodeFromString(TrapState.serializer(), TeaJson.encodeToString(TrapState.serializer(), r.traps)))
        assertEquals(r.shortcut, TeaJson.decodeFromString(ShortcutStats.serializer(), TeaJson.encodeToString(ShortcutStats.serializer(), r.shortcut)))
        assertNotNull(r.session.tally[Skill.VERDICT])
    }
}
