package tea.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.Profile
import tea.ui.components.ChoiceRow
import tea.ui.components.ChoiceState
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaTextField
import tea.ui.screens.goNext

/**
 * The onboarding questions, one per screen, in this order. Ids go into `onboarding_completed.skipped`.
 * Cut to two for the short demo (2026-11-22): text size first, so the next screen is already readable,
 * then the past-experience question (incl. "I lost money"). The other profile fields of plan §9.2.6
 * (here for, age, channels, email app, interests, self-rating, daily goal) are no longer asked and
 * keep their defaults.
 */
enum class OnboardingQuestion(val id: String) {
    TEXT("large_text"),
    PAST("past_experience"),
}

private val PAST = listOf(
    "none" to Res.string.onboarding_past_none,
    "unsure_message" to Res.string.onboarding_past_unsure,
    "lost_money" to Res.string.onboarding_past_lost,
    "prefer_not" to Res.string.onboarding_past_prefer_not,
)

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
        else -> Screen.Home
    }
    app.goNext(next)
}

@Composable
private fun QuestionScreen(app: AppState, q: Int, question: OnboardingQuestion) {
    val start = app.profile.value
    var single by remember { mutableStateOf(initialSingle(question, start)) }
    var text by remember { mutableStateOf(start.pastExperienceText.orEmpty()) }
    val total = OnboardingQuestion.entries.size

    fun next() {
        if (q + 1 < total) app.navigate(Screen.Onboarding(q + 1)) else finishOnboarding(app)
    }

    fun save() {
        app.updateProfile { p -> apply(question, p, single, text) }
        app.answeredOnboardingQuestion(question.id)
        next()
    }

    val answered = single != null

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

private fun initialSingle(q: OnboardingQuestion, p: Profile): String? = when (q) {
    OnboardingQuestion.PAST -> p.pastExperience
    OnboardingQuestion.TEXT -> if (p.largeText) "yes" else null
}

/** Writes one answer into the profile. */
internal fun apply(q: OnboardingQuestion, p: Profile, single: String?, text: String): Profile = when (q) {
    OnboardingQuestion.PAST -> p.copy(
        pastExperience = single,
        pastExperienceText = if (single == null || single == "none") null else text.trim().ifEmpty { null },
    )
    OnboardingQuestion.TEXT -> p.copy(largeText = single == "yes")
}
