package tea.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.net.ApiResult
import tea.app.AppState
import tea.app.Screen
import tea.app.TeaApp
import tea.engine.ChallengeBest
import tea.engine.ChallengeOutcome
import tea.engine.ChallengeRun
import tea.engine.Streak
import tea.engine.StreakState
import tea.generated.resources.*
import tea.model.Chapter
import tea.model.ChapterKind
import tea.model.ChapterStatus
import tea.model.LESSON_1_ID
import tea.model.LESSON_2_DEMO_ID
import tea.model.LESSON_2_ID
import tea.model.LeaderboardResponse
import tea.model.Mood
import tea.model.Persona
import tea.store.FieldCache
import tea.store.LocalFlags
import tea.ui.components.Banner
import tea.ui.components.DashedDivider
import tea.ui.components.PersonaAvatar
import tea.ui.components.PersonaBubble
import tea.ui.components.Pill
import tea.ui.components.SectionHeading
import tea.ui.components.SpiderPanel
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaDialog
import tea.ui.components.TeaIcons
import tea.ui.components.TeaProgressBar
import tea.ui.screens.FourStepsList
import tea.ui.screens.challenge.outcomeWord
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.leaderboard.PERIODS
import tea.ui.screens.leaderboard.RankRow
import tea.ui.screens.placement.LevelCard
import tea.ui.screens.placement.knowsBasics
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import tea.ui.theme.personaStyle

/** Where a chapter stands, derived from local state (docs/challenge-boardroom-crisis.md §3.2). */
sealed interface ChapterProgress {
    data object NotStarted : ChapterProgress

    /** Lesson on section [section] (0-based) of [total]. */
    data class LessonSection(val section: Int, val total: Int) : ChapterProgress
    data object LessonDone : ChapterProgress

    /** Challenge run in progress: [locked] of [total] decisions made. */
    data class ChallengeStep(val locked: Int, val total: Int) : ChapterProgress
    data class ChallengeDone(val best: ChallengeBest) : ChapterProgress
}

/** One row of Home's chapter list. */
data class ChapterUi(val chapter: Chapter, val progress: ChapterProgress = ChapterProgress.NotStarted)

/** The ranking card: loading, offline/failed, guest, or a weekly leaderboard. */
sealed interface RankingUi {
    data object Loading : RankingUi
    data object Offline : RankingUi
    data object Guest : RankingUi
    data class Ready(val data: LeaderboardResponse) : RankingUi
}

/** What Home shows (plan §9.7, docs/challenge-boardroom-crisis.md §3). */
data class HomeUi(
    val firstName: String? = null,
    val xp: Int = 0,
    val streak: Int = 0,
    val dailyMinutes: Int = 5,
    val chapters: List<ChapterUi> = emptyList(),
    val showPlacement: Boolean = false,
    /** Level from the placement test, once taken (drives the level card). */
    val placementLevel: Int? = null,
    val ranking: RankingUi = RankingUi.Loading,
    val online: Boolean = true,
    val loggedIn: Boolean = false,
    val sessionExpired: Boolean = false,
    val field: FieldCache = FieldCache(),
)

/** Home actions; the screen wires them to AppState. */
class HomeActions(
    val openChapter: (ChapterUi) -> Unit = {},
    val openDemo: (ChapterUi) -> Unit = {},
    val placement: () -> Unit = {},
    val lesson1: () -> Unit = {},
    val practise: () -> Unit = {},
    val leaderboard: () -> Unit = {},
    val about: () -> Unit = {},
    val settings: () -> Unit = {},
    val suspicious: () -> Unit = {},
    val signIn: () -> Unit = {},
    val openReveal: () -> Unit = {},
    val practiseFocus: () -> Unit = {},
)

/** Progress of [chapter] from flags and blobs. [total] = lesson sections or challenge decisions (0 = unknown). */
fun chapterProgress(app: TeaApp, flags: LocalFlags, chapter: Chapter, total: Int): ChapterProgress {
    val id = chapter.target ?: return ChapterProgress.NotStarted
    return when (chapter.kind) {
        ChapterKind.LESSON -> when {
            id in flags.lessonsDone -> ChapterProgress.LessonDone
            id in flags.lessonSections -> ChapterProgress.LessonSection(flags.lessonSections.getValue(id), total)
            else -> ChapterProgress.NotStarted
        }
        ChapterKind.CHALLENGE -> {
            val locked = app.load(LearnKeys.challengeRun(id), ChallengeRun.serializer())?.results?.size ?: 0
            val best = app.load(LearnKeys.challengeBest(id), ChallengeBest.serializer())
            when {
                locked in 1 until total.coerceAtLeast(1) -> ChapterProgress.ChallengeStep(locked, total)
                best != null -> ChapterProgress.ChallengeDone(best)
                else -> ChapterProgress.NotStarted
            }
        }
    }
}

/** Where opening a chapter leads. */
fun chapterTarget(app: AppState, ui: ChapterUi): Screen? {
    val id = ui.chapter.target ?: return null
    if (ui.chapter.status != ChapterStatus.AVAILABLE) return null
    // Once the five drill accounts exist, the password chapter *is* the test: opening it asks the
    // learner to log back in to one of them, chosen at random, rather than replaying the lesson.
    if (id == LESSON_2_ID) {
        // Opening the chapter itself is always the full five; the demo button below it is the only
        // way into the two-site run, and pressing Start leaves that mode behind.
        app.save(LearnKeys.VAULT_DEMO, LearnKeys.BOOL, false)
        if (app.load(LearnKeys.VAULT_DRILL_DONE, LearnKeys.BOOL) == true) return Screen.VaultChallenge
    }
    return when (ui.chapter.kind) {
        ChapterKind.LESSON -> Screen.Lesson(id, if (ui.progress == ChapterProgress.LessonDone) 0 else app.savedSection(id))
        ChapterKind.CHALLENGE -> Screen.Challenge(id, app.load(LearnKeys.challengeSection(id), LearnKeys.INT) ?: 0)
    }
}

/**
 * The one-minute demo of the password chapter (Home's second button): the short lesson, the drill
 * cut to two sites, the debrief and the login test. Nothing about the checks or the storage
 * changes — only how much of it there is, so the whole loop fits a stage slot.
 *
 * A run already finished (two accounts, nothing left to log back in to) starts at the login test
 * rather than replaying the lesson, which is what makes the demo repeatable in front of a judge.
 */
fun startPasswordDemo(app: AppState): Screen {
    app.save(LearnKeys.VAULT_DEMO, LearnKeys.BOOL, true)
    if (app.load(LearnKeys.VAULT_DEMO_DONE, LearnKeys.BOOL) == true) return Screen.VaultChallenge
    return Screen.Lesson(LESSON_2_DEMO_ID, app.savedSection(LESSON_2_DEMO_ID))
}

@Composable
fun HomeScreen(app: AppState) {
    val profile by app.profile.collectAsState()
    val xp by app.xp.collectAsState()
    val online by app.online.collectAsState()
    val loggedIn by app.loggedIn.collectAsState()
    val expired by app.sessionExpired.collectAsState()
    val field by app.field.collectAsState()
    val flags by app.flags.collectAsState()
    val lang by app.lang.collectAsState()
    val scope = rememberCoroutineScope()
    var showSuspicious by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) { if (loggedIn) app.refreshField() }

    // Chapters + the size of each target (lesson sections / challenge decisions) for the progress line.
    val catalog by produceState<List<Pair<Chapter, Int>>>(emptyList(), lang) {
        val chapters = runCatching { app.content.chapters(lang) }.getOrDefault(emptyList())
        value = chapters.map { c ->
            val id = c.target
            val total = when {
                id == null || c.status != ChapterStatus.AVAILABLE -> 0
                c.kind == ChapterKind.LESSON -> runCatching { app.content.lesson(id, lang)?.sections?.size }.getOrNull() ?: 0
                else -> runCatching { app.content.challenge(id, lang)?.decisions?.size }.getOrNull() ?: 0
            }
            c to total
        }
    }
    val ranking by produceState<RankingUi>(RankingUi.Loading, online, loggedIn) {
        value = when {
            !online -> RankingUi.Offline
            !loggedIn -> RankingUi.Guest
            else -> when (val r = app.leaderboard(PERIODS[0])) {
                is ApiResult.Ok -> RankingUi.Ready(r.value)
                else -> RankingUi.Offline
            }
        }
    }

    val streakState = remember(xp, flags) { app.load(LearnKeys.STREAK, StreakState.serializer()) ?: StreakState() }
    val chapters = remember(catalog, flags, xp) { catalog.map { (c, total) -> ChapterUi(c, chapterProgress(app, flags, c, total)) } }
    val ui = HomeUi(
        firstName = profile.firstName,
        xp = xp,
        streak = Streak.display(streakState, Streak.localDay(app.now())),
        dailyMinutes = profile.dailyMinutes,
        chapters = chapters,
        showPlacement = profile.placement == null,
        placementLevel = profile.placement?.level,
        ranking = ranking,
        online = online,
        loggedIn = loggedIn,
        sessionExpired = expired,
        field = field,
    )
    HomeContent(
        ui,
        HomeActions(
            openChapter = { c -> chapterTarget(app, c)?.let(app::navigate) },
            openDemo = { c ->
                // The password chapter's one-minute demo: two rules, two accounts, one login back.
                if (c.chapter.target == LESSON_2_ID) app.navigate(startPasswordDemo(app))
                else c.chapter.demoTarget?.let {
                    app.navigate(if (c.chapter.kind == ChapterKind.LESSON) Screen.Lesson(it, 0) else Screen.Challenge(it, 0))
                }
            },
            placement = { app.navigate(Screen.PlacementIntro) },
            lesson1 = { app.navigate(Screen.Lesson(LESSON_1_ID, app.savedSection(LESSON_1_ID))) },
            practise = { app.navigate(Screen.Practice()) },
            leaderboard = { app.navigate(Screen.Leaderboard) },
            about = { app.navigate(Screen.About) },
            settings = { app.navigate(Screen.Settings) },
            suspicious = {
                showSuspicious = true
                scope.launch { app.reportSuspicious() }
            },
            signIn = { app.navigate(Screen.Email) },
            openReveal = { field.revealToken?.let { app.navigate(Screen.Reveal(it)) } },
            practiseFocus = { app.navigate(Screen.Practice(focusWeakest = true)) },
        ),
    )
    if (showSuspicious) {
        SuspiciousDialog(
            onDismiss = { showSuspicious = false },
            onPractise = {
                showSuspicious = false
                app.navigate(Screen.Practice())
            },
        )
    }
}

/**
 * Responsive Home: two columns (chapters | score and ranking) from [TeaSize.wideBreakpoint],
 * one column with a stats strip below it; chapters in a 2-column grid unless compact.
 */
@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions) {
    BoxWithConstraints(Modifier.fillMaxSize().background(TeaTheme.colors.paper)) {
        val wide = maxWidth >= TeaSize.wideBreakpoint
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (wide) {
                Row(
                    Modifier.widthIn(max = TeaSize.maxWideWidth).fillMaxWidth().padding(horizontal = TeaSpacing.s3, vertical = TeaSpacing.s3),
                    horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s4),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s3)) {
                        MainColumn(ui, actions, columns = 2)
                    }
                    Column(Modifier.width(TeaSize.sidebarWidth), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s3)) {
                        ScoreCard(ui.xp, ui.streak, ui.dailyMinutes, Modifier.fillMaxWidth())
                        RankingCard(ui.ranking, actions, Modifier.fillMaxWidth())
                        GuestCard(ui, actions)
                        SideActions(ui, actions)
                    }
                }
            } else {
                Column(
                    Modifier.widthIn(max = TeaSize.maxContentWidth).fillMaxWidth().padding(horizontal = TeaSpacing.s2, vertical = TeaSpacing.s3),
                    verticalArrangement = Arrangement.spacedBy(TeaSpacing.s3),
                ) {
                    MainColumn(ui, actions, columns = if (TeaTheme.isCompact) 1 else 2, statsStrip = true)
                    RankingCard(ui.ranking, actions, Modifier.fillMaxWidth())
                    GuestCard(ui, actions)
                    SideActions(ui, actions)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MainColumn(ui: HomeUi, actions: HomeActions, columns: Int, statsStrip: Boolean = false) {
    SectionHeading(
        if (ui.firstName.isNullOrBlank()) stringResource(Res.string.home_hello_guest) else stringResource(Res.string.home_hello, ui.firstName),
        large = true,
        intro = stringResource(Res.string.home_intro),
    )
    if (ui.sessionExpired) {
        Banner(stringResource(Res.string.shell_session_expired), actionText = stringResource(Res.string.shell_sign_in), onAction = actions.signIn)
    }
    when (ui.field.status) {
        "caught" -> DebriefCard(
            caught = true,
            buttonText = stringResource(if (ui.field.revealToken != null) Res.string.home_debrief_open else Res.string.home_debrief_practise),
            onClick = if (ui.field.revealToken != null) actions.openReveal else actions.practiseFocus,
        )
        "dodged" -> DebriefCard(caught = false, buttonText = stringResource(Res.string.home_debrief_practise), onClick = actions.practiseFocus)
    }
    if (statsStrip) StatsStrip(ui)
    if (ui.showPlacement) {
        PlacementCard(actions.placement)
    } else {
        ui.placementLevel?.let { LevelCard(knowsBasics(it), onHarder = actions.practise, onLesson = actions.lesson1) }
    }
    SectionHeading(stringResource(Res.string.home_chapters_title))
    ui.chapters.chunked(columns).forEachIndexed { row, items ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s3)) {
            items.forEachIndexed { col, c ->
                val i = row * columns + col
                val passwordDemo = c.chapter.target == LESSON_2_ID && c.chapter.status == ChapterStatus.AVAILABLE
                val hasDemo = passwordDemo || c.chapter.demoTarget != null
                ChapterCard(
                    c,
                    onOpen = { actions.openChapter(c) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    tilt = if (TeaTheme.isCompact) 0f else if (i % 2 == 0) -0.6f else 0.6f,
                    demoLabel = if (!hasDemo) null else stringResource(if (passwordDemo) Res.string.home_ch_demo else Res.string.challenge_demo_start),
                    onDemo = if (hasDemo) ({ actions.openDemo(c) }) else null,
                )
            }
            repeat(columns - items.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** One chapter (lesson or challenge): persona, kind, minutes, title, blurb, progress, action. */
@Composable
fun ChapterCard(
    ui: ChapterUi,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    /** A second, quieter action under the main button: the password chapter's one-minute demo. */
    demoLabel: String? = null,
    onDemo: (() -> Unit)? = null,
) {
    val c = ui.chapter
    val colors = TeaTheme.colors
    val soon = c.status == ChapterStatus.SOON || c.target == null
    val style = personaStyle(c.persona)
    TeaCard(
        modifier.then(if (soon) Modifier.semantics { disabled() } else Modifier),
        tilt = tilt,
        shadow = if (soon) 0.dp else TeaSize.shadow,
        onClick = if (soon) null else onOpen,
    ) {
        Column(Modifier.alpha(if (soon) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonaAvatar(c.persona, size = TeaSize.avatarSm)
                Pill(
                    stringResource(if (c.kind == ChapterKind.CHALLENGE) Res.string.home_kind_challenge else Res.string.home_kind_lesson),
                    background = style.soft,
                    color = colors.ink,
                )
                Text(stringResource(Res.string.home_ch_minutes, c.minutes), style = TeaTheme.type.small, color = colors.inkSoft)
            }
            Text(c.title, style = TeaTheme.type.title, color = colors.ink)
            Text(c.blurb, style = TeaTheme.type.body, color = colors.inkSoft)
        }
        Box(Modifier.weight(1f, fill = false))
        if (soon) {
            Pill(stringResource(Res.string.home_soon), background = colors.paper, color = colors.inkSoft, borderColor = colors.rule)
        } else {
            ProgressLine(ui.progress)
            TeaButton(stringResource(buttonLabel(ui.progress)), onOpen, Modifier.fillMaxWidth())
            if (onDemo != null && demoLabel != null) {
                TeaButton(demoLabel, onDemo, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
            }
        }
    }
}

private fun buttonLabel(p: ChapterProgress) = when (p) {
    ChapterProgress.NotStarted -> Res.string.home_ch_start
    is ChapterProgress.LessonSection, is ChapterProgress.ChallengeStep -> Res.string.home_ch_continue
    ChapterProgress.LessonDone, is ChapterProgress.ChallengeDone -> Res.string.home_ch_review
}

@Composable
private fun ProgressLine(p: ChapterProgress) {
    val colors = TeaTheme.colors
    when (p) {
        ChapterProgress.NotStarted ->
            Text(stringResource(Res.string.home_ch_not_started), style = TeaTheme.type.small, color = colors.inkSoft)
        is ChapterProgress.LessonSection -> {
            val total = p.total.coerceAtLeast(p.section + 1)
            TeaProgressBar(
                p.section / total.toFloat(),
                Modifier.fillMaxWidth(),
                label = stringResource(Res.string.home_ch_section, p.section + 1, total),
            )
        }
        ChapterProgress.LessonDone ->
            Text("✓ " + stringResource(Res.string.home_ch_done), style = TeaTheme.type.smallStrong, color = colors.okText)
        is ChapterProgress.ChallengeStep -> TeaProgressBar(
            p.locked / p.total.coerceAtLeast(1).toFloat(),
            Modifier.fillMaxWidth(),
            label = stringResource(Res.string.home_ch_decision, p.locked, p.total),
        )
        is ChapterProgress.ChallengeDone -> Text(
            (if (p.best.outcome == ChallengeOutcome.CONTAINED) "✓ " else "") +
                stringResource(Res.string.home_ch_best, p.best.good, p.best.total, outcomeWord(p.best.outcome)),
            style = TeaTheme.type.smallStrong,
            color = if (p.best.outcome == ChallengeOutcome.CONTAINED) colors.okText else colors.ink,
        )
    }
}

/** "Test your level": optional, until a placement result exists. */
@Composable
fun PlacementCard(onStart: () -> Unit, modifier: Modifier = Modifier) {
    TeaCard(modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.home_placement_title), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        PersonaBubble(Persona.TURTLE, Mood.NEUTRAL, stringResource(Res.string.home_placement_body), onSoft = true)
        TeaButton(stringResource(Res.string.home_placement_start), onStart)
    }
}

/** XP, streak and daily goal (sidebar). */
@Composable
fun ScoreCard(xp: Int, streak: Int, dailyMinutes: Int, modifier: Modifier = Modifier) {
    TeaCard(modifier) {
        Text(stringResource(Res.string.home_score_title), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            Stat(stringResource(Res.string.home_xp_label), stringResource(Res.string.home_xp, xp), Modifier.weight(1f))
            Stat(
                stringResource(Res.string.home_streak_label),
                pluralStringResource(Res.plurals.home_streak_days, streak, streak),
                Modifier.weight(1f),
            )
        }
        Text(
            if (streak == 0) stringResource(Res.string.home_streak_none) else stringResource(Res.string.home_goal, dailyMinutes),
            style = TeaTheme.type.small,
            color = TeaTheme.colors.inkSoft,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
        Text(value, style = TeaTheme.type.bodyStrong, color = TeaTheme.colors.ink)
    }
}

/** One card row: XP | streak | rank (one-column layouts). */
@Composable
private fun StatsStrip(ui: HomeUi) {
    val rank = (ui.ranking as? RankingUi.Ready)?.data?.me?.rank
    TeaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(TeaSpacing.s2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            Stat(stringResource(Res.string.home_xp_label), stringResource(Res.string.home_xp, ui.xp), Modifier.weight(1f))
            Stat(stringResource(Res.string.home_streak_label), pluralStringResource(Res.plurals.home_streak_days, ui.streak, ui.streak), Modifier.weight(1f))
            Stat(
                stringResource(Res.string.home_rank_label),
                if (rank != null) stringResource(Res.string.home_rank_value, rank) else "–",
                Modifier.weight(1f),
            )
        }
    }
}

/** Weekly rank, top 3 and you; offline and guest states. */
@Composable
fun RankingCard(state: RankingUi, actions: HomeActions, modifier: Modifier = Modifier) {
    TeaCard(modifier) {
        Text(stringResource(Res.string.home_rank_title), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        when (state) {
            RankingUi.Loading -> Text(stringResource(Res.string.about_loading), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
            RankingUi.Offline -> Text(stringResource(Res.string.leaderboard_offline), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
            RankingUi.Guest -> {
                Text(stringResource(Res.string.home_rank_guest), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                TeaButton(stringResource(Res.string.settings_create_account), actions.signIn, variant = TeaButtonVariant.Secondary)
            }
            is RankingUi.Ready -> {
                val me = state.data.me
                Text(
                    if (me != null) stringResource(Res.string.home_rank_value, me.rank) else stringResource(Res.string.home_rank_none),
                    style = TeaTheme.type.h3,
                    color = TeaTheme.colors.ink,
                )
                val top = state.data.top.take(3)
                top.forEach { RankRow(it, isMe = it.isMe) }
                if (me != null && top.none { it.isMe }) {
                    DashedDivider()
                    RankRow(me, isMe = true)
                }
                TeaButton(stringResource(Res.string.home_rank_full), actions.leaderboard, variant = TeaButtonVariant.Text, small = true)
            }
        }
    }
}

@Composable
private fun GuestCard(ui: HomeUi, actions: HomeActions) {
    if (ui.loggedIn || ui.sessionExpired) return
    TeaCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.home_guest_title), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        Text(stringResource(Res.string.home_guest_body), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
        TeaButton(stringResource(Res.string.home_guest_action), actions.signIn, variant = TeaButtonVariant.Secondary, enabled = ui.online)
    }
}

@Composable
private fun SideActions(ui: HomeUi, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
        TeaButton(stringResource(Res.string.home_practise), actions.practise, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
        TeaButton(stringResource(Res.string.home_about), actions.about, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
        TeaButton(stringResource(Res.string.home_settings), actions.settings, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
        TeaButton(
            stringResource(Res.string.home_suspicious), actions.suspicious, Modifier.fillMaxWidth(),
            variant = TeaButtonVariant.Secondary, icon = TeaIcons.Info,
        )
    }
}

/** Spider's debrief after a practice email: "caught" (clicked) or "dodged" (reported). Blame-free. */
@Composable
fun DebriefCard(caught: Boolean, buttonText: String, onClick: () -> Unit) {
    SpiderPanel {
        SectionHeading(stringResource(if (caught) Res.string.home_debrief_caught_title else Res.string.home_debrief_dodged_title))
        PersonaBubble(
            Persona.SPIDER,
            if (caught) Mood.SMUG else Mood.WORRIED,
            stringResource(if (caught) Res.string.home_debrief_caught_body else Res.string.home_debrief_dodged_body),
        )
        TeaButton(buttonText, onClick)
    }
}

/** "Got a suspicious email?": the same careful answer whether or not it was ours. */
@Composable
fun SuspiciousDialog(onDismiss: () -> Unit, onPractise: () -> Unit) {
    TeaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.home_suspicious),
        confirmText = stringResource(Res.string.home_suspicious_practise),
        onConfirm = onPractise,
    ) {
        SuspiciousBody()
    }
}

@Composable
fun SuspiciousBody() {
    Text(stringResource(Res.string.home_suspicious_body), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
    FourStepsList()
    Text(stringResource(Res.string.home_suspicious_never), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
}
