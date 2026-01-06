package com.example.deviceowner.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages all cryptographic operations for secure communication
 * Feature 4.10: Secure APIs & Communication
 * 
 * Responsibilities:
 * - Generate and manage cryptographic keys
 * - Sign and verify commands
 * - Encrypt/decrypt sensitive data
 * - Manage device certificates
 * - Handle key rotation
 */
class SecurityManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "security_manager",
        Context.MODE_PRIVATE
    )
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    companion object {
        private const val TAG = "SecurityManager"
        
        // Key aliases
        private const val DEVICE_KEY_PAIR_ALIAS = "device_key_pair"
        private const val DEVICE_CERT_ALIAS = "device_certificate"
        private const val ENCRYPTION_KEY_ALIAS = "encryption_key"
        
        // Preferences keys
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_CERT_EXPIRY = "cert_expiry"
        private const val KEY_LAST_KEY_ROTATION = "last_key_rotation"
        
        // Key rotation interval (90 days)
        private const val KEY_ROTATION_INTERVAL_MS = 90L * 24 * 60 * 60 * 1000
        
        // Algorithm constants
        private const val RSA_ALGORITHM = "RSA"
        private const val RSA_KEY_SIZE = 2048
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * Initialize security manager and generate device keys if needed
     */
    fun initialize(deviceId: String): Boolean {
        return try {
            Log.d(TAG, "Initializing SecurityManager for device: $deviceId")
            
            // Store device ID
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            
            // Generate device key pair if not exists
            if (!keyStore.containsAlias(DEVICE_KEY_PAIR_ALIAS)) {
                generateDeviceKeyPair()
            }
            
            // Generate encryption key if not exists
            if (!keyStore.containsAlias(ENCRYPTION_KEY_ALIAS)) {
                generateEncryptionKey()
            }
            
            // Check if key rotation is needed
            checkAndRotateKeys()
            
            Log.d(TAG, "SecurityManager initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SecurityManager", e)
            false
        }
    }

    /**
     * Generate RSA key pair for device authentication and command signing
     */
    private fun generateDeviceKeyPair() {
        try {
            Log.d(TAG, "Generating device RSA key pair")
            
            val keyPairGenerator = KeyPairGenerator.getInstance(
                RSA_ALGORITHM,
                "AndroidKeyStore"
            )
            
            val keyGenSpec = KeyGenParameterSpec.Builder(
                DEVICE_KEY_PAIR_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setKeySize(RSA_KEY_SIZE)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyPairGenerator.initialize(keyGenSpec)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            // Store public key for backend verification
            val publicKeyBytes = keyPair.public.encoded
            val publicKeyB64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
            prefs.edit().putString(KEY_PUBLIC_KEY, publicKeyB64).apply()
            
            Log.d(TAG, "Device RSA key pair generated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating device key pair", e)
            throw e
        }
    }

    /**
     * Generate AES encryption key for data encryption
     */
    private fun generateEncryptionKey() {
        try {
            Log.d(TAG, "Generating AES encryption key")
            
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            
            val keyGenSpec = KeyGenParameterSpec.Builder(
                ENCRYPTION_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
            
            Log.d(TAG, "AES encryption key generated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating encryption key", e)
            throw e
        }
    }

    /**
     * Check if key rotation is needed and perform if necessary
     */
    private fun checkAndRotateKeys() {
        try {
            val lastRotation = prefs.getLong(KEY_LAST_KEY_ROTATION, 0)
            val now = System.currentTimeMillis()
            
            if (now - lastRotation > KEY_ROTATION_INTERVAL_MS) {
                Log.d(TAG, "Key rotation interval exceeded, rotating keys")
                rotateKeys()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking key rotation", e)
        }
    }

    /**
     * Rotate cryptographic keys
     */
    fun rotateKeys(): Boolean {
        return try {
            Log.d(TAG, "Rotating cryptographic keys")
            
            // Delete old keys
            keyStore.deleteEntry(DEVICE_KEY_PAIR_ALIAS)
            keyStore.deleteEntry(ENCRYPTION_KEY_ALIAS)
            
            // Generate new keys
            generateDeviceKeyPair()
            generateEncryptionKey()
            
            // Update rotation timestamp
            prefs.edit().putLong(KEY_LAST_KEY_ROTATION, System.currentTimeMillis()).apply()
            
            Log.d(TAG, "Keys rotated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating keys", e)
            false
        }
    }

    /**
     * Get device's public key for backend verification
     */
    fun getPublicKeyB64(): String? {
        return try {
            prefs.getString(KEY_PUBLIC_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting public key", e)
            null
        }
    }

    /**
     * Sign data with device private key
     */
    fun signData(data: ByteArray): ByteArray? {
        return try {
            val privateKey = keyStore.getKey(DEVICE_KEY_PAIR_ALIAS, null) as? PrivateKey
                ?: return null
            
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(data)
            
            signature.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing data", e)
            null
        }
    }

    /**
     * Sign data and return as Base64 string
     */
    fun signDataB64(data: ByteArray): String? {
        return signData(data)?.let {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
    }

    /**
     * Verify signature with backend public key
     */
    fun verifySignature(data: ByteArray, signatureB64: String, publicKeyB64: String): Boolean {
        return try {
            val signatureBytes = Base64.decode(signatureB64, Base64.NO_WRAP)
            val publicKeyBytes = Base64.decode(publicKeyB64, Base64.NO_WRAP)
            
            val keyFactory = java.security.KeyFactory.getInstance(RSA_ALGORITHM)
            val publicKey = keyFactory.generatePublic(
                java.security.spec.X509EncodedKeySpec(publicKeyBytes)
            )
            
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(data)
            
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying signature", e)
            false
        }
    }

    /**
     * Encrypt data with AES-256-GCM
     */
    fun encryptData(plaintext: ByteArray): ByteArray? {
        return try {
            val secretKey = keyStore.getKey(ENCRYPTION_KEY_ALIAS, null) as? SecretKey
                ?: return null
            
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            
            // Return IV + ciphertext
            iv + ciphertext
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting data", e)
            null
        }
    }

    /**
     * Decrypt data with AES-256-GCM
     */
    fun decryptData(encryptedData: ByteArray): ByteArray? {
        return try {
            val secretKey = keyStore.getKey(ENCRYPTION_KEY_ALIAS, null) as? SecretKey
                ?: return null
            
            // Extract IV (first 12 bytes for GCM)
            val iv = encryptedData.copyOfRange(0, 12)
            val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
            
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting data", e)
            null
        }
    }

    /**
     * Generate secure random nonce for replay protection
     */
    fun generateNonce(): String {
        val random = SecureRandom()
        val nonceBytes = ByteArray(16)
        random.nextBytes(nonceBytes)
        return Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
    }

    /**
     * Get device ID
     */
    fun getDeviceId(): String? {
        return prefs.getString(KEY_DEVICE_ID, null)
    }
}
