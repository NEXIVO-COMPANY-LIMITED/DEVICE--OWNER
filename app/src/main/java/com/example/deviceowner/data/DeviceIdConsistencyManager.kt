package com.microspace.payo.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Device ID Consistency Manager
 * Periodically verifies and repairs device ID consistency across all storage locations.
 * Prevents heartbeat failures due to sync issues.
 *
 * Issues Fixed:
 * - Issue #1: Multiple storage locations create sync risk
 * - Issue #2: No backup/recovery for device ID
 * - Issue #7: Loan number dependency
 */
object DeviceIdConsistencyManager {
    private const val TAG = "DeviceIdConsistency"
    
    /**
     * Verify device ID consistency across all locations.
     * Returns true if all locations are consistent, false if repairs were needed.
     */
    suspend fun verifyConsistency(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Verifying device ID consistency...")
            return@withContext DeviceIdProvider.verifyAndRepairConsistency(context)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verifying consistency: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Repair device ID consistency if needed.
     * Syncs primary device ID to all backup locations.
     */
    suspend fun repairConsistency(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔧 Repairing device ID consistency...")
            val repaired = DeviceIdProvider.verifyAndRepairConsistency(context)
            if (repaired) {
                Log.i(TAG, "✅ Device ID consistency repaired")
            } else {
                Log.w(TAG, "⚠️ Device ID consistency repair not needed or failed")
            }
            return@withContext repaired
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error repairing consistency: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Validate device ID is available and consistent.
     * Useful for pre-flight checks before critical operations like heartbeat.
     */
    suspend fun validateDeviceIdAvailability(context: Context): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val deviceId = DeviceIdProvider.getDeviceId(context)
            
            if (deviceId == null) {
                return@withContext ValidationResult.Missing(
                    reason = "Device ID not found in any storage location",
                    suggestion = "Device must be registered first"
                )
            }
            
            // Verify consistency
            val isConsistent = DeviceIdProvider.verifyAndRepairConsistency(context)
            
            return@withContext ValidationResult.Valid(
                deviceId = deviceId,
                isConsistent = isConsistent
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error validating device ID: ${e.message}", e)
            return@withContext ValidationResult.Error(
                reason = e.message ?: "Unknown error",
                exception = e
            )
        }
    }
    
    /**
     * Validation result for device ID availability check.
     */
    sealed class ValidationResult {
        data class Valid(
            val deviceId: String,
            val isConsistent: Boolean
        ) : ValidationResult()
        
        data class Missing(
            val reason: String,
            val suggestion: String
        ) : ValidationResult()
        
        data class Error(
            val reason: String,
            val exception: Exception
        ) : ValidationResult()
    }
}
