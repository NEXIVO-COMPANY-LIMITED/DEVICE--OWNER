package com.example.deviceowner.security.enforcement

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

/**
 * Screen Pinning Manager
 * Enforces screen pinning to keep app in foreground
 * Prevents users from switching to other apps
 */
class ScreenPinningManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenPinningManager"
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    /**
     * Enable screen pinning
     */
    fun enableScreenPinning(): Boolean {
        return try {
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "Cannot enable screen pinning - not device owner")
                return false
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Set lock task packages
                val packages = arrayOf(context.packageName)
                devicePolicyManager.setLockTaskPackages(adminComponent, packages)
                Log.d(TAG, "✅ Screen pinning enabled for: ${context.packageName}")
                return true
            } else {
                Log.w(TAG, "Screen pinning not available on Android < 5.0")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling screen pinning", e)
            false
        }
    }
    
    /**
     * Start lock task mode on activity
     */
    fun startLockTask(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.startLockTask()
                Log.d(TAG, "✓ Lock task started")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                activity.startLockTask()
                Log.d(TAG, "✓ Lock task started (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting lock task", e)
        }
    }
    
    /**
     * Stop lock task mode on activity
     */
    fun stopLockTask(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.stopLockTask()
                Log.d(TAG, "✓ Lock task stopped")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                activity.stopLockTask()
                Log.d(TAG, "✓ Lock task stopped (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping lock task", e)
        }
    }
    
    /**
     * Check if lock task is active
     */
    fun isLockTaskActive(activity: Activity): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use reflection for isInLockTaskMode as it might not be available in all SDK versions
                try {
                    val method = activity.javaClass.getMethod("isInLockTaskMode")
                    method.invoke(activity) as? Boolean ?: false
                } catch (e: NoSuchMethodException) {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
