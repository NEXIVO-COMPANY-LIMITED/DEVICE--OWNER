package com.deviceowner.compatibility

import android.content.Context
import android.util.Log

/**
 * Enforces compatibility restrictions offline
 */
class OfflineCompatibilityEnforcer(private val context: Context) {
    
    companion object {
        private const val TAG = "OfflineCompatibilityEnforcer"
    }
    
    private val stateManager = CompatibilityStateManager(context)
    private val compatibilityChecker = DeviceCompatibilityChecker(context)
    private val unsupportedHandler = UnsupportedDeviceHandler(context)
    
    /**
     * Cache compatibility checks locally
     */
    fun cacheCompatibilityCheck() {
        val result = compatibilityChecker.checkCompatibility()
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        stateManager.storeCompatibilityCheck(result, deviceInfo)
        
        Log.i(TAG, "Compatibility check cached: compatible=${result.compatible}")
    }
    
    /**
     * Enforce restrictions offline
     */
    fun enforceRestrictionsOffline(): OfflineEnforcementResult {
        // Try to get cached compatibility check
        val cachedResult = stateManager.getCachedCompatibilityCheck()
        val cachedDeviceInfo = stateManager.getCachedDeviceInfo()
        
        if (cachedResult == null || cachedDeviceInfo == null) {
            Log.w(TAG, "No cached compatibility data available")
            return OfflineEnforcementResult(
                enforced = false,
                reason = "No cached compatibility data",
                restrictedFeatures = emptyList()
            )
        }
        
        // Check if cache is still valid
        if (!stateManager.isCacheValid()) {
            Log.w(TAG, "Cached compatibility data is stale")
            return OfflineEnforcementResult(
                enforced = false,
                reason = "Cached data is stale",
                restrictedFeatures = emptyList()
            )
        }
        
        // Enforce restrictions based on cached data
        val restrictedFeatures = if (!cachedResult.compatible) {
            unsupportedHandler.getRestrictedFeatures()
        } else {
            emptyList()
        }
        
        Log.i(TAG, "Offline enforcement: restricted_features=${restrictedFeatures.size}")
        
        return OfflineEnforcementResult(
            enforced = true,
            reason = "Using cached compatibility data",
            restrictedFeatures = restrictedFeatures,
            cachedAt = stateManager.getLastCheckTime()
        )
    }
    
    /**
     * Verify on reconnection
     */
    fun verifyOnReconnection(): ReconnectionVerificationResult {
        Log.i(TAG, "Verifying compatibility on reconnection")
        
        // Get current compatibility status
        val currentResult = compatibilityChecker.checkCompatibility()
        val currentDeviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        // Get cached status
        val cachedResult = stateManager.getCachedCompatibilityCheck()
        val cachedDeviceInfo = stateManager.getCachedDeviceInfo()
        
        // Compare results
        val statusChanged = cachedResult?.compatible != currentResult.compatible
        val deviceChanged = cachedDeviceInfo?.let {
            it.manufacturer != currentDeviceInfo.manufacturer ||
            it.model != currentDeviceInfo.model ||
            it.apiLevel != currentDeviceInfo.apiLevel
        } ?: true
        
        // Update cache with current status
        stateManager.storeCompatibilityCheck(currentResult, currentDeviceInfo)
        stateManager.recordSyncTime()
        
        Log.i(TAG, "Reconnection verification: statusChanged=$statusChanged, deviceChanged=$deviceChanged")
        
        return ReconnectionVerificationResult(
            statusChanged = statusChanged,
            deviceChanged = deviceChanged,
            currentCompatible = currentResult.compatible,
            previousCompatible = cachedResult?.compatible ?: false,
            errors = currentResult.errors,
            warnings = currentResult.warnings
        )
    }
    
    /**
     * Sync compatibility status
     */
    fun syncCompatibilityStatus(): SyncResult {
        Log.i(TAG, "Syncing compatibility status")
        
        // Get current status
        val result = compatibilityChecker.checkCompatibility()
        val deviceInfo = compatibilityChecker.getDeviceCompatibility()
        
        // Store in cache
        stateManager.storeCompatibilityCheck(result, deviceInfo)
        stateManager.recordSyncTime()
        
        return SyncResult(
            synced = true,
            compatible = result.compatible,
            timestamp = System.currentTimeMillis(),
            lastSyncTime = stateManager.getLastSyncTime()
        )
    }
    
    /**
     * Check if feature is allowed offline
     */
    fun isFeatureAllowedOffline(feature: String): Boolean {
        val enforcement = enforceRestrictionsOffline()
        
        if (!enforcement.enforced) {
            // If we can't enforce offline, allow the feature
            return true
        }
        
        return !enforcement.restrictedFeatures.contains(feature)
    }
    
    /**
     * Get offline enforcement status
     */
    fun getOfflineEnforcementStatus(): String {
        val enforcement = enforceRestrictionsOffline()
        val lastCheck = stateManager.getLastCheckTime()
        val lastSync = stateManager.getLastSyncTime()
        
        return buildString {
            appendLine("Offline Enforcement Status:")
            appendLine("- Enforced: ${enforcement.enforced}")
            appendLine("- Reason: ${enforcement.reason}")
            appendLine("- Restricted Features: ${enforcement.restrictedFeatures.size}")
            appendLine("- Last Check: ${formatTime(lastCheck)}")
            appendLine("- Last Sync: ${formatTime(lastSync)}")
            appendLine("- Cache Valid: ${stateManager.isCacheValid()}")
        }
    }
    
    /**
     * Format timestamp
     */
    private fun formatTime(timestamp: Long): String {
        return if (timestamp == 0L) "Never" else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(timestamp)
    }
}

/**
 * Result of offline enforcement
 */
data class OfflineEnforcementResult(
    val enforced: Boolean,
    val reason: String,
    val restrictedFeatures: List<String> = emptyList(),
    val cachedAt: Long = 0
)

/**
 * Result of reconnection verification
 */
data class ReconnectionVerificationResult(
    val statusChanged: Boolean,
    val deviceChanged: Boolean,
    val currentCompatible: Boolean,
    val previousCompatible: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Result of sync operation
 */
data class SyncResult(
    val synced: Boolean,
    val compatible: Boolean,
    val timestamp: Long,
    val lastSyncTime: Long
)
