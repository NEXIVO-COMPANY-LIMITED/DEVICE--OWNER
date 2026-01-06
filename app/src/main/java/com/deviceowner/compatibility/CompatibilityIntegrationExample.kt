package com.deviceowner.compatibility

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Example integration of Device Compatibility Checking
 */
class CompatibilityIntegrationExample(private val context: Context) {
    
    companion object {
        private const val TAG = "CompatibilityIntegration"
    }
    
    private val compatibilityChecker = DeviceCompatibilityChecker(context)
    private val updateFilter = UpdateDistributionFilter(context)
    private val unsupportedHandler = UnsupportedDeviceHandler(context)
    private val offlineEnforcer = OfflineCompatibilityEnforcer(context)
    
    /**
     * Initialize compatibility checking on app startup
     */
    fun initializeCompatibilityChecking() {
        Log.i(TAG, "Initializing device compatibility checking")
        
        // Perform initial compatibility check
        performCompatibilityCheck()
        
        // Cache the result for offline use
        offlineEnforcer.cacheCompatibilityCheck()
    }
    
    /**
     * Perform compatibility check
     */
    private fun performCompatibilityCheck() {
        val result = compatibilityChecker.checkCompatibility()
        
        Log.i(TAG, "Compatibility check result: compatible=${result.compatible}")
        
        if (!result.compatible) {
            Log.w(TAG, "Device is not compatible: ${result.errors.joinToString(", ")}")
            handleIncompatibleDevice()
        } else if (result.warnings.isNotEmpty()) {
            Log.w(TAG, "Device has compatibility warnings: ${result.warnings.joinToString(", ")}")
        } else {
            Log.i(TAG, "Device is fully compatible")
        }
    }
    
    /**
     * Handle incompatible device
     */
    private fun handleIncompatibleDevice() {
        val action = unsupportedHandler.handleUnsupportedDevice()
        
        Log.e(TAG, "Unsupported device action: ${action.action}")
        Log.e(TAG, "Message: ${action.message}")
        Log.e(TAG, "Severity: ${action.severity}")
        
        // Display warning to user
        val warning = unsupportedHandler.displayWarning()
        Log.w(TAG, warning)
        
        // Get restricted features
        val restrictedFeatures = unsupportedHandler.getRestrictedFeatures()
        Log.w(TAG, "Restricted features: ${restrictedFeatures.joinToString(", ")}")
    }
    
    /**
     * Check if device is eligible for update
     */
    fun checkUpdateEligibility(): Boolean {
        val eligible = updateFilter.isEligibleForUpdate()
        
        Log.i(TAG, "Update eligibility: $eligible")
        
        if (!eligible) {
            val status = updateFilter.getUpdateEligibilityStatus()
            Log.w(TAG, "Update not eligible: ${status.errors.joinToString(", ")}")
        }
        
        return eligible
    }
    
    /**
     * Verify compatibility before update
     */
    fun verifyBeforeUpdate(updateVersion: String): Boolean {
        Log.i(TAG, "Verifying compatibility for update: $updateVersion")
        
        val result = updateFilter.verifyCompatibilityBeforeUpdate()
        
        Log.i(TAG, "Update verification: canUpdate=${result.canUpdate}")
        
        if (!result.canUpdate) {
            Log.e(TAG, "Cannot update: ${result.errors.joinToString(", ")}")
        }
        
        // Log the check
        updateFilter.logCompatibilityCheck(updateVersion)
        
        return result.canUpdate
    }
    
    /**
     * Get device compatibility info
     */
    fun getDeviceInfo(): DeviceCompatibility {
        return compatibilityChecker.getDeviceCompatibility()
    }
    
    /**
     * Get compatibility status
     */
    fun getCompatibilityStatus(): String {
        return compatibilityChecker.getCompatibilityStatus()
    }
    
    /**
     * Check if feature is allowed
     */
    fun isFeatureAllowed(feature: String): Boolean {
        return unsupportedHandler.isFeatureAllowed(feature)
    }
    
    /**
     * Get upgrade recommendation
     */
    fun getUpgradeRecommendation(): String {
        return unsupportedHandler.getUpgradeRecommendation()
    }
    
    /**
     * Enforce offline restrictions
     */
    fun enforceOfflineRestrictions(): OfflineEnforcementResult {
        return offlineEnforcer.enforceRestrictionsOffline()
    }
    
    /**
     * Verify on reconnection
     */
    fun verifyOnReconnection(): ReconnectionVerificationResult {
        return offlineEnforcer.verifyOnReconnection()
    }
}

/**
 * Example ViewModel for compatibility checking
 */
class CompatibilityViewModel(private val context: Context) : ViewModel() {
    
    companion object {
        private const val TAG = "CompatibilityViewModel"
    }
    
    private val compatibilityChecker = DeviceCompatibilityChecker(context)
    private val updateFilter = UpdateDistributionFilter(context)
    private val offlineEnforcer = OfflineCompatibilityEnforcer(context)
    
    /**
     * Check device compatibility
     */
    fun checkDeviceCompatibility() {
        viewModelScope.launch {
            try {
                val result = compatibilityChecker.checkCompatibility()
                val deviceInfo = compatibilityChecker.getDeviceCompatibility()
                
                Log.i(TAG, """
                    Device Compatibility:
                    - Device: ${deviceInfo.manufacturer} ${deviceInfo.model}
                    - API Level: ${deviceInfo.apiLevel}
                    - Compatible: ${result.compatible}
                    - Errors: ${result.errors.size}
                    - Warnings: ${result.warnings.size}
                """.trimIndent())
                
                // Cache for offline use
                offlineEnforcer.cacheCompatibilityCheck()
                
            } catch (e: Exception) {
                Log.e(TAG, "Compatibility check failed", e)
            }
        }
    }
    
    /**
     * Get compatibility report
     */
    fun getCompatibilityReport(): String {
        val result = compatibilityChecker.checkCompatibility()
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        return buildString {
            appendLine("Device Compatibility Report")
            appendLine("=".repeat(40))
            appendLine()
            appendLine("Device Information:")
            appendLine("- Manufacturer: ${deviceInfo.manufacturer}")
            appendLine("- Model: ${deviceInfo.model}")
            appendLine("- API Level: ${deviceInfo.apiLevel}")
            appendLine("- OS Version: ${deviceInfo.osVersion}")
            appendLine()
            appendLine("Compatibility Status:")
            appendLine("- Compatible: ${result.compatible}")
            appendLine()
            
            if (result.errors.isNotEmpty()) {
                appendLine("Errors:")
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
     * Check update eligibility
     */
    fun checkUpdateEligibility() {
        viewModelScope.launch {
            try {
                val eligible = updateFilter.isEligibleForUpdate()
                val status = updateFilter.getUpdateEligibilityStatus()
                
                Log.i(TAG, """
                    Update Eligibility:
                    - Eligible: $eligible
                    - Device: ${status.deviceManufacturer} ${status.deviceModel}
                    - API Level: ${status.apiLevel}
                    - Errors: ${status.errors.size}
                """.trimIndent())
                
            } catch (e: Exception) {
                Log.e(TAG, "Update eligibility check failed", e)
            }
        }
    }
    
    /**
     * Get offline enforcement status
     */
    fun getOfflineStatus(): String {
        return offlineEnforcer.getOfflineEnforcementStatus()
    }
}

/**
 * Example usage in Application class
 */
class CompatibilityAwareApplication : android.app.Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize compatibility checking
        val example = CompatibilityIntegrationExample(this)
        example.initializeCompatibilityChecking()
        
        Log.i("CompatibilityApp", "Device compatibility checking initialized")
    }
}
