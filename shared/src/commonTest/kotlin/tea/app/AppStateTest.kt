package tea.app

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import tea.content.ContentRepository
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.Context
import tea.model.EventTypes
import tea.model.MasteryState
import tea.model.PlacementResult
import tea.model.Profile
import tea.model.Skill
import tea.model.StepKey
import tea.model.StoredEvent
import tea.model.TeaJson
import tea.model.TERMS_VERSION
import tea.net.ApiResult
import tea.net.FakeApi
import tea.store.InMemoryKeyValueStore
import tea.store.FieldCache
import tea.store.LocalFlags
import tea.store.LocalState
import tea.store.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppStateTest {
    private val t0 = Instant.parse("2026-11-21T10:00:00Z")
    private val clock = object : Clock {
        override fun now() = t0
    }
    private val placement = PlacementResult(
        level = 2, stepP = StepKey.entries.associateWith { 0.5 }, hitRate = 0.8, falseAlarmRate = 0.1,
        calibration = 0.2, overconfident = false, totalMs = 180_000, strength = StepKey.WHO, focus = StepKey.VERIFY,
    )

    private class Env(val app: AppState, val api: FakeApi, val store: InMemoryKeyValueStore, val platform: FakePlatformInfo)

    private fun TestScope.env(
        store: InMemoryKeyValueStore = InMemoryKeyValueStore(),
        platform: FakePlatformInfo = FakePlatformInfo(),
        api: FakeApi = FakeApi(),
    ): Env {
        val app = AppState(
            platform = platform,
            local = LocalState(store),
            content = ContentRepository(loadBytes = { error("no content in tests") }),
            scope = backgroundScope,
            apiFactory = { _, token -> api.also { it.token = token } },
            clock = clock,
            debug = true,
        )
        return Env(app, api, store, platform)
    }

    /** Lets the app's background work (debounced uploads) run: advanceUntilIdle ignores backgroundScope. */
    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    private fun attempt(id: String) = Attempt(id, "email-x", Context.PRACTICE, AttemptAnswers(), true, ms = 1, createdAt = t0)

    @Test
    fun freshStartIsLanguageWithLocaleDefaults() = runTest {
        val e = env(platform = FakePlatformInfo(locale = "fr-CH"))
        assertEquals(Screen.Language, e.app.screen.value)
        assertEquals("fr", e.app.lang.value)
        assertEquals("CH", e.app.profile.value.country)
        assertFalse(e.app.loggedIn.value)
    }

    @Test
    fun countryFromTimezoneWhenLocaleHasNoRegion() = runTest {
        val e = env(platform = FakePlatformInfo(locale = "es", timezone = "Europe/Madrid"))
        assertEquals("ES", e.app.profile.value.country)
        assertEquals("es", e.app.lang.value)
    }

    @Test
    fun startScreenRule() = runTest {
        val withPlacement = InMemoryKeyValueStore()
        LocalState(withPlacement).profile = Profile(placement = placement)
        assertEquals(Screen.Home, env(store = withPlacement).app.screen.value)

        val termsOnly = InMemoryKeyValueStore()
        LocalState(termsOnly).flags = LocalFlags(termsVersion = TERMS_VERSION)
        assertEquals(Screen.PlacementIntro, env(store = termsOnly).app.screen.value)
    }

    @Test
    fun backStack() = runTest {
        val app = env().app
        app.navigate(Screen.Email)
        app.navigate(Screen.Email) // same screen twice: ignored
        app.navigate(Screen.Code("a@b.example"))
        assertEquals(3, app.stack.value.size)
        app.back()
        assertEquals(Screen.Email, app.screen.value)
        app.navigateRoot(Screen.Home)
        assertEquals(listOf<Screen>(Screen.Home), app.stack.value)
        app.back()
        assertEquals(Screen.Home, app.screen.value)
        app.navigate(Screen.Lesson("email-01", 3))
        assertEquals(3, app.savedSection("email-01"))
    }

    @Test
    fun revealDeepLinkOpensWithoutAccount() = runTest {
        val platform = FakePlatformInfo().apply { hash = "/reveal/AbCdEf123456789012345" }
        val app = env(platform = platform).app
        assertEquals(Screen.Reveal("AbCdEf123456789012345"), app.screen.value)
        assertEquals(Screen.Language, app.stack.value.first())
        val r = app.reveal("AbCdEf123456789012345")
        assertTrue(r is ApiResult.Ok)
        assertEquals("AbCdEf123456789012345", app.field.value.revealToken)
    }

    @Test
    fun pendingGotchasComeFromClicksAndClearWhenAnswered() = runTest {
        val events = listOf(
            StoredEvent(
                EventTypes.FIELD_CLICKED,
                buildJsonObject {
                    put("send_id", JsonPrimitive("send-1"))
                    put("lesson_id", JsonPrimitive("email-01"))
                },
                t0,
            ),
            StoredEvent(
                EventTypes.FIELD_CLICKED,
                buildJsonObject { put("send_id", JsonPrimitive("old-send")) },
                t0,
            ),
        )
        val pending = AppState.fieldCacheFrom(events, null)
        assertEquals(mapOf("email-01" to "old-send"), pending.pendingGotchas)
        assertTrue(
            AppState.fieldCacheFrom(
                events + StoredEvent(
                    EventTypes.FIELD_GOTCHA,
                    buildJsonObject { put("send_id", JsonPrimitive("send-1")) },
                    t0 + 1.seconds,
                ),
                null,
            ).pendingGotchas["email-01"] == "old-send",
        )
        assertFalse(
            "send-1" in AppState.fieldCacheFrom(events, null, resolvedGotchaSendIds = setOf("send-1"))
                .pendingGotchas.values,
        )
        val oldCache = TeaJson.decodeFromString(FieldCache.serializer(), """{"clicked":true,"status":"caught"}""")
        assertTrue(oldCache.pendingGotchas.isEmpty())
    }

    @Test
    fun authenticatedLessonsInterceptAndContinueOnce() = runTest {
        val store = InMemoryKeyValueStore()
        LocalState(store).apply {
            session = Session("session", "learner@example.test")
            profile = Profile(placement = placement)
            flags = LocalFlags(termsVersion = TERMS_VERSION)
        }
        val api = FakeApi().apply {
            fieldEvents = listOf(
                StoredEvent(
                    EventTypes.FIELD_CLICKED,
                    buildJsonObject {
                        put("send_id", JsonPrimitive("send-1"))
                        put("lesson_id", JsonPrimitive("email-01"))
                    },
                    t0,
                ),
            )
        }
        val e = env(store = store, api = api)
        runCurrent()
        assertEquals(mapOf("email-01" to "send-1"), e.app.field.value.pendingGotchas)
        e.app.navigate(Screen.Lesson("email-01", 2))
        assertEquals(Screen.Gotcha("send-1", "email-01"), e.app.screen.value)

        e.app.finishGotcha("send-1", "email-01", answer = "yes")
        assertEquals(Screen.Lesson("email-01", 0), e.app.screen.value)
        assertFalse(Screen.Gotcha("send-1", "email-01") in e.app.stack.value)
        assertTrue(e.app.field.value.pendingGotchas.isEmpty())
        assertTrue("send-1" in e.app.field.value.resolvedGotchaSendIds)
        settle()
        assertEquals(
            "yes",
            (api.events.values.single { it.type == EventTypes.FIELD_GOTCHA }.payload["answer"] as JsonPrimitive).content,
        )

        e.app.navigate(Screen.Home)
        e.app.navigate(Screen.Lesson("email-01", 0))
        assertEquals(Screen.Lesson("email-01", 0), e.app.screen.value)
    }

    @Test
    fun guestsKeepLessonNavigationAndOfflineGotchaCompletionStaysResolved() = runTest {
        val guestStore = InMemoryKeyValueStore()
        LocalState(guestStore).apply {
            profile = Profile(placement = placement)
            field = FieldCache(pendingGotchas = mapOf("email-01" to "send-1"))
        }
        val guest = env(store = guestStore).app
        guest.navigate(Screen.Lesson("email-01"))
        assertEquals(Screen.Lesson("email-01"), guest.screen.value)

        val store = InMemoryKeyValueStore()
        LocalState(store).apply {
            session = Session("session", "learner@example.test")
            profile = Profile(placement = placement)
            flags = LocalFlags(termsVersion = TERMS_VERSION)
            field = FieldCache(pendingGotchas = mapOf("email-01" to "send-1"))
        }
        val api = FakeApi().apply { onlineFlow.value = false }
        val app = env(store = store, api = api).app
        app.finishGotcha("send-1", "email-01", answer = "no", reason = "verify")
        assertTrue(LocalState(store).field.pendingGotchas.isEmpty())
        assertTrue("send-1" in LocalState(store).field.resolvedGotchaSendIds)
        app.navigate(Screen.Lesson("email-01"))
        assertEquals(Screen.Lesson("email-01", 0), app.screen.value)
    }

    @Test
    fun registrationLinksBlockedAfterTerms() = runTest {
        val store = InMemoryKeyValueStore()
        LocalState(store).apply {
            profile = Profile(placement = placement)
            flags = LocalFlags(termsVersion = TERMS_VERSION)
        }
        val platform = FakePlatformInfo().apply { hash = "/terms" }
        val app = env(store = store, platform = platform).app
        assertEquals(listOf<Screen>(Screen.Home), app.stack.value)
        platform.userSetsHash("/password")
        assertEquals(Screen.Home, app.screen.value)
        assertEquals("/home", platform.hash)
        platform.userSetsHash("/gallery")
        assertEquals(Screen.Gallery, app.screen.value)
    }

    @Test
    fun browserBackPopsTheStack() = runTest {
        val store = InMemoryKeyValueStore()
        LocalState(store).profile = Profile(placement = placement)
        val platform = FakePlatformInfo()
        val app = env(store = store, platform = platform).app
        app.navigate(Screen.Settings)
        app.navigate(Screen.About)
        platform.userSetsHash("/settings")
        assertEquals(listOf(Screen.Home, Screen.Settings), app.stack.value)
        platform.userSetsHash("/leaderboard")
        assertEquals(listOf(Screen.Home, Screen.Settings, Screen.Leaderboard), app.stack.value)
    }

    @Test
    fun registrationFlowEndToEnd() = runTest {
        val e = env()
        val app = e.app
        assertTrue(app.startEmail(" ada@mail.example ") is ApiResult.Ok)
        assertEquals("ada@mail.example", app.local.pendingEmail)
        assertTrue(app.verifyCode("ada@mail.example", "000000") is ApiResult.Err)
        assertTrue(app.verifyCode("ada@mail.example", "123456") is ApiResult.Ok)
        assertTrue(app.loggedIn.value)
        assertEquals("ada@mail.example", app.email.value)
        assertNull(app.local.pendingEmail)
        assertEquals(Screen.Name, app.nextAfterSignIn())

        app.updateProfile { it.copy(firstName = "Ada", displayName = "Ada L.") }
        assertEquals(Screen.Password, app.nextAfterSignIn())
        val short = app.setPassword("short")
        assertEquals("too_short", (short as ApiResult.Ok).value.reason)
        assertEquals(Screen.Password, app.nextAfterSignIn())
        app.setPassword("a long enough passphrase")
        assertEquals(Screen.Onboarding(0), app.nextAfterSignIn())

        app.skipOnboardingQuestion("age_range")
        app.updateProfile { it.copy(hereFor = "ngo") }
        app.finishOnboarding()
        assertEquals(Screen.Terms, app.nextAfterSignIn())

        app.updateDevice(DeviceSnapshot(dark = true, fontScale = 1.25f, widthDp = 390))
        app.acceptTerms()
        settle()
        assertEquals(Screen.PlacementIntro, app.nextAfterSignIn())

        val types = e.api.events.values.map { it.type }
        assertEquals(listOf(EventTypes.ONBOARDING_COMPLETED, EventTypes.TERMS_ACCEPTED), types)
        val skipped = e.api.events.values.first().payload["skipped"].toString()
        assertEquals("[\"age_range\"]", skipped)
        assertEquals("Ada", e.api.profile?.firstName)
        assertEquals("ngo", e.api.profile?.hereFor)
        // device settings: posted once, only the keys of plan §9.2.9
        assertEquals(1, e.api.signals.size)
        val keys = e.api.signals.single().json.keys
        val allowed = setOf("browser_family", "device_class", "touch", "locale", "timezone", "dark_mode", "reduced_motion", "font_scale")
        assertTrue(allowed.containsAll(keys), "unexpected keys: ${keys - allowed}")
        assertEquals(JsonPrimitive("compact"), e.api.signals.single().json["device_class"])
        assertFalse(app.sendSignalsIfNeeded()) // not twice
        assertEquals(0, app.pendingSync.value)
    }

    @Test
    fun guestProgressIsUploadedAfterRegistering() = runTest {
        val api = FakeApi().apply { onlineFlow.value = false }
        val e = env(api = api)
        val app = e.app
        app.continueAsGuest()
        assertEquals(Screen.Onboarding(0), app.screen.value)
        app.finishOnboarding()
        app.acceptTerms()
        app.emit(EventTypes.LESSON_STARTED, buildJsonObject { put("lesson_id", JsonPrimitive("email-01")) })
        app.recordAttempt(attempt("a1"))
        app.saveMastery(listOf(MasteryState(Skill.WHO, 0.7, updatedAt = t0)))
        app.addXp(15)
        settle()
        assertTrue(api.events.isEmpty())
        assertEquals(1, app.local.attempts.size)
        assertEquals(15, app.xp.value)

        api.onlineFlow.value = true
        settle()
        assertTrue(api.events.isEmpty()) // still a guest: nothing leaves the device

        app.startEmail("guest@mail.example")
        app.verifyCode("guest@mail.example", "123456")
        settle()
        assertEquals(
            listOf(EventTypes.ONBOARDING_COMPLETED, EventTypes.TERMS_ACCEPTED, EventTypes.LESSON_STARTED),
            api.events.values.map { it.type },
        )
        assertEquals(setOf("a1"), api.attempts.keys)
        assertEquals(0.7, api.mastery[Skill.WHO]?.p)
        assertEquals(0, app.pendingSync.value)
        assertFalse(app.flags.value.guest)
    }

    @Test
    fun unauthorizedKeepsDataAndAsksToSignInAgain() = runTest {
        val store = InMemoryKeyValueStore()
        LocalState(store).session = Session("tok", "ada@mail.example", "u1")
        val e = env(store = store)
        settle()
        e.api.rejectToken = true
        e.app.emit(EventTypes.LESSON_STARTED)
        settle()
        assertTrue(e.app.sessionExpired.value)
        assertFalse(e.app.loggedIn.value)
        assertEquals(1, e.app.pendingSync.value)
        assertEquals("ada@mail.example", e.app.local.pendingEmail)
    }

    @Test
    fun deleteAccountWipesTheDevice() = runTest {
        val store = InMemoryKeyValueStore()
        LocalState(store).apply {
            session = Session("tok", "ada@mail.example", "u1")
            profile = Profile(firstName = "Ada", placement = placement, language = "fr")
            apiOverride = "https://api.example"
        }
        val e = env(store = store)
        assertEquals(Screen.Home, e.app.screen.value)
        e.api.onlineFlow.value = false
        assertFalse(e.app.deleteAccount()) // offline: nothing deleted
        assertTrue(e.app.loggedIn.value)
        e.api.onlineFlow.value = true
        assertTrue(e.app.deleteAccount())
        assertTrue(e.api.deleted)
        assertEquals(Screen.Language, e.app.screen.value)
        assertFalse(e.app.loggedIn.value)
        assertEquals("fr", e.app.lang.value) // keep the language the person reads
        assertEquals(setOf("tea.api"), store.keys())
    }

    @Test
    fun fieldEventsBecomeDebriefStatus() {
        fun ev(type: String, send: String, minute: Int) = StoredEvent(
            type, buildJsonObject { put("send_id", JsonPrimitive(send)); put("template_id", JsonPrimitive("practice-email-01")) },
            Instant.parse("2026-11-21T10:${minute.toString().padStart(2, '0')}:00Z"),
        )
        assertEquals("none", AppState.fieldCacheFrom(emptyList(), null).status)
        val caught = AppState.fieldCacheFrom(listOf(ev("field_clicked", "s1", 3), ev("field_sent", "s1", 2), ev("field_scheduled", "s1", 1)), "tok")
        assertEquals("caught", caught.status)
        assertTrue(caught.clicked)
        assertEquals("tok", caught.revealToken)
        val dodged = AppState.fieldCacheFrom(listOf(ev("field_reported", "s1", 3), ev("field_sent", "s1", 2)), null)
        assertEquals("dodged", dodged.status)
        assertFalse(dodged.clicked)
        val pending = AppState.fieldCacheFrom(listOf(ev("field_scheduled", "s2", 9), ev("field_clicked", "s1", 3)), null)
        assertEquals("pending", pending.status)
        assertTrue(pending.clicked) // an earlier send was clicked
    }

    @Test
    fun eventsAndFlagsFromTheLearningScreens() = runTest {
        val e = env()
        e.app.emit("field_clicked") // clients never emit field_* events
        assertEquals(0, e.app.pendingSync.value)
        e.app.emit(EventTypes.LESSON_COMPLETED, buildJsonObject { put("lesson_id", JsonPrimitive("email-01")) })
        assertTrue(e.app.lessonDone("email-01"))
        e.app.save("practice.session", ListSerializer(Int.serializer()), listOf(1, 2))
        assertEquals(listOf(1, 2), e.app.load("practice.session", ListSerializer(Int.serializer())))
    }

    @Test
    fun readinessNeedsSessionAndConnection() = runTest {
        val e = env()
        assertNull(e.app.requestReadiness("email-01"))
        e.app.startEmail("ada@mail.example")
        e.app.verifyCode("ada@mail.example", "123456")
        e.app.emit(EventTypes.PRACTICE_EMAIL_OPTED_IN, buildJsonObject { put("lesson_id", JsonPrimitive("email-01")) })
        val r = e.app.requestReadiness("email-01")
        assertEquals(true, r?.scheduled)
        // the opt-in event was uploaded before the readiness call
        val calls = e.api.calls
        assertTrue(calls.indexOf("events") in 0 until calls.indexOf("readiness"))
        e.api.onlineFlow.value = false
        assertNull(e.app.requestReadiness("email-01"))
    }

    @Test
    fun logoutClearsTheDevice() = runTest {
        val e = env()
        e.app.startEmail("ada@mail.example")
        e.app.verifyCode("ada@mail.example", "123456")
        e.app.recordAttempt(attempt("a1"))
        e.app.logout()
        assertTrue("a1" in e.api.attempts) // uploaded before signing out
        assertTrue("logout" in e.api.calls)
        assertFalse(e.app.loggedIn.value)
        assertEquals(Screen.Language, e.app.screen.value)
        assertTrue(e.app.local.attempts.isEmpty())
    }

    @Test
    fun profileMergeKeepsLocalAnswers() {
        val local = Profile(firstName = "Ada", hereFor = "myself", language = "fr")
        val server = Profile(firstName = "Server", lastName = "Lovelace", emailProvider = "gmail", placement = placement)
        val merged = AppState.mergeProfiles(local, server)
        assertEquals("Ada", merged.firstName)
        assertEquals("Lovelace", merged.lastName)
        assertEquals("gmail", merged.emailProvider)
        assertEquals(placement, merged.placement)
        assertEquals("fr", merged.language)
    }

    @Test
    fun debugFlagFromQuery() = runTest {
        val app = env(platform = FakePlatformInfo(query = mapOf("debug" to "0"))).app
        assertFalse(app.debug)
    }

    @Test
    fun loginWithPassword() = runTest {
        val e = env()
        assertTrue(e.app.login("ada@mail.example", "wrong") is ApiResult.Err)
        assertTrue(e.app.login("ada@mail.example", "right-password") is ApiResult.Ok)
        assertTrue(e.app.flags.value.passwordSet)
        assertEquals(Screen.Name, e.app.nextAfterSignIn())
    }
}
