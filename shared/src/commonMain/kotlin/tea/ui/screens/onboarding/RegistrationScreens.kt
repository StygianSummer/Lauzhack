package tea.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.i18n.Lang
import tea.net.ApiResult
import tea.ui.components.Banner
import tea.ui.components.CodeField
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaDropdown
import tea.ui.components.TeaTextField
import tea.ui.components.OptionRow
import tea.ui.screens.ErrorLine
import tea.ui.screens.OkLine
import tea.ui.screens.goNext
import tea.ui.theme.TeaTheme

/** Back arrow only when there is somewhere to go back to. */
@Composable
internal fun backOrNull(app: AppState): (() -> Unit)? {
    val stack by app.stack.collectAsState()
    return if (stack.size > 1) app::back else null
}

fun countryRes(code: String): StringResource = when (code) {
    "CH" -> Res.string.country_CH
    "FR" -> Res.string.country_FR
    "ES" -> Res.string.country_ES
    "DE" -> Res.string.country_DE
    "IT" -> Res.string.country_IT
    "GB" -> Res.string.country_GB
    "IN" -> Res.string.country_IN
    "PK" -> Res.string.country_PK
    else -> Res.string.country_OTHER
}

// ------------------------------------------------------------------------------------------------
// 1. Language + country (plan §9.2.1). Switching re-renders at once.
// ------------------------------------------------------------------------------------------------

@Composable
fun LanguageScreen(app: AppState) {
    val profile by app.profile.collectAsState()
    val loggedIn by app.loggedIn.collectAsState()
    ScreenColumn(onBack = backOrNull(app)) {
        SectionHeading(stringResource(Res.string.language_title), large = true, intro = stringResource(Res.string.language_intro))
        LanguagePicker(profile.language) { code -> app.updateProfile { it.copy(language = code) } }
        CountryPicker(profile.country) { c -> app.updateProfile { it.copy(country = c) } }
        TeaButton(
            stringResource(Res.string.shell_continue),
            onClick = { app.goNext(if (loggedIn) app.nextAfterSignIn() else Screen.Email) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** en / fr / es as big buttons, each name in its own language. */
@Composable
fun LanguagePicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.language_label), style = TeaTheme.type.label, color = TeaTheme.colors.ink)
        OptionRow(
            options = Lang.supported.map { Lang.nativeNames.getValue(it) },
            selected = Lang.supported.indexOf(Lang.fromLocale(selected)).takeIf { it >= 0 },
            onSelect = { onSelect(Lang.supported[it]) },
        )
    }
}

@Composable
fun CountryPicker(selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TeaDropdown(
            label = stringResource(Res.string.language_country),
            options = AppState.COUNTRIES,
            selected = selected,
            onSelect = onSelect,
            optionLabel = { stringResource(countryRes(it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.language_country_help), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
    }
}

// ------------------------------------------------------------------------------------------------
// 2. Email (plan §9.2.2) + "I already have an account" (POST /auth/login)
// ------------------------------------------------------------------------------------------------

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun EmailScreen(app: AppState) {
    val online by app.online.collectAsState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(app.local.pendingEmail ?: app.email.value.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var loginMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }

    val errInvalid = Res.string.email_error_invalid
    fun send() {
        if (busy) return
        val address = email.trim()
        if (!EMAIL.matches(address)) {
            error = errInvalid; return
        }
        // Offline sign-up: no code can be sent, but the Code screen takes the demo code (AppState.verifyOffline).
        if (!online && !loginMode) {
            app.startEmailOffline(address)
            app.navigate(Screen.Code(address))
            return
        }
        busy = true; error = null
        scope.launch {
            val r = if (loginMode) app.login(address, password) else app.startEmail(address)
            busy = false
            password = ""
            when (r) {
                is ApiResult.Ok -> if (loginMode) app.goNext(app.nextAfterSignIn()) else app.navigate(Screen.Code(address))
                is ApiResult.Err -> error = when (r.code) {
                    "invalid_email" -> Res.string.email_error_invalid
                    "rate_limited" -> if (loginMode) Res.string.email_error_login_rate else Res.string.email_error_rate
                    "mail_failed" -> Res.string.email_error_mail
                    "invalid_credentials", "unauthorized" -> Res.string.email_error_credentials
                    else -> Res.string.shell_error_generic
                }
                ApiResult.Offline -> error = Res.string.shell_offline_try
            }
        }
    }

    ScreenColumn(onBack = backOrNull(app)) {
        if (loginMode) {
            SectionHeading(stringResource(Res.string.email_login_title), large = true, intro = stringResource(Res.string.email_login_intro))
        } else {
            SectionHeading(stringResource(Res.string.email_title), large = true, intro = stringResource(Res.string.email_intro))
        }
        TeaTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = stringResource(Res.string.email_label),
            placeholder = stringResource(Res.string.email_placeholder),
            keyboardType = KeyboardType.Email,
            imeAction = if (loginMode) ImeAction.Next else ImeAction.Done,
            onImeAction = { if (!loginMode) send() },
            modifier = Modifier.fillMaxWidth(),
        )
        if (loginMode) {
            TeaTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = stringResource(Res.string.email_password_label),
                password = true,
                onImeAction = { send() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { ErrorLine(stringResource(it)) }
        TeaButton(
            stringResource(if (loginMode) Res.string.email_sign_in else Res.string.email_send),
            onClick = { send() },
            enabled = !busy && (online || !loginMode) && email.isNotBlank() && (!loginMode || password.isNotEmpty()),
            modifier = Modifier.fillMaxWidth(),
        )
        TeaButton(
            stringResource(if (loginMode) Res.string.email_new_account else Res.string.email_have_account),
            onClick = { loginMode = !loginMode; error = null; password = "" },
            variant = TeaButtonVariant.Text,
        )
        if (!online) {
            Banner(stringResource(Res.string.email_offline))
            TeaButton(
                stringResource(Res.string.email_guest),
                onClick = { app.continueAsGuest() },
                variant = TeaButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// 3. Code (plan §9.2.3): 6 digits, auto-advance, "Send a new code" after 60 s.
// ------------------------------------------------------------------------------------------------

@Composable
fun CodeScreen(app: AppState, email: String) {
    val online by app.online.collectAsState()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    var resent by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var wait by remember { mutableIntStateOf(60) }
    LaunchedEffect(round) {
        wait = 60
        while (wait > 0) {
            delay(1_000); wait--
        }
    }

    fun offline(value: String) {
        if (app.verifyOffline(value)) {
            app.goNext(app.nextAfterSignIn())
        } else {
            code = ""
            error = Res.string.code_error_invalid
        }
    }

    fun verify(value: String) {
        if (busy || value.length != 6) return
        if (!online) return offline(value)
        busy = true; error = null
        scope.launch {
            val r = app.verifyCode(email, value)
            busy = false
            when (r) {
                is ApiResult.Ok -> app.goNext(app.nextAfterSignIn())
                is ApiResult.Err -> {
                    code = ""
                    error = when (r.code) {
                        "invalid_code" -> Res.string.code_error_invalid
                        "code_expired" -> Res.string.code_error_expired
                        "too_many_tries" -> Res.string.code_error_tries
                        else -> Res.string.shell_error_generic
                    }
                }
                // The connection dropped while verifying: the demo code still works on this device.
                ApiResult.Offline -> if (value == AppState.OFFLINE_DEMO_CODE) offline(value) else error = Res.string.shell_offline_try
            }
        }
    }

    ScreenColumn(onBack = backOrNull(app)) {
        SectionHeading(stringResource(Res.string.code_title), large = true, intro = stringResource(Res.string.code_intro, email))
        if (!online) Banner(stringResource(Res.string.code_offline))
        CodeField(
            value = code,
            onValueChange = { code = it; error = null },
            label = stringResource(Res.string.code_label),
            error = error?.let { stringResource(it) },
            enabled = !busy,
            onComplete = { verify(it) },
        )
        Banner(stringResource(Res.string.code_never))
        TeaButton(
            stringResource(Res.string.code_verify),
            onClick = { verify(code) },
            enabled = !busy && code.length == 6,
            modifier = Modifier.fillMaxWidth(),
        )
        if (wait > 0) {
            Text(stringResource(Res.string.code_resend_wait, wait), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
        } else {
            TeaButton(
                stringResource(Res.string.code_resend),
                onClick = {
                    scope.launch {
                        val r = app.startEmail(email)
                        resent = r is ApiResult.Ok
                        error = when (r) {
                            is ApiResult.Ok -> null
                            is ApiResult.Err -> if (r.code == "rate_limited") Res.string.email_error_rate else Res.string.email_error_mail
                            ApiResult.Offline -> Res.string.shell_offline_try
                        }
                        round++
                    }
                },
                variant = TeaButtonVariant.Secondary,
            )
        }
        if (resent) OkLine(stringResource(Res.string.code_resent))
        TeaButton(stringResource(Res.string.code_change_email), onClick = { app.back() }, variant = TeaButtonVariant.Text)
    }
}

// ------------------------------------------------------------------------------------------------
// 4. Name (plan §9.2.4)
// ------------------------------------------------------------------------------------------------

/** Default leaderboard name: "First L." */
fun defaultDisplayName(first: String, last: String): String {
    val f = first.trim()
    val l = last.trim().firstOrNull()?.uppercaseChar()
    return if (l == null) f else "$f $l."
}

@Composable
fun NameScreen(app: AppState) {
    val profile = app.profile.value
    var first by remember { mutableStateOf(profile.firstName.orEmpty()) }
    var last by remember { mutableStateOf(profile.lastName.orEmpty()) }
    var display by remember { mutableStateOf(profile.displayName.orEmpty()) }
    var displayEdited by remember { mutableStateOf(!profile.displayName.isNullOrBlank()) }
    var showError by remember { mutableStateOf(false) }
    val shownDisplay = if (displayEdited) display else defaultDisplayName(first, last)

    fun save() {
        if (first.isBlank()) {
            showError = true; return
        }
        app.updateProfile {
            it.copy(
                firstName = first.trim(),
                lastName = last.trim().ifEmpty { null },
                displayName = shownDisplay.trim().ifEmpty { defaultDisplayName(first, last) },
            )
        }
        app.goNext(app.nextAfterSignIn())
    }

    ScreenColumn(onBack = backOrNull(app)) {
        SectionHeading(stringResource(Res.string.name_title), large = true, intro = stringResource(Res.string.name_intro))
        TeaTextField(
            first, { first = it; showError = false }, stringResource(Res.string.name_first),
            error = if (showError) stringResource(Res.string.name_error_first) else null,
            imeAction = ImeAction.Next, modifier = Modifier.fillMaxWidth(),
        )
        TeaTextField(last, { last = it }, stringResource(Res.string.name_last), imeAction = ImeAction.Next, modifier = Modifier.fillMaxWidth())
        TeaTextField(
            shownDisplay, { display = it; displayEdited = true }, stringResource(Res.string.name_display),
            helper = stringResource(Res.string.name_display_help), onImeAction = { save() }, modifier = Modifier.fillMaxWidth(),
        )
        TeaButton(stringResource(Res.string.shell_continue), onClick = { save() }, modifier = Modifier.fillMaxWidth())
    }
}

// ------------------------------------------------------------------------------------------------
// 5. Password (plan §9.2.5): honest line, real reasons, nothing kept on the device.
// ------------------------------------------------------------------------------------------------

@Composable
fun PasswordScreen(app: AppState) {
    val scope = rememberCoroutineScope()
    val online by app.online.collectAsState()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }

    fun submit() {
        if (busy) return
        if (password.length < 10) {
            error = Res.string.password_error_short; return
        }
        busy = true; error = null
        val attempt = password
        scope.launch {
            val r = app.setPassword(attempt)
            password = "" // never keep it, accepted or not
            busy = false
            when (r) {
                is ApiResult.Ok -> if (r.value.accepted) app.goNext(app.nextAfterSignIn()) else error = when (r.value.reason) {
                    "too_short" -> Res.string.password_error_short
                    "breached" -> Res.string.password_error_breached
                    else -> Res.string.shell_error_generic
                }
                is ApiResult.Err -> error = Res.string.shell_error_generic
                ApiResult.Offline -> error = Res.string.shell_offline_try
            }
        }
    }

    ScreenColumn(onBack = backOrNull(app)) {
        SectionHeading(stringResource(Res.string.password_title), large = true, intro = stringResource(Res.string.password_intro))
        TeaTextField(
            password, { password = it; error = null }, stringResource(Res.string.password_label),
            password = true, helper = stringResource(Res.string.password_rule),
            error = error?.let { stringResource(it) }, onImeAction = { submit() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.password_honest), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
        TeaButton(
            stringResource(Res.string.password_save), onClick = { submit() },
            enabled = !busy && online && password.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
        )
        if (!online) Text(stringResource(Res.string.shell_needs_connection), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
    }
}
