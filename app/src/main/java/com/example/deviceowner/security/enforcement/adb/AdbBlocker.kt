package com.microspace.payo.security.enforcement.adb

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import com.microspace.payo.receivers.AdminReceiver
import com.microspace.payo.utils.constants.UserManagerConstants

/**
 * AdbBlocker - USB debugging and file transfer are allowed.
 * No blocking; restrictions are cleared so ADB and MTP work.
 */
class AdbBlocker(private val context: Context) {
    
    companion object {
        private const val TAG = "AdbBlocker"
    }
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, AdminReceiver::class.java)
    
    /**
     * Allow USB debugging and file transfer (clear any restrictions).
     */
    fun disableAdbAndUsbDebugging() {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not device owner")
            return
        }
        try {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            dpm.clearUserRestriction(admin, UserManagerConstants.DISALLOW_CONFIG_DEVELOPER_OPTS)
            Log.d(TAG, "✓ USB debugging and file transfer allowed")
        } catch (e: Exception) {
            Log.e(TAG, "Error allowing USB: ${e.message}", e)
        }
    }
    
    /** No-op: USB debugging is allowed, no monitoring to disable it. */
    fun startAdbMonitoring() {
        Log.d(TAG, "USB debugging allowed - ADB monitoring not active")
    }
}
