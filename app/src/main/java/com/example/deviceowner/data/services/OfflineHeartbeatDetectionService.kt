package com.example.deviceowner.data.services

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.local.UnifiedHeartbeatStorage
import com.example.deviceowner.data.models.UnifiedHeartbeatData
import com.example.deviceowner.data.models.OfflineHeartbeatData
import com.example.deviceowner.data.models.OfflineHeartbeatComparisonResult
import com.example.deviceowner.data.models.HeartbeatMismatch

/**
 * Offline Heartbeat Detection Service
 * Performs tamper detection locally without server communication
 * Uses same detection logic as online mode but with local baseline
 * 
 * Key Features:
 * - Works completely offline
 * - Compares current device state against stored baseline
 * - Detects same tamper indicators as online mode
 * - Stores results for later sync when online
 */
class OfflineHeartbeatDetectionService(private val context: Context) {
    
    private val storage = UnifiedHeartbeatStorage(context)
    
    companion object {
        private const val TAG = "OfflineHeartbeatDetection"
    }
    
    /**
     * Perform offline tamper detection
     * Compares current heartbeat against stored offline baseline
     */
    suspend fun detectTamperOffline(
        currentHeartbeat: UnifiedHeartbeatData
    ): OfflineHeartbeatComparisonResult {
        return try {
            Log.d(TAG, "Starting offline tamper detection for ${currentHeartbeat.deviceId}")
            
            // Get offline baseline
            val baseline = storage.getOfflineBaseline()
                ?: return createNoBaselineResult(currentHeartbeat)
            
            // Convert current to compact format
            val currentCompact = currentHeartbeat.toCompactOfflineData()
            
            // Compare
            val mismatches = compareOfflineData(baseline, currentCompact)
            val severity = calculateSeverity(mismatches)
            val isTampered = severity != "NONE"
            
            Log.d(TAG, "✓ Offline detection complete: tampered=$isTampered, severity=$severity")
            
            OfflineHeartbeatComparisonResult(
                isTampered = isTampered,
                mismatches = mismatches,
                severity = severity,
                detectionMode = "OFFLINE",
                timestamp = System.currentTimeMillis(),
                baselineTimestamp = baseline.timestamp,
                timeSinceBaseline = System.currentTimeMillis() - baseline.timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error during offline detection", e)
            OfflineHeartbeatComparisonResult(
                isTampered = true,
                mismatches = listOf(
                    HeartbeatMismatch(
                        field = "detection_error",
                        expectedValue = "success",
                        actualValue = e.message ?: "unknown error",
                        severity = "HIGH",
                        fieldType = "CRITICAL_TAMPER"
                    )
                ),
                severity = "HIGH",
                detectionMode = "OFFLINE",
                timestamp = System.currentTimeMillis(),
                baselineTimestamp = 0L,
                timeSinceBaseline = 0L
            )
        }
    }
    
    /**
     * Compare offline baseline with current device state
     */
    private fun compareOfflineData(
        baseline: OfflineHeartbeatData,
        current: OfflineHeartbeatData
    ): List<HeartbeatMismatch> {
        val mismatches = mutableListOf<HeartbeatMismatch>()
        
        // Device fingerprint (immutable)
        if (baseline.deviceFingerprint != current.deviceFingerprint) {
            mismatches.add(
                HeartbeatMismatch(
                    field = "device_fingerprint",
                    expectedValue = baseline.deviceFingerprint,
                    actualValue = current.deviceFingerprint,
                    severity = "CRITICAL",
                    fieldType = "IMMUTABLE"
                )
            )
        }
        
        // Rooted status (critical tamper indicator)
        if (baseline.isDeviceRooted != current.isDeviceRooted) {
            mismatches.add(
                HeartbeatMismatch(
                    field = "is_device_rooted",
                    expectedValue = baseline.isDeviceRooted.toString(),
                    actualValue = current.isDeviceRooted.toString(),
                    severity = "CRITICAL",
                    fieldType = "CRITICAL_TAMPER"
                )
            )
        }
        
        // Bootloader unlocked (critical tamper indicator)
        if (baseline.isBootloaderUnlocked != current.isBootloaderUnlocked) {
            mismatches.add(
                HeartbeatMismatch(
                    field = "is_bootloader_unlocked",
                    expectedValue = baseline.isBootloaderUnlocked.toString(),
                    actualValue = current.isBootloaderUnlocked.toString(),
                    severity = "CRITICAL",
                    fieldType = "CRITICAL_TAMPER"
                )
            )
        }
        
        // Custom ROM (critical tamper indicator)
        if (baseline.isCustomRom != current.isCustomRom) {
            mismatches.add(
                HeartbeatMismatch(
                    field = "is_custom_rom",
                    expectedValue = baseline.isCustomRom.toString(),
                    actualValue = current.isCustomRom.toString(),
                    severity = "CRITICAL",
                    fieldType = "CRITICAL_TAMPER"
                )
            )
        }
        
        // USB debugging (security indicator)
        if (baseline.isUsbDebuggingEnabled != current.isUsbDebuggingEnabled) {
            mismatches.add(
                HeartbeatMismatch(
                    field = "is_usb_debugging_enabled",
                    expectedValue = baseline.isUsbDebuggingEnabled.toString(),
                    actualValue = current.isUsbDebuggingEnabled.toString(),
                    severity = "HIGH",
                    fieldType = "SECURITY"
                )
            )
        }
        
        // Developer mode (security indicator)
        if (baseline.isDeveloperModeEnabled != current.isDeveloperModeEnabled) {
            mismatches.add(
                HeartbeatMismatch(
                    field = "is_developer_mode_enabled",
                    expectedValue = baseline.isDeveloperModeEnabled.toString(),
                    actualValue = current.isDeveloperModeEnabled.toString(),
                    severity = "HIGH",
                    fieldType = "SECURITY"
                )
            )
        }
        
        // Installed apps hash (integrity check)
        if (baseline.installedAppsHash != current.installedAppsHash) {
            mismatches.add(
                HeartbeatMismatch(
                    field = "installed_apps_hash",
                    expectedValue = baseline.installedAppsHash,
                    actualValue = current.installedAppsHash,
                    severity = "MEDIUM",
                    fieldType = "INTEGRITY"
                )
            )
        }
        
        // System properties hash (integrity check)
        if (baseline.systemPropertiesHash != current.systemPropertiesHash) {
            mismatches.add(
                HeartbeatMismatch(
                    field = "system_properties_hash",
                    expectedValue = baseline.systemPropertiesHash,
                    actualValue = current.systemPropertiesHash,
                    severity = "MEDIUM",
                    fieldType = "INTEGRITY"
                )
            )
        }
        
        return mismatches
    }
    
    /**
     * Calculate overall severity from mismatches
     */
    private fun calculateSeverity(mismatches: List<HeartbeatMismatch>): String {
        if (mismatches.isEmpty()) return "NONE"
        
        return when {
            mismatches.any { it.severity == "CRITICAL" } -> "CRITICAL"
            mismatches.any { it.severity == "HIGH" } -> "HIGH"
            mismatches.any { it.severity == "MEDIUM" } -> "MEDIUM"
            else -> "LOW"
        }
    }
    
    /**
     * Create result when no baseline exists
     */
    private fun createNoBaselineResult(
        currentHeartbeat: UnifiedHeartbeatData
    ): OfflineHeartbeatComparisonResult {
        Log.w(TAG, "No offline baseline found - cannot perform offline detection")
        return OfflineHeartbeatComparisonResult(
            isTampered = false,
            mismatches = emptyList(),
            severity = "NONE",
            detectionMode = "OFFLINE",
            timestamp = System.currentTimeMillis(),
            baselineTimestamp = 0L,
            timeSinceBaseline = 0L
        )
    }
    
    /**
     * Initialize offline baseline from current heartbeat
     * Called during registration or when baseline needs to be set
     */
    suspend fun initializeOfflineBaseline(heartbeat: UnifiedHeartbeatData): Boolean {
        return try {
            Log.d(TAG, "Initializing offline baseline")
            storage.saveOfflineBaseline(heartbeat)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error initializing offline baseline", e)
            false
        }
    }
    
    /**
     * Check if offline detection is available
     */
    suspend fun isOfflineDetectionAvailable(): Boolean {
        return storage.hasOfflineBaseline()
    }
    
    /**
     * Get offline baseline age in milliseconds
     */
    suspend fun getOfflineBaselineAge(): Long {
        return storage.getOfflineBaselineAge()
    }
}
