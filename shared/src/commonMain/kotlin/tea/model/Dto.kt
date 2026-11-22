package tea.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * HTTP API DTOs (plan §10.3). All bodies are JSON encoded with TeaJson (snake_case keys).
 * Auth: header `Authorization: Bearer <token>`.
 * Errors: non-2xx with body ApiError.
 *
 *  GET    /health                      -> HealthResponse
 *  POST   /auth/start                  StartRequest -> 204
 *  POST   /auth/verify                 VerifyRequest -> VerifyResponse
 *  POST   /auth/password/attempt       PasswordAttemptRequest -> PasswordAttemptResponse   (session)
 *  POST   /auth/login                  LoginRequest -> LoginResponse
 *  POST   /auth/logout                 -> 204                                              (session)
 *  GET    /me/profile                  -> Profile                                          (session)
 *  PUT    /me/profile                  Profile -> Profile                                  (session)
 *  POST   /me/signals                  SignalsRequest -> 204                               (session)
 *  GET    /me/about                    -> List<AboutEntry>                                 (session)
 *  DELETE /me                          -> 204                                              (session)
 *  POST   /me/attempts                 List<Attempt> -> AttemptsResponse                   (session)
 *  GET    /me/mastery                  -> List<MasteryState>                               (session)
 *  PUT    /me/mastery                  List<MasteryState> -> List<MasteryState> (merged)   (session)
 *  POST   /me/events                   List<AppEvent> -> 204                               (session)
 *  GET    /me/events?type=field_*      -> List<StoredEvent>  (newest first)                (session)
 *  GET    /me/settings                 -> SettingsResponse                                 (session)
 *  PUT    /me/settings                 SettingsRequest -> SettingsResponse                 (session)
 *  GET    /leaderboard?period=week|all -> LeaderboardResponse                     (optional session)
 *  POST   /me/readiness                ReadinessRequest -> ReadinessResponse               (session)
 *  POST   /me/field/report             -> 204                                              (session)
 *  GET    /me/field/sends/{sendId}     -> GotchaResponse                                  (session)
 *  GET    /f/c/{token}                 -> 302 APP_PUBLIC_URL#/reveal/{token}
 *  GET    /field/reveal/{token}        -> RevealResponse
 *  POST   /admin/field/cancel          AdminCancelRequest -> 204            (header X-Admin-Key)
 *  POST   /ai/grade (optional)         GradeRequest -> GradeResponse                       (session)
 */

@Serializable
data class ApiError(val error: String, val message: String = "")

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val db: Boolean = false,
    /** "smtp" | "console" */
    val mailer: String = "console",
    val version: String = "dev",
    /** true when POST /ai/grade is available */
    val ai: Boolean = false,
)

@Serializable
data class StartRequest(val email: String, val language: String = "en")

@Serializable
data class VerifyRequest(val email: String, val code: String)

@Serializable
data class VerifyResponse(val token: String, val userId: String, val hasPassword: Boolean)

@Serializable
data class PasswordAttemptRequest(val password: String)

/** reason: "too_short" | "breached" (only when !accepted) */
@Serializable
data class PasswordAttemptResponse(val accepted: Boolean, val reason: String? = null)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val userId: String? = null)

/** Device settings of plan §9.2.9 and nothing else. `json` holds the listed fields. */
@Serializable
data class SignalsRequest(val platform: String, val json: JsonObject)

/** One line of "What we know about you": field name, its value in plain words, why we keep it. */
@Serializable
data class AboutEntry(val field: String, val value: String, val purpose: String)

@Serializable
data class AttemptsResponse(val stored: Int, val duplicates: Int)

@Serializable
data class StoredEvent(val type: String, val payload: JsonObject, val createdAt: Instant)

/** practiceEmails: "allowed" | "pause" | "stop" (null = leave unchanged). */
@Serializable
data class SettingsRequest(val practiceEmails: String? = null)

/**
 * practiceEmails: "off" (never opted in) | "allowed" | "paused" | "stopped".
 * pending: a practice email is scheduled and not yet sent (never says when).
 */
@Serializable
data class SettingsResponse(val practiceEmails: String = "off", val pending: Boolean = false)

@Serializable
data class LeaderboardRow(
    val displayName: String,
    val xp: Int,
    val rank: Int,
    /** The signed-in learner's own row (added: display names and ranks are not unique, so the app cannot tell from them). */
    val isMe: Boolean = false,
)

@Serializable
data class LeaderboardResponse(val top: List<LeaderboardRow> = emptyList(), val me: LeaderboardRow? = null)

@Serializable
data class ReadinessRequest(val lessonId: String)

/** reason (only when !scheduled): "paused" | "stopped" | "not_verified" | "already_done" | "terms_missing" */
@Serializable
data class ReadinessResponse(val scheduled: Boolean, val reason: String? = null)

/** Read by the Reveal screen. steps/hints come from the field template's `reveal` block. */
@Serializable
data class RevealResponse(
    val templateId: String,
    val steps: Map<StepKey, String> = emptyMap(),
    val hints: List<String> = emptyList(),
)

/** Safe, rendered practice-email facts for the signed-in learner's gotcha interview. */
@Serializable
data class GotchaResponse(
    val sendId: String,
    val lessonId: String,
    val templateId: String,
    val clickedAt: Instant,
    val subject: String,
    val text: String,
    val steps: Map<StepKey, String> = emptyMap(),
    val hints: List<String> = emptyList(),
)

@Serializable
data class AdminCancelRequest(val userId: String)

@Serializable
data class GradeRequest(val itemId: String, val text: String)

@Serializable
data class GradeResponse(val steps: Map<StepKey, Boolean>)

/** Server-only: content/field/<template>.<lang>.json (plan §12). */
@Serializable
data class FieldTemplate(
    val id: String,
    val subject: String,
    val text: String,
    val html: String? = null,
    val reveal: FieldReveal = FieldReveal(),
)

@Serializable
data class FieldReveal(
    val steps: Map<StepKey, String> = emptyMap(),
    val hints: List<String> = emptyList(),
)
