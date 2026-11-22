package tea.ui.screens.practice

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.engine.ItemMode
import tea.engine.PracticeAnswer
import tea.generated.resources.Res
import tea.generated.resources.item_action_question
import tea.generated.resources.item_check
import tea.generated.resources.item_confidence_certain
import tea.generated.resources.item_confidence_fairly_sure
import tea.generated.resources.item_confidence_guessing
import tea.generated.resources.item_continue
import tea.generated.resources.item_dont_know
import tea.generated.resources.item_emotion_question
import tea.generated.resources.item_how_sure
import tea.generated.resources.item_image_ai
import tea.generated.resources.item_image_label
import tea.generated.resources.item_image_missing
import tea.generated.resources.item_image_question
import tea.generated.resources.item_image_real
import tea.generated.resources.item_link_question
import tea.generated.resources.item_link_tip
import tea.generated.resources.item_news_dont
import tea.generated.resources.item_news_forward
import tea.generated.resources.item_news_label
import tea.generated.resources.item_news_question
import tea.generated.resources.item_tap_count
import tea.generated.resources.item_tap_done
import tea.generated.resources.item_tap_question
import tea.generated.resources.item_verdict_fine
import tea.generated.resources.item_verdict_not_sure
import tea.generated.resources.item_verdict_question
import tea.generated.resources.item_verdict_trick
import tea.generated.resources.practice_answer_was
import tea.generated.resources.practice_hints_label
import tea.generated.resources.practice_need_hint
import tea.generated.resources.practice_next_step
import tea.generated.resources.practice_try_again
import tea.generated.resources.practice_verdict_title
import tea.generated.resources.practice_why_label
import tea.generated.resources.practice_why_placeholder
import tea.generated.resources.practice_worked_got_it
import tea.model.AttemptAnswers
import tea.model.Confidence
import tea.model.Item
import tea.model.ItemAction
import tea.model.Mood
import tea.model.Persona
import tea.model.StepKey
import tea.model.Support
import tea.model.Verdict
import tea.ui.components.ChoiceRow
import tea.ui.components.ChoiceState
import tea.ui.components.EmailView
import tea.ui.components.OptionRow
import tea.ui.components.PersonaBubble
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaIcon
import tea.ui.components.TeaIcons
import tea.ui.components.TeaTextField
import tea.ui.components.emailRegions
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * What the learner answered on one item, as collected by [ItemRenderer].
 * - [answers]: the field for the item's mode (placement), or verdict + action + first step choices +
 *   free text (FULL).
 * - [confidence]: "How sure are you?" (placement only; null after "I don't know yet").
 * - [ms]: time on the item, measured with the app clock. Never shown.
 * - [dontKnow]: the learner pressed "I don't know yet" (the mode's field is null).
 * - [hintsUsed]: FULL mode, steps where "Need a hint?" was opened.
 */
data class ItemAnswer(
    val answers: AttemptAnswers,
    val confidence: Confidence? = null,
    val ms: Long,
    val dontKnow: Boolean = false,
    val hintsUsed: Set<StepKey> = emptySet(),
) {
    /** The engine's view of a FULL answer. */
    fun toPractice(support: Map<StepKey, Support>): PracticeAnswer = PracticeAnswer(
        support = support,
        verdict = answers.verdict,
        actionId = answers.actionId,
        ms = ms,
        stepChoices = answers.stepChoices,
        hintsUsed = hintsUsed,
        confidence = confidence,
        freeText = answers.freeText,
    )
}

/** Stable shuffle of an item's actions (same order for the same item on every recomposition). */
fun shuffledActions(item: Item): List<ItemAction> = item.actions.shuffled(Random(item.id.hashCode()))

/** Selection state of one placement-mode item (all modes except FULL). */
@Stable
class SingleItemUi(val item: Item, val mode: ItemMode) {
    /** Option index: VERDICT 0 = trick, 1 = fine; LINK / EMOTION: option index; ACTION: index in [actions];
     *  NEWS 0 = forward, 1 = don't; IMAGE 0 = real, 1 = AI. */
    var choice: Int? by mutableStateOf(null)
    var tapped: Set<String> by mutableStateOf(emptySet())
    var confidence: Confidence? by mutableStateOf(null)
    val actions: List<ItemAction> = shuffledActions(item)

    val answered: Boolean get() = if (mode == ItemMode.TAP_HOTSPOT) tapped.isNotEmpty() else choice != null

    fun tap(id: String) {
        tapped = if (id in tapped) tapped - id else tapped + id
    }

    fun answers(): AttemptAnswers {
        val c = choice
        return when (mode) {
            ItemMode.VERDICT -> AttemptAnswers(verdict = c?.let { if (it == 0) Verdict.TRICK else Verdict.FINE })
            ItemMode.TAP_HOTSPOT -> AttemptAnswers(hotspotsTapped = tapped.toList())
            ItemMode.LINK -> AttemptAnswers(linkChoice = c)
            ItemMode.EMOTION -> AttemptAnswers(stepChoices = mapOf(StepKey.EMOTION to c))
            ItemMode.ACTION -> AttemptAnswers(actionId = c?.let { actions.getOrNull(it)?.id })
            ItemMode.NEWS -> AttemptAnswers(newsForward = c?.let { it == 0 })
            ItemMode.IMAGE -> AttemptAnswers(imageAi = c?.let { it == 1 })
            ItemMode.FULL -> AttemptAnswers()
        }
    }

    /** "I don't know yet": the mode's field is null (a null choice for single-choice modes). */
    fun dontKnowAnswers(): AttemptAnswers = when (mode) {
        ItemMode.EMOTION -> AttemptAnswers(stepChoices = mapOf(StepKey.EMOTION to null))
        else -> AttemptAnswers()
    }
}

/**
 * State of a FULL (practice) item: the four steps per support level, then verdict, action and
 * the optional "Why?". Steps at WORKED and PROMPTED are shown one after the other (each must be
 * acknowledged / solved); steps at HINT only offer "Need a hint?"; FADED steps show nothing.
 */
@Stable
class FullItemUi(val item: Item, val support: Map<StepKey, Support>) {
    /** Steps the learner goes through in order (WORKED or PROMPTED, with content on the item). */
    val gates: List<StepKey> = StepKey.entries.filter {
        item.steps[it] != null && (support[it] == Support.WORKED || support[it] == Support.PROMPTED)
    }
    val hintSteps: List<StepKey> = StepKey.entries.filter { item.steps[it] != null && support[it] == Support.HINT }
    val actions: List<ItemAction> = shuffledActions(item)

    var gateIndex: Int by mutableStateOf(0)
        private set
    var hintsOpened: Set<StepKey> by mutableStateOf(emptySet())
        private set
    private val first = mutableStateMapOf<StepKey, Int>()
    private val wrongPicks = mutableStateMapOf<StepKey, Set<Int>>()
    private val solvedSteps = mutableStateMapOf<StepKey, Boolean>()

    var verdict: Verdict? by mutableStateOf(null)
    var actionId: String? by mutableStateOf(null)
    var why: String by mutableStateOf("")

    val currentGate: StepKey? get() = gates.getOrNull(gateIndex)
    val gatesDone: Boolean get() = gateIndex >= gates.size
    val canSubmit: Boolean get() = gatesDone && verdict != null && actionId != null
    val askWhy: Boolean get() = support.values.any { it == Support.FADED }

    fun wrong(step: StepKey): Set<Int> = wrongPicks[step].orEmpty()
    fun solved(step: StepKey): Boolean = solvedSteps[step] == true

    /** Second miss: the answer is revealed (no answer on the first miss). */
    fun revealed(step: StepKey): Boolean = wrong(step).size >= 2

    /** A choice on a PROMPTED (or opened HINT) step. The first choice is what the attempt records. */
    fun choose(step: StepKey, index: Int) {
        val content = item.steps[step] ?: return
        if (solved(step) || index in wrong(step)) return
        if (step !in first) first[step] = index
        if (index == content.correct) {
            solvedSteps[step] = true
        } else {
            val w = wrong(step) + index
            wrongPicks[step] = w
            if (w.size >= 2) solvedSteps[step] = true
        }
    }

    /** "Got it" on a WORKED step, or "Next step" after a PROMPTED one. */
    fun nextGate() {
        if (gateIndex < gates.size) gateIndex++
    }

    fun openHint(step: StepKey) {
        hintsOpened = hintsOpened + step
    }

    fun answer(ms: Long): ItemAnswer = ItemAnswer(
        answers = AttemptAnswers(
            verdict = verdict,
            actionId = actionId,
            stepChoices = first.toMap(),
            freeText = why.trim().takeIf { it.isNotEmpty() },
        ),
        ms = ms,
        hintsUsed = hintsOpened,
    )

    /** Region ids to highlight in the email: the hotspots of the WORKED step on screen. */
    fun highlighted(): Set<String> {
        val step = currentGate ?: return emptySet()
        if (support[step] != Support.WORKED) return emptySet()
        return item.hotspots.filter { it.step == step }.map { it.id }.toSet()
    }
}

/**
 * Renders one item for placement (one question per [mode]) or practice ([ItemMode.FULL]: steps per
 * [support], then verdict and action). Used by placement and practice alike (plan §9.3).
 *
 * Placement modes always offer "I don't know yet" and, after an answer, "How sure are you?".
 * Time is measured from the first composition with [clock] and never shown.
 *
 * @param item already filled with [tea.content.Placeholders].
 * @param loadImage bytes of a breadth image (`ImageContent.file`), or null if missing.
 * @param single / [full] optional pre-made state (tests and screenshots); keyed by item id otherwise.
 */
@Composable
fun ItemRenderer(
    item: Item,
    mode: ItemMode,
    support: Map<StepKey, Support>,
    onAnswer: (ItemAnswer) -> Unit,
    clock: () -> Instant,
    modifier: Modifier = Modifier,
    loadImage: suspend (String) -> ByteArray? = { null },
    single: SingleItemUi? = null,
    full: FullItemUi? = null,
) {
    val start = remember(item.id, mode) { clock() }
    val ms = { (clock() - start).inWholeMilliseconds.coerceAtLeast(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s3)) {
        if (mode == ItemMode.FULL) {
            val ui = full ?: remember(item.id) { FullItemUi(item, support) }
            FullItem(ui, item, clock) { onAnswer(ui.answer(ms())) }
        } else {
            val ui = single ?: remember(item.id, mode) { SingleItemUi(item, mode) }
            SingleItem(ui, item, clock, loadImage, onDontKnow = {
                onAnswer(ItemAnswer(ui.dontKnowAnswers(), confidence = null, ms = ms(), dontKnow = true))
            }) {
                onAnswer(ItemAnswer(ui.answers(), confidence = ui.confidence, ms = ms()))
            }
        }
    }
}

// ---- placement modes ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.SingleItem(
    ui: SingleItemUi,
    item: Item,
    clock: () -> Instant,
    loadImage: suspend (String) -> ByteArray?,
    onDontKnow: () -> Unit,
    onSubmit: () -> Unit,
) {
    when (ui.mode) {
        ItemMode.NEWS -> NewsCard(item)
        ItemMode.IMAGE -> ImageCard(item, loadImage)
        else -> item.email?.let { email ->
            val tap = ui.mode == ItemMode.TAP_HOTSPOT
            EmailView(
                email = email,
                hotspots = item.hotspots,
                tappable = tap,
                highlighted = if (tap) ui.tapped else emptySet(),
                onTap = { ui.tap(it) },
                decoys = tap,
                now = clock(),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
        when (ui.mode) {
            ItemMode.VERDICT -> {
                Question(stringResource(Res.string.item_verdict_question))
                OptionRow(
                    listOf(stringResource(Res.string.item_verdict_trick), stringResource(Res.string.item_verdict_fine)),
                    selected = ui.choice,
                    onSelect = { ui.choice = it },
                )
            }
            ItemMode.TAP_HOTSPOT -> {
                Question(stringResource(Res.string.item_tap_question))
                if (ui.tapped.isNotEmpty()) {
                    Text(
                        pluralStringResource(Res.plurals.item_tap_count, ui.tapped.size, ui.tapped.size),
                        style = TeaTheme.type.smallStrong,
                        color = TeaTheme.colors.inkSoft,
                    )
                }
            }
            ItemMode.LINK -> {
                val q = item.linkQuestion
                Question(stringResource(Res.string.item_link_question, q?.shownText.orEmpty()))
                Text(stringResource(Res.string.item_link_tip), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
                q?.options?.forEachIndexed { i, option ->
                    ChoiceRow(option, if (ui.choice == i) ChoiceState.Selected else ChoiceState.Idle, { ui.choice = i }, mono = true)
                }
            }
            ItemMode.EMOTION -> {
                Question(stringResource(Res.string.item_emotion_question))
                item.steps[StepKey.EMOTION]?.choices?.forEachIndexed { i, option ->
                    ChoiceRow(option, if (ui.choice == i) ChoiceState.Selected else ChoiceState.Idle, { ui.choice = i })
                }
            }
            ItemMode.ACTION -> {
                Question(stringResource(Res.string.item_action_question))
                ui.actions.forEachIndexed { i, action ->
                    ChoiceRow(action.label, if (ui.choice == i) ChoiceState.Selected else ChoiceState.Idle, { ui.choice = i })
                }
            }
            ItemMode.NEWS -> {
                Question(stringResource(Res.string.item_news_question))
                OptionRow(
                    listOf(stringResource(Res.string.item_news_forward), stringResource(Res.string.item_news_dont)),
                    selected = ui.choice,
                    onSelect = { ui.choice = it },
                )
            }
            ItemMode.IMAGE -> {
                Question(stringResource(Res.string.item_image_question))
                OptionRow(
                    listOf(stringResource(Res.string.item_image_real), stringResource(Res.string.item_image_ai)),
                    selected = ui.choice,
                    onSelect = { ui.choice = it },
                )
            }
            ItemMode.FULL -> Unit
        }

        if (ui.answered) {
            Column(Modifier.padding(top = TeaSpacing.s1), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
                Text(stringResource(Res.string.item_how_sure), style = TeaTheme.type.label, color = TeaTheme.colors.ink)
                OptionRow(
                    listOf(
                        stringResource(Res.string.item_confidence_guessing),
                        stringResource(Res.string.item_confidence_fairly_sure),
                        stringResource(Res.string.item_confidence_certain),
                    ),
                    selected = ui.confidence?.ordinal,
                    onSelect = { ui.confidence = Confidence.entries[it] },
                    meters = listOf(0.33f, 0.66f, 1f),
                )
            }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(top = TeaSpacing.s1),
            horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1),
        ) {
            TeaButton(
                if (ui.mode == ItemMode.TAP_HOTSPOT) stringResource(Res.string.item_tap_done) else stringResource(Res.string.item_continue),
                onSubmit,
                enabled = ui.answered && ui.confidence != null,
            )
            TeaButton(stringResource(Res.string.item_dont_know), onDontKnow, variant = TeaButtonVariant.Text)
        }
    }
}

@Composable
private fun Question(text: String) {
    Text(text, Modifier.semantics { heading() }, style = TeaTheme.type.title, color = TeaTheme.colors.ink)
}

@Composable
private fun NewsCard(item: Item) {
    val news = item.news ?: return
    TeaCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.item_news_label), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
        Text(news.headline, style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
        Text("${news.source} · ${news.date}", style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
    }
}

@Composable
private fun ImageCard(item: Item, loadImage: suspend (String) -> ByteArray?) {
    val image = item.image ?: return
    val bitmap by produceState<ImageBitmap?>(null, image.file) {
        value = try {
            loadImage(image.file)?.decodeToImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    TeaCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.item_image_label), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bmp,
                contentDescription = image.alt,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(TeaShapes.md)
                    .border(TeaSize.border, TeaTheme.colors.line, TeaShapes.md),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(stringResource(Res.string.item_image_missing), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
        }
    }
}

// ---- practice (FULL) ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.FullItem(ui: FullItemUi, item: Item, clock: () -> Instant, onSubmit: () -> Unit) {
    item.email?.let { email ->
        EmailView(email, item.hotspots, tappable = false, highlighted = ui.highlighted(), onTap = {}, now = clock())
    }

    // Steps already gone through, in short.
    ui.gates.take(ui.gateIndex).forEach { step -> DoneStep(step, item.steps[step]?.answer.orEmpty()) }

    val gate = ui.currentGate
    if (gate != null) {
        val content = item.steps.getValue(gate)
        if (ui.support[gate] == Support.WORKED) {
            WorkedStep(gate, content.answer) { ui.nextGate() }
        } else {
            PromptedStep(ui, item, gate, onNext = { ui.nextGate() })
        }
        return
    }

    // Hints for steps at HINT level.
    if (ui.hintSteps.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            val closed = ui.hintSteps.filter { it !in ui.hintsOpened }
            if (closed.isNotEmpty()) {
                Text(stringResource(Res.string.practice_hints_label), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s1), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
                    closed.forEach { step ->
                        TeaButton(
                            stringResource(Res.string.practice_need_hint, stepLabel(step)),
                            { ui.openHint(step) },
                            variant = TeaButtonVariant.Secondary,
                            small = true,
                            icon = TeaIcons.Hint,
                        )
                    }
                }
            }
            ui.hintSteps.filter { it in ui.hintsOpened }.forEach { step -> PromptedStep(ui, item, step, onNext = null) }
        }
    }

    // Verdict
    Column(verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
        Text(stringResource(Res.string.practice_verdict_title), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
        Question(stringResource(Res.string.item_verdict_question))
        val verdicts = listOf(Verdict.TRICK, Verdict.FINE, Verdict.NOT_SURE)
        OptionRow(
            listOf(
                stringResource(Res.string.item_verdict_trick),
                stringResource(Res.string.item_verdict_fine),
                stringResource(Res.string.item_verdict_not_sure),
            ),
            selected = ui.verdict?.let { verdicts.indexOf(it) },
            onSelect = { ui.verdict = verdicts[it] },
        )
    }

    // Next action
    Column(verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
        Question(stringResource(Res.string.item_action_question))
        ui.actions.forEach { action ->
            ChoiceRow(
                action.label,
                if (ui.actionId == action.id) ChoiceState.Selected else ChoiceState.Idle,
                { ui.actionId = action.id },
            )
        }
    }

    if (ui.askWhy) {
        TeaTextField(
            ui.why,
            { ui.why = it.take(280) },
            label = stringResource(Res.string.practice_why_label),
            placeholder = stringResource(Res.string.practice_why_placeholder),
        )
    }

    TeaButton(stringResource(Res.string.item_check), onSubmit, enabled = ui.canSubmit)
}

/** A step already done: its question and the answer, compact. */
@Composable
private fun DoneStep(step: StepKey, answer: String) {
    val colors = TeaTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(TeaShapes.md).background(colors.okSoft).border(TeaSize.border, colors.rule, TeaShapes.md)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TeaIcon(TeaIcons.Check, null, tint = colors.okText, size = 18.dp, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(stepQuestion(step), style = TeaTheme.type.smallStrong, color = colors.ink)
            Text(answer, style = TeaTheme.type.small, color = colors.ink)
        }
    }
}

/** WORKED: Cat explains the step on this email (the hotspot is highlighted in the email), "Got it". */
@Composable
private fun WorkedStep(step: StepKey, answer: String, onGotIt: () -> Unit) {
    StepFrame(step) {
        PersonaBubble(Persona.CAT, Mood.NEUTRAL, answer, onSoft = true)
        TeaButton(stringResource(Res.string.practice_worked_got_it), onGotIt, variant = TeaButtonVariant.Secondary)
    }
}

/**
 * PROMPTED: the step question with its three choices. A wrong choice is struck out and disabled and
 * Cat gives a hint (the step prompt + cues); one retry; a second miss reveals the answer.
 * [onNext] null = not a gate (an opened HINT step): no "Next step" button.
 */
@Composable
private fun PromptedStep(ui: FullItemUi, item: Item, step: StepKey, onNext: (() -> Unit)?) {
    val content = item.steps[step] ?: return
    StepFrame(step) {
        val solved = ui.solved(step)
        val revealed = ui.revealed(step)
        content.choices.forEachIndexed { i, choice ->
            val state = when {
                i in ui.wrong(step) -> ChoiceState.Wrong
                solved && i == content.correct -> ChoiceState.Correct
                solved -> ChoiceState.Disabled
                else -> ChoiceState.Idle
            }
            ChoiceRow(choice, state, { ui.choose(step, i) })
        }
        if (ui.wrong(step).isNotEmpty() && !revealed && !solved) {
            val hint = buildString {
                append(stringResource(Res.string.practice_try_again))
                append(' ')
                append(content.prompt)
                if (content.cues.isNotEmpty()) {
                    append(' ')
                    append(content.cues.joinToString(" "))
                }
            }
            PersonaBubble(Persona.CAT, Mood.NEUTRAL, hint, onSoft = true)
        }
        if (revealed) {
            PersonaBubble(Persona.CAT, Mood.NEUTRAL, stringResource(Res.string.practice_answer_was, content.answer), onSoft = true)
        }
        if (solved && onNext != null) {
            TeaButton(stringResource(Res.string.practice_next_step), onNext, variant = TeaButtonVariant.Secondary)
        }
    }
}

@Composable
private fun StepFrame(step: StepKey, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = TeaSize.maxContentWidth),
        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
            Text(
                stepLabel(step).uppercase(),
                Modifier.clip(TeaShapes.pill).background(TeaTheme.colors.ink).padding(horizontal = 10.dp, vertical = 2.dp),
                style = TeaTheme.type.xs.copy(fontWeight = FontWeight.Bold),
                color = TeaTheme.colors.paper,
            )
        }
        Question(stepQuestion(step))
        content()
    }
}

/** Ids of the regions [tea.ui.components.EmailView] can highlight for [item] (red flags). */
fun redFlagRegions(item: Item): Set<String> {
    val email = item.email ?: return emptySet()
    val ids = emailRegions(email, item.hotspots, decoys = false).values.toSet()
    return item.hotspots.filter { it.redFlag && it.id in ids }.map { it.id }.toSet()
}
