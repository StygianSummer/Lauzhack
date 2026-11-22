package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_code_digit
import tea.generated.resources.c_hide_password
import tea.generated.resources.c_show_password
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.focusRing

/**
 * Narrator text input: label above, outlined field (radius 14), optional helper line and error
 * (icon + text, announced). [password] adds a show/hide toggle and hides the text by default.
 */
@Composable
fun TeaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    mono: Boolean = false,
) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    var shown by rememberSaveable { mutableStateOf(false) }
    val shape = TeaShapes.md
    val textStyle = (if (mono) TeaTheme.type.mono else TeaTheme.type.body).copy(color = colors.ink)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = TeaTheme.type.label, color = colors.ink)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            enabled = enabled,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = !password),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            singleLine = singleLine,
            visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
            interactionSource = source,
            cursorBrush = SolidColor(colors.ink),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .focusRing(source, shape, colors.focus)
                        .clip(shape)
                        .background(colors.card)
                        .border(TeaSize.border, if (error != null) colors.bad else colors.line, shape)
                        .alpha(if (enabled) 1f else 0.55f)
                        .padding(start = 14.dp, end = if (password) 2.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle, color = colors.inkSoft)
                        }
                        inner()
                    }
                    if (password) {
                        TeaIconButton(
                            if (shown) TeaIcons.EyeOff else TeaIcons.Eye,
                            stringResource(if (shown) Res.string.c_hide_password else Res.string.c_show_password),
                            onClick = { shown = !shown },
                            bordered = false,
                        )
                    }
                }
            },
        )
        if (error != null) {
            Row(
                Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeaIcon(TeaIcons.Cross, null, tint = colors.badText, size = 16.dp)
                Text(error, style = TeaTheme.type.smallStrong, color = colors.badText)
            }
        } else if (helper != null) {
            Text(helper, style = TeaTheme.type.small, color = colors.inkSoft)
        }
    }
}

/**
 * One-time code input: [length] digit boxes over a single hidden field (paste works, digits only).
 * Calls [onComplete] once all digits are entered.
 */
@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    label: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    onComplete: (String) -> Unit = {},
) {
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val description = label ?: stringResource(Res.string.c_code_digit, length)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = TeaTheme.type.label, color = colors.ink)
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(length)
                if (digits != value) {
                    onValueChange(digits)
                    if (digits.length == length) onComplete(digits)
                }
            },
            modifier = Modifier.semantics {
                contentDescription = description
                if (error != null) error(error)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = TeaTheme.type.body.copy(color = colors.card.copy(alpha = 0f)),
            cursorBrush = SolidColor(colors.card.copy(alpha = 0f)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            interactionSource = source,
            decorationBox = { inner ->
                Box {
                    Box(Modifier.size(1.dp).alpha(0f)) { inner() }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until length) {
                            val active = focused && (i == value.length || (i == length - 1 && value.length == length))
                            val shape = TeaShapes.sm
                            Box(
                                Modifier
                                    .size(width = 46.dp, height = 56.dp)
                                    .clip(shape)
                                    .background(if (i < value.length) colors.card else colors.paper)
                                    .border(
                                        if (active) 3.dp else TeaSize.border,
                                        when {
                                            error != null -> colors.bad
                                            active -> colors.focus
                                            else -> colors.line
                                        },
                                        shape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    value.getOrNull(i)?.toString().orEmpty(),
                                    style = TeaTheme.type.h3.copy(fontFamily = TeaTheme.fonts.mono, fontSize = 26.sp),
                                    color = colors.ink,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            },
        )
        if (error != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TeaIcon(TeaIcons.Cross, null, tint = colors.badText, size = 16.dp)
                Text(error, style = TeaTheme.type.smallStrong, color = colors.badText)
            }
        }
    }
}
