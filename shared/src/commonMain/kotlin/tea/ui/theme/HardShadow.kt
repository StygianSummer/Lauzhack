package tea.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neo-brutal hard shadow (`--stick`): the same [shape] filled with [color], offset by
 * ([offset], [offset]), zero blur. Put it before `clip`/`background` so it is not clipped.
 */
fun Modifier.hardShadow(color: Color, shape: Shape, offset: Dp = TeaSize.shadow): Modifier =
    if (offset <= 0.dp) this else drawBehind {
        val o = offset.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(o, o) { drawOutline(outline, color) }
    }

/**
 * Hover / press behaviour of a raised element (plan §8.1), driven by [interactionSource]
 * (share it with the `clickable`). Rest: shadow [rest]. Hover (desktop/web): lift by (-1, -1),
 * shadow [hover]. Pressed: sink by ([rest], [rest]) and no shadow. Disabled: flat, no shadow.
 * Replaces [hardShadow]; put it before `clip`/`background`.
 */
fun Modifier.pressableHardShadow(
    interactionSource: InteractionSource,
    color: Color,
    shape: Shape,
    enabled: Boolean = true,
    rest: Dp = TeaSize.shadow,
    hover: Dp = rest + 2.dp,
): Modifier = composed {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val targetShift = when {
        !enabled -> 0.dp
        pressed -> rest
        hovered -> (-1).dp
        else -> 0.dp
    }
    val targetShadow = when {
        !enabled -> 0.dp
        pressed -> 0.dp
        hovered -> hover
        else -> rest
    }
    val shift by animateDpAsState(targetShift, tween(120), label = "lift")
    val shadow by animateDpAsState(targetShadow, tween(120), label = "shadow")
    this
        .graphicsLayer {
            translationX = shift.toPx()
            translationY = shift.toPx()
        }
        .hardShadow(color, shape, shadow)
}

/**
 * Focus ring (`:focus-visible`): 3dp [color] outline, offset 3dp outside [shape], drawn while the
 * element has focus. Put it early in the chain (before `clip`).
 */
fun Modifier.focusRing(
    interactionSource: InteractionSource,
    shape: Shape,
    color: Color = TeaColors.dog,
): Modifier = composed {
    val focused by interactionSource.collectIsFocusedAsState()
    drawWithContent {
        drawContent()
        if (focused) {
            val stroke = TeaSize.focusRing.toPx()
            val gap = TeaSize.focusOffset.toPx() + stroke / 2
            val outline = shape.createOutline(
                Size(size.width + 2 * gap, size.height + 2 * gap), layoutDirection, this,
            )
            translate(-gap, -gap) { drawOutline(outline, color, style = Stroke(stroke)) }
        }
    }
}
