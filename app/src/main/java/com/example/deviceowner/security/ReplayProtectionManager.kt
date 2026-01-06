package com.example.deviceowner.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages replay attack protection
 * Feature 4.10: Secure APIs & Communication
 * 
 * Responsibilities:
 * - Validate request timestamps
 * - Track nonces to prevent replay
 * - Maintain command sequence numbers
 * - Prevent duplicate command execution
 */
class ReplayProtectionManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "replay_protection",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val TAG = "ReplayProtectionManager"
        
        // Preferences keys
        private const val KEY_USED_NONCES = "used_nonces"
        private const val KEY_LAST_COMMAND_SEQUENCE = "last_command_sequence"
        private const val KEY_LAST_REQUEST_TIMESTAMP = "last_request_timestamp"
        
        // Timestamp validation window (±5 minutes)
        private const val TIMESTAMP_WINDOW_MS = 5 * 60 * 1000
        
        // Maximum nonces to track (prevent memory issues)
        private const val MAX_NONCES = 1000
    }

    /**
     * Validate request timestamp
     * Returns true if timestamp is within acceptable window
     */
    fun validateTimestamp(requestTimestamp: Long): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val timeDiff = kotlin.math.abs(now - requestTimestamp)
            
            if (timeDiff > TIMESTAMP_WINDOW_MS) {
                Log.w(TAG, "Timestamp validation failed: time difference = $timeDiff ms")
                return false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating timestamp", e)
            false
        }
    }

    /**
     * Check if nonce has been used before
     */
    fun isNonceUsed(nonce: String): Boolean {
        return try {
            val usedNonces = getUsedNonces()
            usedNonces.contains(nonce)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking nonce", e)
            false
        }
    }

    /**
     * Record nonce as used
     */
    fun recordNonce(nonce: String) {
        try {
            val usedNonces = getUsedNonces().toMutableSet()
            
            // Prevent memory issues by limiting nonce storage
            if (usedNonces.size >= MAX_NONCES) {
                Log.w(TAG, "Nonce cache full, clearing old entries")
                usedNonces.clear()
            }
            
            usedNonces.add(nonce)
            
            val json = gson.toJson(usedNonces)
            prefs.edit().putString(KEY_USED_NONCES, json).apply()
            
            Log.d(TAG, "Nonce recorded: $nonce")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording nonce", e)
        }
    }

    /**
     * Get all used nonces
     */
    private fun getUsedNonces(): Set<String> {
        return try {
            val json = prefs.getString(KEY_USED_NONCES, null) ?: return emptySet()
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting used nonces", e)
            emptySet()
        }
    }

    /**
     * Validate and record nonce (atomic operation)
     */
    fun validateAndRecordNonce(nonce: String): Boolean {
        return try {
            if (isNonceUsed(nonce)) {
                Log.w(TAG, "Nonce replay detected: $nonce")
                return false
            }
            
            recordNonce(nonce)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating and recording nonce", e)
            false
        }
    }

    /**
     * Get last command sequence number
     */
    fun getLastCommandSequence(): Long {
        return prefs.getLong(KEY_LAST_COMMAND_SEQUENCE, 0)
    }

    /**
     * Validate command sequence number
     */
    fun validateCommandSequence(sequenceNumber: Long): Boolean {
        return try {
            val lastSequence = getLastCommandSequence()
            
            if (sequenceNumber <= lastSequence) {
                Log.w(TAG, "Command sequence validation failed: $sequenceNumber <= $lastSequence")
                return false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating command sequence", e)
            false
        }
    }

    /**
     * Record command sequence number
     */
    fun recordCommandSequence(sequenceNumber: Long) {
        try {
            prefs.edit().putLong(KEY_LAST_COMMAND_SEQUENCE, sequenceNumber).apply()
            Log.d(TAG, "Command sequence recorded: $sequenceNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording command sequence", e)
        }
    }

    /**
     * Validate and record command sequence (atomic operation)
     */
    fun validateAndRecordCommandSequence(sequenceNumber: Long): Boolean {
        return try {
            if (!validateCommandSequence(sequenceNumber)) {
                return false
            }
            
            recordCommandSequence(sequenceNumber)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating and recording command sequence", e)
            false
        }
    }

    /**
     * Clear all replay protection data
     */
    fun clearAll() {
        try {
            Log.d(TAG, "Clearing all replay protection data")
            prefs.edit().apply {
                remove(KEY_USED_NONCES)
                remove(KEY_LAST_COMMAND_SEQUENCE)
                remove(KEY_LAST_REQUEST_TIMESTAMP)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing replay protection data", e)
        }
    }
}
