package tea.ui.screens.learn

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tea.app.TeaApp
import tea.model.StepKey

/**
 * Blob keys the learning screens persist through [TeaApp.save] (all JSON via TeaJson).
 * Home reads [STREAK] and [lessonSection].
 */
object LearnKeys {
    /** [tea.engine.PlacementState] of a placement in progress (null once finished). */
    const val PLACEMENT_STATE = "placement.state"

    /** Item ids shown during placement (`PlacementOutcome.seenIds`), passed to the first practice session. */
    const val PLACEMENT_SEEN = "placement.seen"

    /**
     * [tea.ui.screens.practice.PracticeRun] of one owner ("lesson:<id>" or "free"): the practice
     * section in progress, or the last finished one. Key: `practice.run.<owner>`.
     */
    fun practiceRun(owner: String) = "practice.run.$owner"

    /** [tea.engine.PracticeSessionState] of the last finished session: history + retry queue for the next one. */
    const val PRACTICE_LAST = "practice.last"
    const val PRACTICE_TRAPS = "practice.traps"
    const val PRACTICE_SHORTCUT = "practice.shortcut"

    /** [tea.ui.screens.practice.SummaryData] of the last finished practice section. */
    const val PRACTICE_SUMMARY = "practice.summary"

    /** [tea.engine.StreakState]; read by Home. */
    const val STREAK = "streak"

    /** Int: the lesson section the learner is on (Home's "Continue lesson 1"). */
    fun lessonSection(lessonId: String) = "lesson.$lessonId.section"

    /** Boolean: `lesson_started` already emitted. */
    fun lessonStarted(lessonId: String) = "lesson.$lessonId.started"

    /** Boolean: `lesson_completed` already emitted and its XP added. */
    fun lessonCompleted(lessonId: String) = "lesson.$lessonId.completed"

    /** String: state of the opt-in card ("dismissed", "scheduled" or "reason:<reason>"). */
    fun lessonReadiness(lessonId: String) = "lesson.$lessonId.readiness"

    /** List<Int>: ticked recovery steps (plan §9.4). */
    fun recovery(lessonId: String) = "tea.recovery.$lessonId"

    val BOOL: KSerializer<Boolean> = Boolean.serializer()
    val INT: KSerializer<Int> = Int.serializer()
    val STRING: KSerializer<String> = String.serializer()
    val STRING_SET: KSerializer<Set<String>> = SetSerializer(String.serializer())
    val INT_LIST: KSerializer<List<Int>> = ListSerializer(Int.serializer())
}

/** Serial name of a step ("who", "emotion", "ask", "verify"), as used in JSON payloads. */
val StepKey.key: String get() = name.lowercase()

/** `{who: p, emotion: p, ...}` for event payloads. */
fun stepMapJson(values: Map<StepKey, Double>): JsonObject =
    JsonObject(StepKey.entries.associate { it.key to JsonPrimitive(values[it] ?: 0.0) })

/** Emits [type] once per [flagKey] (stored as a Boolean blob). Returns true if it emitted now. */
fun TeaApp.emitOnce(flagKey: String, type: String, payload: JsonObject): Boolean {
    if (load(flagKey, LearnKeys.BOOL) == true) return false
    emit(type, payload)
    save(flagKey, LearnKeys.BOOL, true)
    return true
}
