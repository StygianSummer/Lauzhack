package tea.i18n

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.app_name
import tea.generated.resources.language_title
import kotlin.test.Test
import kotlin.test.assertEquals

/** The UI language switches at runtime, in the same composition (plan §13). */
class LanguageSwitchTest {
    @Test
    fun switchesWithoutRestart() {
        val lang = mutableStateOf("en")
        var title = ""
        var fallback = ""
        val scene = ImageComposeScene(200, 200, Density(1f)) {
            ProvideLanguage(lang.value) {
                title = stringResource(Res.string.language_title)
                fallback = stringResource(Res.string.app_name) // not translated: falls back to English
            }
        }
        try {
            fun settle() = repeat(3) { scene.render(it * 100_000_000L) }
            settle()
            assertEquals("Choose your language", title)
            lang.value = "fr"
            settle()
            assertEquals("Choisissez votre langue", title)
            assertEquals("ThisIsTea", fallback)
            lang.value = "es"
            settle()
            assertEquals("Elige tu idioma", title)
            lang.value = "en"
            settle()
            assertEquals("Choose your language", title)
        } finally {
            scene.close()
        }
    }
}
