package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_offline
import tea.generated.resources.c_try_again
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme

enum class BannerKind { Info, Offline }

/**
 * Calm inline notice: soft blue, outlined, no shadow, icon + text, optional text action.
 * Not an error: nothing is lost when offline.
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    kind: BannerKind = BannerKind.Info,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = TeaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(TeaShapes.md)
            .background(colors.selectedSoft)
            .border(TeaSize.border, colors.line, TeaShapes.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeaIcon(if (kind == BannerKind.Offline) TeaIcons.CloudOff else TeaIcons.Info, null, tint = colors.ink)
        Text(text, Modifier.weight(1f).padding(vertical = 6.dp), style = TeaTheme.type.small, color = colors.ink)
        if (actionText != null && onAction != null) {
            TeaButton(actionText, onAction, variant = TeaButtonVariant.Text, small = true)
        }
    }
}

/** "You're offline" banner with the standard copy and an optional retry. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Banner(
        stringResource(Res.string.c_offline),
        modifier,
        kind = BannerKind.Offline,
        actionText = if (onRetry != null) stringResource(Res.string.c_try_again) else null,
        onAction = onRetry,
    )
}
