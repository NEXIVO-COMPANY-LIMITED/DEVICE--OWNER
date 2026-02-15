package com.example.deviceowner.security.firmware

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * Firmware Security stub – native C++/JNI code has been removed.
 * All methods return safe defaults; no native library is loaded.
 */
object FirmwareSecurity {

    private const val TAG = "FirmwareSecurity"

    fun activateSecurityMode(): Boolean {
        Log.d(TAG, "Firmware security disabled (native code removed)")
        return false
    }

    fun deactivateSecurityMode(authToken: String): Boolean = false

    fun checkSecurityStatus(): SecurityStatus? = null

    fun getViolations(): List<Violation> = emptyList()

    fun clearViolations(): Boolean = false

    fun runDiagnostics(): Map<String, String> = mapOf("firmware_security" to "Native library removed (stub)")

    suspend fun monitorViolations(context: Context, callback: (Violation) -> Unit) = withContext(Dispatchers.IO) {
        while (isActive) {
            delay(2000)
        }
    }

    fun isInitialized(): Boolean = false

    fun getVersion(): String = "Stub (native removed)"

    fun exportStatusToFile(context: Context): File? = null

    data class SecurityStatus(
        val bootloaderLocked: Boolean,
        val securityEnabled: Boolean,
        val buttonBlocking: Boolean,
        val bootMode: String = "",
        val violations: ViolationStats,
        val lastViolation: String,
        val timestamp: Long
    ) {
        val isFullySecured: Boolean get() = bootloaderLocked && securityEnabled && buttonBlocking
        fun toJson(): String = """
            {"bootloaderLocked":$bootloaderLocked,"securityEnabled":$securityEnabled,
            "buttonBlocking":$buttonBlocking,"bootMode":"$bootMode","violations":${violations.toJson()},
            "lastViolation":"$lastViolation","timestamp":$timestamp,"isFullySecured":$isFullySecured}
        """.trimIndent()
    }

    data class ViolationStats(val total: Long, val recovery: Long, val fastboot: Long) {
        fun toJson(): String = "{\"total\":$total,\"recovery\":$recovery,\"fastboot\":$fastboot}"
    }

    data class Violation(val timestamp: String, val type: String, val details: String) {
        val severity: String
            get() = when (type) {
                "BOOTLOADER_UNLOCKED", "FASTBOOT_UNLOCKED",
                "FASTBOOT_ATTEMPT", "UNLOCK_ATTEMPT" -> "CRITICAL"
                "RECOVERY_ATTEMPT", "EDL_ATTEMPT" -> "HIGH"
                "BOOTLOADER_STATUS_UNKNOWN", "BUTTON_UNBLOCK_ATTEMPT" -> "HIGH"
                "ADB_ROOT_ATTEMPT", "SYSFS_ACCESS_DENIED", "PROPERTY_SET_FAILED" -> "MEDIUM"
                else -> "LOW"
            }
    }
}
