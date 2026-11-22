package tea.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Contract C1 events bus (plan §5.1). `clientId` is a UUID, idempotency key. */
@Serializable
data class AppEvent(
    val clientId: String,
    val type: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    val createdAt: Instant,
)

object EventTypes {
    // server-emitted
    const val REGISTERED = "registered"
    const val EMAIL_VERIFIED = "email_verified"
    const val FIELD_REMINDED = "field_reminded"

    // client-emitted
    /** payload {version}. On receipt the server also sets users.terms_accepted_at / terms_version. */
    const val TERMS_ACCEPTED = "terms_accepted"
    const val ONBOARDING_COMPLETED = "onboarding_completed"

    /** payload = PlacementResult. Worth +20 XP, once. */
    const val PLACEMENT_COMPLETED = "placement_completed"
    const val LESSON_STARTED = "lesson_started"
    const val LESSON_STATUS_RATED = "lesson_status_rated"
    const val PRACTICE_ITEM_ANSWERED = "practice_item_answered"
    const val HELD_OUT_ANSWERED = "held_out_answered"

    /** payload {lesson_id, xp, per_step}. Worth +50 XP, once per lesson_id. */
    const val LESSON_COMPLETED = "lesson_completed"
    const val PRACTICE_EMAIL_OPTED_IN = "practice_email_opted_in"
    /** Client-emitted, payload {send_id, lesson_id, answer, reason?}. */
    const val FIELD_GOTCHA = "field_gotcha"

    // server-emitted, part 5. Clients may only post FIELD_GOTCHA from this prefix.
    const val FIELD_SCHEDULED = "field_scheduled"
    const val FIELD_SENT = "field_sent"
    const val FIELD_CLICKED = "field_clicked"
    const val FIELD_REPORTED = "field_reported"
    const val FIELD_CANCELLED = "field_cancelled"
    const val FIELD_PREFIX = "field_"
}

const val TERMS_VERSION = "2026-11-21"
const val LESSON_1_ID = "email-01"
const val FIELD_TEMPLATE_ID = "practice-email-01"
