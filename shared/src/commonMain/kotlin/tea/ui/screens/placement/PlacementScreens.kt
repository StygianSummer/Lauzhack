package tea.ui.screens.placement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.Screen
import tea.app.TeaApp
import tea.engine.PlacementState
import tea.generated.resources.Res
import tea.generated.resources.placement_ack_got_it
import tea.generated.resources.placement_ack_together
import tea.generated.resources.placement_empty
import tea.generated.resources.placement_focus_ask
import tea.generated.resources.placement_focus_emotion
import tea.generated.resources.placement_focus_verify
import tea.generated.resources.placement_focus_who
import tea.generated.resources.placement_intro_body
import tea.generated.resources.placement_intro_cat
import tea.generated.resources.placement_intro_dont_know
import tea.generated.resources.placement_intro_title
import tea.generated.resources.placement_loading
import tea.generated.resources.placement_no_result
import tea.generated.resources.placement_progress
import tea.generated.resources.placement_result_focus_label
import tea.generated.resources.placement_result_intro
import tea.generated.resources.placement_result_strength_label
import tea.generated.resources.placement_result_title
import tea.generated.resources.placement_result_turtle
import tea.generated.resources.placement_resume
import tea.generated.resources.placement_start
import tea.generated.resources.placement_start_lesson
import tea.generated.resources.placement_strength_ask
import tea.generated.resources.placement_strength_emotion
import tea.generated.resources.placement_strength_general
import tea.generated.resources.placement_strength_verify
import tea.generated.resources.placement_strength_who
import tea.generated.resources.placement_take_test
import tea.generated.resources.placement_title
import tea.model.Item
import tea.model.LESSON_1_ID
import tea.model.Mood
import tea.model.Persona
import tea.model.StepKey
import tea.ui.components.Banner
import tea.ui.components.PersonaBubble
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaCard
import tea.ui.components.TeaProgressBar
import tea.ui.components.TopBar
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.practice.ItemRenderer
import tea.ui.screens.practice.rememberFilled
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/** "Let's see where to start. About 3 minutes. No score." (plan §9.3) */
@Composable
fun PlacementIntroContent(app: TeaApp) {
    val inProgress = remember { app.load(LearnKeys.PLACEMENT_STATE, PlacementState.serializer())?.answers?.isNotEmpty() == true }
    ScreenColumn {
        SectionHeading(
            stringResource(Res.string.placement_intro_title),
            large = true,
            intro = stringResource(Res.string.placement_intro_body),
        )
        PersonaBubble(Persona.CAT, Mood.NEUTRAL, stringResource(Res.string.placement_intro_cat), onSoft = true)
        Text(stringResource(Res.string.placement_intro_dont_know), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
        TeaButton(
            stringResource(if (inProgress) Res.string.placement_resume else Res.string.placement_start),
            { app.navigate(Screen.Placement) },
        )
    }
}

/**
 * The placement test: one item per screen in the mode the engine asks for, neutral feedback only,
 * a progress bar, never a time. Resumes where it stopped after a reload. When the engine is done,
 * writes the result ([PlacementController.finish]) and shows the result screen.
 */
@Composable
fun PlacementContent(app: TeaApp) {
    val lang by app.lang.collectAsState()
    val pool by produceState<List<Item>?>(null, lang) { value = runCatching { app.content.items(lang) }.getOrDefault(emptyList()) }
    val items = pool
    Column(Modifier.fillMaxSize().background(TeaTheme.colors.paper)) {
        TopBar(title = stringResource(Res.string.placement_title), onBack = { app.back() })
        when {
            items == null -> Centered(stringResource(Res.string.placement_loading))
            items.isEmpty() -> Centered(stringResource(Res.string.placement_empty))
            else -> PlacementRun(app, remember(items) { PlacementController(app, items) }, Modifier.weight(1f))
        }
    }
}

/** The running test around [controller] (also used by screenshots with a prepared controller). */
@Composable
fun PlacementRun(app: TeaApp, controller: PlacementController, modifier: Modifier = Modifier) {
    val step = controller.current
    LaunchedEffect(step == null) {
        if (step == null) {
            controller.finish()
            app.navigateRoot(Screen.PlacementResult)
        }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(controller.answered) { scroll.scrollTo(0) }
    Column(modifier.fillMaxWidth().background(TeaTheme.colors.paper)) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            TeaProgressBar(
                controller.progress,
                Modifier.widthIn(max = TeaSize.maxContentWidth).fillMaxWidth().padding(horizontal = TeaSpacing.s2),
                label = stringResource(Res.string.placement_progress),
            )
        }
        ScreenColumn(Modifier.weight(1f), scrollState = scroll) {
            controller.ack?.let { ack ->
                Banner(stringResource(if (ack == PlacementAck.GOT_IT) Res.string.placement_ack_got_it else Res.string.placement_ack_together))
            }
            if (step != null) {
                key(step.item.id, step.mode, controller.answered) {
                    ItemRenderer(
                        item = rememberFilled(app, step.item),
                        mode = step.mode,
                        support = emptyMap(),
                        onAnswer = { controller.answer(step, it) },
                        clock = app::now,
                        loadImage = { file -> imageBytes(app, file) },
                    )
                }
            }
        }
    }
}

/**
 * Bytes of a breadth image. Items store `file` as "img/<name>" while
 * [tea.content.ContentRepository.imageBytes] already adds "items/breadth/img/", so the prefix is
 * dropped (reported to the content owner).
 */
suspend fun imageBytes(app: TeaApp, file: String): ByteArray? =
    runCatching { app.content.imageBytes(file.removePrefix("img/")) }.getOrNull()

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(TeaSpacing.s3), contentAlignment = Alignment.Center) {
        Text(text, style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
    }
}

/** One strength and one focus sentence, no score (plan §6.2). Button "Start lesson 1". */
@Composable
fun PlacementResultContent(app: TeaApp) {
    val profile by app.profile.collectAsState()
    val result = profile.placement
    ScreenColumn {
        if (result == null) {
            SectionHeading(stringResource(Res.string.placement_no_result), large = true)
            TeaButton(stringResource(Res.string.placement_take_test), { app.navigate(Screen.PlacementIntro) })
            return@ScreenColumn
        }
        SectionHeading(
            stringResource(Res.string.placement_result_title),
            large = true,
            intro = stringResource(Res.string.placement_result_intro),
        )
        val strength = if (result.strength == result.focus) Res.string.placement_strength_general else strengthText(result.strength)
        TeaCard(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.placement_result_strength_label), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.okText)
            Text(stringResource(strength), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        }
        TeaCard(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.placement_result_focus_label), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.hintText)
            Text(stringResource(focusText(result.focus)), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        }
        PersonaBubble(Persona.TURTLE, Mood.HAPPY, stringResource(Res.string.placement_result_turtle), onSoft = true)
        TeaButton(stringResource(Res.string.placement_start_lesson), {
            app.navigateRoot(Screen.Home)
            app.navigate(Screen.Lesson(LESSON_1_ID, 0))
        })
    }
}

private fun strengthText(step: StepKey): StringResource = when (step) {
    StepKey.WHO -> Res.string.placement_strength_who
    StepKey.EMOTION -> Res.string.placement_strength_emotion
    StepKey.ASK -> Res.string.placement_strength_ask
    StepKey.VERIFY -> Res.string.placement_strength_verify
}

private fun focusText(step: StepKey): StringResource = when (step) {
    StepKey.WHO -> Res.string.placement_focus_who
    StepKey.EMOTION -> Res.string.placement_focus_emotion
    StepKey.ASK -> Res.string.placement_focus_ask
    StepKey.VERIFY -> Res.string.placement_focus_verify
}
