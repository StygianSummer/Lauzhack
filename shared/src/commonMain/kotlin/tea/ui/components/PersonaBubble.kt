package tea.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.allDrawableResources
import tea.generated.resources.c_persona_cat
import tea.generated.resources.c_persona_dog
import tea.generated.resources.c_persona_spider
import tea.generated.resources.c_persona_turtle
import tea.model.Mood
import tea.model.Persona
import tea.ui.theme.LocalOnSpiderPanel
import tea.ui.theme.PersonaStyle
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.initial
import tea.ui.theme.key
import tea.ui.theme.personaStyle

/** Display name of the persona ("Cat", "Turtle", "Dog", "Spider"). */
@Composable
fun personaName(persona: Persona): String = stringResource(persona.nameRes)

private val Persona.nameRes: StringResource
    get() = when (this) {
        Persona.CAT -> Res.string.c_persona_cat
        Persona.TURTLE -> Res.string.c_persona_turtle
        Persona.DOG -> Res.string.c_persona_dog
        Persona.SPIDER -> Res.string.c_persona_spider
    }

/**
 * Art for [persona] in [mood]: `drawable/persona_<name>_<mood>.png`, else `persona_<name>_neutral.png`,
 * else null (letter placeholder). Uses the generated resource map, so dropping PNGs into
 * `composeResources/drawable/` is enough: no code change.
 */
fun personaArt(persona: Persona, mood: Mood): DrawableResource? {
    val all = Res.allDrawableResources
    return all["persona_${persona.key}_${mood.name.lowercase()}"] ?: all["persona_${persona.key}_neutral"]
}

/** Full-body illustration `drawable/persona_<name>_full.png`, or null when it is not bundled. */
fun personaFullArt(persona: Persona): DrawableResource? = Res.allDrawableResources["persona_${persona.key}_full"]

/** 48dp circle in the persona accent, 2dp border; the art if present, else the initial letter. Decorative. */
@Composable
fun PersonaAvatar(
    persona: Persona,
    modifier: Modifier = Modifier,
    mood: Mood = Mood.NEUTRAL,
    size: Dp = TeaSize.avatar,
) {
    val style = personaStyle(persona)
    val colors = TeaTheme.colors
    val onPanel = LocalOnSpiderPanel.current
    val art = personaArt(persona, mood)
    Box(
        modifier
            .size(size)
            .clip(TeaShapes.circle)
            .background(style.accent)
            .border(TeaSize.border, if (onPanel) TeaColors.webLine else colors.line, TeaShapes.circle)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(painterResource(art), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                persona.initial,
                style = TeaTheme.type.h3.copy(fontSize = (size.value * 0.46f).sp, lineHeight = (size.value * 0.5f).sp),
                color = style.on,
            )
        }
    }
}

/** `.char-tag`: avatar + name (persona font, upright) + optional role line. */
@Composable
fun PersonaNameTag(
    persona: Persona,
    modifier: Modifier = Modifier,
    role: String? = null,
    mood: Mood = Mood.NEUTRAL,
    avatarSize: Dp = TeaSize.avatar,
) {
    val style = personaStyle(persona)
    val dark = LocalOnSpiderPanel.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonaAvatar(persona, mood = mood, size = avatarSize)
        Column {
            Text(personaName(persona), style = style.name(), color = if (dark) Color.White else TeaTheme.colors.ink)
            if (role != null) {
                Text(role, style = TeaTheme.type.small, color = if (dark) TeaColors.webInk.copy(alpha = 0.85f) else TeaTheme.colors.inkSoft)
            }
        }
    }
}

/**
 * Speech bubble only (`.say`): persona font, shape and size. Spider always speaks from the dark.
 * @param onSoft tinted background (`.say.on-soft`) instead of the card colour.
 */
@Composable
fun PersonaSpeech(
    persona: Persona,
    text: String,
    modifier: Modifier = Modifier,
    onSoft: Boolean = false,
    scale: Float = 1f,
) {
    val style = personaStyle(persona)
    val (bg, border, fg) = bubbleColors(style, onSoft)
    Box(
        modifier
            .clip(style.bubble)
            .background(bg)
            .border(TeaSize.border, border, style.bubble)
            .padding(horizontal = 17.6.dp, vertical = 13.6.dp),
    ) {
        Text(text, style = style.speech(scale), color = fg)
    }
}

@Composable
private fun bubbleColors(style: PersonaStyle, onSoft: Boolean): Triple<Color, Color, Color> {
    val colors = TeaTheme.colors
    return when {
        LocalOnSpiderPanel.current -> Triple(TeaColors.webCard, TeaColors.webLine, TeaColors.webInk)
        style.alwaysDark -> Triple(TeaColors.webBg, colors.line, TeaColors.webInk)
        onSoft -> Triple(style.surface, colors.line, colors.ink)
        else -> Triple(colors.card, colors.line, colors.ink)
    }
}

/**
 * A persona speaking (plan §8.2): avatar + name + bubble in the persona's shape and font.
 * Art from `drawable/persona_<name>_<mood>.png` when present, else the letter placeholder.
 */
@Composable
fun PersonaBubble(
    persona: Persona,
    mood: Mood,
    text: String,
    modifier: Modifier = Modifier,
    role: String? = null,
    onSoft: Boolean = false,
    compactAvatar: Boolean = TeaTheme.isCompact,
) {
    val style = personaStyle(persona)
    val dark = LocalOnSpiderPanel.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.8.dp)) {
        PersonaAvatar(persona, mood = mood, size = if (compactAvatar) TeaSize.avatarSm else TeaSize.avatar)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(personaName(persona), style = style.name(15.sp), color = if (dark) Color.White else TeaTheme.colors.ink)
                if (role != null) {
                    Text(role, style = TeaTheme.type.xs, color = if (dark) TeaColors.webInk.copy(alpha = 0.85f) else TeaTheme.colors.inkSoft)
                }
            }
            PersonaSpeech(persona, text, onSoft = onSoft)
        }
    }
}

/** A heading the persona owns (`.char-title`): Turtle 800, Spider italic and larger. */
@Composable
fun PersonaTitle(persona: Persona, text: String, modifier: Modifier = Modifier) {
    val style = personaStyle(persona)
    val dark = LocalOnSpiderPanel.current
    Text(
        text,
        modifier.semantics { heading() },
        style = style.title(),
        color = if (dark) Color.White else TeaTheme.colors.ink,
    )
}

/** A persona's line without a bubble (`.voice-line` / `.say.plain`), in the persona colour. */
@Composable
fun VoiceLine(persona: Persona, text: String, modifier: Modifier = Modifier, colored: Boolean = false) {
    val style = personaStyle(persona)
    val dark = LocalOnSpiderPanel.current
    Text(
        text,
        modifier,
        style = style.speech(),
        color = when {
            dark -> TeaColors.webInk
            colored -> style.text
            else -> TeaTheme.colors.ink
        },
    )
}
