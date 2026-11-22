package tea.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutesTest {
    private val all = listOf(
        Screen.Language, Screen.Email, Screen.Code("a@b.example"), Screen.Name, Screen.Password, Screen.Onboarding(3),
        Screen.Terms, Screen.PlacementIntro, Screen.Placement, Screen.PlacementResult, Screen.Home,
        Screen.Lesson("email-01", 2), Screen.Gotcha("send-1", "email-01"),
        Screen.Practice(), Screen.Practice(focusWeakest = true), Screen.PracticeSummary,
        Screen.Reveal("AbC_123-xyz"), Screen.About, Screen.Settings, Screen.Leaderboard, Screen.Gallery,
    )

    @Test
    fun everyScreenRoundTrips() {
        for (s in all) {
            assertEquals(
                s,
                Routes.parse(
                    Routes.toHash(s),
                    pendingEmail = "a@b.example",
                    pendingGotchas = mapOf("email-01" to "send-1"),
                ),
                "screen $s",
            )
        }
    }

    @Test
    fun hashesAreReadable() {
        assertEquals("/home", Routes.toHash(Screen.Home))
        assertEquals("/reveal/tok", Routes.toHash(Screen.Reveal("tok")))
        assertEquals("/gotcha/send-1", Routes.toHash(Screen.Gotcha("send-1", "email-01")))
        assertEquals("/lesson/email-01/0", Routes.toHash(Screen.Lesson("email-01")))
        assertEquals("/gallery", Routes.toHash(Screen.Gallery))
    }

    @Test
    fun emailNeverInTheUrl() {
        assertEquals("/code", Routes.toHash(Screen.Code("ada@mail.example")))
        assertEquals(Screen.Email, Routes.parse("#/code", pendingEmail = null))
    }

    @Test
    fun parsesVariants() {
        assertEquals(Screen.Reveal("tok"), Routes.parse("#/reveal/tok"))
        assertEquals(
            Screen.Gotcha("send-1", "email-01"),
            Routes.parse("#/gotcha/send-1", pendingGotchas = mapOf("email-01" to "send-1")),
        )
        assertNull(Routes.parse("#/gotcha/missing", pendingGotchas = mapOf("email-01" to "send-1")))
        assertEquals(Screen.Home, Routes.parse("/home/"))
        assertEquals(Screen.Lesson("email-01", 0), Routes.parse("#/lesson/email-01"))
        assertEquals(Screen.Onboarding(0), Routes.parse("#/onboarding/x"))
        assertNull(Routes.parse(""))
        assertNull(Routes.parse("#/"))
        assertNull(Routes.parse("#/nope"))
        assertNull(Routes.parse("#/reveal"))
    }
}
