package tea.app

/**
 * What the app needs from the platform (plan §3, §9.2.9, §12). Web and desktop implement it
 * today; Android later. Device facts are read only to adapt the UI and, after the terms, to post
 * the short list of device settings of plan §9.2.9 (never the full user agent).
 */
interface PlatformInfo {
    /** "web" | "desktop" | "android" (the `platform` field of `/me/signals`). */
    val name: String
    val isWeb: Boolean get() = name == "web"

    /** BCP 47 tag, e.g. "fr-CH". */
    val locale: String

    /** IANA zone, e.g. "Europe/Zurich". */
    val timezone: String

    /** The system asks for less motion. */
    val reducedMotion: Boolean

    /** "chrome" | "firefox" | "safari" | "edge" | "other" on web; null elsewhere. */
    val browserFamily: String?

    /** Primary pointer: "touch" or "mouse". */
    val pointer: String

    /** Desktop: env var `TEA_API` (C5). Null on web. */
    val envApi: String?

    /** Last-resort backend URL (C5 step 4). */
    val defaultApi: String get() = "http://localhost:8080"

    /** Absolute URL of `config.json` next to the web page; null off the web. */
    val configUrl: String?

    /** Link to the app for "Invite a friend" (web: this page without hash or query); null if unknown. */
    val appLink: String?

    /** A query parameter of the page URL (web only), e.g. `api`, `debug`. */
    fun queryParam(name: String): String?

    /** Opens [url] in the system browser / a new tab. */
    fun openUrl(url: String)

    /**
     * Shares [text] (system share sheet where there is one) or copies it to the clipboard.
     * @return true if shared through a share sheet, false if copied.
     */
    fun shareOrCopy(text: String): Boolean

    /** Current URL hash without the leading '#', e.g. "/home" (web); in-memory elsewhere. */
    var hash: String

    /** Called with the new hash (without '#') when the user changes it (back button, link). */
    fun onHashChange(listener: (String) -> Unit)
}

/** The platform this build runs on. */
expect fun platformInfo(): PlatformInfo

/** Turns a user agent into a browser family (no versions, nothing identifying). */
fun browserFamilyOf(userAgent: String): String {
    val ua = userAgent.lowercase()
    return when {
        "edg/" in ua || "edge/" in ua -> "edge"
        "firefox/" in ua || "fxios/" in ua -> "firefox"
        "opr/" in ua || "opera" in ua -> "other"
        "chrome/" in ua || "crios/" in ua || "chromium/" in ua -> "chrome"
        "safari/" in ua -> "safari"
        else -> "other"
    }
}

/** In-memory platform for tests, previews and screenshots. */
open class FakePlatformInfo(
    override val name: String = "desktop",
    override val locale: String = "en-GB",
    override val timezone: String = "Europe/Zurich",
    override val reducedMotion: Boolean = false,
    override val browserFamily: String? = null,
    override val pointer: String = "mouse",
    override val envApi: String? = null,
    override val configUrl: String? = null,
    override val appLink: String? = "https://thisistea.example/app/",
    private val query: Map<String, String> = emptyMap(),
) : PlatformInfo {
    val opened = mutableListOf<String>()
    val shared = mutableListOf<String>()
    private val listeners = mutableListOf<(String) -> Unit>()
    override var hash: String = ""
    override fun queryParam(name: String): String? = query[name]
    override fun openUrl(url: String) {
        opened += url
    }

    override fun shareOrCopy(text: String): Boolean {
        shared += text
        return false
    }

    override fun onHashChange(listener: (String) -> Unit) {
        listeners += listener
    }

    /** Simulates the user editing the address bar / pressing back. */
    fun userSetsHash(h: String) {
        hash = h
        listeners.forEach { it(h) }
    }
}
