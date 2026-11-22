package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_correct
import tea.generated.resources.c_not_quite
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.focusRing

/** States of a quiz answer row (`.option-row`). */
enum class ChoiceState {
    Idle,
    Selected,
    /** Graded right (green, check mark). Also used to reveal the right answer. */
    Correct,
    /** Graded wrong (red, cross mark, struck out, not clickable). */
    Wrong,
    /** Not available (e.g. after the answer is locked). */
    Disabled,
}

/**
 * One quiz answer (`.option-row`): radio (single) or checkbox (multi) indicator + text.
 * Meaning never by colour alone: Correct has a check and the word in its semantics, Wrong has a
 * cross and a strikethrough. Correct, Wrong and Disabled rows ignore clicks.
 * @param mono URLs and domains (`.opt-mono`).
 */
@Composable
fun ChoiceRow(
    text: String,
    state: ChoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    multi: Boolean = false,
    mono: Boolean = false,
) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val shape = TeaShapes.md
    val interactive = state == ChoiceState.Idle || state == ChoiceState.Selected
    val (border, bg) = when (state) {
        ChoiceState.Idle -> (if (hovered) colors.ink else colors.rule) to colors.card
        ChoiceState.Selected -> colors.ink to colors.selectedSoft
        ChoiceState.Correct -> colors.ok to colors.okSoft
        ChoiceState.Wrong -> colors.bad to colors.badSoft
        ChoiceState.Disabled -> colors.rule to colors.card
    }
    val stateWord = when (state) {
        ChoiceState.Correct -> stringResource(Res.string.c_correct)
        ChoiceState.Wrong -> stringResource(Res.string.c_not_quite)
        else -> null
    }
    val selected = state == ChoiceState.Selected || state == ChoiceState.Correct || state == ChoiceState.Wrong
    val selection = if (multi) {
        Modifier.toggleable(selected, source, indication = null, enabled = interactive, role = Role.Checkbox) { onClick() }
    } else {
        Modifier.selectable(selected, source, indication = null, enabled = interactive, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = TeaSize.minTouch)
            .focusRing(source, shape, colors.focus)
            .clip(shape)
            .background(bg)
            .border(TeaSize.border, border, shape)
            .then(selection)
            .semantics { if (stateWord != null) stateDescription = stateWord }
            .alpha(if (state == ChoiceState.Disabled) 0.55f else 1f)
            .padding(horizontal = 16.dp, vertical = 13.6.dp),
        horizontalArrangement = Arrangement.spacedBy(13.6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceIndicator(state, multi)
        Text(
            text,
            Modifier.weight(1f),
            style = if (mono) TeaTheme.type.mono else TeaTheme.type.body,
            color = colors.ink,
            textDecoration = if (state == ChoiceState.Wrong) TextDecoration.LineThrough else null,
        )
    }
}

/** Radio dot or checkbox square, with a check / cross mark once graded. */
@Composable
private fun ChoiceIndicator(state: ChoiceState, multi: Boolean) {
    val colors = TeaTheme.colors
    val shape = if (multi) RoundedCornerShape(6.dp) else TeaShapes.circle
    val (fill, mark, tint) = when (state) {
        ChoiceState.Correct -> Triple(colors.ok, TeaIcons.Check, Color.White)
        ChoiceState.Wrong -> Triple(colors.bad, TeaIcons.Cross, Color.White)
        ChoiceState.Selected -> Triple(colors.ink, if (multi) TeaIcons.Check else null, colors.card)
        else -> Triple(colors.card, null, colors.ink)
    }
    Box(
        Modifier.size(22.dp).clip(shape).background(fill).border(TeaSize.border, if (state == ChoiceState.Idle || state == ChoiceState.Disabled) colors.line else fill, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            mark != null -> TeaIcon(mark, null, tint = tint, size = 16.dp)
            state == ChoiceState.Selected -> Box(Modifier.size(8.dp).clip(TeaShapes.circle).background(colors.card))
        }
    }
}
