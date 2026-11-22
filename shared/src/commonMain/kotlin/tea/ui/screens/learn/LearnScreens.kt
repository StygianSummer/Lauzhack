package tea.ui.screens.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import tea.app.Screen
import tea.app.TeaApp
import tea.app.AppState
import tea.generated.resources.Res
import tea.generated.resources.practice_title
import tea.model.Item
import tea.ui.components.TopBar
import tea.ui.screens.lesson.LessonContent
import tea.ui.screens.placement.PlacementContent
import tea.ui.screens.placement.PlacementIntroContent
import tea.ui.screens.placement.PlacementResultContent
import tea.ui.screens.practice.PracticeController
import tea.ui.screens.practice.PracticeRunner
import tea.ui.screens.practice.PracticeSummaryContent
import tea.ui.theme.TeaTheme

/*
 * Entry points of the learning screens (plan §9.3–9.6), called by the app shell's router.
 * Implementations live in the placement/, lesson/ and practice/ packages.
 * Blob keys: see LearnKeys.
 */

/** "Let's see where to start. About 3 minutes. No score." → Screen.Placement */
@Composable
fun PlacementIntroScreen(app: TeaApp) {
    PlacementIntroContent(app)
}

/** The adaptive placement test (resumable); at the end navigates (root) to Screen.PlacementResult. */
@Composable
fun PlacementScreen(app: TeaApp) {
    PlacementContent(app)
}

/** One strength + one focus sentence; "Start lesson 1" → Home, then Lesson(email-01, 0). */
@Composable
fun PlacementResultScreen(app: TeaApp) {
    PlacementResultContent(app)
}

/** Renders section [section] of lesson [id], including the practice and readiness sections. */
@Composable
fun LessonScreen(app: AppState, id: String, section: Int) {
    val field by app.field.collectAsState()
    LessonContent(app, id, section, pending = id in field.pendingGotchas)
}

@Composable
fun LessonScreen(app: TeaApp, id: String, section: Int) {
    LessonContent(app, id, section)
}

/** "Practise again": one practice section from the selector, outside a lesson. */
@Composable
fun FreePracticeScreen(app: TeaApp, focusWeakest: Boolean) {
    val lang by app.lang.collectAsState()
    val pool by produceState<List<Item>?>(null, lang) { value = runCatching { app.content.items(lang) }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().background(TeaTheme.colors.paper)) {
        TopBar(title = stringResource(Res.string.practice_title), onBack = { app.back() })
        val items = pool ?: return@Column
        val controller = remember(items) {
            PracticeController(app, items, PracticeController.FREE_OWNER, focusWeakest = focusWeakest)
        }
        PracticeRunner(app, controller, Modifier.weight(1f)) {
            LaunchedEffect(Unit) {
                app.navigateRoot(Screen.Home)
                app.navigate(Screen.PracticeSummary)
            }
            Box(Modifier.fillMaxSize())
        }
    }
}

/** The last practice section's summary (persisted, so it survives navigation and reloads). */
@Composable
fun PracticeSummaryScreen(app: TeaApp) {
    PracticeSummaryContent(app)
}
