package tea.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tea.model.AboutEntry
import tea.model.ApiError
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.AttemptsResponse
import tea.model.HealthResponse
import tea.model.LeaderboardResponse
import tea.model.LoginRequest
import tea.model.LoginResponse
import tea.model.MasteryState
import tea.model.PasswordAttemptRequest
import tea.model.PasswordAttemptResponse
import tea.model.Profile
import tea.model.ReadinessRequest
import tea.model.ReadinessResponse
import tea.model.GotchaResponse
import tea.model.RevealResponse
import tea.model.SettingsRequest
import tea.model.SettingsResponse
import tea.model.SignalsRequest
import tea.model.StartRequest
import tea.model.StoredEvent
import tea.model.TeaJson
import tea.model.VerifyRequest
import tea.model.VerifyResponse

/** The platform HTTP engine (OkHttp on desktop, fetch on web). */
internal expect fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * Ktor client for the ThisIsTea server (plan §10.3, §12).
 *
 * - [baseUrl] is read on every call, so a backend change in Settings applies at once.
 * - [token] is the session token or null (then no Authorization header).
 * - [online] turns false when a call gets no answer and true on any answer. [startMonitor] pings
 *   `/health` at start and every 30 s while offline (every 2 min while online).
 * - Short timeouts: 10 s per request, 4 s for `/health`.
 */
class ApiClient(
    private val baseUrl: () -> String,
    private val token: () -> String?,
    private val http: HttpClient = defaultHttpClient(),
    private val onlineEveryMs: Long = 120_000,
    private val offlineEveryMs: Long = 30_000,
) : TeaApi {
    private val _online = MutableStateFlow(true)
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(null)

    /** Version reported by `/health`, for the debug footer. */
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    private var monitor: Job? = null

    override fun startMonitor(scope: CoroutineScope) {
        monitor?.cancel()
        monitor = scope.launch {
            while (isActive) {
                health()
                delay(if (_online.value) onlineEveryMs else offlineEveryMs)
            }
        }
    }

    // ---- endpoints

    override suspend fun health(): ApiResult<HealthResponse> {
        val r = get<HealthResponse>("/health", timeoutMs = 4_000)
        if (r is ApiResult.Ok) _serverVersion.value = r.value.version
        return r
    }

    override suspend fun authStart(email: String, language: String): ApiResult<Unit> =
        sendUnit(HttpMethod.Post, "/auth/start", StartRequest(email, language))

    override suspend fun authVerify(email: String, code: String): ApiResult<VerifyResponse> =
        send(HttpMethod.Post, "/auth/verify", VerifyRequest(email, code))

    override suspend fun passwordAttempt(password: String): ApiResult<PasswordAttemptResponse> =
        send(HttpMethod.Post, "/auth/password/attempt", PasswordAttemptRequest(password))

    override suspend fun login(email: String, password: String): ApiResult<LoginResponse> =
        send(HttpMethod.Post, "/auth/login", LoginRequest(email, password))

    override suspend fun logout(): ApiResult<Unit> = sendUnit(HttpMethod.Post, "/auth/logout")

    override suspend fun getProfile(): ApiResult<Profile> = get("/me/profile")

    override suspend fun putProfile(profile: Profile): ApiResult<Profile> = send(HttpMethod.Put, "/me/profile", profile)

    override suspend fun postSignals(request: SignalsRequest): ApiResult<Unit> = sendUnit(HttpMethod.Post, "/me/signals", request)

    override suspend fun about(): ApiResult<List<AboutEntry>> = get("/me/about")

    override suspend fun deleteMe(): ApiResult<Unit> = sendUnit(HttpMethod.Delete, "/me")

    override suspend fun postAttempts(attempts: List<Attempt>): ApiResult<AttemptsResponse> =
        send(HttpMethod.Post, "/me/attempts", attempts)

    override suspend fun getMastery(): ApiResult<List<MasteryState>> = get("/me/mastery")

    override suspend fun putMastery(states: List<MasteryState>): ApiResult<List<MasteryState>> =
        send(HttpMethod.Put, "/me/mastery", states)

    override suspend fun postEvents(events: List<AppEvent>): ApiResult<Unit> = sendUnit(HttpMethod.Post, "/me/events", events)

    override suspend fun getEvents(type: String): ApiResult<List<StoredEvent>> =
        get("/me/events", query = mapOf("type" to type))

    override suspend fun getSettings(): ApiResult<SettingsResponse> = get("/me/settings")

    override suspend fun putSettings(practiceEmails: String): ApiResult<SettingsResponse> =
        send(HttpMethod.Put, "/me/settings", SettingsRequest(practiceEmails))

    override suspend fun leaderboard(period: String): ApiResult<LeaderboardResponse> =
        get("/leaderboard", query = mapOf("period" to period))

    override suspend fun readiness(lessonId: String): ApiResult<ReadinessResponse> =
        send(HttpMethod.Post, "/me/readiness", ReadinessRequest(lessonId))

    override suspend fun reportField(): ApiResult<Unit> = sendUnit(HttpMethod.Post, "/me/field/report")

    override suspend fun reveal(token: String): ApiResult<RevealResponse> =
        get("/field/reveal/" + token.filter { it.isLetterOrDigit() || it == '-' || it == '_' })

    override suspend fun gotcha(sendId: String): ApiResult<GotchaResponse> =
        get("/me/field/sends/" + sendId.filter { it.isLetterOrDigit() || it == '-' })

    /** `{ "api": "..." }` from the web build's `config.json` (absolute [url]); null if missing. */
    suspend fun fetchConfigApi(url: String): String? = try {
        val response = http.request(url) { method = HttpMethod.Get; timeout { requestTimeoutMillis = 3_000 } }
        if (!response.status.isSuccess()) null
        else (TeaJson.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.get("api")
            ?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        if (currentCoroutineContext().isActive) null else throw e
    } catch (e: Throwable) {
        null
    }

    fun close() = http.close()

    // ---- plumbing

    private suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        timeoutMs: Long? = null,
    ): ApiResult<T> = exchange(HttpMethod.Get, path, query, timeoutMs, null) { it.body<T>() }

    private suspend inline fun <reified B, reified T> send(method: HttpMethod, path: String, body: B): ApiResult<T> =
        exchange(method, path, emptyMap(), null, { setBody(body) }) { it.body<T>() }

    private suspend inline fun <reified B> sendUnit(method: HttpMethod, path: String, body: B): ApiResult<Unit> =
        exchange(method, path, emptyMap(), null, { setBody(body) }) { }

    private suspend fun sendUnit(method: HttpMethod, path: String): ApiResult<Unit> =
        exchange(method, path, emptyMap(), null, null) { }

    private suspend fun <T> exchange(
        method: HttpMethod,
        path: String,
        query: Map<String, String>,
        timeoutMs: Long?,
        body: (HttpRequestBuilder.() -> Unit)?,
        parse: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        val response = try {
            http.request(baseUrl().trimEnd('/') + path) {
                this.method = method
                query.forEach { (k, v) -> url.parameters.append(k, v) }
                token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                if (timeoutMs != null) timeout { requestTimeoutMillis = timeoutMs }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    body()
                }
            }
        } catch (e: CancellationException) {
            // A request timeout may surface as a cancellation; only a real cancellation propagates.
            if (currentCoroutineContext().isActive) return offline() else throw e
        } catch (e: Throwable) {
            return offline()
        }
        _online.value = true
        if (response.status.isSuccess()) {
            return try {
                ApiResult.Ok(parse(response))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ApiResult.Err("bad_response", response.status.value, e.message.orEmpty())
            }
        }
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val err = runCatching { TeaJson.decodeFromString(ApiError.serializer(), text) }.getOrNull()
        return ApiResult.Err(err?.error ?: "http_${response.status.value}", response.status.value, err?.message.orEmpty())
    }

    private fun offline(): ApiResult<Nothing> {
        _online.value = false
        return ApiResult.Offline
    }

    companion object {
        fun defaultHttpClient(): HttpClient = createHttpClient {
            expectSuccess = false
            install(ContentNegotiation) { json(TeaJson) }
            install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        }
    }
}
