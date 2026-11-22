package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.focusRing

/**
 * Checkbox with its label; the whole row toggles (48dp min). Checked = ink box with a check mark.
 * @param supporting optional second line in inkSoft.
 */
@Composable
fun TeaCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    val rowShape = TeaShapes.sm
    Row(
        modifier
            .defaultMinSize(minHeight = TeaSize.minTouch)
            .focusRing(source, rowShape, colors.focus)
            .clip(rowShape)
            .toggleable(checked, source, indication = null, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.55f)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = if (supporting == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        val boxShape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .padding(top = if (supporting == null) 0.dp else 2.dp)
                .size(24.dp)
                .clip(boxShape)
                .background(if (checked) colors.ink else colors.card)
                .border(TeaSize.border, colors.line, boxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) TeaIcon(TeaIcons.Check, null, tint = colors.card, size = 18.dp)
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(label, style = TeaTheme.type.body, color = colors.ink)
            if (supporting != null) Text(supporting, style = TeaTheme.type.small, color = colors.inkSoft)
        }
    }
}
