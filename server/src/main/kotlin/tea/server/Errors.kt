package tea.server

import io.ktor.http.HttpStatusCode

/**
 * Thrown by routes; StatusPages turns it into `ApiError(error = code, message)` with [status].
 * Error codes are a client contract: keep them stable (listed in the server handoff).
 */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String = "",
) : RuntimeException(message)

fun badRequest(code: String, message: String = "") = ApiException(HttpStatusCode.BadRequest, code, message)
fun unauthorized(message: String = "Sign in first.") = ApiException(HttpStatusCode.Unauthorized, "unauthorized", message)
fun notFound(message: String = "") = ApiException(HttpStatusCode.NotFound, "not_found", message)
fun rateLimited() = ApiException(HttpStatusCode.TooManyRequests, "rate_limited", "Please wait a minute and try again.")
