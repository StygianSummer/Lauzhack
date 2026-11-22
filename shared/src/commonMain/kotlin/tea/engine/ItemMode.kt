package tea.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tea.model.AttemptAnswers
import tea.model.Item
import tea.model.StepKey
import tea.model.Verdict

/**
 * How one item is shown by `ItemRenderer` (plan §9.3). Placement uses one single-question mode per
 * item; practice always uses [FULL] (steps per support + verdict + next action).
 */
@Serializable
enum class ItemMode {
    /** Placement phase A: "Trying to trick you, or fine?" (+ "How sure are you?"). */
    @SerialName("verdict") VERDICT,

    /** Placement phase B: "Tap the warning sign" on the email (all hotspots + 2 decoys tappable). */
    @SerialName("tap_hotspot") TAP_HOTSPOT,

    /** Placement phase B: "Where does this link really go?" ([Item.linkQuestion]). */
    @SerialName("link") LINK,

    /** Placement phase B: "Which feeling is pushed?" (`steps.emotion` choices). */
    @SerialName("emotion") EMOTION,

    /** Placement phase B: "What do you do next?" ([Item.actions]; right = a `safe` action). */
    @SerialName("action") ACTION,

    /** Placement phase C: "Would you forward this?" (right = forward only if the news is real). */
    @SerialName("news") NEWS,

    /** Placement phase C: "Real photo or AI-generated?". */
    @SerialName("image") IMAGE,

    /** Practice: the four steps per support level, then verdict, then next action. */
    @SerialName("full") FULL,
}

/**
 * Per-mode correctness rules. A `null` answer always means "I don't know yet" and is wrong.
 * The answers are read from [AttemptAnswers] so the same object can be stored in the [tea.model.Attempt].
 */
object Correctness {
    /** Decoy tap regions the UI adds in [ItemMode.TAP_HOTSPOT] mode; they count as non-red-flag taps. */
    const val DECOY_GREETING = "decoy:greeting"
    const val DECOY_SIGNATURE = "decoy:signature"
    val DECOYS: List<String> = listOf(DECOY_GREETING, DECOY_SIGNATURE)

    /**
     * Hotspot rule (plan §6.2 phase B): at least one red-flag hotspot with step `who` tapped, and no more
     * than one tap on anything that is not a red flag (non-red-flag hotspots, decoys, unknown ids).
     * Taps on red flags of other steps are neither hits nor penalties. Duplicate ids count once.
     */
    fun hotspot(item: Item, tapped: Collection<String>): Boolean {
        val taps = tapped.toSet()
        val redFlags = item.hotspots.filter { it.redFlag }.map { it.id }.toSet()
        val whoFlags = item.hotspots.filter { it.redFlag && it.step == StepKey.WHO }.map { it.id }.toSet()
        val hits = taps.count { it in whoFlags }
        val nonFlags = taps.count { it !in redFlags }
        return hits >= 1 && nonFlags <= 1
    }

    /** Link question: the chosen option index equals [tea.model.LinkQuestion.correct]. */
    fun link(item: Item, choice: Int?): Boolean {
        val q = item.linkQuestion ?: return false
        return choice != null && choice == q.correct
    }

    /** A step's multiple-choice question: chosen index equals `steps.<step>.correct`. */
    fun step(item: Item, step: StepKey, choice: Int?): Boolean {
        val content = item.steps[step] ?: return false
        return choice != null && choice == content.correct
    }

    fun emotion(item: Item, choice: Int?): Boolean = step(item, StepKey.EMOTION, choice)

    /** Next action: right when the chosen action's outcome is `safe`. */
    fun action(item: Item, actionId: String?): Boolean =
        actionId != null && item.actions.any { it.id == actionId && it.isSafe }

    /** News: right = forward only if the news is real (and do not forward if it is not). */
    fun news(item: Item, forward: Boolean?): Boolean {
        val n = item.news ?: return false
        return forward != null && forward == n.real
    }

    /** Image: right when the "AI-generated?" answer matches. */
    fun image(item: Item, aiGenerated: Boolean?): Boolean {
        val img = item.image ?: return false
        return aiGenerated != null && aiGenerated == img.aiGenerated
    }

    /** Verdict: TRICK for scams, FINE for legit; NOT_SURE / null are wrong. */
    fun verdict(item: Item, verdict: Verdict?): Boolean = Xp.verdictCorrect(item, verdict)

    /**
     * The single "right or wrong" for an item shown in [mode]. For [ItemMode.FULL] this is the verdict
     * (what [tea.model.Attempt.correct] stores); step and action results are handled by [Practice].
     */
    fun isCorrect(item: Item, mode: ItemMode, answers: AttemptAnswers): Boolean = when (mode) {
        ItemMode.VERDICT, ItemMode.FULL -> verdict(item, answers.verdict)
        ItemMode.TAP_HOTSPOT -> hotspot(item, answers.hotspotsTapped)
        ItemMode.LINK -> link(item, answers.linkChoice)
        ItemMode.EMOTION -> emotion(item, answers.stepChoices[StepKey.EMOTION])
        ItemMode.ACTION -> action(item, answers.actionId)
        ItemMode.NEWS -> news(item, answers.newsForward)
        ItemMode.IMAGE -> image(item, answers.imageAi)
    }

    /** Does [item] carry the content needed to be shown in [mode]? */
    fun supports(item: Item, mode: ItemMode): Boolean = when (mode) {
        ItemMode.VERDICT, ItemMode.FULL -> item.kind == "email" && item.email != null
        ItemMode.TAP_HOTSPOT -> item.hotspots.any { it.redFlag && it.step == StepKey.WHO }
        ItemMode.LINK -> item.linkQuestion?.options?.isNotEmpty() == true
        ItemMode.EMOTION -> item.steps[StepKey.EMOTION]?.choices?.isNotEmpty() == true
        ItemMode.ACTION -> item.actions.any { it.isSafe }
        ItemMode.NEWS -> item.kind == "news" && item.news != null
        ItemMode.IMAGE -> item.kind == "image" && item.image != null
    }
}
