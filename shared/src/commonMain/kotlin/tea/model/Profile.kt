package tea.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** Learner profile (plan §4.3). Stored locally and in `profiles.json` on the server. */
@Serializable
data class Profile(
    val language: String = "en",
    val country: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    /** gmail|outlook|proton|icloud|other (derived by the server from the verified address) */
    val emailProvider: String? = null,
    /** myself|relative|ngo|company */
    val hereFor: String? = null,
    /** optional: <25|25-44|45-64|65+ */
    val ageRange: String? = null,
    /** email|sms|messengers|calls|social */
    val channels: List<String> = emptyList(),
    /** webmail|outlook|phone_app */
    val emailApp: String? = null,
    /** banking|parcels|jobs|investing|donations|social|invoices|grants|shared_accounts|beneficiary_data */
    val interests: List<String> = emptyList(),
    /** none|unsure_message|lost_money|prefer_not */
    val pastExperience: String? = null,
    val pastExperienceText: String? = null,
    /** 1..5 */
    val selfRating: Int? = null,
    val largeText: Boolean = false,
    /** null = follow system */
    val darkMode: Boolean? = null,
    val dailyMinutes: Int = 5,
    val placement: PlacementResult? = null,
    /** LOW|MEDIUM|HIGH from the lesson */
    val statusRating: String? = null,
) {
    /** Feedback switches to the gentle tone (plan §9.2.6). */
    val gentleTone: Boolean get() = pastExperience == "lost_money" || !pastExperienceText.isNullOrBlank()
}

/** Output of the placement test (plan §6.2). */
@Serializable
data class PlacementResult(
    val level: Int,
    val stepP: Map<StepKey, Double>,
    /** hit = scam flagged */
    val hitRate: Double,
    /** false alarm = legit flagged */
    val falseAlarmRate: Double,
    /** mean |confidenceScore - correct|, 0..1; lower is better */
    val calibration: Double,
    val overconfident: Boolean,
    /** keys "news", "image" */
    val breadth: Map<String, Boolean> = emptyMap(),
    val totalMs: Long,
    val strength: StepKey,
    val focus: StepKey,
)

/** Per-skill mastery (plan §4.3, §6.3). For Skill.VERDICT, `level` is the target item difficulty. */
@Serializable
data class MasteryState(
    val skill: Skill,
    val p: Double,
    val level: Int = 1,
    val attempts: Int = 0,
    val unaided: Int = 0,
    val support: Support = Support.WORKED,
    val testedOut: Boolean = false,
    val nextReviewAt: Instant? = null,
    val reviewInterval: Double = 1.0,
    val updatedAt: Instant,
) {
    /** Mastered = p >= 0.95 && unaided >= 3. */
    val mastered: Boolean get() = p >= 0.95 && unaided >= 3
}

/** One answered item (plan §4.3). `clientId` is a UUID and the idempotency key for sync. */
@Serializable
data class Attempt(
    val clientId: String,
    val itemId: String,
    val context: Context,
    val answers: AttemptAnswers,
    /** verdict right (for breadth / single-mode placement items: that question right) */
    val correct: Boolean,
    val confidence: Confidence? = null,
    val scaffold: Map<StepKey, Support> = emptyMap(),
    val hintsUsed: Int = 0,
    val ms: Long,
    val createdAt: Instant,
)

@Serializable
data class AttemptAnswers(
    val verdict: Verdict? = null,
    val actionId: String? = null,
    /** FIRST choice per step; null value = "I don't know yet". Absent key = step not asked. */
    val stepChoices: Map<StepKey, Int?> = emptyMap(),
    val hotspotsTapped: List<String> = emptyList(),
    val linkChoice: Int? = null,
    val newsForward: Boolean? = null,
    val imageAi: Boolean? = null,
    val freeText: String? = null,
)
