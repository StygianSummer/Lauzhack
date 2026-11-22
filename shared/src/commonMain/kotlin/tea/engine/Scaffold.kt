package tea.engine

import tea.model.Item
import tea.model.MasteryState
import tea.model.Skill
import tea.model.StepKey
import tea.model.Support

/**
 * Support level per step with hysteresis (plan §6.4).
 *
 * | p         | Support  |
 * |-----------|----------|
 * | < 0.35    | WORKED   |
 * | 0.35–0.6  | PROMPTED |
 * | 0.6–0.8   | HINT     |
 * | ≥ 0.8     | FADED    |
 *
 * Up a level when p reaches the level's upper threshold; down only when p falls below the level's lower
 * threshold minus [HYSTERESIS]. Several levels can be crossed in one update.
 */
object Scaffold {
    const val HYSTERESIS = 0.05

    /** Lower threshold of each level (WORKED has none). */
    private fun lower(s: Support): Double = when (s) {
        Support.WORKED -> 0.0
        Support.PROMPTED -> 0.35
        Support.HINT -> 0.6
        Support.FADED -> 0.8
    }

    private fun up(s: Support): Support = Support.entries[(s.ordinal + 1).coerceAtMost(Support.entries.size - 1)]
    private fun down(s: Support): Support = Support.entries[(s.ordinal - 1).coerceAtLeast(0)]

    /** Support for p without hysteresis (used for initial states, e.g. after placement). */
    fun levelFor(p: Double): Support = when {
        p >= 0.8 -> Support.FADED
        p >= 0.6 -> Support.HINT
        p >= 0.35 -> Support.PROMPTED
        else -> Support.WORKED
    }

    /** Next support given the [current] stored one and the new [p], with hysteresis. */
    fun supportFor(current: Support, p: Double): Support {
        var s = current
        while (s != Support.FADED && p >= lower(up(s))) s = up(s)
        while (s != Support.WORKED && p < lower(s) - HYSTERESIS) s = down(s)
        return s
    }

    /** Returns [state] with its `support` moved per [supportFor] (only for the four step skills). */
    fun withSupport(state: MasteryState): MasteryState =
        if (state.skill.step == null) state else state.copy(support = supportFor(state.support, state.p))

    /**
     * Support to show for each of the four steps on [item]: the stored `support` of each step skill
     * (missing skill = WORKED); everything FADED for a held-out item; FADED for a step whose
     * `steps.<step>` content is missing on the item (nothing to show).
     */
    fun supportMap(item: Item, mastery: Map<Skill, MasteryState>): Map<StepKey, Support> =
        StepKey.entries.associateWith { step ->
            when {
                item.heldOut -> Support.FADED
                item.steps[step] == null -> Support.FADED
                else -> mastery[step.skill]?.support ?: Support.WORKED
            }
        }
}
