package com.microspace.payo.device

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Checks device compatibility for Device Owner provisioning.
 * Validates:
 * - Android version (13+)
 * - Device brand and model
 * - Stock Android (no custom ROM)
 */
class DeviceOwnerCompatibilityChecker(private val context: Context) {

    data class DeviceInfo(
        val brand: String,
        val model: String,
        val androidVersion: Int,
        val isStockAndroid: Boolean,
        val manufacturer: String
    )

    data class CompatibilityResult(
        val isCompatible: Boolean,
        val deviceInfo: DeviceInfo,
        val issues: List<String>
    ) {
        val details: String
            get() {
                val status = if (isCompatible) "✓ COMPATIBLE" else "❌ NOT COMPATIBLE"
                val deviceDetails = """
                    Device: ${deviceInfo.brand} ${deviceInfo.model}
                    Android Version: ${deviceInfo.androidVersion}
                    Manufacturer: ${deviceInfo.manufacturer}
                    Stock Android: ${deviceInfo.isStockAndroid}
                """.trimIndent()
                
                val issuesText = if (issues.isNotEmpty()) {
                    "Issues:\n" + issues.joinToString("\n") { "  • $it" }
                } else {
                    "No issues found"
                }
                
                return "$status\n\n$deviceDetails\n\n$issuesText"
            }
    }

    fun checkCompatibility(): CompatibilityResult {
        val issues = mutableListOf<String>()
        val deviceInfo = getDeviceInfo()

        Log.d(TAG, "=== Device Compatibility Check (DISABLED) ===")
        Log.d(TAG, "Brand: ${deviceInfo.brand}")
        Log.d(TAG, "Model: ${deviceInfo.model}")
        Log.d(TAG, "Android Version: ${deviceInfo.androidVersion}")
        Log.d(TAG, "Manufacturer: ${deviceInfo.manufacturer}")
        Log.d(TAG, "Stock Android: ${deviceInfo.isStockAndroid}")

        // ===== COMPATIBILITY CHECKS DISABLED PER USER REQUEST =====
        /*
        // Check 1: ONLY Google Pixel phones allowed
        if (!isGooglePixel(deviceInfo.brand, deviceInfo.model, deviceInfo.manufacturer)) {
            issues.add("Only Google Pixel phones are supported. Your device: ${deviceInfo.brand} ${deviceInfo.model}")
            Log.w(TAG, "❌ Device brand check failed: Not a Google Pixel")
        } else {
            Log.d(TAG, "✓ Device is Google Pixel")
        }

        // Check 2: Android version must be 13 or higher
        if (deviceInfo.androidVersion < 13) {
            issues.add("Android version must be 13 or higher (current: ${deviceInfo.androidVersion})")
            Log.w(TAG, "❌ Android version check failed: ${deviceInfo.androidVersion}")
        } else {
            Log.d(TAG, "✓ Android version check passed: ${deviceInfo.androidVersion}")
        }

        // Check 3: Must be stock Android (no custom ROM)
        if (!deviceInfo.isStockAndroid) {
            issues.add("Device must run stock Android (no custom ROM detected)")
            Log.w(TAG, "❌ Stock Android check failed")
        } else {
            Log.d(TAG, "✓ Stock Android check passed")
        }
        */
        // ===== END DISABLED CHECKS =====
        
        // Force compatibility to true regardless of findings
        val isCompatible = true 
        Log.d(TAG, "=== Compatibility Result: FORCED COMPATIBLE ✓ ===")

        return CompatibilityResult(
            isCompatible = isCompatible,
            deviceInfo = deviceInfo,
            issues = emptyList() // Clear any issues
        )
    }

    private fun isGooglePixel(brand: String, model: String, manufacturer: String): Boolean {
        // Must be Google manufacturer
        if (!manufacturer.equals("Google", ignoreCase = true)) {
            return false
        }

        // Must be Pixel model
        val pixelModels = listOf(
            "Pixel 6",
            "Pixel 6a",
            "Pixel 6 Pro",
            "Pixel 7",
            "Pixel 7a",
            "Pixel 7 Pro",
            "Pixel 8",
            "Pixel 8a",
            "Pixel 8 Pro",
            "Pixel 9",
            "Pixel 9a",
            "Pixel 9 Pro",
            "Pixel 9 Pro XL",
            "Pixel 9 Pro Fold"
        )

        return pixelModels.any { 
            model.contains(it, ignoreCase = true) 
        }
    }

    private fun getDeviceInfo(): DeviceInfo {
        val brand = Build.BRAND ?: "Unknown"
        val model = Build.MODEL ?: "Unknown"
        val androidVersion = Build.VERSION.SDK_INT
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val isStockAndroid = isStockAndroid()

        return DeviceInfo(
            brand = brand,
            model = model,
            androidVersion = androidVersion,
            isStockAndroid = isStockAndroid,
            manufacturer = manufacturer
        )
    }

    private fun isStockAndroid(): Boolean {
        // Check for custom ROM indicators
        val buildTags = Build.TAGS ?: ""
        val buildFingerprint = Build.FINGERPRINT ?: ""
        val buildHost = Build.HOST ?: ""

        // Common custom ROM indicators
        val customRomIndicators = listOf(
            "LineageOS",
            "AOSP",
            "Paranoid",
            "Resurrection",
            "Slim",
            "OmniROM",
            "CyanogenMod",
            "Mokee",
            "Flyme",
            "MIUI",
            "ColorOS",
            "FuntouchOS",
            "OneUI",
            "HyperOS"
        )

        // Check if any custom ROM indicator is found
        for (indicator in customRomIndicators) {
            if (buildFingerprint.contains(indicator, ignoreCase = true) ||
                buildTags.contains(indicator, ignoreCase = true) ||
                buildHost.contains(indicator, ignoreCase = true)
            ) {
                // Some of these are actually stock or semi-stock (OneUI, ColorOS, etc.)
                // Only flag truly custom ROMs
                if (indicator in listOf("LineageOS", "CyanogenMod", "Resurrection", "Slim", "OmniROM", "Mokee")) {
                    return false
                }
            }
        }

        return true
    }

    private fun isSupportedManufacturer(manufacturer: String): Boolean {
        val supportedManufacturers = listOf(
            "Google",
            "Samsung",
            "OnePlus",
            "Xiaomi",
            "Motorola",
            "Nokia",
            "Sony",
            "LG",
            "HTC",
            "Huawei",
            "Oppo",
            "Vivo",
            "Realme",
            "Nothing",
            "Asus",
            "Lenovo"
        )

        return supportedManufacturers.any { 
            manufacturer.contains(it, ignoreCase = true) 
        }
    }

    private fun isValidDeviceBrand(brand: String): Boolean {
        val validBrands = listOf(
            "google",
            "samsung",
            "oneplus",
            "xiaomi",
            "motorola",
            "nokia",
            "sony",
            "lg",
            "htc",
            "huawei",
            "oppo",
            "vivo",
            "realme",
            "nothing",
            "asus",
            "lenovo"
        )

        return validBrands.any { 
            brand.lowercase().contains(it) 
        }
    }

    companion object {
        private const val TAG = "CompatibilityChecker"
    }
}
