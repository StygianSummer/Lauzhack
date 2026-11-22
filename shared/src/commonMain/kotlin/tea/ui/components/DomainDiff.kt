package tea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_domain_fake
import tea.generated.resources.c_domain_real
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme

/**
 * Indexes of the characters of [fake] that differ from [real]: everything between their common
 * prefix and common suffix, so swaps and insertions read as one marked block
 * ("thisist[ae-app].example", "thisistea.example[.secure-login.example]").
 */
fun domainDiffIndexes(real: String, fake: String): Set<Int> {
    val prefix = real.commonPrefixWith(fake).length
    val maxSuffix = minOf(real.length, fake.length) - prefix
    var suffix = 0
    while (suffix < maxSuffix && real[real.length - 1 - suffix] == fake[fake.length - 1 - suffix]) suffix++
    return (prefix until fake.length - suffix).toSet()
}

/**
 * Real vs fake domain (`.domain-diff`): Spider's night card, monospace rows labelled "Real" and
 * "Fake"; the fake's differing characters are red-boxed and underlined (not colour alone).
 */
@Composable
fun DomainDiff(real: String, fake: String, modifier: Modifier = Modifier) {
    val diff = remember(real, fake) { domainDiffIndexes(real, fake) }
    val mono = TeaTheme.type.mono.copy(fontSize = 17.6.sp)
    val fakeText = remember(fake, diff) {
        buildAnnotatedString {
            fake.forEachIndexed { i, c ->
                if (i in diff) {
                    withStyle(SpanStyle(background = TeaColors.spider, color = Color.White, textDecoration = TextDecoration.Underline)) {
                        append(c)
                    }
                } else append(c)
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(TeaShapes.md)
            .background(TeaColors.webCard)
            .border(TeaSize.border, TeaColors.webLine, TeaShapes.md)
            .padding(horizontal = 19.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DiffRow(stringResource(Res.string.c_domain_real)) {
            Text(real, style = mono, color = TeaColors.webOk)
        }
        DiffRow(stringResource(Res.string.c_domain_fake)) {
            Text(fakeText, style = mono, color = TeaColors.webInk)
        }
    }
}

@Composable
private fun DiffRow(label: String, value: @Composable () -> Unit) {
    if (TeaTheme.isCompact) {
        Column {
            Text(label, style = TeaTheme.type.xs, color = TeaColors.webSoft)
            value()
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, Modifier.width(52.dp), style = TeaTheme.type.small, color = TeaColors.webSoft)
        value()
    }
}
