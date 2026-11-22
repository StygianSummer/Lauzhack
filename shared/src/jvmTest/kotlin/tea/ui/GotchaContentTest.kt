package tea.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.time.Instant
import tea.model.GotchaResponse
import tea.model.StepKey
import tea.ui.screens.GotchaContent
import tea.ui.theme.TeaTheme

@OptIn(ExperimentalTestApi::class)
class GotchaContentTest {
    private val response = GotchaResponse(
        sendId = "gallery-send",
        lessonId = "email-01",
        templateId = "gallery-sample",
        clickedAt = Instant.parse("2026-11-21T10:00:00Z"),
        subject = "A quick check about your account",
        text = "Please review the practice page.",
        steps = mapOf(
            StepKey.WHO to "Check the real sender.",
            StepKey.EMOTION to "Notice urgency.",
            StepKey.ASK to "Identify the request.",
            StepKey.VERIFY to "Use a route you control.",
        ),
        hints = listOf("A familiar name is not proof."),
    )

    @Test
    fun yesBranchShowsFactsAndFourStepInformation() = runComposeUiTest {
        setContent {
            var answer by remember { mutableStateOf<String?>(null) }
            var reason by remember { mutableStateOf<String?>(null) }
            TeaTheme(dark = false) {
                GotchaContent(
                    data = response,
                    clickedWhen = "yesterday evening",
                    answer = answer,
                    reason = reason,
                    onAnswer = { answer = it },
                    onReason = { reason = it },
                    scrollEnabled = false,
                    animateTransition = false,
                )
            }
        }

        onNodeWithText("I sent you a little something. Take a good look.")
            .assert(hasText("I sent you a little something. Take a good look."))
        onNodeWithText("Do you know this email?").assert(hasText("Do you know this email?"))
        onNodeWithText("Does it sound familiar? Did it look real to you?")
            .assert(hasText("Does it sound familiar? Did it look real to you?"))
        onNodeWithText("Yes, I know it. It looked real").performClick()

        onNodeWithText("Aha! You fell for it. You clicked my link. I sent you that email: it was a practice trap.")
            .assert(hasText("Aha! You fell for it. You clicked my link. I sent you that email: it was a practice trap."))
        onNodeWithText("You clicked it yesterday evening.").assert(hasText("You clicked it yesterday evening."))
        onNodeWithText("The email: “A quick check about your account”")
            .assert(hasText("The email: “A quick check about your account”"))
        onNodeWithText("What you gave me: one click, and I know it worked. That's all a real scammer needs to start.")
            .assert(hasText("What you gave me: one click, and I know it worked. That's all a real scammer needs to start."))
        onNodeWithText("Your four checks").assert(hasText("Your four checks"))
        onNodeWithText("Check the real sender.").assert(hasText("Check the real sender."))
        onNodeWithText("Notice urgency.").assert(hasText("Notice urgency."))
        onNodeWithText("Identify the request.").assert(hasText("Identify the request."))
        onNodeWithText("Use a route you control.").assert(hasText("Use a route you control."))
        onNodeWithText("Continue to lesson").assert(hasText("Continue to lesson"))
    }

    @Test
    fun noBranchAsksWhyThenShowsFactsAndFourStepInformation() = runComposeUiTest {
        setContent {
            var answer by remember { mutableStateOf<String?>(null) }
            var reason by remember { mutableStateOf<String?>(null) }
            TeaTheme(dark = true) {
                GotchaContent(
                    data = response,
                    clickedWhen = "yesterday evening",
                    answer = answer,
                    reason = reason,
                    onAnswer = { answer = it },
                    onReason = { reason = it },
                    scrollEnabled = false,
                    animateTransition = false,
                )
            }
        }

        onNodeWithText("No, I've never seen it").performClick()
        onNodeWithText("Never seen it? It landed in your inbox, and the link was clicked yesterday evening. So why did it work on you?")
            .assert(
                hasText(
                    "Never seen it? It landed in your inbox, and the link was clicked yesterday evening. So why did it work on you?",
                ),
            )
        onNodeWithText("Why do you think it worked?").assert(hasText("Why do you think it worked?"))
        onNodeWithText("I trusted who it seemed to come from")
            .assert(hasText("I trusted who it seemed to come from"))
        onNodeWithText("It made me feel I had to act fast").assert(hasText("It made me feel I had to act fast"))
        onNodeWithText("What it asked for seemed normal").assert(hasText("What it asked for seemed normal"))
        onNodeWithText("I didn't check it another way").assert(hasText("I didn't check it another way"))
        onNodeWithText("I trusted who it seemed to come from").performClick()

        onNodeWithText("I borrowed a familiar name. The real sender is the first clue.")
            .assert(hasText("I borrowed a familiar name. The real sender is the first clue."))
        onNodeWithText("You clicked it yesterday evening.").assert(hasText("You clicked it yesterday evening."))
        onNodeWithText("The email: “A quick check about your account”")
            .assert(hasText("The email: “A quick check about your account”"))
        onNodeWithText("What you gave me: one click, and I know it worked. That's all a real scammer needs to start.")
            .assert(hasText("What you gave me: one click, and I know it worked. That's all a real scammer needs to start."))
        onNodeWithText("Your four checks").assert(hasText("Your four checks"))
        onNodeWithText("Check the real sender.").assert(hasText("Check the real sender."))
        onNodeWithText("Notice urgency.").assert(hasText("Notice urgency."))
        onNodeWithText("Identify the request.").assert(hasText("Identify the request."))
        onNodeWithText("Use a route you control.").assert(hasText("Use a route you control."))
        onNodeWithText("A clue to keep").assert(hasText("A clue to keep"))
        onNodeWithText("Continue to lesson").assert(hasText("Continue to lesson"))
    }
}
