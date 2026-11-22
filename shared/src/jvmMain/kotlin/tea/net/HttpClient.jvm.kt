package tea.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
    engine { config { connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS) } }
    block()
}
