package tea.app

import kotlinx.browser.window

private fun jsTimeZone(): String = js("(Intl.DateTimeFormat().resolvedOptions().timeZone || '')")

private fun jsMatches(query: String): Boolean = js("(!!(window.matchMedia && window.matchMedia(query).matches))")

private fun jsQueryParam(name: String): String? = js("new URLSearchParams(window.location.search).get(name)")

private fun jsConfigUrl(): String = js("new URL('config.json', window.location.href).href")

private fun jsShareOrCopy(text: String): Boolean = js(
    """((function(t) {
        if (navigator.share && window.matchMedia && window.matchMedia('(pointer: coarse)').matches) {
            navigator.share({ text: t }).catch(function () {});
            return true;
        }
        if (navigator.clipboard && navigator.clipboard.writeText) { navigator.clipboard.writeText(t).catch(function () {}); }
        return false;
    })(text))""",
)

/** Browser: hash routing, localStorage, `matchMedia` for device preferences. */
class WebPlatformInfo : PlatformInfo {
    override val name: String = "web"
    override val locale: String = window.navigator.language.ifBlank { "en" }
    override val timezone: String = runCatching { jsTimeZone() }.getOrDefault("")
    override val reducedMotion: Boolean = runCatching { jsMatches("(prefers-reduced-motion: reduce)") }.getOrDefault(false)
    override val browserFamily: String? = browserFamilyOf(window.navigator.userAgent)
    override val pointer: String = if (runCatching { jsMatches("(pointer: coarse)") }.getOrDefault(false)) "touch" else "mouse"
    override val envApi: String? = null
    override val configUrl: String? = runCatching { jsConfigUrl() }.getOrNull()
    override val appLink: String? = window.location.origin + window.location.pathname

    override fun queryParam(name: String): String? = runCatching { jsQueryParam(name) }.getOrNull()

    override fun openUrl(url: String) {
        window.open(url, "_blank", "noopener")
    }

    override fun shareOrCopy(text: String): Boolean = runCatching { jsShareOrCopy(text) }.getOrDefault(false)

    override var hash: String
        get() = window.location.hash.removePrefix("#")
        set(value) {
            val target = "#$value"
            if (window.location.hash != target) window.location.hash = target
        }

    override fun onHashChange(listener: (String) -> Unit) {
        window.addEventListener("hashchange", { listener(window.location.hash.removePrefix("#")) })
    }
}

actual fun platformInfo(): PlatformInfo = WebPlatformInfo()
