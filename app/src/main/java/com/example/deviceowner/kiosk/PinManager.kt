package com.example.deviceowner.kiosk

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson

/**
 * PIN Manager for Hard Lock Unlock
 * 
 * Stores unlock PIN in encrypted local database
 * PIN is NOT displayed on screen
 * User enters PIN via keyboard for verification
 */
object PinManager {
    
    private const val TAG = "PinManager"
    private const val PREFS_NAME = "unlock_pins"
    private const val PIN_KEY = "current_unlock_pin"
    private const val PIN_TIMESTAMP_KEY = "pin_timestamp"
    
    /**
     * Store unlock PIN in encrypted local database
     * Called when heartbeat response contains unlock_password
     * 
     * @param context Android context
     * @param pin The unlock PIN from server
     */
    fun storePinLocally(context: Context, pin: String) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            encryptedPrefs.edit().apply {
                putString(PIN_KEY, pin)
                putLong(PIN_TIMESTAMP_KEY, System.currentTimeMillis())
                apply()
            }
            
            Log.i(TAG, "✅ PIN stored securely in local database")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error storing PIN: ${e.message}", e)
        }
    }
    
    /**
     * Verify user-entered PIN against stored PIN
     * 
     * @param context Android context
     * @param userEnteredPin The PIN entered by user
     * @return True if PIN matches, false otherwise
     */
    fun verifyPin(context: Context, userEnteredPin: String): Boolean {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            val storedPin = encryptedPrefs.getString(PIN_KEY, null)
            
            if (storedPin == null) {
                Log.w(TAG, "⚠️ No PIN stored in database")
                return false
            }
            
            val isMatch = storedPin == userEnteredPin
            
            if (isMatch) {
                Log.i(TAG, "✅ PIN verified successfully")
                clearPin(context)  // Clear PIN after successful verification
            } else {
                Log.w(TAG, "❌ PIN verification failed")
            }
            
            isMatch
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verifying PIN: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get stored PIN (for debugging only)
     * 
     * @param context Android context
     * @return Stored PIN or null
     */
    fun getStoredPin(context: Context): String? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            encryptedPrefs.getString(PIN_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PIN: ${e.message}", e)
            null
        }
    }
    
    /**
     * Clear stored PIN from database
     * 
     * @param context Android context
     */
    fun clearPin(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            encryptedPrefs.edit().apply {
                remove(PIN_KEY)
                remove(PIN_TIMESTAMP_KEY)
                apply()
            }
            
            Log.d(TAG, "✅ PIN cleared from database")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing PIN: ${e.message}", e)
        }
    }
    
    /**
     * Check if PIN exists in database
     * 
     * @param context Android context
     * @return True if PIN exists
     */
    fun hasPinStored(context: Context): Boolean {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            encryptedPrefs.getString(PIN_KEY, null) != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking PIN: ${e.message}", e)
            false
        }
    }
}
