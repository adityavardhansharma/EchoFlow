package com.echoflow.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encrypted envelope written to disk. The salt and IV are not secret — they must travel with
 * the ciphertext so the same passkey can re-derive the key on another install — but the passkey
 * itself never leaves the user's head, so the file is safe even if it leaks.
 */
data class BackupEnvelope(
    val magic: String,
    val v: Int,
    val iter: Int,
    val salt: String, // base64
    val iv: String,   // base64
    val ct: String,   // base64 ciphertext (AES-GCM, tag appended)
)

/**
 * Password-based authenticated encryption for the uninstall-surviving backup.
 *
 * Key derivation is PBKDF2-HMAC-SHA256 (210k iterations, OWASP 2023 guidance) over a random
 * 16-byte salt; the payload is sealed with AES-256-GCM under a random 12-byte IV. GCM's auth tag
 * means a wrong passkey (or any tampering) fails loudly with [javax.crypto.AEADBadTagException]
 * rather than returning garbage — which is exactly how the UI tells "wrong recovery key" apart
 * from "corrupt file".
 *
 * Iteration count and sizes from the on-disk envelope are treated as untrusted input and bounded
 * before PBKDF2 runs, so a crafted file cannot force unbounded CPU work during recovery.
 */
object BackupCrypto {
    const val MAGIC = "ECHOFLOW-BACKUP"
    const val VERSION = 1
    /** PBKDF2 iterations used when writing new backups (OWASP 2023 guidance). */
    const val ITERATIONS = 210_000
    /** Hard ceiling on envelope-supplied iterations so recovery cannot be DoS'd. */
    const val MAX_ITERATIONS = 1_000_000
    /** Floor: reject envelopes that advertise too-weak derivation. */
    const val MIN_ITERATIONS = 100_000
    private const val KEY_BITS = 256
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun encrypt(plaintext: ByteArray, passkey: String): BackupEnvelope {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(passkey, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return BackupEnvelope(
            magic = MAGIC,
            v = VERSION,
            iter = ITERATIONS,
            salt = b64(salt),
            iv = b64(iv),
            ct = b64(ct),
        )
    }

    /**
     * @throws IllegalArgumentException when the envelope is unsupported or malformed
     * @throws javax.crypto.AEADBadTagException when the passkey is wrong or the data was tampered
     */
    fun decrypt(env: BackupEnvelope, passkey: String): ByteArray {
        require(env.v == VERSION) { "Unsupported backup crypto version: ${env.v}" }
        require(env.iter in MIN_ITERATIONS..MAX_ITERATIONS) {
            "PBKDF2 iteration count out of range: ${env.iter}"
        }
        val salt = unb64(env.salt)
        val iv = unb64(env.iv)
        val ct = unb64(env.ct)
        require(salt.size == SALT_BYTES) { "Invalid salt length: ${salt.size}" }
        require(iv.size == IV_BYTES) { "Invalid IV length: ${iv.size}" }
        require(ct.isNotEmpty()) { "Empty ciphertext" }

        val key = deriveKey(passkey, salt, env.iter)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun deriveKey(passkey: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passkey.toCharArray(), salt, iterations, KEY_BITS)
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}
