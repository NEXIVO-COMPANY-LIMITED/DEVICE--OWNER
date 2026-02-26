package com.microspace.payo.registration

import android.content.Context
import android.util.Log
import com.microspace.payo.data.remote.api.InstallationStatusService
import com.microspace.payo.device.DeviceOwnerManager
import com.microspace.payo.utils.storage.SharedPreferencesManager
import kotlinx.coroutines.launch

/**
 * Manages device registration and installation status reporting
 * Coordinates between device registration and backend API calls
 */
class DeviceRegistrationManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceRegistrationManager"
        private const val PREFS_NAME = "device_registration"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_REGISTRATION_COMPLETE = "registration_complete"
        private const val KEY_INSTALLATION_STATUS_SENT = "installation_status_sent"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val installationStatusService = InstallationStatusService(context)
    private val sharedPrefsManager = SharedPreferencesManager(context)

    /**
     * Get device ID from server registration response.
     */
    fun getServerDeviceId(): String? {
        val deviceId = sharedPrefsManager.getDeviceId()
            ?: prefs.getString(KEY_DEVICE_ID, null)
            ?: context.getSharedPreferences("device_data", Context.MODE_PRIVATE).getString("device_id_for_heartbeat", null)
        
        if (deviceId.isNullOrBlank()) {
            Log.w(TAG, "⚠️ Device ID not found - device must be registered first")
            return null
        }
        
        // Validate it's server-assigned
        if (deviceId.startsWith("ANDROID-") || deviceId.startsWith("UNREGISTERED-")) {
            Log.e(TAG, "❌ ERROR: Found locally generated device ID: $deviceId")
            return null
        }
        
        return deviceId
    }

    /**
     * Mark device registration as complete
     */
    fun markRegistrationComplete() {
        prefs.edit().putBoolean(KEY_REGISTRATION_COMPLETE, true).apply()
        sharedPrefsManager.setRegistrationCompleted(true)
        Log.d(TAG, "Device registration marked as complete")
    }

    /**
     * Check if device registration is complete
     */
    fun isRegistrationComplete(): Boolean {
        return prefs.getBoolean(KEY_REGISTRATION_COMPLETE, false) || sharedPrefsManager.isRegistrationCompleted()
    }

    /**
     * Check if installation status has been sent
     */
    fun hasInstallationStatusBeenSent(): Boolean {
        return prefs.getBoolean(KEY_INSTALLATION_STATUS_SENT, false)
    }

    /**
     * Mark installation status as sent
     */
    private fun markInstallationStatusSent() {
        prefs.edit().putBoolean(KEY_INSTALLATION_STATUS_SENT, true).apply()
        Log.d(TAG, "Installation status marked as sent")
    }

    /**
     * Send installation status to backend
     * Should be called after successful device registration
     */
    fun sendInstallationStatus(
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val deviceId = getServerDeviceId()
        
        if (deviceId.isNullOrBlank()) {
            Log.e(TAG, "❌ Cannot send installation status: Device not registered yet")
            onFailure("Device must be registered first")
            return
        }
        
        if (hasInstallationStatusBeenSent()) {
            Log.d(TAG, "Installation status already sent for device: $deviceId")
            cleanupProvisioningWiFi() // Ensure WiFi is cleaned up even if status was already sent
            onSuccess()
            return
        }

        Log.d(TAG, "Sending installation status for device: $deviceId")
        
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val success = installationStatusService.sendInstallationStatusWithRetry(
                    deviceId = deviceId,
                    completed = true,
                    reason = "Device Owner activated successfully",
                    maxRetries = 3
                )

                if (success) {
                    markInstallationStatusSent()
                    cleanupProvisioningWiFi()
                    Log.d(TAG, "✓ Installation status sent successfully and WiFi cleanup initiated")
                    onSuccess()
                } else {
                    Log.e(TAG, "✗ Failed to send installation status")
                    onFailure("Failed to send installation status after retries")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during installation status send: ${e.message}", e)
                onFailure(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Send installation status with lifecycle scope (preferred method)
     */
    fun sendInstallationStatusWithLifecycle(
        lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val deviceId = getServerDeviceId()
        
        if (deviceId.isNullOrBlank()) {
            Log.e(TAG, "❌ Cannot send installation status: Device not registered yet")
            onFailure("Device must be registered first")
            return
        }
        
        if (hasInstallationStatusBeenSent()) {
            Log.d(TAG, "Installation status already sent for device: $deviceId")
            cleanupProvisioningWiFi()
            onSuccess()
            return
        }

        lifecycleScope.launch {
            try {
                val success = installationStatusService.sendInstallationStatusWithRetry(
                    deviceId = deviceId,
                    completed = true,
                    reason = "Device Owner activated successfully",
                    maxRetries = 3
                )

                if (success) {
                    markInstallationStatusSent()
                    cleanupProvisioningWiFi()
                    Log.d(TAG, "✓ Installation status sent successfully and WiFi cleanup initiated")
                    onSuccess()
                } else {
                    Log.e(TAG, "✗ Failed to send installation status")
                    onFailure("Failed to send installation status after retries")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during installation status send: ${e.message}", e)
                onFailure(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Forgets the WiFi network that was used during provisioning.
     * Uses improved persistent storage from SharedPreferencesManager.
     */
    fun cleanupProvisioningWiFi() {
        try {
            val provisioningSsid = sharedPrefsManager.getProvisioningWifiSsid()
            if (!provisioningSsid.isNullOrBlank()) {
                Log.i(TAG, "🧹 Cleaning up provisioning WiFi: $provisioningSsid")
                val dom = DeviceOwnerManager(context)
                dom.forgetWiFiNetwork(provisioningSsid)
                
                // Clear the saved SSID from persistent storage
                sharedPrefsManager.clearProvisioningWifiSsid()
                Log.d(TAG, "Provisioning SSID reference cleared from storage")
            } else {
                Log.d(TAG, "No provisioning WiFi SSID found to clean up")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during WiFi cleanup: ${e.message}")
        }
    }

    /**
     * Reset registration state (for testing/debugging)
     */
    fun resetRegistrationState() {
        prefs.edit().clear().apply()
        sharedPrefsManager.setRegistrationCompleted(false)
        Log.d(TAG, "Registration state reset")
    }

    /**
     * Get registration status summary
     */
    fun getRegistrationStatus(): String {
        val deviceId = getServerDeviceId() ?: "NOT_REGISTERED"
        val isComplete = isRegistrationComplete()
        val statusSent = hasInstallationStatusBeenSent()
        
        return """
            Device ID: $deviceId
            Registration Complete: $isComplete
            Installation Status Sent: $statusSent
        """.trimIndent()
    }
}
