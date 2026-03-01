package com.microspace.payo.security.response

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.microspace.payo.receivers.admin.AdminReceiver

/**
 * Remote Wipe Manager
 * Provides remote wipe capability for compromised devices
 * WARNING: This will erase all device data
 */
class RemoteWipeManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RemoteWipeManager"
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, com.microspace.payo.receivers.admin.AdminReceiver::class.java)
    
    /**
     * Perform remote wipe (factory reset)
     * WARNING: This will erase ALL data on the device
     */
    fun performRemoteWipe(reason: String = "Remote wipe requested by administrator"): Boolean {
        return try {
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.e(TAG, "Cannot perform remote wipe - not device owner")
                return false
            }
            
            Log.e(TAG, "â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•")
            Log.e(TAG, "ðŸš¨ REMOTE WIPE INITIATED")
            Log.e(TAG, "Reason: $reason")
            Log.e(TAG, "â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•")
            
            // Method 1: Wipe device data (Android 5.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    devicePolicyManager.wipeData(0) // 0 = wipe all data
                    Log.e(TAG, "âœ“ Remote wipe command sent - device will reset")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error performing remote wipe via wipeData", e)
                }
            }
            
            // Method 2: Alternative wipe method
            try {
                // Use reflection to call wipeData with different flags if needed
                val method = devicePolicyManager.javaClass.getMethod(
                    "wipeData",
                    Int::class.javaPrimitiveType
                )
                method.invoke(devicePolicyManager, 0)
                Log.e(TAG, "âœ“ Remote wipe command sent (alternative method)")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error performing remote wipe (alternative method)", e)
            }
            
            Log.e(TAG, "âŒ Failed to perform remote wipe")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception performing remote wipe", e)
            false
        }
    }
    
    /**
     * Wipe only app data (less destructive)
     */
    fun wipeAppData(packageName: String): Boolean {
        return try {
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "Cannot wipe app data - not device owner")
                return false
            }
            
            Log.d(TAG, "Wiping app data for: $packageName")
            
            // Clear app data using reflection (IPackageDataObserver is hidden API)
            val packageManager = context.packageManager
            val packageManagerClass = packageManager.javaClass
            val clearApplicationUserDataMethod = packageManagerClass.getMethod(
                "clearApplicationUserData",
                String::class.java,
                Any::class.java  // Use Any instead of hidden IPackageDataObserver
            )
            
            clearApplicationUserDataMethod.invoke(
                packageManager,
                packageName,
                null
            )
            
            Log.d(TAG, "âœ“ App data wipe initiated for: $packageName")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping app data", e)
            false
        }
    }
    
    /**
     * Check if remote wipe is supported
     */
    fun isRemoteWipeSupported(): Boolean {
        return try {
            devicePolicyManager.isDeviceOwnerApp(context.packageName) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        } catch (e: Exception) {
            false
        }
    }
}




