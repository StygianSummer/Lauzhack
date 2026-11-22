package tea.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Server configuration (plan §10.2). Read from environment variables; `.env` is never committed
 * (template: deploy/.env.example). Every field has a dev default so `./gradlew :server:run` works
 * with zero setup (in-memory H2, ConsoleMailer).
 */
data class Config(
    val port: Int = 8080,
    /** Blank → in-memory H2 (dev only, data is lost on restart). */
    val databaseUrl: String = "",
    val databaseUser: String = "",
    val databasePassword: String = "",
    /** Exact origins allowed by CORS, besides any http://localhost:* / http://127.0.0.1:* */
    val allowedOrigins: List<String> = emptyList(),
    /**
     * Base of the web app, used in links inside emails (e.g. https://org.github.io/repo/). The
     * default is the wasm dev server (`:composeApp:wasmJsBrowserDevelopmentRun`, pinned to 8081).
     */
    val appPublicUrl: String = "http://localhost:8081/",
    /** Base of this API as seen from the outside (e.g. https://host/api), used for click links inside emails. */
    val apiPublicUrl: String = "http://localhost:8080",
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 587,
    val smtpUser: String = "",
    /** Empty → ConsoleMailer. */
    val smtpPassword: String = "",
    val smtpFromName: String = "ThisIsTea",
    val fieldDelayMinS: Int = 15,
    val fieldDelayMaxS: Int = 90,
    val fieldRemindDelayS: Int = 300,
    /** How often the field scheduler looks for due sends (plan: 5 s). */
    val fieldPollInterval: Duration = 5.seconds,
    /** Blank → /admin endpoints are disabled. */
    val adminKey: String = "",
    /** Secret mixed into verification-code hashes. Blank → a random per-process pepper. */
    val codePepper: String = "",
    /** Root of the content folder (items/, field/). */
    val contentDir: String = "content",
    /** false disables the per-email / per-IP rate limits (tests, local demo). */
    val rateLimitsEnabled: Boolean = true,
    val version: String = "dev",
    /**
     * DEBUG ONLY. When not blank, this code is accepted by POST /auth/verify in place of the emailed
     * one (a code must still have been requested with /auth/start and not be expired).
     * From the environment (DEV_VERIFY_CODE) it defaults to "426769" while no SMTP password is set
     * (ConsoleMailer, dev) and to disabled as soon as real mail is configured.
     */
    val devVerifyCode: String = "",
) {
    val smtpEnabled: Boolean get() = smtpPassword.isNotBlank()

    companion object {
        const val DEV_VERIFY_CODE_DEFAULT = "426769"

        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun s(key: String, default: String = "") = env[key]?.trim()?.takeIf { it.isNotEmpty() } ?: default
            fun i(key: String, default: Int) = env[key]?.trim()?.toIntOrNull() ?: default
            val d = Config()
            // PUBLIC_URL: the one origin of a single-domain deployment (deploy/docker-compose.prod.yml),
            // where the reverse proxy serves the web app at / and this API at /api. It sets the
            // defaults of APP_PUBLIC_URL, API_PUBLIC_URL and adds itself to the CORS origins.
            val publicUrl = s("PUBLIC_URL").trimEnd('/')
            return Config(
                port = i("PORT", d.port),
                databaseUrl = s("DATABASE_URL"),
                databaseUser = s("DATABASE_USER"),
                databasePassword = s("DATABASE_PASSWORD"),
                allowedOrigins = (listOf(publicUrl) + s("ALLOWED_ORIGINS").split(','))
                    .map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.distinct(),
                appPublicUrl = s("APP_PUBLIC_URL", if (publicUrl.isEmpty()) d.appPublicUrl else "$publicUrl/"),
                apiPublicUrl = s("API_PUBLIC_URL", if (publicUrl.isEmpty()) d.apiPublicUrl else "$publicUrl/api"),
                smtpHost = s("SMTP_HOST", d.smtpHost),
                smtpPort = i("SMTP_PORT", d.smtpPort),
                smtpUser = s("SMTP_USER"),
                smtpPassword = s("SMTP_PASSWORD"),
                smtpFromName = s("SMTP_FROM_NAME", d.smtpFromName),
                fieldDelayMinS = i("FIELD_DELAY_MIN_S", d.fieldDelayMinS),
                fieldDelayMaxS = i("FIELD_DELAY_MAX_S", d.fieldDelayMaxS),
                fieldRemindDelayS = i("FIELD_REMIND_DELAY_S", d.fieldRemindDelayS).coerceAtLeast(0),
                fieldPollInterval = i("FIELD_POLL_S", 5).coerceAtLeast(1).seconds,
                adminKey = s("ADMIN_KEY"),
                codePepper = s("CODE_PEPPER"),
                contentDir = s("CONTENT_DIR", d.contentDir),
                rateLimitsEnabled = s("RATE_LIMITS", "on").lowercase() !in setOf("off", "false", "0", "no"),
                version = s("APP_VERSION", d.version),
                devVerifyCode = (env["DEV_VERIFY_CODE"]?.trim()?.takeIf { it.isNotEmpty() } ?: if (s("SMTP_PASSWORD").isEmpty()) DEV_VERIFY_CODE_DEFAULT else "")
                    .takeIf { it.lowercase() !in setOf("off", "false", "no", "0") } ?: "",
            )
        }
    }
}
