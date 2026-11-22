package tea.ui.screens.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.Screen
import tea.app.TeaApp
import tea.generated.resources.Res
import tea.generated.resources.practice_streak
import tea.generated.resources.summary_before
import tea.generated.resources.summary_empty
import tea.generated.resources.summary_held_out_right
import tea.generated.resources.summary_held_out_wrong
import tea.generated.resources.summary_home
import tea.generated.resources.summary_items
import tea.generated.resources.summary_leaderboard
import tea.generated.resources.summary_lesson_bonus
import tea.generated.resources.summary_no_traps
import tea.generated.resources.summary_now
import tea.generated.resources.summary_practise_again
import tea.generated.resources.summary_steps_title
import tea.generated.resources.summary_title
import tea.generated.resources.summary_trap_flag
import tea.generated.resources.summary_trap_miss
import tea.generated.resources.summary_traps_title
import tea.generated.resources.summary_xp
import tea.model.Mood
import tea.model.Persona
import tea.model.StepKey
import tea.ui.components.FeedbackCard
import tea.ui.components.FeedbackKind
import tea.ui.components.PersonaBubble
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.screens.learn.LearnKeys
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * The end of a practice section (plan §9.5): per-step bars before → now, XP earned, streak,
 * traps to watch, then [actions] (buttons). Used inline at the end of a lesson's practice section
 * and by [tea.ui.screens.learn.PracticeSummaryScreen].
 */
@Composable
fun ColumnScope.SummaryContent(summary: SummaryData, actions: @Composable ColumnScope.() -> Unit) {
    val colors = TeaTheme.colors
    SectionHeading(
        stringResource(Res.string.summary_title),
        intro = pluralStringResource(Res.plurals.summary_items, summary.itemsAnswered, summary.itemsAnswered),
    )
    summary.heldOutCorrect?.let { right ->
        FeedbackCard(
            if (right) FeedbackKind.Correct else FeedbackKind.NotQuite,
            if (right) Persona.CAT else Persona.TURTLE,
            stringResource(if (right) Res.string.summary_held_out_right else Res.string.summary_held_out_wrong),
            mood = if (right) Mood.HAPPY else Mood.NEUTRAL,
        )
    }

    TeaCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.summary_steps_title), style = TeaTheme.type.h3, color = colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Legend(colors.inkSoft, stringResource(Res.string.summary_before))
            Legend(colors.ok, stringResource(Res.string.summary_now))
        }
        StepKey.entries.forEach { step ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stepQuestion(step), style = TeaTheme.type.smallStrong, color = colors.ink)
                Bar((summary.before[step] ?: 0.0).toFloat(), colors.inkSoft)
                Bar((summary.after[step] ?: 0.0).toFloat(), colors.ok)
            }
        }
    }

    TeaCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.summary_xp, summary.xp), style = TeaTheme.type.h3, color = colors.ink)
        if (summary.lessonBonus > 0) {
            Text(stringResource(Res.string.summary_lesson_bonus, summary.lessonBonus), style = TeaTheme.type.body, color = colors.ink)
        }
        if (summary.streak > 0) {
            Text(pluralStringResource(Res.plurals.practice_streak, summary.streak, summary.streak), style = TeaTheme.type.bodyStrong, color = colors.okText)
        }
    }

    if (summary.traps.isEmpty()) {
        PersonaBubble(Persona.CAT, Mood.HAPPY, stringResource(Res.string.summary_no_traps), onSoft = true)
    } else {
        TeaCard(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.summary_traps_title), style = TeaTheme.type.h3, color = colors.ink)
            summary.traps.forEach { trap ->
                val words = trap.clue.replace('_', ' ')
                Text(
                    "• " + stringResource(if (trap.misses) Res.string.summary_trap_miss else Res.string.summary_trap_flag, words),
                    style = TeaTheme.type.body,
                    color = colors.ink,
                )
            }
        }
    }

    actions()
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(18.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, style = TeaTheme.type.xs, color = TeaTheme.colors.inkSoft)
    }
}

@Composable
private fun Bar(fill: Float, color: Color) {
    val colors = TeaTheme.colors
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.fillMaxWidth().height(10.dp).clip(shape).background(colors.track).border(1.dp, colors.line, shape)) {
        if (fill > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(fill.coerceIn(0f, 1f)).clip(shape).background(color))
    }
}

/** Secondary buttons shared by both summaries: [extra] first, then Leaderboard and Home. */
@Composable
fun SummaryLinks(app: TeaApp, extra: @Composable () -> Unit = {}) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
        extra()
        TeaButton(stringResource(Res.string.summary_leaderboard), { app.navigate(Screen.Leaderboard) }, variant = TeaButtonVariant.Secondary)
        TeaButton(stringResource(Res.string.summary_home), { app.navigateRoot(Screen.Home) }, variant = TeaButtonVariant.Secondary)
    }
}

/** The summary screen after a free practice section; reads the persisted [SummaryData]. */
@Composable
fun PracticeSummaryContent(app: TeaApp) {
    val summary = remember { app.load(LearnKeys.PRACTICE_SUMMARY, SummaryData.serializer()) }
    ScreenColumn(onBack = { app.back() }) {
        if (summary == null) {
            Text(stringResource(Res.string.summary_empty), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
            SummaryLinks(app)
        } else {
            SummaryContent(summary) {
                TeaButton(stringResource(Res.string.summary_practise_again), { app.navigate(Screen.Practice()) })
                SummaryLinks(app)
            }
        }
    }
}
