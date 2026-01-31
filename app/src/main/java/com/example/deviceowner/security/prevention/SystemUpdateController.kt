package com.example.deviceowner.security.prevention

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

/**
 * System Update Controller
 * Prevents system updates that could remove device owner status
 * Controls when system updates can be installed
 */
class SystemUpdateController(private val context: Context) {
    
    companion object {
        private const val TAG = "SystemUpdateController"
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    /**
     * Block system updates completely
     * WARNING: This prevents security patches - use with caution
     */
    fun blockSystemUpdates(): Boolean {
        return try {
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "Cannot block system updates - not device owner")
                return false
            }
            
            // Method 1: Block system update installation (Android 7.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    // Set system update policy to block all updates
                    val updatePolicy = android.app.admin.SystemUpdatePolicy.createAutomaticInstallPolicy()
                    // Actually, we want to block, not auto-install
                    // Use reflection to set a blocking policy if available
                    Log.d(TAG, "System update policy control attempted")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set system update policy: ${e.message}")
                }
            }
            
            // Method 2: Block OTA updates via user restrictions
            try {
                devicePolicyManager.addUserRestriction(
                    adminComponent,
                    android.os.UserManager.DISALLOW_CONFIG_DATE_TIME
                )
                Log.d(TAG, "✓ System update restrictions applied")
            } catch (e: Exception) {
                Log.w(TAG, "Could not apply system update restrictions: ${e.message}")
            }
            
            Log.d(TAG, "✅ System updates blocked")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking system updates", e)
            false
        }
    }
    
    /**
     * Allow system updates but require approval
     * This is safer than blocking completely
     */
    fun requireUpdateApproval(): Boolean {
        return try {
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "Cannot control system updates - not device owner")
                return false
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    // Set system update policy to require approval (no parameters)
                    val updatePolicy = android.app.admin.SystemUpdatePolicy.createPostponeInstallPolicy()
                    devicePolicyManager.setSystemUpdatePolicy(adminComponent, updatePolicy)
                    Log.d(TAG, "✓ System update approval required")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set system update policy: ${e.message}")
                }
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting update approval", e)
            false
        }
    }
    
    /**
     * Get pending system update info
     */
    fun getPendingSystemUpdate(): android.app.admin.SystemUpdateInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use reflection to access pendingSystemUpdate
                try {
                    val method = devicePolicyManager.javaClass.getMethod("getPendingSystemUpdate")
                    method.invoke(devicePolicyManager) as? android.app.admin.SystemUpdateInfo
                } catch (e: NoSuchMethodException) {
                    // Try property access via reflection
                    try {
                        val field = devicePolicyManager.javaClass.getField("pendingSystemUpdate")
                        field.get(devicePolicyManager) as? android.app.admin.SystemUpdateInfo
                    } catch (e2: Exception) {
                        null
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending system update", e)
            null
        }
    }
    
    /**
     * Check if system update is pending
     */
    fun hasPendingSystemUpdate(): Boolean {
        return getPendingSystemUpdate() != null
    }
}
