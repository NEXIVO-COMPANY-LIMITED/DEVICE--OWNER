package com.deviceowner.compatibility

import android.content.Context
import android.util.Log

/**
 * Filters updates based on device compatibility
 */
class UpdateDistributionFilter(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateDistributionFilter"
    }
    
    private val compatibilityChecker = DeviceCompatibilityChecker(context)
    private val stateManager = CompatibilityStateManager(context)
    
    /**
     * Check if device is eligible for update
     */
    fun isEligibleForUpdate(): Boolean {
        val result = compatibilityChecker.checkCompatibility()
        
        Log.i(TAG, "Update eligibility check: compatible=${result.compatible}")
        
        if (!result.compatible) {
            Log.w(TAG, "Device not eligible for update: ${result.errors.joinToString(", ")}")
            return false
        }
        
        return true
    }
    
    /**
     * Verify compatibility before update
     */
    fun verifyCompatibilityBeforeUpdate(): VerifyUpdateResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check compatibility
        val compatResult = compatibilityChecker.checkCompatibility()
        if (!compatResult.compatible) {
            errors.addAll(compatResult.errors)
        }
        warnings.addAll(compatResult.warnings)
        
        // Check device info
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        // Log verification
        Log.i(TAG, "Update verification: errors=${errors.size}, warnings=${warnings.size}")
        
        return VerifyUpdateResult(
            canUpdate = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            deviceInfo = deviceInfo
        )
    }
    
    /**
     * Filter devices for update distribution
     */
    fun filterDevicesForUpdate(devices: List<DeviceInfo>): List<DeviceInfo> {
        return devices.filter { device ->
            val isSupported = CompatibilityMatrix.isDeviceSupported(
                device.manufacturer,
                device.model,
                device.apiLevel
            )
            
            if (!isSupported) {
                Log.w(TAG, "Filtering out unsupported device: ${device.manufacturer} ${device.model}")
            }
            
            isSupported
        }
    }
    
    /**
     * Get update eligibility status
     */
    fun getUpdateEligibilityStatus(): UpdateEligibilityStatus {
        val result = compatibilityChecker.checkCompatibility()
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        return UpdateEligibilityStatus(
            eligible = result.compatible,
            deviceManufacturer = deviceInfo.manufacturer,
            deviceModel = deviceInfo.model,
            apiLevel = deviceInfo.apiLevel,
            errors = result.errors,
            warnings = result.warnings,
            recommendations = result.recommendations
        )
    }
    
    /**
     * Log compatibility check for update
     */
    fun logCompatibilityCheck(updateVersion: String) {
        val result = compatibilityChecker.checkCompatibility()
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        Log.i(TAG, """
            Update Compatibility Check:
            - Update Version: $updateVersion
            - Device: ${deviceInfo.manufacturer} ${deviceInfo.model}
            - API Level: ${deviceInfo.apiLevel}
            - Compatible: ${result.compatible}
            - Errors: ${result.errors.size}
            - Warnings: ${result.warnings.size}
        """.trimIndent())
        
        // Store in state manager for offline reference
        stateManager.storeCompatibilityCheck(result, deviceInfo)
    }
}

/**
 * Represents device information for filtering
 */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val features: Set<String> = emptySet()
)

/**
 * Result of update verification
 */
data class VerifyUpdateResult(
    val canUpdate: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val deviceInfo: DeviceCompatibility
)

/**
 * Update eligibility status
 */
data class UpdateEligibilityStatus(
    val eligible: Boolean,
    val deviceManufacturer: String,
    val deviceModel: String,
    val apiLevel: Int,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)
