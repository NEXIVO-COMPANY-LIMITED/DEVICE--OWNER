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

    /**
     * Handle device mismatch
     * Performs security actions based on mismatch type
     */
    suspend fun handleMismatch(details: MismatchDetails) {
        Log.e(TAG, "Device mismatch detected: ${details.type}")
        Log.e(TAG, "Description: ${details.description}")
        
        // Log the incident
        auditLog.logMismatch(details)
        
        // Perform security actions
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
        
        // Lock device
        lockDevice()
        
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
        
        // Immediate lock
        lockDevice()
        
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
        
        // Immediate lock
        lockDevice()
        
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
        
        // Lock device
        lockDevice()
        
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
        
        // Lock device
        lockDevice()
        
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
     * Lock device immediately
     */
    fun lockDevice() {
        try {
            Log.w(TAG, "Locking device due to mismatch")
            deviceOwnerManager.lockDevice()
            auditLog.logAction("DEVICE_LOCKED", "Device locked due to identifier mismatch")
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device", e)
        }
    }

    /**
     * Alert backend about mismatch
     * Sends alert via API with retry queue for offline scenarios
     */
    private suspend fun alertBackend(details: MismatchDetails) {
        withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Alerting backend about mismatch: ${details.type}")
                
                // Get device ID
                val prefs = context.getSharedPreferences("identifier_prefs", Context.MODE_PRIVATE)
                val deviceId = prefs.getString("device_id", "") ?: ""
                val loanId = prefs.getString("loan_id", "") ?: ""
                
                if (deviceId.isEmpty()) {
                    Log.e(TAG, "Device ID not found, cannot alert backend")
                    return@withContext
                }
                
                // Create alert
                val mismatchAlert = com.example.deviceowner.data.api.MismatchAlert(
                    deviceId = deviceId,
                    mismatchType = details.type.name,
                    description = details.description,
                    severity = details.severity,
                    storedValue = details.storedValue,
                    currentValue = details.currentValue,
                    timestamp = details.timestamp,
                    deviceProfile = getCurrentDeviceProfile(),
                    loanId = loanId
                )
                
                // Send to backend
                val success = sendMismatchAlert(deviceId, mismatchAlert)
                
                if (success) {
                    Log.d(TAG, "✓ Backend alerted successfully")
                    auditLog.logAction("BACKEND_ALERT_SENT", "Mismatch reported: ${details.type}")
                } else {
                    Log.e(TAG, "✗ Failed to alert backend, queuing for retry")
                    // Queue for retry
                    queueMismatchAlert(mismatchAlert)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error alerting backend", e)
            }
        }
    }
    
    /**
     * Send mismatch alert to backend via API
     */
    private suspend fun sendMismatchAlert(
        deviceId: String,
        alert: com.example.deviceowner.data.api.MismatchAlert
    ): Boolean {
        return try {
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl("http://82.29.168.120/")
                .addConverterFactory(com.google.gson.Gson().let { 
                    retrofit2.converter.gson.GsonConverterFactory.create(it)
                })
                .client(createOkHttpClient())
                .build()
            
            val apiService = retrofit.create(com.example.deviceowner.data.api.HeartbeatApiService::class.java)
            val response = apiService.reportMismatch(deviceId, alert)
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "✓ Mismatch alert sent: ${body?.message}")
                
                // Process any action from backend
                body?.command?.let { command ->
                    Log.w(TAG, "Backend action: ${command.type}")
                }
                
                true
            } else {
                Log.e(TAG, "✗ Failed to send alert: ${response.code()} - ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mismatch alert", e)
            false
        }
    }
    
    /**
     * Create OkHttpClient with proper configuration
     */
    private fun createOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            })
            .build()
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
    
    /**
     * Queue mismatch alert for retry when offline
     */
    private fun queueMismatchAlert(alert: com.example.deviceowner.data.api.MismatchAlert) {
        try {
            val alertQueue = MismatchAlertQueue(context)
            alertQueue.queueAlert(alert)
            Log.d(TAG, "Mismatch alert queued for retry")
            auditLog.logAction("ALERT_QUEUED", "Mismatch alert queued for retry")
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing mismatch alert", e)
        }
    }

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
