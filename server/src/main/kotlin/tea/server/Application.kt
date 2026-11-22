package tea.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import tea.model.ApiError
import tea.model.HealthResponse
import tea.model.TeaJson
import tea.server.auth.RateLimiter
import tea.server.auth.authRoutes
import tea.server.db.Db
import tea.server.field.FieldTemplates
import tea.server.field.fieldRoutes
import tea.server.leaderboard.ItemCatalog
import tea.server.leaderboard.leaderboardRoutes
import tea.server.mail.ConsoleMailer
import tea.server.mail.Mailer
import tea.server.mail.SmtpMailer
import tea.server.me.aboutRoutes
import tea.server.me.attemptRoutes
import tea.server.me.eventRoutes
import tea.server.me.masteryRoutes
import tea.server.me.profileRoutes
import tea.server.me.settingsRoutes
import tea.server.password.HibpClient
import tea.server.password.PasswordAnalyzer
import tea.server.password.PwnedClient

private val log = LoggerFactory.getLogger("tea.server")

fun main() {
    val config = Config.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }
        .start(wait = true)
}

/** Origins of local dev servers (any port). */
private val LOCAL_ORIGIN = Regex("^http://(localhost|127\\.0\\.0\\.1)(:\\d+)?$")

/** Paths whose last segment is a secret token: logged as `<token>`. */
private val TOKEN_PATH = Regex("^(/f/c/|/field/reveal/)[^/]+$")

/**
 * Wires the whole server. Tests call it with fakes (mailer, clock, HIBP, short poll interval).
 * Returns the Services so tests can reach the scheduler or the database.
 */
fun Application.module(
    config: Config = Config.fromEnv(),
    mailer: Mailer? = null,
    clock: Clock = Clock.System,
    pwned: PwnedClient = HibpClient(),
    startScheduler: Boolean = true,
): Services {
    val db = Db.connect(config)
    val pepper = config.codePepper.ifBlank {
        log.warn("CODE_PEPPER is not set: using a random pepper (codes sent before a restart stop working)")
        Crypto.newToken()
    }
    if (config.devVerifyCode.isNotBlank()) {
        log.warn("DEBUG: the override verification code is ENABLED (DEV_VERIFY_CODE). Set DEV_VERIFY_CODE=off or configure SMTP_PASSWORD before real use.")
    }
    val theMailer = mailer ?: if (config.smtpEnabled) SmtpMailer(config) else ConsoleMailer().also {
        log.warn("SMTP_PASSWORD is not set: mails are printed to the log (ConsoleMailer, dev only)")
    }
    val services = Services(
        config = config,
        db = db,
        mailer = theMailer,
        clock = clock,
        analyzer = PasswordAnalyzer(pwned),
        limiter = RateLimiter(clock, config.rateLimitsEnabled),
        items = ItemCatalog.load(config.contentDir),
        templates = FieldTemplates(config.contentDir),
        codePepper = pepper,
    )
    attributes.put(ServicesKey, services)
    monitor.subscribe(ApplicationStopped) { runCatching { db.close() } }

    install(ContentNegotiation) { json(TeaJson) }

    install(CallLogging) {
        // Method, path (tokens redacted) and status only: never bodies, headers or query strings.
        format { call ->
            val path = call.request.path().replace(TOKEN_PATH) { it.groupValues[1] + "<token>" }
            "${call.response.status()?.value ?: "-"} ${call.request.httpMethod.value} $path"
        }
    }

    install(CORS) {
        val exact = config.allowedOrigins.toSet()
        allowOrigins { origin -> origin in exact || LOCAL_ORIGIN.matches(origin) }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }

    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respond(e.status, ApiError(e.code, e.message))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", "The request body is not valid."))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", "The request body is not valid."))
        }
        exception<Throwable> { call, e ->
            log.error("unhandled error on {}: {}", call.request.path().replace(TOKEN_PATH) { it.groupValues[1] + "<token>" }, e.toString())
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal", "Something went wrong on our side."))
        }
    }

    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    db = db.ping(),
                    mailer = theMailer.kind,
                    version = config.version,
                    ai = false,
                ),
            )
        }
        authRoutes()
        profileRoutes()
        aboutRoutes()
        attemptRoutes()
        masteryRoutes()
        eventRoutes()
        settingsRoutes()
        leaderboardRoutes()
        fieldRoutes()
    }

    if (startScheduler) services.field.start(this)
    return services
}
