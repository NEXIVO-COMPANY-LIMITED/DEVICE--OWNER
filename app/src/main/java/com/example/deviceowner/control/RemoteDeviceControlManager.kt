package com.example.deviceowner.control

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.deviceowner.data.models.SoftLockType
import com.example.deviceowner.presentation.activities.MainActivity
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.services.SoftLockMonitorService
import com.example.deviceowner.services.SoftLockOverlayService
import com.example.deviceowner.ui.activities.lock.HardLockActivity
import com.example.deviceowner.ui.activities.lock.SoftLockActivity

/**
 * Enterprise-grade Remote Control.
 * Implements Kiosk Mode and Package Suspension for 100% perfection.
 */
class RemoteDeviceControlManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RemoteControl"
        private const val PREFS = "control_prefs"
        const val LOCK_UNLOCKED = "unlocked"
        const val LOCK_STATE_UNLOCKED = "unlocked" // Alias for compatibility
        const val LOCK_SOFT = "soft_lock"
        const val LOCK_HARD = "hard_lock"
    }
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, AdminReceiver::class.java)
    
    fun getLockState(): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("state", LOCK_UNLOCKED) ?: LOCK_UNLOCKED

    fun isLocked(): Boolean = getLockState() != LOCK_UNLOCKED
    
    fun isHardLocked(): Boolean = getLockState() == LOCK_HARD
    
    fun getLockReason(): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("reason", "") ?: ""
    
    fun getLockTimestamp(): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getLong("lock_timestamp", 0L)

    /**
     * Enhanced Soft Lock: Persistent Kotlin Compose screen with specific action messages
     * Shows what action triggered the lock and requires user acknowledgment
     * Prevents debug mode activation, app uninstall, and cache clearing
     * 
     * NOTE: Some violations may automatically escalate to hard lock
     */
    fun applySoftLock(reason: String, triggerAction: String = "") {
        // Check if this violation should trigger hard lock instead
        if (SoftLockType.shouldTriggerHardLock(triggerAction, reason)) {
            Log.w(TAG, "Violation requires hard lock - escalating: $reason")
            applyHardLock("ESCALATED: $reason")
            return
        }
        
        if (getLockState() == LOCK_SOFT) {
            Log.d(TAG, "Device already in soft lock state")
            return
        }
        
        Log.i(TAG, "Applying enhanced soft lock: $reason (trigger: $triggerAction)")
        saveState(LOCK_SOFT, reason)
        
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                // Apply Device Owner restrictions to prevent tampering
                applyDeviceOwnerRestrictions()
                
                Log.d(TAG, "Device owner permissions confirmed for enhanced soft lock")
            } catch (e: Exception) {
                Log.e(TAG, "Enhanced soft lock setup failed: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Not device owner - soft lock may have limited functionality")
        }
        
        // Start enhanced soft lock overlay with specific trigger action
        try {
            SoftLockOverlayService.startOverlay(context, reason, triggerAction)
            Log.d(TAG, "Enhanced soft lock overlay started with trigger: $triggerAction")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start enhanced soft lock overlay: ${e.message}", e)
        }
        
        // Start monitoring service for unauthorized activities
        try {
            SoftLockMonitorService.startMonitoring(context)
            Log.d(TAG, "Soft lock monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start soft lock monitoring: ${e.message}", e)
        }
    }
    
    /**
     * Apply Device Owner restrictions during soft lock to prevent tampering
     */
    private fun applyDeviceOwnerRestrictions() {
        try {
            // Prevent app uninstallation
            dpm.setUninstallBlocked(admin, context.packageName, true)
            Log.d(TAG, "App uninstall blocked")
            
            // Add user restrictions to prevent tampering
            dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_USB_FILE_TRANSFER)
            Log.d(TAG, "User restrictions applied for soft lock")
            
            // Disable certain system apps that could be used to bypass restrictions
            val restrictedPackages = listOf(
                "com.android.settings", // Settings app
                "com.android.packageinstaller", // Package installer
                "com.google.android.packageinstaller" // Google package installer
            )
            
            restrictedPackages.forEach { packageName ->
                try {
                    dpm.setApplicationHidden(admin, packageName, true)
                    Log.d(TAG, "Hidden app: $packageName")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not hide app $packageName: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying Device Owner restrictions: ${e.message}", e)
        }
    }

    /**
     * Absolute Hard Lock: Absolute Kiosk Mode.
     * Physically disables Home, Back, and Recents buttons.
     */
    fun applyHardLock(reason: String) {
        if (getLockState() == LOCK_HARD) {
            Log.d(TAG, "Device already in hard lock state")
            return
        }
        
        Log.e(TAG, "Applying hard lock: $reason")
        saveState(LOCK_HARD, reason)
        
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                // 1. Enter Kiosk Mode (Lock Task)
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
                Log.d(TAG, "Lock task packages set")
                
                // 2. Disable Status Bar (Notifications) and Keyguard
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setStatusBarDisabled(admin, true)
                    dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL)
                    Log.d(TAG, "Status bar and keyguard disabled")
                }

                // 3. Apply user restrictions for hard lock
                dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                Log.d(TAG, "User restrictions applied")

                // 4. Start Kiosk Activity
                val intent = Intent(context, HardLockActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("lock_reason", reason)
                }
                context.startActivity(intent)
                Log.d(TAG, "Hard lock activity started")
                
                // 5. Fallback: OS Level Screen Lock
                dpm.lockNow()
                Log.d(TAG, "Device screen locked")
                
            } catch (e: Exception) {
                Log.e(TAG, "Hard lock enforcement failed: ${e.message}", e)
                // Even if some steps fail, save the state so we know device should be locked
            }
        } else {
            Log.e(TAG, "Cannot apply hard lock - not device owner")
        }
    }

    fun unlockDevice() {
        val currentState = getLockState()
        if (currentState == LOCK_UNLOCKED) {
            Log.d(TAG, "Device already unlocked")
            return
        }
        
        Log.i(TAG, "Unlocking device from state: $currentState")
        saveState(LOCK_UNLOCKED, "")
        
        // Clean up soft lock components
        if (currentState == LOCK_SOFT) {
            try {
                // Stop soft lock overlay service
                SoftLockOverlayService.stopOverlay(context)
                Log.d(TAG, "Soft lock overlay stopped")
                
                // Stop soft lock monitoring
                SoftLockMonitorService.stopMonitoring(context)
                Log.d(TAG, "Soft lock monitoring stopped")
                
                // Remove Device Owner restrictions applied during soft lock
                removeDeviceOwnerRestrictions()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up soft lock: ${e.message}", e)
            }
        }
        
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                // Clear kiosk mode
                dpm.setLockTaskPackages(admin, arrayOf())
                Log.d(TAG, "Lock task packages cleared")
                
                // Re-enable status bar and keyguard
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setStatusBarDisabled(admin, false)
                    dpm.setKeyguardDisabledFeatures(admin, 0)
                    Log.d(TAG, "Status bar and keyguard re-enabled")
                }
                
                // Clear user restrictions
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                Log.d(TAG, "User restrictions cleared")
                
            } catch (e: Exception) {
                Log.e(TAG, "Unlock cleanup failed: ${e.message}", e)
                // Continue with launching main activity even if cleanup fails
            }
        } else {
            Log.w(TAG, "Not device owner - unlock may have limited functionality")
        }

        // Launch main activity
        try {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            Log.d(TAG, "Main activity launched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch main activity: ${e.message}", e)
        }
    }
    
    /**
     * Remove Device Owner restrictions applied during soft lock
     */
    private fun removeDeviceOwnerRestrictions() {
        try {
            // Re-enable app uninstallation (though Device Owner should still prevent it)
            dpm.setUninstallBlocked(admin, context.packageName, false)
            Log.d(TAG, "App uninstall restriction removed")
            
            // Clear user restrictions applied during soft lock
            dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_USB_FILE_TRANSFER)
            Log.d(TAG, "Soft lock user restrictions cleared")
            
            // Re-enable hidden system apps
            val restrictedPackages = listOf(
                "com.android.settings",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller"
            )
            
            restrictedPackages.forEach { packageName ->
                try {
                    dpm.setApplicationHidden(admin, packageName, false)
                    Log.d(TAG, "Re-enabled app: $packageName")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not re-enable app $packageName: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error removing Device Owner restrictions: ${e.message}", e)
        }
    }

    private fun saveState(state: String, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", state)
            .putString("reason", reason)
            .putLong("lock_timestamp", System.currentTimeMillis())
            .apply()
    }
}
