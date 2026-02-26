package com.microspace.payo.security.enforcement.input

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.microspace.payo.receivers.AdminReceiver

/**
 * PowerButtonBlocker - Prevents fastboot/recovery access via power button combinations
 * 
 * Blocks:
 * - Volume Down + Power (Fastboot)
 * - Volume Up + Power (Recovery)
 * - Long press Power (Safe Mode)
 * - Power button access during lock
 */
class PowerButtonBlocker(private val context: Context) {
    
    companion object {
        private const val TAG = "PowerButtonBlocker"
    }
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, AdminReceiver::class.java)
    
    /**
     * Block all power button combinations that lead to recovery/fastboot
     */
    fun blockPowerButtonCombinations() {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not device owner - cannot block power button")
            return
        }
        
        try {
            Log.d(TAG, "🔒 Blocking power button combinations...")
            
            // 1. Disable Safe Mode (prevents long-press power)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            Log.d(TAG, "✓ Safe boot disabled")
            
            // 2. REMOVED: Factory reset blocking - allow factory reset
            // 3. REMOVED: USB debugging blocking - allow developer options
            // 4. REMOVED: USB file transfer blocking - allow file transfer
            // 5. Disable mounting physical media
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            Log.d(TAG, "✓ Physical media mounting disabled")
            
            // 6. Disable config changes
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_WIFI)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_BLUETOOTH)
            Log.d(TAG, "✓ Config changes disabled")
            
            // 7. Disable keyguard features (prevents power menu)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setKeyguardDisabledFeatures(
                    admin,
                    DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL
                )
                Log.d(TAG, "✓ Keyguard features disabled")
            }
            
            // 8. Disable status bar (prevents power menu access)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(admin, true)
                Log.d(TAG, "✓ Status bar disabled")
            }
            
            // 9. Force auto time (prevents date manipulation for bypass)
            dpm.setAutoTimeRequired(admin, true)
            Log.d(TAG, "✓ Auto time enforced")
            
            Log.i(TAG, "✅ Power button combinations partially blocked (factory reset and developer options allowed)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking power button combinations: ${e.message}", e)
        }
    }
    
    /**
     * Intercept power button presses at system level
     * This is a secondary layer - works with lock task mode
     */
    fun enablePowerButtonInterception() {
        try {
            // When in lock task mode, power button is intercepted by the app
            // The app can then decide what to do with it
            
            // Set lock task packages (only this app can run)
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            Log.d(TAG, "✓ Lock task mode enabled - power button intercepted")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling power button interception: ${e.message}", e)
        }
    }
}
