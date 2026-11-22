package tea.engine

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import tea.model.BodyBlock
import tea.model.EmailContent
import tea.model.Hotspot
import tea.model.ImageContent
import tea.model.Item
import tea.model.ItemAction
import tea.model.ItemCategory
import tea.model.ItemStatus
import tea.model.LinkQuestion
import tea.model.MasteryState
import tea.model.NewsContent
import tea.model.Skill
import tea.model.Slots
import tea.model.StepContent
import tea.model.StepKey
import tea.model.Support

/** Small in-code item fixtures (tests never read content/). */
object Fx {
    val NOW: Instant = Instant.parse("2026-11-21T10:00:00Z")
    val TODAY: LocalDate = LocalDate.parse("2026-11-21")

    fun email(
        id: String,
        scam: Boolean = true,
        difficulty: Int = 1,
        twinOf: String? = null,
        use: List<String> = listOf("practice"),
        identity: String = "bank-$id",
        ask: String = "click_login",
        clues: List<String> = emptyList(),
        heldOut: Boolean = false,
        expectedMs: Long = 20_000,
        cues: Boolean = false,
        whoFlag: Boolean = scam,
    ): Item = Item(
        id = id,
        language = "en",
        status = ItemStatus.DRAFT,
        kind = "email",
        category = if (scam) ItemCategory.SCAM else ItemCategory.LEGIT,
        difficulty = difficulty,
        twinOf = twinOf,
        heldOut = heldOut,
        expectedMs = expectedMs,
        use = use,
        slots = Slots(identity = identity, channel = "email", ask = ask, surfaceClues = clues),
        email = EmailContent(
            fromName = "Bank", fromAddress = "info@bank.example", subject = "Hello",
            body = listOf(BodyBlock("text", "Hi"), BodyBlock("link", "Log in", "https://x.example", "h-link")),
        ),
        hotspots = buildList {
            add(Hotspot("h-from", "from_address", StepKey.WHO, redFlag = whoFlag, explain = "odd sender"))
            add(Hotspot("h-urg", "subject", StepKey.EMOTION, redFlag = scam, explain = "urgency"))
            add(Hotspot("h-link", "link", StepKey.ASK, redFlag = false, explain = "a link"))
        },
        steps = StepKey.entries.associateWith {
            StepContent("q?", "a", listOf("x", "y", "z"), correct = 1, cues = if (cues) listOf("c") else emptyList())
        },
        linkQuestion = LinkQuestion("Log in", listOf("a", "b", "c", "d"), correct = 2),
        actions = listOf(
            ItemAction("open-app", "Open the app yourself", "safe", "ok"),
            ItemAction("click", "Click the link", "unsafe", "bad"),
        ),
    )

    fun news(id: String, real: Boolean) = Item(
        id = id, language = "en", status = ItemStatus.DRAFT, kind = "news", category = ItemCategory.LEGIT,
        difficulty = 1, use = listOf("placement"), slots = Slots("news", "web", ask = "forward"),
        news = NewsContent("Headline", "Daily", "2026-11-20", real = real),
    )

    fun image(id: String, ai: Boolean) = Item(
        id = id, language = "en", status = ItemStatus.DRAFT, kind = "image", category = ItemCategory.LEGIT,
        difficulty = 1, use = listOf("placement"), slots = Slots("photo", "web", ask = "share"),
        image = ImageContent("img.png", "a photo", aiGenerated = ai),
    )

    /** Placement pool: pairs at levels 1..6 (two at level 2), one phase_b item per level, news, image. */
    fun placementPool(): List<Item> = buildList {
        for (l in 1..6) {
            add(email("pl$l-scam", true, l, use = listOf("placement")))
            add(email("pl$l-legit", false, l, twinOf = "pl$l-scam", use = listOf("placement")))
        }
        add(email("pl2b-scam", true, 2, use = listOf("placement")))
        add(email("pl2b-legit", false, 2, twinOf = "pl2b-scam", use = listOf("placement")))
        for (l in 1..6) add(email("pb$l", true, l, use = listOf("practice", "phase_b")))
        add(news("news-1", real = false))
        add(image("img-1", ai = true))
    }

    fun mastery(p: Double = 0.5, support: Support = Scaffold.levelFor(p), level: Int = 1): Map<Skill, MasteryState> =
        Skill.entries.associateWith { MasteryState(it, p = p, level = level, support = support, updatedAt = NOW) }
}
