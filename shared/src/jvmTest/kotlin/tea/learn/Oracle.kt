package tea.learn

import tea.engine.ItemMode
import tea.engine.PlacementStep
import tea.model.AttemptAnswers
import tea.model.Confidence
import tea.model.StepKey
import tea.model.Verdict
import tea.ui.screens.practice.ItemAnswer

/** Right answers for placement steps (tests and screenshots). */
object Oracle {
    /** The right answer for [step], given with confidence CERTAIN and fast. */
    fun right(step: PlacementStep): ItemAnswer {
        val item = step.item
        val answers = when (step.mode) {
            ItemMode.VERDICT, ItemMode.FULL -> AttemptAnswers(verdict = if (item.isScam) Verdict.TRICK else Verdict.FINE)
            ItemMode.TAP_HOTSPOT -> AttemptAnswers(hotspotsTapped = listOf(item.hotspots.first { it.redFlag && it.step == StepKey.WHO }.id))
            ItemMode.LINK -> AttemptAnswers(linkChoice = item.linkQuestion!!.correct)
            ItemMode.EMOTION -> AttemptAnswers(stepChoices = mapOf(StepKey.EMOTION to item.steps.getValue(StepKey.EMOTION).correct))
            ItemMode.ACTION -> AttemptAnswers(actionId = item.actions.first { it.isSafe }.id)
            ItemMode.NEWS -> AttemptAnswers(newsForward = item.news!!.real)
            ItemMode.IMAGE -> AttemptAnswers(imageAi = item.image!!.aiGenerated)
        }
        return ItemAnswer(answers, Confidence.CERTAIN, ms = 4_000)
    }
}
