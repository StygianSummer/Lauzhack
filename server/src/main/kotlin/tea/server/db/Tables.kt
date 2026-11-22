package tea.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/*
 * Contract C1 (plan §5). Ids are UUIDs stored as 36-char strings; timestamps are timestamptz.
 * Table and column names are exactly the plan's.
 */

private const val ID = 36

object Users : Table("users") {
    val id = varchar("id", ID)
    val email = varchar("email", 320).uniqueIndex()
    val emailVerifiedAt = timestampWithTimeZone("email_verified_at").nullable()
    val firstName = varchar("first_name", 100).nullable()
    val lastName = varchar("last_name", 100).nullable()
    val displayName = varchar("display_name", 100).nullable()
    val language = varchar("language", 8)
    val country = varchar("country", 16).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val passwordHash = varchar("password_hash", 255).nullable()
    val termsAcceptedAt = timestampWithTimeZone("terms_accepted_at").nullable()
    val termsVersion = varchar("terms_version", 32).nullable()
    val practiceEmailsAllowed = bool("practice_emails_allowed").default(false)
    val practiceEmailsPausedAt = timestampWithTimeZone("practice_emails_paused_at").nullable()
    val practiceEmailsStoppedAt = timestampWithTimeZone("practice_emails_stopped_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Profiles : Table("profiles") {
    val userId = varchar("user_id", ID)
    val json = text("json")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

/** Findings about the accepted password only (plan §10.4). Never plaintext. */
object PasswordFindings : Table("password_findings") {
    val userId = varchar("user_id", ID)
    val length = integer("length")
    val classes = integer("classes")
    val score = integer("score")
    val pwned = bool("pwned")
    val pwnedCount = integer("pwned_count")
    val containsName = bool("contains_name")
    val containsEmail = bool("contains_email")
    val containsYear = bool("contains_year")
    /** null = none found; else a short pattern name, e.g. "qwerty_row" or "spatial" */
    val keyboardPattern = varchar("keyboard_pattern", 32).nullable()
    val analysedAt = timestampWithTimeZone("analysed_at")
    override val primaryKey = PrimaryKey(userId)
}

/** Device settings of plan §9.2.9 only. */
object ClientSignals : Table("client_signals") {
    val id = varchar("id", ID)
    val userId = varchar("user_id", ID).index()
    val platform = varchar("platform", 32)
    val json = text("json")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Mastery : Table("mastery") {
    val userId = varchar("user_id", ID)
    val skill = varchar("skill", 32)
    val p = double("p")
    val level = integer("level")
    val attempts = integer("attempts")
    val unaided = integer("unaided")
    val support = varchar("support", 16)
    val testedOut = bool("tested_out")
    val reviewInterval = double("review_interval")
    val nextReviewAt = timestampWithTimeZone("next_review_at").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId, skill)
}

object Attempts : Table("attempts") {
    val id = varchar("id", ID)
    val clientId = varchar("client_id", 64).uniqueIndex()
    val userId = varchar("user_id", ID).index()
    val itemId = varchar("item_id", 128)
    val context = varchar("context", 16)
    val answersJson = text("answers_json")
    val correct = bool("correct")
    val confidence = varchar("confidence", 16).nullable()
    val scaffoldJson = text("scaffold_json")
    val hintsUsed = integer("hints_used")
    val ms = long("ms")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Schema only in MVP 0. */
object Groups : Table("groups") {
    val id = varchar("id", ID)
    val name = varchar("name", 100)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Schema only in MVP 0. */
object GroupMembers : Table("group_members") {
    val groupId = varchar("group_id", ID)
    val userId = varchar("user_id", ID).index()
    override val primaryKey = PrimaryKey(groupId, userId)
}

/** Append-only events bus. client_id is null for server-emitted events. */
object Events : Table("events") {
    val id = varchar("id", ID)
    val clientId = varchar("client_id", 64).nullable().uniqueIndex()
    val userId = varchar("user_id", ID).index()
    val type = varchar("type", 64)
    val payloadJson = text("payload_json")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object EmailCodes : Table("email_codes") {
    val id = varchar("id", ID)
    val email = varchar("email", 320).index()
    val codeHash = varchar("code_hash", 64)
    val expiresAt = timestampWithTimeZone("expires_at")
    val tries = integer("tries").default(0)
    val createdAt = timestampWithTimeZone("created_at")
    val usedAt = timestampWithTimeZone("used_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Sessions : Table("sessions") {
    val id = varchar("id", ID)
    val userId = varchar("user_id", ID).index()
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val createdAt = timestampWithTimeZone("created_at")
    val lastSeenAt = timestampWithTimeZone("last_seen_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Part 5 practice emails. token_hash is set at send time (the raw token only lives in the email),
 * so it is null while the send is pending.
 */
object FieldSends : Table("field_sends") {
    val id = varchar("id", ID)
    val userId = varchar("user_id", ID).index()
    val templateId = varchar("template_id", 64)
    val tokenHash = varchar("token_hash", 64).nullable().uniqueIndex()
    val scheduledAt = timestampWithTimeZone("scheduled_at")
    val sentAt = timestampWithTimeZone("sent_at").nullable()
    val clickedAt = timestampWithTimeZone("clicked_at").nullable()
    val reportedAt = timestampWithTimeZone("reported_at").nullable()
    val cancelledAt = timestampWithTimeZone("cancelled_at").nullable()
    val lessonId = varchar("lesson_id", 64)
    val remindAt = timestampWithTimeZone("remind_at").nullable()
    val remindedAt = timestampWithTimeZone("reminded_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

val allTables: Array<Table> = arrayOf(
    Users, Profiles, PasswordFindings, ClientSignals, Mastery, Attempts,
    Groups, GroupMembers, Events, EmailCodes, Sessions, FieldSends,
)
