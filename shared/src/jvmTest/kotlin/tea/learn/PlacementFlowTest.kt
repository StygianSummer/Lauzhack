package tea.learn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import tea.app.FakeTeaApp
import tea.engine.ItemMode
import tea.engine.PlacementState
import tea.engine.PlacementStep
import tea.engine.Xp
import tea.model.AttemptAnswers
import tea.model.Confidence
import tea.model.Context
import tea.model.EventTypes
import tea.model.Skill
import tea.model.StepKey
import tea.model.Verdict
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.placement.PlacementAck
import tea.ui.screens.placement.PlacementController
import tea.ui.screens.practice.ItemAnswer

/** Headless run of the placement flow (plan §6.2, §9.3) on the real bundled content. */
class PlacementFlowTest {
    private val pool = TestContent.items()

    private fun right(step: PlacementStep) = Oracle.right(step)

    private fun dontKnow() = ItemAnswer(AttemptAnswers(), confidence = null, ms = 4_000, dontKnow = true)

    @Test
    fun allRightReachesAHighLevelAndWritesEverything() {
        val app = FakeTeaApp(TestContent.repository())
        val c = PlacementController(app, pool, Random(1))
        val modes = mutableListOf<ItemMode>()
        var guard = 0
        while (true) {
            val step = c.current ?: break
            modes += step.mode
            c.answer(step, right(step))
            app.advance(5.seconds)
            assertEquals(PlacementAck.GOT_IT, c.ack)
            assertTrue(c.progress <= 0.95f)
            assertTrue(++guard < 30, "placement does not end")
        }
        val outcome = c.finish()

        assertTrue(outcome.result.level >= 5, "level ${outcome.result.level}")
        assertTrue(ItemMode.NEWS in modes && ItemMode.IMAGE in modes, "breadth items shown: $modes")
        assertTrue(listOf(ItemMode.TAP_HOTSPOT, ItemMode.LINK, ItemMode.EMOTION, ItemMode.ACTION).all { it in modes }, "phase B: $modes")
        assertEquals(modes.size, app.attempts.size)
        assertTrue(app.attempts.all { it.context == Context.PLACEMENT && it.correct })
        assertEquals(Xp.PLACEMENT_COMPLETED, app.xp.value)
        assertEquals(Skill.entries.toSet(), app.mastery.value.keys)
        assertNotNull(app.profile.value.placement)
        val events = app.eventsOf(EventTypes.PLACEMENT_COMPLETED)
        assertEquals(1, events.size)
        assertEquals(outcome.result.level, events.single().payload["level"]!!.jsonPrimitive.int)
        assertEquals(outcome.seenIds, app.load(LearnKeys.PLACEMENT_SEEN, LearnKeys.STRING_SET))
        assertNull(app.load(LearnKeys.PLACEMENT_STATE, PlacementState.serializer()))
    }

    @Test
    fun dontKnowStartsAtTheBottomWithNeutralFeedback() {
        val app = FakeTeaApp(TestContent.repository())
        val c = PlacementController(app, pool, Random(2))
        while (true) {
            val step = c.current ?: break
            c.answer(step, dontKnow())
            assertEquals(PlacementAck.TOGETHER, c.ack)
        }
        val outcome = c.finish()
        assertEquals(1, outcome.result.level)
        assertTrue(app.attempts.none { it.correct })
    }

    @Test
    fun aReloadResumesAtTheSameItem() {
        val app = FakeTeaApp(TestContent.repository())
        val first = PlacementController(app, pool, Random(3))
        repeat(3) { first.answer(first.current!!, right(first.current!!)) }
        val before = first.current!!

        val second = PlacementController(app, pool, Random(99))
        assertTrue(second.resumed)
        assertEquals(3, second.answered)
        assertEquals(before.item.id, second.current!!.item.id)
        assertEquals(before.mode, second.current!!.mode)
    }
}
