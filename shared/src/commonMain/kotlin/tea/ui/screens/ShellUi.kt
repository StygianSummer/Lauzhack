package tea.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.Res
import tea.generated.resources.shell_step_ask
import tea.generated.resources.shell_step_emotion
import tea.generated.resources.shell_step_verify
import tea.generated.resources.shell_step_who
import tea.model.StepKey
import tea.ui.components.TeaIcon
import tea.ui.components.TeaIcons
import tea.ui.theme.TeaSpacing
import tea.ui.theme.LocalOnSpiderPanel
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaTheme

/*
 * Small building blocks shared by the app-shell screens (onboarding, home, settings, about,
 * leaderboard, reveal). Components that belong to the design system live in tea.ui.components.
 */

/** The four step questions (plan §1.1), in order. */
val StepKey.questionRes: StringResource
    get() = when (this) {
        StepKey.WHO -> Res.string.shell_step_who
        StepKey.EMOTION -> Res.string.shell_step_emotion
        StepKey.ASK -> Res.string.shell_step_ask
        StepKey.VERIFY -> Res.string.shell_step_verify
    }

/** Error line (cross icon + text), announced to screen readers. */
@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeaIcon(TeaIcons.Cross, null, tint = TeaTheme.colors.badText, size = 16.dp)
        Text(text, style = TeaTheme.type.smallStrong, color = TeaTheme.colors.badText)
    }
}

/** Calm confirmation line (check icon + text). */
@Composable
fun OkLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeaIcon(TeaIcons.Check, null, tint = TeaTheme.colors.okText, size = 16.dp)
        Text(text, style = TeaTheme.type.smallStrong, color = TeaTheme.colors.okText)
    }
}

/** A bullet list of short lines. */
@Composable
fun BulletList(lines: List<String>, modifier: Modifier = Modifier) {
    val spider = LocalOnSpiderPanel.current
    val ink = if (spider) TeaColors.webInk else TeaTheme.colors.ink
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.padding(top = 10.dp).size(6.dp).clip(CircleShape).background(if (spider) TeaColors.spider else TeaTheme.colors.ink),
                )
                Text(line, Modifier.weight(1f), style = TeaTheme.type.body, color = ink)
            }
        }
    }
}

/** The four steps, numbered, each with an optional answer under it. */
@Composable
fun FourStepsList(answers: Map<StepKey, String> = emptyMap(), modifier: Modifier = Modifier) {
    val spider = LocalOnSpiderPanel.current
    val ink = if (spider) TeaColors.webInk else TeaTheme.colors.ink
    val softInk = if (spider) TeaColors.webInk.copy(alpha = 0.85f) else TeaTheme.colors.inkSoft
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
        StepKey.entries.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (spider) TeaColors.spider else TeaTheme.colors.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Text((i + 1).toString(), style = TeaTheme.type.smallStrong, color = if (spider) Color.White else TeaTheme.colors.paper)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(step.questionRes), style = TeaTheme.type.bodyStrong, color = ink)
                    answers[step]?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = TeaTheme.type.body, color = softInk)
                    }
                }
            }
        }
    }
}

/** Navigates to [screen]; Home and the placement intro become the new root (no way back into signup). */
fun AppState.goNext(screen: Screen) {
    if (screen == Screen.Home || screen == Screen.PlacementIntro) navigateRoot(screen) else navigate(screen)
}
