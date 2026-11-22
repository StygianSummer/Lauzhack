package tea.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_correct
import tea.generated.resources.c_hint
import tea.generated.resources.c_not_quite
import tea.model.Mood
import tea.model.Persona
import tea.ui.theme.TeaTheme

enum class FeedbackKind { Correct, NotQuite, Hint }

/** Icon + word verdict ("Correct" / "Not quite" / "Hint"), coloured but never colour-only. */
@Composable
fun VerdictLabel(kind: FeedbackKind, modifier: Modifier = Modifier, text: String? = null) {
    val colors = TeaTheme.colors
    val (icon, color, word) = when (kind) {
        FeedbackKind.Correct -> Triple(TeaIcons.Check, colors.okText, stringResource(Res.string.c_correct))
        FeedbackKind.NotQuite -> Triple(TeaIcons.Cross, colors.badText, stringResource(Res.string.c_not_quite))
        FeedbackKind.Hint -> Triple(TeaIcons.Hint, colors.hintText, stringResource(Res.string.c_hint))
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TeaIcon(icon, null, tint = color, size = 18.dp)
        Text(text ?: word, style = TeaTheme.type.smallStrong, color = color)
    }
}

/**
 * Feedback after an answer (`.feedback.is-ok / is-bad / is-hint`): avatar, verdict label with
 * icon and word, then the persona's line on their tinted bubble. Announced politely.
 * @param verdict overrides the default word ("Correct", "Not quite", "Hint").
 */
@Composable
fun FeedbackCard(
    kind: FeedbackKind,
    persona: Persona,
    text: String,
    modifier: Modifier = Modifier,
    mood: Mood = when (kind) {
        FeedbackKind.Correct -> Mood.HAPPY
        FeedbackKind.NotQuite -> if (persona == Persona.SPIDER) Mood.SMUG else Mood.WORRIED
        FeedbackKind.Hint -> Mood.NEUTRAL
    },
    verdict: String? = null,
) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.8.dp),
    ) {
        PersonaAvatar(persona, mood = mood, size = if (TeaTheme.isCompact) 38.dp else 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VerdictLabel(kind, text = verdict)
            PersonaSpeech(persona, text, onSoft = true)
        }
    }
}
