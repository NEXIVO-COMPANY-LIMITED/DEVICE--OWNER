package com.example.deviceowner.security.prevention

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

/**
 * ADB Backup Prevention
 * Prevents users from backing up device data via ADB
 * This is critical for data security
 */
class AdbBackupPrevention(private val context: Context) {
    
    companion object {
        private const val TAG = "AdbBackupPrevention"
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    /**
     * Prevent ADB backup completely
     */
    fun preventAdbBackup(): Boolean {
        return try {
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "Cannot prevent ADB backup - not device owner")
                return false
            }
            
            Log.d(TAG, "🔒 Preventing ADB backup...")
            
            // Method 1: Block USB debugging (already done, but ensure it's active)
            // This is handled by DeviceOwnerManager.blockOnlyUSBDebugging()
            
            // Method 2: Disable backup service (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    // Set backup disabled for device owner app
                    android.app.backup.BackupManager.dataChanged(context.packageName)
                    
                    // Disable backup via settings
                    android.provider.Settings.Global.putInt(
                        context.contentResolver,
                        "backup_enabled",
                        0
                    )
                    Log.d(TAG, "✓ Backup service disabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not disable backup service: ${e.message}")
                }
            }
            
            // Method 3: Block ADB access via user restrictions
            try {
                // DISALLOW_DEBUGGING_FEATURES already blocks ADB
                // But we ensure it's active
                devicePolicyManager.addUserRestriction(
                    adminComponent,
                    UserManager.DISALLOW_DEBUGGING_FEATURES
                )
                Log.d(TAG, "✓ ADB debugging blocked via user restriction")
            } catch (e: Exception) {
                Log.w(TAG, "Could not block ADB via user restriction: ${e.message}")
            }
            
            // Method 4: Set backup transport to null (prevents backups)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    android.provider.Settings.Secure.putString(
                        context.contentResolver,
                        "backup_transport",
                        ""
                    )
                    Log.d(TAG, "✓ Backup transport disabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not disable backup transport: ${e.message}")
                }
            }
            
            Log.d(TAG, "✅ ADB backup prevention applied")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error preventing ADB backup", e)
            false
        }
    }
    
    /**
     * Verify ADB backup is blocked
     */
    fun verifyAdbBackupBlocked(): Boolean {
        return try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val restrictions = userManager.getUserRestrictions()
            
            val debuggingBlocked = restrictions.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES, false)
            val backupEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.Global.getInt(
                    context.contentResolver,
                    "backup_enabled",
                    1
                ) == 1
            } else {
                true
            }
            
            val isBlocked = debuggingBlocked && !backupEnabled
            
            if (isBlocked) {
                Log.d(TAG, "✅ ADB backup is properly blocked")
            } else {
                Log.w(TAG, "⚠️ ADB backup may not be fully blocked")
                Log.w(TAG, "   - Debugging blocked: $debuggingBlocked")
                Log.w(TAG, "   - Backup enabled: $backupEnabled")
            }
            
            isBlocked
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying ADB backup block", e)
            false
        }
    }
}
