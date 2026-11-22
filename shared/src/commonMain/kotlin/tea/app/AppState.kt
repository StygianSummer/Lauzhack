package tea.app

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import tea.BuildInfo
import tea.content.ContentRepository
import tea.i18n.Lang
import tea.model.AboutEntry
import tea.model.VaultAccountsResponse
import tea.model.VaultChallengeResponse
import tea.model.VaultCreateResponse
import tea.model.VaultDebriefResponse
import tea.model.VaultLoginRequest
import tea.model.VaultLoginResponse
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.EventTypes
import tea.model.LeaderboardResponse
import tea.model.LoginResponse
import tea.model.MasteryState
import tea.model.PasswordAttemptResponse
import tea.model.Profile
import tea.model.ReadinessResponse
import tea.model.RevealResponse
import tea.model.SettingsResponse
import tea.model.SignalsRequest
import tea.model.Skill
import tea.model.StoredEvent
import tea.model.TERMS_VERSION
import tea.model.VerifyResponse
import tea.net.ApiClient
import tea.net.ApiResult
import tea.net.BackendUrl
import tea.net.SyncQueue
import tea.net.TeaApi
import tea.store.FieldCache
import tea.store.LocalFlags
import tea.store.LocalState
import tea.store.Session

/** Device facts read from the composition (they need Compose to know). */
data class DeviceSnapshot(val dark: Boolean, val fontScale: Float, val widthDp: Int)

/**
 * The app shell's state (plan §9.1): navigation, session, profile, learning state, sync and
 * connectivity. Implements [TeaApp] for the learning screens; the shell's own screens use the
 * extra functions below. Everything is persisted in [local] at once; uploads go through [queue].
 *
 * Create one with [AppState.create] (real platform) or the constructor (tests: in-memory store,
 * fake [TeaApi], test scope).
 */
class AppState(
    val platform: PlatformInfo,
    val local: LocalState,
    override val content: ContentRepository,
    private val scope: CoroutineScope,
    apiFactory: (baseUrl: () -> String, token: () -> String?) -> TeaApi,
    private val clock: Clock = Clock.System,
    debug: Boolean = BuildInfo.DEBUG,
    /** Delay before a queued change is uploaded (coalesces bursts). */
    private val flushDelayMs: Long = 300,
) : TeaApp {

    override val debug: Boolean = when (platform.queryParam("debug")) {
        "1", "true" -> true
        "0", "false" -> false
        else -> debug
    }

    // ---- backend URL (C5)

    private val _apiUrl = MutableStateFlow(
        if (platform.isWeb) BackendUrl.normalise(local.apiOverride) ?: platform.defaultApi
        else BackendUrl.resolveDesktop(platform.envApi, local, platform.defaultApi).url,
    )
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    // ---- session

    private val _session = MutableStateFlow(local.session)
    val session: StateFlow<Session?> = _session.asStateFlow()

    val api: TeaApi = apiFactory({ _apiUrl.value }, { _session.value?.token })

    private val _email = MutableStateFlow(local.session?.email)
    override val email: StateFlow<String?> = _email.asStateFlow()

    private val _loggedIn = MutableStateFlow(local.session != null)
    override val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /** The server refused our token (expired or revoked): Home offers to sign in again. */
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    override val online: StateFlow<Boolean> get() = api.online

    // ---- learner

    private val _profile = MutableStateFlow(initialProfile())
    override val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _lang = MutableStateFlow(_profile.value.language)
    override val lang: StateFlow<String> = _lang.asStateFlow()

    private val _flags = MutableStateFlow(local.flags)
    val flags: StateFlow<LocalFlags> = _flags.asStateFlow()

    private val _mastery = MutableStateFlow(local.mastery.associateBy { it.skill })
    override val mastery: StateFlow<Map<Skill, MasteryState>> = _mastery.asStateFlow()

    private val _xp = MutableStateFlow(local.xp)
    override val xp: StateFlow<Int> = _xp.asStateFlow()

    private val _field = MutableStateFlow(local.field)
    val field: StateFlow<FieldCache> = _field.asStateFlow()

    private val _fieldClicked = MutableStateFlow(local.field.clicked)
    override val fieldClicked: StateFlow<Boolean> = _fieldClicked.asStateFlow()

    val queue = SyncQueue(local, api, onProfileSynced = { server ->
        // The server derives email_provider from the verified address; keep everything else local.
        if (server.emailProvider != null && server.emailProvider != _profile.value.emailProvider) {
            setProfile(_profile.value.copy(emailProvider = server.emailProvider), sync = false)
        }
    })

    /** Items waiting for upload. */
    val pendingSync: StateFlow<Int> get() = queue.pending

    private var device: DeviceSnapshot? = null

    // ---- navigation

    private val _stack = MutableStateFlow(initialStack())
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()
    private val _screen = MutableStateFlow(_stack.value.last())
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val flushTrigger = Channel<Unit>(Channel.CONFLATED)

    init {
        platform.onHashChange { onHashChanged(it) }
        scope.launch {
            if (platform.isWeb) {
                val client = api as? ApiClient
                val resolved = BackendUrl.resolveWeb(platform.queryParam("api"), local) {
                    platform.configUrl?.let { client?.fetchConfigApi(it) }
                }
                _apiUrl.value = resolved.url
            }
            api.startMonitor(scope)
        }
        scope.launch {
            for (t in flushTrigger) {
                delay(flushDelayMs)
                flushNow()
            }
        }
        // Back online: upload the queue and refresh the practice-email status.
        scope.launch {
            api.online.drop(1).filter { it }.collect {
                requestFlush()
                refreshField()
            }
        }
        if (_session.value != null) {
            requestFlush()
            scope.launch { refreshField() }
        }
    }

    // =============================================================================================
    // TeaApp
    // =============================================================================================

    override fun updateProfile(transform: (Profile) -> Profile) = setProfile(transform(_profile.value), sync = true)

    override fun saveMastery(states: Collection<MasteryState>) {
        if (states.isEmpty()) return
        val merged = _mastery.value.toMutableMap()
        states.forEach { merged[it.skill] = it }
        _mastery.value = merged
        local.mastery = merged.values.toList()
        queue.addMastery(states)
        requestFlush()
    }

    override fun addXp(amount: Int) {
        if (amount == 0) return
        val v = (_xp.value + amount).coerceAtLeast(0)
        _xp.value = v
        local.xp = v
    }

    override fun recordAttempt(attempt: Attempt) {
        local.appendAttempt(attempt)
        queue.addAttempt(attempt)
        requestFlush()
    }

    override fun emit(type: String, payload: JsonObject) {
        if (type.startsWith(EventTypes.FIELD_PREFIX)) return // server-only events
        queue.addEvent(AppEvent(newId(), type, payload, now()))
        if (type == EventTypes.LESSON_COMPLETED) {
            val lessonId = (payload["lesson_id"] as? JsonPrimitive)?.contentOrNull
            if (lessonId != null) updateFlags { it.copy(lessonsDone = (it.lessonsDone + lessonId).distinct()) }
        }
        requestFlush()
    }

    override fun <T> load(key: String, serializer: KSerializer<T>): T? = local.loadBlob(key, serializer)

    override fun <T> save(key: String, serializer: KSerializer<T>, value: T?) = local.saveBlob(key, serializer, value)

    override suspend fun requestReadiness(lessonId: String): ReadinessResponse? {
        if (_session.value == null || !online.value) return null
        flushNow()
        return when (val r = api.readiness(lessonId)) {
            is ApiResult.Ok -> r.value.also { scope.launch { refreshField() } }
            is ApiResult.Err -> {
                if (r.status == 401) onUnauthorized()
                null
            }
            ApiResult.Offline -> null
        }
    }

    override fun now(): Instant = clock.now()

    override fun navigate(screen: Screen) {
        if (screen is Screen.Lesson) rememberLessonSection(screen)
        val s = _stack.value
        if (s.last() == screen) return
        setStack(s + screen)
    }

    override fun navigateRoot(screen: Screen) {
        if (screen is Screen.Lesson) rememberLessonSection(screen)
        setStack(listOf(screen))
    }

    override fun back() {
        val s = _stack.value
        if (s.size > 1) setStack(s.dropLast(1))
    }

    val canGoBack: Boolean get() = _stack.value.size > 1

    override fun openUrl(url: String) = platform.openUrl(url)

    // =============================================================================================
    // Shell: navigation helpers
    // =============================================================================================

    /** Called when the web URL hash changes (back/forward buttons, a typed or followed link). */
    fun onHashChanged(hash: String) {
        val target = Routes.parse(hash, local.pendingEmail) ?: return
        val s = _stack.value
        if (target == s.last()) return
        if (!allowedByLink(target)) {
            platform.hash = Routes.toHash(s.last())
            return
        }
        if (s.size > 1 && s[s.size - 2] == target) setStack(s.dropLast(1)) else navigate(target)
    }

    /** The screen to show after a successful sign-in or registration step (plan §9.2 order). */
    fun nextAfterSignIn(): Screen {
        val p = _profile.value
        val f = _flags.value
        return when {
            p.firstName.isNullOrBlank() -> Screen.Name
            // A guest (offline) has no server account, so there is no password to set.
            !f.passwordSet && !f.guest -> Screen.Password
            !f.onboardingDone -> Screen.Onboarding(0)
            f.termsVersion == null -> Screen.Terms
            // Registration ends on Home; the placement test is an optional card there.
            else -> Screen.Home
        }
    }

    /** The section of [lessonId] the learner last opened (for "Continue lesson 1"). */
    fun savedSection(lessonId: String): Int = _flags.value.lessonSections[lessonId] ?: 0

    fun lessonDone(lessonId: String): Boolean = lessonId in _flags.value.lessonsDone

    private fun setStack(s: List<Screen>) {
        _stack.value = s
        _screen.value = s.last()
    }

    private fun rememberLessonSection(screen: Screen.Lesson) {
        if (_flags.value.lessonSections[screen.id] != screen.section) {
            updateFlags { it.copy(lessonSections = it.lessonSections + (screen.id to screen.section)) }
        }
    }

    private fun hasStarted(): Boolean = _flags.value.termsVersion != null || _profile.value.placement != null

    private fun startScreen(): Screen = when {
        hasStarted() -> Screen.Home
        else -> Screen.Language
    }

    private fun allowedByLink(target: Screen): Boolean = when {
        target is Screen.Reveal || target == Screen.Gallery -> true
        Routes.isRegistration(target) -> !hasStarted()
        else -> hasStarted()
    }

    private fun initialStack(): List<Screen> {
        val start = startScreen()
        val deep = Routes.parse(platform.hash, local.pendingEmail)
        return if (deep != null && deep != start && allowedByLink(deep)) listOf(start, deep) else listOf(start)
    }

    // =============================================================================================
    // Shell: registration and sign-in (plan §9.2)
    // =============================================================================================

    /** `POST /auth/start`; remembers the address for the Code screen. */
    suspend fun startEmail(email: String): ApiResult<Unit> {
        val address = email.trim()
        val r = api.authStart(address, _lang.value)
        if (r is ApiResult.Ok) local.pendingEmail = address
        return r
    }

    /** `POST /auth/verify`; on success the session is stored and local data merged with the server's. */
    suspend fun verifyCode(email: String, code: String): ApiResult<VerifyResponse> {
        val r = api.authVerify(email.trim(), code)
        if (r is ApiResult.Ok) {
            signIn(Session(r.value.token, email.trim(), r.value.userId), passwordSet = r.value.hasPassword)
        }
        return r
    }

    /** "I already have an account": `POST /auth/login`. */
    suspend fun login(email: String, password: String): ApiResult<LoginResponse> {
        val r = api.login(email.trim(), password)
        if (r is ApiResult.Ok) signIn(Session(r.value.token, email.trim(), r.value.userId), passwordSet = true)
        return r
    }

    /** `POST /auth/password/attempt`. The password is never stored on the device. */
    suspend fun setPassword(password: String): ApiResult<PasswordAttemptResponse> {
        val r = api.passwordAttempt(password)
        if (r is ApiResult.Ok && r.value.accepted) updateFlags { it.copy(passwordSet = true) }
        if (r is ApiResult.Err && r.status == 401) onUnauthorized()
        return r
    }

    /** Offline: no code can be sent, but the address is kept so the Code screen can take the demo code. */
    fun startEmailOffline(email: String) {
        local.pendingEmail = email.trim()
    }

    /**
     * Offline sign-up with the demo code [OFFLINE_DEMO_CODE] (the same code the server accepts while no
     * SMTP password is set). It opens nothing on the server: the learner becomes a local guest, exactly
     * like "Continue without an account", and continues at the Name screen. Returns false for any other code.
     */
    fun verifyOffline(code: String): Boolean {
        if (code != OFFLINE_DEMO_CODE) return false
        updateFlags { it.copy(guest = true) }
        if (!local.hasProfile) setProfile(_profile.value, sync = false)
        return true
    }

    /** "Continue without an account" (offline): local guest, straight to the onboarding questions. */
    fun continueAsGuest() {
        updateFlags { it.copy(guest = true) }
        if (!local.hasProfile) setProfile(_profile.value, sync = false)
        navigate(if (_flags.value.onboardingDone) Screen.Terms else Screen.Onboarding(0))
    }

    fun skipOnboardingQuestion(id: String) =
        updateFlags { it.copy(onboardingSkipped = (it.onboardingSkipped + id).distinct()) }

    fun answeredOnboardingQuestion(id: String) =
        updateFlags { it.copy(onboardingSkipped = it.onboardingSkipped - id) }

    /** Emits `onboarding_completed {skipped}` once. */
    fun finishOnboarding() {
        if (!_flags.value.onboardingDone) {
            val skipped = _flags.value.onboardingSkipped
            emit(EventTypes.ONBOARDING_COMPLETED, buildJsonObject { put("skipped", JsonArray(skipped.map { JsonPrimitive(it) })) })
        }
        updateFlags { it.copy(onboardingDone = true) }
    }

    /**
     * Terms accepted: emits `terms_accepted {version}` and, when signed in and online, uploads it
     * at once (the server learns the acceptance from it), then posts the device settings once.
     * Accepting the terms does not switch practice emails on.
     */
    fun acceptTerms() {
        updateFlags { it.copy(termsVersion = TERMS_VERSION) }
        emit(EventTypes.TERMS_ACCEPTED, buildJsonObject { put("version", JsonPrimitive(TERMS_VERSION)) })
        scope.launch {
            flushNow()
            sendSignalsIfNeeded()
        }
    }

    fun updateDevice(snapshot: DeviceSnapshot) {
        device = snapshot
    }

    /** The device settings of plan §9.2.9 and nothing else. */
    fun signalsRequest(): SignalsRequest {
        val d = device
        val width = d?.widthDp ?: 800
        return SignalsRequest(
            platform = platform.name,
            json = buildJsonObject {
                platform.browserFamily?.let { put("browser_family", JsonPrimitive(it)) }
                put("device_class", JsonPrimitive(if (width < 600) "compact" else if (width < 840) "medium" else "expanded"))
                put("touch", JsonPrimitive(platform.pointer == "touch"))
                put("locale", JsonPrimitive(platform.locale))
                put("timezone", JsonPrimitive(platform.timezone))
                put("dark_mode", JsonPrimitive(d?.dark ?: false))
                put("reduced_motion", JsonPrimitive(platform.reducedMotion))
                put("font_scale", JsonPrimitive(((d?.fontScale ?: 1f) * 100).toInt() / 100.0))
            },
        )
    }

    /** `POST /me/signals` once per account, after the terms. */
    suspend fun sendSignalsIfNeeded(): Boolean {
        if (_session.value == null || _flags.value.termsVersion == null || _flags.value.signalsSent) return false
        val r = api.postSignals(signalsRequest())
        if (r is ApiResult.Ok) updateFlags { it.copy(signalsSent = true) }
        return r is ApiResult.Ok
    }

    private suspend fun signIn(session: Session, passwordSet: Boolean) {
        local.session = session
        local.pendingEmail = null
        _session.value = session
        _email.value = session.email
        _loggedIn.value = true
        _sessionExpired.value = false
        updateFlags { it.copy(passwordSet = passwordSet, guest = false, signalsSent = false) }
        // Merge what the server already knows (another device, or an earlier registration).
        val server = api.getProfile().valueOrNull
        if (server != null) {
            setProfile(mergeProfiles(_profile.value, server), sync = false)
            if (server.hereFor != null || server.placement != null) updateFlags { it.copy(onboardingDone = true) }
        }
        api.getMastery().valueOrNull?.let { remote ->
            val merged = _mastery.value.toMutableMap()
            remote.forEach { r -> if ((merged[r.skill]?.updatedAt ?: Instant.DISTANT_PAST) < r.updatedAt) merged[r.skill] = r }
            _mastery.value = merged
            local.mastery = merged.values.toList()
        }
        // Upload whatever was done as a guest, plus the merged profile.
        if (_mastery.value.isNotEmpty()) queue.addMastery(_mastery.value.values)
        queue.markProfileDirty()
        requestFlush()
        scope.launch { refreshField() }
    }

    // =============================================================================================
    // Shell: sync, field, settings, account
    // =============================================================================================

    fun requestFlush() {
        flushTrigger.trySend(Unit)
    }

    /** Uploads the queue now if signed in. */
    suspend fun flushNow(): SyncQueue.FlushResult? {
        if (_session.value == null) return null
        val r = queue.flush()
        if (r.unauthorized) onUnauthorized()
        return r
    }

    /** Re-reads the practice-email outcome from `GET /me/events?type=field_*`. */
    suspend fun refreshField() {
        if (_session.value == null) return
        when (val r = api.getEvents(EventTypes.FIELD_PREFIX + "*")) {
            is ApiResult.Ok -> setField(fieldCacheFrom(r.value, _field.value.revealToken))
            is ApiResult.Err -> if (r.status == 401) onUnauthorized()
            ApiResult.Offline -> Unit
        }
    }

    /** The Reveal screen opened [token] on this device: remember it so Home can link back. */
    fun rememberRevealToken(token: String) {
        if (_field.value.revealToken != token) setField(_field.value.copy(revealToken = token))
    }

    suspend fun reveal(token: String): ApiResult<RevealResponse> {
        val r = api.reveal(token)
        if (r is ApiResult.Ok) {
            rememberRevealToken(token)
            scope.launch { refreshField() }
        }
        return r
    }

    /** "Got a suspicious email?": `POST /me/field/report` when possible. The UI answers the same either way. */
    suspend fun reportSuspicious(): Boolean {
        if (_session.value == null) return false
        val ok = api.reportField() is ApiResult.Ok
        if (ok) refreshField()
        return ok
    }

    suspend fun practiceSettings(): ApiResult<SettingsResponse> =
        if (_session.value == null) ApiResult.Err("unauthorized", 401) else api.getSettings().also { checkAuth(it) }

    /** [value]: "allowed" | "pause" | "stop". */
    suspend fun setPracticeEmails(value: String): ApiResult<SettingsResponse> = api.putSettings(value).also { checkAuth(it) }

    suspend fun aboutRows(): ApiResult<List<AboutEntry>> = api.about().also { checkAuth(it) }

    // ---- the Vault drill (docs/password-drill.md) ----

    suspend fun vaultAccounts(): ApiResult<VaultAccountsResponse> = api.vaultAccounts().also { checkAuth(it) }

    /**
     * Sends one invented password to be graded. The string is handed straight to the client and not
     * kept in any state holder: the server answers with the verdict, never with the password.
     */
    suspend fun vaultCreate(siteId: String, username: String, password: String): ApiResult<VaultCreateResponse> =
        api.vaultCreate(siteId, username, password).also { checkAuth(it) }

    suspend fun vaultDebrief(): ApiResult<VaultDebriefResponse> = api.vaultDebrief().also { checkAuth(it) }

    suspend fun vaultChallenge(): ApiResult<VaultChallengeResponse> = api.vaultChallenge().also { checkAuth(it) }

    suspend fun vaultLogin(request: VaultLoginRequest): ApiResult<VaultLoginResponse> =
        api.vaultLogin(request).also { checkAuth(it) }

    /** The one tap after an attempt. It lands on that attempt's row and changes nothing else. */
    suspend fun vaultSelfReport(siteId: String, selfReport: String): ApiResult<Unit> =
        api.vaultSelfReport(siteId, selfReport).also { checkAuth(it) }

    suspend fun vaultReset(siteId: String): ApiResult<Unit> = api.vaultReset(siteId).also { checkAuth(it) }

    suspend fun vaultClear(): ApiResult<Unit> = api.vaultClear().also { checkAuth(it) }

    suspend fun leaderboard(period: String): ApiResult<LeaderboardResponse> = api.leaderboard(period)

    /** Settings: a backend URL typed by the learner (blank = back to the default order). */
    fun setApiUrl(raw: String?): Boolean {
        val url = BackendUrl.normalise(raw)
        if (!raw.isNullOrBlank() && url == null) return false
        local.apiOverride = url
        _apiUrl.value = if (platform.isWeb) url ?: platform.defaultApi
        else BackendUrl.resolveDesktop(platform.envApi, local, platform.defaultApi).url
        scope.launch { api.health() }
        return true
    }

    /** Pings the server now ("Try again" on the offline banner). */
    fun retryConnection() {
        scope.launch { api.health() }
    }

    /** Signs out: uploads what it can, revokes the session, clears this device, back to Language. */
    suspend fun logout() {
        if (_session.value != null) {
            flushNow()
            api.logout()
        }
        wipe()
    }

    /**
     * "Delete my account": `DELETE /me` (signed in) then clears the device. Guests: clears the device.
     * @return false when the server could not be reached (nothing was deleted).
     */
    suspend fun deleteAccount(): Boolean {
        if (_session.value != null) {
            when (val r = api.deleteMe()) {
                is ApiResult.Ok -> Unit
                is ApiResult.Err -> if (r.status != 401) return false
                ApiResult.Offline -> return false
            }
        }
        wipe()
        return true
    }

    private fun wipe() {
        val language = _lang.value
        local.clearAll()
        queue.clear()
        _session.value = null
        _email.value = null
        _loggedIn.value = false
        _sessionExpired.value = false
        _flags.value = LocalFlags()
        _profile.value = Profile(language = language)
        _mastery.value = emptyMap()
        _xp.value = 0
        _field.value = FieldCache()
        _fieldClicked.value = false
        navigateRoot(Screen.Language)
    }

    private fun checkAuth(r: ApiResult<*>) {
        if (r is ApiResult.Err && r.status == 401) onUnauthorized()
    }

    private fun onUnauthorized() {
        val s = _session.value ?: return
        local.session = null
        local.pendingEmail = s.email
        _session.value = null
        _loggedIn.value = false
        _sessionExpired.value = true
    }

    // =============================================================================================
    // internals
    // =============================================================================================

    private fun initialProfile(): Profile =
        if (local.hasProfile) local.profile else Profile(language = Lang.fromLocale(platform.locale), country = defaultCountry())

    /** Country preselection from the locale region, else the time zone (plan §9.2.1). */
    fun defaultCountry(): String? {
        val region = Lang.regionOf(platform.locale)
        if (region != null) return if (region in COUNTRIES) region else "OTHER"
        return when (platform.timezone) {
            "Europe/Zurich" -> "CH"
            "Europe/Paris" -> "FR"
            "Europe/Madrid", "Atlantic/Canary" -> "ES"
            "Europe/Berlin" -> "DE"
            "Europe/Rome" -> "IT"
            "Europe/London" -> "GB"
            "Asia/Kolkata", "Asia/Calcutta" -> "IN"
            "Asia/Karachi" -> "PK"
            else -> null
        }
    }

    private fun setProfile(p: Profile, sync: Boolean) {
        _profile.value = p
        _lang.value = Lang.fromLocale(p.language)
        local.profile = p
        if (sync) {
            // Guests too: the profile is uploaded once they register.
            queue.markProfileDirty()
            if (_session.value != null) requestFlush()
        }
    }

    private fun updateFlags(transform: (LocalFlags) -> LocalFlags) {
        val f = transform(_flags.value)
        _flags.value = f
        local.flags = f
    }

    private fun setField(f: FieldCache) {
        _field.value = f
        _fieldClicked.value = f.clicked
        local.field = f
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): String = Uuid.random().toString()

    companion object {
        /** Accepted by [verifyOffline]; matches the server's DEV_VERIFY_CODE default (decisions.md). */
        const val OFFLINE_DEMO_CODE = "426769"

        /** Country list of plan §9.2.1. */
        val COUNTRIES = listOf("CH", "FR", "ES", "DE", "IT", "GB", "IN", "PK", "OTHER")

        /** The real app: platform store, Ktor client. */
        fun create(scope: CoroutineScope, platform: PlatformInfo = platformInfo()): AppState {
            val store = tea.store.platformKeyValueStore()
            return AppState(
                platform = platform,
                local = LocalState(store),
                content = ContentRepository(),
                scope = scope,
                apiFactory = { base, token -> ApiClient(base, token) },
            )
        }

        /** Field status from server events (newest first): see [FieldCache]. */
        fun fieldCacheFrom(events: List<StoredEvent>, revealToken: String?): FieldCache {
            val clicked = events.any { it.type == EventTypes.FIELD_CLICKED }
            val newest = events.maxByOrNull { it.createdAt } ?: return FieldCache(clicked, "none", revealToken)
            val sendId = newest.payload["send_id"]?.jsonPrimitive?.contentOrNull
            val ofSend = if (sendId == null) listOf(newest) else events.filter { it.payload["send_id"]?.jsonPrimitive?.contentOrNull == sendId }
            val types = ofSend.map { it.type }.toSet()
            val status = when {
                EventTypes.FIELD_CLICKED in types -> "caught"
                EventTypes.FIELD_REPORTED in types -> "dodged"
                EventTypes.FIELD_CANCELLED in types -> "none"
                EventTypes.FIELD_SENT in types -> "sent"
                EventTypes.FIELD_SCHEDULED in types -> "pending"
                else -> "none"
            }
            return FieldCache(clicked, status, revealToken)
        }

        /** Local answers win; the server fills what this device does not know. */
        fun mergeProfiles(local: Profile, server: Profile): Profile = local.copy(
            country = local.country ?: server.country,
            firstName = local.firstName ?: server.firstName,
            lastName = local.lastName ?: server.lastName,
            displayName = local.displayName ?: server.displayName,
            emailProvider = server.emailProvider ?: local.emailProvider,
            hereFor = local.hereFor ?: server.hereFor,
            ageRange = local.ageRange ?: server.ageRange,
            channels = local.channels.ifEmpty { server.channels },
            emailApp = local.emailApp ?: server.emailApp,
            interests = local.interests.ifEmpty { server.interests },
            pastExperience = local.pastExperience ?: server.pastExperience,
            pastExperienceText = local.pastExperienceText ?: server.pastExperienceText,
            selfRating = local.selfRating ?: server.selfRating,
            placement = local.placement ?: server.placement,
            statusRating = local.statusRating ?: server.statusRating,
        )
    }
}
