package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.focusRing
import tea.ui.theme.pressableHardShadow

/**
 * One big choice button (`.btn-choice` / `.btn-active`), e.g. "Guessing / Fairly sure / Certain"
 * or a verdict. Selected = ink fill, pressed in, with a check mark (not colour alone).
 * @param meter 0..1 fill of the round `.meter` gauge, or null for none.
 */
@Composable
fun OptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meter: Float? = null,
    enabled: Boolean = true,
) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = TeaShapes.md
    val fg = if (selected) colors.paper else colors.ink
    Row(
        modifier
            .defaultMinSize(minHeight = 56.dp)
            .focusRing(source, shape, colors.focus)
            .then(
                if (selected) Modifier.graphicsLayer { translationX = 3.dp.toPx(); translationY = 3.dp.toPx() }
                else Modifier.pressableHardShadow(source, colors.shadow, shape, enabled, rest = TeaSize.shadowSm, hover = 4.dp),
            )
            .clip(shape)
            .background(if (selected) colors.ink else colors.card)
            .border(TeaSize.border, colors.line, shape)
            .selectable(selected, source, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (meter != null) Meter(meter, fg)
        Text(text, Modifier.weight(1f), style = TeaTheme.type.label.copy(fontSize = TeaTheme.type.body.fontSize), color = fg)
        if (selected) TeaIcon(TeaIcons.Check, null, tint = fg, size = 20.dp)
    }
}

@Composable
private fun Meter(fill: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(38.dp).clip(TeaShapes.circle).border(TeaSize.border, color, TeaShapes.circle),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(fill.coerceIn(0f, 1f)).background(color.copy(alpha = 0.85f)))
    }
}

/**
 * A group of [OptionButton]s (`.choice-row`): side by side, stacked when compact.
 * @param meters optional fill per option (e.g. 0.33, 0.66, 1.0 for confidence).
 */
@Composable
fun OptionRow(
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    meters: List<Float>? = null,
    enabled: Boolean = true,
    stacked: Boolean = TeaTheme.isCompact,
) {
    val gap = Arrangement.spacedBy(16.dp)
    if (stacked) {
        Column(modifier.selectableGroup(), verticalArrangement = gap) {
            options.forEachIndexed { i, label ->
                OptionButton(label, selected == i, { onSelect(i) }, Modifier.fillMaxWidth(), meters?.getOrNull(i), enabled)
            }
        }
    } else {
        Row(modifier.selectableGroup().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = gap) {
            options.forEachIndexed { i, label ->
                OptionButton(label, selected == i, { onSelect(i) }, Modifier.weight(1f).fillMaxHeight(), meters?.getOrNull(i), enabled)
            }
        }
    }
}
