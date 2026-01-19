package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MismatchType {
    FINGERPRINT_MISMATCH,
    IMEI_MISMATCH,
    SERIAL_MISMATCH,
    ANDROID_ID_MISMATCH,
    MULTIPLE_MISMATCHES,
    DEVICE_SWAP_DETECTED,
    DEVICE_CLONE_DETECTED
}

data class MismatchDetails(
    val type: MismatchType,
    val description: String,
    val storedValue: String,
    val currentValue: String,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: String = "HIGH"
)

class DeviceMismatchHandler(private val context: Context) {

    companion object {
        private const val TAG = "DeviceMismatchHandler"
    }

    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val auditLog = IdentifierAuditLog(context)
    private val paymentUserLockManager = PaymentUserLockManager(context)

    /**
     * Handle device mismatch
     * Applies HARD LOCK for all mismatch types
     */
    suspend fun handleMismatch(details: MismatchDetails) {
        Log.e(TAG, "Device mismatch detected: ${details.type}")
        Log.e(TAG, "Description: ${details.description}")
        
        // Log the incident
        auditLog.logMismatch(details)
        
        // Get device ID
        val prefs = context.getSharedPreferences("identifier_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", "") ?: ""
        
        // Apply hard lock for all mismatch types
        paymentUserLockManager.applyHardLockForDeviceMismatch(deviceId, details.description)
        
        // Perform additional security actions based on mismatch type
        when (details.type) {
            MismatchType.FINGERPRINT_MISMATCH -> handleFingerprintMismatch(details)
            MismatchType.DEVICE_SWAP_DETECTED -> handleDeviceSwap(details)
            MismatchType.DEVICE_CLONE_DETECTED -> handleDeviceClone(details)
            MismatchType.MULTIPLE_MISMATCHES -> handleMultipleMismatches(details)
            else -> handleGenericMismatch(details)
        }
    }

    /**
     * Handle fingerprint mismatch
     */
    private suspend fun handleFingerprintMismatch(details: MismatchDetails) {
        Log.e(TAG, "Handling fingerprint mismatch")
        
        // Alert backend
        alertBackend(details)
        
        // Log incident
        auditLog.logIncident(
            type = "FINGERPRINT_MISMATCH",
            severity = "HIGH",
            details = details.description
        )
    }

    /**
     * Handle device swap detection
     */
    private suspend fun handleDeviceSwap(details: MismatchDetails) {
        Log.e(TAG, "Device swap detected!")
        
        // Alert backend with high priority
        alertBackend(details)
        
        // Log as critical incident
        auditLog.logIncident(
            type = "DEVICE_SWAP",
            severity = "CRITICAL",
            details = "Device swap detected - multiple identifiers changed"
        )
        
        // Disable critical features
        disableCriticalFeatures()
    }

    /**
     * Handle device clone detection
     */
    private suspend fun handleDeviceClone(details: MismatchDetails) {
        Log.e(TAG, "Device clone detected!")
        
        // Alert backend with critical priority
        alertBackend(details)
        
        // Log as critical incident
        auditLog.logIncident(
            type = "DEVICE_CLONE",
            severity = "CRITICAL",
            details = "Device clone detected - fingerprint matches but other identifiers differ"
        )
        
        // Disable all features
        disableCriticalFeatures()
        
        // Wipe sensitive data
        wipeSensitiveData()
    }

    /**
     * Handle multiple mismatches
     */
    private suspend fun handleMultipleMismatches(details: MismatchDetails) {
        Log.e(TAG, "Multiple mismatches detected")
        
        // Alert backend
        alertBackend(details)
        
        // Log incident
        auditLog.logIncident(
            type = "MULTIPLE_MISMATCHES",
            severity = "CRITICAL",
            details = details.description
        )
        
        // Disable critical features
        disableCriticalFeatures()
    }

    /**
     * Handle generic mismatch
     */
    private suspend fun handleGenericMismatch(details: MismatchDetails) {
        Log.e(TAG, "Generic mismatch detected")
        
        // Alert backend
        alertBackend(details)
        
        // Log incident
        auditLog.logIncident(
            type = "GENERIC_MISMATCH",
            severity = "HIGH",
            details = details.description
        )
    }

    /**
     * Alert backend about mismatch
     * 
     * NOTE: Backend will detect mismatch from heartbeat data automatically.
     * No need to send separate API call. Just log locally and lock device.
     * Backend compares heartbeat data with baseline and responds with lock_status.
     */
    private suspend fun alertBackend(details: MismatchDetails) {
        withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Mismatch detected locally: ${details.type}")
                Log.w(TAG, "Backend will detect this from heartbeat data")
                Log.w(TAG, "Device locked locally. Backend will confirm via heartbeat response.")
                
                // Log locally - backend will detect from heartbeat
                auditLog.logAction(
                    "MISMATCH_DETECTED_LOCALLY", 
                    "Type: ${details.type}, Backend will detect from heartbeat"
                )
                
                // Device is already locked by handleMismatch()
                // Backend will detect mismatch from next heartbeat and respond with lock_status
                // No need for separate API call
                
            } catch (e: Exception) {
                Log.e(TAG, "Error logging mismatch", e)
            }
        }
    }
    
    /**
     * Get current device profile for alert
     */
    private fun getCurrentDeviceProfile(): Map<String, String> {
        return try {
            val identifier = DeviceIdentifier(context)
            val profile = identifier.createDeviceProfile()
            mapOf(
                "imei" to profile.imei,
                "serialNumber" to profile.serialNumber,
                "androidId" to profile.androidId,
                "manufacturer" to profile.manufacturer,
                "model" to profile.model,
                "androidVersion" to profile.androidVersion,
                "apiLevel" to profile.apiLevel.toString(),
                "deviceFingerprint" to profile.deviceFingerprint,
                "simSerialNumber" to profile.simSerialNumber,
                "buildNumber" to profile.buildNumber
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device profile", e)
            emptyMap()
        }
    }
    
    // Removed queueMismatchAlert() - not needed
    // Backend detects mismatch from heartbeat data automatically

    /**
     * Disable critical features
     */
    private fun disableCriticalFeatures() {
        try {
            Log.w(TAG, "Disabling critical features")
            
            // Disable camera
            deviceOwnerManager.disableCamera(true)
            
            // Disable USB
            deviceOwnerManager.disableUSB(true)
            
            // Disable developer options
            deviceOwnerManager.disableDeveloperOptions(true)
            
            auditLog.logAction("FEATURES_DISABLED", "Critical features disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling features", e)
        }
    }

    /**
     * Wipe sensitive data
     */
    fun wipeSensitiveData() {
        try {
            Log.w(TAG, "Wiping sensitive data")
            val wipeManager = SensitiveDataWipeManager(context)
            val success = wipeManager.wipeSensitiveData()
            
            if (success) {
                Log.w(TAG, "✓ Sensitive data wiped successfully")
                auditLog.logAction("DATA_WIPED", "Sensitive data wiped successfully")
            } else {
                Log.w(TAG, "⚠ Sensitive data wipe completed with some failures")
                auditLog.logAction("DATA_WIPE_PARTIAL", "Sensitive data wipe completed with failures")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping data", e)
            auditLog.logAction("DATA_WIPE_ERROR", "Error wiping data: ${e.message}")
        }
    }

    /**
     * Get mismatch history
     */
    suspend fun getMismatchHistory(): List<MismatchDetails> {
        return withContext(Dispatchers.IO) {
            auditLog.getMismatchHistory()
        }
    }

    /**
     * Clear mismatch history
     */
    suspend fun clearMismatchHistory() {
        withContext(Dispatchers.IO) {
            auditLog.clearMismatchHistory()
        }
    }
}
