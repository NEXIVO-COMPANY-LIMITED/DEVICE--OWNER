package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles tamper detection responses with severity-based actions
 * Integrates with TamperDetector to provide coordinated security responses
 * 
 * HARD LOCK triggers:
 * - CRITICAL severity tampering
 * - HIGH severity tampering
 */
class TamperResponseHandler(private val context: Context) {

    companion object {
        private const val TAG = "TamperResponseHandler"
    }

    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val auditLog = IdentifierAuditLog(context)
    private val adaptiveProtectionManager = AdaptiveProtectionManager(context)
    private val paymentUserLockManager = PaymentUserLockManager(context)

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
     * Applies HARD LOCK immediately
     */
    private suspend fun handleCriticalTamper(tamperStatus: TamperStatus) {
        Log.e(TAG, "CRITICAL tamper response initiated")

        try {
            val deviceId = getDeviceId()
            val tamperDetails = tamperStatus.tamperFlags.joinToString(", ")

            // 1. Apply hard lock
            paymentUserLockManager.applyHardLockForTampering(deviceId, tamperDetails)

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
     * Applies HARD LOCK with feature disabling
     */
    private suspend fun handleHighTamper(tamperStatus: TamperStatus) {
        Log.w(TAG, "HIGH tamper response initiated")

        try {
            val deviceId = getDeviceId()
            val tamperDetails = tamperStatus.tamperFlags.joinToString(", ")

            // 1. Apply hard lock
            paymentUserLockManager.applyHardLockForTampering(deviceId, tamperDetails)

            // 2. Disable critical features
            disableCriticalFeatures(tamperStatus)

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
     * Moderate response with monitoring (no lock)
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
     * Get device ID from preferences
     */
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("identifier_prefs", Context.MODE_PRIVATE)
        return prefs.getString("device_id", "") ?: ""
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

                // Log locally - backend will detect from heartbeat data
                sendTamperAlert(deviceId, tamperAlert)
            } catch (e: Exception) {
                Log.e(TAG, "Error alerting backend", e)
            }
        }
    }

    /**
     * Send tamper alert to backend
     * 
     * NOTE: Backend will detect tampering from heartbeat data automatically.
     * No need to send separate API call. Just log locally and lock device.
     * Backend compares heartbeat data with baseline and responds with lock_status.
     */
    private suspend fun sendTamperAlert(
        deviceId: String,
        alert: TamperAlert
    ): Boolean {
        return try {
            Log.w(TAG, "Tampering detected locally: ${alert.tamperFlags.joinToString(", ")}")
            Log.w(TAG, "Backend will detect this from heartbeat data")
            Log.w(TAG, "Device locked locally. Backend will confirm via heartbeat response.")
            
            // Log locally - backend will detect from heartbeat
            auditLog.logAction(
                "TAMPER_DETECTED_LOCALLY",
                "Flags: ${alert.tamperFlags.joinToString(", ")}, Backend will detect from heartbeat"
            )
            
            // Device is already locked by handleTamperAlert()
            // Backend will detect tampering from next heartbeat and respond with lock_status
            // No need for separate API call
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error logging tamper alert", e)
            false
        }
    }

    // Removed queueTamperAlert() - not needed
    // Backend detects tampering from heartbeat data automatically

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
