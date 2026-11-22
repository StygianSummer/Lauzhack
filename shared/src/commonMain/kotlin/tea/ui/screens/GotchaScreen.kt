package tea.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.Res
import tea.generated.resources.*
import tea.model.GotchaResponse
import tea.model.Mood
import tea.model.Persona
import tea.net.ApiResult
import tea.ui.components.ChoiceRow
import tea.ui.components.ChoiceState
import tea.ui.components.PersonaBubble
import tea.ui.components.PersonaNameTag
import tea.ui.components.SpiderWeb
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.SectionHeading
import tea.ui.components.personaFullArt
import tea.ui.screens.FourStepsList
import tea.ui.screens.BulletList
import tea.ui.theme.LocalOnSpiderPanel
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import tea.ui.theme.Voice
import tea.ui.theme.hardShadow

private const val TRANSITION_MS = 450

@Composable
fun GotchaScreen(app: AppState, screen: Screen.Gotcha) {
    val online by app.online.collectAsState()
    var result by remember(screen.sendId) { mutableStateOf<ApiResult<GotchaResponse>?>(null) }
    var round by remember { mutableStateOf(0) }
    var answer by remember(screen.sendId) { mutableStateOf<String?>(null) }
    var reason by remember(screen.sendId) { mutableStateOf<String?>(null) }

    LaunchedEffect(screen.sendId, online, round) {
        if (result == null && online) result = app.gotcha(screen.sendId)
    }

    val data = (result as? ApiResult.Ok)?.value
    val notFound = (result as? ApiResult.Err)?.status == 404
    val failed = data == null && (result != null || !online)
    GotchaContent(
        data = data,
        clickedWhen = data?.let { gotchaWhen(it.clickedAt, app.now()) }.orEmpty(),
        loading = result == null && online,
        loadFailed = failed,
        notFound = notFound,
        answer = answer,
        reason = reason,
        onAnswer = {
            answer = it
            reason = null
        },
        onReason = { reason = it },
        onRetry = {
            app.retryConnection()
            result = null
            round++
        },
        onFinish = { answer, reason ->
            app.finishGotcha(screen.sendId, data?.lessonId ?: screen.lessonId, answer, reason)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Pure interview content. Supplying [data] and [answer] renders either branch without the email flow.
 */
@Composable
fun GotchaContent(
    data: GotchaResponse?,
    clickedWhen: String,
    answer: String? = null,
    reason: String? = null,
    loading: Boolean = false,
    loadFailed: Boolean = false,
    notFound: Boolean = false,
    onAnswer: (String) -> Unit = {},
    onReason: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onFinish: (answer: String, reason: String?) -> Unit = { _, _ -> },
    scrollEnabled: Boolean = true,
    animateTransition: Boolean = true,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    var entered by remember(data?.sendId) { mutableStateOf(!animateTransition) }
    var leaving by remember(data?.sendId) { mutableStateOf(false) }
    LaunchedEffect(data?.sendId, animateTransition) {
        if (animateTransition) entered = true
    }
    val background by animateColorAsState(
        targetValue = if (leaving || !entered) TeaTheme.colors.paper else TeaColors.spiderDeep,
        animationSpec = tween(TRANSITION_MS),
        label = "gotcha-background",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (leaving) 0f else if (entered) 1f else 0f,
        animationSpec = tween(TRANSITION_MS),
        label = "gotcha-content",
    )
    val webAlpha by animateFloatAsState(
        targetValue = if (leaving || !entered) 0f else 1f,
        animationSpec = tween(TRANSITION_MS),
        label = "gotcha-web",
    )
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(TRANSITION_MS.toLong())
            onFinish(answer ?: "no", reason)
        }
    }

    Box(modifier.background(background)) {
        Canvas(Modifier.fillMaxWidth().height(420.dp)) {
            val gradientHeight = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(TeaColors.spider, TeaColors.spiderDeep),
                    endY = gradientHeight,
                ),
                size = Size(size.width, gradientHeight),
                alpha = webAlpha,
            )
        }
        SpiderWeb(Modifier.align(Alignment.TopEnd).size(360.dp).alpha(webAlpha), alpha = 0.15f)
        Voice(Persona.SPIDER) {
            CompositionLocalProvider(LocalOnSpiderPanel provides true) {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (scrollEnabled) Modifier.fillMaxHeight().verticalScroll(scroll) else Modifier)
                        .alpha(contentAlpha),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .widthIn(max = TeaSize.maxContentWidth)
                            .fillMaxWidth()
                            .padding(horizontal = TeaSpacing.s2, vertical = TeaSpacing.s3),
                        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = TeaSpacing.s1),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(Res.string.gotcha_header),
                                style = TeaTheme.type.smallStrong,
                                color = TeaColors.webInk,
                            )
                            val art = personaFullArt(Persona.SPIDER)
                            if (art != null) {
                                Image(
                                    painterResource(art),
                                    contentDescription = null,
                                    modifier = Modifier.size(84.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                        SectionHeading(
                            stringResource(Res.string.gotcha_title),
                            large = true,
                            intro = stringResource(Res.string.gotcha_intro),
                        )
                        PersonaNameTag(
                            Persona.SPIDER,
                            role = stringResource(Res.string.gotcha_role),
                            mood = Mood.SMUG,
                        )
                        when {
                            loading -> Text(stringResource(Res.string.gotcha_loading), color = TeaColors.webInk, style = TeaTheme.type.body)
                            data == null -> {
                                PersonaBubble(
                                    Persona.SPIDER,
                                    Mood.SMUG,
                                    stringResource(if (notFound) Res.string.gotcha_not_found else if (loadFailed) Res.string.gotcha_load_failed else Res.string.gotcha_not_found),
                                )
                                if (!notFound) {
                                    TeaButton(
                                        stringResource(Res.string.gotcha_retry),
                                        onRetry,
                                        variant = TeaButtonVariant.Secondary,
                                    )
                                }
                                TeaButton(
                                    stringResource(Res.string.gotcha_continue),
                                    onClick = { leaving = true },
                                )
                            }
                            else -> {
                                when (answer) {
                                    "yes" -> {
                                        PersonaBubble(Persona.SPIDER, Mood.SMUG, stringResource(Res.string.gotcha_yes_spider))
                                        GotchaFactsCard(data, clickedWhen)
                                        GotchaStepsCard(data)
                                    }
                                    "no" -> {
                                        if (reason == null) {
                                            PersonaBubble(
                                                Persona.SPIDER,
                                                Mood.SMUG,
                                                stringResource(Res.string.gotcha_no_spider, clickedWhen),
                                            )
                                            SectionHeading(stringResource(Res.string.gotcha_reason_question))
                                            listOf(
                                                Res.string.gotcha_reason_who to "who",
                                                Res.string.gotcha_reason_emotion to "emotion",
                                                Res.string.gotcha_reason_ask to "ask",
                                                Res.string.gotcha_reason_verify to "verify",
                                            ).forEach { (label, key) ->
                                                ChoiceRow(
                                                    stringResource(label),
                                                    state = ChoiceState.Idle,
                                                    onClick = { onReason(key) },
                                                )
                                            }
                                        } else {
                                            PersonaBubble(
                                                Persona.SPIDER,
                                                Mood.SMUG,
                                                stringResource(reasonLine(reason)),
                                            )
                                            GotchaFactsCard(data, clickedWhen)
                                            GotchaStepsCard(data)
                                        }
                                    }
                                    else -> {
                                        PersonaBubble(Persona.SPIDER, Mood.SMUG, stringResource(Res.string.gotcha_spider_open))
                                        GotchaEmailCard(data)
                                        SectionHeading(stringResource(Res.string.gotcha_recognition_question))
                                        ChoiceRow(
                                            stringResource(Res.string.gotcha_yes),
                                            state = ChoiceState.Idle,
                                            onClick = { onAnswer("yes") },
                                        )
                                        ChoiceRow(
                                            stringResource(Res.string.gotcha_no),
                                            state = ChoiceState.Idle,
                                            onClick = { onAnswer("no") },
                                        )
                                    }
                                }
                                if (answer != null && (answer == "yes" || reason != null)) {
                                    TeaButton(
                                        stringResource(Res.string.gotcha_continue),
                                        onClick = { leaving = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GotchaFactsCard(data: GotchaResponse, clickedWhen: String) {
    val shape = TeaShapes.panel
    Column(
        Modifier
            .fillMaxWidth()
            .hardShadow(TeaColors.spider, shape, TeaSize.shadowLg)
            .clip(shape)
            .background(TeaColors.gotchaCard)
            .border(TeaSize.border, TeaColors.spider, shape)
            .padding(TeaSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
    ) {
        Text(
            stringResource(Res.string.gotcha_fact_clicked, clickedWhen),
            Modifier
                .fillMaxWidth()
                .clip(TeaShapes.sm)
                .background(TeaColors.spider)
                .padding(TeaSpacing.s1),
            style = TeaTheme.type.body,
            color = TeaColors.spiderSoft,
        )
        Text(
            stringResource(Res.string.gotcha_fact_email, data.subject),
            style = TeaTheme.type.body,
            color = TeaColors.webInk,
        )
        Text(
            stringResource(Res.string.gotcha_fact_gave),
            style = TeaTheme.type.body,
            color = TeaColors.webInk,
        )
    }
}

@Composable
private fun GotchaEmailCard(data: GotchaResponse) {
    val shape = TeaShapes.panel
    Column(
        Modifier
            .fillMaxWidth()
            .hardShadow(TeaColors.spider, shape, TeaSize.shadowLg)
            .clip(shape)
            .background(TeaColors.gotchaCard)
            .border(TeaSize.border, TeaColors.spider, shape)
            .padding(TeaSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1),
    ) {
        SectionHeading(stringResource(Res.string.gotcha_email_heading))
        Text(data.subject, style = TeaTheme.type.title, color = TeaColors.webInk)
        Text(data.text, style = TeaTheme.type.body, color = TeaColors.webInk)
    }
}

@Composable
private fun GotchaStepsCard(data: GotchaResponse) {
    val shape = TeaShapes.panel
    Column(
        Modifier
            .fillMaxWidth()
            .hardShadow(TeaColors.spider, shape, TeaSize.shadowLg)
            .clip(shape)
            .background(TeaColors.gotchaCard)
            .border(TeaSize.border, TeaColors.spider, shape)
            .padding(TeaSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
    ) {
        SectionHeading(stringResource(Res.string.gotcha_steps_heading))
        FourStepsList(data.steps)
        if (data.hints.isNotEmpty()) {
            SectionHeading(stringResource(Res.string.gotcha_hints_heading))
            BulletList(data.hints)
        }
    }
}

@Composable
private fun reasonLine(reason: String) = when (reason) {
    "who" -> Res.string.gotcha_no_spider_who
    "emotion" -> Res.string.gotcha_no_spider_emotion
    "ask" -> Res.string.gotcha_no_spider_ask
    else -> Res.string.gotcha_no_spider_verify
}

@Composable
private fun gotchaWhen(clickedAt: kotlin.time.Instant, now: kotlin.time.Instant): String {
    val elapsed = now - clickedAt
    return when {
        elapsed < 1.minutes -> stringResource(Res.string.gotcha_time_just_now)
        elapsed < 1.hours -> {
            val count = elapsed.inWholeMinutes.toInt().coerceAtLeast(1)
            pluralStringResource(Res.plurals.gotcha_time_minutes_ago, count, count)
        }
        elapsed < 24.hours -> {
            val count = elapsed.inWholeHours.toInt().coerceAtLeast(1)
            pluralStringResource(Res.plurals.gotcha_time_hours_ago, count, count)
        }
        elapsed < 2.days -> stringResource(Res.string.gotcha_time_yesterday)
        elapsed < 7.days -> {
            val count = elapsed.inWholeDays.toInt().coerceAtLeast(2)
            pluralStringResource(Res.plurals.gotcha_time_days_ago, count, count)
        }
        else -> stringResource(Res.string.gotcha_time_earlier)
    }
}

@Composable
fun LessonPendingPill(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(TeaShapes.pill)
            .background(TeaColors.spider)
            .border(TeaSize.border, TeaColors.ink, TeaShapes.pill)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.lesson_pending_label),
            style = TeaTheme.type.smallStrong,
            color = Color.White,
        )
    }
}
