package tea.ui.components

import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tea.model.Mood
import tea.model.Persona
import tea.ui.theme.LocalOnSpiderPanel
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import tea.ui.theme.Voice
import tea.ui.theme.focusRing
import tea.ui.theme.hardShadow
import tea.ui.theme.personaStyle
import tea.ui.theme.pressableHardShadow

/**
 * `.card`: card colour, 2dp outline, radius 22, hard shadow. Clickable when [onClick] is set
 * (then it lifts on hover and sinks when pressed).
 * @param tilt degrees; only hero persona cards are tilted (±1 to 1.5).
 */
@Composable
fun TeaCard(
    modifier: Modifier = Modifier,
    background: Color = TeaTheme.colors.card,
    contentPadding: PaddingValues = PaddingValues(TeaSpacing.s3),
    shadow: Dp = TeaSize.shadow,
    tilt: Float = 0f,
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(TeaSpacing.s2),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TeaTheme.colors
    val shape = TeaShapes.lg
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (tilt != 0f) Modifier.rotate(tilt) else Modifier)
            .focusRing(source, shape, colors.focus)
            .then(
                if (onClick != null) Modifier.pressableHardShadow(source, colors.shadow, shape, rest = shadow)
                else Modifier.hardShadow(colors.shadow, shape, shadow),
            )
            .clip(shape)
            .background(background)
            .border(TeaSize.border, colors.line, shape)
            .then(
                if (onClick != null) Modifier.clickable(source, indication = null, role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * A card one persona owns (`.lesson-card` / `.guide`): tinted top with name tag and optional
 * persona title, then [content] below a dashed divider. Spider's card is always dark.
 * The persona is provided as [LocalPersona][tea.ui.theme.LocalPersona] to [content].
 */
@Composable
fun PersonaCard(
    persona: Persona,
    modifier: Modifier = Modifier,
    title: String? = null,
    role: String? = null,
    mood: Mood = Mood.NEUTRAL,
    tilt: Float = 0f,
    top: (@Composable ColumnScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val style = personaStyle(persona)
    val night = style.alwaysDark
    Voice(persona) {
        CompositionLocalProvider(LocalOnSpiderPanel provides (night || LocalOnSpiderPanel.current)) {
            TeaCard(
                modifier,
                background = if (night) TeaColors.webBg else TeaTheme.colors.card,
                contentPadding = PaddingValues(0.dp),
                tilt = tilt,
                verticalArrangement = Arrangement.Top,
            ) {
                Column(
                    Modifier.fillMaxWidth().background(style.surface).padding(horizontal = TeaSpacing.s3, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
                ) {
                    val full = personaFullArt(persona)
                    if (full == null) {
                        PersonaNameTag(persona, role = role, mood = mood)
                    } else {
                        // Hero header: name tag on the left, the full-body illustration on the right.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PersonaNameTag(persona, Modifier.weight(1f), role = role, mood = mood)
                            Image(painterResource(full), null, Modifier.size(if (TeaTheme.isCompact) 72.dp else 96.dp))
                        }
                    }
                    if (title != null) PersonaTitle(persona, title)
                    top?.invoke(this)
                }
                if (content != null) {
                    DashedDivider(color = if (night) TeaColors.webLine else TeaTheme.colors.rule)
                    Column(
                        Modifier.fillMaxWidth().padding(TeaSpacing.s3),
                        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
                        content = content,
                    )
                }
            }
        }
    }
}

/** 2dp dashed `rule` divider used inside cards. */
@Composable
fun DashedDivider(modifier: Modifier = Modifier, color: Color = TeaTheme.colors.rule) {
    Canvas(modifier.fillMaxWidth().height(TeaSize.border)) {
        val y = size.height / 2
        drawLine(
            color, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
}
