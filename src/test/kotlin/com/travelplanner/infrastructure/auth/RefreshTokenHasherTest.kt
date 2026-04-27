package com.travelplanner.infrastructure.auth

import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RefreshTokenHasherTest {

    private val secret = "test-secret-key-at-least-32-characters-long!!"
    private val hasher = RefreshTokenHasher(secret)

    @Test
    fun `hash is deterministic for the same input`() {
        val token = "5b0e1ad5-39c5-4c9f-bc3e-c6b3a52eaa66"
        assertEquals(hasher.hash(token), hasher.hash(token))
    }

    @Test
    fun `different inputs produce different outputs`() {
        assertNotEquals(hasher.hash("token-a"), hasher.hash("token-b"))
    }

    @Test
    fun `output is 64 lowercase hex chars`() {
        val out = hasher.hash("any-input")
        assertEquals(64, out.length)
        assertTrue(out.matches(Regex("^[0-9a-f]{64}$")), "got: $out")
    }

    @Test
    fun `different secrets produce different outputs for the same token`() {
        val a = RefreshTokenHasher("secret-one").hash("same-token")
        val b = RefreshTokenHasher("secret-two").hash("same-token")
        assertNotEquals(a, b)
    }

    @Test
    fun `matches an independently-computed HmacSHA256 hex digest`() {
        val key = "lockdown-secret"
        val msg = "lockdown-token"

        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        val expected = mac.doFinal(msg.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, RefreshTokenHasher(key).hash(msg))
    }
}
