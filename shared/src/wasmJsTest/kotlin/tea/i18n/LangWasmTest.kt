package tea.i18n

import androidx.compose.ui.text.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** On the web, Compose resources read the locale from navigator.language(s): our override must win. */
class LangWasmTest {
    @Test
    fun overridesTheLocaleComposeReads() {
        setPlatformLanguage("fr")
        assertEquals("fr", Locale.current.language)
        setPlatformLanguage("es")
        assertEquals("es", Locale.current.language)
        setPlatformLanguage("en")
        assertEquals("en", Locale.current.language)
    }
}
