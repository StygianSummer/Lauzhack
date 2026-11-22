package tea.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.Profile
import tea.ui.components.ChoiceRow
import tea.ui.components.ChoiceState
import tea.ui.components.OptionRow
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaTextField
import tea.ui.screens.goNext
import tea.ui.theme.TeaTheme

/** The onboarding questions of plan §9.2.6, one per screen, in this order. Ids go into `onboarding_completed.skipped`. */
enum class OnboardingQuestion(val id: String) {
    HERE_FOR("here_for"),
    AGE("age_range"),
    CHANNELS("channels"),
    EMAIL_APP("email_app"),
    INTERESTS("interests"),
    PAST("past_experience"),
    RATING("self_rating"),
    TEXT("large_text"),
    GOAL("daily_goal"),
}

private val HERE_FOR = listOf(
    "myself" to Res.string.onboarding_here_for_myself,
    "relative" to Res.string.onboarding_here_for_relative,
    "ngo" to Res.string.onboarding_here_for_ngo,
    "company" to Res.string.onboarding_here_for_company,
)
private val AGES = listOf(
    "<25" to Res.string.onboarding_age_under25,
    "25-44" to Res.string.onboarding_age_25_44,
    "45-64" to Res.string.onboarding_age_45_64,
    "65+" to Res.string.onboarding_age_65,
)
private val CHANNELS = listOf(
    "email" to Res.string.onboarding_channel_email,
    "sms" to Res.string.onboarding_channel_sms,
    "messengers" to Res.string.onboarding_channel_messengers,
    "calls" to Res.string.onboarding_channel_calls,
    "social" to Res.string.onboarding_channel_social,
)
private val EMAIL_APPS = listOf(
    "webmail" to Res.string.onboarding_email_app_webmail,
    "outlook" to Res.string.onboarding_email_app_outlook,
    "phone_app" to Res.string.onboarding_email_app_phone,
)
private val INTERESTS = listOf(
    "banking" to Res.string.onboarding_interest_banking,
    "parcels" to Res.string.onboarding_interest_parcels,
    "jobs" to Res.string.onboarding_interest_jobs,
    "investing" to Res.string.onboarding_interest_investing,
    "donations" to Res.string.onboarding_interest_donations,
    "social" to Res.string.onboarding_interest_social,
)
private val INTERESTS_ORG = listOf(
    "invoices" to Res.string.onboarding_interest_invoices,
    "grants" to Res.string.onboarding_interest_grants,
    "shared_accounts" to Res.string.onboarding_interest_shared_accounts,
    "beneficiary_data" to Res.string.onboarding_interest_beneficiary_data,
)
private val PAST = listOf(
    "none" to Res.string.onboarding_past_none,
    "unsure_message" to Res.string.onboarding_past_unsure,
    "lost_money" to Res.string.onboarding_past_lost,
    "prefer_not" to Res.string.onboarding_past_prefer_not,
)
private val GOALS = listOf(2, 5, 10)

@Composable
fun OnboardingScreen(app: AppState, q: Int) {
    val question = OnboardingQuestion.entries.getOrNull(q)
    if (question == null) {
        LaunchedEffect(Unit) { finishOnboarding(app) }
        return
    }
    key(q) { QuestionScreen(app, q, question) }
}

private fun finishOnboarding(app: AppState) {
    app.finishOnboarding()
    val next = when {
        app.flags.value.termsVersion == null -> Screen.Terms
        app.profile.value.placement == null -> Screen.PlacementIntro
        else -> Screen.Home
    }
    app.goNext(next)
}

@Composable
private fun QuestionScreen(app: AppState, q: Int, question: OnboardingQuestion) {
    val start = app.profile.value
    var single by remember { mutableStateOf(initialSingle(question, start)) }
    var multi by remember { mutableStateOf(initialMulti(question, start)) }
    var text by remember { mutableStateOf(start.pastExperienceText.orEmpty()) }
    val total = OnboardingQuestion.entries.size

    fun next() {
        if (q + 1 < total) app.navigate(Screen.Onboarding(q + 1)) else finishOnboarding(app)
    }

    fun save() {
        app.updateProfile { p -> apply(question, p, single, multi, text) }
        app.answeredOnboardingQuestion(question.id)
        next()
    }

    val answered = when (question) {
        OnboardingQuestion.CHANNELS, OnboardingQuestion.INTERESTS -> multi.isNotEmpty()
        else -> single != null
    }

    ScreenColumn(
        title = stringResource(Res.string.onboarding_progress, q + 1, total),
        onBack = backOrNull(app),
        trailing = {
            TeaButton(
                stringResource(Res.string.onboarding_skip),
                onClick = { app.skipOnboardingQuestion(question.id); next() },
                variant = TeaButtonVariant.Text,
                small = true,
            )
        },
    ) {
        when (question) {
            OnboardingQuestion.HERE_FOR -> {
                Heading(Res.string.onboarding_here_for_title, Res.string.onboarding_here_for_intro)
                SingleChoice(HERE_FOR, single) { single = it }
            }
            OnboardingQuestion.AGE -> {
                Heading(Res.string.onboarding_age_title, Res.string.onboarding_age_intro)
                SingleChoice(AGES, single) { single = it }
            }
            OnboardingQuestion.CHANNELS -> {
                Heading(Res.string.onboarding_channels_title, Res.string.onboarding_pick_all)
                MultiChoice(CHANNELS, multi) { multi = it }
            }
            OnboardingQuestion.EMAIL_APP -> {
                Heading(Res.string.onboarding_email_app_title, Res.string.onboarding_email_app_intro)
                SingleChoice(EMAIL_APPS, single) { single = it }
            }
            OnboardingQuestion.INTERESTS -> {
                Heading(Res.string.onboarding_interests_title, Res.string.onboarding_interests_intro)
                val org = start.hereFor == "ngo" || start.hereFor == "company"
                MultiChoice(if (org) INTERESTS_ORG + INTERESTS else INTERESTS, multi) { multi = it }
            }
            OnboardingQuestion.PAST -> {
                Heading(Res.string.onboarding_past_title, Res.string.onboarding_past_intro)
                SingleChoice(PAST, single) { single = it }
                if (single != null && single != "none") {
                    TeaTextField(
                        text, { text = it }, stringResource(Res.string.onboarding_past_text),
                        helper = stringResource(Res.string.onboarding_past_text_help),
                        singleLine = false, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OnboardingQuestion.RATING -> {
                Heading(Res.string.onboarding_rating_title, Res.string.onboarding_rating_intro)
                OptionRow(
                    options = (1..5).map { it.toString() },
                    selected = single?.toIntOrNull()?.minus(1),
                    onSelect = { single = (it + 1).toString() },
                    stacked = false,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Res.string.onboarding_rating_low), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
                    Text(stringResource(Res.string.onboarding_rating_high), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft, textAlign = TextAlign.End)
                }
            }
            OnboardingQuestion.TEXT -> {
                Heading(Res.string.onboarding_text_title, Res.string.onboarding_text_intro)
                // Applies at once (plan §9.2.6).
                SingleChoice(
                    listOf("yes" to Res.string.onboarding_text_yes, "no" to Res.string.onboarding_text_no),
                    single,
                ) { v ->
                    single = v
                    app.updateProfile { it.copy(largeText = v == "yes") }
                }
            }
            OnboardingQuestion.GOAL -> {
                Heading(Res.string.onboarding_goal_title, Res.string.onboarding_goal_intro)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GOALS.forEach { m ->
                        ChoiceRow(
                            stringResource(Res.string.onboarding_goal_minutes, m),
                            if (single == m.toString()) ChoiceState.Selected else ChoiceState.Idle,
                            onClick = { single = m.toString() },
                        )
                    }
                }
            }
        }
        TeaButton(stringResource(Res.string.shell_continue), onClick = { save() }, enabled = answered, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Heading(title: StringResource, intro: StringResource) {
    SectionHeading(stringResource(title), large = true, intro = stringResource(intro))
}

@Composable
private fun SingleChoice(options: List<Pair<String, StringResource>>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (value, label) ->
            ChoiceRow(stringResource(label), if (selected == value) ChoiceState.Selected else ChoiceState.Idle, onClick = { onSelect(value) })
        }
    }
}

@Composable
private fun MultiChoice(options: List<Pair<String, StringResource>>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (value, label) ->
            ChoiceRow(
                stringResource(label),
                if (value in selected) ChoiceState.Selected else ChoiceState.Idle,
                onClick = { onChange(if (value in selected) selected - value else selected + value) },
                multi = true,
            )
        }
    }
}

private fun initialSingle(q: OnboardingQuestion, p: Profile): String? = when (q) {
    OnboardingQuestion.HERE_FOR -> p.hereFor
    OnboardingQuestion.AGE -> p.ageRange
    OnboardingQuestion.EMAIL_APP -> p.emailApp
    OnboardingQuestion.PAST -> p.pastExperience
    OnboardingQuestion.RATING -> p.selfRating?.toString()
    OnboardingQuestion.TEXT -> if (p.largeText) "yes" else null
    OnboardingQuestion.GOAL -> p.dailyMinutes.toString()
    else -> null
}

private fun initialMulti(q: OnboardingQuestion, p: Profile): Set<String> = when (q) {
    OnboardingQuestion.CHANNELS -> p.channels.toSet()
    OnboardingQuestion.INTERESTS -> p.interests.toSet()
    else -> emptySet()
}

/** Writes one answer into the profile. */
internal fun apply(q: OnboardingQuestion, p: Profile, single: String?, multi: Set<String>, text: String): Profile = when (q) {
    OnboardingQuestion.HERE_FOR -> p.copy(hereFor = single)
    OnboardingQuestion.AGE -> p.copy(ageRange = single)
    OnboardingQuestion.CHANNELS -> p.copy(channels = CHANNELS.map { it.first }.filter { it in multi })
    OnboardingQuestion.EMAIL_APP -> p.copy(emailApp = single)
    OnboardingQuestion.INTERESTS -> p.copy(interests = (INTERESTS_ORG + INTERESTS).map { it.first }.filter { it in multi })
    OnboardingQuestion.PAST -> p.copy(
        pastExperience = single,
        pastExperienceText = if (single == null || single == "none") null else text.trim().ifEmpty { null },
    )
    OnboardingQuestion.RATING -> p.copy(selfRating = single?.toIntOrNull())
    OnboardingQuestion.TEXT -> p.copy(largeText = single == "yes")
    OnboardingQuestion.GOAL -> p.copy(dailyMinutes = single?.toIntOrNull() ?: p.dailyMinutes)
}
