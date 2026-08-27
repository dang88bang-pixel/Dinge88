package com.secureguard.enterprise.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet die SQLCipher-Passphrase für die Room-Datenbank.
 *
 * Die Passphrase wird einmalig zufällig erzeugt und mit einem
 * AndroidKeyStore-AES-Schlüssel (GCM) verschlüsselt in SharedPreferences
 * abgelegt. So verlässt der Klartext-Passphrase das Gerät nicht und
 * ist an die Hardware-/StrongBox-Keys gebunden.
 *
 * Migration unverschlüsselt → SQLCipher:
 * siehe [com.secureguard.enterprise.data.local.SqlCipherHelper].
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Liefert die SQLCipher-Passphrase (Klartext, nur im Speicher).
     * Erzeugt sie beim ersten Aufruf und speichert sie verschlüsselt.
     */
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val wrapped = prefs.getString(KEY_WRAPPED, null)
        val ivB64 = prefs.getString(KEY_IV, null)
        if (wrapped != null && ivB64 != null) {
            return try {
                unwrap(Base64.decode(wrapped, Base64.NO_WRAP), Base64.decode(ivB64, Base64.NO_WRAP))
            } catch (e: Exception) {
                Log.e(TAG, "Passphrase konnte nicht entpackt werden – neu erzeugen", e)
                createAndStore()
            }
        }
        return createAndStore()
    }

    /** Hex-Darstellung (nur für Debug/Security-Screen, nicht loggen in Prod). */
    fun passphraseFingerprint(): String {
        val p = getOrCreatePassphrase()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(p)
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    fun isPassphraseInitialized(): Boolean =
        prefs.contains(KEY_WRAPPED) && prefs.contains(KEY_IV)

    private fun createAndStore(): ByteArray {
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encrypted = wrap(passphrase)
        prefs.edit()
            .putString(KEY_WRAPPED, Base64.encodeToString(encrypted.data, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "SQLCipher-Passphrase erzeugt und im KeyStore verwahrt")
        return passphrase
    }

    private fun wrap(plain: ByteArray): Wrapped {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        return Wrapped(cipher.doFinal(plain), cipher.iv)
    }

    private fun unwrap(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return try {
            generateWrappingKey(strongBox = supportsStrongBox())
        } catch (e: Exception) {
            // Gerät ohne StrongBox (oder API < 28): Fallback auf TEE/Software-Key.
            Log.w(TAG, "StrongBox-Key nicht verfügbar – Fallback ohne StrongBox", e)
            generateWrappingKey(strongBox = false)
        }
    }

    /** StrongBox existiert erst ab API 28 und ist nicht auf jedem Gerät vorhanden. */
    private fun supportsStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEEP)

    private fun generateWrappingKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
        if (strongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private data class Wrapped(val data: ByteArray, val iv: ByteArray)

    companion object {
        private const val TAG = "DbKeyManager"
        private const val PREFS = "secureguard_db_key"
        private const val KEY_WRAPPED = "passphrase_wrapped"
        private const val KEY_IV = "passphrase_iv"
        private const val KEY_CREATED_AT = "created_at"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "secureguard_sqlcipher_wrap"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
