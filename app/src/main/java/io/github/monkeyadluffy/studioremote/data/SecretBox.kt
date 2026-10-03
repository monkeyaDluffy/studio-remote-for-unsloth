package io.github.monkeyadluffy.studioremote.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts secrets (session tokens) with an AES key that never leaves the Android Keystore.
 * Stored form is [PREFIX] + Base64(iv + ciphertext); values without the prefix are pre-1.12 plain text.
 */
object SecretBox {
    private const val ALIAS = "studio_remote_token_key"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val PREFIX = "enc1:"

    fun isEncrypted(stored: String) = stored.startsWith(PREFIX)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    /** Null when the value can't be decrypted (key gone, data corrupt): the user has to sign in again. */
    fun decrypt(stored: String): String? = runCatching {
        val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, IV_SIZE)
        val ct = raw.copyOfRange(IV_SIZE, raw.size)
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(c.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()
}
