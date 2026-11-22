package tea.ui.screens.practice

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.practice_support_faded
import tea.generated.resources.practice_support_hint
import tea.generated.resources.practice_support_prompted
import tea.generated.resources.practice_support_worked
import tea.generated.resources.step_ask
import tea.generated.resources.step_ask_question
import tea.generated.resources.step_emotion
import tea.generated.resources.step_emotion_question
import tea.generated.resources.step_verify
import tea.generated.resources.step_verify_question
import tea.generated.resources.step_who
import tea.generated.resources.step_who_question
import tea.model.StepKey
import tea.model.Support

/** Short step label for bars: "Who", "Feeling", "Ask", "Check". */
@Composable
fun stepLabel(step: StepKey): String = stringResource(
    when (step) {
        StepKey.WHO -> Res.string.step_who
        StepKey.EMOTION -> Res.string.step_emotion
        StepKey.ASK -> Res.string.step_ask
        StepKey.VERIFY -> Res.string.step_verify
    },
)

/** The step as the learner asks it (plan §1.1): "Who is really writing?" … */
@Composable
fun stepQuestion(step: StepKey): String = stringResource(
    when (step) {
        StepKey.WHO -> Res.string.step_who_question
        StepKey.EMOTION -> Res.string.step_emotion_question
        StepKey.ASK -> Res.string.step_ask_question
        StepKey.VERIFY -> Res.string.step_verify_question
    },
)

/** One word for the support level, shown under the top bar's step bars. */
@Composable
fun supportLabel(support: Support): String = stringResource(
    when (support) {
        Support.WORKED -> Res.string.practice_support_worked
        Support.PROMPTED -> Res.string.practice_support_prompted
        Support.HINT -> Res.string.practice_support_hint
        Support.FADED -> Res.string.practice_support_faded
    },
)
