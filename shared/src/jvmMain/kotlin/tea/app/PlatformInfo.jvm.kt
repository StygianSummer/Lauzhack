package tea.app

import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.util.Locale
import java.util.TimeZone

/** Desktop (jvm): no URL bar, so the hash is in memory; links open in the system browser. */
class DesktopPlatformInfo : PlatformInfo {
    override val name: String = "desktop"
    override val locale: String = Locale.getDefault().toLanguageTag()
    override val timezone: String = TimeZone.getDefault().id
    override val reducedMotion: Boolean = false
    override val browserFamily: String? = null
    override val pointer: String = "mouse"
    override val envApi: String? = System.getenv("TEA_API")?.takeIf { it.isNotBlank() }
    override val configUrl: String? = null
    override val appLink: String? = System.getenv("TEA_APP_URL")?.takeIf { it.isNotBlank() }
    override var hash: String = ""

    override fun queryParam(name: String): String? = System.getProperty("tea.$name")

    override fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }

    override fun shareOrCopy(text: String): Boolean {
        if (!GraphicsEnvironment.isHeadless()) {
            runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
        }
        return false
    }

    override fun onHashChange(listener: (String) -> Unit) = Unit
}

actual fun platformInfo(): PlatformInfo = DesktopPlatformInfo()
