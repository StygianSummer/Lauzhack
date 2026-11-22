package tea.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * The one shared Json (plan §4). Kotlin properties are camelCase, JSON is snake_case
 * (via the naming strategy, so no @SerialName is needed on properties).
 * Used for content files, the local store and the HTTP API on both client and server.
 */
@OptIn(ExperimentalSerializationApi::class)
val TeaJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}
