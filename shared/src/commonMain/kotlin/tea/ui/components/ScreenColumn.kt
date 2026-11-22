package tea.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_back
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * Top bar (`.topbar`): paper background, 2dp bottom rule, optional back button, title (display
 * font), optional trailing action (e.g. a Text-variant "Skip" button). Content limited to 720dp.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = TeaTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.paper)
            .drawBehind {
                val w = TeaSize.border.toPx()
                drawLine(colors.line, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = TeaSize.maxContentWidth).fillMaxWidth().padding(horizontal = TeaSpacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s1),
        ) {
            if (onBack != null) {
                TeaIconButton(TeaIcons.Back, stringResource(Res.string.c_back), onBack)
            } else {
                Spacer(Modifier.size(TeaSpacing.s1))
            }
            Text(
                title.orEmpty(),
                Modifier.weight(1f).semantics { heading() },
                style = TeaTheme.type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}

/**
 * Standard screen scaffold (plan §9.1): paper background, optional [TopBar], a vertically
 * scrolling column centred at max 720dp with s2 side padding, and an optional [footer]
 * (e.g. [DebugFooter]) pushed to the bottom when the content is short.
 * Show the top bar by giving a [title], [onBack] or [trailing].
 */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(TeaSpacing.s3),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TeaTheme.colors
    Column(modifier.fillMaxSize().background(colors.paper)) {
        if (title != null || onBack != null || trailing != null) {
            TopBar(title = title, onBack = onBack, trailing = trailing)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .heightIn(min = viewport),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = TeaSize.maxContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = TeaSpacing.s2)
                        .padding(top = TeaSpacing.s3, bottom = TeaSpacing.s3),
                    verticalArrangement = verticalArrangement,
                    horizontalAlignment = horizontalAlignment,
                    content = content,
                )
                if (footer != null) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.widthIn(max = TeaSize.maxContentWidth).fillMaxWidth().padding(horizontal = TeaSpacing.s2)) {
                        footer()
                    }
                }
            }
        }
    }
}
