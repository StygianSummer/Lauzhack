package tea.ui.screens.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.Screen
import tea.app.TeaApp
import tea.content.Placeholders
import tea.content.ResourceCatalog
import tea.generated.resources.Res
import tea.generated.resources.lesson_complete
import tea.generated.resources.lesson_complete_body
import tea.generated.resources.lesson_continue
import tea.generated.resources.lesson_home
import tea.generated.resources.lesson_loading
import tea.generated.resources.lesson_minutes
import tea.generated.resources.lesson_missing
import tea.generated.resources.lesson_practice_intro
import tea.generated.resources.lesson_practice_title
import tea.generated.resources.lesson_readiness_dismissed
import tea.generated.resources.lesson_readiness_failed
import tea.generated.resources.lesson_readiness_guest
import tea.generated.resources.lesson_readiness_guest_address
import tea.generated.resources.lesson_readiness_not_now
import tea.generated.resources.lesson_readiness_offline
import tea.generated.resources.lesson_readiness_reason_already_done
import tea.generated.resources.lesson_readiness_reason_not_verified
import tea.generated.resources.lesson_readiness_reason_other
import tea.generated.resources.lesson_readiness_reason_paused
import tea.generated.resources.lesson_readiness_reason_stopped
import tea.generated.resources.lesson_readiness_reason_terms_missing
import tea.generated.resources.lesson_readiness_sending
import tea.generated.resources.lesson_readiness_title
import tea.generated.resources.lesson_rules_tap
import tea.generated.resources.lesson_rules_title
import tea.generated.resources.lesson_section_attacks
import tea.generated.resources.lesson_section_content
import tea.generated.resources.lesson_section_practice
import tea.generated.resources.lesson_section_readiness
import tea.generated.resources.lesson_section_status
import tea.generated.resources.lesson_section_tools
import tea.generated.resources.lesson_status_not_rated
import tea.generated.resources.lesson_status_now
import tea.generated.resources.lesson_status_title
import tea.generated.resources.lesson_tools_title
import tea.generated.resources.summary_continue
import tea.generated.resources.summary_practise_again
import tea.model.AttacksSection
import tea.model.ContentSection
import tea.model.EventTypes
import tea.model.Item
import tea.model.Lesson
import tea.model.LessonSection
import tea.model.Mood
import tea.model.Persona
import tea.model.PracticeSection
import tea.model.ReadinessSection
import tea.model.RuleCard
import tea.model.StatusSection
import tea.model.ToolRow
import tea.model.ToolsSection
import tea.ui.components.OptionRow
import tea.ui.components.PersonaBubble
import tea.ui.components.PersonaCard
import tea.ui.components.PersonaSpeech
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.StepBar
import tea.ui.components.TeaButton
import tea.ui.screens.LessonPendingPill
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaDialog
import tea.ui.components.TopBar
import tea.ui.components.VoiceLine
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.learn.emitOnce
import tea.ui.screens.practice.PracticeController
import tea.ui.screens.practice.PracticeRunner
import tea.ui.screens.practice.SummaryContent
import tea.ui.screens.practice.SummaryLinks
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import tea.ui.theme.personaStyle

private sealed interface Loaded<out T> {
    data object Loading : Loaded<Nothing>
    data class Done<T>(val value: T?) : Loaded<T>
}

/**
 * Lesson renderer (plan §9.4): top bar, a progress row of the section titles (done / current),
 * then section [section] of lesson [id]. Emits `lesson_started` once, stores the current section
 * under `lesson.<id>.section` (Home's "Continue"), and moves between sections with
 * `navigate(Screen.Lesson(id, n))`; after the last section, back to Home.
 */
@Composable
fun LessonContent(app: TeaApp, id: String, section: Int, pending: Boolean = false) {
    val lang by app.lang.collectAsState()
    val loaded by produceState<Loaded<Lesson>>(Loaded.Loading, id, lang) {
        value = Loaded.Done(runCatching { app.content.lesson(id, lang) }.getOrNull())
    }
    LaunchedEffect(id) {
        app.emitOnce(LearnKeys.lessonStarted(id), EventTypes.LESSON_STARTED, JsonObject(mapOf("lesson_id" to JsonPrimitive(id))))
    }
    LaunchedEffect(id, section) { app.save(LearnKeys.lessonSection(id), LearnKeys.INT, section) }

    val state = loaded
    val lesson = (state as? Loaded.Done)?.value
    Column(Modifier.fillMaxSize().background(TeaTheme.colors.paper)) {
        TopBar(title = lesson?.title, onBack = { app.back() })
        if (lesson == null) {
            Box(Modifier.fillMaxSize().padding(TeaSpacing.s3), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (state is Loaded.Loading) Res.string.lesson_loading else Res.string.lesson_missing),
                    style = TeaTheme.type.body,
                    color = TeaTheme.colors.inkSoft,
                )
            }
            return@Column
        }
        val index = section.coerceIn(0, lesson.sections.lastIndex)
        LessonProgress(lesson, index, pending)
        key(index) {
            LessonSectionView(app, lesson, index, Modifier.weight(1f))
        }
    }
}

/** Goes to section [n] of [lesson], or Home after the last one. */
fun goToSection(app: TeaApp, lesson: Lesson, n: Int) {
    if (n >= lesson.sections.size) app.navigateRoot(Screen.Home) else app.navigate(Screen.Lesson(lesson.id, n))
}

@Composable
private fun LessonProgress(lesson: Lesson, index: Int, pending: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1),
    ) {
        if (pending) LessonPendingPill()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StepBar(
                lesson.sections.map { sectionTitle(it) },
                index,
                Modifier.widthIn(max = TeaSize.maxContentWidth).fillMaxWidth().padding(horizontal = TeaSpacing.s2),
            )
        }
    }
}

@Composable
fun sectionTitle(section: LessonSection): String = stringResource(
    when (section) {
        is ContentSection -> Res.string.lesson_section_content
        is ToolsSection -> Res.string.lesson_section_tools
        is AttacksSection -> Res.string.lesson_section_attacks
        is StatusSection -> Res.string.lesson_section_status
        is PracticeSection -> Res.string.lesson_section_practice
        is ReadinessSection -> Res.string.lesson_section_readiness
    },
)

/** One section; the practice section manages its own scrolling, the others share a scroll column. */
@Composable
fun LessonSectionView(app: TeaApp, lesson: Lesson, index: Int, modifier: Modifier = Modifier) {
    when (val s = lesson.sections[index]) {
        is PracticeSection -> PracticeSectionView(app, lesson, index, s, modifier)
        else -> ScreenColumn(modifier, scrollState = rememberScrollState()) {
            if (index == 0) {
                if (lesson.subtitle.isNotBlank()) {
                    Text(lesson.subtitle, style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                }
                Text(stringResource(Res.string.lesson_minutes, lesson.minutes), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
            }
            when (s) {
                is ContentSection -> RulesSection(s, index + 1)
                is ToolsSection -> ToolsSectionView(app, s, index + 1)
                is AttacksSection -> AttacksSectionView(app, lesson, s, index + 1)
                is StatusSection -> StatusSectionView(app, lesson.id, s, index + 1)
                is ReadinessSection -> ReadinessSectionView(app, lesson.id, s)
                is PracticeSection -> Unit
            }
            val last = index == lesson.sections.lastIndex
            TeaButton(
                stringResource(if (last) Res.string.lesson_home else Res.string.lesson_continue),
                { goToSection(app, lesson, index + 1) },
                Modifier.padding(top = TeaSpacing.s2),
                variant = if (last) TeaButtonVariant.Secondary else TeaButtonVariant.Primary,
            )
        }
    }
}

// ---- content: two rule cards ------------------------------------------------------------------

@Composable
private fun ColumnScope.RulesSection(s: ContentSection, number: Int) {
    var open by remember { mutableStateOf<RuleCard?>(null) }
    SectionHeading(stringResource(Res.string.lesson_rules_title), number = number, intro = stringResource(Res.string.lesson_rules_tap))
    if (TeaTheme.isCompact) {
        s.cards.forEachIndexed { i, card -> RuleCardView(card, tilt(i), Modifier.fillMaxWidth()) { open = card } }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s3)) {
            s.cards.forEachIndexed { i, card -> RuleCardView(card, tilt(i), Modifier.weight(1f).fillMaxHeight()) { open = card } }
        }
    }
    open?.let { card ->
        TeaDialog(onDismissRequest = { open = null }, title = card.title) { RuleCardBody(card, large = true) }
    }
}

private fun tilt(i: Int) = if (i % 2 == 0) -1f else 1f

@Composable
fun RuleCardView(card: RuleCard, tilt: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PersonaCard(
        card.persona,
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        title = card.title,
        role = card.role,
        tilt = tilt,
        top = { PersonaSpeech(card.persona, card.say, onSoft = false) },
        content = { RuleCardBody(card, large = false) },
    )
}

@Composable
private fun ColumnScope.RuleCardBody(card: RuleCard, large: Boolean) {
    if (large) PersonaSpeech(card.persona, card.say, onSoft = true)
    card.rules.forEachIndexed { i, rule ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberDot(i + 1, card.persona)
            Column(Modifier.weight(1f)) {
                Text(rule.headline, style = if (large) TeaTheme.type.title else TeaTheme.type.bodyStrong, color = TeaTheme.colors.ink)
                Text(rule.body, style = TeaTheme.type.body, color = TeaTheme.colors.ink)
            }
        }
    }
    if (card.signOff.isNotBlank()) VoiceLine(card.persona, card.signOff, colored = true)
}

@Composable
fun NumberDot(n: Int, persona: Persona) {
    val style = personaStyle(persona)
    Box(
        Modifier.size(28.dp).clip(TeaShapes.circle).background(style.accent).border(TeaSize.border, TeaTheme.colors.line, TeaShapes.circle),
        contentAlignment = Alignment.Center,
    ) {
        Text(n.toString(), style = TeaTheme.type.smallStrong, color = style.on)
    }
}

// ---- tools: Dog --------------------------------------------------------------------------------

@Composable
private fun ColumnScope.ToolsSectionView(app: TeaApp, s: ToolsSection, number: Int) {
    val catalog by produceState<ResourceCatalog?>(null) { value = runCatching { app.content.resources() }.getOrNull() }
    SectionHeading(stringResource(Res.string.lesson_tools_title), number = number)
    PersonaBubble(Persona.DOG, Mood.HAPPY, s.intro, onSoft = true)
    val cat = catalog ?: return
    s.tools.forEach { tool ->
        val resource = cat.byId(tool.resourceId) ?: return@forEach
        if (!resource.hasUrl) return@forEach
        ToolRowView(tool) { app.openUrl(resource.url) }
    }
}

@Composable
private fun ToolRowView(tool: ToolRow, onOpen: () -> Unit) {
    val dog = personaStyle(Persona.DOG)
    TeaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(TeaSpacing.s2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            Box(
                Modifier.size(44.dp).clip(TeaShapes.sm).background(dog.surface).border(TeaSize.border, TeaTheme.colors.line, TeaShapes.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(tool.name.take(1).uppercase(), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tool.name, style = TeaTheme.type.title, color = TeaTheme.colors.ink)
                Text(tool.what, style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
                VoiceLine(Persona.DOG, tool.dogSays, colored = true)
                TeaButton(tool.button, onOpen, Modifier.padding(top = 4.dp), variant = TeaButtonVariant.Secondary, small = true)
            }
        }
    }
}

// ---- status ------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.StatusSectionView(app: TeaApp, lessonId: String, s: StatusSection, number: Int) {
    val profile by app.profile.collectAsState()
    val selected = s.levels.indexOfFirst { it.key == profile.statusRating }.takeIf { it >= 0 }
    SectionHeading(stringResource(Res.string.lesson_status_title), number = number)
    TeaCard(Modifier.fillMaxWidth()) {
        Text(s.question, style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        Text(
            stringResource(
                Res.string.lesson_status_now,
                selected?.let { s.levels[it].label } ?: stringResource(Res.string.lesson_status_not_rated),
            ),
            style = TeaTheme.type.small,
            color = TeaTheme.colors.inkSoft,
        )
        OptionRow(
            s.levels.map { it.label },
            selected = selected,
            onSelect = { i ->
                val level = s.levels[i]
                if (profile.statusRating != level.key) {
                    app.updateProfile { it.copy(statusRating = level.key) }
                    app.emit(
                        EventTypes.LESSON_STATUS_RATED,
                        JsonObject(mapOf("lesson_id" to JsonPrimitive(lessonId), "level" to JsonPrimitive(level.key))),
                    )
                }
            },
            meters = s.levels.indices.map { (it + 1f) / s.levels.size },
        )
        selected?.let { i ->
            val level = s.levels[i]
            PersonaBubble(level.persona, if (level.persona == Persona.SPIDER) Mood.SMUG else Mood.NEUTRAL, level.reply, onSoft = true)
        }
    }
}

// ---- practice ----------------------------------------------------------------------------------

@Composable
private fun PracticeSectionView(app: TeaApp, lesson: Lesson, index: Int, s: PracticeSection, modifier: Modifier) {
    val lang by app.lang.collectAsState()
    val pool by produceState<List<Item>?>(null, lang) { value = runCatching { app.content.items(lang) }.getOrDefault(emptyList()) }
    val items = pool ?: run {
        Box(modifier.fillMaxSize())
        return
    }
    var restarts by remember { mutableStateOf(0) }
    val controller = remember(items, restarts) {
        PracticeController(app, items, PracticeController.lessonOwner(lesson.id), s, lessonId = lesson.id, restart = restarts > 0)
    }
    PracticeRunner(
        app,
        controller,
        modifier,
        intro = {
            SectionHeading(stringResource(Res.string.lesson_practice_title), number = index + 1, intro = stringResource(Res.string.lesson_practice_intro))
        },
    ) { summary ->
        SummaryContent(summary) {
            TeaButton(stringResource(Res.string.summary_continue), { goToSection(app, lesson, index + 1) })
            SummaryLinks(app) {
                TeaButton(stringResource(Res.string.summary_practise_again), { restarts++ }, variant = TeaButtonVariant.Secondary)
            }
        }
    }
}

// ---- readiness: the opt-in card (plan §9.6) ----------------------------------------------------

private const val READINESS_DISMISSED = "dismissed"
private const val READINESS_SCHEDULED = "scheduled"
private const val READINESS_REASON = "reason:"

@Composable
private fun ColumnScope.ReadinessSectionView(app: TeaApp, lessonId: String, s: ReadinessSection) {
    val profile by app.profile.collectAsState()
    val email by app.email.collectAsState()
    val online by app.online.collectAsState()
    val loggedIn by app.loggedIn.collectAsState()
    val key = LearnKeys.lessonReadiness(lessonId)
    var status by remember { mutableStateOf(app.load(key, LearnKeys.STRING)) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SectionHeading(stringResource(Res.string.lesson_complete), large = true, intro = stringResource(Res.string.lesson_complete_body))

    val current = status
    when {
        current == READINESS_DISMISSED -> Text(stringResource(Res.string.lesson_readiness_dismissed), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
        current == READINESS_SCHEDULED -> PersonaBubble(Persona.CAT, Mood.HAPPY, s.done, onSoft = true)
        else -> TeaCard(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.lesson_readiness_title), style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
            // A guest has no registered address: never show the placeholder address as if mail went there.
            val values = if (loggedIn && email != null) Placeholders.values(profile, email)
            else Placeholders.values(profile, null) + ("email" to stringResource(Res.string.lesson_readiness_guest_address))
            Text(boldMarkdown(Placeholders.fill(s.prompt, values)), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
            if (current != null && current.startsWith(READINESS_REASON)) {
                PersonaBubble(Persona.TURTLE, Mood.NEUTRAL, stringResource(reasonText(current.removePrefix(READINESS_REASON))), onSoft = true)
            }
            if (failed) {
                Text(stringResource(Res.string.lesson_readiness_failed), style = TeaTheme.type.small, color = TeaTheme.colors.badText)
            }
            if (!loggedIn) {
                Text(stringResource(Res.string.lesson_readiness_guest), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
            }
            val canPress = online && loggedIn && !busy
            Row(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                TeaButton(
                    when {
                        busy -> stringResource(Res.string.lesson_readiness_sending)
                        !online -> stringResource(Res.string.lesson_readiness_offline)
                        else -> s.button
                    },
                    {
                        busy = true
                        failed = false
                        app.emit(EventTypes.PRACTICE_EMAIL_OPTED_IN, JsonObject(mapOf("lesson_id" to JsonPrimitive(lessonId))))
                        scope.launch {
                            val answer = runCatching { app.requestReadiness(lessonId) }.getOrNull()
                            busy = false
                            if (answer == null) {
                                failed = true
                            } else {
                                val next = if (answer.scheduled) READINESS_SCHEDULED else READINESS_REASON + (answer.reason ?: "")
                                status = next
                                app.save(key, LearnKeys.STRING, next)
                            }
                        }
                    },
                    enabled = canPress,
                )
                TeaButton(
                    stringResource(Res.string.lesson_readiness_not_now),
                    {
                        status = READINESS_DISMISSED
                        app.save(key, LearnKeys.STRING, READINESS_DISMISSED)
                    },
                    variant = TeaButtonVariant.Text,
                )
            }
        }
    }
}

private fun reasonText(reason: String): StringResource = when (reason) {
    "paused" -> Res.string.lesson_readiness_reason_paused
    "stopped" -> Res.string.lesson_readiness_reason_stopped
    "not_verified" -> Res.string.lesson_readiness_reason_not_verified
    "already_done" -> Res.string.lesson_readiness_reason_already_done
    "terms_missing" -> Res.string.lesson_readiness_reason_terms_missing
    else -> Res.string.lesson_readiness_reason_other
}

/** `**bold**` spans (used by the lesson's readiness prompt) to an AnnotatedString. */
fun boldMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
