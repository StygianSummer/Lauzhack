package tea.store

import kotlinx.serialization.builtins.serializer
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.Context
import tea.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LocalStateTest {
    private val store = InMemoryKeyValueStore()
    private val state = LocalState(store)

    @Test
    fun roundTrips() {
        assertFalse(state.hasProfile)
        assertEquals(Profile(), state.profile)
        state.profile = Profile(firstName = "Ada", language = "fr")
        state.session = Session("tok", "ada@mail.example", "u1")
        state.xp = 42
        state.updateFlags { it.copy(termsVersion = "v1", lessonSections = mapOf("email-01" to 3)) }
        val again = LocalState(store)
        assertTrue(again.hasProfile)
        assertEquals("Ada", again.profile.firstName)
        assertEquals("tok", again.session?.token)
        assertEquals(42, again.xp)
        assertEquals(3, again.flags.lessonSections["email-01"])
    }

    @Test
    fun blobsAreNamespaced() {
        state.saveBlob("streak", Int.serializer(), 3)
        assertEquals("3", store.get("tea.blob.streak"))
        assertEquals(3, state.loadBlob("streak", Int.serializer()))
        state.saveBlob("streak", Int.serializer(), null)
        assertNull(state.loadBlob("streak", Int.serializer()))
    }

    @Test
    fun attemptsLogIsBounded() {
        val t = Instant.parse("2026-11-21T10:00:00Z")
        fun a(i: Int) = Attempt("a$i", "i", Context.PRACTICE, AttemptAnswers(), true, ms = 1, createdAt = t)
        state.attempts = (0 until LocalState.MAX_ATTEMPTS + 20).map { a(it) }
        assertEquals(LocalState.MAX_ATTEMPTS, state.attempts.size)
        assertEquals("a20", state.attempts.first().clientId)
        state.appendAttempt(a(9999))
        assertEquals(LocalState.MAX_ATTEMPTS, state.attempts.size)
        assertEquals("a21", state.attempts.first().clientId)
        assertEquals("a9999", state.attempts.last().clientId)
    }

    @Test
    fun clearAllKeepsOnlyTheBackendUrl() {
        state.profile = Profile(firstName = "Ada")
        state.apiOverride = "https://api.example"
        store.put("other.app.key", "x")
        state.clearAll()
        assertEquals(setOf("tea.api", "other.app.key"), store.keys())
    }

    @Test
    fun corruptValuesReadAsMissing() {
        store.put(LocalState.KEY_PROFILE, "{not json")
        assertEquals(Profile(), state.profile)
    }
}
