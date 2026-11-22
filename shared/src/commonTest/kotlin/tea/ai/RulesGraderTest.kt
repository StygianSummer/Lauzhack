package tea.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import tea.content.Fixtures
import tea.model.Item
import tea.model.StepContent
import tea.model.StepKey

class RulesGraderTest {
    private val grader = RulesGrader()

    private fun item(): Item {
        val base = Fixtures.scam()
        fun step(vararg cues: String) = StepContent("Prompt", "Answer", listOf("a", "b", "c"), 1, cues.toList())
        return base.copy(
            slots = base.slots.copy(emotion = listOf("urgency"), ask = "click_login", verify = "open_bank_app_yourself"),
            steps = mapOf(
                StepKey.WHO to step("lookalike domain"),
                StepKey.EMOTION to step("2 hours"),
                StepKey.ASK to step("password"),
                StepKey.VERIFY to step("Did you start a registration yourself?"),
            ),
        )
    }

    @Test
    fun matchesCuesAndSlotKeywordsPerStep() = runTest {
        val result = grader.grade(item(), "The domain looks like a lookalike, they rush me, and want me to click to log in.")
        assertEquals(true, result[StepKey.WHO])
        assertEquals(true, result[StepKey.EMOTION], "synonym of urgency")
        assertEquals(true, result[StepKey.ASK], "click from click_login")
        assertEquals(false, result[StepKey.VERIFY])
    }

    @Test
    fun verifyFromTheSlotAndStemming() {
        val result = grader.gradeNow(item(), "I'd open the banking app myself instead")
        assertTrue(result.getValue(StepKey.VERIFY), "open/bank from open_bank_app_yourself; 'banking' stems to 'bank'")
        assertFalse(result.getValue(StepKey.EMOTION))
    }

    @Test
    fun aMultiWordCueNeedsAllItsWordsAndASentenceCueTwo() {
        assertTrue(grader.gradeNow(item(), "only 2 hours left").getValue(StepKey.EMOTION))
        assertFalse(grader.gradeNow(item(), "it says hours").getValue(StepKey.EMOTION))
        // "Did you start a registration yourself?" → content words start, registration: two are enough.
        assertTrue(grader.gradeNow(item(), "I never started any registration").getValue(StepKey.VERIFY))
        assertFalse(grader.gradeNow(item(), "registration").getValue(StepKey.VERIFY))
    }

    @Test
    fun emptyOrUnrelatedTextGradesNothing() {
        assertTrue(grader.gradeNow(item(), "").values.none { it })
        assertTrue(grader.gradeNow(item(), "nice weather today").values.none { it })
    }

    @Test
    fun stemStripsCommonSuffixes() {
        assertEquals("check", RulesGrader.stem("checking"))
        assertEquals("link", RulesGrader.stem("links"))
        assertEquals("ask", RulesGrader.stem("ask"))
        assertEquals(listOf("don", "t", "click"), RulesGrader.tokens("Don't CLICK!"))
    }

    @Test
    fun bundledVariationsAreEmpty() {
        assertTrue(BundledVariations.EMPTY.variationsOf("email-bank-login-01").isEmpty())
        val v = BundledVariations(mapOf("seed" to listOf(item())))
        assertEquals(1, v.variationsOf("seed").size)
    }
}
