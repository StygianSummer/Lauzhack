package tea.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * UI + content language (plan §13). `AppState.lang` holds the code; [ProvideLanguage] makes every
 * `stringResource` under it resolve in that language, on every platform, without a restart.
 *
 * How it works: Compose resources pick `values-<lang>/strings.xml` from the resource environment,
 * whose language comes from `androidx.compose.ui.text.intl.Locale.current` (jvm:
 * `java.util.Locale.getDefault()`, web: `navigator.language`). [setPlatformLanguage] changes that
 * locale and [ProvideLanguage] re-creates the UI under `key(lang)`. Missing keys in a language
 * fall back to `values/strings.xml` (English). Adding a language = adding files
 * (`values-<code>/strings.xml`, `content/.../<id>.<code>.json`) plus the code in [supported].
 */
object Lang {
    /** Languages with UI strings and content. */
    val supported: List<String> = listOf("en", "fr", "es")

    const val DEFAULT = "en"

    /** Name of each language in itself (never translated). */
    val nativeNames: Map<String, String> = mapOf("en" to "English", "fr" to "Français", "es" to "Español")

    /** "fr-CH" -> "fr"; anything unsupported -> "en". */
    fun fromLocale(locale: String?): String {
        val code = locale?.substringBefore('-')?.substringBefore('_')?.lowercase().orEmpty()
        return if (code in supported) code else DEFAULT
    }

    /** "fr-CH" -> "CH" (upper case region), else null. */
    fun regionOf(locale: String?): String? =
        locale?.split('-', '_')?.drop(1)?.firstOrNull { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
}

/** The current UI language code inside [ProvideLanguage]. */
val LocalLanguage = staticCompositionLocalOf { Lang.DEFAULT }

/**
 * Makes [lang] the locale Compose resources read (`androidx.compose.ui.text.intl.Locale.current`):
 * jvm = `java.util.Locale.setDefault`, web = a getter override of `navigator.language(s)`.
 */
expect fun setPlatformLanguage(lang: String)

/**
 * Renders [content] with every Compose string resource in [lang], switching without a restart:
 * sets the platform locale, then re-creates the subtree under `key(lang)` so every
 * `stringResource` reads the new locale. (Screen-local UI state is reset on a switch; app state
 * lives in AppState and is kept.)
 */
@Composable
fun ProvideLanguage(lang: String, content: @Composable () -> Unit) {
    remember(lang) { setPlatformLanguage(lang) }
    key(lang) {
        CompositionLocalProvider(LocalLanguage provides lang, content = content)
    }
}
