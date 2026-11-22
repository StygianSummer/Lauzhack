package tea.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tea.generated.resources.Res
import tea.generated.resources.bricolage_grotesque_extrabold
import tea.generated.resources.bricolage_grotesque_semibold
import tea.generated.resources.cormorant_garamond_semibold
import tea.generated.resources.cormorant_garamond_semibold_italic
import tea.generated.resources.figtree_bold
import tea.generated.resources.figtree_medium
import tea.generated.resources.figtree_regular
import tea.generated.resources.figtree_semibold
import tea.generated.resources.fraunces_extrabold
import tea.generated.resources.fraunces_regular
import tea.generated.resources.fredoka_medium
import tea.generated.resources.space_grotesk_medium

/** The bundled families (plan §8.3). Read with `TeaTheme.fonts`. */
@Immutable
class TeaFonts(
    /** Bricolage Grotesque 600/800: headings, brand, step numbers, score. */
    val display: FontFamily,
    /** Figtree 400–700: body, buttons, labels, nav. */
    val body: FontFamily,
    val cat: FontFamily,
    val turtle: FontFamily,
    val dog: FontFamily,
    val spider: FontFamily,
    /** Domains and URLs only. */
    val mono: FontFamily = FontFamily.Monospace,
)

@Composable
fun rememberTeaFonts(): TeaFonts {
    val display = FontFamily(
        Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
        Font(Res.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
    )
    val body = FontFamily(
        Font(Res.font.figtree_regular, FontWeight.Normal),
        Font(Res.font.figtree_medium, FontWeight.Medium),
        Font(Res.font.figtree_semibold, FontWeight.SemiBold),
        Font(Res.font.figtree_bold, FontWeight.Bold),
    )
    val cat = FontFamily(Font(Res.font.space_grotesk_medium, FontWeight.Medium))
    val turtle = FontFamily(
        Font(Res.font.fraunces_regular, FontWeight.Normal),
        Font(Res.font.fraunces_extrabold, FontWeight.ExtraBold),
    )
    val dog = FontFamily(Font(Res.font.fredoka_medium, FontWeight.Medium))
    val spider = FontFamily(
        Font(Res.font.cormorant_garamond_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.cormorant_garamond_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    )
    return remember(display, body, cat, turtle, dog, spider) {
        TeaFonts(display, body, cat, turtle, dog, spider)
    }
}

/**
 * Narrator type scale (plan §8.1; 1rem = 16sp). Read with `TeaTheme.type`.
 * Headings use the display font, lineHeight 1.1, letterSpacing -0.015em.
 */
@Immutable
data class TeaTypography(
    /** Page title, `--t-3xl`: 56sp, 40sp compact. */
    val h1: TextStyle,
    /** Section title, `--t-2xl`: 36sp, 29sp compact. */
    val h2: TextStyle,
    /** Card / dialog title, `--t-xl` 25.6sp. */
    val h3: TextStyle,
    /** Question title, top bar title, `--t-lg` 20.8sp. */
    val title: TextStyle,
    /** Body, `--t-md` 16.8sp, lineHeight 1.6. */
    val body: TextStyle,
    val bodyStrong: TextStyle,
    /** Buttons and field labels (Figtree 700, 16sp). */
    val label: TextStyle,
    /** `--t-sm` 14.5sp: roles, captions, verdict labels. */
    val small: TextStyle,
    val smallStrong: TextStyle,
    /** `--t-xs` 13sp: fine print, debug footer. */
    val xs: TextStyle,
    /** Monospace for domains and URLs. */
    val mono: TextStyle,
    val monoSmall: TextStyle,
)

fun teaTypography(fonts: TeaFonts, compact: Boolean): TeaTypography {
    val heading = TextStyle(
        fontFamily = fonts.display,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 1.1.em,
        letterSpacing = (-0.015).em,
    )
    val body = TextStyle(fontFamily = fonts.body, fontWeight = FontWeight.Normal, fontSize = 16.8.sp, lineHeight = 1.6.em)
    return TeaTypography(
        h1 = heading.copy(fontSize = if (compact) 40.sp else 56.sp),
        h2 = heading.copy(fontSize = if (compact) 29.sp else 36.sp),
        h3 = heading.copy(fontSize = 25.6.sp, lineHeight = 1.12.em),
        title = heading.copy(fontSize = 20.8.sp, lineHeight = 1.2.em),
        body = body,
        bodyStrong = body.copy(fontWeight = FontWeight.Bold),
        label = TextStyle(fontFamily = fonts.body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 1.25.em),
        small = body.copy(fontSize = 14.5.sp, lineHeight = 1.45.em),
        smallStrong = body.copy(fontSize = 14.5.sp, lineHeight = 1.45.em, fontWeight = FontWeight.Bold),
        xs = body.copy(fontSize = 13.sp, lineHeight = 1.4.em),
        mono = TextStyle(fontFamily = fonts.mono, fontSize = 16.sp, lineHeight = 1.45.em),
        monoSmall = TextStyle(fontFamily = fonts.mono, fontSize = 13.sp, lineHeight = 1.35.em),
    )
}
