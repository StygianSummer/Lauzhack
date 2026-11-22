package tea.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tea.model.AppEvent
import tea.model.EventTypes
import tea.model.StartRequest
import tea.model.TERMS_VERSION
import tea.model.TeaJson
import tea.model.VerifyRequest
import tea.model.VerifyResponse
import tea.server.mail.Mailer
import tea.server.password.PwnedClient

class FakeMailer : Mailer {
    data class Sent(val to: String, val subject: String, val text: String, val html: String?)

    override val kind = "console"
    val sent = java.util.Collections.synchronizedList(mutableListOf<Sent>())

    override suspend fun send(to: String, subject: String, text: String, html: String?) {
        sent += Sent(to, subject, text, html)
    }

    fun lastCodeFor(email: String): String =
        Regex("\\b(\\d{6})\\b").find(sent.last { it.to == email }.text)!!.groupValues[1]
}

class FakeClock(var now: Instant = Instant.parse("2026-11-18T10:00:00Z")) : Clock {
    override fun now(): Instant = now
    fun advance(d: Duration) { now += d }
}

/** SHA-1 (upper hex) → breach count. Everything else: 0. */
class FakePwned(private val known: Map<String, Int> = emptyMap()) : PwnedClient {
    val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
    override suspend fun pwnedCount(sha1Hex: String): Int? {
        calls += sha1Hex
        return known[sha1Hex.uppercase()] ?: 0
    }

    companion object {
        fun of(vararg breached: String) =
            FakePwned(breached.associate { Crypto.sha1Hex(it.toByteArray()).uppercase() to 4242 })
    }
}

fun testConfig(
    contentDir: String = "does-not-exist",
    rateLimits: Boolean = false,
    devVerifyCode: String = "",
    fieldRemindDelayS: Int = 300,
) = Config(
    devVerifyCode = devVerifyCode,
    databaseUrl = "jdbc:h2:mem:test-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
    appPublicUrl = "https://app.example/tea/",
    apiPublicUrl = "https://api.example",
    fieldDelayMinS = 0,
    fieldDelayMaxS = 0,
    fieldRemindDelayS = fieldRemindDelayS,
    fieldPollInterval = 100.milliseconds,
    adminKey = "admin-secret",
    codePepper = "test-pepper",
    contentDir = contentDir,
    rateLimitsEnabled = rateLimits,
)

class TestCtx(
    val client: HttpClient,
    val services: Services,
    val mailer: FakeMailer,
    val clock: FakeClock,
    val pwned: FakePwned,
) {
    /** start → code from the fake mailer → verify. Returns the session. */
    suspend fun register(email: String = "jane.doe@mail.example", language: String = "en"): VerifyResponse {
        val r = client.post("/auth/start") { json(StartRequest(email, language)) }
        assertEquals(HttpStatusCode.NoContent, r.status)
        val v = client.post("/auth/verify") { json(VerifyRequest(email, mailer.lastCodeFor(email))) }
        assertEquals(HttpStatusCode.OK, v.status)
        return TeaJson.decodeFromString(VerifyResponse.serializer(), v.bodyText())
    }

    suspend fun acceptTerms(token: String) {
        val r = client.post("/me/events") {
            bearerAuth(token)
            json(listOf(AppEvent(UUID.randomUUID().toString(), EventTypes.TERMS_ACCEPTED,
                JsonObject(mapOf("version" to JsonPrimitive(TERMS_VERSION))), clock.now())))
        }
        assertEquals(HttpStatusCode.NoContent, r.status)
    }

    suspend fun get(path: String, token: String? = null): HttpResponse =
        client.get(path) { if (token != null) bearerAuth(token) }

    suspend inline fun <reified T> post(path: String, token: String?, body: T): HttpResponse =
        client.post(path) { if (token != null) bearerAuth(token); json(body) }

    suspend inline fun <reified T> put(path: String, token: String?, body: T): HttpResponse =
        client.put(path) { if (token != null) bearerAuth(token); json(body) }
}

inline fun <reified T> HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend fun HttpResponse.bodyText(): String = bodyAsText()

inline fun <reified T> decode(text: String): T = TeaJson.decodeFromString(text)

fun teaTest(
    contentDir: String = "does-not-exist",
    rateLimits: Boolean = false,
    pwned: FakePwned = FakePwned(),
    startScheduler: Boolean = false,
    devVerifyCode: String = "",
    fieldRemindDelayS: Int = 300,
    block: suspend TestCtx.() -> Unit,
) = testApplication {
    val mailer = FakeMailer()
    val clock = FakeClock()
    lateinit var services: Services
    application {
        services = module(testConfig(contentDir, rateLimits, devVerifyCode, fieldRemindDelayS), mailer, clock, pwned, startScheduler)
    }
    startApplication()
    val client = createClient {
        followRedirects = false
        install(ContentNegotiation) { json(TeaJson) }
    }
    TestCtx(client, services, mailer, clock, pwned).block()
    services.db.close()
}

/** A temp content dir with the given relative files. */
fun contentDir(files: Map<String, String>): String {
    val root = Files.createTempDirectory("tea-content").toFile()
    for ((path, text) in files) File(root, path).apply { parentFile.mkdirs(); writeText(text) }
    root.deleteOnExit()
    return root.path
}
