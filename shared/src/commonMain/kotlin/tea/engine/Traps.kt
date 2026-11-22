package tea.engine

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tea.model.Item
import tea.model.Verdict

@Serializable
enum class TrapKind {
    /** The learner let a scam with this clue through (FINE or NOT_SURE on a scam). */
    @SerialName("miss") MISS,

    /** The learner distrusted a legit message with this clue (TRICK, or NOT_SURE: choice, see [Traps.record]). */
    @SerialName("false_alarm") FALSE_ALARM,
}

/** An active recurring mistake on one surface clue. [rightDays]: ISO dates of right answers on items with the clue. */
@Serializable
data class Trap(val clue: String, val kind: TrapKind, val rightDays: List<String> = emptyList()) {
    /**
     * Stable key for i18n lookups in the summary: `misses_<clue>` or `flags_<clue>`
     * (e.g. `misses_display_name`, `flags_urgency`).
     */
    val key: String get() = (if (kind == TrapKind.MISS) "misses_" else "flags_") + clue

    /** English fallback label for "Watch out for: …" when the UI has no translation for [key]. */
    val label: String
        get() {
            val words = clue.replace('_', ' ')
            return if (kind == TrapKind.MISS) "missing the sign \"$words\"" else "flagging real messages for \"$words\""
        }
}

/**
 * Recurring mistakes per surface clue (plan §6.7). [misses] and [falseAlarms] count wrong verdicts per
 * clue of `slots.surfaceClues`; [active] are the current traps.
 */
@Serializable
data class TrapState(
    val misses: Map<String, Int> = emptyMap(),
    val falseAlarms: Map<String, Int> = emptyMap(),
    val active: List<Trap> = emptyList(),
)

object Traps {
    /** Wrong answers on the same clue (and kind) before it becomes a trap. */
    const val THRESHOLD = 2

    /** Right answers on this many different local days clear a trap. */
    const val CLEAR_DAYS = 3

    /**
     * Record one verdict on [item] answered on local date [today].
     * - Wrong verdict: each clue gets +1 miss on a scam, +1 false alarm on a legit item (the plan names TRICK
     *   on legit; choice: NOT_SURE on a legit item is also counted as a false alarm, it is not a miss).
     *   A counter reaching [THRESHOLD] opens a trap (once per clue and kind).
     * - Right verdict: every active trap whose clue the item has gets [today] added; a trap with right answers
     *   on [CLEAR_DAYS] different days is cleared and its counter reset to 0.
     */
    fun record(state: TrapState, item: Item, verdict: Verdict?, today: LocalDate): TrapState {
        val clues = item.slots.surfaceClues.toSet()
        if (clues.isEmpty()) return state
        val right = Xp.verdictCorrect(item, verdict)
        if (right) {
            val day = today.toString()
            var misses = state.misses
            var falseAlarms = state.falseAlarms
            val active = state.active.mapNotNull { trap ->
                if (trap.clue !in clues) return@mapNotNull trap
                val days = if (day in trap.rightDays) trap.rightDays else trap.rightDays + day
                if (days.size >= CLEAR_DAYS) {
                    if (trap.kind == TrapKind.MISS) misses = misses - trap.clue else falseAlarms = falseAlarms - trap.clue
                    null
                } else {
                    trap.copy(rightDays = days)
                }
            }
            return TrapState(misses, falseAlarms, active)
        }
        val kind = if (item.isScam) TrapKind.MISS else TrapKind.FALSE_ALARM
        val counters = (if (kind == TrapKind.MISS) state.misses else state.falseAlarms).toMutableMap()
        val active = state.active.toMutableList()
        for (clue in clues.sorted()) {
            val n = (counters[clue] ?: 0) + 1
            counters[clue] = n
            if (n >= THRESHOLD && active.none { it.clue == clue && it.kind == kind }) active += Trap(clue, kind)
        }
        return if (kind == TrapKind.MISS) state.copy(misses = counters, active = active)
        else state.copy(falseAlarms = counters, active = active)
    }

    /** Does [item] exercise an active trap (has one of the trapped clues)? Used by the selector (+2). */
    fun exercises(state: TrapState, item: Item): Boolean {
        if (state.active.isEmpty()) return false
        val clues = item.slots.surfaceClues
        return state.active.any { it.clue in clues }
    }

    /** Keys of the active traps, for the practice summary ("Watch out for: …"). See [Trap.key]. */
    fun keys(state: TrapState): List<String> = state.active.map { it.key }

    /** English fallback labels of the active traps (see [Trap.label]). */
    fun labels(state: TrapState): List<String> = state.active.map { it.label }
}
