package tea.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.MasteryState
import tea.model.Profile
import tea.store.LocalState

/**
 * Offline-first upload queue (plan §12). Pending events, attempts, mastery and a "profile changed"
 * flag live in the KeyValueStore (`tea.queue.*`), so they survive reloads. [flush] uploads them in
 * batches when online and signed in; the server is idempotent on `client_id` (events, attempts) and
 * last-write-wins on `updated_at` (mastery), so a retry after a lost answer is harmless.
 * A guest who registers later keeps the same queue: the first flush after sign-in uploads it.
 *
 * Order of a flush: events first (so `terms_accepted` lands before anything that needs it), then
 * attempts, mastery, profile.
 */
class SyncQueue(
    private val state: LocalState,
    private val api: TeaApi,
    private val onProfileSynced: (Profile) -> Unit = {},
    private val batchSize: Int = 200,
) {
    data class FlushResult(
        val events: Int = 0,
        val attempts: Int = 0,
        val mastery: Int = 0,
        val profile: Boolean = false,
        /** Items the server refused as invalid (400): dropped so they do not block the queue. */
        val dropped: Int = 0,
        val offline: Boolean = false,
        val unauthorized: Boolean = false,
    )

    private val mutex = Mutex()
    private val _pending = MutableStateFlow(count())

    /** Number of items waiting for upload (profile counts as one). */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    // ---- queueing (synchronous, persisted at once)

    fun addEvent(event: AppEvent) {
        events = events.filterNot { it.clientId == event.clientId } + event
        refresh()
    }

    fun addAttempt(attempt: Attempt) {
        attempts = attempts.filterNot { it.clientId == attempt.clientId } + attempt
        refresh()
    }

    fun addMastery(states: Collection<MasteryState>) {
        val bySkill = mastery.associateBy { it.skill }.toMutableMap()
        states.forEach { bySkill[it.skill] = it }
        mastery = bySkill.values.toList()
        refresh()
    }

    fun markProfileDirty() {
        state.store.put(KEY_PROFILE, "1")
        refresh()
    }

    val pendingEvents: List<AppEvent> get() = events
    val pendingAttempts: List<Attempt> get() = attempts
    val pendingMastery: List<MasteryState> get() = mastery
    val profileDirty: Boolean get() = state.store.get(KEY_PROFILE) == "1"

    fun clear() {
        listOf(KEY_EVENTS, KEY_ATTEMPTS, KEY_MASTERY, KEY_PROFILE).forEach { state.store.remove(it) }
        refresh()
    }

    // ---- upload

    /** Uploads everything pending. The caller checks that a session exists. Never throws. */
    suspend fun flush(): FlushResult = mutex.withLock {
        var r = FlushResult()

        // Events
        val ev = uploadList(events, { api.postEvents(it) }) { sent ->
            val ids = sent.map { it.clientId }.toSet()
            events = events.filterNot { it.clientId in ids }
        }
        r = r.copy(events = ev.sent, dropped = r.dropped + ev.dropped)
        if (ev.stop != null) return@withLock finish(r, ev.stop)

        // Attempts
        val at = uploadList(attempts, { api.postAttempts(it) }) { sent ->
            val ids = sent.map { it.clientId }.toSet()
            attempts = attempts.filterNot { it.clientId in ids }
        }
        r = r.copy(attempts = at.sent, dropped = r.dropped + at.dropped)
        if (at.stop != null) return@withLock finish(r, at.stop)

        // Mastery (one request; last-write-wins on the server)
        val m = mastery
        if (m.isNotEmpty()) {
            when (val res = api.putMastery(m)) {
                is ApiResult.Ok -> {
                    val sent = m.map { it.skill to it.updatedAt }.toSet()
                    mastery = mastery.filterNot { (it.skill to it.updatedAt) in sent }
                    r = r.copy(mastery = m.size)
                }
                is ApiResult.Err -> {
                    if (res.status == 401) return@withLock finish(r, ApiResult.Err(res.code, 401))
                    if (res.status == 400) {
                        mastery = emptyList()
                        r = r.copy(dropped = r.dropped + m.size)
                    } else return@withLock finish(r, res)
                }
                ApiResult.Offline -> return@withLock finish(r, ApiResult.Offline)
            }
        }

        // Profile
        if (profileDirty) {
            when (val res = api.putProfile(state.profile)) {
                is ApiResult.Ok -> {
                    state.store.remove(KEY_PROFILE)
                    onProfileSynced(res.value)
                    r = r.copy(profile = true)
                }
                is ApiResult.Err -> {
                    if (res.status == 400) state.store.remove(KEY_PROFILE)
                    else return@withLock finish(r, res)
                }
                ApiResult.Offline -> return@withLock finish(r, ApiResult.Offline)
            }
        }
        finish(r, null)
    }

    private fun finish(r: FlushResult, stop: ApiResult<*>?): FlushResult {
        refresh()
        return when {
            stop == ApiResult.Offline -> r.copy(offline = true)
            stop is ApiResult.Err && stop.status == 401 -> r.copy(unauthorized = true)
            else -> r
        }
    }

    private class Upload(val sent: Int, val dropped: Int, val stop: ApiResult<*>?)

    /**
     * Sends [items] in batches. On 400 a batch is retried one by one so a single invalid item is
     * dropped without losing the others. Stops on offline, 401 and other errors (kept for later).
     */
    private suspend fun <T> uploadList(
        items: List<T>,
        post: suspend (List<T>) -> ApiResult<*>,
        onSent: (List<T>) -> Unit,
    ): Upload {
        var sent = 0
        var dropped = 0
        for (batch in items.chunked(batchSize)) {
            when (val res = post(batch)) {
                is ApiResult.Ok<*> -> {
                    onSent(batch); sent += batch.size
                }
                ApiResult.Offline -> return Upload(sent, dropped, res)
                is ApiResult.Err -> {
                    if (res.status != 400) return Upload(sent, dropped, res)
                    for (one in batch) {
                        when (val single = post(listOf(one))) {
                            is ApiResult.Ok<*> -> sent++
                            ApiResult.Offline -> return Upload(sent, dropped, single)
                            is ApiResult.Err -> if (single.status == 400) dropped++ else return Upload(sent, dropped, single)
                        }
                        onSent(listOf(one))
                    }
                }
            }
        }
        return Upload(sent, dropped, null)
    }

    // ---- persistence

    private var events: List<AppEvent>
        get() = state.read(KEY_EVENTS, ListSerializer(AppEvent.serializer())) ?: emptyList()
        set(v) = state.write(KEY_EVENTS, ListSerializer(AppEvent.serializer()), v.ifEmpty { null })

    private var attempts: List<Attempt>
        get() = state.read(KEY_ATTEMPTS, ListSerializer(Attempt.serializer())) ?: emptyList()
        set(v) = state.write(KEY_ATTEMPTS, ListSerializer(Attempt.serializer()), v.ifEmpty { null })

    private var mastery: List<MasteryState>
        get() = state.read(KEY_MASTERY, ListSerializer(MasteryState.serializer())) ?: emptyList()
        set(v) = state.write(KEY_MASTERY, ListSerializer(MasteryState.serializer()), v.ifEmpty { null })

    private fun count(): Int = events.size + attempts.size + mastery.size + (if (profileDirty) 1 else 0)

    private fun refresh() {
        _pending.value = count()
    }

    companion object {
        const val KEY_EVENTS = "tea.queue.events"
        const val KEY_ATTEMPTS = "tea.queue.attempts"
        const val KEY_MASTERY = "tea.queue.mastery"
        const val KEY_PROFILE = "tea.queue.profile"
    }
}
