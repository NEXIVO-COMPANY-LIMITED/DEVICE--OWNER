package com.microspace.payo.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.microspace.payo.receivers.AdminReceiver
import com.microspace.payo.ui.activities.lock.security.SecurityViolationActivity

/**
 * Kiosk Mode Manager
 */
object KioskModeManager {
    
    private const val TAG = "KioskModeManager"
    
    fun enableKioskMode(
        context: Context,
        lockScreenActivityClass: Class<*> = SecurityViolationActivity::class.java
    ) {
        try {
            Log.i(TAG, "🔒 Enabling kiosk mode...")
            
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, AdminReceiver::class.java)
            
            if (dpm == null || !dpm.isAdminActive(adminComponent)) {
                Log.e(TAG, "❌ Device Owner not active")
                return
            }
            
            val lockTaskPackages = arrayOf(context.packageName)
            dpm.setLockTaskPackages(adminComponent, lockTaskPackages)
            dpm.setStatusBarDisabled(adminComponent, true)
            dpm.setKeyguardDisabled(adminComponent, true)
            
            startLockScreenInKioskMode(context, lockScreenActivityClass)
            Log.i(TAG, "✅ Kiosk mode enabled successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling kiosk mode: ${e.message}")
        }
    }
    
    fun disableKioskMode(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, AdminReceiver::class.java)
            
            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                dpm.setLockTaskPackages(adminComponent, arrayOf())
                dpm.setStatusBarDisabled(adminComponent, false)
                dpm.setKeyguardDisabled(adminComponent, false)
                Log.i(TAG, "✅ Kiosk mode disabled successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error disabling kiosk mode: ${e.message}")
        }
    }
    
    private fun startLockScreenInKioskMode(context: Context, lockScreenActivityClass: Class<*>) {
        try {
            val intent = Intent(context, lockScreenActivityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting lock screen: ${e.message}")
        }
    }

    fun isKioskModeEnabled(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, AdminReceiver::class.java)
        return if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.getLockTaskPackages(adminComponent).isNotEmpty()
        } else false
    }
}
