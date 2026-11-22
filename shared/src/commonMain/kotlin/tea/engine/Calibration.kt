package tea.engine

import kotlin.math.abs
import tea.model.Confidence

/** One answer as seen by calibration: the confidence given (null = not asked / skipped) and whether it was right. */
data class ConfidenceAnswer(val confidence: Confidence?, val correct: Boolean)

/**
 * Confidence calibration (plan §6.2). Score: guessing 0.33, fairly sure 0.66, certain 1.0
 * ([Confidence.score]).
 */
object Calibration {
    fun score(confidence: Confidence): Double = confidence.score

    /** |confidenceScore − (1 if correct else 0)|. */
    fun error(confidence: Confidence, correct: Boolean): Double = abs(score(confidence) - if (correct) 1.0 else 0.0)

    /** Mean calibration error over answers with a confidence, 0..1, lower is better. 0.0 when there are none. */
    fun mean(answers: List<ConfidenceAnswer>): Double {
        val rated = answers.filter { it.confidence != null }
        if (rated.isEmpty()) return 0.0
        return rated.sumOf { error(it.confidence!!, it.correct) } / rated.size
    }

    /** `selfRating >= 4` and placement `level <= 2`, OR any wrong answer given with `CERTAIN`. */
    fun overconfident(selfRating: Int?, level: Int, answers: List<ConfidenceAnswer>): Boolean =
        (selfRating != null && selfRating >= 4 && level <= 2) ||
            answers.any { !it.correct && it.confidence == Confidence.CERTAIN }
}
