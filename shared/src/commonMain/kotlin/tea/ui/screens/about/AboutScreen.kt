package tea.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.generated.resources.*
import tea.i18n.Lang
import tea.model.AboutEntry
import tea.net.ApiResult
import tea.ui.components.Banner
import tea.ui.components.DashedDivider
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.screens.ErrorLine
import tea.ui.screens.onboarding.OnboardingQuestion
import tea.ui.screens.onboarding.countryRes
import tea.ui.screens.settings.DeleteAccountDialog
import tea.ui.theme.TeaTheme

/** One line of "What we know about you", already in plain words. */
data class AboutRow(val field: String, val value: String, val purpose: String)

/**
 * "What we know about you" (plan §9.2): the server's rows (`GET /me/about`, localised by the
 * server) when signed in and online, plus what this device stores. Guests and offline: device only.
 */
@Composable
fun AboutScreen(app: AppState) {
    val loggedIn by app.loggedIn.collectAsState()
    val online by app.online.collectAsState()
    val lang by app.lang.collectAsState()
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf<List<AboutEntry>?>(null) }
    var serverFailed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn, online, lang) {
        if (loggedIn && online) {
            when (val r = app.aboutRows()) {
                is ApiResult.Ok -> { server = r.value; serverFailed = false }
                else -> serverFailed = true
            }
        }
    }

    ScreenColumn(onBack = app::back) {
        SectionHeading(stringResource(Res.string.about_title), large = true, intro = stringResource(Res.string.about_intro))
        when {
            !loggedIn -> Banner(stringResource(Res.string.about_guest))
            !online || serverFailed -> Banner(stringResource(Res.string.about_offline))
            else -> {
                SectionHeading(stringResource(Res.string.about_server))
                val rows = server
                if (rows == null) {
                    Text(stringResource(Res.string.about_loading), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
                } else {
                    RowsCard(rows.map { AboutRow(it.field, it.value, it.purpose) })
                }
            }
        }
        SectionHeading(stringResource(Res.string.about_device))
        RowsCard(localRows(app))
        TeaButton(
            stringResource(if (loggedIn) Res.string.settings_delete else Res.string.settings_delete_guest),
            { deleteFailed = false; confirmDelete = true },
            variant = TeaButtonVariant.Danger,
        )
        if (deleteFailed) ErrorLine(stringResource(Res.string.settings_delete_failed))
    }
    if (confirmDelete) {
        DeleteAccountDialog(loggedIn, onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            scope.launch { deleteFailed = !app.deleteAccount() }
        }
    }
}

@Composable
fun RowsCard(rows: List<AboutRow>) {
    TeaCard(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, row ->
            if (i > 0) DashedDivider()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.field, style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
                Text(row.value, style = TeaTheme.type.body, color = TeaTheme.colors.ink)
                Text(stringResource(Res.string.about_purpose, row.purpose), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
            }
        }
    }
}

/** Everything this device stores, in plain words. */
@Composable
fun localRows(app: AppState): List<AboutRow> {
    val p by app.profile.collectAsState()
    val xp by app.xp.collectAsState()
    val mastery by app.mastery.collectAsState()
    val pending by app.pendingSync.collectAsState()
    val session by app.session.collectAsState()
    val notGiven = stringResource(Res.string.about_not_given)
    val on = stringResource(Res.string.about_yes)
    val off = stringResource(Res.string.about_no)
    val system = stringResource(Res.string.about_system)
    val flags by app.flags.collectAsState()
    // Only the ids still asked count: a learner who skipped a question that was later dropped keeps it in the list.
    val asked = OnboardingQuestion.entries.map { it.id }
    val answered = if (flags.onboardingDone) asked.size - flags.onboardingSkipped.count { it in asked } else listOf(
        p.pastExperience != null,
    ).count { it }
    val name = listOfNotNull(p.firstName, p.lastName).joinToString(" ").ifBlank { notGiven }
    val country = p.country?.let { stringResource(countryRes(it)) } ?: notGiven
    val rows = mutableListOf(
        AboutRow(stringResource(Res.string.about_local_language), "${Lang.nativeNames[p.language] ?: p.language}, $country", stringResource(Res.string.about_local_language_why)),
        AboutRow(stringResource(Res.string.about_local_name), name, stringResource(Res.string.about_local_name_why)),
        AboutRow(
            stringResource(Res.string.about_local_answers),
            stringResource(Res.string.about_local_answers_value, answered, OnboardingQuestion.entries.size),
            stringResource(Res.string.about_local_answers_why),
        ),
        AboutRow(
            stringResource(Res.string.about_local_display),
            stringResource(Res.string.about_local_display_value, p.darkMode?.let { if (it) on else off } ?: system, if (p.largeText) on else off, p.dailyMinutes),
            stringResource(Res.string.about_local_display_why),
        ),
        AboutRow(
            stringResource(Res.string.about_local_attempts),
            stringResource(Res.string.about_local_attempts_value, remember(xp, pending) { app.local.attempts.size }),
            stringResource(Res.string.about_local_attempts_why),
        ),
        AboutRow(
            stringResource(Res.string.about_local_skills),
            stringResource(Res.string.about_local_skills_value, mastery.size, xp),
            stringResource(Res.string.about_local_skills_why),
        ),
        AboutRow(
            stringResource(Res.string.about_local_progress),
            stringResource(Res.string.about_local_progress_value, remember(xp, pending) { app.local.blobKeys().size }),
            stringResource(Res.string.about_local_progress_why),
        ),
        AboutRow(stringResource(Res.string.about_local_queue), stringResource(Res.string.about_local_queue_value, pending), stringResource(Res.string.about_local_queue_why)),
    )
    session?.let {
        rows += AboutRow(stringResource(Res.string.about_local_session), stringResource(Res.string.about_local_session_value, it.email), stringResource(Res.string.about_local_session_why))
    }
    return rows
}
