package tea.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import tea.server.auth.PasswordHasher
import tea.server.password.HibpClient
import tea.server.password.PasswordAnalyzer
import tea.server.password.PasswordVerdict

class PasswordTest {
    private fun analyse(pw: String, name: String? = "Maria", email: String? = "maria.lopez", pwned: FakePwned = FakePwned()) =
        runBlocking { PasswordAnalyzer(pwned).analyse(pw.toCharArray(), name, email) }

    @Test
    fun rejectsShortAndBreachedWithReasonOnly() {
        assertEquals(PasswordVerdict.Rejected("too_short"), analyse("abc"))
        assertEquals(PasswordVerdict.Rejected("breached"), analyse("correcthorse", pwned = FakePwned.of("correcthorse")))
    }

    @Test
    fun findings() {
        val f = assertIs<PasswordVerdict.Accepted>(analyse("M4ria-2003-asdf")).findings
        assertEquals(15, f.length)
        assertEquals(4, f.classes)
        assertTrue(f.containsName)       // leetspeak: 4 → a
        assertTrue(f.containsYear)
        assertEquals("qwerty_row", f.keyboardPattern)
        assertFalse(f.pwned)
        assertEquals(0, f.pwnedCount)

        val g = assertIs<PasswordVerdict.Accepted>(analyse("lopez tends violets", name = null)).findings
        assertTrue(g.containsEmail)      // "lopez" piece of the local part
        assertFalse(g.containsName)
        assertFalse(g.containsYear)
        assertNull(g.keyboardPattern)
        assertEquals(2, g.classes)
        assertTrue(g.score in 0..4)
    }

    @Test
    fun hibpUnavailableCountsAsNotPwned() {
        val down = object : tea.server.password.PwnedClient {
            override suspend fun pwnedCount(sha1Hex: String): Int? = null
        }
        assertIs<PasswordVerdict.Accepted>(runBlocking { PasswordAnalyzer(down).analyse("quiet river stones".toCharArray(), null, null) })
    }

    @Test
    fun hibpRangeParsing() {
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\nABCDEF0123456789ABCDEF0123456789ABC:42\r\nFFFF0000FFFF0000FFFF0000FFFF0000FFF:0"
        assertEquals(42, HibpClient.parse(body, "ABCDEF0123456789ABCDEF0123456789ABC"))
        assertEquals(0, HibpClient.parse(body, "1111111111111111111111111111111111F"))
    }

    @Test
    fun argon2idPhcRoundTrip() {
        val phc = PasswordHasher.hash("a long passphrase".toCharArray())
        assertTrue(Regex("^\\\$argon2id\\\$v=19\\\$m=19456,t=2,p=1\\\$[A-Za-z0-9+/]{22}\\\$[A-Za-z0-9+/]{43}$").matches(phc), phc)
        assertTrue(PasswordHasher.verify("a long passphrase".toCharArray(), phc))
        assertFalse(PasswordHasher.verify("a long passphrasE".toCharArray(), phc))
        assertFalse(PasswordHasher.verify("x".toCharArray(), "garbage"))
    }
}
