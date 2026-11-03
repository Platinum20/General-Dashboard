package com.platinum20.dashboard.security

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Token e chiavi salvati sul telefono cifrati con una chiave del Keystore di Android, che non
 * esce mai dal telefono: copiando i file dell'app (backup, telefono con root) restano illeggibili.
 * I valori in chiaro delle versioni vecchie si leggono ancora e si cifrano alla prima lettura,
 * così nessun accesso va rifatto. Senza Keystore (test) i valori restano in chiaro.
 */
object Vault {
    private const val ALIAS = "canvas_vault"
    internal const val PREFIX = "v1:"

    /** Valore da scrivere nelle preferenze. */
    fun seal(plain: String): String =
        runCatching { PREFIX + encrypt(plain) }.getOrDefault(plain)

    /** Valore letto dalle preferenze: null se è cifrato ma non si apre più (chiave persa). */
    fun open(stored: String?): String? = when {
        stored == null -> null
        !isSealed(stored) -> stored
        else -> runCatching { decrypt(stored.removePrefix(PREFIX)) }.getOrNull()
    }

    fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

    /** Legge una chiave; se era ancora in chiaro la riscrive cifrata. */
    fun read(prefs: SharedPreferences, key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        val plain = open(stored)
        if (plain != null && !isSealed(stored)) {
            val sealed = seal(plain)
            if (isSealed(sealed)) prefs.edit().putString(key, sealed).apply()
        }
        return plain
    }

    fun put(editor: SharedPreferences.Editor, key: String, plain: String): SharedPreferences.Editor =
        editor.putString(key, seal(plain))

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(body: String): String {
        val data = Base64.decode(body, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    }
}
