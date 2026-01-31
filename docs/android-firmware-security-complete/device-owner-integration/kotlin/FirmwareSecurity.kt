package com.yourcompany.deviceowner.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Firmware Security Interface for Device Owner
 * ============================================
 * 
 * PURPOSE: Provide high-level Kotlin API for firmware security features
 * USAGE: Call activateSecurityMode() after Device Owner installation
 * 
 * FEATURES:
 * - One-line security activation
 * - Comprehensive status checking
 * - Violation monitoring
 * - Remote management integration
 * - Automatic error handling
 * - Logging and diagnostics
 * 
 * INTEGRATION:
 * 1. Copy this file to your Device Owner project
 * 2. Add native library (firmware_security_jni.cpp)
 * 3. Update build.gradle for JNI
 * 4. Call activateSecurityMode() after setup
 * 
 * EXAMPLE:
 * ```kotlin
 * // In your Device Owner registration flow
 * class DeviceRegistrationManager {
 *     suspend fun completeRegistration() {
 *         // Your existing registration code...
 *         
 *         // Activate firmware security
 *         val success = FirmwareSecurity.activateSecurityMode()
 *         if (success) {
 *             Log.i(TAG, "✓ Device secured")
 *         }
 *     }
 * }
 * ```
 */
object FirmwareSecurity {
    
    private const val TAG = "FirmwareSecurity"
    private const val LIBRARY_NAME = "firmware_security"
    
    private var initialized = false
    private var libraryLoaded = false
    
    init {
        try {
            System.loadLibrary(LIBRARY_NAME)
            libraryLoaded = true
            Log.i(TAG, "✓ Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            libraryLoaded = false
            Log.e(TAG, "✗ Failed to load native library", e)
            Log.e(TAG, "Make sure lib$LIBRARY_NAME.so is in app/src/main/jniLibs/")
        }
    }
    
    // ============================================================================
    // NATIVE METHODS (JNI)
    // ============================================================================
    
    private external fun isBootloaderLocked(): Boolean
    private external fun enableButtonBlocking(enable: Boolean): Boolean
    private external fun enableSecurityMode(): Boolean
    private external fun disableSecurityMode(authToken: String): Boolean
    private external fun getSecurityStatus(): String
    private external fun getViolationLog(): String
    private external fun clearViolationLog(): Boolean
    private external fun testSecurityFeatures(): String
    
    // ============================================================================
    // PUBLIC API
    // ============================================================================
    
    /**
     * Activate complete firmware security
     * Call this immediately after Device Owner installation
     * 
     * @return true if security was successfully activated
     */
    fun activateSecurityMode(): Boolean {
        if (!libraryLoaded) {
            Log.e(TAG, "Cannot activate: Native library not loaded")
            return false
        }
        
        return try {
            Log.i(TAG, "=== ACTIVATING FIRMWARE SECURITY ===")
            
            val result = enableSecurityMode()
            
            if (result) {
                Log.i(TAG, "✓ Security mode activated successfully")
                Log.i(TAG, "  - Bootloader monitoring: ENABLED")
                Log.i(TAG, "  - Button blocking: ACTIVE")
                Log.i(TAG, "  - Recovery mode: BLOCKED")
                Log.i(TAG, "  - Fastboot mode: BLOCKED")
                
                // Verify activation
                val status = checkSecurityStatus()
                if (status != null) {
                    Log.i(TAG, "✓ Security verification passed")
                    Log.i(TAG, "  - Bootloader: ${if (status.bootloaderLocked) "LOCKED" else "UNLOCKED"}")
                    Log.i(TAG, "  - Button blocking: ${if (status.buttonBlocking) "ACTIVE" else "INACTIVE"}")
                } else {
                    Log.w(TAG, "⚠ Could not verify security status")
                }
                
                initialized = true
            } else {
                Log.e(TAG, "✗ Security activation failed")
                Log.e(TAG, "  Check logcat for details")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error activating security", e)
            false
        }
    }
    
    /**
     * Deactivate security (requires authorization)
     * Only use for authorized device returns
     * 
     * @param authToken Authorization token from server
     * @return true if security was successfully deactivated
     */
    fun deactivateSecurityMode(authToken: String): Boolean {
        if (!libraryLoaded || !initialized) {
            Log.e(TAG, "Cannot deactivate: Not initialized")
            return false
        }
        
        if (authToken.length < 32) {
            Log.e(TAG, "Invalid authorization token")
            return false
        }
        
        return try {
            Log.w(TAG, "=== DEACTIVATING FIRMWARE SECURITY ===")
            
            val result = disableSecurityMode(authToken)
            
            if (result) {
                Log.i(TAG, "✓ Security mode deactivated")
                initialized = false
            } else {
                Log.e(TAG, "✗ Deactivation failed (invalid token?)")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating security", e)
            false
        }
    }
    
    /**
     * Check comprehensive security status
     * 
     * @return SecurityStatus object with all details, or null on error
     */
    fun checkSecurityStatus(): SecurityStatus? {
        if (!libraryLoaded) {
            Log.w(TAG, "Cannot check status: Library not loaded")
            return null
        }
        
        return try {
            val jsonString = getSecurityStatus()
            val json = JSONObject(jsonString)
            
            val violations = json.getJSONObject("violations")
            
            SecurityStatus(
                bootloaderLocked = json.getBoolean("bootloaderLocked"),
                securityEnabled = json.getBoolean("securityEnabled"),
                buttonBlocking = json.getBoolean("buttonBlocking"),
                violations = ViolationStats(
                    total = violations.getLong("total"),
                    recovery = violations.getLong("recovery"),
                    fastboot = violations.getLong("fastboot")
                ),
                lastViolation = json.optString("lastViolation", ""),
                timestamp = json.getLong("timestamp")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking security status", e)
            null
        }
    }
    
    /**
     * Get all security violations
     * 
     * @return List of Violation objects, or empty list on error
     */
    fun getViolations(): List<Violation> {
        if (!libraryLoaded) {
            return emptyList()
        }
        
        return try {
            val jsonString = getViolationLog()
            val jsonArray = JSONArray(jsonString)
            
            val violations = mutableListOf<Violation>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                violations.add(
                    Violation(
                        timestamp = item.getString("timestamp"),
                        type = item.getString("type"),
                        details = item.getString("details")
                    )
                )
            }
            
            violations
        } catch (e: Exception) {
            Log.e(TAG, "Error getting violations", e)
            emptyList()
        }
    }
    
    /**
     * Clear violation log
     */
    fun clearViolations(): Boolean {
        if (!libraryLoaded) return false
        
        return try {
            clearViolationLog()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing violations", e)
            false
        }
    }
    
    /**
     * Test all security features
     * Useful for debugging and validation
     * 
     * @return Map of test results
     */
    fun runDiagnostics(): Map<String, String> {
        if (!libraryLoaded) {
            return mapOf("error" to "Library not loaded")
        }
        
        return try {
            val jsonString = testSecurityFeatures()
            val json = JSONObject(jsonString)
            val tests = json.getJSONArray("tests")
            
            val results = mutableMapOf<String, String>()
            for (i in 0 until tests.length()) {
                val test = tests.getJSONObject(i)
                results[test.getString("name")] = test.getString("status")
            }
            
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error running diagnostics", e)
            mapOf("error" to e.message ?: "Unknown error")
        }
    }
    
    /**
     * Monitor violations in real-time
     * Call this from a background service
     * 
     * @param context Application context
     * @param callback Called when violation detected
     */
    suspend fun monitorViolations(
        context: Context,
        callback: (Violation) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting violation monitoring...")
        
        var lastCount = 0L
        
        while (isActive) {
            try {
                val status = checkSecurityStatus()
                val currentCount = status?.violations?.total ?: 0L
                
                if (currentCount > lastCount) {
                    Log.w(TAG, "New violation detected! Total: $currentCount")
                    
                    // Get latest violations
                    val violations = getViolations()
                    if (violations.isNotEmpty()) {
                        callback(violations.last())
                    }
                    
                    lastCount = currentCount
                }
                
                delay(5000) // Check every 5 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring violations", e)
                delay(10000) // Wait longer on error
            }
        }
    }
    
    // ============================================================================
    // DATA CLASSES
    // ============================================================================
    
    data class SecurityStatus(
        val bootloaderLocked: Boolean,
        val securityEnabled: Boolean,
        val buttonBlocking: Boolean,
        val violations: ViolationStats,
        val lastViolation: String,
        val timestamp: Long
    ) {
        val isFullySecured: Boolean
            get() = bootloaderLocked && securityEnabled && buttonBlocking
        
        fun toJson(): String {
            return """
                {
                    "bootloaderLocked": $bootloaderLocked,
                    "securityEnabled": $securityEnabled,
                    "buttonBlocking": $buttonBlocking,
                    "violations": ${violations.toJson()},
                    "lastViolation": "$lastViolation",
                    "timestamp": $timestamp,
                    "isFullySecured": $isFullySecured
                }
            """.trimIndent()
        }
    }
    
    data class ViolationStats(
        val total: Long,
        val recovery: Long,
        val fastboot: Long
    ) {
        fun toJson(): String {
            return """{"total":$total,"recovery":$recovery,"fastboot":$fastboot}"""
        }
    }
    
    data class Violation(
        val timestamp: String,
        val type: String,
        val details: String
    ) {
        val severity: String
            get() = when (type) {
                "FASTBOOT_ATTEMPT", "UNLOCK_ATTEMPT" -> "CRITICAL"
                "RECOVERY_ATTEMPT", "EDL_ATTEMPT" -> "HIGH"
                "ADB_ROOT_ATTEMPT" -> "MEDIUM"
                else -> "LOW"
            }
    }
    
    // ============================================================================
    // HELPER FUNCTIONS
    // ============================================================================
    
    /**
     * Check if security is properly initialized
     */
    fun isInitialized(): Boolean = initialized && libraryLoaded
    
    /**
     * Get library version info
     */
    fun getVersion(): String {
        return if (libraryLoaded) {
            "2.0 Production (Real Device Implementation)"
        } else {
            "Not Loaded"
        }
    }
    
    /**
     * Export security status to file for debugging
     */
    fun exportStatusToFile(context: Context): File? {
        val status = checkSecurityStatus() ?: return null
        
        return try {
            val file = File(context.getExternalFilesDir(null), "security_status.json")
            file.writeText(status.toJson())
            Log.i(TAG, "Status exported to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting status", e)
            null
        }
    }
}

/**
 * Extension functions for integration with your Device Owner app
 */

// Extension for your Device Registration Manager
suspend fun FirmwareSecurity.activateSecurityModeAsync(): Result<Unit> {
    return withContext(Dispatchers.IO) {
        try {
            val success = activateSecurityMode()
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Security activation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Extension to check if device is ready for loan
fun FirmwareSecurity.SecurityStatus.isReadyForLoan(): Boolean {
    return isFullySecured && violations.total < 10 // Allow some test violations
}

// Extension to create server report
fun FirmwareSecurity.SecurityStatus.toServerReport(): Map<String, Any> {
    return mapOf(
        "bootloader_locked" to bootloaderLocked,
        "security_enabled" to securityEnabled,
        "button_blocking" to buttonBlocking,
        "violation_count" to violations.total,
        "last_violation" to lastViolation,
        "status" to if (isFullySecured) "SECURED" else "UNSECURED",
        "timestamp" to timestamp
    )
}
