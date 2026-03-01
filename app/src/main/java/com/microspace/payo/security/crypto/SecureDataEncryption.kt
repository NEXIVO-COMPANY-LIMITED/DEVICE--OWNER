package com.microspace.payo.security.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Comprehensive encryption utility for sensitive data at rest.
 * Uses AES-256-GCM for authenticated encryption with HMAC for additional integrity.
 */
class SecureDataEncryption(private val context: Context) {

    companion object {
        private const val TAG = "SecureDataEncryption"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "secure_data_key_v2"
        private const val HMAC_KEY_ALIAS = "hmac_integrity_key"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getOrCreateKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: generateKey()
    }

    private fun getOrCreateHmacKey(): SecretKey {
        val existingKey = keyStore.getEntry(HMAC_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: generateHmacKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
        }.build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    private fun generateHmacKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        val parameterSpec = KeyGenParameterSpec.Builder(
            HMAC_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).setKeySize(256).build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts sensitive string data with integrity protection
     * Format: [IV][ENCRYPTED_DATA][HMAC]
     */
    fun encryptString(plaintext: String): String {
        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            
            // Calculate HMAC for integrity
            val hmac = calculateHmac(iv + encryptedData)
            
            // Combined format: IV + EncryptedData + HMAC
            val combined = iv + encryptedData + hmac
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            throw EncryptionException("Failed to encrypt data", e)
        }
    }

    /**
     * Decrypts string data and verifies integrity
     */
    fun decryptString(encryptedData: String): String {
        return try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            
            // Extract components
            val hmacLength = 32 // SHA-256 HMAC length
            val iv = combined.sliceArray(0 until IV_LENGTH)
            val hmac = combined.takeLast(hmacLength).toByteArray()
            val ciphertext = combined.sliceArray(IV_LENGTH until (combined.size - hmacLength))
            
            // Verify HMAC before attempting decryption
            val calculatedHmac = calculateHmac(iv + ciphertext)
            if (!calculatedHmac.contentEquals(hmac)) {
                throw EncryptionException("Data integrity check failed - possible tampering detected")
            }
            
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), gcmSpec)
            
            val decryptedData = cipher.doFinal(ciphertext)
            String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            throw EncryptionException("Failed to decrypt data", e)
        }
    }

    private fun calculateHmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(getOrCreateHmacKey())
        return mac.doFinal(data)
    }

    /**
     * Securely clears keys from KeyStore (use with caution)
     */
    fun clearKeys() {
        keyStore.deleteEntry(KEY_ALIAS)
        keyStore.deleteEntry(HMAC_KEY_ALIAS)
    }
    
    // Legacy support methods
    fun encryptBytes(plaintext: ByteArray): String = encryptString(Base64.encodeToString(plaintext, Base64.NO_WRAP))
    fun decryptBytes(encryptedData: String): ByteArray = Base64.decode(decryptString(encryptedData), Base64.NO_WRAP)
    fun encryptMap(data: Map<String, String>): Map<String, String> = data.mapValues { encryptString(it.value) }
    fun decryptMap(encryptedData: Map<String, String>): Map<String, String> = encryptedData.mapValues { decryptString(it.value) }
}

class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)




