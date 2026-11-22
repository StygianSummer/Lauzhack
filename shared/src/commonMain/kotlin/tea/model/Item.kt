package tea.model

import kotlinx.serialization.Serializable

/** Contract C2: `content/items/<kind dir>/<id>.<lang>.json` (plan §4.1). JSON keys are snake_case. */
@Serializable
data class Item(
    val id: String,
    val language: String,
    val status: ItemStatus,
    /** "email" | "news" | "image" */
    val kind: String,
    val category: ItemCategory,
    /** 1..6, ladder in plan §6.1 */
    val difficulty: Int,
    /** id of the scam this legit item mirrors (or vice versa) */
    val twinOf: String? = null,
    val heldOut: Boolean = false,
    val unsolicited: Boolean = false,
    val expectedMs: Long = 25_000,
    /** "placement", "practice", "phase_b" */
    val use: List<String> = listOf("practice"),
    val slots: Slots,
    val email: EmailContent? = null,
    val news: NewsContent? = null,
    val image: ImageContent? = null,
    val hotspots: List<Hotspot> = emptyList(),
    /** all four required for kind == email */
    val steps: Map<StepKey, StepContent> = emptyMap(),
    val linkQuestion: LinkQuestion? = null,
    /** >= 2, at least one safe (email items) */
    val actions: List<ItemAction> = emptyList(),
    /** Spider's line; for legit items: Turtle's "why it's fine" */
    val attackerReveal: String? = null,
    /** slot ask used to pick Dog's card; defaults to slots.ask */
    val resourceAsk: String? = null,
) {
    /** Seed root: an item and its twin share the same seed (plan §6.5). */
    val seed: String get() = twinOf ?: id
    val isScam: Boolean get() = category == ItemCategory.SCAM
    fun usedFor(u: String): Boolean = u in use
}

@Serializable
data class Slots(
    val identity: String,
    val channel: String,
    val emotion: List<String> = emptyList(),
    val ask: String,
    val surfaceClues: List<String> = emptyList(),
    val verify: String = "",
)

@Serializable
data class EmailContent(
    val fromName: String,
    val fromAddress: String,
    val replyTo: String? = null,
    val to: String = "{{email}}",
    val subject: String,
    /** ISO-8601 duration ago, e.g. "-PT2H" */
    val received: String = "-PT1H",
    val body: List<BodyBlock>,
    val attachments: List<String> = emptyList(),
)

/** t: "text" | "link" | "signature". For links, v is the shown text and href the real target. */
@Serializable
data class BodyBlock(
    val t: String,
    val v: String,
    val href: String? = null,
    val hotspot: String? = null,
)

/**
 * target: "from_name" | "from_address" | "reply_to" | "subject" | "body:<index>" | "link" |
 * "attachment:<index>". "link" means the first body block with t == "link".
 */
@Serializable
data class Hotspot(
    val id: String,
    val target: String,
    val step: StepKey,
    val redFlag: Boolean,
    val explain: String,
)

@Serializable
data class StepContent(
    val prompt: String,
    val answer: String,
    val choices: List<String>,
    val correct: Int,
    val cues: List<String> = emptyList(),
)

@Serializable
data class LinkQuestion(
    val shownText: String,
    val options: List<String>,
    val correct: Int,
)

/** outcome: "safe" | "unsafe" | "neutral" */
@Serializable
data class ItemAction(
    val id: String,
    val label: String,
    val outcome: String,
    val consequence: String,
) {
    val isSafe: Boolean get() = outcome == "safe"
    val isUnsafe: Boolean get() = outcome == "unsafe"
}

@Serializable
data class NewsContent(
    val headline: String,
    val source: String,
    val date: String,
    val real: Boolean,
    val tells: List<String> = emptyList(),
)

@Serializable
data class ImageContent(
    val file: String,
    val alt: String,
    val aiGenerated: Boolean,
    val tells: List<String> = emptyList(),
)

/** `files/content/index.json`, written by the Gradle task :shared:syncContent (plan §3.1). */
@Serializable
data class ContentIndex(
    /** "<kind dir>/<id>", e.g. "email/email-bank-login-01" */
    val items: List<String> = emptyList(),
    val lessons: List<String> = emptyList(),
    val languages: List<String> = listOf("en"),
)
