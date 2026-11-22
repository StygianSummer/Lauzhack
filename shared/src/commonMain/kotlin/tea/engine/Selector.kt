package tea.engine

import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Instant
import tea.model.Item
import tea.model.ItemCategory
import tea.model.MasteryState
import tea.model.Skill
import tea.model.StepKey

/** Where a practice section is: regular items, the final held-out item, or over. */
enum class SectionStatus { PRACTICE, HELD_OUT, DONE }

/**
 * Item selection for one `practice` section (plan §6.5). Use [Practice.next] from the UI; these functions
 * are the building blocks.
 *
 * Score per eligible item, highest wins (ties random):
 * - +3 trains the weakest non-mastered step, +1 more if that step is in `focus`;
 * - +2 `difficulty == target` (`mastery[VERDICT].level`), +1 if off by one;
 * - +2 a tested-out level's review is due (`mastery[VERDICT]` tested out and `nextReviewAt <= now`) and the
 *   item's difficulty is one of the tested-out levels (below [PracticeSessionState.testedOutBelow]);
 * - +3 matches the shortcut breaker's request; +2 exercises an active trap;
 * - legit balance over the recent history (last 20 items): share < 30% → legit +3, > 50% → legit −3;
 * - +4 for an item from the retry queue that is due again.
 *
 * Exclusions: held-out items; items seen this session unless due in the retry queue; the same seed as the
 * previous item; the same `slots.identity` AND `slots.ask` as the previous item.
 *
 * Choices where the plan is silent:
 * - "trains step s" = has a red-flag hotspot with step s, or `steps.<s>` has non-empty `cues`
 *   (every email has all four steps, so "has step content" alone would not discriminate);
 * - "legit" for the balance = `category == legit` (unsolicited items are legit by content convention);
 *   with an empty history no balance bonus applies;
 * - no relaxation: when every item is excluded, [pick] returns null and the section moves on.
 */
object Selector {
    const val HISTORY_SIZE = 20
    const val LEGIT_LOW = 0.30
    const val LEGIT_HIGH = 0.50

    /** Retry: re-offered after at least this many other items. */
    const val RETRY_GAP = 2

    /** Section status from counts alone (does not check whether items are left). */
    fun status(session: PracticeSessionState): SectionStatus = when {
        session.heldOutDone -> SectionStatus.DONE
        session.practiceCount < session.count -> SectionStatus.PRACTICE
        session.endWithHeldOut -> SectionStatus.HELD_OUT
        else -> SectionStatus.DONE
    }

    /** Does [item] train [step]? (red-flag hotspot on that step, or step content with cues) */
    fun trains(item: Item, step: StepKey): Boolean =
        item.hotspots.any { it.redFlag && it.step == step } || item.steps[step]?.cues?.isNotEmpty() == true

    /** Share of legit items in the recent history, or null when it is empty. */
    fun legitShare(session: PracticeSessionState): Double? {
        val h = session.history.takeLast(HISTORY_SIZE)
        if (h.isEmpty()) return null
        return h.count { it.legit }.toDouble() / h.size
    }

    /** Retry entries that may be shown now. */
    fun dueRetries(session: PracticeSessionState): Set<String> =
        session.retryQueue.filter { it.eligibleAt <= session.shownCount }.map { it.itemId }.toSet()

    /** The items that may be picked now (practice pool, after every exclusion rule). */
    fun eligible(pool: List<Item>, session: PracticeSessionState): List<Item> {
        val due = dueRetries(session)
        val prevId = session.history.lastOrNull()?.itemId
        val prev = prevId?.let { id -> pool.firstOrNull { it.id == id } }
        return pool.filter { item ->
            item.usedFor("practice") &&
                !item.heldOut &&
                (item.id !in session.seen || item.id in due) &&
                item.id != prevId &&
                (prev == null || item.seed != prev.seed) &&
                (prev == null || !(item.slots.identity == prev.slots.identity && item.slots.ask == prev.slots.ask))
        }
    }

    /** Score of one eligible item (see the object KDoc). */
    fun score(
        item: Item,
        mastery: Map<Skill, MasteryState>,
        session: PracticeSessionState,
        shortcut: ShortcutStats,
        traps: TrapState,
        focus: List<StepKey>,
        now: Instant,
    ): Int {
        var score = 0
        val weakest = Mastery.weakestStep(mastery)
        if (weakest != null && trains(item, weakest)) {
            score += 3
            if (weakest in focus) score += 1
        }
        val diff = abs(item.difficulty - Mastery.targetDifficulty(mastery))
        score += when (diff) { 0 -> 2; 1 -> 1; else -> 0 }
        val verdict = mastery[Skill.VERDICT]
        if (verdict?.testedOut == true && Review.isDue(verdict, now) && item.difficulty < session.testedOutBelow) score += 2
        if (ShortcutBreaker.matchesRequest(shortcut, item)) score += 3
        if (Traps.exercises(traps, item)) score += 2
        val share = legitShare(session)
        if (share != null && item.category == ItemCategory.LEGIT) {
            if (share < LEGIT_LOW) score += 3 else if (share > LEGIT_HIGH) score -= 3
        }
        if (item.id in dueRetries(session)) score += 4
        return score
    }

    /** Next regular practice item, or null when nothing is eligible. Ignores the section count (see [Practice.next]). */
    fun pick(
        pool: List<Item>,
        mastery: Map<Skill, MasteryState>,
        session: PracticeSessionState,
        shortcut: ShortcutStats,
        traps: TrapState,
        focus: List<StepKey> = session.focus,
        now: Instant,
        random: Random,
    ): Item? {
        val candidates = eligible(pool, session)
        if (candidates.isEmpty()) return null
        val scored = candidates.map { it to score(it, mastery, session, shortcut, traps, focus, now) }
        val best = scored.maxOf { it.second }
        return scored.filter { it.second == best }.map { it.first }.random(random)
    }

    /**
     * The held-out item that ends the section (shown with all support FADED), or null if none is left.
     * Prefers a held-out item whose identity × ask combination appears in none of the items shown this
     * session; falls back to any unseen held-out item.
     */
    fun heldOut(pool: List<Item>, session: PracticeSessionState, random: Random): Item? {
        val candidates = pool.filter { it.heldOut && it.id !in session.seen }
        if (candidates.isEmpty()) return null
        val shownCombos = session.shownCombos.toSet()
        val fresh = candidates.filter { combo(it) !in shownCombos }
        return fresh.ifEmpty { candidates }.random(random)
    }

    internal fun combo(item: Item): String = item.slots.identity + "×" + item.slots.ask
}
