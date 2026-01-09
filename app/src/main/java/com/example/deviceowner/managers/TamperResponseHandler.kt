package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles tamper detection responses with severity-based actions
 * Integrates with TamperDetector to provide coordinated security responses
 */
class TamperResponseHandler(private val context: Context) {

    companion object {
        private const val TAG = "TamperResponseHandler"
    }

    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val auditLog = IdentifierAuditLog(context)
    private val adaptiveProtectionManager = AdaptiveProtectionManager(context)

    /**
     * Handle tamper detection with severity-based response
     * Main entry point for tamper response
     */
    suspend fun handleTamperDetection(tamperStatus: TamperStatus) {
        Log.e(TAG, "Tamper detected: ${tamperStatus.tamperFlags}")
        Log.e(TAG, "Severity: ${tamperStatus.severity}")

        // Log the tamper detection
        auditLog.logIncident(
            type = "TAMPER_DETECTED",
            severity = tamperStatus.severity.name,
            details = "Tamper flags: ${tamperStatus.tamperFlags.joinToString(", ")}"
        )

        // Execute response based on severity
        when (tamperStatus.severity) {
            TamperSeverity.CRITICAL -> handleCriticalTamper(tamperStatus)
            TamperSeverity.HIGH -> handleHighTamper(tamperStatus)
            TamperSeverity.MEDIUM -> handleMediumTamper(tamperStatus)
            TamperSeverity.LOW -> handleLowTamper(tamperStatus)
            TamperSeverity.NONE -> {
                Log.d(TAG, "No tampering detected")
            }
        }
    }

    /**
     * Handle CRITICAL severity tampering
     * Immediate aggressive response
     */
    private suspend fun handleCriticalTamper(tamperStatus: TamperStatus) {
        Log.e(TAG, "CRITICAL tamper response initiated")

        try {
            // 1. Lock device immediately
            lockDevice("Critical tampering detected")

            // 2. Disable all features
            disableAllFeatures()

            // 3. Wipe sensitive data
            wipeSensitiveData()

            // 4. Alert backend
            alertBackend(tamperStatus)

            // 5. Log comprehensive incident
            logTamperResponse(tamperStatus, "CRITICAL_RESPONSE_EXECUTED")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling critical tamper", e)
            auditLog.logIncident(
                type = "CRITICAL_TAMPER_RESPONSE_ERROR",
                severity = "CRITICAL",
                details = "Error during critical tamper response: ${e.message}"
            )
        }
    }

    /**
     * Handle HIGH severity tampering
     * Strong response with feature disabling
     */
    private suspend fun handleHighTamper(tamperStatus: TamperStatus) {
        Log.w(TAG, "HIGH tamper response initiated")

        try {
            // 1. Disable critical features
            disableCriticalFeatures(tamperStatus)

            // 2. Lock device
            lockDevice("High severity tampering detected")

            // 3. Alert backend
            alertBackend(tamperStatus)

            // 4. Enable enhanced monitoring
            enableEnhancedMonitoring()

            // 5. Log incident
            logTamperResponse(tamperStatus, "HIGH_RESPONSE_EXECUTED")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling high tamper", e)
            auditLog.logIncident(
                type = "HIGH_TAMPER_RESPONSE_ERROR",
                severity = "HIGH",
                details = "Error during high tamper response: ${e.message}"
            )
        }
    }

    /**
     * Handle MEDIUM severity tampering
     * Moderate response with monitoring
     */
    private suspend fun handleMediumTamper(tamperStatus: TamperStatus) {
        Log.w(TAG, "MEDIUM tamper response initiated")

        try {
            // 1. Alert backend
            alertBackend(tamperStatus)

            // 2. Enable enhanced monitoring
            enableEnhancedMonitoring()

            // 3. Log incident
            logTamperResponse(tamperStatus, "MEDIUM_RESPONSE_EXECUTED")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling medium tamper", e)
            auditLog.logIncident(
                type = "MEDIUM_TAMPER_RESPONSE_ERROR",
                severity = "MEDIUM",
                details = "Error during medium tamper response: ${e.message}"
            )
        }
    }

    /**
     * Handle LOW severity tampering
     * Minimal response with logging only
     */
    private suspend fun handleLowTamper(tamperStatus: TamperStatus) {
        Log.d(TAG, "LOW tamper response initiated")

        try {
            // 1. Log incident
            logTamperResponse(tamperStatus, "LOW_RESPONSE_EXECUTED")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling low tamper", e)
        }
    }

    /**
     * Lock device immediately
     */
    private fun lockDevice(reason: String) {
        try {
            Log.w(TAG, "Locking device: $reason")
            deviceOwnerManager.lockDevice()
            auditLog.logAction("DEVICE_LOCKED", reason)
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device", e)
        }
    }

    /**
     * Disable all features
     */
    private suspend fun disableAllFeatures() {
        try {
            Log.w(TAG, "Disabling all features")

            withContext(Dispatchers.IO) {
                // Disable camera
                deviceOwnerManager.disableCamera(true)
                Log.d(TAG, "✓ Camera disabled")

                // Disable USB
                deviceOwnerManager.disableUSB(true)
                Log.d(TAG, "✓ USB disabled")

                // Disable developer options
                deviceOwnerManager.disableDeveloperOptions(true)
                Log.d(TAG, "✓ Developer options disabled")

                // Note: Microphone control not available in DeviceOwnerManager
                // This would require additional permissions or custom implementation
            }

            auditLog.logAction("ALL_FEATURES_DISABLED", "All device features disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling all features", e)
        }
    }

    /**
     * Disable critical features based on tamper flags
     */
    private suspend fun disableCriticalFeatures(tamperStatus: TamperStatus) {
        try {
            Log.w(TAG, "Disabling critical features")

            withContext(Dispatchers.IO) {
                // Disable camera if root or bootloader detected
                if (tamperStatus.tamperFlags.contains("ROOTED") ||
                    tamperStatus.tamperFlags.contains("BOOTLOADER_UNLOCKED")
                ) {
                    deviceOwnerManager.disableCamera(true)
                    Log.d(TAG, "✓ Camera disabled")
                }

                // Disable USB if custom ROM or USB debug detected
                if (tamperStatus.tamperFlags.contains("CUSTOM_ROM") ||
                    tamperStatus.tamperFlags.contains("USB_DEBUG_ENABLED")
                ) {
                    deviceOwnerManager.disableUSB(true)
                    Log.d(TAG, "✓ USB disabled")
                }

                // Disable developer options if dev mode detected
                if (tamperStatus.tamperFlags.contains("DEV_MODE_ENABLED")) {
                    deviceOwnerManager.disableDeveloperOptions(true)
                    Log.d(TAG, "✓ Developer options disabled")
                }
            }

            auditLog.logAction(
                "CRITICAL_FEATURES_DISABLED",
                "Features disabled for flags: ${tamperStatus.tamperFlags.joinToString(", ")}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling critical features", e)
        }
    }

    /**
     * Wipe sensitive data
     */
    private suspend fun wipeSensitiveData() {
        try {
            Log.w(TAG, "Wiping sensitive data")

            withContext(Dispatchers.IO) {
                val wipeManager = SensitiveDataWipeManager(context)
                val success = wipeManager.wipeSensitiveData()

                if (success) {
                    Log.w(TAG, "✓ Sensitive data wiped successfully")
                    auditLog.logAction("DATA_WIPED", "Sensitive data wiped successfully")
                } else {
                    Log.w(TAG, "⚠ Sensitive data wipe completed with some failures")
                    auditLog.logAction("DATA_WIPE_PARTIAL", "Sensitive data wipe completed with failures")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping data", e)
            auditLog.logAction("DATA_WIPE_ERROR", "Error wiping data: ${e.message}")
        }
    }

    /**
     * Alert backend about tampering
     */
    private suspend fun alertBackend(tamperStatus: TamperStatus) {
        withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Alerting backend about tampering")

                val prefs = context.getSharedPreferences("identifier_prefs", Context.MODE_PRIVATE)
                val deviceId = prefs.getString("device_id", "") ?: ""

                if (deviceId.isEmpty()) {
                    Log.e(TAG, "Device ID not found, cannot alert backend")
                    return@withContext
                }

                // Create tamper alert
                val tamperAlert = TamperAlert(
                    deviceId = deviceId,
                    tamperFlags = tamperStatus.tamperFlags,
                    severity = tamperStatus.severity.name,
                    timestamp = tamperStatus.timestamp,
                    details = buildTamperDetails(tamperStatus)
                )

                // Send to backend
                val success = sendTamperAlert(deviceId, tamperAlert)

                if (success) {
                    Log.d(TAG, "✓ Backend alerted successfully")
                    auditLog.logAction("BACKEND_ALERT_SENT", "Tamper alert sent to backend")
                } else {
                    Log.e(TAG, "✗ Failed to alert backend, queuing for retry")
                    queueTamperAlert(tamperAlert)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error alerting backend", e)
            }
        }
    }

    /**
     * Send tamper alert to backend
     */
    private suspend fun sendTamperAlert(
        deviceId: String,
        alert: TamperAlert
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
            
            // Convert TamperAlert to MismatchAlert for backend reporting
            val mismatchAlert = com.example.deviceowner.data.api.MismatchAlert(
                deviceId = deviceId,
                mismatchType = "TAMPER_DETECTED",
                description = "Tamper detected: ${alert.tamperFlags.joinToString(", ")}",
                severity = alert.severity,
                storedValue = "CLEAN",
                currentValue = alert.tamperFlags.joinToString(", "),
                timestamp = alert.timestamp
            )
            
            val response = apiService.reportMismatch(deviceId, mismatchAlert)

            if (response.isSuccessful) {
                val body: com.example.deviceowner.data.api.MismatchAlertResponse? = response.body()
                Log.d(TAG, "✓ Tamper alert sent: ${body?.message}")

                // Process any action from backend
                body?.command?.let { command ->
                    Log.w(TAG, "Backend action received: ${command.type}")
                }

                true
            } else {
                Log.e(TAG, "✗ Failed to send alert: ${response.code()} - ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tamper alert", e)
            false
        }
    }

    /**
     * Queue tamper alert for retry when offline
     */
    private fun queueTamperAlert(alert: TamperAlert) {
        try {
            val prefs = context.getSharedPreferences("tamper_alerts_queue", Context.MODE_PRIVATE)
            val gson = com.google.gson.Gson()
            val alertJson = gson.toJson(alert)

            val queue = prefs.getStringSet("alerts", mutableSetOf()) ?: mutableSetOf()
            queue.add(alertJson)

            prefs.edit().putStringSet("alerts", queue).apply()
            Log.d(TAG, "Tamper alert queued for retry")
            auditLog.logAction("ALERT_QUEUED", "Tamper alert queued for retry")
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing tamper alert", e)
        }
    }

    /**
     * Enable enhanced monitoring
     */
    private suspend fun enableEnhancedMonitoring() {
        try {
            Log.d(TAG, "Enabling enhanced monitoring")

            withContext(Dispatchers.IO) {
                adaptiveProtectionManager.setProtectionLevel(AdaptiveProtectionManager.ProtectionLevel.ENHANCED)
                Log.d(TAG, "✓ Enhanced monitoring enabled")
                auditLog.logAction("ENHANCED_MONITORING_ENABLED", "Enhanced monitoring activated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling enhanced monitoring", e)
        }
    }

    /**
     * Build detailed tamper information
     */
    private fun buildTamperDetails(tamperStatus: TamperStatus): Map<String, String> {
        return mapOf(
            "timestamp" to tamperStatus.timestamp.toString(),
            "severity" to tamperStatus.severity.name,
            "flags_count" to tamperStatus.tamperFlags.size.toString(),
            "flags" to tamperStatus.tamperFlags.joinToString("|"),
            "is_tampered" to tamperStatus.isTampered.toString()
        )
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
     * Log tamper response
     */
    private fun logTamperResponse(tamperStatus: TamperStatus, action: String) {
        try {
            auditLog.logIncident(
                type = action,
                severity = tamperStatus.severity.name,
                details = "Tamper flags: ${tamperStatus.tamperFlags.joinToString(", ")}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error logging tamper response", e)
        }
    }

    /**
     * Get tamper response history
     */
    suspend fun getTamperResponseHistory(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                auditLog.getAuditEntries()
                    .filter { it.type == "TAMPER_DETECTED" }
                    .map { "${it.timestamp}: ${it.description}" }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting tamper history", e)
                emptyList()
            }
        }
    }
}

/**
 * Tamper alert data class for backend communication
 */
data class TamperAlert(
    val deviceId: String,
    val tamperFlags: List<String>,
    val severity: String,
    val timestamp: Long,
    val details: Map<String, String> = emptyMap()
)

/**
 * Protection level enum
 */
enum class ProtectionLevel {
    NORMAL,
    ENHANCED,
    CRITICAL
}
