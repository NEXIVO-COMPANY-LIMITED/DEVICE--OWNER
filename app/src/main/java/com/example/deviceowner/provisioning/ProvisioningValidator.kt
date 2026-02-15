package com.example.deviceowner.provisioning

import android.content.Context
import android.util.Log
import com.example.deviceowner.device.DeviceOwnerCompatibilityChecker

/**
 * Provisioning Validator
 * 
 * Validates device compatibility before allowing Device Owner provisioning.
 * If device is incompatible, provisioning is blocked completely.
 */
class ProvisioningValidator(private val context: Context) {
    
    companion object {
        private const val TAG = "ProvisioningValidator"
        private const val PREF_NAME = "provisioning_validation"
        private const val KEY_COMPATIBILITY_CHECKED = "compatibility_checked"
        private const val KEY_IS_COMPATIBLE = "is_compatible"
        private const val KEY_COMPATIBILITY_ISSUES = "compatibility_issues"
    }
    
    private val compatibilityChecker = DeviceOwnerCompatibilityChecker(context)
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Validate device before QR code provisioning
     * Returns true if device is compatible, false if incompatible
     */
    fun validateBeforeProvisioning(): Boolean {
        Log.d(TAG, "🔍 Validating device for Device Owner provisioning...")
        
        val result = compatibilityChecker.checkCompatibility()
        
        // Save validation result
        prefs.edit().apply {
            putBoolean(KEY_COMPATIBILITY_CHECKED, true)
            putBoolean(KEY_IS_COMPATIBLE, result.isCompatible)
            putString(KEY_COMPATIBILITY_ISSUES, result.issues.joinToString("|"))
        }.apply()
        
        if (result.isCompatible) {
            Log.i(TAG, "✅ Device is compatible - provisioning allowed")
            return true
        } else {
            Log.e(TAG, "❌ Device is NOT compatible - provisioning BLOCKED")
            Log.e(TAG, "Issues: ${result.issues.joinToString(", ")}")
            return false
        }
    }
    
    /**
     * Check if device was already validated
     */
    fun wasValidated(): Boolean {
        return prefs.getBoolean(KEY_COMPATIBILITY_CHECKED, false)
    }
    
    /**
     * Check if device is compatible (from cached result)
     */
    fun isCompatible(): Boolean {
        return prefs.getBoolean(KEY_IS_COMPATIBLE, false)
    }
    
    /**
     * Get compatibility issues (from cached result)
     */
    fun getCompatibilityIssues(): List<String> {
        val issuesString = prefs.getString(KEY_COMPATIBILITY_ISSUES, "") ?: ""
        return if (issuesString.isEmpty()) {
            emptyList()
        } else {
            issuesString.split("|")
        }
    }
    
    /**
     * Get full compatibility report
     */
    fun getCompatibilityReport(): String {
        return compatibilityChecker.checkCompatibility().details
    }
    
    /**
     * Clear validation cache (for testing)
     */
    fun clearValidationCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Validation cache cleared")
    }
}
