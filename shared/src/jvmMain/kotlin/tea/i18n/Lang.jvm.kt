package tea.i18n

import java.util.Locale

actual fun setPlatformLanguage(lang: String) {
    if (Locale.getDefault().language != lang) Locale.setDefault(Locale.forLanguageTag(lang))
}
