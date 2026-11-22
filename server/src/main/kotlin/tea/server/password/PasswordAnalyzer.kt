package tea.server.password

import me.gosimple.nbvcxz.Nbvcxz
import me.gosimple.nbvcxz.matching.match.SpacialMatch
import tea.server.Crypto

/** What we keep about the accepted password (plan §10.4). Never the password itself. */
data class PasswordFindingsData(
    val length: Int,
    val classes: Int,
    val score: Int,
    val pwned: Boolean,
    val pwnedCount: Int,
    val containsName: Boolean,
    val containsEmail: Boolean,
    val containsYear: Boolean,
    /** null = none; "spatial" (nbvcxz keyboard match) or "<layout>_row" (row walk) */
    val keyboardPattern: String?,
)

sealed interface PasswordVerdict {
    data class Rejected(val reason: String) : PasswordVerdict
    data class Accepted(val findings: PasswordFindingsData) : PasswordVerdict
}

/**
 * In-memory analysis only. Accept iff length >= 10 and not found in HIBP.
 * Rejected attempts return only the reason; nothing about them is stored by the caller.
 */
class PasswordAnalyzer(private val pwned: PwnedClient) {

    suspend fun analyse(password: CharArray, firstName: String?, emailLocalPart: String?): PasswordVerdict {
        val length = Character.codePointCount(password, 0, password.size)
        if (length < MIN_LENGTH) return PasswordVerdict.Rejected("too_short")

        val utf8 = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
        val bytes = ByteArray(utf8.remaining()).also { utf8.get(it) }
        val sha1 = Crypto.sha1Hex(bytes)
        bytes.fill(0)
        val count = pwned.pwnedCount(sha1) ?: 0 // unavailable → treated as not pwned (logged by the client)
        if (count > 0) return PasswordVerdict.Rejected("breached")

        val pw = String(password) // nbvcxz needs a String; it lives only for this call
        val result = synchronized(nbvcxz) { nbvcxz.estimate(pw) }
        val spatial = result.matches.any { it is SpacialMatch }
        val normalised = leet(pw.lowercase())
        return PasswordVerdict.Accepted(
            PasswordFindingsData(
                length = length,
                classes = classes(password),
                score = result.basicScore.coerceIn(0, 4),
                pwned = false,
                pwnedCount = 0,
                containsName = containsWord(normalised, firstName),
                containsEmail = emailTokens(emailLocalPart).any { normalised.contains(it) },
                containsYear = YEAR.containsMatchIn(pw),
                keyboardPattern = rowWalk(pw.lowercase()) ?: if (spatial) "spatial" else null,
            ),
        )
    }

    companion object {
        const val MIN_LENGTH = 10
        private val YEAR = Regex("19\\d\\d|20\\d\\d")
        private val nbvcxz by lazy { Nbvcxz() }

        private val ROWS = listOf(
            "qwerty" to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890"),
            "azerty" to listOf("azertyuiop", "qsdfghjklm", "wxcvbn"),
            "qwertz" to listOf("qwertzuiop", "yxcvbnm"),
        )

        fun classes(pw: CharArray): Int {
            var lower = false; var upper = false; var digit = false; var symbol = false
            for (c in pw) when {
                c.isLowerCase() -> lower = true
                c.isUpperCase() -> upper = true
                c.isDigit() -> digit = true
                else -> symbol = true
            }
            return listOf(lower, upper, digit, symbol).count { it }
        }

        /** Lowercase + common leetspeak substitutions. */
        fun leet(s: String): String = buildString(s.length) {
            for (c in s) append(
                when (c) {
                    '0' -> 'o'; '1', '!', '|' -> 'i'; '3' -> 'e'; '4', '@' -> 'a'
                    '5', '$' -> 's'; '7' -> 't'; '8' -> 'b'; '9' -> 'g'
                    else -> c
                },
            )
        }

        private fun containsWord(normalisedPw: String, word: String?): Boolean {
            val w = word?.trim()?.lowercase()?.let(::leet) ?: return false
            return w.length >= 3 && normalisedPw.contains(w)
        }

        /** The whole local part (3+ chars) plus its alphabetic pieces of 4+ letters, leet-normalised. */
        fun emailTokens(localPart: String?): List<String> {
            val lp = localPart?.lowercase()?.let(::leet) ?: return emptyList()
            val pieces = lp.split(Regex("[^a-z]+")).filter { it.length >= 4 }
            return (listOf(lp).filter { it.length >= 3 } + pieces).distinct()
        }

        /** 4+ consecutive keys of one keyboard row, forwards or backwards. */
        fun rowWalk(lower: String): String? {
            for ((layout, rows) in ROWS) for (row in rows) {
                for (r in listOf(row, row.reversed())) {
                    for (i in 0..r.length - 4) if (lower.contains(r.substring(i, i + 4))) {
                        return if (row.all { it.isDigit() }) "digit_row" else "${layout}_row"
                    }
                }
            }
            return null
        }
    }
}
