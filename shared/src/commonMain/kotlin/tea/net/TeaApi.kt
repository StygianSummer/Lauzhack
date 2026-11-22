package tea.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

import tea.model.AboutEntry
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.AttemptsResponse
import tea.model.HealthResponse
import tea.model.LeaderboardResponse
import tea.model.LoginResponse
import tea.model.MasteryState
import tea.model.PasswordAttemptResponse
import tea.model.Profile
import tea.model.ReadinessResponse
import tea.model.GotchaResponse
import tea.model.RevealResponse
import tea.model.SettingsResponse
import tea.model.SignalsRequest
import tea.model.StoredEvent
import tea.model.VerifyResponse

/** Result of one API call. Never throws (except cancellation). */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** The server answered with an error: [code] from `ApiError.error` (e.g. `invalid_code`), [status] HTTP. */
    data class Err(val code: String, val status: Int, val message: String = "") : ApiResult<Nothing>

    /** No answer (no network, server down, timeout). */
    data object Offline : ApiResult<Nothing>

    val valueOrNull: T? get() = (this as? Ok<T>)?.value
    val isOk: Boolean get() = this is Ok
}

inline fun <T, R> ApiResult<T>.map(f: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(f(value))
    is ApiResult.Err -> this
    ApiResult.Offline -> ApiResult.Offline
}

/**
 * One function per endpoint of plan §10.3 (see `tea.model.Dto`). [ApiClient] is the real one;
 * tests use fakes. Functions needing a session use the token the client was given.
 */
interface TeaApi {
    /** False after a call got no answer; true again on any answer. */
    val online: StateFlow<Boolean>

    /** Pings `/health` now, then every 30 s while offline (less often while online). */
    fun startMonitor(scope: CoroutineScope)

    suspend fun health(): ApiResult<HealthResponse>
    suspend fun authStart(email: String, language: String): ApiResult<Unit>
    suspend fun authVerify(email: String, code: String): ApiResult<VerifyResponse>
    suspend fun passwordAttempt(password: String): ApiResult<PasswordAttemptResponse>
    suspend fun login(email: String, password: String): ApiResult<LoginResponse>
    suspend fun logout(): ApiResult<Unit>
    suspend fun getProfile(): ApiResult<Profile>
    suspend fun putProfile(profile: Profile): ApiResult<Profile>
    suspend fun postSignals(request: SignalsRequest): ApiResult<Unit>
    suspend fun about(): ApiResult<List<AboutEntry>>
    suspend fun deleteMe(): ApiResult<Unit>
    suspend fun postAttempts(attempts: List<Attempt>): ApiResult<AttemptsResponse>
    suspend fun getMastery(): ApiResult<List<MasteryState>>
    suspend fun putMastery(states: List<MasteryState>): ApiResult<List<MasteryState>>
    suspend fun postEvents(events: List<AppEvent>): ApiResult<Unit>
    suspend fun getEvents(type: String): ApiResult<List<StoredEvent>>
    suspend fun getSettings(): ApiResult<SettingsResponse>
    suspend fun putSettings(practiceEmails: String): ApiResult<SettingsResponse>
    suspend fun leaderboard(period: String): ApiResult<LeaderboardResponse>
    suspend fun readiness(lessonId: String): ApiResult<ReadinessResponse>
    suspend fun reportField(): ApiResult<Unit>
    suspend fun reveal(token: String): ApiResult<RevealResponse>
    suspend fun gotcha(sendId: String): ApiResult<GotchaResponse>
}
