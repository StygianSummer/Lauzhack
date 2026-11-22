package tea.engine

import kotlin.time.Instant
import tea.model.Item
import tea.model.MasteryState
import tea.model.Skill
import tea.model.StepKey
import tea.model.Support

/**
 * Bayesian knowledge tracing (plan §6.3), same model as hopingo's `learner.js`.
 *
 * Evidence rules (applied by callers through [countsAsCorrect] and [observe]):
 * - support `WORKED` → no evidence (no update, no attempt count);
 * - a used hint counts as wrong; `ms > 2 × expectedMs` counts as wrong (except the first item of a session);
 * - "I don't know yet" counts as wrong;
 * - `unaided++` when the (effective) answer is right and support is PROMPTED / HINT / FADED.
 *   Choice: a right but too slow answer does not count as unaided (it counts as wrong, see above).
 */
object Bkt {
    const val SLIP = 0.10
    const val LEARN = 0.12

    const val GUESS_VERDICT = 0.5
    const val GUESS_HOTSPOT = 0.2

    /** Yes/no questions (news forward, image real/AI). */
    const val GUESS_BINARY = 0.5

    /** Prior per step from the placement search level (plan §6.2); 7 = "above the ladder". */
    val PRIORS: Map<Int, Double> = mapOf(1 to 0.10, 2 to 0.20, 3 to 0.35, 4 to 0.50, 5 to 0.65, 6 to 0.75, 7 to 0.85)

    fun prior(level: Int): Double = PRIORS.getValue(level.coerceIn(1, 7))

    /** One BKT step: posterior given the answer, then the learning transition. Result in 0..1. */
    fun update(p: Double, correct: Boolean, guess: Double): Double {
        val posterior = if (correct) {
            val num = p * (1 - SLIP)
            val den = num + (1 - p) * guess
            if (den <= 0.0) p else num / den
        } else {
            val num = p * SLIP
            val den = num + (1 - p) * (1 - guess)
            if (den <= 0.0) p else num / den
        }
        return (posterior + (1 - posterior) * LEARN).coerceIn(0.0, 1.0)
    }

    /** Guess for an n-choice question: `1 / n` (0.5 if n is unknown). */
    fun guessChoices(n: Int): Double = if (n > 0) 1.0 / n else GUESS_VERDICT

    /** Guess probability for an item shown in [mode] (plan §6.3). */
    fun guess(item: Item, mode: ItemMode): Double = when (mode) {
        ItemMode.VERDICT, ItemMode.FULL -> GUESS_VERDICT
        ItemMode.TAP_HOTSPOT -> GUESS_HOTSPOT
        ItemMode.LINK -> guessChoices(item.linkQuestion?.options?.size ?: 0)
        ItemMode.EMOTION -> guessChoices(item.steps[StepKey.EMOTION]?.choices?.size ?: 0)
        ItemMode.ACTION -> guessChoices(item.actions.size)
        ItemMode.NEWS, ItemMode.IMAGE -> GUESS_BINARY
    }

    /** `ms > 2 × expectedMs`. */
    fun isSlow(ms: Long, expectedMs: Long): Boolean = ms > 2 * expectedMs

    /** Effective correctness after the evidence rules (hint → wrong, slow → wrong unless [ignoreTime]). */
    fun countsAsCorrect(correct: Boolean, hintUsed: Boolean, ms: Long, expectedMs: Long, ignoreTime: Boolean): Boolean =
        correct && !hintUsed && (ignoreTime || !isSlow(ms, expectedMs))

    /**
     * Apply one piece of evidence to a skill. [correct] must already be the effective correctness
     * ([countsAsCorrect]). With [support] `WORKED` nothing changes. Does not touch [MasteryState.support]
     * (see [Scaffold.withSupport]) nor the VERDICT difficulty level.
     */
    /**
     * A worked example was studied (support `WORKED`): no evidence and no attempt counted, only the
     * BKT learning transition `p + (1 - p)·LEARN`. This is what lets a step leave WORKED: from
     * p = 0.20 two worked examples reach PROMPTED (0.35), from p = 0.10 three do.
     */
    fun studied(state: MasteryState, now: Instant): MasteryState =
        state.copy(p = (state.p + (1 - state.p) * LEARN).coerceIn(0.0, 1.0), updatedAt = now)

    fun observe(state: MasteryState, correct: Boolean, guess: Double, support: Support, now: Instant): MasteryState {
        if (support == Support.WORKED) return state
        return state.copy(
            p = update(state.p, correct, guess),
            attempts = state.attempts + 1,
            unaided = state.unaided + if (correct) 1 else 0,
            updatedAt = now,
        )
    }
}

/** Helpers around the per-skill [MasteryState] map. */
object Mastery {
    /** Lowest difficulty / highest difficulty of the ladder (plan §6.1). */
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 6

    /** A fresh state for a learner without placement: prior of level 1, support from p. */
    fun initial(skill: Skill, now: Instant, p: Double = Bkt.prior(1), level: Int = 1): MasteryState =
        MasteryState(skill = skill, p = p, level = level, support = Scaffold.levelFor(p), updatedAt = now)

    /** List (as stored / synced) → map; missing skills are filled with [initial]. */
    fun toMap(list: List<MasteryState>, now: Instant): Map<Skill, MasteryState> {
        val byskill = list.associateBy { it.skill }
        return Skill.entries.associateWith { byskill[it] ?: initial(it, now) }
    }

    /** Map → list in [Skill] order. */
    fun toList(map: Map<Skill, MasteryState>): List<MasteryState> = Skill.entries.mapNotNull { map[it] }

    fun get(map: Map<Skill, MasteryState>, skill: Skill, now: Instant): MasteryState = map[skill] ?: initial(skill, now)

    /** Target item difficulty for the next practice item = `mastery[VERDICT].level`, clamped 1..6. */
    fun targetDifficulty(map: Map<Skill, MasteryState>): Int =
        (map[Skill.VERDICT]?.level ?: MIN_LEVEL).coerceIn(MIN_LEVEL, MAX_LEVEL)

    /**
     * Weakest non-mastered step (lowest p; ties in order who, emotion, ask, verify), or null when all four
     * steps are mastered. Missing skills count as p = prior(1).
     */
    fun weakestStep(map: Map<Skill, MasteryState>): StepKey? =
        StepKey.entries
            .filter { map[it.skill]?.mastered != true }
            .minByOrNull { map[it.skill]?.p ?: Bkt.prior(1) }

    /** p per step (missing = prior(1)). */
    fun stepP(map: Map<Skill, MasteryState>): Map<StepKey, Double> =
        StepKey.entries.associateWith { map[it.skill]?.p ?: Bkt.prior(1) }
}
