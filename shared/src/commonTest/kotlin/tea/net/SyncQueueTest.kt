package tea.net

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.Context
import tea.model.MasteryState
import tea.model.Profile
import tea.model.Skill
import tea.store.InMemoryKeyValueStore
import tea.store.LocalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncQueueTest {
    private val t0 = Instant.parse("2026-11-21T10:00:00Z")
    private fun event(id: String, type: String = "lesson_started") = AppEvent(id, type, JsonObject(emptyMap()), t0)
    private fun attempt(id: String) = Attempt(id, "email-x", Context.PRACTICE, AttemptAnswers(), true, ms = 1000, createdAt = t0)
    private fun mastery(skill: Skill, p: Double, at: Instant = t0) = MasteryState(skill, p, updatedAt = at)

    private val store = InMemoryKeyValueStore()
    private val state = LocalState(store)
    private val api = FakeApi(token = { "tok" })

    @Test
    fun queuePersistsAcrossInstances() {
        val q = SyncQueue(state, api)
        q.addEvent(event("e1"))
        q.addAttempt(attempt("a1"))
        q.addMastery(listOf(mastery(Skill.WHO, 0.4)))
        q.markProfileDirty()
        val again = SyncQueue(LocalState(store), api)
        assertEquals(4, again.pending.value)
        assertEquals(listOf("e1"), again.pendingEvents.map { it.clientId })
    }

    @Test
    fun flushUploadsEverythingAndEmptiesTheQueue() = runTest {
        state.profile = Profile(firstName = "Ada")
        var synced: Profile? = null
        val q = SyncQueue(state, api, onProfileSynced = { synced = it })
        q.addEvent(event("e1", "terms_accepted"))
        q.addEvent(event("e2"))
        q.addAttempt(attempt("a1"))
        q.addMastery(listOf(mastery(Skill.WHO, 0.4), mastery(Skill.ASK, 0.6)))
        q.markProfileDirty()
        val r = q.flush()
        assertEquals(SyncQueue.FlushResult(events = 2, attempts = 1, mastery = 2, profile = true), r)
        assertEquals(0, q.pending.value)
        assertEquals(listOf("e1", "e2"), api.events.keys.toList())
        assertEquals("Ada", api.profile?.firstName)
        assertEquals("other", synced?.emailProvider)
        // events go first (terms_accepted must reach the server before anything that needs it)
        assertEquals(listOf("events", "attempts", "putMastery", "putProfile"), api.calls)
    }

    @Test
    fun offlineKeepsTheQueue() = runTest {
        val q = SyncQueue(state, api)
        q.addEvent(event("e1"))
        api.onlineFlow.value = false
        val r = q.flush()
        assertTrue(r.offline)
        assertEquals(1, q.pending.value)
        api.onlineFlow.value = true
        q.flush()
        assertEquals(0, q.pending.value)
        assertEquals(1, api.events.size)
    }

    @Test
    fun lostAnswerRetriesIdempotently() = runTest {
        val q = SyncQueue(state, api)
        q.addAttempt(attempt("a1"))
        q.addAttempt(attempt("a2"))
        api.loseResponses = 1 // the server stores the batch, but the answer never arrives
        assertTrue(q.flush().offline)
        assertEquals(2, q.pending.value)
        q.addAttempt(attempt("a3"))
        val r = q.flush()
        assertEquals(3, r.attempts)
        assertEquals(0, q.pending.value)
        assertEquals(listOf("a1", "a2", "a3"), api.attempts.keys.toList()) // no duplicates
        assertEquals(2, api.postAttemptsCalls)
    }

    @Test
    fun invalidItemIsDroppedWithoutLosingTheOthers() = runTest {
        val q = SyncQueue(state, api)
        q.addEvent(event("good1"))
        q.addEvent(event("bad", "field_clicked")) // the server refuses client field_* events
        q.addEvent(event("good2"))
        val r = q.flush()
        assertEquals(1, r.dropped)
        assertEquals(2, r.events)
        assertEquals(setOf("good1", "good2"), api.events.keys)
        assertEquals(0, q.pending.value)
    }

    @Test
    fun unauthorizedStopsAndKeeps() = runTest {
        val q = SyncQueue(state, api)
        q.addEvent(event("e1"))
        api.rejectToken = true
        val r = q.flush()
        assertTrue(r.unauthorized)
        assertEquals(1, q.pending.value)
    }

    @Test
    fun masteryKeepsLatestPerSkillAndLaterChangesSurvive() = runTest {
        val q = SyncQueue(state, api)
        q.addMastery(listOf(mastery(Skill.WHO, 0.3)))
        q.addMastery(listOf(mastery(Skill.WHO, 0.5, t0.plus(kotlin.time.Duration.parse("1m")))))
        assertEquals(1, q.pendingMastery.size)
        q.flush()
        assertEquals(0.5, api.mastery[Skill.WHO]?.p)
        assertFalse(q.profileDirty)
    }

    @Test
    fun batchesAreChunked() = runTest {
        val q = SyncQueue(state, api, batchSize = 2)
        repeat(5) { q.addAttempt(attempt("a$it")) }
        q.flush()
        assertEquals(3, api.postAttemptsCalls)
        assertEquals(5, api.attempts.size)
    }
}
