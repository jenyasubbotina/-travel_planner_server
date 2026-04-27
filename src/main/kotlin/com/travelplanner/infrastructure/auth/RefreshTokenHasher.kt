package com.travelplanner.infrastructure.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Deterministic, keyed one-way hash for opaque refresh tokens.
 *
 * Refresh tokens are 122-bit random UUIDs (see JwtService.generateRefreshToken),
 * so the input is already high-entropy. This hash exists only so a database
 * compromise cannot yield live refresh tokens — not for password-strength reasons,
 * which is why a fast keyed digest is the right tool, not bcrypt.
 *
 * NOTE: rotating [secret] invalidates every existing refresh-token row.
 */
class RefreshTokenHasher(secret: String) {

    private val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGO)

    fun hash(token: String): String {
        val mac = Mac.getInstance(HMAC_ALGO).apply { init(keySpec) }
        return mac.doFinal(token.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    companion object {
        private const val HMAC_ALGO = "HmacSHA256"
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
