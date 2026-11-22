package tea.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tea.model.Persona
import tea.ui.theme.LocalOnSpiderPanel
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import tea.ui.theme.Voice
import tea.ui.theme.hardShadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spider's attack panel (`.attack-panel`): always dark (night palette in light and dark mode),
 * radius 28, 2dp outline, 6dp hard shadow in spider red, faint web in the top-right corner.
 * Inside, [LocalOnSpiderPanel] is true and [LocalPersona][tea.ui.theme.LocalPersona] is Spider,
 * so bubbles, name tags and headings switch to the night palette by themselves.
 */
@Composable
fun SpiderPanel(
    modifier: Modifier = Modifier,
    padding: Dp = if (TeaTheme.isCompact) 22.dp else TeaSpacing.s4,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = TeaShapes.panel
    Box(
        modifier
            .fillMaxWidth()
            .hardShadow(TeaColors.spider, shape, TeaSize.shadowLg)
            .clip(shape)
            .background(TeaColors.webBg)
            .border(TeaSize.border, TeaTheme.colors.line, shape),
    ) {
        SpiderWeb(Modifier.align(Alignment.TopEnd).size(260.dp))
        Voice(Persona.SPIDER) {
            CompositionLocalProvider(LocalOnSpiderPanel provides true, LocalContentColor provides TeaColors.webInk) {
                Column(
                    Modifier.fillMaxWidth().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
                    content = content,
                )
            }
        }
    }
}

/** The decorative web (`.attack-panel::before`): rings and spokes from the top-right corner. */
@Composable
internal fun SpiderWeb(modifier: Modifier, alpha: Float = 0.2f) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val c = Offset(size.width, 0f)
        val color = Color.White.copy(alpha = alpha)
        val stroke = Stroke(1.dp.toPx())
        for (r in listOf(18, 52, 92, 140)) {
            drawCircle(color, r.dp.toPx(), c, style = stroke)
        }
        val len = 260.dp.toPx()
        for (deg in listOf(8f, 30f, 52f, 74f)) {
            // Spokes fan out down-left from the corner, between "west" (0°) and "south" (90°).
            val a = deg / 180f * PI.toFloat()
            drawLine(color, c, Offset(c.x - len * cos(a), c.y + len * sin(a)), stroke.width)
        }
    }
}
