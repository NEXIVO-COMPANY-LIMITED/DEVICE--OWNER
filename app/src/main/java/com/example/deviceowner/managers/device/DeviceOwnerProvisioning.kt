package com.example.deviceowner.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

class DeviceOwnerProvisioning(private val context: Context) {

    companion object {
        private const val TAG = "DeviceOwnerProvisioning"
        
        // Provisioning intents
        const val ACTION_PROVISION_MANAGED_DEVICE = "android.app.action.PROVISION_MANAGED_DEVICE"
        const val ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE = 
            "android.app.action.PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE"
        const val EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME = 
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"
        const val EXTRA_PROVISIONING_SKIP_ENCRYPTION = 
            "android.app.extra.PROVISIONING_SKIP_ENCRYPTION"
        const val EXTRA_PROVISIONING_SKIP_USER_SETUP = 
            "android.app.extra.PROVISIONING_SKIP_USER_SETUP"
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    /**
     * Check if device owner provisioning is available
     * Uses reflection to access the hidden API method across Android versions
     */
    fun isDeviceOwnerProvisioningAvailable(): Boolean {
        return try {
            // Use reflection to call isDeviceOwnerProvisioningSupported
            // This method exists in DevicePolicyManager but is hidden in public API
            val method = devicePolicyManager.javaClass.getMethod(
                "isDeviceOwnerProvisioningSupported"
            )
            method.invoke(devicePolicyManager) as Boolean
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "isDeviceOwnerProvisioningSupported method not found on this API level")
            true // Assume available if method doesn't exist
        } catch (e: Exception) {
            Log.w(TAG, "Error checking provisioning support: ${e.message}", e)
            true // Assume available if check fails
        }
    }

    /**
     * Get provisioning intent for NFC/QR code provisioning
     * This intent should be triggered from a trusted provisioning app
     */
    fun getProvisioningIntent(): Intent {
        return Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE).apply {
            putExtra(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent
            )
            putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, false)
            putExtra(EXTRA_PROVISIONING_SKIP_USER_SETUP, false)
        }
    }

    /**
     * Check if provisioning is needed
     */
    fun isProvisioningNeeded(): Boolean {
        return !isDeviceOwnerAlready()
    }

    /**
     * Check if app is already device owner
     */
    fun isDeviceOwnerAlready(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } else {
            @Suppress("DEPRECATION")
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        }
    }

    /**
     * Log provisioning status
     */
    fun logProvisioningStatus() {
        Log.d(TAG, "Device Owner Provisioning Status:")
        Log.d(TAG, "  - Provisioning Available: ${isDeviceOwnerProvisioningAvailable()}")
        Log.d(TAG, "  - Already Device Owner: ${isDeviceOwnerAlready()}")
        Log.d(TAG, "  - Provisioning Needed: ${isProvisioningNeeded()}")
        Log.d(TAG, "  - Admin Component: $adminComponent")
    }
}
