package tea.ui.screens.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.ai.RulesGrader
import tea.app.TeaApp
import tea.content.Placeholders
import tea.content.Resource
import tea.engine.ItemMode
import tea.engine.PracticeAnswer
import tea.engine.PracticeOutcome
import tea.generated.resources.Res
import tea.generated.resources.practice_dog_generic
import tea.generated.resources.practice_dog_intro
import tea.generated.resources.practice_empty
import tea.generated.resources.practice_final_banner
import tea.generated.resources.practice_final_check
import tea.generated.resources.practice_four_steps_title
import tea.generated.resources.practice_gentle_correct
import tea.generated.resources.practice_gentle_line
import tea.generated.resources.practice_gentle_not_quite
import tea.generated.resources.practice_link_unchecked
import tea.generated.resources.practice_next
import tea.generated.resources.practice_open
import tea.generated.resources.practice_outcome_neutral
import tea.generated.resources.practice_outcome_safe
import tea.generated.resources.practice_outcome_unsafe
import tea.generated.resources.practice_progress
import tea.generated.resources.practice_see_summary
import tea.generated.resources.practice_streak
import tea.generated.resources.practice_was_legit
import tea.generated.resources.practice_was_scam
import tea.generated.resources.practice_why_missed
import tea.generated.resources.practice_why_ticks_title
import tea.generated.resources.practice_why_touched
import tea.generated.resources.practice_xp
import tea.generated.resources.practice_xp_hint
import tea.generated.resources.practice_you_chose
import tea.model.Item
import tea.model.Mood
import tea.model.Persona
import tea.model.StepKey
import tea.model.Support
import tea.ui.components.Banner
import tea.ui.components.EmailView
import tea.ui.components.FeedbackCard
import tea.ui.components.FeedbackKind
import tea.ui.components.PersonaBubble
import tea.ui.components.ScreenColumn
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaIcon
import tea.ui.components.TeaIcons
import tea.ui.components.VoiceLine
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * The practice loop UI (plan §9.5) around a [PracticeController]: the top bar (per-step mini bars,
 * XP, streak), the item ([ItemRenderer] in FULL mode), then the feedback card, then the next item.
 * When the section is over, [finished] renders the end (summary inline in a lesson, navigation for
 * free practice).
 *
 * @param header pinned above the practice top bar (the lesson's section progress row).
 */
@Composable
fun PracticeRunner(
    app: TeaApp,
    controller: PracticeController,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    intro: (@Composable ColumnScope.() -> Unit)? = null,
    finished: @Composable ColumnScope.(SummaryData) -> Unit,
) {
    val scroll = rememberScrollState()
    val current = controller.current
    val outcome = controller.lastOutcome
    LaunchedEffect(current?.item?.id, outcome != null) { scroll.scrollTo(0) }

    Column(modifier.fillMaxSize().background(TeaTheme.colors.paper)) {
        header()
        if (!controller.finished) PracticeTopBar(app, controller)
        ScreenColumn(Modifier.weight(1f), scrollState = scroll) {
            val summary = controller.summary
            when {
                controller.finished && summary != null -> finished(summary)
                current != null && outcome != null -> {
                    val item = rememberFilled(app, current.item)
                    val answer = controller.lastAnswer
                    FeedbackView(app, item, answer, outcome)
                    TeaButton(
                        stringResource(if (current.heldOut) Res.string.practice_see_summary else Res.string.practice_next),
                        { controller.advance() },
                    )
                    item.email?.let { email ->
                        EmailView(email, item.hotspots, tappable = false, highlighted = redFlagRegions(item), onTap = {}, now = app.now())
                    }
                }
                current != null -> {
                    if (intro != null && controller.run.session.shownCount == 0) intro()
                    val item = rememberFilled(app, current.item)
                    if (current.heldOut) Banner(stringResource(Res.string.practice_final_banner))
                    ItemRenderer(
                        item = item,
                        mode = ItemMode.FULL,
                        support = current.support,
                        onAnswer = { controller.submit(it.toPractice(current.support)) },
                        clock = app::now,
                    )
                }
                else -> Text(stringResource(Res.string.practice_empty), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
            }
        }
    }
}

/** [item] with `{{first_name}}`, `{{email}}`… filled from the profile (computed once per item). */
@Composable
fun rememberFilled(app: TeaApp, item: Item): Item {
    val profile by app.profile.collectAsState()
    val email by app.email.collectAsState()
    return remember(item, profile.firstName, email) { Placeholders.fill(item, profile, email) }
}

// ---- top bar -----------------------------------------------------------------------------------

/**
 * Per-step mini bars (p per step, labelled, with the support word so the fading is visible),
 * the XP counter (long-press shows the raw p per step in debug builds) and the streak.
 */
@Composable
fun PracticeTopBar(app: TeaApp, controller: PracticeController, modifier: Modifier = Modifier) {
    val colors = TeaTheme.colors
    val xp by app.xp.collectAsState()
    var raw by remember { mutableStateOf(false) }
    val stepP = controller.stepP
    val support = controller.stepSupport
    val current = controller.current
    Box(
        modifier.fillMaxWidth().background(colors.paper).drawBehind {
            val w = TeaSize.border.toPx()
            drawLine(colors.line, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
        }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = TeaSize.maxContentWidth).fillMaxWidth().padding(horizontal = TeaSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
                Text(
                    if (current?.heldOut == true) stringResource(Res.string.practice_final_check)
                    else stringResource(
                        Res.string.practice_progress,
                        (controller.practiceCount + if (controller.lastOutcome == null) 1 else 0).coerceIn(1, controller.count),
                        controller.count,
                    ),
                    Modifier.weight(1f),
                    style = TeaTheme.type.smallStrong,
                    color = colors.ink,
                )
                val streak = controller.streakNow
                if (streak > 0) Chip(pluralStringResource(Res.plurals.practice_streak, streak, streak))
                val hint = if (app.debug) stringResource(Res.string.practice_xp_hint) else null
                Chip(
                    stringResource(Res.string.practice_xp, xp),
                    Modifier.semantics { if (hint != null) contentDescription = hint }
                        .pointerInput(app.debug) { detectTapGestures(onLongPress = { if (app.debug) raw = !raw }) },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepKey.entries.forEach { step ->
                    val p = stepP[step] ?: 0.0
                    val s = support[step] ?: Support.WORKED
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stepLabel(step), Modifier.weight(1f), style = TeaTheme.type.xs.copy(fontWeight = TeaTheme.type.smallStrong.fontWeight), color = colors.ink, maxLines = 1)
                            if (s == Support.FADED) TeaIcon(TeaIcons.Check, null, tint = colors.okText, size = 13.dp)
                        }
                        MiniBar(p.toFloat())
                        Text(
                            if (raw) "p=" + (kotlin.math.round(p * 100) / 100).toString() else supportLabel(s),
                            style = TeaTheme.type.xs,
                            color = colors.inkSoft,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBar(fill: Float, color: androidx.compose.ui.graphics.Color = TeaTheme.colors.ok) {
    val colors = TeaTheme.colors
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.track)
            .border(1.dp, colors.line, RoundedCornerShape(4.dp)),
    ) {
        if (fill > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(fill.coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier) {
    val colors = TeaTheme.colors
    Text(
        text,
        modifier.clip(TeaShapes.pill).background(colors.card).border(TeaSize.border, colors.line, TeaShapes.pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = TeaTheme.type.smallStrong,
        color = colors.ink,
    )
}

// ---- feedback ----------------------------------------------------------------------------------

/**
 * Feedback after an answer (plan §9.5 step 4): Correct / Not quite without blame, Spider's reveal
 * (scam) or Turtle's explanation (legit), the consequence of the chosen action, the four steps
 * filled in, the "Why?" ticks, and Dog's "what to do now" card after an unsafe action.
 */
@Composable
fun FeedbackView(app: TeaApp, item: Item, answer: PracticeAnswer?, outcome: PracticeOutcome) {
    val colors = TeaTheme.colors
    val profile by app.profile.collectAsState()
    val gentle = profile.gentleTone
    val kind = if (outcome.verdictCorrect) FeedbackKind.Correct else FeedbackKind.NotQuite
    val persona = if (item.isScam) Persona.SPIDER else Persona.TURTLE
    val line = item.attackerReveal
        ?: stringResource(if (item.isScam) Res.string.practice_was_scam else Res.string.practice_was_legit)
    FeedbackCard(
        kind,
        persona,
        line,
        verdict = if (gentle) stringResource(if (outcome.verdictCorrect) Res.string.practice_gentle_correct else Res.string.practice_gentle_not_quite) else null,
        mood = if (gentle && persona == Persona.SPIDER) Mood.NEUTRAL else if (outcome.verdictCorrect) Mood.HAPPY else if (persona == Persona.SPIDER) Mood.SMUG else Mood.WORRIED,
    )
    if (gentle && !outcome.verdictCorrect) {
        PersonaBubble(Persona.TURTLE, Mood.NEUTRAL, stringResource(Res.string.practice_gentle_line), onSoft = true)
    }

    val action = item.actions.firstOrNull { it.id == answer?.actionId }
    if (action != null) {
        TeaCard(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.practice_you_chose, action.label), style = TeaTheme.type.bodyStrong, color = colors.ink)
            val (icon, tint, word) = when {
                action.isSafe -> Triple(TeaIcons.Check, colors.okText, stringResource(Res.string.practice_outcome_safe))
                action.isUnsafe -> Triple(TeaIcons.Cross, colors.badText, stringResource(Res.string.practice_outcome_unsafe))
                else -> Triple(TeaIcons.Info, colors.inkSoft, stringResource(Res.string.practice_outcome_neutral))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeaIcon(icon, null, tint = tint, size = 18.dp)
                Text(word, style = TeaTheme.type.smallStrong, color = tint)
            }
            Text(action.consequence, style = TeaTheme.type.body, color = colors.ink)
        }
        if (action.isUnsafe) DogCard(app, item)
    }

    TeaCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.practice_four_steps_title), style = TeaTheme.type.h3, color = colors.ink)
        StepKey.entries.forEach { step ->
            val content = item.steps[step] ?: return@forEach
            val right = outcome.stepCorrect[step]
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepNumber(step.ordinal + 1)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stepQuestion(step), style = TeaTheme.type.smallStrong, color = colors.ink)
                        if (right == true) TeaIcon(TeaIcons.Check, null, tint = colors.okText, size = 16.dp)
                    }
                    Text(content.answer, style = TeaTheme.type.small, color = colors.ink)
                }
            }
        }
    }

    val text = answer?.freeText
    if (!text.isNullOrBlank()) {
        val ticks = remember(item.id, text) { RulesGrader().gradeNow(item, text) }
        TeaCard(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.practice_why_ticks_title), style = TeaTheme.type.bodyStrong, color = colors.ink)
            Text("“$text”", style = TeaTheme.type.small, color = colors.inkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StepKey.entries.forEach { step ->
                    val ok = ticks[step] == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TeaIcon(if (ok) TeaIcons.Check else TeaIcons.Cross, null, tint = if (ok) colors.okText else colors.inkSoft, size = 16.dp)
                        Text(
                            stepLabel(step) + ": " + stringResource(if (ok) Res.string.practice_why_touched else Res.string.practice_why_missed),
                            style = TeaTheme.type.small,
                            color = colors.ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepNumber(n: Int) {
    val colors = TeaTheme.colors
    Box(
        Modifier.padding(top = 2.dp).clip(TeaShapes.circle).background(colors.ink).padding(horizontal = 8.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(n.toString(), style = TeaTheme.type.xs.copy(fontWeight = TeaTheme.type.smallStrong.fontWeight), color = colors.paper)
    }
}

/**
 * Dog's "what to do now" card (plan §9.5): resources for `resourceAsk ?: slots.ask` in the
 * learner's country, then international ones. Only verified links, except in debug builds where
 * all are shown with a "link not yet checked" label. Without a matching resource Dog gives the
 * general advice.
 */
@Composable
fun DogCard(app: TeaApp, item: Item) {
    val profile by app.profile.collectAsState()
    val ask = item.resourceAsk ?: item.slots.ask
    val resources by produceState<List<Resource>>(emptyList(), ask, profile.country) {
        value = runCatching { app.content.resourcesFor(ask, profile.country, verifiedOnly = !app.debug) }.getOrDefault(emptyList()).take(2)
    }
    if (resources.isEmpty()) {
        PersonaBubble(Persona.DOG, Mood.WORRIED, stringResource(Res.string.practice_dog_generic), onSoft = true)
        return
    }
    PersonaBubble(Persona.DOG, Mood.NEUTRAL, stringResource(Res.string.practice_dog_intro), onSoft = true)
    resources.forEach { r -> ResourceRow(app, r) }
}

@Composable
private fun ResourceRow(app: TeaApp, r: Resource) {
    val colors = TeaTheme.colors
    TeaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(TeaSpacing.s2)) {
        Text(r.name, style = TeaTheme.type.bodyStrong, color = colors.ink)
        if (r.purpose.isNotBlank()) VoiceLine(Persona.DOG, r.purpose)
        if (!r.verified) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TeaIcon(TeaIcons.Info, null, tint = colors.hintText, size = 16.dp)
                Text(stringResource(Res.string.practice_link_unchecked), style = TeaTheme.type.xs, color = colors.hintText)
            }
        }
        if (r.hasUrl) {
            TeaButton(stringResource(Res.string.practice_open), { app.openUrl(r.url) }, variant = TeaButtonVariant.Secondary, small = true)
        }
    }
}
