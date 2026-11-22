package tea.content

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tea.model.Item
import tea.model.Profile
import tea.model.TeaJson

/**
 * Fills `{{first_name}}`, `{{email}}` and `{{email_domain}}` at render time (plan §4.1).
 * Guests (no profile / no address) get first name "Sam" and email "you@mail.example".
 * Unknown placeholders (e.g. the server's `{{click_url}}`) are left untouched.
 */
object Placeholders {
    const val GUEST_FIRST_NAME = "Sam"
    const val GUEST_EMAIL = "you@mail.example"

    private val token = Regex("""\{\{\s*([a-z_]+)\s*\}\}""")

    /** The values used for [profile] and [email]. */
    fun values(profile: Profile?, email: String?): Map<String, String> {
        val firstName = profile?.firstName?.trim()?.takeIf { it.isNotEmpty() } ?: GUEST_FIRST_NAME
        val address = email?.trim()?.takeIf { it.contains('@') } ?: GUEST_EMAIL
        return mapOf(
            "first_name" to firstName,
            "email" to address,
            "email_domain" to address.substringAfterLast('@'),
        )
    }

    fun fill(text: String, profile: Profile?, email: String?): String = fill(text, values(profile, email))

    /** Replaces every `{{key}}` whose key is in [values]; other placeholders stay as they are. */
    fun fill(text: String, values: Map<String, String>): String {
        if (!text.contains("{{")) return text
        return token.replace(text) { m -> values[m.groupValues[1]] ?: m.value }
    }

    /** A copy of [item] with every text field filled (walks the serialised form, so new fields are covered). */
    fun fill(item: Item, profile: Profile?, email: String?): Item = fill(item, values(profile, email))

    fun fill(item: Item, values: Map<String, String>): Item {
        fun walk(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.mapValues { (_, v) -> walk(v) })
            is JsonArray -> JsonArray(e.map(::walk))
            is JsonPrimitive -> if (e.isString && e.content.contains("{{")) JsonPrimitive(fill(e.content, values)) else e
        }
        val json = TeaJson.encodeToJsonElement(Item.serializer(), item)
        return TeaJson.decodeFromJsonElement(Item.serializer(), walk(json))
    }
}
