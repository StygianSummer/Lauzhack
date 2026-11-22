package tea.server.auth

import tea.server.Crypto

/** Verification codes and email helpers. Codes are stored only as SHA-256(pepper, email, code). */
object Codes {
    const val LENGTH = 6
    const val MAX_TRIES = 5
    const val VALID_MINUTES = 10L

    private val EMAIL = Regex("^[^\\s@]{1,64}@[^\\s@]{1,253}\\.[^\\s@]{2,63}$")

    fun newCode(): String = Crypto.random.nextInt(1_000_000).toString().padStart(LENGTH, '0')

    fun hash(pepper: String, email: String, code: String): String = Crypto.sha256Hex("$pepper:$email:$code")

    /** Trimmed + lowercased, or null if it does not look like an address. */
    fun normaliseEmail(raw: String): String? {
        val e = raw.trim().lowercase()
        return e.takeIf { it.length <= 320 && EMAIL.matches(it) }
    }

    fun localPart(email: String): String = email.substringBefore('@')

    /** gmail | outlook | proton | icloud | other (plan §4.3), from the verified address's domain. */
    fun provider(email: String): String {
        val d = email.substringAfterLast('@').lowercase()
        val first = d.substringBefore('.')
        return when {
            d == "gmail.com" || d == "googlemail.com" -> "gmail"
            first in setOf("outlook", "hotmail", "live", "msn") -> "outlook"
            first in setOf("proton", "protonmail") || d == "pm.me" -> "proton"
            d in setOf("icloud.com", "me.com", "mac.com") -> "icloud"
            else -> "other"
        }
    }

    fun normaliseLanguage(raw: String?): String =
        raw?.trim()?.lowercase()?.take(2)?.takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "en"
}
