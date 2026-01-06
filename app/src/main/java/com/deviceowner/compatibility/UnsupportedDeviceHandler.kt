package com.deviceowner.compatibility

import android.content.Context
import android.util.Log

/**
 * Handles unsupported device scenarios
 */
class UnsupportedDeviceHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "UnsupportedDeviceHandler"
    }
    
    private val compatibilityChecker = DeviceCompatibilityChecker(context)
    private val stateManager = CompatibilityStateManager(context)
    
    /**
     * Handle unsupported device
     */
    fun handleUnsupportedDevice(): UnsupportedDeviceAction {
        val result = compatibilityChecker.checkCompatibility()
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        Log.w(TAG, "Handling unsupported device: ${deviceInfo.manufacturer} ${deviceInfo.model}")
        
        val action = determineAction(result, deviceInfo)
        
        // Log the action
        logUnsupportedDeviceAttempt(deviceInfo, action)
        
        return action
    }
    
    /**
     * Determine action for unsupported device
     */
    private fun determineAction(
        result: CompatibilityCheckResult,
        deviceInfo: DeviceCompatibility
    ): UnsupportedDeviceAction {
        return when {
            result.errors.any { it.contains("API level") } -> {
                UnsupportedDeviceAction(
                    action = "UPGRADE_REQUIRED",
                    message = "Your device requires Android ${getMinAndroidVersion()} or higher",
                    severity = "CRITICAL",
                    allowedFeatures = emptyList(),
                    recommendations = listOf(
                        "Update your device to a newer Android version",
                        "Contact your device manufacturer for updates"
                    )
                )
            }
            result.errors.any { it.contains("not in the supported") } -> {
                UnsupportedDeviceAction(
                    action = "DEVICE_NOT_SUPPORTED",
                    message = "Your device model is not officially supported",
                    severity = "HIGH",
                    allowedFeatures = listOf("VIEW_STATUS", "VIEW_LOGS"),
                    recommendations = listOf(
                        "Contact support for assistance",
                        "Consider upgrading to a supported device"
                    )
                )
            }
            result.warnings.isNotEmpty() -> {
                UnsupportedDeviceAction(
                    action = "LIMITED_SUPPORT",
                    message = "Your device has limited support",
                    severity = "MEDIUM",
                    allowedFeatures = listOf("VIEW_STATUS", "VIEW_LOGS", "BASIC_OPERATIONS"),
                    recommendations = result.recommendations
                )
            }
            else -> {
                UnsupportedDeviceAction(
                    action = "UNKNOWN",
                    message = "Unknown compatibility status",
                    severity = "LOW",
                    allowedFeatures = emptyList(),
                    recommendations = listOf("Contact support")
                )
            }
        }
    }
    
    /**
     * Display warning to user
     */
    fun displayWarning(): String {
        val result = compatibilityChecker.checkCompatibility()
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        return buildString {
            appendLine("⚠️ Device Compatibility Warning")
            appendLine()
            appendLine("Device: ${deviceInfo.manufacturer} ${deviceInfo.model}")
            appendLine("Android Version: ${getAndroidVersionName(deviceInfo.apiLevel)}")
            appendLine()
            
            if (result.errors.isNotEmpty()) {
                appendLine("Issues:")
                result.errors.forEach { appendLine("  • $it") }
                appendLine()
            }
            
            if (result.warnings.isNotEmpty()) {
                appendLine("Warnings:")
                result.warnings.forEach { appendLine("  • $it") }
                appendLine()
            }
            
            if (result.recommendations.isNotEmpty()) {
                appendLine("Recommendations:")
                result.recommendations.forEach { appendLine("  • $it") }
            }
        }
    }
    
    /**
     * Restrict features for unsupported device
     */
    fun getRestrictedFeatures(): List<String> {
        val result = compatibilityChecker.checkCompatibility()
        
        return when {
            !result.compatible && result.errors.any { it.contains("API level") } -> {
                listOf(
                    "DEVICE_LOCK",
                    "PASSWORD_MANAGEMENT",
                    "REMOTE_COMMANDS",
                    "UPDATE_MANAGEMENT",
                    "SECURITY_POLICIES"
                )
            }
            !result.compatible -> {
                listOf(
                    "DEVICE_LOCK",
                    "REMOTE_COMMANDS",
                    "UPDATE_MANAGEMENT"
                )
            }
            result.warnings.isNotEmpty() -> {
                listOf("REMOTE_COMMANDS")
            }
            else -> emptyList()
        }
    }
    
    /**
     * Check if feature is allowed
     */
    fun isFeatureAllowed(feature: String): Boolean {
        val restrictedFeatures = getRestrictedFeatures()
        return !restrictedFeatures.contains(feature)
    }
    
    /**
     * Log unsupported device attempt
     */
    private fun logUnsupportedDeviceAttempt(
        deviceInfo: DeviceCompatibility,
        action: UnsupportedDeviceAction
    ) {
        Log.w(TAG, """
            Unsupported Device Attempt:
            - Device: ${deviceInfo.manufacturer} ${deviceInfo.model}
            - API Level: ${deviceInfo.apiLevel}
            - Action: ${action.action}
            - Severity: ${action.severity}
            - Timestamp: ${System.currentTimeMillis()}
        """.trimIndent())
    }
    
    /**
     * Recommend device upgrade
     */
    fun getUpgradeRecommendation(): String {
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        return buildString {
            appendLine("Device Upgrade Recommendation")
            appendLine()
            appendLine("Current Device: ${deviceInfo.manufacturer} ${deviceInfo.model}")
            appendLine("Current Android: ${getAndroidVersionName(deviceInfo.apiLevel)}")
            appendLine()
            appendLine("Recommended Devices:")
            
            CompatibilityMatrix.getSupportedDevices()
                .distinctBy { it.manufacturer }
                .take(5)
                .forEach { device ->
                    appendLine("  • ${device.manufacturer} - ${device.model}")
                }
        }
    }
    
    /**
     * Get Android version name
     */
    private fun getAndroidVersionName(apiLevel: Int): String {
        return when (apiLevel) {
            28 -> "Android 9 (Pie)"
            29 -> "Android 10 (Q)"
            30 -> "Android 11 (R)"
            31 -> "Android 12 (S)"
            32 -> "Android 12 (S)"
            33 -> "Android 13 (T)"
            34 -> "Android 14 (U)"
            else -> "Android $apiLevel"
        }
    }
    
    /**
     * Get minimum Android version
     */
    private fun getMinAndroidVersion(): String {
        return getAndroidVersionName(28)
    }
}

/**
 * Represents action to take for unsupported device
 */
data class UnsupportedDeviceAction(
    val action: String,
    val message: String,
    val severity: String,
    val allowedFeatures: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)
