package tea.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_step_current
import tea.generated.resources.c_step_done
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme

/**
 * Outlined pill progress bar. [progress] 0..1. Optional [label] above on the left and
 * [trailingLabel] on the right (e.g. "3 of 10").
 */
@Composable
fun TeaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    trailingLabel: String? = null,
    color: Color = TeaTheme.colors.ok,
) {
    val colors = TeaTheme.colors
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null || trailingLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Text(label.orEmpty(), Modifier.weight(1f), style = TeaTheme.type.smallStrong, color = colors.ink)
                if (trailingLabel != null) Text(trailingLabel, style = TeaTheme.type.small, color = colors.inkSoft)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(TeaShapes.pill)
                .background(colors.card)
                .border(TeaSize.border, colors.line, TeaShapes.pill)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        ) {
            if (p > 0f) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(p).clip(TeaShapes.pill).background(color)
                        .border(TeaSize.border, colors.line, TeaShapes.pill),
                )
            }
        }
    }
}

/**
 * Labelled mini step bar (`.topbar .steps`): one segment per step with a 5dp bar under the label.
 * Steps before [current] are done (turtle bar + check mark), [current] has an ink bar and bold
 * label, later ones are muted. Meaning carried by the check mark and semantics too.
 */
@Composable
fun StepBar(
    steps: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val colors = TeaTheme.colors
    val doneWord = stringResource(Res.string.c_step_done)
    val currentWord = stringResource(Res.string.c_step_current)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.4.dp)) {
        steps.forEachIndexed { i, label ->
            val done = i < current
            val isCurrent = i == current
            Column(
                Modifier.weight(1f).semantics {
                    when {
                        done -> stateDescription = doneWord
                        isCurrent -> stateDescription = currentWord
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (done) TeaIcon(TeaIcons.Check, null, tint = colors.okText, size = 14.dp)
                    Text(
                        label,
                        style = TeaTheme.type.xs.copy(fontWeight = TeaTheme.type.smallStrong.fontWeight),
                        color = if (done || isCurrent) colors.ink else colors.inkSoft,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(
                        when {
                            done -> colors.ok
                            isCurrent -> colors.ink
                            else -> colors.track
                        },
                    ),
                )
            }
        }
    }
}
