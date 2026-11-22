package tea.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small stroke icons drawn as vectors (no icon font, no emoji: both are unreliable on the wasm
 * canvas). 24x24 viewport, 2.5 stroke, round caps.
 */
object TeaIcons {
    val Check: ImageVector by lazy { stroke("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }
    val Cross: ImageVector by lazy {
        stroke("cross") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    }
    val Back: ImageVector by lazy {
        stroke("back") { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
    }
    val ChevronDown: ImageVector by lazy { stroke("chevron") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } }
    val Eye: ImageVector by lazy {
        stroke("eye") {
            moveTo(2f, 12f); curveTo(5f, 6.5f, 19f, 6.5f, 22f, 12f); curveTo(19f, 17.5f, 5f, 17.5f, 2f, 12f); close()
            circle(12f, 12f, 3f)
        }
    }
    val EyeOff: ImageVector by lazy {
        stroke("eye-off") {
            moveTo(2f, 12f); curveTo(5f, 6.5f, 19f, 6.5f, 22f, 12f); curveTo(19f, 17.5f, 5f, 17.5f, 2f, 12f); close()
            circle(12f, 12f, 3f)
            moveTo(4f, 4f); lineTo(20f, 20f)
        }
    }
    val Info: ImageVector by lazy {
        stroke("info") { circle(12f, 12f, 9.5f); moveTo(12f, 11f); lineTo(12f, 16.5f); moveTo(12f, 7.6f); lineTo(12f, 7.7f) }
    }
    /** Lightbulb-free hint mark: a question mark in a circle. */
    val Hint: ImageVector by lazy {
        stroke("hint") {
            circle(12f, 12f, 9.5f)
            moveTo(9.3f, 9.5f); curveTo(9.3f, 6.5f, 14.7f, 6.5f, 14.7f, 9.5f); curveTo(14.7f, 11.5f, 12f, 11.5f, 12f, 13.8f)
            moveTo(12f, 16.9f); lineTo(12f, 17f)
        }
    }
    val CloudOff: ImageVector by lazy {
        stroke("cloud-off") {
            moveTo(7.5f, 18.5f); lineTo(17f, 18.5f)
            curveTo(19.8f, 18.5f, 21.5f, 16.6f, 21.5f, 14.4f); curveTo(21.5f, 12.2f, 19.8f, 10.5f, 17.6f, 10.5f)
            curveTo(17f, 7.5f, 14.6f, 5.5f, 11.7f, 5.5f); curveTo(8.6f, 5.5f, 6.2f, 7.8f, 6f, 10.8f)
            curveTo(3.9f, 11.2f, 2.5f, 12.9f, 2.5f, 14.8f); curveTo(2.5f, 16.9f, 4.4f, 18.5f, 7.5f, 18.5f)
            moveTo(3f, 3f); lineTo(21f, 21f)
        }
    }
    val Document: ImageVector by lazy {
        stroke("document") {
            moveTo(14f, 3f); lineTo(6f, 3f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(18f, 7f); close()
            moveTo(14f, 3f); lineTo(14f, 7f); lineTo(18f, 7f)
        }
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        close()
    }

    private fun stroke(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathData(block),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}

/** An icon tinted with [tint]. Pass a [contentDescription] unless a word next to it says the same. */
@Composable
fun TeaIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}
