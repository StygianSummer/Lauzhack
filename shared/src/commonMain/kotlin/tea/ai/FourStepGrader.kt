package tea.ai

import tea.model.Item
import tea.model.StepKey

/**
 * Grades the optional "Why?" free text of a practice item against the four steps (plan §11).
 * Result: per step, whether the text touches on it. In MVP 0 this is shown as ticks only and never
 * changes mastery.
 */
interface FourStepGrader {
    suspend fun grade(item: Item, text: String): Map<StepKey, Boolean>
}

/**
 * The default grader: no AI, no network. Per step, true when the text matches one of
 * `steps.<step>.cues` or a keyword taken from the item's slots:
 * - who: `slots.identity` and the surface clues about the sender (`lookalike_sender`, `reply_to`...);
 * - emotion: `slots.emotion` (plus a few common synonyms, e.g. urgency → hurry, rush, deadline);
 * - ask: `slots.ask` (e.g. `click_login` → click, login);
 * - verify: `slots.verify` (e.g. `type_address_yourself` → type, address, yourself).
 *
 * Matching is on normalised words (lower case, letters and digits only) stemmed by simple suffix
 * stripping. A cue of up to three content words must match all of them; a longer cue (a sentence,
 * as some items use) matches when at least two of its content words appear.
 */
class RulesGrader : FourStepGrader {

    override suspend fun grade(item: Item, text: String): Map<StepKey, Boolean> = gradeNow(item, text)

    /** Non-suspending version, for tests and UI previews. */
    fun gradeNow(item: Item, text: String): Map<StepKey, Boolean> {
        val words = tokens(text).toSet()
        if (words.isEmpty()) return StepKey.entries.associateWith { false }
        return StepKey.entries.associateWith { step ->
            val cues = item.steps[step]?.cues.orEmpty()
            cues.any { cueMatches(it, words) } || slotKeywords(item, step).any { it in words }
        }
    }

    private fun cueMatches(cue: String, words: Set<String>): Boolean {
        val cueWords = tokens(cue).filter { it !in STOP }
        if (cueWords.isEmpty()) return false
        return if (cueWords.size <= 3) cueWords.all { it in words } else cueWords.count { it in words } >= 2
    }

    /** Stemmed keywords for [step] from the item's slots (stop words removed). */
    internal fun slotKeywords(item: Item, step: StepKey): Set<String> {
        val slots = item.slots
        val raw: List<String> = when (step) {
            StepKey.WHO -> listOf(slots.identity) + slots.surfaceClues.filter { clue -> WHO_CLUES.any { clue.contains(it) } } +
                listOf("sender", "address", "domain", "lookalike", "fake")
            StepKey.EMOTION -> slots.emotion + slots.emotion.flatMap { EMOTION_SYNONYMS[it].orEmpty() }
            StepKey.ASK -> listOf(slots.ask)
            StepKey.VERIFY -> listOf(slots.verify)
        }
        return raw.flatMap { tokens(it.replace('_', ' ')) }.filter { it !in STOP && it.length >= 3 }.toSet()
    }

    companion object {
        private val WHO_CLUES = listOf("sender", "reply_to", "display_name", "lookalike", "domain", "hijacked", "thread")

        private val EMOTION_SYNONYMS = mapOf(
            "urgency" to listOf("urgent", "hurry", "rush", "deadline", "quick", "fast", "now", "pressure", "clock"),
            "fear" to listOf("scared", "afraid", "worry", "worried", "threat", "panic", "blocked", "lose"),
            "greed" to listOf("prize", "money", "win", "reward", "free"),
            "curiosity" to listOf("curious", "wonder"),
            "authority" to listOf("boss", "ceo", "manager", "official", "order"),
            "helpfulness" to listOf("help", "favour", "favor"),
            "trust" to listOf("trust", "familiar", "known"),
            "guilt" to listOf("guilty", "sorry"),
            "social_proof" to listOf("everyone", "share"),
        )

        /** Words too common to count as evidence on their own. */
        private val STOP: Set<String> = setOf(
            "the", "and", "you", "your", "yourself", "for", "with", "this", "that", "are", "not", "but", "any", "can",
            "from", "what", "who", "how", "did", "does", "doe", "there", "is", "it", "to", "a", "of", "or", "in", "on",
            "be", "an", "do", "if", "app", "email", "mail", "none", "one", "they", "them", "its", "has", "have", "into",
        ).map { stem(it) }.toSet()

        /** Lower case, split on anything that is not a letter or digit, then [stem]. */
        fun tokens(text: String): List<String> =
            text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map(::stem)

        /** Simple suffix stripping: "checking" → "check", "links" → "link", "worried" → "worri". */
        fun stem(word: String): String {
            var w = word
            for (suffix in listOf("ing", "ed", "es", "ly", "s")) {
                if (w.length > suffix.length + 2 && w.endsWith(suffix)) {
                    w = w.dropLast(suffix.length)
                    break
                }
            }
            return w
        }
    }
}
