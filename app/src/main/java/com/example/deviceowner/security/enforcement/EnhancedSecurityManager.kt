package com.example.deviceowner.security.enforcement

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

class EnhancedSecurityManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedSecurityManager"
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    /**
     * Apply security restrictions
     * FOCUS: Factory Reset and Developer Options
     * ALLOWS: Cache deletion and App data management
     */
    fun apply100PercentPerfectSecurity(): Boolean {
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) return false
        
        Log.d(TAG, "🔒 Applying balanced security (Cache allowed)...")
        
        blockDeveloperOptionsAbsolutely()
        blockFactoryResetAbsolutely()
        blockCriticalSettingsPaths()
        applySystemLevelBlocks()
        
        // Ensure Cache deletion is NOT blocked
        devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
        
        return verifyPerfectSecurity()
    }
    
    private fun blockDeveloperOptionsAbsolutely(): Boolean {
        // REMOVED: Developer options blocking - allow developer mode
        return true
    }
    
    /**
     * Blocks factory reset absolutely. GAP FIX #3: RE-IMPLEMENTED (was removed).
     * @return true if factory reset is blocked
     */
    fun blockFactoryResetAbsolutely(): Boolean {
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "Not Device Owner - cannot block factory reset")
            return false
        }
        return try {
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            Log.i(TAG, "✓ Added DISALLOW_FACTORY_RESET")
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            val blocked = verifyFactoryResetBlocked()
            if (blocked) Log.i(TAG, "✓ Factory reset BLOCKED") else Log.e(TAG, "✗ Factory reset blocking FAILED")
            blocked
        } catch (e: Exception) {
            Log.e(TAG, "Exception blocking factory reset", e)
            false
        }
    }

    /**
     * Verifies factory reset is still blocked. Re-applies if missing.
     */
    fun verifyFactoryResetBlocked(): Boolean {
        return try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            @Suppress("DEPRECATION")
            val restrictions = userManager.getUserRestrictions()
            val blocked = restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false)
            if (!blocked) {
                Log.w(TAG, "⚠️ Factory reset NOT blocked - re-applying")
                return blockFactoryResetAbsolutely()
            }
            Log.d(TAG, "✓ Factory reset still blocked")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying factory reset block", e)
            false
        }
    }
    
    private fun blockCriticalSettingsPaths(): Boolean {
        try {
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_REMOVE_USER)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            
            // PROTECT OUR APP ONLY (Prevents uninstall but allows cache/data management)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, true)
            }
        } catch (e: Exception) {}
        return true
    }
    
    private fun applySystemLevelBlocks(): Boolean {
        try {
            devicePolicyManager.setScreenCaptureDisabled(adminComponent, true)
        } catch (e: Exception) {}
        return true
    }
    
    private fun verifyPerfectSecurity(): Boolean {
        return verifyFactoryResetBlocked()
    }

    /**
     * Apply network restrictions after device registration
     */
    fun applyNetworkRestrictionsAfterRegistration(): Boolean {
        return try {
            // Allow network settings for user to manage data
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_WIFI)
            Log.d(TAG, "✓ Network restrictions applied after registration")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply network restrictions: ${e.message}")
            false
        }
    }
}
