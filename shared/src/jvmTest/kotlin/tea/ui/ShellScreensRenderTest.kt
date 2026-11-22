package tea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import tea.app.AppState
import tea.app.FakePlatformInfo
import tea.app.Screen
import tea.content.ContentRepository
import tea.model.PlacementResult
import tea.model.Profile
import tea.model.SettingsResponse
import tea.model.StepKey
import tea.model.TERMS_VERSION
import tea.net.FakeApi
import tea.store.FieldCache
import tea.store.InMemoryKeyValueStore
import tea.store.LocalFlags
import tea.store.LocalState
import tea.store.Session
import tea.ui.components.TeaDialogCard
import tea.ui.screens.AppRoot
import tea.ui.screens.home.SuspiciousBody
import tea.ui.screens.onboarding.TermsText
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Renders the app-shell screens off-screen (light, dark, compact 380dp) to
 * `shared/build/screenshots/shell_*.png`, through the real [AppRoot] (theme, language, router,
 * banner, footer) with a fake server.
 */
class ShellScreensRenderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val now = Instant.parse("2026-11-21T14:05:00Z")
    private val placement = PlacementResult(
        level = 2, stepP = StepKey.entries.associateWith { 0.5 }, hitRate = 0.8, falseAlarmRate = 0.1,
        calibration = 0.2, overconfident = false, totalMs = 180_000, strength = StepKey.WHO, focus = StepKey.VERIFY,
    )

    private fun fieldEvent(type: String) = tea.model.StoredEvent(
        type,
        kotlinx.serialization.json.buildJsonObject { put("send_id", kotlinx.serialization.json.JsonPrimitive("s1")) },
        now,
    )

    @AfterTest
    fun close() = scope.cancel()

    private fun app(
        screen: Screen,
        profile: Profile = Profile(),
        loggedIn: Boolean = false,
        online: Boolean = true,
        flags: LocalFlags = LocalFlags(),
        field: FieldCache = FieldCache(),
        api: FakeApi = FakeApi(),
    ): AppState {
        val store = InMemoryKeyValueStore()
        LocalState(store).apply {
            this.profile = profile
            this.flags = flags
            this.field = field
            if (loggedIn) session = Session("tok", "ada@mail.example", "u1")
            pendingEmail = "ada@mail.example"
        }
        api.onlineFlow.value = online
        val app = AppState(
            platform = FakePlatformInfo(),
            local = LocalState(store),
            content = ContentRepository(),
            scope = scope,
            apiFactory = { _, token -> api.also { it.token = token } },
            clock = object : Clock { override fun now() = now },
        )
        app.navigateRoot(screen)
        return app
    }

    private fun shot(name: String, app: AppState, width: Int = 760, height: Int = 1300) {
        val files = Screenshots.render("shell_$name", widthDp = width, heightDp = height, density = 1.25f) { AppRoot(app) }
        assertTrue(files.all { it.length() > 0 })
    }

    private val registered = Profile(firstName = "Ada", lastName = "Lovelace", displayName = "Ada L.", placement = placement, country = "CH")
    private val doneFlags = LocalFlags(termsVersion = TERMS_VERSION, onboardingDone = true, passwordSet = true, lessonSections = mapOf("email-01" to 2))

    @Test fun language() = shot("language_light", app(Screen.Language, Profile(country = "CH")))
    @Test fun languageFrench() = shot("language_fr", app(Screen.Language, Profile(language = "fr", country = "FR")))
    @Test fun languageDarkCompact() = shot("language_dark_compact", app(Screen.Language, Profile(darkMode = true, country = "CH")), width = 380, height = 900)
    @Test fun email() = shot("email", app(Screen.Email), height = 900)
    @Test fun emailOffline() = shot("email_offline_compact", app(Screen.Email, online = false), width = 380, height = 1000)
    @Test fun code() = shot("code", app(Screen.Code("ada@mail.example")), height = 900)
    @Test fun codeCompact() = shot("code_compact", app(Screen.Code("ada@mail.example")), width = 380, height = 900)
    @Test fun name() = shot("name", app(Screen.Name, loggedIn = true), height = 900)
    @Test fun password() = shot("password", app(Screen.Password, loggedIn = true), height = 900)
    @Test fun passwordDark() = shot("password_dark", app(Screen.Password, Profile(darkMode = true), loggedIn = true), height = 900)
    @Test fun onboardingHereFor() = shot("onboarding_0", app(Screen.Onboarding(0), loggedIn = true), height = 900)
    @Test fun onboardingInterestsNgo() = shot("onboarding_4_ngo_compact", app(Screen.Onboarding(4), Profile(hereFor = "ngo"), loggedIn = true), width = 380, height = 1300)
    @Test fun onboardingPast() = shot("onboarding_5", app(Screen.Onboarding(5), Profile(pastExperience = "unsure_message"), loggedIn = true), height = 1000)
    @Test fun onboardingRating() = shot("onboarding_6_compact", app(Screen.Onboarding(6), loggedIn = true), width = 380, height = 800)
    @Test fun onboardingTextLarge() = shot("onboarding_7_large", app(Screen.Onboarding(7), Profile(largeText = true), loggedIn = true), height = 900)
    @Test fun terms() = shot("terms", app(Screen.Terms, loggedIn = true), height = 1200)
    @Test fun termsCompact() = shot("terms_compact", app(Screen.Terms, loggedIn = true), width = 380, height = 1500)

    @Test
    fun termsText() {
        Screenshots.render("shell_terms_text", widthDp = 600, heightDp = 3000, density = 1.25f, cropToContent = true) {
            TeaTheme(dark = false) {
                Column(androidx.compose.ui.Modifier.fillMaxWidth().background(TeaTheme.colors.paper).padding(TeaSpacing.s2)) {
                    TermsText(maxHeight = 3000)
                }
            }
        }
    }

    @Test
    fun suspiciousDialog() {
        Screenshots.render("shell_suspicious_dialog", widthDp = 600, heightDp = 2000, density = 1.25f, cropToContent = true) {
            TeaTheme(dark = false) {
                Column(androidx.compose.ui.Modifier.fillMaxWidth().background(TeaTheme.colors.paper).padding(TeaSpacing.s2)) {
                    TeaDialogCard("Got a suspicious email?", {}, confirmText = "Practise the four steps", onConfirm = {}) { SuspiciousBody() }
                }
            }
        }
    }

    @Test
    fun home() = shot(
        "home_caught",
        app(
            Screen.Home, registered, loggedIn = true, flags = doneFlags, field = FieldCache(clicked = true, status = "caught", revealToken = "tok"),
            api = FakeApi().apply { fieldEvents = listOf(fieldEvent("field_clicked")) },
        ),
        height = 1600,
    )

    @Test
    fun homeDark() = shot(
        "home_dark_dodged",
        app(
            Screen.Home, registered.copy(darkMode = true), loggedIn = true, flags = doneFlags.copy(lessonsDone = listOf("email-01")),
            api = FakeApi().apply { fieldEvents = listOf(fieldEvent("field_reported")) },
        ),
        height = 1600,
    )

    @Test
    fun homeGuestOfflineCompact() = shot(
        "home_guest_offline_compact",
        app(Screen.Home, Profile(placement = placement), online = false, flags = LocalFlags(termsVersion = TERMS_VERSION, guest = true)),
        width = 380,
        height = 1500,
    )

    @Test
    fun settings() = shot(
        "settings",
        app(Screen.Settings, registered, loggedIn = true, flags = doneFlags, api = FakeApi().apply { practice = SettingsResponse("allowed", pending = true) }),
        height = 2400,
    )

    @Test
    fun settingsGuestCompact() = shot("settings_guest_compact", app(Screen.Settings, Profile(placement = placement), flags = doneFlags), width = 380, height = 2600)

    @Test fun about() = shot("about", app(Screen.About, registered, loggedIn = true, flags = doneFlags), height = 2000)
    @Test fun aboutGuestDark() = shot("about_guest_dark", app(Screen.About, Profile(darkMode = true), flags = doneFlags), height = 1900)
    @Test fun leaderboard() = shot("leaderboard", app(Screen.Leaderboard, registered, loggedIn = true, flags = doneFlags), height = 1000)
    @Test fun leaderboardCompact() = shot("leaderboard_compact", app(Screen.Leaderboard, registered, loggedIn = true, flags = doneFlags), width = 380, height = 1000)
    @Test fun reveal() = shot("reveal", app(Screen.Reveal("AbCdEf123456789012345")), height = 1400)
    @Test fun revealCompactDark() = shot("reveal_compact_dark", app(Screen.Reveal("AbCdEf123456789012345"), Profile(darkMode = true)), width = 380, height = 1500)
    @Test fun revealMissing() = shot("reveal_missing", app(Screen.Reveal("missing")), height = 900)
}
