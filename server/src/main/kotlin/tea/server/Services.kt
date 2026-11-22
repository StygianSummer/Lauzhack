package tea.server

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.util.AttributeKey
import kotlin.time.Clock
import tea.server.auth.RateLimiter
import tea.server.db.Db
import tea.server.field.FieldScheduler
import tea.server.field.FieldTemplates
import tea.server.leaderboard.ItemCatalog
import tea.server.mail.Mailer
import tea.server.password.PasswordAnalyzer

/** Everything the routes need, wired once in [module] (no DI framework). */
class Services(
    val config: Config,
    val db: Db,
    val mailer: Mailer,
    val clock: Clock,
    val analyzer: PasswordAnalyzer,
    val limiter: RateLimiter,
    val items: ItemCatalog,
    val templates: FieldTemplates,
    /** Secret mixed into verification-code hashes. */
    val codePepper: String,
) {
    val field: FieldScheduler by lazy { FieldScheduler(this) }
}

val ServicesKey = AttributeKey<Services>("tea.services")

val ApplicationCall.services: Services get() = application.attributes[ServicesKey]

/**
 * First X-Forwarded-For hop when behind the reverse proxy (Caddy overwrites the header with the real
 * client address), else the socket peer. Used for rate limits only. In production the app port is
 * never published, so the header cannot be set by a client directly.
 */
fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.origin.remoteHost
