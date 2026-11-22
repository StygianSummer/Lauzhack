package tea.engine

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import tea.model.MasteryState
import tea.model.Skill

/** Right / total evidence count for one skill during one session. */
@Serializable
data class Tally(val right: Int = 0, val total: Int = 0) {
    fun add(correct: Boolean): Tally = Tally(right + if (correct) 1 else 0, total + 1)
    val rate: Double get() = if (total == 0) 0.0 else right.toDouble() / total
}

/**
 * Spaced review (plan §6.8). Per skill, `reviewInterval` in days, starting at 1.
 *
 * Choice where the plan is silent: a skill's "review answers" are all the evidence that skill received
 * during the session (every non-WORKED step observation, every verdict); [Practice] keeps them in
 * [PracticeSessionState.tally]. Skills with no evidence this session keep their interval and date.
 */
object Review {
    const val PASS_RATE = 0.75
    const val GROWTH = 2.5

    /** End-of-session update of one skill: ≥ 75% right → interval × 2.5, else 1; next review = now + interval days. */
    fun update(state: MasteryState, tally: Tally, now: Instant): MasteryState {
        if (tally.total == 0) return state
        val interval = if (tally.rate >= PASS_RATE) state.reviewInterval * GROWTH else 1.0
        return state.copy(reviewInterval = interval, nextReviewAt = now + interval.days, updatedAt = now)
    }

    /** Apply [update] to every skill with a tally. */
    fun endOfSession(mastery: Map<Skill, MasteryState>, tally: Map<Skill, Tally>, now: Instant): Map<Skill, MasteryState> =
        mastery.mapValues { (skill, state) -> tally[skill]?.let { update(state, it, now) } ?: state }

    /** Is a review due for this skill (has a date and it is not in the future)? */
    fun isDue(state: MasteryState?, now: Instant): Boolean {
        val at = state?.nextReviewAt ?: return false
        return at <= now
    }
}
