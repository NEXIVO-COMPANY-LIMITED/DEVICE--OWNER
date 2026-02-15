package com.example.deviceowner.control

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

/**
 * HardLockManager - Applies system-level hard locks to device
 *
 * Responsibilities:
 * - Lock device via DevicePolicyManager
 * - Show lock overlay that can't be dismissed
 * - Persist lock state across reboots
 * - Prevent app uninstallation during lock
 *
 * Hard Lock Triggers:
 * - Backend says is_locked: true (payment overdue or security violation)
 * - Local detection of HIGH severity violations (rooted device, bootloader unlocked, etc.)
 *
 * Lock State Persistence:
 * - Saved to SharedPreferences with timestamp
 * - Survives app restart and device reboot
 * - Can be queried to determine current lock status
 */
class HardLockManager(private val context: Context) {
    
    companion object {
        private const val TAG = "HardLockManager"
        const val LOCK_PREF_NAME = "device_locks"
        const val LOCK_TYPE_KEY = "lock_type"
        const val LOCK_REASON_KEY = "lock_reason"
        const val LOCK_TIMESTAMP_KEY = "lock_timestamp"
        const val LOCK_SOURCE_KEY = "lock_source"  // "backend" or "local"
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    /**
     * Apply hard lock to device
     * - Locks device immediately via DevicePolicyManager
     * - Shows lock overlay that can't be dismissed
     * - Persists lock state across reboots
     * 
     * @param reason Human-readable reason for lock (shown to user)
     * @param recommendation Optional recommendation for user
     * @param source "backend" or "local" - where the lock decision came from
     */
    fun applyHardLock(
        reason: String,
        recommendation: String? = null,
        source: String = "local"
    ) {
        Log.w(TAG, "🔒 Applying HARD LOCK - Source: $source, Reason: $reason")
        
        try {
            // Save lock state FIRST (before attempting DPM lock)
            // This ensures lock state persists even if DPM fails
            saveLockState("hard", reason, source)
            
            // Check if we have device admin
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                Log.e(TAG, "⚠️ Device admin not active - showing overlay only")
                showLockOverlay(reason, recommendation)
                return
            }
            
            // Lock device immediately via DevicePolicyManager
            Log.d(TAG, "🔐 Calling DevicePolicyManager.lockNow()")
            devicePolicyManager.lockNow()
            
            // Show lock overlay
            showLockOverlay(reason, recommendation)
            
            Log.i(TAG, "✅ Hard lock applied successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying hard lock: ${e.message}", e)
            // Fallback: show overlay even if DPM fails
            showLockOverlay(reason, recommendation)
        }
    }
    
    /**
     * Show lock overlay that can't be dismissed
     * This is the UI layer of the hard lock
     * 
     * The overlay:
     * - Covers entire screen
     * - Blocks back button
     * - Shows lock reason and recommendation
     * - Can't be swiped away or dismissed
     */
    private fun showLockOverlay(reason: String, recommendation: String?) {
        Log.d(TAG, "📱 Showing lock overlay")
        
        try {
            // Try to find and start the lock overlay activity
            // This will be implemented in the actual lock activity
            val lockOverlayClass = try {
                Class.forName("com.example.deviceowner.ui.activities.lock.DeviceLockOverlayActivity")
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "⚠️ DeviceLockOverlayActivity not found, trying alternative")
                try {
                    Class.forName("com.example.deviceowner.ui.activities.lock.HardLockActivity")
                } catch (e2: ClassNotFoundException) {
                    Log.w(TAG, "⚠️ HardLockActivity not found either")
                    null
                }
            }
            
            if (lockOverlayClass != null) {
                val intent = Intent(context, lockOverlayClass).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_HISTORY
                    putExtra("is_locked", true)
                    putExtra("lock_reason", reason)
                    putExtra("recommendation", recommendation)
                }
                context.startActivity(intent)
            } else {
                Log.w(TAG, "⚠️ No lock overlay activity found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing lock overlay: ${e.message}", e)
        }
    }
    
    /**
     * Check if device is currently hard-locked
     * 
     * @return true if device is in hard lock state
     */
    fun isDeviceHardLocked(): Boolean {
        return try {
            val lockType = getLockType()
            lockType == "hard"
        } catch (e: Exception) {
            Log.e(TAG, "Error checking hard lock status: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get current lock type from SharedPreferences
     * 
     * @return "hard", "soft", or null if not locked
     */
    fun getLockType(): String? {
        return try {
            val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
            sharedPref.getString(LOCK_TYPE_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lock type: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get lock reason from SharedPreferences
     * This is the human-readable reason shown to the user
     * 
     * @return Lock reason or null if not locked
     */
    fun getLockReason(): String? {
        return try {
            val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
            sharedPref.getString(LOCK_REASON_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lock reason: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get lock timestamp from SharedPreferences
     * This is when the lock was applied
     * 
     * @return Timestamp in milliseconds, or 0 if not locked
     */
    fun getLockTimestamp(): Long {
        return try {
            val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
            sharedPref.getLong(LOCK_TIMESTAMP_KEY, 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lock timestamp: ${e.message}", e)
            0L
        }
    }
    
    /**
     * Get lock source from SharedPreferences
     * This indicates whether the lock came from backend or local detection
     * 
     * @return "backend", "local", or null if not locked
     */
    fun getLockSource(): String? {
        return try {
            val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
            sharedPref.getString(LOCK_SOURCE_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lock source: ${e.message}", e)
            null
        }
    }
    
    /**
     * Save lock state to SharedPreferences
     * This persists across app restarts and reboots
     * 
     * @param lockType "hard" or "soft"
     * @param reason Human-readable reason for lock
     * @param source "backend" or "local"
     */
    private fun saveLockState(lockType: String, reason: String, source: String) {
        try {
            val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
            sharedPref.edit().apply {
                putString(LOCK_TYPE_KEY, lockType)
                putString(LOCK_REASON_KEY, reason)
                putLong(LOCK_TIMESTAMP_KEY, System.currentTimeMillis())
                putString(LOCK_SOURCE_KEY, source)
                apply()
            }
            Log.d(TAG, "💾 Lock state saved: type=$lockType, source=$source, reason=$reason")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving lock state: ${e.message}", e)
        }
    }
    
    /**
     * Clear lock state (called when lock is released)
     * This removes all lock-related SharedPreferences entries
     */
    fun clearLockState() {
        try {
            val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
            sharedPref.edit().apply {
                remove(LOCK_TYPE_KEY)
                remove(LOCK_REASON_KEY)
                remove(LOCK_TIMESTAMP_KEY)
                remove(LOCK_SOURCE_KEY)
                apply()
            }
            Log.d(TAG, "🔓 Lock state cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing lock state: ${e.message}", e)
        }
    }
    
    /**
     * Release hard lock (admin only)
     * 
     * Note: lockNow() cannot be reversed programmatically
     * User must unlock manually or admin must remove the lock
     * 
     * This method clears the lock state from SharedPreferences
     * but doesn't actually unlock the device
     */
    fun releaseHardLock() {
        Log.w(TAG, "🔓 Releasing hard lock")
        
        try {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                // Note: lockNow() cannot be reversed programmatically
                // User must unlock manually or admin must remove the lock
                Log.i(TAG, "Hard lock can only be released by user or admin")
                clearLockState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing hard lock: ${e.message}", e)
        }
    }
    
    /**
     * Get lock status summary
     * Useful for logging and debugging
     * 
     * @return String describing current lock status
     */
    fun getLockStatusSummary(): String {
        val lockType = getLockType()
        val lockReason = getLockReason()
        val lockSource = getLockSource()
        val lockTimestamp = getLockTimestamp()
        
        return if (lockType == null) {
            "No lock active"
        } else {
            "Lock: type=$lockType, source=$lockSource, reason=$lockReason, timestamp=$lockTimestamp"
        }
    }
}
