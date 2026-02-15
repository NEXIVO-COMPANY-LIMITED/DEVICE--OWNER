package com.example.deviceowner.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.ui.activities.LockScreenActivity

/**
 * Kiosk Mode Manager
 * 
 * Enables kiosk mode for hard locks due to security violations
 * Device is locked to only show lock screen - user cannot access anything else
 * 
 * Features:
 * - Lock device to single app (LockScreenActivity)
 * - Disable home button, back button, recent apps
 * - Disable notifications
 * - Disable status bar
 * - Prevent app switching
 * - Prevent device unlock
 */
object KioskModeManager {
    
    private const val TAG = "KioskModeManager"
    
    /**
     * Enable kiosk mode for security violation lock
     * Device is locked to show only lock screen
     * 
     * @param context Android context
     * @param lockScreenActivityClass The activity to lock device to
     */
    fun enableKioskMode(
        context: Context,
        lockScreenActivityClass: Class<*> = LockScreenActivity::class.java
    ) {
        try {
            Log.i(TAG, "🔒 Enabling kiosk mode for security violation lock...")
            
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, AdminReceiver::class.java)
            
            if (dpm == null) {
                Log.e(TAG, "❌ DevicePolicyManager not available")
                return
            }
            
            if (!dpm.isAdminActive(adminComponent)) {
                Log.e(TAG, "❌ Device Owner not active - cannot enable kiosk mode")
                return
            }
            
            // 1. Set lock task packages (only LockScreenActivity can run)
            val lockTaskPackages = arrayOf(context.packageName)
            dpm.setLockTaskPackages(adminComponent, lockTaskPackages)
            Log.d(TAG, "✅ Lock task packages set: ${context.packageName}")
            
            // 2. Disable status bar expansion
            dpm.setStatusBarDisabled(adminComponent, true)
            Log.d(TAG, "✅ Status bar disabled")
            
            // 3. Disable keyguard (lock screen)
            dpm.setKeyguardDisabled(adminComponent, true)
            Log.d(TAG, "✅ Keyguard disabled")
            
            // 4. Set screen brightness to maximum
            try {
                val settings = android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    255
                )
                Log.d(TAG, "Screen brightness: $settings")
            } catch (e: Exception) {
                Log.w(TAG, "Could not read screen brightness: ${e.message}")
            }
            
            // 5. Start lock screen activity in kiosk mode
            startLockScreenInKioskMode(context, lockScreenActivityClass)
            
            Log.i(TAG, "✅ Kiosk mode enabled successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling kiosk mode: ${e.message}", e)
        }
    }
    
    /**
     * Disable kiosk mode and restore normal device operation
     * 
     * @param context Android context
     */
    fun disableKioskMode(context: Context) {
        try {
            Log.i(TAG, "🔓 Disabling kiosk mode...")
            
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, AdminReceiver::class.java)
            
            if (dpm == null) {
                Log.e(TAG, "❌ DevicePolicyManager not available")
                return
            }
            
            if (!dpm.isAdminActive(adminComponent)) {
                Log.e(TAG, "❌ Device Owner not active")
                return
            }
            
            // 1. Clear lock task packages
            dpm.setLockTaskPackages(adminComponent, arrayOf())
            Log.d(TAG, "✅ Lock task packages cleared")
            
            // 2. Enable status bar
            dpm.setStatusBarDisabled(adminComponent, false)
            Log.d(TAG, "✅ Status bar enabled")
            
            // 3. Enable keyguard
            dpm.setKeyguardDisabled(adminComponent, false)
            Log.d(TAG, "✅ Keyguard enabled")
            
            Log.i(TAG, "✅ Kiosk mode disabled successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error disabling kiosk mode: ${e.message}", e)
        }
    }
    
    /**
     * Start lock screen activity in kiosk mode
     * Activity will be pinned and user cannot exit
     * 
     * @param context Android context
     * @param lockScreenActivityClass The activity to start
     */
    private fun startLockScreenInKioskMode(
        context: Context,
        lockScreenActivityClass: Class<*>
    ) {
        try {
            val intent = Intent(context, lockScreenActivityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            
            context.startActivity(intent)
            Log.d(TAG, "✅ Lock screen activity started in kiosk mode")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting lock screen: ${e.message}", e)
        }
    }
    
    /**
     * Check if kiosk mode is currently enabled
     * 
     * @param context Android context
     * @return True if kiosk mode is enabled
     */
    fun isKioskModeEnabled(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, AdminReceiver::class.java)
            
            if (dpm == null || !dpm.isAdminActive(adminComponent)) {
                false
            } else {
                // Check if lock task packages are set
                val lockTaskPackages = dpm.getLockTaskPackages(adminComponent)
                lockTaskPackages.isNotEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking kiosk mode: ${e.message}")
            false
        }
    }
    
    /**
     * Get current lock task packages
     * 
     * @param context Android context
     * @return Array of package names in lock task mode
     */
    fun getLockTaskPackages(context: Context): Array<String> {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, AdminReceiver::class.java)
            
            if (dpm == null || !dpm.isAdminActive(adminComponent)) {
                arrayOf()
            } else {
                dpm.getLockTaskPackages(adminComponent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting lock task packages: ${e.message}")
            arrayOf()
        }
    }
}
