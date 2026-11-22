package tea.server.password

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Have I Been Pwned "Pwned Passwords" lookup with k-anonymity: callers pass the SHA-1 hex of the
 * password; only its first 5 characters ever leave the server.
 */
interface PwnedClient {
    /** Times the password was seen in breaches (0 = not found), or null when the service is unavailable. */
    suspend fun pwnedCount(sha1Hex: String): Int?
}

class HibpClient(
    private val baseUrl: String = "https://api.pwnedpasswords.com/range/",
    private val timeout: Duration = Duration.ofSeconds(2),
) : PwnedClient {
    private val log = LoggerFactory.getLogger(HibpClient::class.java)
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(timeout).build()

    override suspend fun pwnedCount(sha1Hex: String): Int? {
        val upper = sha1Hex.uppercase()
        val prefix = upper.take(5)
        val suffix = upper.drop(5)
        return try {
            val req = HttpRequest.newBuilder(URI.create(baseUrl + prefix))
                .timeout(timeout)
                .header("Add-Padding", "true")
                .header("User-Agent", "ThisIsTea-server")
                .GET().build()
            val resp = withTimeoutOrNull(timeout.toMillis() + 500) {
                http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).await()
            }
            if (resp == null || resp.statusCode() != 200) {
                log.warn("hibp unavailable")
                return null
            }
            parse(resp.body(), suffix)
        } catch (e: Exception) {
            log.warn("hibp unavailable")
            null
        }
    }

    companion object {
        /** Lines are "SUFFIX:COUNT"; padding lines have count 0. */
        fun parse(body: String, suffixUpper: String): Int {
            for (line in body.lineSequence()) {
                val i = line.indexOf(':')
                if (i <= 0) continue
                if (line.substring(0, i).trim().equals(suffixUpper, ignoreCase = true)) {
                    return line.substring(i + 1).trim().toIntOrNull() ?: 0
                }
            }
            return 0
        }
    }
}
