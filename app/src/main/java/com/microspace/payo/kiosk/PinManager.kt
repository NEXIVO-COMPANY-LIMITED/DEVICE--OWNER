package com.microspace.payo.kiosk

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson

/**
 * PIN Manager for Hard Lock Unlock
 * 
 * Stores unlock PIN in local database (plain text)
 * PIN is NOT displayed on screen
 * User enters PIN via keyboard for verification
 */
object PinManager {
    
    private const val TAG = "PinManager"
    private const val PREFS_NAME = "unlock_pins"
    private const val PIN_KEY = "current_unlock_pin"
    private const val PIN_TIMESTAMP_KEY = "pin_timestamp"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Store unlock PIN in local database
     * Called when heartbeat response contains unlock_password
     * 
     * @param context Android context
     * @param pin The unlock PIN from server
     */
    fun storePinLocally(context: Context, pin: String) {
        try {
            val prefs = getPrefs(context)
            prefs.edit().apply {
                putString(PIN_KEY, pin)
                putLong(PIN_TIMESTAMP_KEY, System.currentTimeMillis())
                apply()
            }
            
            Log.i(TAG, "âœ… PIN stored in local database")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error storing PIN: ${e.message}", e)
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
            val prefs = getPrefs(context)
            val storedPin = prefs.getString(PIN_KEY, null)
            
            if (storedPin == null) {
                Log.w(TAG, "âš ï¸ No PIN stored in database")
                return false
            }
            
            val isMatch = storedPin == userEnteredPin
            
            if (isMatch) {
                Log.i(TAG, "âœ… PIN verified successfully")
                clearPin(context)  // Clear PIN after successful verification
            } else {
                Log.w(TAG, "âŒ PIN verification failed")
            }
            
            isMatch
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error verifying PIN: ${e.message}", e)
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
            val prefs = getPrefs(context)
            prefs.getString(PIN_KEY, null)
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
            val prefs = getPrefs(context)
            prefs.edit().apply {
                remove(PIN_KEY)
                remove(PIN_TIMESTAMP_KEY)
                apply()
            }
            
            Log.d(TAG, "âœ… PIN cleared from database")
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
            val prefs = getPrefs(context)
            prefs.getString(PIN_KEY, null) != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking PIN: ${e.message}", e)
            false
        }
    }
}




