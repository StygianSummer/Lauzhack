package tea.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.provider.Settings
import tea.android.TeaAndroid
import java.util.TimeZone

/**
 * Android: no URL bar, so the hash is in memory (like desktop); links open in the browser and
 * "Invite a friend" uses the system share sheet. Backend (plan C5): [envApi] is the URL baked in
 * at build time, then the settings value, then [defaultApi] (`10.0.2.2` on the emulator, which
 * is the host machine's localhost).
 */
class AndroidPlatformInfo(private val context: Context) : PlatformInfo {
    override val name: String = "android"

    /** The device language (not the app language, which may have been switched in Settings). */
    override val locale: String = Resources.getSystem().configuration.locales[0].toLanguageTag()
    override val timezone: String = TimeZone.getDefault().id
    override val reducedMotion: Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    override val browserFamily: String? = null
    override val pointer: String = "touch"
    override val envApi: String? = TeaAndroid.envApi
    override val defaultApi: String = if (isEmulator()) "http://10.0.2.2:8080" else "http://localhost:8080"
    override val configUrl: String? = null
    override val appLink: String? = null
    override var hash: String = ""

    override fun queryParam(name: String): String? = null

    override fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun shareOrCopy(text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val shared = runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (shared) return true
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ThisIsTea", text))
        }
        return false
    }

    override fun onHashChange(listener: (String) -> Unit) = Unit

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE in setOf("goldfish", "ranchu") || Build.MODEL.contains("sdk_gphone") ||
            Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK built for") ||
            Build.PRODUCT.contains("sdk")
}

actual fun platformInfo(): PlatformInfo = AndroidPlatformInfo(TeaAndroid.context)
