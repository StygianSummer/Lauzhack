package tea.net

import tea.store.LocalState

/**
 * Contract C5 (plan §12): which backend the app talks to, chosen at runtime.
 *
 * Web, in order: (1) `?api=<url>` query parameter, then saved to `tea.api`; `?api=reset` clears the
 * saved value and continues; (2) saved `tea.api`; (3) `config.json` next to the page
 * (`{"api": "https://..."}`, written by the Pages workflow); (4) `http://localhost:8080`.
 *
 * Desktop / Android: env var `TEA_API`, then the value saved in Settings (`tea.api`), then
 * `http://localhost:8080` (Android emulator: `http://10.0.2.2:8080`).
 */
object BackendUrl {
    const val DEFAULT = "http://localhost:8080"
    const val ANDROID_EMULATOR = "http://10.0.2.2:8080"

    enum class Source { QUERY, SAVED, CONFIG, ENV, DEFAULT }

    data class Resolved(val url: String, val source: Source)

    suspend fun resolveWeb(queryApi: String?, state: LocalState, fetchConfig: suspend () -> String?): Resolved {
        val q = queryApi?.trim()
        if (q.equals("reset", ignoreCase = true)) {
            state.apiOverride = null
        } else {
            normalise(q)?.let {
                state.apiOverride = it
                return Resolved(it, Source.QUERY)
            }
        }
        normalise(state.apiOverride)?.let { return Resolved(it, Source.SAVED) }
        normalise(fetchConfig())?.let { return Resolved(it, Source.CONFIG) }
        return Resolved(DEFAULT, Source.DEFAULT)
    }

    fun resolveDesktop(env: String?, state: LocalState, default: String = DEFAULT): Resolved {
        normalise(env)?.let { return Resolved(it, Source.ENV) }
        normalise(state.apiOverride)?.let { return Resolved(it, Source.SAVED) }
        return Resolved(default, Source.DEFAULT)
    }

    /** Trimmed `http(s)://...` without trailing slashes, or null if [raw] is not a usable URL. */
    fun normalise(raw: String?): String? {
        val s = raw?.trim()?.trimEnd('/') ?: return null
        val lower = s.lowercase()
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) return null
        val host = s.substringAfter("://").substringBefore('/')
        if (host.isBlank() || host.any { it.isWhitespace() }) return null
        return s
    }
}
