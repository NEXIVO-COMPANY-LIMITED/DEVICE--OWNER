package com.deviceowner.compatibility

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Checks device compatibility with the application
 */
class DeviceCompatibilityChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceCompatibilityChecker"
        private const val MIN_API_LEVEL = 28
    }
    
    /**
     * Perform full compatibility check
     */
    fun checkCompatibility(): CompatibilityCheckResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Check API level
        if (Build.VERSION.SDK_INT < MIN_API_LEVEL) {
            errors.add("API level ${Build.VERSION.SDK_INT} is below minimum required level $MIN_API_LEVEL")
            recommendations.add("Please upgrade to Android ${getAndroidVersionName(MIN_API_LEVEL)} or higher")
        }
        
        // Check device in compatibility matrix
        val deviceInfo = getDeviceCompatibility()
        if (!deviceInfo.isCompatible) {
            errors.addAll(deviceInfo.issues)
            recommendations.add("This device model may not be fully supported. Please contact support.")
        }
        
        // Check required features
        val missingFeatures = checkRequiredFeatures()
        if (missingFeatures.isNotEmpty()) {
            warnings.add("Missing features: ${missingFeatures.joinToString(", ")}")
            recommendations.add("Some features may not be available on this device")
        }
        
        val compatible = errors.isEmpty()
        
        Log.i(TAG, "Compatibility check: compatible=$compatible, errors=${errors.size}, warnings=${warnings.size}")
        
        return CompatibilityCheckResult(
            compatible = compatible,
            warnings = warnings,
            errors = errors,
            recommendations = recommendations
        )
    }
    
    /**
     * Get device compatibility information
     */
    fun getDeviceCompatibility(): DeviceCompatibility {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val apiLevel = Build.VERSION.SDK_INT
        val osVersion = Build.VERSION.RELEASE.toIntOrNull() ?: 0
        
        val features = getDeviceFeatures()
        val issues = mutableListOf<String>()
        
        // Check if device is in compatibility matrix
        val isSupported = CompatibilityMatrix.isDeviceSupported(manufacturer, model, apiLevel)
        
        if (!isSupported) {
            issues.add("Device $manufacturer $model is not in the supported devices list")
        }
        
        // Check required features
        val supportedDeviceInfo = CompatibilityMatrix.getSupportedDeviceInfo(manufacturer, model)
        if (supportedDeviceInfo != null) {
            val missingFeatures = supportedDeviceInfo.requiredFeatures - features
            if (missingFeatures.isNotEmpty()) {
                issues.add("Missing required features: ${missingFeatures.joinToString(", ")}")
            }
        }
        
        return DeviceCompatibility(
            isCompatible = isSupported && issues.isEmpty(),
            manufacturer = manufacturer,
            model = model,
            osVersion = osVersion,
            apiLevel = apiLevel,
            features = features,
            issues = issues
        )
    }
    
    /**
     * Get device features
     */
    private fun getDeviceFeatures(): Set<String> {
        val features = mutableSetOf<String>()
        val pm = context.packageManager
        
        // Check common features
        val featuresToCheck = listOf(
            "android.hardware.nfc",
            "android.hardware.fingerprint",
            "android.hardware.camera",
            "android.hardware.camera.front",
            "android.hardware.bluetooth",
            "android.hardware.wifi",
            "android.hardware.location",
            "android.hardware.location.gps",
            "android.hardware.microphone",
            "android.hardware.telephony",
            "android.hardware.usb.host"
        )
        
        for (feature in featuresToCheck) {
            if (pm.hasSystemFeature(feature)) {
                features.add(feature)
            }
        }
        
        return features
    }
    
    /**
     * Check required features
     */
    private fun checkRequiredFeatures(): List<String> {
        val pm = context.packageManager
        val requiredFeatures = listOf(
            "android.hardware.nfc",
            "android.hardware.telephony"
        )
        
        val missing = mutableListOf<String>()
        for (feature in requiredFeatures) {
            if (!pm.hasSystemFeature(feature)) {
                missing.add(feature)
            }
        }
        
        return missing
    }
    
    /**
     * Get Android version name from API level
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
     * Check if device is compatible
     */
    fun isCompatible(): Boolean {
        return checkCompatibility().compatible
    }
    
    /**
     * Get compatibility status string
     */
    fun getCompatibilityStatus(): String {
        val result = checkCompatibility()
        return when {
            result.compatible -> "Device is fully compatible"
            result.errors.isNotEmpty() -> "Device is not compatible: ${result.errors.first()}"
            result.warnings.isNotEmpty() -> "Device has compatibility warnings: ${result.warnings.first()}"
            else -> "Unknown compatibility status"
        }
    }
}
