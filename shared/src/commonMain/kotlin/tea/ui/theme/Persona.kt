package tea.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import tea.model.Persona

/**
 * Contract C4 (plan §8.2): everything a component needs to speak in a persona's voice.
 * Never hardcode a persona colour or font in a component: get a style with [personaStyle]
 * (or `TeaTheme.personas[persona]`).
 *
 * [accent], [deep], [soft], [on] are the raw CSS tokens. [surface] and [text] are resolved for
 * the current mode: use [surface] as the tinted background (`--v-soft`) and [text] for text in the
 * persona colour on narrator surfaces (`deep` in light mode, a light tint of `accent` in dark mode).
 */
@Immutable
data class PersonaStyle(
    val persona: Persona,
    val accent: Color,
    val deep: Color,
    val soft: Color,
    /** Text on top of [accent]. */
    val on: Color,
    val font: FontFamily,
    /** Speech weight. */
    val weight: FontWeight,
    val italic: Boolean,
    /** Speech size. */
    val size: TextUnit,
    val bubble: Shape,
    /** Weight of the persona's card title (Turtle 800). */
    val titleWeight: FontWeight,
    val tracking: TextUnit,
    /** Tinted background for the current mode. Spider: always the night palette. */
    val surface: Color,
    /** Text in the persona colour, readable on the current narrator surfaces. */
    val text: Color,
    /** Spider always speaks from the dark (bubbles and panels use the web* palette). */
    val alwaysDark: Boolean,
) {
    val fontStyle: FontStyle get() = if (italic) FontStyle.Italic else FontStyle.Normal

    /** What the persona says (`.say`), optionally scaled (`.guide .say` uses 0.95). */
    fun speech(scale: Float = 1f): TextStyle = TextStyle(
        fontFamily = font,
        fontWeight = weight,
        fontStyle = fontStyle,
        fontSize = size * scale,
        letterSpacing = tracking,
        lineHeight = 1.45.em,
    )

    /** Name tag (`.char-tag .name`): persona font, upright, never italic. */
    fun name(size: TextUnit = 17.6.sp): TextStyle = TextStyle(
        fontFamily = font,
        fontWeight = weight,
        fontStyle = FontStyle.Normal,
        fontSize = size,
        lineHeight = 1.15.em,
    )

    /** A heading the persona owns (`.char-title`). Spider's is italic and larger. */
    fun title(): TextStyle = TextStyle(
        fontFamily = font,
        fontWeight = titleWeight,
        fontStyle = fontStyle,
        fontSize = if (persona == Persona.SPIDER) 30.4.sp else 25.6.sp,
        lineHeight = 1.12.em,
        letterSpacing = (-0.01).em,
    )
}

/** The four styles for the current theme. Index with `TeaTheme.personas[Persona.CAT]`. */
@Immutable
class PersonaStyles(private val styles: Map<Persona, PersonaStyle>) {
    operator fun get(persona: Persona): PersonaStyle = styles.getValue(persona)
    val all: List<PersonaStyle> get() = Persona.entries.map(::get)
}

fun personaStyles(fonts: TeaFonts, dark: Boolean): PersonaStyles {
    fun shape(topStart: Int, topEnd: Int, bottomEnd: Int, bottomStart: Int) =
        RoundedCornerShape(topStart.dp, topEnd.dp, bottomEnd.dp, bottomStart.dp)
    val cat = PersonaStyle(
        persona = Persona.CAT,
        accent = TeaColors.cat, deep = TeaColors.catDeep, soft = TeaColors.catSoft, on = TeaColors.ink,
        font = fonts.cat, weight = FontWeight.Medium, italic = false, size = 16.3.sp,
        bubble = shape(4, 16, 16, 16), titleWeight = FontWeight.Medium, tracking = (-0.01).em,
        surface = if (dark) Color(0xFF3A2A0A) else TeaColors.catSoft,
        text = if (dark) Color(0xFFFFC766) else TeaColors.catDeep,
        alwaysDark = false,
    )
    val turtle = PersonaStyle(
        persona = Persona.TURTLE,
        accent = TeaColors.turtle, deep = TeaColors.turtleDeep, soft = TeaColors.turtleSoft, on = Color.White,
        font = fonts.turtle, weight = FontWeight.Normal, italic = false, size = 17.3.sp,
        bubble = shape(28, 28, 28, 28), titleWeight = FontWeight.ExtraBold, tracking = 0.em,
        surface = if (dark) Color(0xFF0F3A2B) else TeaColors.turtleSoft,
        text = if (dark) Color(0xFF5FD9AE) else TeaColors.turtleDeep,
        alwaysDark = false,
    )
    val dog = PersonaStyle(
        persona = Persona.DOG,
        accent = TeaColors.dog, deep = TeaColors.dogDeep, soft = TeaColors.dogSoft, on = Color.White,
        font = fonts.dog, weight = FontWeight.Medium, italic = false, size = 17.sp,
        bubble = shape(20, 20, 20, 6), titleWeight = FontWeight.Medium, tracking = 0.em,
        surface = if (dark) Color(0xFF16295A) else TeaColors.dogSoft,
        text = if (dark) Color(0xFF8AB4FF) else TeaColors.dogDeep,
        alwaysDark = false,
    )
    val spider = PersonaStyle(
        persona = Persona.SPIDER,
        accent = TeaColors.spider, deep = TeaColors.spiderDeep, soft = TeaColors.spiderSoft, on = Color.White,
        font = fonts.spider, weight = FontWeight.SemiBold, italic = true, size = 20.8.sp,
        bubble = shape(2, 22, 2, 22), titleWeight = FontWeight.SemiBold, tracking = 0.em,
        surface = TeaColors.webBg,
        text = if (dark) Color(0xFFFF8A99) else TeaColors.spiderDeep,
        alwaysDark = true,
    )
    return PersonaStyles(listOf(cat, turtle, dog, spider).associateBy { it.persona })
}

internal val LocalPersonaStyles = staticCompositionLocalOf<PersonaStyles> {
    error("No TeaTheme: wrap your UI in TeaTheme { }")
}

/**
 * The persona whose voice the surrounding UI is in (`.voice-<name>` on a container), or null for
 * the narrator. Set it with [Voice]; components that take an optional persona fall back to it.
 */
val LocalPersona = compositionLocalOf<Persona?> { null }

/** True inside a [tea.ui.components.SpiderPanel]: bubbles and headings switch to the night palette. */
val LocalOnSpiderPanel = compositionLocalOf { false }

/** Style of [persona] for the current theme. */
@Composable
@ReadOnlyComposable
fun personaStyle(persona: Persona): PersonaStyle = LocalPersonaStyles.current[persona]

/** Style of the current [LocalPersona], or null for the narrator. */
@Composable
@ReadOnlyComposable
fun currentPersonaStyle(): PersonaStyle? = LocalPersona.current?.let { LocalPersonaStyles.current[it] }

/** Puts [content] in [persona]'s voice (sets [LocalPersona]). */
@Composable
fun Voice(persona: Persona, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPersona provides persona, content = content)
}

/** Lowercase key used in resource names, e.g. `persona_cat_neutral.png`. */
val Persona.key: String get() = name.lowercase()

/** Placeholder avatar letter (C/T/D/S). */
val Persona.initial: String get() = name.take(1)
