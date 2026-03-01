package com.microspace.payo.services.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.microspace.payo.data.DeviceIdProvider
import com.microspace.payo.data.models.tamper.TamperEventRequest
import com.microspace.payo.data.remote.ApiClient
import com.microspace.payo.services.reporting.ServerBugAndLogReporter
import kotlinx.coroutines.*
import java.io.File

/**
 * Detects security violations (tampering) on the device and reports them to the backend.
 * 
 * Updated: Unknown Sources check removed as it's now enforced via Device Owner Policy
 * to prevent false-positive hard locks on boot.
 */
object TamperDetectionService {

    private const val TAG = "TamperDetectionService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val apiClient by lazy { ApiClient() }

    fun checkAndReportTampering(context: Context) {
        scope.launch {
            try {
                val deviceId = DeviceIdProvider.getDeviceId(context)
                
                if (deviceId.isNullOrBlank() || 
                    deviceId.startsWith("ANDROID-") || 
                    deviceId.startsWith("UNREGISTERED-")) {
                    Log.w(TAG, "âš ï¸ Cannot check tampering: device not registered")
                    return@launch
                }

                Log.d(TAG, "ðŸ” Checking for security violations...")
                val violations = mutableListOf<TamperViolation>()
                
                if (isBootloaderUnlocked()) {
                    violations.add(TamperViolation("BOOTLOADER_UNLOCKED", "Device bootloader is unlocked"))
                }
                
                if (isDeviceRooted()) {
                    violations.add(TamperViolation("ROOT_DETECTED", "Root access detected on device"))
                }
                
                if (isDeveloperModeEnabled(context)) {
                    violations.add(TamperViolation("DEVELOPER_MODE", "Developer options are enabled"))
                }
                
                if (isUsbDebuggingEnabled(context)) {
                    violations.add(TamperViolation("USB_DEBUG", "USB debugging is enabled"))
                }
                
                if (isCustomRomDetected()) {
                    violations.add(TamperViolation("CUSTOM_ROM", "Custom ROM detected", mapOf("fingerprint" to Build.FINGERPRINT)))
                }
                
                if (areSystemFilesModified()) {
                    violations.add(TamperViolation("SYSTEM_MODIFIED", "System files have been modified"))
                }
                
                if (isSecurityPatchOld()) {
                    violations.add(TamperViolation("SECURITY_PATCH_OLD", "Security patch level is outdated"))
                }
                
                // Removed: isUnknownSourcesEnabled check to prevent false hard-locks on boot
                
                if (isAdbEnabled(context)) {
                    violations.add(TamperViolation("ADB_ENABLED", "ADB is enabled"))
                }
                
                if (isMockLocationEnabled(context)) {
                    violations.add(TamperViolation("MOCK_LOCATION", "Mock location is enabled"))
                }
                
                if (violations.isNotEmpty()) {
                    Log.w(TAG, "âš ï¸ Found ${violations.size} security violation(s)")
                    for (violation in violations) {
                        reportTampering(deviceId, violation)
                    }
                } else {
                    Log.i(TAG, "âœ… No security violations detected")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "âŒ Error checking tampering: ${e.message}", e)
            }
        }
    }

    private suspend fun reportTampering(deviceId: String, violation: TamperViolation) {
        try {
            val request = TamperEventRequest.forDjango(
                tamperType = violation.type,
                description = violation.description,
                extraData = violation.extraData
            )
            
            val response = apiClient.postTamperEvent(deviceId, request)
            if (response.isSuccessful) {
                Log.e(TAG, "ðŸ”’ TAMPERING REPORTED: ${violation.type}")
                ServerBugAndLogReporter.postLog("tamper", "Critical", "Violation: ${violation.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error reporting tampering: ${e.message}")
        }
    }

    private fun isBootloaderUnlocked(): Boolean {
        val bootloader = getSystemProperty("ro.boot.bootloader") ?: getSystemProperty("ro.bootloader") ?: ""
        return bootloader.contains("unlocked", ignoreCase = true)
    }

    private fun isDeviceRooted(): Boolean {
        val suPaths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su")
        for (path in suPaths) { if (File(path).exists()) return true }
        return false
    }

    private fun isDeveloperModeEnabled(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    }

    private fun isUsbDebuggingEnabled(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
    }

    private fun isCustomRomDetected(): Boolean {
        return Build.FINGERPRINT.contains("test-keys") || Build.TAGS.contains("test-keys")
    }

    private fun areSystemFilesModified(): Boolean = File("/system/framework/XposedBridge.jar").exists()

    private fun isSecurityPatchOld(): Boolean {
        return try {
            val patch = Build.VERSION.SECURITY_PATCH
            if (patch.isNullOrEmpty()) return false
            val patchDate = java.text.SimpleDateFormat("yyyy-MM-dd").parse(patch)
            val threeMonthsAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            patchDate.time < threeMonthsAgo
        } catch (e: Exception) { false }
    }

    private fun isAdbEnabled(context: Context): Boolean = isUsbDebuggingEnabled(context)

    private fun isMockLocationEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION, 0) != 0
        } catch (e: Exception) { false }
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            process.inputStream.bufferedReader().use { it.readLine() }
        } catch (e: Exception) { null }
    }

    private data class TamperViolation(val type: String, val description: String, val extraData: Map<String, Any?>? = null)
}




