package tea.server.auth

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * In-memory sliding-window limits keyed by email or IP (plan §10.3). Keys contain email addresses:
 * never log them. Disabled entirely with RATE_LIMITS=off.
 */
class RateLimiter(private val clock: Clock, private val enabled: Boolean) {
    data class Rule(val key: String, val limit: Int, val window: Duration)

    private val hits = HashMap<String, ArrayDeque<Instant>>()

    /** True (and every rule records one hit) iff no rule is exhausted. */
    @Synchronized
    fun allow(vararg rules: Rule): Boolean {
        if (!enabled) return true
        val now = clock.now()
        for (r in rules) {
            val q = hits.getOrPut(r.key) { ArrayDeque() }
            while (q.isNotEmpty() && q.first() <= now - r.window) q.removeFirst()
            if (q.size >= r.limit) return false
        }
        for (r in rules) hits.getValue(r.key).addLast(now)
        if (hits.size > 50_000) hits.entries.removeIf { it.value.isEmpty() }
        return true
    }
}
