package tea.app

import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import tea.content.ContentRepository
import tea.model.Attempt
import tea.model.MasteryState
import tea.model.Profile
import tea.model.ReadinessResponse
import tea.model.Skill

/**
 * Navigation targets (plan §9.1). The app shell owns the back stack and, on web, mirrors the
 * screen in `location.hash` (`#/home`, `#/reveal/<token>`).
 */
sealed interface Screen {
    data object Language : Screen
    data object Email : Screen
    data class Code(val email: String) : Screen
    data object Name : Screen
    data object Password : Screen
    data class Onboarding(val q: Int) : Screen
    data object Terms : Screen
    data object PlacementIntro : Screen
    data object Placement : Screen
    data object PlacementResult : Screen
    data object Home : Screen

    /** [section] = index into Lesson.sections. */
    data class Lesson(val id: String, val section: Int = 0) : Screen

    /** A field-intercept interview for the send tied to [lessonId]. */
    data class Gotcha(val sendId: String, val lessonId: String) : Screen

    /** A free "Practise again" section outside a lesson; [focusWeakest] is set from the Reveal screen. */
    data class Practice(val focusWeakest: Boolean = false) : Screen
    data object PracticeSummary : Screen
    data class Reveal(val token: String) : Screen
    data object About : Screen
    data object Settings : Screen
    data object Leaderboard : Screen
    data object Gallery : Screen
}

/**
 * The contract between the app shell (AppState: store, network, navigation) and the learning
 * screens (placement, lesson, practice, summary). The shell implements it; learning screens only
 * talk to this interface, never to the store or the network directly.
 *
 * Everything written through this interface is persisted locally at once and queued for sync
 * (offline first, plan §12). All functions are safe to call from the UI thread.
 */
interface TeaApp {
    val content: ContentRepository

    // ---- who is learning
    val profile: StateFlow<Profile>

    /** The registered address, or null for a guest (placeholders then use "you@mail.example"). */
    val email: StateFlow<String?>

    /** Current UI + content language code ("en", "fr", "es"). */
    val lang: StateFlow<String>
    val online: StateFlow<Boolean>
    val loggedIn: StateFlow<Boolean>

    /** True in dev/debug builds: enables demo aids (raw p per step, unverified resource links). */
    val debug: Boolean

    fun updateProfile(transform: (Profile) -> Profile)

    // ---- learning state
    val mastery: StateFlow<Map<Skill, MasteryState>>

    /** Replaces the given skills (others untouched), persists, queues sync. */
    fun saveMastery(states: Collection<MasteryState>)

    /** Total XP shown in the UI (local running total). */
    val xp: StateFlow<Int>
    fun addXp(amount: Int)

    /** Persists the attempt locally and queues it for `POST /me/attempts`. */
    fun recordAttempt(attempt: Attempt)

    /** Emits a C1 event (plan §5.1; types in tea.model.EventTypes): stored locally, queued for sync. */
    fun emit(type: String, payload: JsonObject = JsonObject(emptyMap()))

    /**
     * Small JSON blobs for engine state that must survive reloads (PlacementState,
     * PracticeSessionState, TrapState, ShortcutStats, StreakState, recovery ticks, lesson progress).
     * Keys are namespaced by the caller, e.g. "practice.session", "tea.recovery.email-01".
     */
    fun <T> load(key: String, serializer: KSerializer<T>): T?
    fun <T> save(key: String, serializer: KSerializer<T>, value: T?)

    // ---- part 5 hooks used by the lesson
    /** `POST /me/readiness`. Null when offline, not logged in, or the call failed. */
    suspend fun requestReadiness(lessonId: String): ReadinessResponse?

    /** True if a `field_clicked` event exists for this user (cached; false when offline/guest). */
    val fieldClicked: StateFlow<Boolean>

    // ---- environment
    fun now(): Instant
    fun navigate(screen: Screen)

    /** Replace the whole back stack with [screen] (e.g. after placement → Home). */
    fun navigateRoot(screen: Screen)
    fun back()

    /** Opens the URL in the system browser / a new tab. */
    fun openUrl(url: String)
}
