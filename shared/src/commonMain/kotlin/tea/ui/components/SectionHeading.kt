package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tea.ui.theme.LocalOnSpiderPanel
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * Narrator section heading (`.section-head`): optional numbered circle, display-font title,
 * optional intro line in inkSoft. Inside a SpiderPanel the circle turns spider red and text light.
 * @param large page-level title (h1 size) instead of a section title (h2).
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    number: Int? = null,
    intro: String? = null,
    large: Boolean = false,
) {
    val colors = TeaTheme.colors
    val night = LocalOnSpiderPanel.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            if (number != null) {
                val fill = if (night) TeaColors.spider else colors.ink
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(TeaShapes.circle)
                        .background(fill)
                        .border(TeaSize.border, if (night) TeaColors.spider else colors.line, TeaShapes.circle)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        number.toString(),
                        style = TeaTheme.type.title.copy(fontSize = 18.sp),
                        color = if (night) Color.White else colors.paper,
                    )
                }
            }
            Text(
                text,
                Modifier.semantics { heading() },
                style = if (large) TeaTheme.type.h1 else TeaTheme.type.h2,
                color = if (night) Color.White else colors.ink,
            )
        }
        if (intro != null) {
            Text(
                intro,
                Modifier.widthIn(max = 560.dp),
                style = TeaTheme.type.body,
                color = if (night) TeaColors.webInk.copy(alpha = 0.85f) else colors.inkSoft,
            )
        }
    }
}
