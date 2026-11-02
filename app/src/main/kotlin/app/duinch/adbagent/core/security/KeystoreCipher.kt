package app.duinch.adbagent.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AES-256-GCM encryption utility using AndroidKeyStore.
 * Transparently migrates legacy plaintext preferences and falls back safely in JVM unit tests.
 */
object KeystoreCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "adb_remote_master_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PREFIX = "enc:v1:"

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        if (plainText.startsWith(PREFIX)) return plainText
        return try {
            val secretKey = getOrCreateSecretKey() ?: return plainText
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val bodyB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "$PREFIX$ivB64:$bodyB64"
        } catch (_: Throwable) {
            plainText
        }
    }

    fun decrypt(storedValue: String): String {
        if (storedValue.isEmpty() || !storedValue.startsWith(PREFIX)) {
            return storedValue
        }
        return try {
            val payload = storedValue.removePrefix(PREFIX)
            val sepIdx = payload.indexOf(':')
            if (sepIdx <= 0) return ""
            val iv = Base64.decode(payload.substring(0, sepIdx), Base64.NO_WRAP)
            val cipherBytes = Base64.decode(payload.substring(sepIdx + 1), Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey() ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
