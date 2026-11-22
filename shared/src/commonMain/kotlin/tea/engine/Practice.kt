package tea.engine

import kotlin.random.Random
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.Confidence
import tea.model.Context
import tea.model.Item
import tea.model.ItemCategory
import tea.model.MasteryState
import tea.model.PracticeSection
import tea.model.Skill
import tea.model.StepKey
import tea.model.Support
import tea.model.Verdict

/** A wrong item waiting to come back; it may be shown again once `shownCount >= eligibleAt`. */
@Serializable
data class RetryEntry(val itemId: String, val eligibleAt: Int)

/** One answered item in the recent history (kept across sessions, last [Selector.HISTORY_SIZE]). */
@Serializable
data class HistoryEntry(val itemId: String, val correct: Boolean, val legit: Boolean)

/**
 * State of one practice section, `@Serializable` (persist it with `TeaJson` to survive reloads).
 * Create it with [Practice.startSession]; only [Practice.applyAnswer] changes it.
 *
 * - [count], [focus], [endWithHeldOut]: from the lesson's `practice` section.
 * - [testedOutBelow]: placement level; difficulties below it are tested-out levels (review bonus).
 * - [shown]: ids answered this session in order (held-out included); [practiceCount]: regular items answered.
 * - [seen]: ids not to repeat this session (shown + placement items passed in `alreadySeen`).
 * - [retryQueue]: wrong items to re-offer; [history]: last 20 items across sessions.
 * - [consecutiveFastRight] / [consecutiveWrong]: counters of the difficulty rule (§6.3).
 * - [tally]: per-skill evidence this session, used by [Review] at the end.
 * - [startP]: p per step at session start, for the summary's "before → after".
 */
@Serializable
data class PracticeSessionState(
    val count: Int = 8,
    val focus: List<StepKey> = emptyList(),
    val endWithHeldOut: Boolean = true,
    val testedOutBelow: Int = 1,
    val shown: List<String> = emptyList(),
    val shownCombos: List<String> = emptyList(),
    val practiceCount: Int = 0,
    val seen: Set<String> = emptySet(),
    val retryQueue: List<RetryEntry> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val consecutiveFastRight: Int = 0,
    val consecutiveWrong: Int = 0,
    val heldOutDone: Boolean = false,
    val heldOutCorrect: Boolean? = null,
    val tally: Map<Skill, Tally> = emptyMap(),
    val xp: Int = 0,
    val startP: Map<StepKey, Double> = emptyMap(),
) {
    val shownCount: Int get() = shown.size
}

/** What the practice screen shows next: [item] in [mode] FULL with [support] per step. */
data class PracticeStep(
    val item: Item,
    /** true for the final "click check" item: all support FADED. */
    val heldOut: Boolean,
    val support: Map<StepKey, Support>,
    val mode: ItemMode = ItemMode.FULL,
)

/**
 * The learner's answer to one practice item, as collected by the UI.
 * - [stepChoices]: FIRST choice per step that was asked (PROMPTED, or HINT after "Need a hint?");
 *   null value = "I don't know yet"; absent key = not asked (WORKED, FADED, HINT without hint).
 * - [hintsUsed]: steps where help was used ("Need a hint?" tapped at HINT level).
 * - [support]: support actually shown per step (pass [PracticeStep.support]).
 * - [isFirstOfSession]: null = derive (no item answered yet in this session).
 */
data class PracticeAnswer(
    val support: Map<StepKey, Support>,
    val verdict: Verdict?,
    val actionId: String?,
    val ms: Long,
    val stepChoices: Map<StepKey, Int?> = emptyMap(),
    val hintsUsed: Set<StepKey> = emptySet(),
    val confidence: Confidence? = null,
    val freeText: String? = null,
    val isFirstOfSession: Boolean? = null,
)

/** Everything [Practice.applyAnswer] produces. Persist the four states, store [attempt], add [xp]. */
data class PracticeOutcome(
    val mastery: Map<Skill, MasteryState>,
    val traps: TrapState,
    val shortcut: ShortcutStats,
    val session: PracticeSessionState,
    /** XP for this item ([Xp.xpFor] of [attempt]). */
    val xp: Int,
    /** Ready to store locally and queue for sync (context PRACTICE). */
    val attempt: Attempt,
    val verdictCorrect: Boolean,
    val actionSafe: Boolean,
    /** Effective correctness per step that received evidence (WORKED steps are absent). */
    val stepCorrect: Map<StepKey, Boolean>,
)

/** Data for `PracticeSummary`. */
data class PracticeSummary(
    val before: Map<StepKey, Double>,
    val after: Map<StepKey, Double>,
    val xp: Int,
    val itemsAnswered: Int,
    /** null if no held-out item was shown. */
    val heldOutCorrect: Boolean?,
    /** i18n keys of the active traps ([Trap.key]). */
    val trapKeys: List<String>,
    /** English fallback labels ([Trap.label]). */
    val trapLabels: List<String>,
)

/**
 * Practice inside the lesson (plan §9.5). Driving loop for the UI:
 * ```
 * var session = Practice.startSession(section, mastery, placementLevel, previousSession, placementSeenIds)
 * while (true) {
 *     val step = Practice.next(pool, mastery, session, shortcut, traps, now, random) ?: break
 *     // render step.item with step.support, collect a PracticeAnswer
 *     val out = Practice.applyAnswer(step.item, answer, mastery, traps, shortcut, session, now, today)
 *     mastery = out.mastery; traps = out.traps; shortcut = out.shortcut; session = out.session
 *     streak = Streak.record(streak, today); save out.attempt; xp += out.xp
 * }
 * mastery = Practice.endSession(session, mastery, now)     // spaced review intervals
 * show Practice.summary(session, mastery, traps)
 * ```
 */
object Practice {
    /**
     * Start a section. [previous] (the last session's state, if any) carries the recent history and the
     * retry queue: wrong items of the last session are due right away. [placementLevel] is
     * `profile.placement?.level`. [alreadySeen]: e.g. [PlacementOutcome.seenIds] when practice follows placement.
     */
    fun startSession(
        count: Int = 8,
        focus: List<StepKey> = emptyList(),
        endWithHeldOut: Boolean = true,
        mastery: Map<Skill, MasteryState> = emptyMap(),
        placementLevel: Int? = null,
        previous: PracticeSessionState? = null,
        alreadySeen: Set<String> = emptySet(),
    ): PracticeSessionState = PracticeSessionState(
        count = count,
        focus = focus,
        endWithHeldOut = endWithHeldOut,
        testedOutBelow = placementLevel ?: previous?.testedOutBelow ?: 1,
        seen = alreadySeen,
        retryQueue = previous?.retryQueue?.map { it.copy(eligibleAt = 0) } ?: emptyList(),
        history = previous?.history ?: emptyList(),
        startP = Mastery.stepP(mastery),
    )

    /** Same as above, reading count / focus / endWithHeldOut from the lesson section. */
    fun startSession(
        section: PracticeSection,
        mastery: Map<Skill, MasteryState>,
        placementLevel: Int?,
        previous: PracticeSessionState? = null,
        alreadySeen: Set<String> = emptySet(),
    ): PracticeSessionState =
        startSession(section.count, section.focus, section.endWithHeldOut, mastery, placementLevel, previous, alreadySeen)

    /**
     * The next item to show, or null when the section is over. Regular items until `count` (or until no
     * item is eligible), then the held-out item (all FADED) if `endWithHeldOut`, then null.
     */
    fun next(
        pool: List<Item>,
        mastery: Map<Skill, MasteryState>,
        session: PracticeSessionState,
        shortcut: ShortcutStats,
        traps: TrapState,
        now: Instant,
        random: Random,
    ): PracticeStep? {
        if (session.heldOutDone) return null
        if (session.practiceCount < session.count) {
            val item = Selector.pick(pool, mastery, session, shortcut, traps, session.focus, now, random)
            if (item != null) return PracticeStep(item, heldOut = false, support = Scaffold.supportMap(item, mastery))
        }
        if (session.endWithHeldOut) {
            val item = Selector.heldOut(pool, session, random)
            if (item != null) return PracticeStep(item, heldOut = true, support = Scaffold.supportMap(item, mastery))
        }
        return null
    }

    /**
     * Apply one answer. Rules:
     * - Slow (`ms > 2 × expectedMs`) makes every piece of evidence wrong, except on the first item of a session.
     * - Per step with support ≠ WORKED: if the step was asked ([PracticeAnswer.stepChoices] has the key) the
     *   evidence is that choice (guess 1/choices); otherwise (FADED, or HINT without hint) the learner applied
     *   the step silently and the evidence is the verdict for who/emotion/ask (guess 0.5) and the action for
     *   verify (guess 1/actions). A hint on the step makes it wrong. Then support moves with hysteresis.
     * - VERDICT skill: verdict right (NOT_SURE = wrong), guess 0.5; difficulty `level` +1 after 2
     *   consecutive fast right items (`ms <= expectedMs`), −1 after 2 consecutive wrong items, clamped 1..6
     *   (choice: the held-out item does not move the level).
     * - Wrong verdict on a regular item → retry queue, due after ≥ 2 other items.
     * - Traps, shortcut stats, history, tally and session XP are updated; [today] is the local date (traps).
     */
    fun applyAnswer(
        item: Item,
        answer: PracticeAnswer,
        mastery: Map<Skill, MasteryState>,
        traps: TrapState,
        shortcut: ShortcutStats,
        session: PracticeSessionState,
        now: Instant,
        today: LocalDate,
        clientId: String = Ids.newClientId(),
    ): PracticeOutcome {
        val first = answer.isFirstOfSession ?: session.shown.isEmpty()
        val slow = !first && Bkt.isSlow(answer.ms, item.expectedMs)
        val verdictRight = Xp.verdictCorrect(item, answer.verdict)
        val actionSafe = Correctness.action(item, answer.actionId)

        val m = Skill.entries.associateWith { Mastery.get(mastery, it, now) }.toMutableMap()
        val tally = session.tally.toMutableMap()
        val stepCorrect = mutableMapOf<StepKey, Boolean>()

        for (step in StepKey.entries) {
            val support = answer.support[step] ?: Scaffold.supportMap(item, mastery).getValue(step)
            if (support == Support.WORKED) {
                // No evidence, but the worked example is a learning opportunity: without this a step
                // below 0.35 could never leave WORKED (found in the end-to-end run).
                m[step.skill] = Scaffold.withSupport(Bkt.studied(m.getValue(step.skill), now))
                continue
            }
            val content = item.steps[step]
            val (raw, guess) = when {
                content != null && answer.stepChoices.containsKey(step) ->
                    Correctness.step(item, step, answer.stepChoices[step]) to Bkt.guessChoices(content.choices.size)
                step == StepKey.VERIFY -> actionSafe to Bkt.guessChoices(item.actions.size)
                else -> verdictRight to Bkt.GUESS_VERDICT
            }
            val eff = raw && step !in answer.hintsUsed && !slow
            m[step.skill] = Scaffold.withSupport(Bkt.observe(m.getValue(step.skill), eff, guess, support, now))
            stepCorrect[step] = eff
            tally[step.skill] = (tally[step.skill] ?: Tally()).add(eff)
        }

        // VERDICT skill + difficulty level.
        val verdictEff = verdictRight && !slow
        var verdictState = Bkt.observe(m.getValue(Skill.VERDICT), verdictEff, Bkt.GUESS_VERDICT, Support.FADED, now)
        tally[Skill.VERDICT] = (tally[Skill.VERDICT] ?: Tally()).add(verdictEff)
        var fastRight = session.consecutiveFastRight
        var wrong = session.consecutiveWrong
        if (!item.heldOut) {
            val fast = answer.ms <= item.expectedMs
            when {
                verdictRight && fast -> { fastRight++; wrong = 0 }
                !verdictRight -> { wrong++; fastRight = 0 }
                else -> { fastRight = 0; wrong = 0 }
            }
            var level = verdictState.level
            if (fastRight >= 2) { level++; fastRight = 0 }
            if (wrong >= 2) { level--; wrong = 0 }
            verdictState = verdictState.copy(level = level.coerceIn(Mastery.MIN_LEVEL, Mastery.MAX_LEVEL))
        }
        m[Skill.VERDICT] = verdictState

        val scaffold = StepKey.entries.associateWith { answer.support[it] ?: Scaffold.supportMap(item, mastery).getValue(it) }
        val attempt = Attempt(
            clientId = clientId,
            itemId = item.id,
            context = Context.PRACTICE,
            answers = AttemptAnswers(
                verdict = answer.verdict,
                actionId = answer.actionId,
                stepChoices = answer.stepChoices,
                freeText = answer.freeText,
            ),
            correct = verdictRight,
            confidence = answer.confidence,
            scaffold = scaffold,
            hintsUsed = answer.hintsUsed.size,
            ms = answer.ms,
            createdAt = now,
        )
        val xp = Xp.xpFor(attempt, item)

        val shown = session.shown + item.id
        var retry = session.retryQueue.filter { it.itemId != item.id }
        if (!verdictRight && !item.heldOut) retry = retry + RetryEntry(item.id, eligibleAt = shown.size + Selector.RETRY_GAP)
        val newSession = session.copy(
            shown = shown,
            shownCombos = session.shownCombos + Selector.combo(item),
            practiceCount = session.practiceCount + if (item.heldOut) 0 else 1,
            seen = session.seen + item.id,
            retryQueue = retry,
            history = (session.history + HistoryEntry(item.id, verdictRight, item.category == ItemCategory.LEGIT))
                .takeLast(Selector.HISTORY_SIZE),
            consecutiveFastRight = fastRight,
            consecutiveWrong = wrong,
            heldOutDone = session.heldOutDone || item.heldOut,
            heldOutCorrect = if (item.heldOut) verdictRight && actionSafe else session.heldOutCorrect,
            tally = tally,
            xp = session.xp + xp,
        )

        return PracticeOutcome(
            mastery = m,
            traps = Traps.record(traps, item, answer.verdict, today),
            shortcut = ShortcutBreaker.record(shortcut, item, answer.verdict),
            session = newSession,
            xp = xp,
            attempt = attempt,
            verdictCorrect = verdictRight,
            actionSafe = actionSafe,
            stepCorrect = stepCorrect,
        )
    }

    /** Is the section over? (same as `next(...) == null`) */
    fun isOver(
        pool: List<Item>,
        mastery: Map<Skill, MasteryState>,
        session: PracticeSessionState,
        shortcut: ShortcutStats,
        traps: TrapState,
        now: Instant,
    ): Boolean = next(pool, mastery, session, shortcut, traps, now, Random(0)) == null

    /** End of section: spaced-review update of every skill that got evidence (§6.8). */
    fun endSession(session: PracticeSessionState, mastery: Map<Skill, MasteryState>, now: Instant): Map<Skill, MasteryState> =
        Review.endOfSession(mastery, session.tally, now)

    fun summary(session: PracticeSessionState, mastery: Map<Skill, MasteryState>, traps: TrapState): PracticeSummary =
        PracticeSummary(
            before = session.startP,
            after = Mastery.stepP(mastery),
            xp = session.xp,
            itemsAnswered = session.shownCount,
            heldOutCorrect = session.heldOutCorrect,
            trapKeys = Traps.keys(traps),
            trapLabels = Traps.labels(traps),
        )
}

/** Idempotency keys for attempts and events. */
object Ids {
    /** A random UUID v4 string (not deterministic: that is the point). */
    @OptIn(ExperimentalUuidApi::class)
    fun newClientId(): String = Uuid.random().toString()
}
