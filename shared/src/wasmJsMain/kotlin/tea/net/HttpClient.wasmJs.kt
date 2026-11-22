package tea.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js

internal actual fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Js) { block() }
