package tea.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_close
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * Modal dialog (`dialog.card-dialog`): a big card with a title, a round close button, [content],
 * and up to two buttons. Esc / outside click dismiss. Use [destructive] for "Delete my account".
 */
@Composable
fun TeaDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(TeaSpacing.s2), contentAlignment = Alignment.Center) {
            TeaDialogCard(title, onDismissRequest, modifier, confirmText, onConfirm, dismissText, destructive, content)
        }
    }
}

/** The card [TeaDialog] shows; also usable inline (e.g. in the gallery or a screenshot). */
@Composable
fun TeaDialogCard(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    TeaCard(
        modifier.widthIn(max = 560.dp),
        shadow = 8.dp,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = TeaSpacing.s3, end = TeaSpacing.s3, top = TeaSpacing.s3, bottom = TeaSpacing.s3),
                verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
            ) {
                Text(
                    title,
                    Modifier.padding(end = TeaSize.minTouch).semantics { heading() },
                    style = TeaTheme.type.h3,
                    color = TeaTheme.colors.ink,
                )
                content()
                if (confirmText != null || dismissText != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = TeaSpacing.s1),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissText != null) {
                            TeaButton(dismissText, onDismissRequest, variant = TeaButtonVariant.Secondary)
                        }
                        if (confirmText != null) {
                            TeaButton(
                                confirmText,
                                { onConfirm?.invoke() },
                                variant = if (destructive) TeaButtonVariant.Danger else TeaButtonVariant.Primary,
                            )
                        }
                    }
                }
            }
            TeaIconButton(
                TeaIcons.Cross,
                stringResource(Res.string.c_close),
                onDismissRequest,
                Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
    }
}
