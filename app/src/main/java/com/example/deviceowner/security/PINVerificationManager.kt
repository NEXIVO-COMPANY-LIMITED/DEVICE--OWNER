package com.example.deviceowner.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest

/**
 * Manages PIN verification for lock/unlock operations
 * Feature 4.4: Remote Lock/Unlock
 */
class PINVerificationManager(private val context: Context) {

    companion object {
        private const val TAG = "PINVerificationManager"
        private const val PREFS_NAME = "pin_verification_prefs"
        private const val KEY_PIN_ATTEMPTS = "pin_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"
        private const val MAX_ATTEMPTS = 3
        private const val LOCKOUT_DURATION_MS = 15 * 60 * 1000 // 15 minutes
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Verify PIN against hash
     */
    fun verifyPin(pin: String, pinHash: String): Boolean {
        return try {
            val hash = hashPin(pin)
            hash == pinHash
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying PIN", e)
            false
        }
    }

    /**
     * Hash PIN using SHA-256
     */
    fun hashPin(pin: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(pin.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing PIN", e)
            ""
        }
    }

    /**
     * Check if PIN verification is locked due to too many attempts
     */
    fun isLocked(): Boolean {
        return try {
            val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0)
            val now = System.currentTimeMillis()
            
            if (lockedUntil > now) {
                Log.w(TAG, "PIN verification locked until ${lockedUntil - now}ms")
                return true
            }
            
            // Clear lock if expired
            if (lockedUntil > 0) {
                prefs.edit().remove(KEY_LOCKED_UNTIL).apply()
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking lock status", e)
            false
        }
    }

    /**
     * Get remaining lockout time in milliseconds
     */
    fun getRemainingLockoutTime(): Long {
        return try {
            val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0)
            val now = System.currentTimeMillis()
            
            if (lockedUntil > now) {
                lockedUntil - now
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting remaining lockout time", e)
            0
        }
    }

    /**
     * Increment failed PIN attempts
     */
    fun incrementFailedAttempts(lockId: String): Int {
        return try {
            val key = "${KEY_PIN_ATTEMPTS}_$lockId"
            val attempts = prefs.getInt(key, 0) + 1
            prefs.edit().putInt(key, attempts).apply()
            
            Log.w(TAG, "Failed PIN attempt #$attempts for lock $lockId")
            
            if (attempts >= MAX_ATTEMPTS) {
                lockPINVerification()
                Log.e(TAG, "PIN verification locked after $MAX_ATTEMPTS failed attempts")
            }
            
            attempts
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing failed attempts", e)
            0
        }
    }

    /**
     * Reset failed PIN attempts
     */
    fun resetFailedAttempts(lockId: String) {
        try {
            val key = "${KEY_PIN_ATTEMPTS}_$lockId"
            prefs.edit().remove(key).apply()
            Log.d(TAG, "Failed PIN attempts reset for lock $lockId")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting failed attempts", e)
        }
    }

    /**
     * Get remaining PIN attempts
     */
    fun getRemainingAttempts(lockId: String): Int {
        return try {
            val key = "${KEY_PIN_ATTEMPTS}_$lockId"
            val attempts = prefs.getInt(key, 0)
            MAX_ATTEMPTS - attempts
        } catch (e: Exception) {
            Log.e(TAG, "Error getting remaining attempts", e)
            MAX_ATTEMPTS
        }
    }

    /**
     * Lock PIN verification temporarily
     */
    private fun lockPINVerification() {
        try {
            val lockedUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            prefs.edit().putLong(KEY_LOCKED_UNTIL, lockedUntil).apply()
            Log.w(TAG, "PIN verification locked for ${LOCKOUT_DURATION_MS / 1000} seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Error locking PIN verification", e)
        }
    }

    /**
     * Unlock PIN verification (admin action)
     */
    fun unlockPINVerification() {
        try {
            prefs.edit().remove(KEY_LOCKED_UNTIL).apply()
            Log.d(TAG, "PIN verification unlocked")
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking PIN verification", e)
        }
    }

    /**
     * Clear all PIN data
     */
    fun clearAllData() {
        try {
            prefs.edit().clear().apply()
            Log.d(TAG, "All PIN verification data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing PIN data", e)
        }
    }
}

