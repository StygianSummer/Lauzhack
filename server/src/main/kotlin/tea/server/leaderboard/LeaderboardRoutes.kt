package tea.server.leaderboard

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import tea.engine.Xp
import tea.model.EventTypes
import tea.model.LeaderboardResponse
import tea.model.LeaderboardRow
import tea.model.TeaJson
import tea.server.auth.optionalUser
import tea.server.badRequest
import tea.server.db.Attempts
import tea.server.db.Events
import tea.server.db.Users
import tea.server.db.toKInstant
import tea.server.me.UserData
import tea.server.me.attemptFromRow
import tea.server.me.str
import tea.server.services

private val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")

/** Monday 00:00 Europe/Zurich of the week containing [now]. */
fun weekStart(now: Instant): Instant =
    now.toJavaInstant().atZone(ZURICH).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZURICH).toInstant().toKotlinInstant()

/**
 * XP per user since [since] (null = all time): Xp.xpFor over stored attempts, +20 for the first
 * placement_completed, +50 per distinct lesson_id of lesson_completed. Bonuses count in the
 * period where their first event happened. Call inside a transaction.
 */
fun computeXp(items: ItemCatalog, since: Instant?): Map<String, Int> {
    val xp = HashMap<String, Int>()
    for (row in Attempts.selectAll()) {
        val a = runCatching { attemptFromRow(row) }.getOrNull() ?: continue
        if (since != null && a.createdAt < since) continue
        val v = Xp.xpFor(a, items[a.itemId])
        if (v != 0) xp.merge(row[Attempts.userId], v, Int::plus)
    }
    val bonusTypes = listOf(EventTypes.PLACEMENT_COMPLETED, EventTypes.LESSON_COMPLETED)
    // First occurrence per (user, bonus key).
    val firsts = HashMap<Pair<String, String>, Instant>()
    for (row in Events.selectAll().where { Events.type inList bonusTypes }) {
        val user = row[Events.userId]
        val key = if (row[Events.type] == EventTypes.PLACEMENT_COMPLETED) {
            "placement"
        } else {
            val payload = runCatching { TeaJson.decodeFromString(JsonObject.serializer(), row[Events.payloadJson]) }.getOrNull()
            "lesson:" + (payload?.str("lesson_id") ?: continue)
        }
        val at = row[Events.createdAt].toKInstant()
        firsts.merge(user to key, at) { a, b -> if (a < b) a else b }
    }
    for ((k, at) in firsts) {
        if (since != null && at < since) continue
        val bonus = if (k.second == "placement") Xp.PLACEMENT_COMPLETED else Xp.LESSON_COMPLETED
        xp.merge(k.first, bonus, Int::plus)
    }
    return xp
}

fun Route.leaderboardRoutes() {
    get("/leaderboard") {
        val s = call.services
        val me = call.optionalUser()
        val period = call.request.queryParameters["period"] ?: "week"
        val since = when (period) {
            "week" -> weekStart(s.clock.now())
            "all" -> null
            else -> throw badRequest("invalid_period", "period must be week or all.")
        }
        val response = s.db.tx {
            val xp = computeXp(s.items, since)
            val ids = (xp.filterValues { it > 0 }.keys + listOfNotNull(me?.userId)).toList()
            val names = if (ids.isEmpty()) emptyMap() else
                Users.selectAll().where { Users.id inList ids }.associate { it[Users.id] to UserData.displayName(it) }
            // Competition ranking: equal XP → equal rank.
            val ranked = xp.filter { it.value > 0 && it.key in names }
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { names[it.key] })
            var rank = 0
            var prev: Int? = null
            val rows = ranked.mapIndexed { i, e ->
                if (e.value != prev) { rank = i + 1; prev = e.value }
                e.key to LeaderboardRow(names.getValue(e.key), e.value, rank, isMe = e.key == me?.userId)
            }
            val meRow = me?.let { m ->
                rows.firstOrNull { it.first == m.userId }?.second
                    ?: names[m.userId]?.let { LeaderboardRow(it, xp[m.userId] ?: 0, rows.size + 1, isMe = true) }
            }
            LeaderboardResponse(top = rows.take(20).map { it.second }, me = meRow)
        }
        call.respond(response)
    }
}
