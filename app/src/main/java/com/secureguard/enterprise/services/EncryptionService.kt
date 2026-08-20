package com.secureguard.enterprise.services

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ende-zu-Ende-Verschlüsselung (AES/GCM/NoPadding) mit Schlüsseln im
 * AndroidKeyStore. Der Schlüssel verlässt das Gerät nie; IV und Ciphertext
 * werden getrennt gespeichert (siehe [EncryptedData]).
 */
@Singleton
class EncryptionService @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(data: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedData(
            data = cipher.doFinal(data),
            iv = cipher.iv
        )
    }

    fun decrypt(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "secureguard_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

/** Verschlüsselter Blob inkl. Initialisierungsvektor. */
data class EncryptedData(
    val data: ByteArray,
    val iv: ByteArray
)
