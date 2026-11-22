package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_choose
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.focusRing
import tea.ui.theme.hardShadow

/**
 * Select field: label above, an outlined button showing the choice, and a popup list below it
 * (48dp rows, selected row marked with a check). [optionLabel] turns an option into its text.
 */
@Composable
fun <T> TeaDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(Res.string.c_choose),
    enabled: Boolean = true,
) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val shape = TeaShapes.md
    val density = LocalDensity.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = TeaTheme.type.label, color = colors.ink)
        val current = selected?.let { optionLabel(it) }
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .onSizeChanged { anchorWidth = it.width }
                .focusRing(source, shape, colors.focus)
                .clip(shape)
                .background(colors.card)
                .border(TeaSize.border, colors.line, shape)
                .clickable(source, indication = null, enabled = enabled, role = Role.DropdownList) { expanded = true }
                .semantics { stateDescription = current ?: placeholder }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current ?: placeholder, Modifier.weight(1f), style = TeaTheme.type.body, color = if (current == null) colors.inkSoft else colors.ink)
            TeaIcon(TeaIcons.ChevronDown, null, tint = colors.ink)
        }
        if (expanded) {
            val gap = with(density) { 6.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { BelowAnchor(gap) },
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(with(density) { anchorWidth.toDp() })
                        .padding(end = TeaSize.shadow, bottom = TeaSize.shadow)
                        .hardShadow(colors.shadow, shape)
                        .clip(shape)
                        .background(colors.card)
                        .border(TeaSize.border, colors.line, shape)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    options.forEach { option ->
                        DropdownRow(optionLabel(option), option == selected) {
                            onSelect(option)
                            expanded = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownRow(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = TeaSize.minTouch)
            .focusRing(source, TeaShapes.sm, colors.focus)
            .background(if (hovered) colors.selectedSoft else Color.Transparent)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, Modifier.weight(1f), style = if (isSelected) TeaTheme.type.bodyStrong else TeaTheme.type.body, color = colors.ink)
        if (isSelected) TeaIcon(TeaIcons.Check, null, tint = colors.ink, size = 18.dp)
    }
}

private class BelowAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
