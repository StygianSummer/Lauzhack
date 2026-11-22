package tea.engine

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Streak state (plan §6.9): consecutive local days with at least one practice item. No hearts, no lives.
 * [lastDay] is an ISO date (`yyyy-MM-dd`) of the last day with practice.
 */
@Serializable
data class StreakState(val current: Int = 0, val best: Int = 0, val lastDay: String? = null)

object Streak {
    /** The learner's local date for [now] (pass the zone explicitly in tests). */
    fun localDay(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        now.toLocalDateTime(zone).date

    /**
     * Record one practice item answered on [today]. Same day: unchanged. Day after [StreakState.lastDay]:
     * +1. Any other gap (or first time): restart at 1. A [today] before lastDay (clock change) is ignored.
     */
    fun record(state: StreakState, today: LocalDate): StreakState {
        val last = state.lastDay?.let { LocalDate.parse(it) }
        if (last != null && today <= last) return state
        val current = if (last != null && today.minus(1, DateTimeUnit.DAY) == last) state.current + 1 else 1
        return StreakState(current = current, best = maxOf(state.best, current), lastDay = today.toString())
    }

    /** Streak to display on [today]: the stored streak if the last practice was today or yesterday, else 0. */
    fun display(state: StreakState, today: LocalDate): Int {
        val last = state.lastDay?.let { LocalDate.parse(it) } ?: return 0
        return if (last == today || last == today.minus(1, DateTimeUnit.DAY)) state.current else 0
    }
}
