package com.microspace.payo.security.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Manages database passphrase securely using Android KeyStore via EncryptedSharedPreferences.
 * The passphrase is a high-entropy cryptographically secure string.
 */
object DatabasePassphraseManager {

    private const val PREFS_NAME = "db_passphrase_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_v2"
    private const val PASSPHRASE_LENGTH = 64 // Increased entropy

    /**
     * Gets or creates a database passphrase
     */
    fun getOrCreatePassphrase(context: Context): String {
        val encryptedPrefs = getEncryptedPreferences(context)
        
        var passphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
        
        if (passphrase.isNullOrEmpty()) {
            passphrase = generateSecurePassphrase()
            encryptedPrefs.edit()
                .putString(KEY_DB_PASSPHRASE, passphrase)
                .apply()
        }
        
        return passphrase
    }

    /**
     * Generates a high-entropy random passphrase
     */
    private fun generateSecurePassphrase(): String {
        val random = SecureRandom()
        val bytes = ByteArray(PASSPHRASE_LENGTH)
        random.nextBytes(bytes)
        // Use URL_SAFE to avoid issues with special characters in some SQLCipher versions
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun getEncryptedPreferences(context: Context): EncryptedSharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    fun getPassphrase(context: Context): String = getOrCreatePassphrase(context)
}




