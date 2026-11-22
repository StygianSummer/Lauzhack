package tea.engine

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.Confidence
import tea.model.Context
import tea.model.Item
import tea.model.MasteryState
import tea.model.PlacementResult
import tea.model.Skill
import tea.model.StepKey
import tea.model.Verdict

@Serializable
enum class PlacementPhase {
    /** Find the level with scam/legit pairs (max 5 probes). */
    @SerialName("a") A,

    /** Per-step probes at the level (tap hotspot, link, emotion, action). */
    @SerialName("b") B,

    /** Breadth: one news item, one image item. */
    @SerialName("c") C,
}

/** One recorded placement answer. [guess] is kept so [Placement.finish] needs no item pool. */
@Serializable
data class PlacementAnswer(
    val itemId: String,
    val mode: ItemMode,
    val phase: PlacementPhase,
    val correct: Boolean,
    val scam: Boolean,
    val verdict: Verdict? = null,
    val confidence: Confidence? = null,
    val ms: Long,
    val slow: Boolean = false,
    val guess: Double = 0.5,
)

/**
 * Placement state (plan §6.2), `@Serializable` so a half-done placement survives a reload.
 * Search state: `lo=1, hi=7, idx=2, probes=0, warmupRetryUsed=false`.
 *
 * - [pair]: ids of the phase-A pair being shown (display order), [pending]: those not yet answered.
 * - [searchLevel]: `lo` once phase A has stopped (1..7, 7 = above the ladder); null while searching.
 * - [used]: every item id shown (phase B items must not come back in practice this session).
 * - [modesDone]: phase B / C modes already answered.
 * - [seed]: makes [Placement.next] deterministic for a given state (calling it twice gives the same step).
 * - [quick]: the short test of the demo: one scam/legit pair (two emails), no warm-up retry, no
 *   phase B or C. Both right (and not "guessing") → level 3, "knows the basics"; otherwise level 1.
 */
@Serializable
data class PlacementState(
    val lo: Int = 1,
    val hi: Int = 7,
    val idx: Int = 2,
    val probes: Int = 0,
    val warmupRetryUsed: Boolean = false,
    val seed: Long = 0,
    val pair: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val searchLevel: Int? = null,
    val used: Set<String> = emptySet(),
    val modesDone: List<ItemMode> = emptyList(),
    val answers: List<PlacementAnswer> = emptyList(),
    val quick: Boolean = false,
) {
    /** Placement level (1..6) once known: `min(lo, 6)`. */
    val level: Int? get() = searchLevel?.let { min(it, 6) }
}

/** What to show next: [item] in [mode]. [pair] is the phase-A pair the item belongs to (empty otherwise). */
data class PlacementStep(
    val item: Item,
    val mode: ItemMode,
    val phase: PlacementPhase,
    val pair: List<String> = emptyList(),
)

/** Everything [Placement.finish] produces. */
data class PlacementOutcome(
    /** Store in `profile.placement`; payload of the `placement_completed` event. */
    val result: PlacementResult,
    /** All 7 skills, ready to store as the initial mastery. */
    val mastery: List<MasteryState>,
    /** Item ids shown during placement: pass to [Practice.startSession] as `alreadySeen`. */
    val seenIds: Set<String>,
)

/**
 * Placement test (plan §6.2), a pure state machine. Driving loop for the UI:
 * ```
 * var s = Placement.start(pool, Random)
 * while (true) {
 *     val step = Placement.next(s, pool) ?: break          // null = done, show the result screen
 *     // render step.item in step.mode, ask "How sure are you?", measure ms
 *     s = Placement.answer(s, step, answers, ms, confidence)
 *     // save Placement.attempt(step, answers, ms, confidence, clientId, now) locally
 *     progressBar = Placement.progress(s)
 * }
 * val outcome = Placement.finish(s, profile.selfRating, now)
 * ```
 * The pool is all items (any language already filtered): phase A uses `use: placement` email pairs linked
 * by `twin_of`, phase B `use: phase_b` items, phase C items of kind `news` / `image`.
 * Feedback during placement is neutral only; never show time.
 */
object Placement {
    const val MAX_PROBES = 5

    /** Denominator of the progress bar estimate. */
    const val EXPECTED_ITEMS = 11

    /** Items in a quick placement: one scam/legit pair. */
    const val QUICK_ITEMS = 2
    const val PROGRESS_CAP = 0.95f

    val PHASE_B_MODES: List<ItemMode> = listOf(ItemMode.TAP_HOTSPOT, ItemMode.LINK, ItemMode.EMOTION, ItemMode.ACTION)
    val PHASE_C_MODES: List<ItemMode> = listOf(ItemMode.NEWS, ItemMode.IMAGE)

    /**
     * A fresh placement. [random] only seeds the state; the pool is not needed yet but kept for symmetry.
     * [quick]: the two-email version (see [PlacementState.quick]).
     */
    @Suppress("UNUSED_PARAMETER")
    fun start(pool: List<Item>, random: Random, quick: Boolean = false): PlacementState =
        PlacementState(seed = random.nextLong(), quick = quick)

    /** Progress bar estimate: answered / 11 (2 when quick), capped at 95% until the result screen. */
    fun progress(state: PlacementState): Float =
        min(state.answers.size.toFloat() / (if (state.quick) QUICK_ITEMS else EXPECTED_ITEMS), PROGRESS_CAP)

    /** True when [next] returns null. */
    fun isDone(state: PlacementState, pool: List<Item>): Boolean = next(state, pool) == null

    /**
     * The step to show now, or null when the placement is over. Pure and idempotent for a given state.
     * Phase A ends when the stopping rule fired or no unused pair exists; phase B/C modes without a
     * suitable unused item are skipped.
     */
    fun next(state: PlacementState, pool: List<Item>): PlacementStep? {
        val byId = pool.associateBy { it.id }
        val rnd = Random(state.seed + state.answers.size)
        if (state.searchLevel == null) {
            state.pending.firstOrNull()?.let { id ->
                byId[id]?.let { return PlacementStep(it, ItemMode.VERDICT, PlacementPhase.A, state.pair) }
            }
            val pair = pickPair(state, pool, rnd)
            if (pair != null) return PlacementStep(pair.first(), ItemMode.VERDICT, PlacementPhase.A, pair.map { it.id })
        }
        if (state.quick) return null
        val level = state.level ?: min(state.lo, 6)
        for (mode in PHASE_B_MODES) {
            if (mode in state.modesDone) continue
            val item = pickPhaseB(pool, state.used, mode, level, rnd) ?: continue
            return PlacementStep(item, mode, PlacementPhase.B)
        }
        for (mode in PHASE_C_MODES) {
            if (mode in state.modesDone) continue
            val kind = if (mode == ItemMode.NEWS) "news" else "image"
            val candidates = pool.filter { it.kind == kind && it.id !in state.used && Correctness.supports(it, mode) }
            if (candidates.isEmpty()) continue
            val preferred = candidates.filter { it.usedFor("placement") }.ifEmpty { candidates }
            return PlacementStep(preferred.random(rnd), mode, PlacementPhase.C)
        }
        return null
    }

    /**
     * Record the answer to [step]. [answers] carries the field for the step's mode (verdict, hotspotsTapped,
     * linkChoice, stepChoices[EMOTION], actionId, newsForward, imageAi); null = "I don't know yet" = wrong.
     * [confidence] is the "How sure are you?" answer (null if skipped).
     */
    fun answer(
        state: PlacementState,
        step: PlacementStep,
        answers: AttemptAnswers,
        ms: Long,
        confidence: Confidence?,
    ): PlacementState {
        val item = step.item
        val correct = Correctness.isCorrect(item, step.mode, answers)
        val record = PlacementAnswer(
            itemId = item.id,
            mode = step.mode,
            phase = step.phase,
            correct = correct,
            scam = item.isScam,
            verdict = answers.verdict,
            confidence = confidence,
            ms = ms,
            slow = Bkt.isSlow(ms, item.expectedMs),
            guess = Bkt.guess(item, step.mode),
        )
        var s = state.copy(answers = state.answers + record, used = state.used + item.id)
        if (step.phase != PlacementPhase.A) {
            // Phase A may have ended because no pair was left: freeze the level now.
            if (s.searchLevel == null) s = s.copy(searchLevel = s.lo, pair = emptyList(), pending = emptyList())
            return s.copy(modesDone = s.modesDone + step.mode)
        }
        val pair = if (state.pending.isEmpty()) step.pair else state.pair
        val pending = (if (state.pending.isEmpty()) step.pair else state.pending) - item.id
        s = s.copy(pair = pair, pending = pending)
        if (pending.isNotEmpty()) return s

        // The pair is complete: judge it.
        val pairAnswers = s.answers.takeLast(pair.size).filter { it.itemId in pair }
        val firstProbe = s.probes == 0
        val known = pairAnswers.size == pair.size && pairAnswers.all {
            it.correct && it.confidence != Confidence.GUESSING && (firstProbe || !it.slow)
        }
        s = s.copy(pair = emptyList(), pending = emptyList())
        if (s.quick) {
            // One pair decides: known → lo = idx + 1, else the bottom of the ladder.
            val lo = if (known) s.idx + 1 else s.lo
            return s.copy(lo = lo, hi = if (known) s.hi else s.idx, probes = s.probes + 1, searchLevel = lo)
        }
        if (firstProbe && !known && !s.warmupRetryUsed) {
            // Warm-up grace: show a second pair at the same idx; its result replaces this one.
            return s.copy(warmupRetryUsed = true)
        }
        val lo = if (known) s.idx + 1 else s.lo
        val hi = if (known) s.hi else s.idx
        val probes = s.probes + 1
        val stop = lo >= hi || probes >= MAX_PROBES
        return s.copy(lo = lo, hi = hi, idx = (lo + hi) / 2, probes = probes, searchLevel = if (stop) lo else null)
    }

    /** The [Attempt] to store for a placement answer (context PLACEMENT, earns no XP by itself). */
    fun attempt(
        step: PlacementStep,
        answers: AttemptAnswers,
        ms: Long,
        confidence: Confidence?,
        clientId: String,
        now: Instant,
    ): Attempt = Attempt(
        clientId = clientId,
        itemId = step.item.id,
        context = Context.PLACEMENT,
        answers = answers,
        correct = Correctness.isCorrect(step.item, step.mode, answers),
        confidence = confidence,
        ms = ms,
        createdAt = now,
    )

    /**
     * Build the result and the initial mastery of all 7 skills.
     * - Steps: prior from the search level (`lo`, 1..7), then the phase B answers as BKT updates
     *   (tap hotspot → who; link → who and ask; emotion → emotion; action → verify; slow or
     *   "I don't know yet" = wrong). Support from p without hysteresis.
     * - VERDICT: p = prior, `level` = placement level.
     * - BREADTH_NEWS / BREADTH_IMAGE: prior, then the phase C answer as a BKT update.
     * - If level > 1 (levels below it are tested out): every step skill and VERDICT gets
     *   `testedOut = true, nextReviewAt = now`, so practice reviews the easier levels early.
     * - `hitRate` / `falseAlarmRate` over all phase A answers (warm-up pair included);
     *   `calibration` over every answer with a confidence; `strength` / `focus` = highest / lowest step p
     *   (ties: who, emotion, ask, verify).
     */
    fun finish(state: PlacementState, selfRating: Int?, now: Instant): PlacementOutcome {
        val searchLevel = state.searchLevel ?: state.lo
        val level = min(searchLevel, 6)
        val prior = Bkt.prior(searchLevel)
        val testedOut = level > 1

        val mastery = Skill.entries.associateWith { skill ->
            Mastery.initial(skill, now, p = prior, level = if (skill == Skill.VERDICT) level else 1)
        }.toMutableMap()

        fun evidence(skill: Skill, a: PlacementAnswer) {
            val eff = a.correct && !a.slow
            mastery[skill] = Bkt.observe(mastery.getValue(skill), eff, a.guess, tea.model.Support.PROMPTED, now)
        }
        for (a in state.answers) {
            when (a.mode) {
                ItemMode.TAP_HOTSPOT -> evidence(Skill.WHO, a)
                ItemMode.LINK -> { evidence(Skill.WHO, a); evidence(Skill.ASK, a) }
                ItemMode.EMOTION -> evidence(Skill.EMOTION, a)
                ItemMode.ACTION -> evidence(Skill.VERIFY, a)
                ItemMode.NEWS -> evidence(Skill.BREADTH_NEWS, a)
                ItemMode.IMAGE -> evidence(Skill.BREADTH_IMAGE, a)
                else -> Unit
            }
        }
        for (skill in Skill.entries) {
            var m = mastery.getValue(skill)
            m = m.copy(support = Scaffold.levelFor(m.p), updatedAt = now)
            if (testedOut && (skill.step != null || skill == Skill.VERDICT)) m = m.copy(testedOut = true, nextReviewAt = now)
            mastery[skill] = m
        }

        val stepP = Mastery.stepP(mastery)
        val strength = StepKey.entries.maxWith(compareBy<StepKey> { stepP.getValue(it) }.thenByDescending { it.ordinal })
        val focus = StepKey.entries.minWith(compareBy<StepKey> { stepP.getValue(it) }.thenBy { it.ordinal })

        val phaseA = state.answers.filter { it.phase == PlacementPhase.A }
        val scams = phaseA.filter { it.scam }
        val legits = phaseA.filter { !it.scam }
        val hitRate = if (scams.isEmpty()) 0.0 else scams.count { it.verdict == Verdict.TRICK }.toDouble() / scams.size
        val faRate = if (legits.isEmpty()) 0.0 else legits.count { it.verdict == Verdict.TRICK }.toDouble() / legits.size
        val conf = state.answers.map { ConfidenceAnswer(it.confidence, it.correct) }
        val breadth = buildMap {
            state.answers.firstOrNull { it.mode == ItemMode.NEWS }?.let { put("news", it.correct) }
            state.answers.firstOrNull { it.mode == ItemMode.IMAGE }?.let { put("image", it.correct) }
        }

        val result = PlacementResult(
            level = level,
            stepP = stepP,
            hitRate = hitRate,
            falseAlarmRate = faRate,
            calibration = Calibration.mean(conf),
            overconfident = Calibration.overconfident(selfRating, level, conf),
            breadth = breadth,
            totalMs = state.answers.sumOf { it.ms },
            strength = strength,
            focus = focus,
        )
        return PlacementOutcome(result, Mastery.toList(mastery), state.used)
    }

    // ---- internals ----

    /** All (scam, legit) placement pairs: same difficulty, linked by twin_of. Each pair once. */
    internal fun pairs(pool: List<Item>): List<List<Item>> {
        val placement = pool.filter { it.usedFor("placement") && it.kind == "email" }
        val byId = placement.associateBy { it.id }
        val seen = mutableSetOf<String>()
        val out = mutableListOf<List<Item>>()
        for (item in placement) {
            val twin = item.twinOf?.let { byId[it] } ?: continue
            if (twin.difficulty != item.difficulty || twin.category == item.category) continue
            val key = listOf(item.id, twin.id).sorted().joinToString("|")
            if (!seen.add(key)) continue
            out += if (item.isScam) listOf(item, twin) else listOf(twin, item)
        }
        return out
    }

    /** Unused pair at idx, else at the nearest level (prefer lower); random pair and random order. */
    private fun pickPair(state: PlacementState, pool: List<Item>, rnd: Random): List<Item>? {
        val unused = pairs(pool).filter { p -> p.none { it.id in state.used } }
        if (unused.isEmpty()) return null
        val best = unused.minOf { rank(it.first().difficulty, state.idx) }
        val atBest = unused.filter { rank(it.first().difficulty, state.idx) == best }
        return atBest.random(rnd).shuffled(rnd)
    }

    private fun pickPhaseB(pool: List<Item>, used: Set<String>, mode: ItemMode, level: Int, rnd: Random): Item? {
        val candidates = pool.filter { it.usedFor("phase_b") && it.id !in used && Correctness.supports(it, mode) }
        if (candidates.isEmpty()) return null
        val best = candidates.minOf { rank(it.difficulty, level) }
        return candidates.filter { rank(it.difficulty, level) == best }.random(rnd)
    }

    /** Distance ranking: same level first, then 1 lower, 1 higher, 2 lower, … */
    private fun rank(difficulty: Int, target: Int): Int {
        val d = difficulty - target
        return abs(d) * 2 + if (d > 0) 1 else 0
    }
}
