package com.example.deviceowner.managers

import android.util.Log
import com.example.deviceowner.data.models.HeartbeatComparisonResult
import com.example.deviceowner.data.models.HeartbeatMismatch
import com.example.deviceowner.data.models.UnifiedHeartbeatData

/**
 * Unified Heartbeat Comparison Engine
 * Used for BOTH online and offline comparison
 * Ensures identical detection logic regardless of mode
 * 
 * Comparison modes:
 * 1. ONLINE: Compare current data with server-verified data
 * 2. OFFLINE: Compare current data with locally-stored baseline
 * 
 * Both modes use identical comparison logic
 */
class UnifiedHeartbeatComparison {
    
    companion object {
        private const val TAG = "UnifiedHeartbeatComparison"
    }
    
    /**
     * Compare current heartbeat with verified/baseline data
     * Works for both online and offline modes
     */
    fun compareHeartbeats(
        currentData: UnifiedHeartbeatData,
        referenceData: UnifiedHeartbeatData,
        mode: String = "OFFLINE" // ONLINE or OFFLINE
    ): HeartbeatComparisonResult {
        
        Log.d(TAG, "Comparing heartbeats in $mode mode")
        Log.d(TAG, "Current: ${currentData.deviceId} @ ${currentData.timestamp}")
        Log.d(TAG, "Reference: ${referenceData.deviceId} @ ${referenceData.timestamp}")
        
        val mismatches = mutableListOf<HeartbeatMismatch>()
        var maxSeverity = "NONE"
        
        // ==================== IMMUTABLE FIELDS ====================
        // These fields CANNOT change from registration
        // If they change, device has been swapped or cloned
        
        val immutableMismatches = compareImmutableFields(currentData, referenceData)
        mismatches.addAll(immutableMismatches)
        if (immutableMismatches.isNotEmpty()) {
            maxSeverity = "CRITICAL"
        }
        
        // ==================== CRITICAL TAMPER FIELDS ====================
        // These fields indicate tampering/rooting
        
        val tamperMismatches = compareCriticalTamperFields(currentData, referenceData)
        mismatches.addAll(tamperMismatches)
        if (tamperMismatches.isNotEmpty() && maxSeverity != "CRITICAL") {
            maxSeverity = "CRITICAL"
        }
        
        // ==================== SECURITY FIELDS ====================
        // These fields indicate security compromise
        
        val securityMismatches = compareSecurityFields(currentData, referenceData)
        mismatches.addAll(securityMismatches)
        if (securityMismatches.isNotEmpty() && maxSeverity == "NONE") {
            maxSeverity = "HIGH"
        }
        
        // ==================== INTEGRITY FIELDS ====================
        // These fields indicate app/system modifications
        
        val integrityMismatches = compareIntegrityFields(currentData, referenceData)
        mismatches.addAll(integrityMismatches)
        if (integrityMismatches.isNotEmpty() && maxSeverity == "NONE") {
            maxSeverity = "MEDIUM"
        }
        
        val isTampered = mismatches.isNotEmpty()
        
        Log.d(TAG, "Comparison result: isTampered=$isTampered, severity=$maxSeverity, mismatches=${mismatches.size}")
        
        for (mismatch in mismatches) {
            Log.w(TAG, "  MISMATCH: ${mismatch.field} (${mismatch.fieldType})")
            Log.w(TAG, "    Expected: ${mismatch.expectedValue}")
            Log.w(TAG, "    Actual: ${mismatch.actualValue}")
            Log.w(TAG, "    Severity: ${mismatch.severity}")
        }
        
        return HeartbeatComparisonResult(
            isTampered = isTampered,
            mismatches = mismatches,
            severity = maxSeverity,
            detectionMode = mode,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Compare immutable fields (device swap detection)
     */
    private fun compareImmutableFields(
        current: UnifiedHeartbeatData,
        reference: UnifiedHeartbeatData
    ): List<HeartbeatMismatch> {
        
        val mismatches = mutableListOf<HeartbeatMismatch>()
        
        // Device ID
        if (current.deviceId != reference.deviceId) {
            mismatches.add(HeartbeatMismatch(
                field = "Device ID",
                expectedValue = reference.deviceId,
                actualValue = current.deviceId,
                severity = "CRITICAL",
                fieldType = "IMMUTABLE"
            ))
        }
        
        // Android ID
        if (current.androidId != reference.androidId) {
            mismatches.add(HeartbeatMismatch(
                field = "Android ID",
                expectedValue = reference.androidId,
                actualValue = current.androidId,
                severity = "CRITICAL",
                fieldType = "IMMUTABLE"
            ))
        }
        
        // Device Fingerprint
        if (current.deviceFingerprint != reference.deviceFingerprint) {
            mismatches.add(HeartbeatMismatch(
                field = "Device Fingerprint",
                expectedValue = reference.deviceFingerprint,
                actualValue = current.deviceFingerprint,
                severity = "CRITICAL",
                fieldType = "IMMUTABLE"
            ))
        }
        
        // Manufacturer
        if (current.manufacturer != reference.manufacturer) {
            mismatches.add(HeartbeatMismatch(
                field = "Manufacturer",
                expectedValue = reference.manufacturer,
                actualValue = current.manufacturer,
                severity = "CRITICAL",
                fieldType = "IMMUTABLE"
            ))
        }
        
        // Model
        if (current.model != reference.model) {
            mismatches.add(HeartbeatMismatch(
                field = "Model",
                expectedValue = reference.model,
                actualValue = current.model,
                severity = "CRITICAL",
                fieldType = "IMMUTABLE"
            ))
        }
        
        // Bootloader
        if (current.bootloader != reference.bootloader) {
            mismatches.add(HeartbeatMismatch(
                field = "Bootloader",
                expectedValue = reference.bootloader,
                actualValue = current.bootloader,
                severity = "CRITICAL",
                fieldType = "IMMUTABLE"
            ))
        }
        
        // Processor
        if (current.processor != reference.processor) {
            mismatches.add(HeartbeatMismatch(
                field = "Processor",
                expectedValue = reference.processor,
                actualValue = current.processor,
                severity = "CRITICAL",
                fieldType = "IMMUTABLE"
            ))
        }
        
        return mismatches
    }
    
    /**
     * Compare critical tamper detection fields
     */
    private fun compareCriticalTamperFields(
        current: UnifiedHeartbeatData,
        reference: UnifiedHeartbeatData
    ): List<HeartbeatMismatch> {
        
        val mismatches = mutableListOf<HeartbeatMismatch>()
        
        // Root status
        if (current.isDeviceRooted != reference.isDeviceRooted) {
            mismatches.add(HeartbeatMismatch(
                field = "Device Rooted",
                expectedValue = reference.isDeviceRooted.toString(),
                actualValue = current.isDeviceRooted.toString(),
                severity = "CRITICAL",
                fieldType = "CRITICAL_TAMPER"
            ))
        }
        
        // Bootloader unlock status
        if (current.isBootloaderUnlocked != reference.isBootloaderUnlocked) {
            mismatches.add(HeartbeatMismatch(
                field = "Bootloader Unlocked",
                expectedValue = reference.isBootloaderUnlocked.toString(),
                actualValue = current.isBootloaderUnlocked.toString(),
                severity = "CRITICAL",
                fieldType = "CRITICAL_TAMPER"
            ))
        }
        
        // Custom ROM status
        if (current.isCustomRom != reference.isCustomRom) {
            mismatches.add(HeartbeatMismatch(
                field = "Custom ROM",
                expectedValue = reference.isCustomRom.toString(),
                actualValue = current.isCustomRom.toString(),
                severity = "CRITICAL",
                fieldType = "CRITICAL_TAMPER"
            ))
        }
        
        // Installed apps hash
        if (current.installedAppsHash != reference.installedAppsHash) {
            mismatches.add(HeartbeatMismatch(
                field = "Installed Apps Hash",
                expectedValue = reference.installedAppsHash,
                actualValue = current.installedAppsHash,
                severity = "CRITICAL",
                fieldType = "CRITICAL_TAMPER"
            ))
        }
        
        // System properties hash
        if (current.systemPropertiesHash != reference.systemPropertiesHash) {
            mismatches.add(HeartbeatMismatch(
                field = "System Properties Hash",
                expectedValue = reference.systemPropertiesHash,
                actualValue = current.systemPropertiesHash,
                severity = "CRITICAL",
                fieldType = "CRITICAL_TAMPER"
            ))
        }
        
        return mismatches
    }
    
    /**
     * Compare security fields
     */
    private fun compareSecurityFields(
        current: UnifiedHeartbeatData,
        reference: UnifiedHeartbeatData
    ): List<HeartbeatMismatch> {
        
        val mismatches = mutableListOf<HeartbeatMismatch>()
        
        // USB debugging
        if (current.isUsbDebuggingEnabled != reference.isUsbDebuggingEnabled) {
            mismatches.add(HeartbeatMismatch(
                field = "USB Debugging Enabled",
                expectedValue = reference.isUsbDebuggingEnabled.toString(),
                actualValue = current.isUsbDebuggingEnabled.toString(),
                severity = "HIGH",
                fieldType = "SECURITY"
            ))
        }
        
        // Developer mode
        if (current.isDeveloperModeEnabled != reference.isDeveloperModeEnabled) {
            mismatches.add(HeartbeatMismatch(
                field = "Developer Mode Enabled",
                expectedValue = reference.isDeveloperModeEnabled.toString(),
                actualValue = current.isDeveloperModeEnabled.toString(),
                severity = "HIGH",
                fieldType = "SECURITY"
            ))
        }
        
        return mismatches
    }
    
    /**
     * Compare integrity fields
     */
    private fun compareIntegrityFields(
        current: UnifiedHeartbeatData,
        reference: UnifiedHeartbeatData
    ): List<HeartbeatMismatch> {
        
        val mismatches = mutableListOf<HeartbeatMismatch>()
        
        // Tamper severity
        if (current.tamperSeverity != reference.tamperSeverity) {
            mismatches.add(HeartbeatMismatch(
                field = "Tamper Severity",
                expectedValue = reference.tamperSeverity,
                actualValue = current.tamperSeverity,
                severity = "MEDIUM",
                fieldType = "INTEGRITY"
            ))
        }
        
        return mismatches
    }
    
    /**
     * Check if comparison result indicates critical tampering
     */
    fun isCriticalTampering(result: HeartbeatComparisonResult): Boolean {
        return result.severity == "CRITICAL" && result.isTampered
    }
    
    /**
     * Get action to take based on comparison result
     */
    fun getActionForResult(result: HeartbeatComparisonResult): String {
        return when {
            result.severity == "CRITICAL" -> "HARD_LOCK"
            result.severity == "HIGH" -> "HARD_LOCK"
            result.severity == "MEDIUM" -> "ALERT"
            else -> "CONTINUE"
        }
    }
}
