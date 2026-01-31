package com.example.deviceowner.security.firmware

/**
 * Firmware Security API for Device Owner.
 * See docs/android-firmware-security-complete, docs/deviceowner-firmware-integration-complete,
 * docs/FIRMWARE_ENVIRONMENT_CHECKLIST.md, docs/PIXEL_IMPLEMENTATION_GUIDE.md.
 *
 * Call activateSecurityMode() after Device Owner setup / successful registration.
 */

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FirmwareSecurity {

    private const val TAG = "FirmwareSecurity"
    private const val LIBRARY_NAME = "firmware_security"

    private var initialized = false
    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary(LIBRARY_NAME)
            libraryLoaded = true
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            libraryLoaded = false
            Log.e(TAG, "Failed to load native library: ${e.message}", e)
        }
    }

    private external fun isBootloaderLocked(): Boolean
    private external fun enableButtonBlocking(enable: Boolean): Boolean
    private external fun enableSecurityMode(): Boolean
    private external fun disableSecurityMode(authToken: String): Boolean
    private external fun getSecurityStatus(): String
    private external fun getViolationLog(): String
    private external fun clearViolationLog(): Boolean
    private external fun testSecurityFeatures(): String

    fun activateSecurityMode(): Boolean {
        if (!libraryLoaded) {
            Log.e(TAG, "Cannot activate: native library not loaded")
            return false
        }
        return try {
            Log.i(TAG, "Activating firmware security...")
            val result = enableSecurityMode()
            if (result) {
                Log.i(TAG, "Firmware security activated")
                val status = checkSecurityStatus()
                if (status != null) {
                    Log.i(TAG, "Bootloader: ${if (status.bootloaderLocked) "LOCKED" else "UNLOCKED"}, " +
                        "button blocking: ${if (status.buttonBlocking) "ACTIVE" else "INACTIVE"}")
                }
                initialized = true
            } else {
                Log.w(TAG, "Firmware security activation failed (property/sysfs may need root)")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error activating firmware security", e)
            false
        }
    }

    fun deactivateSecurityMode(authToken: String): Boolean {
        if (!libraryLoaded || !initialized) return false
        if (authToken.length < 32) return false
        return try {
            disableSecurityMode(authToken)
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating firmware security", e)
            false
        }
    }

    fun checkSecurityStatus(): SecurityStatus? {
        if (!libraryLoaded) return null
        return try {
            val json = JSONObject(getSecurityStatus())
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

    fun getViolations(): List<Violation> {
        if (!libraryLoaded) return emptyList()
        return try {
            val arr = JSONArray(getViolationLog())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Violation(
                    timestamp = o.getString("timestamp"),
                    type = o.getString("type"),
                    details = o.getString("details")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting violations", e)
            emptyList()
        }
    }

    fun clearViolations(): Boolean {
        if (!libraryLoaded) return false
        return try { clearViolationLog() } catch (e: Exception) {
            Log.e(TAG, "Error clearing violations", e)
            false
        }
    }

    fun runDiagnostics(): Map<String, String> {
        if (!libraryLoaded) return mapOf("error" to "Library not loaded")
        return try {
            val json = JSONObject(testSecurityFeatures())
            val tests = json.getJSONArray("tests")
            (0 until tests.length()).associate { i ->
                val t = tests.getJSONObject(i)
                t.getString("name") to t.getString("status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running diagnostics", e)
            mapOf("error" to (e.message ?: "Unknown"))
        }
    }

    suspend fun monitorViolations(context: Context, callback: (Violation) -> Unit) = withContext(Dispatchers.IO) {
        var lastViolationTimestamp = ""
        while (isActive) {
            try {
                val violations = getViolations()
                if (violations.isNotEmpty()) {
                    val latestViolation = violations.last()
                    // Check if this is a new violation
                    if (latestViolation.timestamp != lastViolationTimestamp) {
                        lastViolationTimestamp = latestViolation.timestamp
                        callback(latestViolation)
                    }
                }
                delay(2000) // Check every 2 seconds for faster response
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring violations", e)
                delay(5000) // Longer delay on error
            }
        }
    }

    fun isInitialized(): Boolean = initialized && libraryLoaded

    fun getVersion(): String = if (libraryLoaded) "2.0 (Device Owner + Pixel)" else "Not loaded"

    fun exportStatusToFile(context: Context): File? {
        val status = checkSecurityStatus() ?: return null
        return try {
            val file = File(context.getExternalFilesDir(null), "security_status.json")
            file.writeText(status.toJson())
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting status", e)
            null
        }
    }

    data class SecurityStatus(
        val bootloaderLocked: Boolean,
        val securityEnabled: Boolean,
        val buttonBlocking: Boolean,
        val violations: ViolationStats,
        val lastViolation: String,
        val timestamp: Long
    ) {
        val isFullySecured: Boolean get() = bootloaderLocked && securityEnabled && buttonBlocking
        fun toJson(): String = """
            {"bootloaderLocked":$bootloaderLocked,"securityEnabled":$securityEnabled,
            "buttonBlocking":$buttonBlocking,"violations":${violations.toJson()},
            "lastViolation":"$lastViolation","timestamp":$timestamp,"isFullySecured":$isFullySecured}
        """.trimIndent()
    }

    data class ViolationStats(val total: Long, val recovery: Long, val fastboot: Long) {
        fun toJson(): String = "{\"total\":$total,\"recovery\":$recovery,\"fastboot\":$fastboot}"
    }

    data class Violation(val timestamp: String, val type: String, val details: String) {
        val severity: String
            get() = when (type) {
                // JNI emits these; must trigger critical handling (hard lock)
                "BOOTLOADER_UNLOCKED", "FASTBOOT_UNLOCKED",
                "FASTBOOT_ATTEMPT", "UNLOCK_ATTEMPT" -> "CRITICAL"
                "RECOVERY_ATTEMPT", "EDL_ATTEMPT" -> "HIGH"
                "BOOTLOADER_STATUS_UNKNOWN", "BUTTON_UNBLOCK_ATTEMPT" -> "HIGH"
                "ADB_ROOT_ATTEMPT", "SYSFS_ACCESS_DENIED", "PROPERTY_SET_FAILED" -> "MEDIUM"
                else -> "LOW"
            }
    }
}
