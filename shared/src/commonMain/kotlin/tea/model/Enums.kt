package tea.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The four steps (plan §1.1). Serialised lowercase. Order matters: who, emotion, ask, verify. */
@Serializable
enum class StepKey {
    @SerialName("who") WHO,
    @SerialName("emotion") EMOTION,
    @SerialName("ask") ASK,
    @SerialName("verify") VERIFY;

    val skill: Skill
        get() = when (this) {
            WHO -> Skill.WHO
            EMOTION -> Skill.EMOTION
            ASK -> Skill.ASK
            VERIFY -> Skill.VERIFY
        }
}

@Serializable
enum class Skill {
    @SerialName("who") WHO,
    @SerialName("emotion") EMOTION,
    @SerialName("ask") ASK,
    @SerialName("verify") VERIFY,
    @SerialName("verdict") VERDICT,
    @SerialName("breadth_news") BREADTH_NEWS,
    @SerialName("breadth_image") BREADTH_IMAGE;

    val step: StepKey?
        get() = when (this) {
            WHO -> StepKey.WHO
            EMOTION -> StepKey.EMOTION
            ASK -> StepKey.ASK
            VERIFY -> StepKey.VERIFY
            else -> null
        }
}

@Serializable
enum class ItemCategory {
    @SerialName("scam") SCAM,
    @SerialName("legit") LEGIT,
}

@Serializable
enum class ItemStatus {
    @SerialName("draft") DRAFT,
    @SerialName("reviewed") REVIEWED,
}

@Serializable
enum class Confidence(val score: Double) {
    @SerialName("guessing") GUESSING(0.33),
    @SerialName("fairly_sure") FAIRLY_SURE(0.66),
    @SerialName("certain") CERTAIN(1.0),
}

/** "Trying to trick me" / "Fine" / "Not sure". */
@Serializable
enum class Verdict {
    @SerialName("trick") TRICK,
    @SerialName("fine") FINE,
    @SerialName("not_sure") NOT_SURE,
}

@Serializable
enum class Context {
    @SerialName("placement") PLACEMENT,
    @SerialName("practice") PRACTICE,
    @SerialName("review") REVIEW,
}

/** Support level per step (plan §6.4), from most to least help. */
@Serializable
enum class Support {
    @SerialName("worked") WORKED,
    @SerialName("prompted") PROMPTED,
    @SerialName("hint") HINT,
    @SerialName("faded") FADED,
}

/** Contract C4. Styles live in tea.ui.theme. */
@Serializable
enum class Persona {
    @SerialName("cat") CAT,
    @SerialName("turtle") TURTLE,
    @SerialName("dog") DOG,
    @SerialName("spider") SPIDER,
}

enum class Mood { NEUTRAL, HAPPY, WORRIED, SMUG }
