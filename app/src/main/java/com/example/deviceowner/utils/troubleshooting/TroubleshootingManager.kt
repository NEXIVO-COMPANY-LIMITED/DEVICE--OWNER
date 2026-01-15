package com.example.deviceowner.utils.troubleshooting

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comprehensive Troubleshooting Manager
 * Handles all common Device Owner setup and runtime issues
 * Provides diagnostic checks and automatic recovery
 */
class TroubleshootingManager(private val context: Context) {

    companion object {
        private const val TAG = "TroubleshootingManager"
        
        // Issue codes
        const val ISSUE_DEVICE_OWNER_NOT_SET = "DEVICE_OWNER_NOT_SET"
        const val ISSUE_BUILD_FAILED = "BUILD_FAILED"
        const val ISSUE_PERMISSIONS_NOT_GRANTED = "PERMISSIONS_NOT_GRANTED"
        const val ISSUE_SERVICES_NOT_STARTING = "SERVICES_NOT_STARTING"
        const val ISSUE_DEVICE_OWNER_ALREADY_SET = "DEVICE_OWNER_ALREADY_SET"
        const val ISSUE_APK_INSTALLATION_FAILED = "APK_INSTALLATION_FAILED"
        const val ISSUE_USB_DEBUGGING_DISABLED = "USB_DEBUGGING_DISABLED"
        const val ISSUE_DEVICE_ADMIN_NOT_ACTIVE = "DEVICE_ADMIN_NOT_ACTIVE"
        const val ISSUE_HEARTBEAT_FAILED = "HEARTBEAT_FAILED"
        const val ISSUE_NETWORK_CONNECTIVITY = "NETWORK_CONNECTIVITY"
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    /**
     * Run comprehensive diagnostic check
     * Returns list of issues found
     */
    suspend fun runDiagnostics(): List<DiagnosticResult> {
        return withContext(Dispatchers.Default) {
            val results = mutableListOf<DiagnosticResult>()

            Log.d(TAG, "Starting comprehensive diagnostics...")

            // Check 1: Device Owner Status
            results.add(checkDeviceOwnerStatus())

            // Check 2: Device Admin Status
            results.add(checkDeviceAdminStatus())

            // Check 3: Permissions
            results.add(checkPermissions())

            // Check 4: Services
            results.add(checkServices())

            // Check 5: Network Connectivity
            results.add(checkNetworkConnectivity())

            // Check 6: USB Debugging
            results.add(checkUSBDebugging())

            // Check 7: API Level
            results.add(checkAPILevel())

            // Check 8: Storage
            results.add(checkStorage())

            Log.d(TAG, "Diagnostics complete. Issues found: ${results.count { !it.isHealthy }}")

            results
        }
    }

    /**
     * Check Device Owner Status
     */
    private fun checkDeviceOwnerStatus(): DiagnosticResult {
        return try {
            val isDeviceOwner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                devicePolicyManager.isDeviceOwnerApp(context.packageName)
            } else {
                @Suppress("DEPRECATION")
                devicePolicyManager.isDeviceOwnerApp(context.packageName)
            }

            if (isDeviceOwner) {
                Log.d(TAG, "✓ Device Owner status: ACTIVE")
                DiagnosticResult(
                    checkName = "Device Owner Status",
                    isHealthy = true,
                    message = "App is properly set as Device Owner",
                    issueCode = null,
                    severity = DiagnosticSeverity.INFO
                )
            } else {
                Log.w(TAG, "✗ Device Owner status: NOT SET")
                DiagnosticResult(
                    checkName = "Device Owner Status",
                    isHealthy = false,
                    message = "App is not set as Device Owner. Run: adb shell dpm set-device-owner com.example.deviceowner/.receivers.AdminReceiver",
                    issueCode = ISSUE_DEVICE_OWNER_NOT_SET,
                    severity = DiagnosticSeverity.CRITICAL,
                    suggestedFix = "adb shell dpm set-device-owner com.example.deviceowner/.receivers.AdminReceiver"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Device Owner status", e)
            DiagnosticResult(
                checkName = "Device Owner Status",
                isHealthy = false,
                message = "Error checking Device Owner status: ${e.message}",
                issueCode = ISSUE_DEVICE_OWNER_NOT_SET,
                severity = DiagnosticSeverity.CRITICAL,
                exception = e
            )
        }
    }

    /**
     * Check Device Admin Status
     */
    private fun checkDeviceAdminStatus(): DiagnosticResult {
        return try {
            val isAdmin = devicePolicyManager.isAdminActive(adminComponent)

            if (isAdmin) {
                Log.d(TAG, "✓ Device Admin status: ACTIVE")
                DiagnosticResult(
                    checkName = "Device Admin Status",
                    isHealthy = true,
                    message = "AdminReceiver is properly registered as Device Admin",
                    issueCode = null,
                    severity = DiagnosticSeverity.INFO
                )
            } else {
                Log.w(TAG, "✗ Device Admin status: NOT ACTIVE")
                DiagnosticResult(
                    checkName = "Device Admin Status",
                    isHealthy = false,
                    message = "AdminReceiver is not registered as Device Admin",
                    issueCode = ISSUE_DEVICE_ADMIN_NOT_ACTIVE,
                    severity = DiagnosticSeverity.CRITICAL,
                    suggestedFix = "Ensure AdminReceiver is declared in AndroidManifest.xml with proper intent filters"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Device Admin status", e)
            DiagnosticResult(
                checkName = "Device Admin Status",
                isHealthy = false,
                message = "Error checking Device Admin status: ${e.message}",
                issueCode = ISSUE_DEVICE_ADMIN_NOT_ACTIVE,
                severity = DiagnosticSeverity.CRITICAL,
                exception = e
            )
        }
    }

    /**
     * Check if required permissions are granted
     */
    private fun checkPermissions(): DiagnosticResult {
        return try {
            val requiredPermissions = listOf(
                "android.permission.MANAGE_DEVICE_ADMINS",
                "android.permission.BIND_DEVICE_ADMIN",
                "android.permission.MANAGE_USERS",
                "android.permission.REBOOT",
                "android.permission.INTERNET"
            )

            val missingPermissions = mutableListOf<String>()

            for (permission in requiredPermissions) {
                if (context.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission)
                }
            }

            if (missingPermissions.isEmpty()) {
                Log.d(TAG, "✓ All required permissions are granted")
                DiagnosticResult(
                    checkName = "Permissions",
                    isHealthy = true,
                    message = "All required permissions are properly declared and granted",
                    issueCode = null,
                    severity = DiagnosticSeverity.INFO
                )
            } else {
                Log.w(TAG, "✗ Missing permissions: $missingPermissions")
                DiagnosticResult(
                    checkName = "Permissions",
                    isHealthy = false,
                    message = "Missing permissions: ${missingPermissions.joinToString(", ")}",
                    issueCode = ISSUE_PERMISSIONS_NOT_GRANTED,
                    severity = DiagnosticSeverity.HIGH,
                    suggestedFix = "Ensure all permissions are declared in AndroidManifest.xml"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            DiagnosticResult(
                checkName = "Permissions",
                isHealthy = false,
                message = "Error checking permissions: ${e.message}",
                issueCode = ISSUE_PERMISSIONS_NOT_GRANTED,
                severity = DiagnosticSeverity.HIGH,
                exception = e
            )
        }
    }

    /**
     * Check if services are properly declared
     */
    private fun checkServices(): DiagnosticResult {
        return try {
            val requiredServices = listOf(
                "com.example.deviceowner.services.UnifiedHeartbeatService",
                "com.example.deviceowner.services.TamperDetectionService",
                "com.example.deviceowner.services.DeviceOwnerRecoveryService"
            )

            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SERVICES
            )

            val declaredServices = packageInfo.services?.map { it.name } ?: emptyList()
            val missingServices = requiredServices.filter { it !in declaredServices }

            if (missingServices.isEmpty()) {
                Log.d(TAG, "✓ All required services are declared")
                DiagnosticResult(
                    checkName = "Services",
                    isHealthy = true,
                    message = "All required services are properly declared in AndroidManifest.xml",
                    issueCode = null,
                    severity = DiagnosticSeverity.INFO
                )
            } else {
                Log.w(TAG, "✗ Missing services: $missingServices")
                DiagnosticResult(
                    checkName = "Services",
                    isHealthy = false,
                    message = "Missing services: ${missingServices.joinToString(", ")}",
                    issueCode = ISSUE_SERVICES_NOT_STARTING,
                    severity = DiagnosticSeverity.HIGH,
                    suggestedFix = "Ensure all services are declared in AndroidManifest.xml"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking services", e)
            DiagnosticResult(
                checkName = "Services",
                isHealthy = false,
                message = "Error checking services: ${e.message}",
                issueCode = ISSUE_SERVICES_NOT_STARTING,
                severity = DiagnosticSeverity.HIGH,
                exception = e
            )
        }
    }

    /**
     * Check network connectivity
     */
    private fun checkNetworkConnectivity(): DiagnosticResult {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork

            if (network != null) {
                Log.d(TAG, "✓ Network connectivity: ACTIVE")
                DiagnosticResult(
                    checkName = "Network Connectivity",
                    isHealthy = true,
                    message = "Device has active network connection",
                    issueCode = null,
                    severity = DiagnosticSeverity.INFO
                )
            } else {
                Log.w(TAG, "✗ Network connectivity: OFFLINE")
                DiagnosticResult(
                    checkName = "Network Connectivity",
                    isHealthy = false,
                    message = "Device is offline. Heartbeat will use offline mode.",
                    issueCode = ISSUE_NETWORK_CONNECTIVITY,
                    severity = DiagnosticSeverity.MEDIUM,
                    suggestedFix = "Connect device to WiFi or mobile network"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connectivity", e)
            DiagnosticResult(
                checkName = "Network Connectivity",
                isHealthy = false,
                message = "Error checking network connectivity: ${e.message}",
                issueCode = ISSUE_NETWORK_CONNECTIVITY,
                severity = DiagnosticSeverity.MEDIUM,
                exception = e
            )
        }
    }

    /**
     * Check USB Debugging status
     */
    private fun checkUSBDebugging(): DiagnosticResult {
        return try {
            val isUSBDebuggingEnabled = android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Global.ADB_ENABLED,
                0
            ) == 1

            if (isUSBDebuggingEnabled) {
                Log.d(TAG, "⚠ USB Debugging: ENABLED (should be disabled in production)")
                DiagnosticResult(
                    checkName = "USB Debugging",
                    isHealthy = true,
                    message = "USB Debugging is enabled. Disable in production for security.",
                    issueCode = null,
                    severity = DiagnosticSeverity.WARNING
                )
            } else {
                Log.d(TAG, "✓ USB Debugging: DISABLED")
                DiagnosticResult(
                    checkName = "USB Debugging",
                    isHealthy = true,
                    message = "USB Debugging is properly disabled",
                    issueCode = null,
                    severity = DiagnosticSeverity.INFO
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking USB Debugging", e)
            DiagnosticResult(
                checkName = "USB Debugging",
                isHealthy = false,
                message = "Error checking USB Debugging: ${e.message}",
                issueCode = ISSUE_USB_DEBUGGING_DISABLED,
                severity = DiagnosticSeverity.INFO,
                exception = e
            )
        }
    }

    /**
     * Check API Level compatibility
     */
    private fun checkAPILevel(): DiagnosticResult {
        return try {
            val apiLevel = Build.VERSION.SDK_INT
            val minRequired = 24

            if (apiLevel >= minRequired) {
                Log.d(TAG, "✓ API Level: $apiLevel (minimum required: $minRequired)")
                DiagnosticResult(
                    checkName = "API Level",
                    isHealthy = true,
                    message = "Device API level ($apiLevel) meets minimum requirement ($minRequired)",
                    issueCode = null,
                    severity = DiagnosticSeverity.INFO
                )
            } else {
                Log.w(TAG, "✗ API Level: $apiLevel (minimum required: $minRequired)")
                DiagnosticResult(
                    checkName = "API Level",
                    isHealthy = false,
                    message = "Device API level ($apiLevel) is below minimum requirement ($minRequired)",
                    issueCode = null,
                    severity = DiagnosticSeverity.CRITICAL,
                    suggestedFix = "Device must be running Android 7.0 (API 24) or higher"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking API Level", e)
            DiagnosticResult(
                checkName = "API Level",
                isHealthy = false,
                message = "Error checking API Level: ${e.message}",
                issueCode = null,
                severity = DiagnosticSeverity.HIGH,
                exception = e
            )
        }
    }

    /**
     * Check storage availability
     */
    private fun checkStorage(): DiagnosticResult {
        return try {
            val stats = android.os.StatFs(context.filesDir.absolutePath)
            val availableBytes = stats.availableBytes
            val minRequired = 100 * 1024 * 1024 // 100 MB

            if (availableBytes >= minRequired) {
                Log.d(TAG, "✓ Storage: ${availableBytes / (1024 * 1024)} MB available")
                DiagnosticResult(
                    checkName = "Storage",
                    isHealthy = true,
                    message = "Sufficient storage available (${availableBytes / (1024 * 1024)} MB)",
                    issueCode = null,
                    severity = DiagnosticSeverity.INFO
                )
            } else {
                Log.w(TAG, "✗ Storage: Low (${availableBytes / (1024 * 1024)} MB available)")
                DiagnosticResult(
                    checkName = "Storage",
                    isHealthy = false,
                    message = "Low storage available (${availableBytes / (1024 * 1024)} MB). Minimum required: 100 MB",
                    issueCode = null,
                    severity = DiagnosticSeverity.MEDIUM,
                    suggestedFix = "Free up storage space on device"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage", e)
            DiagnosticResult(
                checkName = "Storage",
                isHealthy = false,
                message = "Error checking storage: ${e.message}",
                issueCode = null,
                severity = DiagnosticSeverity.MEDIUM,
                exception = e
            )
        }
    }

    /**
     * Attempt automatic recovery for common issues
     */
    suspend fun attemptAutoRecovery(issueCode: String): Boolean {
        return withContext(Dispatchers.Default) {
            Log.d(TAG, "Attempting auto-recovery for issue: $issueCode")

            when (issueCode) {
                ISSUE_DEVICE_OWNER_NOT_SET -> {
                    Log.w(TAG, "Cannot auto-recover: Device Owner must be set via ADB")
                    false
                }
                ISSUE_DEVICE_ADMIN_NOT_ACTIVE -> {
                    Log.w(TAG, "Cannot auto-recover: AdminReceiver must be properly declared in manifest")
                    false
                }
                ISSUE_PERMISSIONS_NOT_GRANTED -> {
                    Log.w(TAG, "Cannot auto-recover: Permissions must be declared in manifest")
                    false
                }
                ISSUE_SERVICES_NOT_STARTING -> {
                    Log.d(TAG, "Attempting to restart services...")
                    restartServices()
                    true
                }
                ISSUE_NETWORK_CONNECTIVITY -> {
                    Log.d(TAG, "Network offline - will retry when online")
                    true
                }
                else -> {
                    Log.w(TAG, "Unknown issue code: $issueCode")
                    false
                }
            }
        }
    }

    /**
     * Restart all services
     */
    private fun restartServices() {
        try {
            val services = listOf(
                "com.example.deviceowner.services.UnifiedHeartbeatService",
                "com.example.deviceowner.services.TamperDetectionService",
                "com.example.deviceowner.services.DeviceOwnerRecoveryService"
            )

            for (serviceName in services) {
                try {
                    val intent = Intent()
                    intent.setClassName(context.packageName, serviceName)
                    context.startService(intent)
                    Log.d(TAG, "✓ Started service: $serviceName")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Failed to start service: $serviceName", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting services", e)
        }
    }

    /**
     * Generate diagnostic report
     */
    suspend fun generateDiagnosticReport(): String {
        val results = runDiagnostics()
        val report = StringBuilder()

        report.append("═══════════════════════════════════════════════════════════\n")
        report.append("DEVICE OWNER DIAGNOSTIC REPORT\n")
        report.append("═══════════════════════════════════════════════════════════\n\n")

        report.append("Device Information:\n")
        report.append("  Manufacturer: ${Build.MANUFACTURER}\n")
        report.append("  Model: ${Build.MODEL}\n")
        report.append("  API Level: ${Build.VERSION.SDK_INT}\n")
        report.append("  Android Version: ${Build.VERSION.RELEASE}\n\n")

        report.append("Diagnostic Results:\n")
        report.append("───────────────────────────────────────────────────────────\n")

        for (result in results) {
            val status = if (result.isHealthy) "✓" else "✗"
            report.append("$status ${result.checkName}\n")
            report.append("   Status: ${result.severity.name}\n")
            report.append("   Message: ${result.message}\n")
            if (result.suggestedFix != null) {
                report.append("   Fix: ${result.suggestedFix}\n")
            }
            report.append("\n")
        }

        val criticalIssues = results.filter { it.severity == DiagnosticSeverity.CRITICAL }
        val highIssues = results.filter { it.severity == DiagnosticSeverity.HIGH }

        report.append("───────────────────────────────────────────────────────────\n")
        report.append("Summary:\n")
        report.append("  Total Checks: ${results.size}\n")
        report.append("  Healthy: ${results.count { it.isHealthy }}\n")
        report.append("  Issues: ${results.count { !it.isHealthy }}\n")
        report.append("  Critical: ${criticalIssues.size}\n")
        report.append("  High: ${highIssues.size}\n")
        report.append("═══════════════════════════════════════════════════════════\n")

        return report.toString()
    }
}

/**
 * Diagnostic Result Data Class
 */
data class DiagnosticResult(
    val checkName: String,
    val isHealthy: Boolean,
    val message: String,
    val issueCode: String?,
    val severity: DiagnosticSeverity,
    val suggestedFix: String? = null,
    val exception: Exception? = null
)

/**
 * Diagnostic Severity Levels
 */
enum class DiagnosticSeverity {
    INFO,
    WARNING,
    MEDIUM,
    HIGH,
    CRITICAL
}
