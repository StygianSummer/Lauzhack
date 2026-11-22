package tea.content

import kotlin.test.Test
import kotlin.test.assertEquals
import tea.model.Profile

class PlaceholdersTest {

    @Test
    fun guestDefaults() {
        assertEquals(
            "Hi Sam, sent to you@mail.example via mail.example",
            Placeholders.fill("Hi {{first_name}}, sent to {{email}} via {{email_domain}}", null, null),
        )
    }

    @Test
    fun profileValues() {
        val p = Profile(firstName = "Lina")
        assertEquals("Hi Lina <lina@post.example>, from help@post.example",
            Placeholders.fill("Hi {{first_name}} <{{email}}>, from help@{{email_domain}}", p, "lina@post.example"))
    }

    @Test
    fun blankNameAndBadEmailFallBackToGuest() {
        assertEquals("Sam you@mail.example", Placeholders.fill("{{first_name}} {{email}}", Profile(firstName = "  "), "nope"))
    }

    @Test
    fun unknownPlaceholdersStay() {
        assertEquals("Click {{click_url}} Sam", Placeholders.fill("Click {{click_url}} {{ first_name }}", null, null))
    }

    @Test
    fun fillsEveryTextFieldOfAnItem() {
        val item = Fixtures.scam().copy(attackerReveal = "Bye {{first_name}}")
        val filled = Placeholders.fill(item, Profile(firstName = "Ana"), "ana@mail.example")
        assertEquals("Urgent, Ana", filled.email!!.subject)
        assertEquals("ana@mail.example", filled.email.to)
        assertEquals("Bye Ana", filled.attackerReveal)
        // untouched fields survive the round trip
        assertEquals(item.copy(email = item.email!!.copy(subject = "Urgent, Ana", to = "ana@mail.example"), attackerReveal = "Bye Ana"), filled)
    }
}
