package tea.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.SettingsResponse
import tea.net.ApiResult
import tea.ui.components.OptionRow
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaCheckbox
import tea.ui.components.TeaDialog
import tea.ui.components.TeaTextField
import tea.ui.screens.ErrorLine
import tea.ui.screens.onboarding.CountryPicker
import tea.ui.screens.onboarding.LanguagePicker
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

private val GOALS = listOf(2, 5, 10)

/** Settings (plan §9.7). */
@Composable
fun SettingsScreen(app: AppState) {
    val profile by app.profile.collectAsState()
    val loggedIn by app.loggedIn.collectAsState()
    val online by app.online.collectAsState()
    val email by app.email.collectAsState()
    val apiUrl by app.apiUrl.collectAsState()
    val scope = rememberCoroutineScope()

    var practice by remember { mutableStateOf<SettingsResponse?>(null) }
    var practiceError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    var backend by remember { mutableStateOf(app.local.apiOverride.orEmpty()) }
    var backendInvalid by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn, online) {
        if (loggedIn && online) {
            when (val r = app.practiceSettings()) {
                is ApiResult.Ok -> { practice = r.value; practiceError = false }
                else -> practiceError = true
            }
        }
    }

    ScreenColumn(onBack = app::back) {
        SectionHeading(stringResource(Res.string.settings_title), large = true)
        // Language
        Group {
            LanguagePicker(profile.language) { code -> app.updateProfile { it.copy(language = code) } }
            CountryPicker(profile.country) { c -> app.updateProfile { it.copy(country = c) } }
        }

        // Display
        Group(stringResource(Res.string.settings_display)) {
            Label(stringResource(Res.string.settings_theme))
            OptionRow(
                options = listOf(
                    stringResource(Res.string.settings_theme_system),
                    stringResource(Res.string.settings_theme_light),
                    stringResource(Res.string.settings_theme_dark),
                ),
                selected = when (profile.darkMode) { null -> 0; false -> 1; true -> 2 },
                onSelect = { i -> app.updateProfile { it.copy(darkMode = when (i) { 1 -> false; 2 -> true; else -> null }) } },
            )
            TeaCheckbox(profile.largeText, { v -> app.updateProfile { it.copy(largeText = v) } }, stringResource(Res.string.settings_large_text))
            Label(stringResource(Res.string.settings_goal))
            OptionRow(
                options = GOALS.map { stringResource(Res.string.settings_goal_minutes, it) },
                selected = GOALS.indexOf(profile.dailyMinutes).takeIf { it >= 0 },
                onSelect = { i -> app.updateProfile { it.copy(dailyMinutes = GOALS[i]) } },
                stacked = false,
            )
        }

        // Practice emails
        Group(stringResource(Res.string.settings_practice_title)) {
            Muted(stringResource(Res.string.settings_practice_intro))
            when {
                !loggedIn -> Muted(stringResource(Res.string.settings_practice_guest))
                !online || practiceError -> Muted(stringResource(Res.string.shell_needs_connection))
                practice == null -> Muted(stringResource(Res.string.about_loading))
                else -> {
                    val p = practice!!
                    if (p.practiceEmails == "off") Text(stringResource(Res.string.settings_practice_off), style = TeaTheme.type.bodyStrong, color = TeaTheme.colors.ink)
                    OptionRow(
                        options = listOf(
                            stringResource(Res.string.settings_practice_allowed),
                            stringResource(Res.string.settings_practice_paused),
                            stringResource(Res.string.settings_practice_stopped),
                        ),
                        selected = when (p.practiceEmails) { "allowed" -> 0; "paused" -> 1; "stopped" -> 2; else -> null },
                        onSelect = { i ->
                            val value = listOf("allowed", "pause", "stop")[i]
                            scope.launch {
                                when (val r = app.setPracticeEmails(value)) {
                                    is ApiResult.Ok -> practice = r.value
                                    else -> practiceError = true
                                }
                            }
                        },
                    )
                    Muted(stringResource(if (p.pending) Res.string.settings_practice_pending else Res.string.settings_practice_none_pending))
                }
            }
        }

        // Account
        Group(stringResource(Res.string.settings_account)) {
            if (loggedIn) {
                Text(stringResource(Res.string.settings_signed_in_as, email.orEmpty()), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
                TeaButton(stringResource(Res.string.settings_logout), { confirmLogout = true }, variant = TeaButtonVariant.Secondary)
            } else {
                Muted(stringResource(Res.string.settings_guest))
                TeaButton(stringResource(Res.string.settings_create_account), { app.navigate(Screen.Email) }, variant = TeaButtonVariant.Secondary, enabled = online)
            }
            TeaButton(stringResource(Res.string.home_about), { app.navigate(Screen.About) }, variant = TeaButtonVariant.Secondary)
            TeaButton(
                stringResource(if (loggedIn) Res.string.settings_delete else Res.string.settings_delete_guest),
                { deleteFailed = false; confirmDelete = true },
                variant = TeaButtonVariant.Danger,
            )
            if (deleteFailed) ErrorLine(stringResource(Res.string.settings_delete_failed))
        }

        // Backend URL (C5)
        Group(stringResource(Res.string.settings_backend)) {
            Muted(stringResource(Res.string.settings_backend_help))
            app.platform.envApi?.let { Muted(stringResource(Res.string.settings_backend_env, it)) }
            TeaTextField(
                backend, { backend = it; backendInvalid = false }, stringResource(Res.string.settings_backend),
                placeholder = app.platform.defaultApi, keyboardType = KeyboardType.Uri, mono = true,
                error = if (backendInvalid) stringResource(Res.string.settings_backend_invalid) else null,
                onImeAction = { backendInvalid = !app.setApiUrl(backend) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                TeaButton(stringResource(Res.string.settings_backend_save), { backendInvalid = !app.setApiUrl(backend) }, variant = TeaButtonVariant.Secondary, small = true)
                TeaButton(stringResource(Res.string.settings_backend_reset), { backend = ""; app.setApiUrl(null) }, variant = TeaButtonVariant.Text, small = true)
            }
            Text(stringResource(Res.string.settings_backend_active, apiUrl), style = TeaTheme.type.monoSmall, color = TeaTheme.colors.inkSoft)
        }
    }

    if (confirmLogout) {
        TeaDialog(
            onDismissRequest = { confirmLogout = false },
            title = stringResource(Res.string.settings_logout_title),
            confirmText = stringResource(Res.string.settings_logout),
            onConfirm = { confirmLogout = false; scope.launch { app.logout() } },
            dismissText = stringResource(Res.string.shell_cancel),
        ) { Muted(stringResource(Res.string.settings_logout_body)) }
    }
    if (confirmDelete) {
        DeleteAccountDialog(
            loggedIn = loggedIn,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { deleteFailed = !app.deleteAccount() }
            },
        )
    }
}

/** Confirm dialog for "Delete my account" (also used by About). */
@Composable
fun DeleteAccountDialog(loggedIn: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    TeaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (loggedIn) Res.string.settings_delete_title else Res.string.settings_delete_guest),
        confirmText = stringResource(Res.string.settings_delete_confirm),
        onConfirm = onConfirm,
        dismissText = stringResource(Res.string.shell_cancel),
        destructive = true,
    ) {
        Muted(stringResource(if (loggedIn) Res.string.settings_delete_body else Res.string.settings_delete_guest_body))
    }
}

@Composable
private fun Group(title: String? = null, content: @Composable () -> Unit) {
    TeaCard(Modifier.fillMaxWidth()) {
        if (title != null) Text(title, style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = TeaTheme.type.label, color = TeaTheme.colors.ink)

@Composable
private fun Muted(text: String) = Text(text, style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
