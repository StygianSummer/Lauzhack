package tea.ui.components

import tea.model.BodyBlock
import tea.model.EmailContent
import tea.model.Hotspot
import tea.model.StepKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailRegionsTest {
    private val email = EmailContent(
        fromName = "Alpine Savings",
        fromAddress = "support@alpine-savings-secure.example",
        subject = "Confirm",
        body = listOf(
            BodyBlock("text", "Dear customer,"),
            BodyBlock("text", "Within 2 hours.", hotspot = "urgency"),
            BodyBlock("link", "Confirm", href = "https://x.example"),
            BodyBlock("signature", "Alpine Savings"),
        ),
        attachments = listOf("a.pdf"),
    )
    private fun h(id: String, target: String) = Hotspot(id, target, StepKey.WHO, true, "")

    @Test
    fun resolvesTargets() {
        assertEquals(EmailPart.Body(2), email.partFor("link"))
        assertEquals(EmailPart.FromAddress, email.partFor("from_address"))
        assertEquals(EmailPart.Attachment(0), email.partFor("attachment:0"))
        assertNull(email.partFor("reply_to"))
        assertNull(email.partFor("body:9"))
    }

    @Test
    fun mapsHotspotsBlockIdsAndDecoys() {
        val regions = emailRegions(email, listOf(h("sender", "from_address"), h("link", "link"), h("urgency", "subject")), decoys = true)
        assertEquals("sender", regions[EmailPart.FromAddress])
        assertEquals("link", regions[EmailPart.Body(2)])
        assertEquals("urgency", regions[EmailPart.Subject])
        assertEquals("urgency", regions[EmailPart.Body(1)])
        assertEquals(EmailDecoys.GREETING, regions[EmailPart.Body(0)])
        assertEquals(EmailDecoys.SIGNATURE, regions[EmailPart.Body(3)])
    }

    @Test
    fun noDecoysWhenOff() {
        val regions = emailRegions(email, emptyList(), decoys = false)
        assertEquals(emptyMap(), regions)
    }

    @Test
    fun domainDiffMarksSwappedLetters() {
        assertEquals((7..12).toSet(), domainDiffIndexes("thisistea.example", "thisistae-app.example"))
        assertEquals((17 until 38).toSet(), domainDiffIndexes("thisistea.example", "thisistea.example.secure-login.example"))
        assertEquals(emptySet(), domainDiffIndexes("same.example", "same.example"))
        assertEquals(setOf(4), domainDiffIndexes("bank.example", "bankk.example"))
    }
}
