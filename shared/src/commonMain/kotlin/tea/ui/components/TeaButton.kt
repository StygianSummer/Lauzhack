package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.focusRing
import tea.ui.theme.pressableHardShadow

enum class TeaButtonVariant {
    /** `.btn.btn-go`: turtle green, dark text. One per screen. */
    Primary,
    /** `.btn`: card colour, outlined, hard shadow. */
    Secondary,
    /** Quiet: no outline, no shadow, underlined label. */
    Text,
    /** Primary-weight button for irreversible actions ("Delete my account"): red fill, white text. */
    Danger,
}

/**
 * Narrator button (Figtree 700). Hover lifts, press sinks into the shadow; min 48dp tall; focus ring.
 * @param small `.btn-sm` (smaller label and padding, still 48dp touch target).
 */
@Composable
fun TeaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TeaButtonVariant = TeaButtonVariant.Primary,
    enabled: Boolean = true,
    small: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val shape = TeaShapes.md
    val raised = variant != TeaButtonVariant.Text
    val (bg, fg) = when (variant) {
        TeaButtonVariant.Primary -> (if (hovered && enabled) colors.goHover else colors.go) to colors.onGo
        TeaButtonVariant.Secondary -> colors.card to colors.ink
        TeaButtonVariant.Text -> Color.Transparent to colors.ink
        TeaButtonVariant.Danger -> colors.bad to Color.White
    }
    val style = if (small) TeaTheme.type.label.copy(fontSize = TeaTheme.type.small.fontSize) else TeaTheme.type.label
    Box(
        modifier
            .defaultMinSize(minHeight = TeaSize.minTouch, minWidth = TeaSize.minTouch)
            .focusRing(source, shape, colors.focus)
            .then(
                if (raised) {
                    Modifier.pressableHardShadow(
                        source, colors.shadow, shape, enabled,
                        rest = if (small) TeaSize.shadowSm else TeaSize.shadow,
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(bg)
            .then(if (raised) Modifier.border(TeaSize.border, colors.line, shape) else Modifier)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(if (small) PaddingValues(horizontal = 16.dp, vertical = 8.dp) else PaddingValues(horizontal = 21.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) TeaIcon(icon, null, tint = fg, size = if (small) 18.dp else 20.dp)
            Text(
                text,
                style = style,
                color = fg,
                textAlign = TextAlign.Center,
                textDecoration = if (variant == TeaButtonVariant.Text) TextDecoration.Underline else null,
            )
        }
    }
}

/**
 * Round icon button (`.dialog-close`, back arrow): [size] circle with border and small shadow,
 * inside a 48dp touch target. [contentDescription] is required (icon-only).
 */
@Composable
fun TeaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = true,
    size: Dp = 40.dp,
) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = TeaShapes.circle
    Box(
        modifier
            .size(TeaSize.minTouch)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .focusRing(source, shape, colors.focus)
                .then(if (bordered) Modifier.pressableHardShadow(source, colors.shadow, shape, enabled, rest = TeaSize.shadowSm) else Modifier)
                .clip(shape)
                .background(if (bordered) colors.card else Color.Transparent)
                .then(if (bordered) Modifier.border(TeaSize.border, colors.line, shape) else Modifier)
                .alpha(if (enabled) 1f else 0.5f),
            contentAlignment = Alignment.Center,
        ) {
            TeaIcon(icon, null, tint = colors.ink, size = 20.dp)
        }
    }
}
