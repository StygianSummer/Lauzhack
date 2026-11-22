package tea.app

import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import tea.content.ContentRepository
import tea.engine.Ids
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.MasteryState
import tea.model.Profile
import tea.model.ReadinessResponse
import tea.model.Skill
import tea.model.TeaJson

/**
 * In-memory [TeaApp] for tests and screenshots: no store, no network. Everything written is kept
 * in public lists/maps so tests can assert on it. The clock only moves when the test moves it.
 */
class FakeTeaApp(
    override val content: ContentRepository,
    profile: Profile = Profile(firstName = "Sam", country = "CH"),
    email: String? = "sam@mail.example",
    online: Boolean = true,
    loggedIn: Boolean = true,
    override val debug: Boolean = false,
    var clock: Instant = Instant.parse("2026-11-21T10:00:00Z"),
    mastery: Map<Skill, MasteryState> = emptyMap(),
    xp: Int = 0,
) : TeaApp {
    override val profile = MutableStateFlow(profile)
    override val email = MutableStateFlow(email)
    override val lang = MutableStateFlow(profile.language)
    override val online = MutableStateFlow(online)
    override val loggedIn = MutableStateFlow(loggedIn)
    override val mastery = MutableStateFlow(mastery)
    override val xp = MutableStateFlow(xp)
    override val fieldClicked = MutableStateFlow(false)

    val attempts = mutableListOf<Attempt>()
    val events = mutableListOf<AppEvent>()
    val blobs = mutableMapOf<String, String>()
    val stack = mutableListOf<Screen>()
    val opened = mutableListOf<String>()
    val readinessCalls = mutableListOf<String>()

    /** What [requestReadiness] answers (null = offline / failed). */
    var readiness: ReadinessResponse? = ReadinessResponse(scheduled = true)

    override fun updateProfile(transform: (Profile) -> Profile) {
        profile.value = transform(profile.value)
    }

    override fun saveMastery(states: Collection<MasteryState>) {
        mastery.value = mastery.value + states.associateBy { it.skill }
    }

    override fun addXp(amount: Int) {
        xp.value += amount
    }

    override fun recordAttempt(attempt: Attempt) {
        attempts += attempt
    }

    override fun emit(type: String, payload: JsonObject) {
        events += AppEvent(Ids.newClientId(), type, payload, clock)
    }

    override fun <T> load(key: String, serializer: KSerializer<T>): T? =
        blobs[key]?.let { TeaJson.decodeFromString(serializer, it) }

    override fun <T> save(key: String, serializer: KSerializer<T>, value: T?) {
        if (value == null) blobs.remove(key) else blobs[key] = TeaJson.encodeToString(serializer, value)
    }

    override suspend fun requestReadiness(lessonId: String): ReadinessResponse? {
        readinessCalls += lessonId
        return readiness
    }

    override fun now(): Instant = clock

    fun advance(d: Duration) {
        clock += d
    }

    override fun navigate(screen: Screen) {
        stack += screen
    }

    override fun navigateRoot(screen: Screen) {
        stack.clear()
        stack += screen
    }

    override fun back() {
        stack.removeLastOrNull()
    }

    override fun openUrl(url: String) {
        opened += url
    }

    fun eventsOf(type: String): List<AppEvent> = events.filter { it.type == type }
}
