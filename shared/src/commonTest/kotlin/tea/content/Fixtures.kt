package tea.content

import tea.model.BodyBlock
import tea.model.EmailContent
import tea.model.Hotspot
import tea.model.Item
import tea.model.ItemAction
import tea.model.ItemCategory
import tea.model.ItemStatus
import tea.model.LinkQuestion
import tea.model.Slots
import tea.model.StepContent
import tea.model.StepKey

/** A small valid scam/legit pair that tests mutate to plant one problem at a time. */
object Fixtures {
    val denylist = listOf("google", "paypal", "post.ch", "ups")

    private fun steps() = StepKey.entries.associateWith {
        StepContent(prompt = "Prompt", answer = "Answer", choices = listOf("a", "b", "c"), correct = 1, cues = listOf("cue"))
    }

    fun scam(
        id: String = "scam-01",
        difficulty: Int = 2,
        identity: String = "bank",
        ask: String = "click_login",
        use: List<String> = listOf("practice"),
    ) = Item(
        id = id, language = "en", status = ItemStatus.DRAFT, kind = "email", category = ItemCategory.SCAM,
        difficulty = difficulty, use = use,
        slots = Slots(identity = identity, channel = "email", emotion = listOf("fear"), ask = ask),
        email = EmailContent(
            fromName = "SwissAlpBank", fromAddress = "alerts@swissalpbank-secure.example",
            subject = "Urgent, {{first_name}}", to = "{{email}}",
            body = listOf(
                BodyBlock("text", "Dear customer,"),
                BodyBlock("link", "Log in", href = "https://swissalpbank-secure.example/login", hotspot = "hs-link"),
                BodyBlock("signature", "SwissAlpBank"),
            ),
        ),
        hotspots = listOf(
            Hotspot("hs-from", "from_address", StepKey.WHO, true, "Lookalike domain."),
            Hotspot("hs-link", "link", StepKey.ASK, true, "Goes to swissalpbank-secure.example."),
            Hotspot("hs-greet", "body:0", StepKey.WHO, true, "Generic greeting."),
        ),
        steps = steps(),
        linkQuestion = LinkQuestion("Log in", listOf("swissalpbank.example", "swissalpbank-secure.example"), 1),
        actions = listOf(
            ItemAction("open-app", "Open the app yourself", "safe", "Nothing happened."),
            ItemAction("click", "Click the link", "unsafe", "Password stolen."),
        ),
        attackerReveal = "Got you.",
    )

    fun legit(id: String = "legit-01", twinOf: String? = "scam-01", difficulty: Int = 2, use: List<String> = listOf("practice")) =
        scam(id = id, difficulty = difficulty, use = use).copy(category = ItemCategory.LEGIT, twinOf = twinOf)
}
