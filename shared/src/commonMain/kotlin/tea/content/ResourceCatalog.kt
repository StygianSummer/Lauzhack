package tea.content

import kotlinx.serialization.Serializable

/** `content/resources/resources.json`: Dog's "what to do now" cards and lesson tools, picked by ask x country. */
@Serializable
data class ResourceCatalog(val resources: List<Resource> = emptyList()) {

    fun byId(id: String): Resource? = resources.firstOrNull { it.id == id }

    /**
     * Resources for an [ask] slot (e.g. "click_login") in [country] (ISO code, any case).
     * Country-specific entries come first, then the `ALL` entries, so `first()` is the local card when
     * one exists and the international one otherwise. With no country, only `ALL` entries.
     */
    fun forAsk(ask: String, country: String?, verifiedOnly: Boolean = false): List<Resource> {
        val matching = resources.filter { ask in it.asks && (!verifiedOnly || it.verified) }
        val cc = country?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it != ALL }
        val local = if (cc == null) emptyList() else matching.filter { r -> r.countries.any { it.equals(cc, true) } }
        val global = matching.filter { r -> r.countries.any { it.equals(ALL, true) } }
        return (local + global).distinctBy { it.id }
    }

    companion object {
        const val ALL = "ALL"
    }
}

@Serializable
data class Resource(
    val id: String,
    val name: String,
    /** Empty for entries that are a phone number or an instruction rather than a website. */
    val url: String = "",
    /** ISO codes or "ALL". */
    val countries: List<String> = listOf(ResourceCatalog.ALL),
    /** Slot asks this resource helps with (plan §7.2). */
    val asks: List<String> = emptyList(),
    val topic: String? = null,
    val purpose: String = "",
    /** false until a person has opened the link and confirmed its purpose. */
    val verified: Boolean = false,
) {
    val hasUrl: Boolean get() = url.isNotBlank()
}
