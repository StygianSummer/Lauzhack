package tea.i18n

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * On Android, Compose resources read the language from `LocalConfiguration.current.locales`,
 * not from `java.util.Locale.getDefault()`. [setPlatformLanguage] records the app language here
 * and [ProvideAndroidLanguage] (wrapped around `App()` by the Android shell) re-provides
 * LocalConfiguration with that locale, so the switch happens without restarting the activity.
 */
private val appLanguage = mutableStateOf<String?>(null)

actual fun setPlatformLanguage(lang: String) {
    if (Locale.getDefault().language != lang) Locale.setDefault(Locale.forLanguageTag(lang))
    appLanguage.value = lang
}

/** Provides a [LocalConfiguration] whose locale is the app language chosen by the learner. */
@Composable
fun ProvideAndroidLanguage(content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val lang by appLanguage
    val config = remember(base, lang) {
        val code = lang ?: return@remember base
        Configuration(base).apply { setLocales(LocaleList(Locale.forLanguageTag(code))) }
    }
    CompositionLocalProvider(LocalConfiguration provides config, content = content)
}
