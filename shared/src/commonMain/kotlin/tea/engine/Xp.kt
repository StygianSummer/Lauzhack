package tea.engine

import tea.model.Attempt
import tea.model.Context
import tea.model.Item
import tea.model.Support
import tea.model.Verdict

/**
 * XP rules (plan §6.9). Pure; the same code runs on the client and on the server
 * (the leaderboard recomputes XP from stored attempts, clients never send XP numbers).
 */
object Xp {
    const val VERDICT = 10
    const val SAFE_ACTION = 5
    const val UNAIDED_STEP = 2
    const val HELD_OUT = 30
    const val LESSON_COMPLETED = 50
    const val PLACEMENT_COMPLETED = 20

    /**
     * XP for one attempt. Placement attempts earn nothing (the placement bonus is an event bonus).
     * `item` may be null when the item is unknown (e.g. removed content): then only the verdict counts.
     */
    fun xpFor(attempt: Attempt, item: Item?): Int {
        if (attempt.context == Context.PLACEMENT) return 0
        val safe = item?.actions?.firstOrNull { it.id == attempt.answers.actionId }?.isSafe == true
        if (item?.heldOut == true) {
            return if (attempt.correct && safe) HELD_OUT else 0
        }
        var xp = 0
        if (attempt.correct) xp += VERDICT
        if (safe) xp += SAFE_ACTION
        if (item != null && attempt.hintsUsed == 0) {
            for ((step, choice) in attempt.answers.stepChoices) {
                val support = attempt.scaffold[step] ?: continue
                if (support == Support.WORKED) continue
                val content = item.steps[step] ?: continue
                if (choice != null && choice == content.correct) xp += UNAIDED_STEP
            }
        }
        return xp
    }

    /** Is the verdict right for this item? NOT_SURE is never right. */
    fun verdictCorrect(item: Item, verdict: Verdict?): Boolean = when (verdict) {
        Verdict.TRICK -> item.isScam
        Verdict.FINE -> !item.isScam
        else -> false
    }
}
