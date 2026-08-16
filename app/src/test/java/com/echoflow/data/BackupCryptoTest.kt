package com.echoflow.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.AEADBadTagException

/** Robolectric so [android.util.Base64] is real. */
@RunWith(RobolectricTestRunner::class)
class BackupCryptoTest {

    private val payload = "the quick brown fox — chats & keys".toByteArray(Charsets.UTF_8)

    @Test
    fun round_trips_with_the_correct_passkey() {
        val env = BackupCrypto.encrypt(payload, "correct horse battery")
        assertArrayEquals(payload, BackupCrypto.decrypt(env, "correct horse battery"))
    }

    @Test
    fun wrong_passkey_fails_loudly() {
        val env = BackupCrypto.encrypt(payload, "right-key")
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.decrypt(env, "wrong-key")
        }
    }

    @Test
    fun tampered_ciphertext_fails_the_auth_tag() {
        val env = BackupCrypto.encrypt(payload, "key")
        // Flip a character in the base64 ciphertext.
        val ch = env.ct[0]
        val swapped = (if (ch == 'A') 'B' else 'A') + env.ct.substring(1)
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypt(env.copy(ct = swapped), "key")
        }
    }

    @Test
    fun each_encryption_uses_a_fresh_salt_and_iv() {
        val a = BackupCrypto.encrypt(payload, "key")
        val b = BackupCrypto.encrypt(payload, "key")
        // Same plaintext + passkey must not produce identical ciphertext.
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.iv, b.iv)
        assertNotEquals(a.ct, b.ct)
    }

    @Test
    fun rejects_unbounded_iteration_count_before_kdf() {
        val env = BackupCrypto.encrypt(payload, "key")
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(env.copy(iter = Int.MAX_VALUE), "key")
        }
    }

    @Test
    fun rejects_too_few_iterations() {
        val env = BackupCrypto.encrypt(payload, "key")
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(env.copy(iter = 1), "key")
        }
    }

    @Test
    fun rejects_unsupported_crypto_version() {
        val env = BackupCrypto.encrypt(payload, "key")
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(env.copy(v = 99), "key")
        }
    }
}
