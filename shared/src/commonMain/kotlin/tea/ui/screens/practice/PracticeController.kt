package tea.ui.screens.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tea.app.TeaApp
import tea.engine.Mastery
import tea.engine.Practice
import tea.engine.PracticeAnswer
import tea.engine.PracticeOutcome
import tea.engine.PracticeSessionState
import tea.engine.PracticeStep
import tea.engine.Scaffold
import tea.engine.ShortcutStats
import tea.engine.Streak
import tea.engine.StreakState
import tea.engine.TrapState
import tea.engine.Xp
import tea.model.EventTypes
import tea.model.Item
import tea.model.MasteryState
import tea.model.PracticeSection
import tea.model.Skill
import tea.model.StepKey
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.learn.emitOnce
import tea.ui.screens.learn.key
import tea.ui.screens.learn.stepMapJson

/**
 * The practice section in progress, persisted under [LearnKeys.practiceRun] after every change.
 * [owner] says which screen it belongs to ("lesson:<id>" or "free"); [seed] + the number of items
 * shown make item selection deterministic, so a reload shows the same item again.
 */
@Serializable
data class PracticeRun(
    val owner: String,
    val seed: Long,
    val session: PracticeSessionState,
    val currentItemId: String? = null,
    val finished: Boolean = false,
)

/** What the practice summary shows; persisted under [LearnKeys.PRACTICE_SUMMARY] so it survives navigation. */
@Serializable
data class SummaryData(
    val owner: String,
    val lessonId: String? = null,
    val before: Map<StepKey, Double>,
    val after: Map<StepKey, Double>,
    val xp: Int,
    val lessonBonus: Int = 0,
    val itemsAnswered: Int,
    val heldOutCorrect: Boolean? = null,
    val traps: List<SummaryTrap> = emptyList(),
    val streak: Int = 0,
)

/** One "watch out for" line: the clue and whether the learner misses it or over-flags it. */
@Serializable
data class SummaryTrap(val clue: String, val misses: Boolean)

/**
 * Drives one practice section (plan §6.5, §9.5) on top of the pure [Practice] engine: picks items,
 * applies answers, and writes everything through [TeaApp]: mastery, attempts, XP, events
 * (`practice_item_answered`, `held_out_answered`, `lesson_completed`), traps, shortcut stats,
 * streak, and the summary. No UI code: the practice screen and the headless tests use it the same way.
 *
 * Loop: [current] → render → [submit] → show [lastOutcome] → [advance] … until [finished].
 */
class PracticeController(
    private val app: TeaApp,
    private val pool: List<Item>,
    /** "lesson:<id>" or "free". */
    val owner: String,
    private val section: PracticeSection = PracticeSection(),
    /** Set inside a lesson: the end of the section emits `lesson_completed` (once) and adds its XP. */
    val lessonId: String? = null,
    /** Focus the section on the learner's weakest step (from the Reveal screen). */
    private val focusWeakest: Boolean = false,
    /** Start a new section even if a finished one of the same owner is stored. */
    restart: Boolean = false,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    random: Random = Random.Default,
) {
    var mastery: Map<Skill, MasteryState> by mutableStateOf(Mastery.toMap(app.mastery.value.values.toList(), app.now()))
        private set
    var traps: TrapState by mutableStateOf(app.load(LearnKeys.PRACTICE_TRAPS, TrapState.serializer()) ?: TrapState())
        private set
    var shortcut: ShortcutStats by mutableStateOf(app.load(LearnKeys.PRACTICE_SHORTCUT, ShortcutStats.serializer()) ?: ShortcutStats())
        private set
    var streak: StreakState by mutableStateOf(app.load(LearnKeys.STREAK, StreakState.serializer()) ?: StreakState())
        private set

    var run: PracticeRun by mutableStateOf(loadOrStart(restart, random))
        private set

    /** The item on screen (null between items, or when the section is over). */
    var current: PracticeStep? by mutableStateOf(null)
        private set

    /** Result of the last [submit], shown as feedback until [advance]. */
    var lastOutcome: PracticeOutcome? by mutableStateOf(null)
        private set

    /** The answer given to [current], kept with [lastOutcome] for the feedback card. */
    var lastAnswer: PracticeAnswer? by mutableStateOf(null)
        private set

    var summary: SummaryData? by mutableStateOf(if (run.finished) app.load(LearnKeys.PRACTICE_SUMMARY, SummaryData.serializer()) else null)
        private set

    val finished: Boolean get() = run.finished

    init {
        if (!run.finished) {
            val resumedItem = run.currentItemId?.let { id -> pool.firstOrNull { it.id == id } }
            if (resumedItem != null) current = stepFor(resumedItem) else advance()
        } else if (summary == null) {
            // Finished but the summary blob is gone: rebuild it from the stored session.
            summary = buildSummary(0)
        }
    }

    /** Regular items answered so far in this section. */
    val practiceCount: Int get() = run.session.practiceCount
    val count: Int get() = run.session.count

    /** p per step right now (for the top bar). */
    val stepP: Map<StepKey, Double> get() = Mastery.stepP(mastery)

    /** Support each step would get on a regular item now (for the top bar labels). */
    val stepSupport: Map<StepKey, tea.model.Support>
        get() = StepKey.entries.associateWith { mastery[it.skill]?.support ?: Scaffold.levelFor(mastery[it.skill]?.p ?: 0.0) }

    val today: LocalDate get() = Streak.localDay(app.now(), zone)

    /** Streak to show today. */
    val streakNow: Int get() = Streak.display(streak, today)

    /** Applies [answer] to [current] and writes everything. Returns the outcome (also in [lastOutcome]). */
    fun submit(answer: PracticeAnswer): PracticeOutcome {
        val step = checkNotNull(current) { "no item on screen" }
        val now = app.now()
        val day = today
        val out = Practice.applyAnswer(step.item, answer, mastery, traps, shortcut, run.session, now, day)

        mastery = out.mastery
        app.saveMastery(out.mastery.values)
        traps = out.traps
        app.save(LearnKeys.PRACTICE_TRAPS, TrapState.serializer(), traps)
        shortcut = out.shortcut
        app.save(LearnKeys.PRACTICE_SHORTCUT, ShortcutStats.serializer(), shortcut)

        app.recordAttempt(out.attempt)
        if (out.xp > 0) app.addXp(out.xp)
        if (step.heldOut) {
            app.emit(
                EventTypes.HELD_OUT_ANSWERED,
                JsonObject(
                    mapOf(
                        "item_id" to JsonPrimitive(step.item.id),
                        "correct" to JsonPrimitive(out.verdictCorrect && out.actionSafe),
                        "verdict" to JsonPrimitive(answer.verdict?.name?.lowercase()),
                        "action_id" to JsonPrimitive(answer.actionId),
                    ),
                ),
            )
        } else {
            app.emit(
                EventTypes.PRACTICE_ITEM_ANSWERED,
                JsonObject(
                    mapOf(
                        "item_id" to JsonPrimitive(step.item.id),
                        "correct" to JsonPrimitive(out.verdictCorrect),
                        "scaffold" to JsonObject(out.attempt.scaffold.entries.associate { (k, v) -> k.key to JsonPrimitive(v.name.lowercase()) }),
                    ),
                ),
            )
        }

        streak = Streak.record(streak, day)
        app.save(LearnKeys.STREAK, StreakState.serializer(), streak)

        // currentItemId = null: a reload during the feedback moves on to the next item.
        run = run.copy(session = out.session, currentItemId = null)
        saveRun()
        lastOutcome = out
        lastAnswer = answer
        return out
    }

    /** Moves to the next item, or ends the section (spaced review, summary, lesson completion). */
    fun advance() {
        lastOutcome = null
        lastAnswer = null
        if (run.finished) return
        val step = Practice.next(pool, mastery, run.session, shortcut, traps, app.now(), Random(run.seed + run.session.shownCount))
        if (step == null) {
            current = null
            // Nothing to practise at all (e.g. content failed to load): do not end the section.
            if (run.session.shownCount > 0) finish()
        } else {
            current = step
            run = run.copy(currentItemId = step.item.id)
            saveRun()
        }
    }

    private fun finish() {
        val now = app.now()
        mastery = Practice.endSession(run.session, mastery, now)
        app.saveMastery(mastery.values)
        var bonus = 0
        if (lessonId != null) {
            val payload = JsonObject(
                mapOf(
                    "lesson_id" to JsonPrimitive(lessonId),
                    "xp" to JsonPrimitive(run.session.xp),
                    "per_step" to stepMapJson(Mastery.stepP(mastery)),
                ),
            )
            if (app.emitOnce(LearnKeys.lessonCompleted(lessonId), EventTypes.LESSON_COMPLETED, payload)) {
                app.addXp(Xp.LESSON_COMPLETED)
                bonus = Xp.LESSON_COMPLETED
            }
        }
        val data = buildSummary(bonus)
        summary = data
        app.save(LearnKeys.PRACTICE_SUMMARY, SummaryData.serializer(), data)
        app.save(LearnKeys.PRACTICE_LAST, PracticeSessionState.serializer(), run.session)
        run = run.copy(finished = true, currentItemId = null)
        saveRun()
    }

    private fun buildSummary(bonus: Int): SummaryData {
        val s = Practice.summary(run.session, mastery, traps)
        return SummaryData(
            owner = owner,
            lessonId = lessonId,
            before = s.before,
            after = s.after,
            xp = s.xp,
            lessonBonus = bonus,
            itemsAnswered = s.itemsAnswered,
            heldOutCorrect = s.heldOutCorrect,
            traps = traps.active.map { SummaryTrap(it.clue, it.kind == tea.engine.TrapKind.MISS) },
            streak = streakNow,
        )
    }

    private fun stepFor(item: Item): PracticeStep =
        PracticeStep(item, heldOut = item.heldOut, support = Scaffold.supportMap(item, mastery))

    private fun saveRun() = app.save(LearnKeys.practiceRun(owner), PracticeRun.serializer(), run)

    private fun loadOrStart(restart: Boolean, random: Random): PracticeRun {
        val stored = app.load(LearnKeys.practiceRun(owner), PracticeRun.serializer())
        if (stored != null && !restart) {
            // Same section: resume it, or show its summary when it is over (lesson). A free section
            // that is over starts again: opening "Practise again" means a new one.
            if (!stored.finished || lessonId != null) return stored
        }
        val previous = app.load(LearnKeys.PRACTICE_LAST, PracticeSessionState.serializer())
        val seen = if (previous == null) app.load(LearnKeys.PLACEMENT_SEEN, LearnKeys.STRING_SET).orEmpty() else emptySet()
        val focus = if (focusWeakest) listOfNotNull(Mastery.weakestStep(mastery)) else section.focus
        val session = Practice.startSession(
            count = section.count,
            focus = focus,
            endWithHeldOut = section.endWithHeldOut,
            mastery = mastery,
            placementLevel = app.profile.value.placement?.level,
            previous = previous,
            alreadySeen = seen,
        )
        return PracticeRun(owner = owner, seed = random.nextLong(), session = session).also {
            app.save(LearnKeys.practiceRun(owner), PracticeRun.serializer(), it)
        }
    }

    companion object {
        fun lessonOwner(lessonId: String) = "lesson:$lessonId"
        const val FREE_OWNER = "free"
    }
}
