package tea.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * Small inkSoft line at the bottom of every screen (plan §9.1), e.g.
 * `DebugFooter("api.example", "online", "build 1a2b3c")`. Items are joined with " · ".
 */
@Composable
fun DebugFooter(vararg items: String, modifier: Modifier = Modifier) {
    Text(
        items.filter { it.isNotBlank() }.joinToString(" · "),
        modifier.fillMaxWidth().padding(vertical = TeaSpacing.s2),
        style = TeaTheme.type.xs.copy(fontFamily = TeaTheme.fonts.mono),
        color = TeaTheme.colors.inkSoft,
        textAlign = TextAlign.Center,
    )
}
