package tea.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import tea.model.Attempt
import tea.model.MasteryState
import tea.model.Profile
import tea.model.TeaJson

/** A signed-in session. The token is the only secret the app keeps (never the password). */
@Serializable
data class Session(val token: String, val email: String, val userId: String? = null)

/** Small app-shell facts that are not part of the profile. */
@Serializable
data class LocalFlags(
    /** Version of the terms accepted on this device, null = not yet. */
    val termsVersion: String? = null,
    val onboardingDone: Boolean = false,
    val onboardingSkipped: List<String> = emptyList(),
    /** The server said this account has a password (or the learner set one here). */
    val passwordSet: Boolean = false,
    /** Device settings (plan §9.2.9) were posted once for the current account. */
    val signalsSent: Boolean = false,
    /** Last section opened per lesson id (for "Continue lesson 1"). */
    val lessonSections: Map<String, Int> = emptyMap(),
    /** Lesson ids with a `lesson_completed` event. */
    val lessonsDone: List<String> = emptyList(),
    /** The learner chose "Continue without an account". */
    val guest: Boolean = false,
)

/**
 * Cached outcome of the learner's practice emails (from `GET /me/events?type=field_*`).
 * [status]: "none" | "pending" | "sent" | "caught" (link clicked) | "dodged" (reported).
 */
@Serializable
data class FieldCache(
    val clicked: Boolean = false,
    val status: String = "none",
    /** Token of the last reveal link opened on this device, so Home can link back to it. */
    val revealToken: String? = null,
    /** Lesson id → send id for clicked field emails not yet answered on this device. */
    val pendingGotchas: Map<String, String> = emptyMap(),
    /** Completed locally while offline, until the server confirms the matching event. */
    val resolvedGotchaSendIds: Set<String> = emptySet(),
)

/**
 * Typed access to everything the app keeps on the device. All keys start with `tea.`:
 *
 * | key | value |
 * |---|---|
 * | `tea.session` | [Session] (token, email, user id) |
 * | `tea.pending_email` | address waiting for its code |
 * | `tea.profile` | [Profile] |
 * | `tea.mastery` | list of [MasteryState] |
 * | `tea.xp` | Int, local running total |
 * | `tea.attempts` | last [MAX_ATTEMPTS] [Attempt]s (log for "What we know about you") |
 * | `tea.flags` | [LocalFlags] |
 * | `tea.field` | [FieldCache] |
 * | `tea.api` | backend URL chosen with `?api=` or in Settings (plan §12, C5) |
 * | `tea.queue.events` / `.attempts` / `.mastery` / `.profile` | [tea.net.SyncQueue] |
 * | `tea.blob.<key>` | engine blobs written through `TeaApp.save` |
 */
class LocalState(val store: KeyValueStore) {

    fun <T> read(key: String, serializer: KSerializer<T>): T? =
        store.get(key)?.let { runCatching { TeaJson.decodeFromString(serializer, it) }.getOrNull() }

    fun <T> write(key: String, serializer: KSerializer<T>, value: T?) {
        if (value == null) store.remove(key) else store.put(key, TeaJson.encodeToString(serializer, value))
    }

    var session: Session?
        get() = read(KEY_SESSION, Session.serializer())
        set(v) = write(KEY_SESSION, Session.serializer(), v)

    var pendingEmail: String?
        get() = store.get(KEY_PENDING_EMAIL)
        set(v) = if (v == null) store.remove(KEY_PENDING_EMAIL) else store.put(KEY_PENDING_EMAIL, v)

    var profile: Profile
        get() = read(KEY_PROFILE, Profile.serializer()) ?: Profile()
        set(v) = write(KEY_PROFILE, Profile.serializer(), v)

    val hasProfile: Boolean get() = store.get(KEY_PROFILE) != null

    var mastery: List<MasteryState>
        get() = read(KEY_MASTERY, ListSerializer(MasteryState.serializer())) ?: emptyList()
        set(v) = write(KEY_MASTERY, ListSerializer(MasteryState.serializer()), v)

    var xp: Int
        get() = store.get(KEY_XP)?.toIntOrNull() ?: 0
        set(v) = store.put(KEY_XP, v.toString())

    var attempts: List<Attempt>
        get() = read(KEY_ATTEMPTS, ListSerializer(Attempt.serializer())) ?: emptyList()
        set(v) = write(KEY_ATTEMPTS, ListSerializer(Attempt.serializer()), v.takeLast(MAX_ATTEMPTS))

    fun appendAttempt(a: Attempt) {
        attempts = (attempts.filterNot { it.clientId == a.clientId } + a)
    }

    var flags: LocalFlags
        get() = read(KEY_FLAGS, LocalFlags.serializer()) ?: LocalFlags()
        set(v) = write(KEY_FLAGS, LocalFlags.serializer(), v)

    fun updateFlags(transform: (LocalFlags) -> LocalFlags) {
        flags = transform(flags)
    }

    var field: FieldCache
        get() = read(KEY_FIELD, FieldCache.serializer()) ?: FieldCache()
        set(v) = write(KEY_FIELD, FieldCache.serializer(), v)

    var apiOverride: String?
        get() = store.get(KEY_API)
        set(v) = if (v.isNullOrBlank()) store.remove(KEY_API) else store.put(KEY_API, v)

    fun <T> loadBlob(key: String, serializer: KSerializer<T>): T? = read(BLOB_PREFIX + key, serializer)
    fun <T> saveBlob(key: String, serializer: KSerializer<T>, value: T?) = write(BLOB_PREFIX + key, serializer, value)

    /** Number of engine blobs stored (shown to guests in "What we know about you"). */
    fun blobKeys(): List<String> = store.keys().filter { it.startsWith(BLOB_PREFIX) }.map { it.removePrefix(BLOB_PREFIX) }.sorted()

    /** Deletes everything ThisIsTea stored on this device, except the chosen backend URL. */
    fun clearAll() {
        val api = apiOverride
        store.clear("tea.")
        apiOverride = api
    }

    companion object {
        const val MAX_ATTEMPTS = 500
        const val KEY_SESSION = "tea.session"
        const val KEY_PENDING_EMAIL = "tea.pending_email"
        const val KEY_PROFILE = "tea.profile"
        const val KEY_MASTERY = "tea.mastery"
        const val KEY_XP = "tea.xp"
        const val KEY_ATTEMPTS = "tea.attempts"
        const val KEY_FLAGS = "tea.flags"
        const val KEY_FIELD = "tea.field"
        const val KEY_API = "tea.api"
        const val BLOB_PREFIX = "tea.blob."
    }
}
