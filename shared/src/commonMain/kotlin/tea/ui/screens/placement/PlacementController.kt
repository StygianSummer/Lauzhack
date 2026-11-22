package tea.ui.screens.placement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import kotlinx.serialization.json.jsonObject
import tea.app.TeaApp
import tea.engine.Ids
import tea.engine.Placement
import tea.engine.PlacementOutcome
import tea.engine.PlacementState
import tea.engine.PlacementStep
import tea.engine.Xp
import tea.model.EventTypes
import tea.model.Item
import tea.model.PlacementResult
import tea.model.TeaJson
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.practice.ItemAnswer

/** Neutral acknowledgement shown above the next placement item (no corrections during the test). */
enum class PlacementAck { GOT_IT, TOGETHER }

/**
 * Drives the placement test (plan §6.2, §9.3) on top of the pure [Placement] engine and persists
 * its state after every answer under [LearnKeys.PLACEMENT_STATE], so a reload resumes at the same
 * item. No UI code: the screens and the headless tests use it the same way.
 *
 * ```
 * val c = PlacementController(app, pool)
 * while (c.current != null) c.answer(c.current!!, itemAnswer)
 * c.finish()   // mastery, profile.placement, event, XP, seen ids
 * ```
 */
class PlacementController(
    private val app: TeaApp,
    private val pool: List<Item>,
    random: Random = Random.Default,
) {
    var state: PlacementState by mutableStateOf(
        app.load(LearnKeys.PLACEMENT_STATE, PlacementState.serializer())
            ?: Placement.start(pool, random).also { app.save(LearnKeys.PLACEMENT_STATE, PlacementState.serializer(), it) },
    )
        private set

    /** Acknowledgement of the previous answer, or null before the first. */
    var ack: PlacementAck? by mutableStateOf(null)
        private set

    /** True when a placement was already under way when this controller was created. */
    val resumed: Boolean = state.answers.isNotEmpty()

    /** What to show now, or null when the test is over (then call [finish]). */
    val current: PlacementStep? get() = Placement.next(state, pool)

    /** Progress bar estimate (answered / 11, capped at 95%). */
    val progress: Float get() = Placement.progress(state)

    val answered: Int get() = state.answers.size

    /** Records [answer] to [step]: stores the attempt (context PLACEMENT), moves the engine, persists. */
    fun answer(step: PlacementStep, answer: ItemAnswer) {
        app.recordAttempt(
            Placement.attempt(step, answer.answers, answer.ms, answer.confidence, Ids.newClientId(), app.now()),
        )
        state = Placement.answer(state, step, answer.answers, answer.ms, answer.confidence)
        app.save(LearnKeys.PLACEMENT_STATE, PlacementState.serializer(), state)
        ack = if (answer.dontKnow) PlacementAck.TOGETHER else PlacementAck.GOT_IT
    }

    /**
     * Ends the test: initial mastery for all skills, `profile.placement`, the `placement_completed`
     * event (payload = the result), +20 XP, and the seen item ids for the first practice section.
     * Clears the in-progress state.
     */
    fun finish(): PlacementOutcome {
        val outcome = Placement.finish(state, app.profile.value.selfRating, app.now())
        app.saveMastery(outcome.mastery)
        app.updateProfile { it.copy(placement = outcome.result) }
        app.emit(
            EventTypes.PLACEMENT_COMPLETED,
            TeaJson.encodeToJsonElement(PlacementResult.serializer(), outcome.result).jsonObject,
        )
        app.addXp(Xp.PLACEMENT_COMPLETED)
        app.save(LearnKeys.PLACEMENT_SEEN, LearnKeys.STRING_SET, outcome.seenIds)
        app.save(LearnKeys.PLACEMENT_STATE, PlacementState.serializer(), null)
        return outcome
    }
}
