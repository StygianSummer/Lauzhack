package tea.server.auth

import java.security.MessageDigest
import java.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import tea.server.Crypto

/**
 * Argon2id (Bouncy Castle) with m = 19456 KiB, t = 2, p = 1, 16-byte salt, 32-byte hash,
 * stored as a PHC string `$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>` (base64 without padding).
 */
object PasswordHasher {
    private const val M = 19456
    private const val T = 2
    private const val P = 1
    private const val SALT_LEN = 16
    private const val HASH_LEN = 32
    private val b64e = Base64.getEncoder().withoutPadding()
    private val b64d = Base64.getDecoder()

    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_LEN).also { Crypto.random.nextBytes(it) }
        val out = derive(password, salt, M, T, P, HASH_LEN)
        return "\$argon2id\$v=19\$m=$M,t=$T,p=$P\$${b64e.encodeToString(salt)}\$${b64e.encodeToString(out)}"
    }

    fun verify(password: CharArray, phc: String): Boolean {
        val parts = phc.split('$')
        // ["", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash]
        if (parts.size != 6 || parts[1] != "argon2id" || parts[2] != "v=19") return false
        val params = parts[3].split(',').associate { kv -> kv.substringBefore('=') to kv.substringAfter('=').toIntOrNull() }
        val m = params["m"] ?: return false
        val t = params["t"] ?: return false
        val p = params["p"] ?: return false
        val salt = runCatching { b64d.decode(parts[4]) }.getOrNull() ?: return false
        val expected = runCatching { b64d.decode(parts[5]) }.getOrNull() ?: return false
        val actual = derive(password, salt, m, t, p, expected.size)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun derive(password: CharArray, salt: ByteArray, m: Int, t: Int, p: Int, len: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(m)
            .withIterations(t)
            .withParallelism(p)
            .withSalt(salt)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        val out = ByteArray(len)
        gen.generateBytes(password, out)
        return out
    }
}
