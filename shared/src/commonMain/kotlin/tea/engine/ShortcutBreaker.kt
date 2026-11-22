package tea.engine

import kotlinx.serialization.Serializable
import tea.model.Item
import tea.model.Verdict

/**
 * A pending request from the shortcut breaker: first a **legit** item that has [clue], then a **scam** that
 * lacks it.
 */
@Serializable
data class ShortcutRequest(val clue: String, val legitShown: Boolean = false, val scamShown: Boolean = false)

/**
 * Shortcut breaker state (plan §6.6). [flaggedWith]: per clue, how many items judged TRICK contained it;
 * [seen]: per clue, how many items shown contained it; [trickVerdicts]: number of TRICK verdicts counted.
 */
@Serializable
data class ShortcutStats(
    val trickVerdicts: Int = 0,
    val flaggedWith: Map<String, Int> = emptyMap(),
    val seen: Map<String, Int> = emptyMap(),
    val request: ShortcutRequest? = null,
)

/**
 * Detects a learner who flags messages because of one surface clue (e.g. "urgency") and asks the selector for
 * counter-examples.
 *
 * Choices where the plan is silent: dominance is only checked while no request is pending; when a request
 * is cleared, `trickVerdicts` and `flaggedWith` restart from zero (a fresh window), `seen` is kept.
 * Ties between dominating clues: the most flagged, then alphabetical.
 */
object ShortcutBreaker {
    const val MIN_TRICKS = 4
    const val SHARE = 0.70
    const val MIN_FLAGGED = 3

    /** Does [item] satisfy the pending part of the request? (legit with the clue, then scam without it) */
    fun matchesRequest(stats: ShortcutStats, item: Item): Boolean {
        val r = stats.request ?: return false
        val has = r.clue in item.slots.surfaceClues
        return when {
            !r.legitShown -> !item.isScam && has
            !r.scamShown -> item.isScam && !has
            else -> false
        }
    }

    /** The clue that currently dominates the TRICK verdicts, or null. */
    fun dominantClue(stats: ShortcutStats): String? {
        if (stats.trickVerdicts < MIN_TRICKS) return null
        return stats.flaggedWith.entries
            .filter { it.value >= MIN_FLAGGED && it.value.toDouble() / stats.trickVerdicts >= SHARE }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key
    }

    /** Record that [item] was shown and answered with [verdict]. */
    fun record(stats: ShortcutStats, item: Item, verdict: Verdict?): ShortcutStats {
        val clues = item.slots.surfaceClues.toSet()
        val seen = stats.seen.toMutableMap().also { m -> clues.forEach { m[it] = (m[it] ?: 0) + 1 } }
        var s = stats.copy(seen = seen)
        if (verdict == Verdict.TRICK) {
            val flagged = s.flaggedWith.toMutableMap().also { m -> clues.forEach { m[it] = (m[it] ?: 0) + 1 } }
            s = s.copy(trickVerdicts = s.trickVerdicts + 1, flaggedWith = flagged)
        }
        val r = stats.request
        if (r != null) {
            if (!matchesRequest(stats, item)) return s
            val next = if (!r.legitShown) r.copy(legitShown = true) else r.copy(scamShown = true)
            return if (next.legitShown && next.scamShown) {
                s.copy(request = null, trickVerdicts = 0, flaggedWith = emptyMap())
            } else {
                s.copy(request = next)
            }
        }
        val clue = dominantClue(s) ?: return s
        return s.copy(request = ShortcutRequest(clue))
    }
}
