package tea.server

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.Context
import tea.model.EventTypes
import tea.model.LeaderboardResponse
import tea.model.LeaderboardRow
import tea.model.Profile
import tea.model.StepKey
import tea.model.Support
import tea.server.leaderboard.weekStart

class LeaderboardTest {
    private fun item(id: String, heldOut: Boolean) = """
        {"id":"$id","language":"en","status":"draft","kind":"email","category":"scam","difficulty":1,
         "held_out":$heldOut,
         "slots":{"identity":"bank","channel":"email","ask":"login"},
         "steps":{"who":{"prompt":"p","answer":"a","choices":["x","y"],"correct":0}},
         "actions":[{"id":"safe","label":"l","outcome":"safe","consequence":"c"},{"id":"bad","label":"l","outcome":"unsafe","consequence":"c"}]}
    """.trimIndent()

    private val content = contentDir(mapOf(
        "items/email/p-1.en.json" to item("p-1", false),
        "items/email/h-1.en.json" to item("h-1", true),
        "items/email/p-1.fr.json" to "not json, and not an en file anyway",
        "items/email/broken.en.json" to "{ broken",
    ))

    private fun attempt(item: String, at: Instant, ctx: Context = Context.PRACTICE) = Attempt(
        clientId = UUID.randomUUID().toString(), itemId = item, context = ctx,
        answers = AttemptAnswers(actionId = "safe", stepChoices = mapOf(StepKey.WHO to 0)),
        correct = true, scaffold = mapOf(StepKey.WHO to Support.FADED), ms = 1000, createdAt = at,
    )

    private fun event(type: String, at: Instant, lesson: String? = null) = AppEvent(
        UUID.randomUUID().toString(), type,
        JsonObject(lesson?.let { mapOf("lesson_id" to JsonPrimitive(it)) } ?: emptyMap()), at,
    )

    @Test
    fun weekStartsMondayMidnightZurich() {
        // Wed 2026-11-18 10:00 UTC → Mon 2026-11-16 00:00 CET = Sun 2026-11-15 23:00 UTC
        assertEquals(Instant.parse("2026-11-15T23:00:00Z"), weekStart(Instant.parse("2026-11-18T10:00:00Z")))
        // Sunday 23:30 UTC is already Monday 00:30 in Zurich
        assertEquals(Instant.parse("2026-11-15T23:00:00Z"), weekStart(Instant.parse("2026-11-15T23:30:00Z")))
    }

    @Test
    fun xpIsRecomputedFromAttemptsAndEventBonuses() = teaTest(contentDir = content) {
        val a = register("a@x.example")
        val b = register("b@x.example")
        put("/me/profile", a.token, Profile(firstName = "Ada", lastName = "lovelace"))
        val now = clock.now()
        val lastWeek = now - 7.days

        post("/me/attempts", a.token, listOf(
            attempt("p-1", now),                         // 10 + 5 + 2 = 17
            attempt("h-1", now),                         // held-out: 30
            attempt("p-1", now, Context.PLACEMENT),      // 0
            attempt("unknown-item", now),                // verdict only: 10
            attempt("p-1", lastWeek),                    // 17, all-time only
        ))
        post("/me/events", a.token, listOf(
            event(EventTypes.PLACEMENT_COMPLETED, lastWeek),           // +20 once, all-time only (first one was last week)
            event(EventTypes.PLACEMENT_COMPLETED, now),
            event(EventTypes.LESSON_COMPLETED, now, "email-01"),       // +50
            event(EventTypes.LESSON_COMPLETED, now, "email-01"),       // same lesson: nothing
            event(EventTypes.LESSON_COMPLETED, now, "email-02"),       // +50
        ))
        post("/me/attempts", b.token, listOf(attempt("p-1", now)))   // 17

        val week = decode<LeaderboardResponse>(get("/leaderboard?period=week", a.token).bodyText())
        assertEquals(listOf(LeaderboardRow("Ada L.", 157, 1, isMe = true), LeaderboardRow("Learner", 17, 2)), week.top)
        assertEquals(LeaderboardRow("Ada L.", 157, 1, isMe = true), week.me)

        val all = decode<LeaderboardResponse>(get("/leaderboard?period=all").bodyText())
        assertEquals(157 + 17 + 20, all.top.first().xp)
        assertNull(all.me)
        assertTrue(all.top.none { it.isMe }, "anonymous: no row is mine")

        // a user with no XP still sees themselves
        val c = register("c@x.example")
        val withC = decode<LeaderboardResponse>(get("/leaderboard?period=week", c.token).bodyText())
        assertEquals(LeaderboardRow("Learner", 0, 3, isMe = true), withC.me)
        assertEquals(2, withC.top.size)
        assertTrue(withC.top.none { it.isMe })
    }

    /** Regression (UI run): two learners with the same name and XP were both shown as "You". */
    @Test
    fun onlyTheCallersRowIsMarkedEvenWithTwins() = teaTest(contentDir = content) {
        val a = register("twin1@x.example")
        val b = register("twin2@x.example")
        for (t in listOf(a, b)) {
            put("/me/profile", t.token, Profile(firstName = "Uma", lastName = "Ito"))
            post("/me/attempts", t.token, listOf(attempt("p-1", clock.now())))
        }
        val board = decode<LeaderboardResponse>(get("/leaderboard?period=week", a.token).bodyText())
        assertEquals(listOf(1, 1), board.top.map { it.rank })
        assertEquals(listOf("Uma I.", "Uma I."), board.top.map { it.displayName })
        assertEquals(1, board.top.count { it.isMe })
        assertTrue(board.me!!.isMe)
    }
}
