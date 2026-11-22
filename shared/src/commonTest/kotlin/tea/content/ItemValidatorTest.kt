package tea.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tea.content.ItemValidator.Rules
import tea.model.AttacksSection
import tea.model.BodyBlock
import tea.model.Hotspot
import tea.model.Hunt
import tea.model.HuntFlag
import tea.model.Item
import tea.model.Lesson
import tea.model.Recovery
import tea.model.StepKey

class ItemValidatorTest {

    private fun validate(
        vararg items: Item,
        strict: Boolean = false,
        byLang: Map<String, List<Item>> = mapOf("en" to items.toList()),
        lessons: List<Lesson> = emptyList(),
        languages: Collection<String> = byLang.keys,
    ) = ItemValidator.validate(byLang, Fixtures.denylist, strict, lessons, languages)

    private fun assertOnly(rule: String, report: ValidationReport) {
        assertTrue(report.errorsFor(rule).isNotEmpty(), "expected a '$rule' error, got:\n${report.format()}")
        assertEquals(setOf(rule), report.errors.map { it.rule }.toSet(), report.format())
    }

    private fun Item.withBody(vararg blocks: BodyBlock) = copy(email = email!!.copy(body = blocks.toList()))

    @Test
    fun validPairPasses() {
        val report = validate(Fixtures.scam(), Fixtures.legit())
        assertTrue(report.ok, report.format())
        assertTrue(report.warnings.isEmpty(), report.format())
    }

    // --- §7.3 rule: missing translation (warning, error when strict) ---

    @Test
    fun missingTranslationIsWarningUnlessStrict() {
        val byLang = mapOf("en" to listOf(Fixtures.scam()), "fr" to emptyList())
        val lenient = validate(byLang = byLang, languages = listOf("en", "fr", "es"))
        assertTrue(lenient.ok, lenient.format())
        assertEquals(2, lenient.warningsFor(Rules.MISSING_TRANSLATION).size) // fr and es

        val strict = validate(byLang = byLang, languages = listOf("en", "fr", "es"), strict = true)
        assertEquals(2, strict.errorsFor(Rules.MISSING_TRANSLATION).size)
    }

    // --- §7.3 rule: deny-listed brand (case-insensitive, word match) ---

    @Test
    fun denylistedBrandIsRejected() {
        val item = Fixtures.scam().let { it.copy(email = it.email!!.copy(fromName = "PayPal Security")) }
        assertOnly(Rules.DENYLISTED_BRAND, validate(item))
    }

    @Test
    fun denylistMatchesWholeWordsOnly() {
        // "groups" and "pickups" contain "ups" but are not the brand.
        val ok = Fixtures.scam().let { it.copy(attackerReveal = "Groups and pickups are fine.") }
        assertTrue(validate(ok).ok, validate(ok).format())
        val bad = Fixtures.scam().let { it.copy(attackerReveal = "Your UPS parcel.") }
        assertOnly(Rules.DENYLISTED_BRAND, validate(bad))
        val dotted = Fixtures.scam().let { it.copy(attackerReveal = "Sent via Post.ch today.") }
        assertTrue(validate(dotted).errorsFor(Rules.DENYLISTED_BRAND).isNotEmpty())
    }

    // --- §7.3 rule: host not on .example ---

    @Test
    fun nonExampleUrlIsRejected() {
        val item = Fixtures.scam().withBody(
            BodyBlock("text", "Dear customer,"),
            BodyBlock("link", "Log in", href = "https://swissalpbank-secure.com/login", hotspot = "hs-link"),
            BodyBlock("signature", "SwissAlpBank"),
        )
        assertOnly(Rules.HOST_NOT_EXAMPLE, validate(item))
    }

    @Test
    fun nonExampleEmailAddressIsRejected() {
        val item = Fixtures.scam().let { it.copy(email = it.email!!.copy(fromAddress = "alerts@swissalpbank.ch")) }
        assertOnly(Rules.HOST_NOT_EXAMPLE, validate(item))
    }

    @Test
    fun bareDomainInTextIsRejected() {
        val item = Fixtures.scam().copy(attackerReveal = "I registered swissalpbank-login.net yesterday.")
        assertOnly(Rules.HOST_NOT_EXAMPLE, validate(item))
    }

    @Test
    fun placeholdersAndExampleHostsAreAccepted() {
        val item = Fixtures.scam().let {
            it.copy(
                email = it.email!!.copy(fromAddress = "security@{{email_domain}}", replyTo = "help@mail.thisistea.example"),
                attackerReveal = "See www.swissalpbank.example or https://thisistea.example:8080/x, file statement.pdf.",
            )
        }
        val report = validate(item)
        assertTrue(report.ok, report.format())
    }

    @Test
    fun hostExtraction() {
        assertEquals(listOf("a.example"), ItemValidator.hostsIn("go to https://user@a.example:443/p?q=1"))
        assertEquals(listOf("b.com"), ItemValidator.hostsIn("mail x.y@b.com now"))
        assertEquals(emptyList(), ItemValidator.hostsIn("Hello {{first_name}}, write to me@{{email_domain}}. It is fine.Ok"))
    }

    // --- §7.3 rule: hotspot target does not exist ---

    @Test
    fun hotspotOnMissingBodyBlockIsRejected() {
        val item = Fixtures.scam().let { it.copy(hotspots = it.hotspots + Hotspot("hs-x", "body:7", StepKey.EMOTION, true, "x")) }
        assertOnly(Rules.HOTSPOT_TARGET_MISSING, validate(item))
    }

    @Test
    fun hotspotOnNullReplyToIsRejected() {
        val item = Fixtures.scam().let { it.copy(hotspots = it.hotspots + Hotspot("hs-r", "reply_to", StepKey.WHO, true, "x")) }
        assertOnly(Rules.HOTSPOT_TARGET_MISSING, validate(item))
        val withReplyTo = item.copy(email = item.email!!.copy(replyTo = "x@y.example"))
        assertTrue(validate(withReplyTo).ok)
    }

    // --- §7.3 rule: email item without all four steps ---

    @Test
    fun missingStepIsRejected() {
        val item = Fixtures.scam().let { it.copy(steps = it.steps - StepKey.VERIFY) }
        assertOnly(Rules.STEPS_INCOMPLETE, validate(item))
    }

    // --- §7.3 rule: step correct outside choices ---

    @Test
    fun stepCorrectOutOfRangeIsRejected() {
        val item = Fixtures.scam().let { s ->
            s.copy(steps = s.steps + (StepKey.ASK to s.steps.getValue(StepKey.ASK).copy(correct = 3)))
        }
        assertOnly(Rules.STEP_CORRECT_OUT_OF_RANGE, validate(item))
    }

    // --- §7.3 rule: fewer than 2 actions / no safe action ---

    @Test
    fun tooFewActionsIsRejected() {
        val item = Fixtures.scam().let { it.copy(actions = it.actions.take(1)) }
        assertOnly(Rules.TOO_FEW_ACTIONS, validate(item))
    }

    @Test
    fun noSafeActionIsRejected() {
        val item = Fixtures.scam().let { s -> s.copy(actions = s.actions.map { it.copy(outcome = "unsafe") }) }
        assertOnly(Rules.NO_SAFE_ACTION, validate(item))
    }

    // --- §7.3 rule: link block without hotspot ---

    @Test
    fun linkWithoutHotspotIsRejected() {
        val item = Fixtures.scam().withBody(
            BodyBlock("text", "Dear customer,"),
            BodyBlock("link", "Log in", href = "https://swissalpbank-secure.example/login"),
            BodyBlock("signature", "SwissAlpBank"),
        )
        assertOnly(Rules.LINK_WITHOUT_HOTSPOT, validate(item))
    }

    // --- §7.3 rule: held-out pair reused ---

    @Test
    fun heldOutPairReusedIsRejected() {
        val heldOut = Fixtures.scam(id = "held-01").copy(heldOut = true)
        assertOnly(Rules.HELD_OUT_PAIR_REUSED, validate(Fixtures.scam(), heldOut))

        val unique = Fixtures.scam(id = "held-02", identity = "file_sharing").copy(heldOut = true)
        assertTrue(validate(Fixtures.scam(), unique).ok)
    }

    // --- §7.3 rule: placement scam without a legit twin at the same level ---

    @Test
    fun placementScamWithoutTwinIsRejected() {
        val scam = Fixtures.scam(id = "pl-l3-scam", difficulty = 3, use = listOf("placement"))
        assertOnly(Rules.PLACEMENT_TWIN_MISSING, validate(scam))

        val wrongLevel = Fixtures.legit(id = "pl-l3-legit", twinOf = "pl-l3-scam", difficulty = 4)
        assertOnly(Rules.PLACEMENT_TWIN_MISSING, validate(scam, wrongLevel))

        val twin = Fixtures.legit(id = "pl-l3-legit", twinOf = "pl-l3-scam", difficulty = 3, use = listOf("placement"))
        assertTrue(validate(scam, twin).ok, validate(scam, twin).format())
    }

    // --- extras ---

    @Test
    fun structuralExtras() {
        assertOnly(Rules.TWIN_NOT_FOUND, validate(Fixtures.legit(twinOf = "nope")))
        assertOnly(Rules.DIFFICULTY_OUT_OF_RANGE, validate(Fixtures.scam(difficulty = 7)))
        assertOnly(Rules.DUPLICATE_ID, validate(Fixtures.scam(), Fixtures.scam()))
        assertOnly(Rules.LANGUAGE_MISMATCH, validate(byLang = mapOf("fr" to listOf(Fixtures.scam()))))
        val badRef = Fixtures.scam().withBody(
            BodyBlock("text", "Dear customer,", hotspot = "hs-ghost"),
            BodyBlock("link", "Log in", href = "https://swissalpbank-secure.example/login", hotspot = "hs-link"),
            BodyBlock("signature", "SwissAlpBank"),
        )
        assertOnly(Rules.UNKNOWN_HOTSPOT_REF, validate(badRef))
        val badLq = Fixtures.scam().let { it.copy(linkQuestion = it.linkQuestion!!.copy(correct = 5)) }
        assertOnly(Rules.LINK_QUESTION_OUT_OF_RANGE, validate(badLq))
    }

    // --- lessons ---

    private fun lesson(itemId: String, vararg hotspots: String) = Lesson(
        id = "email-01", language = "en", title = "t",
        sections = listOf(
            AttacksSection(
                Hunt(itemId, "intro", hotspots.map { HuntFlag(it, "title", "says") }, "debrief"),
                Recovery("a", "b", "c", "d", emptyList()),
            ),
        ),
    )

    @Test
    fun lessonHuntIsChecked() {
        assertTrue(validate(Fixtures.scam(), lessons = listOf(lesson("scam-01", "hs-from", "hs-link"))).ok)
        assertOnly(Rules.LESSON_HUNT_ITEM_MISSING, validate(Fixtures.scam(), lessons = listOf(lesson("ghost", "hs-from"))))
        assertOnly(Rules.LESSON_HUNT_HOTSPOT_MISSING, validate(Fixtures.scam(), lessons = listOf(lesson("scam-01", "hs-nope"))))
    }
}
