package tea.server

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import tea.model.AboutEntry
import tea.model.ApiError
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.AttemptAnswers
import tea.model.AttemptsResponse
import tea.model.Context
import tea.model.EventTypes
import tea.model.MasteryState
import tea.model.PasswordAttemptRequest
import tea.model.Profile
import tea.model.ReadinessRequest
import tea.model.SignalsRequest
import tea.model.Skill
import tea.model.StoredEvent
import tea.model.Support
import tea.model.TERMS_VERSION
import tea.server.db.ClientSignals
import tea.server.db.Events
import tea.server.db.Users
import tea.server.db.allTables

class MeTest {
    private fun attempt(id: String, item: String = "email-x", ctx: Context = Context.PRACTICE, at: kotlin.time.Instant) =
        Attempt(clientId = id, itemId = item, context = ctx, answers = AttemptAnswers(), correct = true, ms = 1000, createdAt = at)

    private fun event(type: String, at: kotlin.time.Instant, vararg kv: Pair<String, String>) =
        AppEvent(UUID.randomUUID().toString(), type, JsonObject(kv.associate { it.first to JsonPrimitive(it.second) }), at)

    @Test
    fun attemptsAreIdempotentOnClientId() = teaTest {
        val s = register()
        val a = attempt("c-1", at = clock.now())
        val b = attempt("c-2", at = clock.now())
        assertEquals(AttemptsResponse(2, 0), decode<AttemptsResponse>(post("/me/attempts", s.token, listOf(a, b)).bodyText()))
        assertEquals(AttemptsResponse(1, 2), decode<AttemptsResponse>(post("/me/attempts", s.token, listOf(a, b, attempt("c-3", at = clock.now()))).bodyText()))
        // duplicate inside one batch
        assertEquals(AttemptsResponse(1, 1), decode<AttemptsResponse>(post("/me/attempts", s.token, listOf(attempt("c-4", at = clock.now()), attempt("c-4", at = clock.now()))).bodyText()))
        // needs a session
        assertEquals(HttpStatusCode.Unauthorized, post("/me/attempts", null, listOf(a)).status)
    }

    @Test
    fun eventRules() = teaTest {
        val s = register()
        // field_* is server-only
        val r = post("/me/events", s.token, listOf(event("field_clicked", clock.now())))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals("forbidden_event_type", decode<ApiError>(r.bodyText()).error)

        // terms_accepted sets the users columns
        acceptTerms(s.token)
        services.db.txBlocking {
            val u = Users.selectAll().single()
            assertNotNull(u[Users.termsAcceptedAt])
            assertEquals(TERMS_VERSION, u[Users.termsVersion])
        }

        // idempotent on client_id; practice_email_opted_in deduplicated per lesson
        val e = event("lesson_started", clock.now(), "lesson_id" to "email-01")
        post("/me/events", s.token, listOf(e, e))
        post("/me/events", s.token, listOf(e))
        post("/me/events", s.token, listOf(event(EventTypes.PRACTICE_EMAIL_OPTED_IN, clock.now(), "lesson_id" to "email-01")))
        post("/me/events", s.token, listOf(event(EventTypes.PRACTICE_EMAIL_OPTED_IN, clock.now(), "lesson_id" to "email-01")))
        // the server-side press does not add a third one either
        acceptTerms(s.token)
        post("/me/readiness", s.token, ReadinessRequest("email-01"))
        services.db.txBlocking {
            assertEquals(1, Events.selectAll().where { Events.type eq "lesson_started" }.count())
            assertEquals(1, Events.selectAll().where { Events.type eq EventTypes.PRACTICE_EMAIL_OPTED_IN }.count())
        }

        // GET ?type=field_* is a prefix match, newest first
        clock.advance(1.minutes)
        services.field.tick()
        val fieldEvents = decode<List<StoredEvent>>(get("/me/events?type=field_*", s.token).bodyText())
        assertEquals(listOf("field_sent", "field_scheduled"), fieldEvents.map { it.type })
        val exact = decode<List<StoredEvent>>(get("/me/events?type=lesson_started", s.token).bodyText())
        assertEquals(1, exact.size)
    }

    @Test
    fun gotchaEventsAreTheOnlyClientFieldEventsAndAreListedByPrefix() = teaTest {
        val s = register()
        val gotcha = event(
            EventTypes.FIELD_GOTCHA,
            clock.now(),
            "send_id" to "send-1",
            "lesson_id" to "email-01",
            "answer" to "no",
            "reason" to "verify",
        )
        assertEquals(HttpStatusCode.NoContent, post("/me/events", s.token, listOf(gotcha)).status)
        assertEquals(
            listOf(EventTypes.FIELD_GOTCHA),
            decode<List<StoredEvent>>(get("/me/events?type=field_*", s.token).bodyText()).map { it.type },
        )
        val invalid = event(EventTypes.FIELD_GOTCHA, clock.now(), "send_id" to "send-1", "lesson_id" to "email-01", "answer" to "maybe")
        assertEquals("invalid_field_gotcha", decode<ApiError>(post("/me/events", s.token, listOf(invalid)).bodyText()).error)
        val serverOnly = event(EventTypes.FIELD_CLICKED, clock.now())
        assertEquals(HttpStatusCode.BadRequest, post("/me/events", s.token, listOf(serverOnly)).status)
    }

    @Test
    fun masteryIsLastWriteWins() = teaTest {
        val s = register()
        val t0 = clock.now()
        val m1 = MasteryState(Skill.WHO, p = 0.4, updatedAt = t0)
        put("/me/mastery", s.token, listOf(m1))
        // older write loses
        val merged1 = decode<List<MasteryState>>(put("/me/mastery", s.token, listOf(m1.copy(p = 0.1, updatedAt = t0 - 1.hours))).bodyText())
        assertEquals(0.4, merged1.single().p)
        // newer write wins, and a second skill is added
        val merged2 = decode<List<MasteryState>>(put("/me/mastery", s.token,
            listOf(m1.copy(p = 0.9, support = Support.FADED, updatedAt = t0 + 1.hours), MasteryState(Skill.VERIFY, 0.2, updatedAt = t0))).bodyText())
        assertEquals(listOf(Skill.WHO, Skill.VERIFY), merged2.map { it.skill })
        assertEquals(0.9, merged2.first().p)
        assertEquals(Support.FADED, merged2.first().support)
        assertEquals(merged2, decode<List<MasteryState>>(get("/me/mastery", s.token).bodyText()))
    }

    @Test
    fun profileCopiesNamesAndKeepsServerProvider() = teaTest {
        val s = register("sam@gmail.com")
        val p = decode<Profile>(put("/me/profile", s.token, Profile(language = "es", firstName = "Sam", lastName = "Lee", emailProvider = "fake")).bodyText())
        assertEquals("gmail", p.emailProvider)
        services.db.txBlocking {
            val u = Users.selectAll().single()
            assertEquals("Sam", u[Users.firstName]); assertEquals("es", u[Users.language])
        }
    }

    @Test
    fun signalsKeepOnlyListedFields() = teaTest {
        val s = register()
        val json = JsonObject(mapOf(
            "browser_family" to JsonPrimitive("chrome"),
            "timezone" to JsonPrimitive("Europe/Zurich"),
            "user_agent" to JsonPrimitive("Mozilla/5.0 full string"),
            "contacts" to JsonPrimitive("nope"),
        ))
        assertEquals(HttpStatusCode.NoContent, post("/me/signals", s.token, SignalsRequest("web", json)).status)
        services.db.txBlocking {
            val stored = ClientSignals.selectAll().single()[ClientSignals.json]
            assertTrue("chrome" in stored && "Europe/Zurich" in stored)
            assertTrue("Mozilla" !in stored && "contacts" !in stored)
        }
    }

    @Test
    fun aboutCoversEveryStoredFieldAndDeleteRemovesEverything() = teaTest {
        val s = register()
        val other = register("other@mail.example")
        acceptTerms(s.token)
        put("/me/profile", s.token, Profile(firstName = "Jane", lastName = "Doe", hereFor = "ngo", channels = listOf("email", "sms")))
        post("/auth/password/attempt", s.token, PasswordAttemptRequest("a-long-enough-passphrase"))
        post("/me/signals", s.token, SignalsRequest("web", JsonObject(mapOf("locale" to JsonPrimitive("en-GB")))))
        put("/me/mastery", s.token, listOf(MasteryState(Skill.WHO, 0.5, updatedAt = clock.now())))
        post("/me/attempts", s.token, listOf(attempt("a-1", at = clock.now())))
        post("/me/readiness", s.token, ReadinessRequest("email-01"))
        services.field.tick()
        post("/me/attempts", other.token, listOf(attempt("b-1", at = clock.now())))

        val about = decode<List<AboutEntry>>(get("/me/about", s.token).bodyText())
        val byField = about.associateBy { it.field }
        val expected = listOf(
            "Email address", "Email confirmed", "Account created", "First name", "Last name", "Leaderboard name",
            "Language", "Country", "Password", "Terms of use", "Practice emails", "Email provider", "Here for",
            "Age range", "Channels you use", "Email app", "What you do online", "Past experience with scams",
            "How good you think you are", "Large text", "Dark mode", "Daily goal", "Placement result",
            "Your status rating in the lesson", "Your password: length and kinds of characters",
            "Your password: strength score", "Your password: data breach check", "Your password: personal details inside",
            "Your password: keyboard patterns", "Device settings", "Skill progress", "Answered exercises",
            "Activity record", "Practice emails sent to you", "Signed-in devices", "Sign-in codes", "Groups",
        )
        assertEquals(expected, about.map { it.field })
        assertTrue(about.all { it.value.isNotBlank() && it.purpose.isNotBlank() })
        assertTrue("24 characters long and uses 2 kinds" in byField.getValue("Your password: length and kinds of characters").value)
        assertTrue("en-GB" in byField.getValue("Device settings").value)
        assertTrue("Sent: 1" in byField.getValue("Practice emails sent to you").value)
        assertEquals("1", byField.getValue("Answered exercises").value)

        // French wording when the user's language is French
        put("/me/profile", s.token, Profile(language = "fr", firstName = "Jane"))
        val fr = decode<List<AboutEntry>>(get("/me/about", s.token).bodyText())
        assertEquals("Adresse e-mail", fr.first().field)

        // hard delete
        assertEquals(HttpStatusCode.NoContent, client.delete("/me") { bearerAuth(s.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, get("/me/profile", s.token).status)
        services.db.txBlocking {
            for (t in allTables) {
                val cols = t.columns.map { it.name }
                val rows = t.selectAll().toList()
                for (row in rows) for (c in t.columns) {
                    val v = row[c]
                    assertTrue(v != s.userId && v != "jane.doe@mail.example", "row about the deleted user left in ${t.tableName}.${c.name}")
                }
                assertNotNull(cols)
            }
            // the other user is untouched
            assertEquals(1, Users.selectAll().count())
        }
        assertEquals(HttpStatusCode.OK, get("/me/profile", other.token).status)
    }
}
