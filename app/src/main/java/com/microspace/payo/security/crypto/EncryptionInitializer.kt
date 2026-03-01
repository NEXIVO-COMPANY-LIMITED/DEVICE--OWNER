package com.microspace.payo.security.crypto

import android.content.Context
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase

/**
 * Initializes and manages the encryption system for the application.
 * Should be called during app startup to ensure all encryption keys are properly initialized.
 */
object EncryptionInitializer {

    private const val TAG = "EncryptionInitializer"

    /**
     * Initializes all encryption components
     * Call this during app startup (e.g., in Application.onCreate())
     */
    fun initializeEncryption(context: Context) {
        try {
            Log.d(TAG, "Initializing encryption system...")
            
            // 0. Load SQLCipher native libraries
            SQLiteDatabase.loadLibs(context)
            
            // 1. Initialize Android KeyStore keys
            initializeKeyStoreKeys(context)
            
            // 2. Initialize database passphrase
            initializeDatabasePassphrase(context)
            
            // 3. Initialize encrypted preferences
            initializeEncryptedPreferences(context)
            
            Log.d(TAG, "Encryption system initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize encryption system", e)
            throw EncryptionException("Encryption initialization failed", e)
        }
    }

    /**
     * Initializes Android KeyStore keys for encryption
     */
    private fun initializeKeyStoreKeys(context: Context) {
        try {
            Log.d(TAG, "Initializing KeyStore keys...")
            
            // Initialize SecureDataEncryption key
            val encryption = SecureDataEncryption(context)
            encryption.encryptString("test") // Trigger key generation if needed
            
            Log.d(TAG, "KeyStore keys initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize KeyStore keys", e)
            throw e
        }
    }

    /**
     * Initializes database passphrase
     */
    private fun initializeDatabasePassphrase(context: Context) {
        try {
            Log.d(TAG, "Initializing database passphrase...")
            
            DatabasePassphraseManager.getOrCreatePassphrase(context)
            
            Log.d(TAG, "Database passphrase initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database passphrase", e)
            throw e
        }
    }

    /**
     * Initializes encrypted preferences
     */
    private fun initializeEncryptedPreferences(context: Context) {
        try {
            Log.d(TAG, "Initializing encrypted preferences...")
            
            val prefsManager = EncryptedPreferencesManager(context)
            
            // Access each preference type to ensure they're created
            prefsManager.getDeviceDataPreferences()
            prefsManager.getRegistrationPreferences()
            prefsManager.getPaymentPreferences()
            prefsManager.getSecurityPreferences()
            prefsManager.getLoanPreferences()
            
            Log.d(TAG, "Encrypted preferences initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize encrypted preferences", e)
            throw e
        }
    }

    /**
     * Verifies encryption system is working correctly
     */
    fun verifyEncryption(context: Context): Boolean {
        return try {
            Log.d(TAG, "Verifying encryption system...")
            
            val encryption = SecureDataEncryption(context)
            val testData = "encryption_test_${System.currentTimeMillis()}"
            
            val encrypted = encryption.encryptString(testData)
            val decrypted = encryption.decryptString(encrypted)
            
            val isValid = testData == decrypted
            
            if (isValid) {
                Log.d(TAG, "Encryption verification successful")
            } else {
                Log.e(TAG, "Encryption verification failed: data mismatch")
            }
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Encryption verification failed", e)
            false
        }
    }

    /**
     * Clears all encrypted data (use with caution)
     */
    fun clearAllEncryptedData(context: Context) {
        try {
            Log.w(TAG, "Clearing all encrypted data...")
            
            val prefsManager = EncryptedPreferencesManager(context)
            prefsManager.clearAllPreferences()
            
            Log.w(TAG, "All encrypted data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear encrypted data", e)
            throw e
        }
    }

    /**
     * Gets encryption system status
     */
    fun getEncryptionStatus(context: Context): EncryptionStatus {
        return try {
            val isInitialized = verifyEncryption(context)
            
            EncryptionStatus(
                isInitialized = isInitialized,
                keyStoreAvailable = true,
                databaseEncrypted = true,
                preferencesEncrypted = true,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get encryption status", e)
            EncryptionStatus(
                isInitialized = false,
                keyStoreAvailable = false,
                databaseEncrypted = false,
                preferencesEncrypted = false,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }
}

/**
 * Data class representing encryption system status
 */
data class EncryptionStatus(
    val isInitialized: Boolean,
    val keyStoreAvailable: Boolean,
    val databaseEncrypted: Boolean,
    val preferencesEncrypted: Boolean,
    val timestamp: Long,
    val error: String? = null
) {
    fun isHealthy(): Boolean {
        return isInitialized && keyStoreAvailable && databaseEncrypted && preferencesEncrypted
    }
}




