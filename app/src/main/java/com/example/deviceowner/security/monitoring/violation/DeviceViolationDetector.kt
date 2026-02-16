package com.example.deviceowner.security.monitoring.violation

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.models.heartbeat.HeartbeatResponse
import java.time.LocalDateTime

/**
 * DeviceViolationDetector - Detects security violations from heartbeat response
 * 
 * Violations are classified by severity:
 * - HIGH: Critical security issues → HARD LOCK
 * - MEDIUM: Configuration changes → SOFT LOCK
 * - LOW: Informational → Log only
 * 
 * HIGH Severity Violations (HARD LOCK):
 * - Device serial number changed
 * - Device bootloader changed
 * - Device IMEI changed
 * - Android ID changed
 * - Manufacturer changed
 * - Model changed
 * - Device rooted
 * - Bootloader unlocked
 * - Custom ROM detected
 * 
 * MEDIUM Severity Violations (SOFT LOCK):
 * - Build number changed
 * - Security patch level changed
 * - Fingerprint changed
 * - Build ID changed
 * - OS version changed
 * 
 * LOW Severity Violations (LOG ONLY):
 * - Battery level changed
 * - Location changed
 * - Installed apps changed
 */
class DeviceViolationDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceViolationDetector"
        
        // HIGH severity violations (HARD LOCK)
        private val HIGH_SEVERITY_FIELDS = setOf(
            "serial_number",
            "bootloader",
            "device_id",
            "imei",
            "android_id",
            "manufacturer",
            "model",
            "is_device_rooted",
            "is_bootloader_unlocked",
            "is_custom_rom"
        )
        
        // MEDIUM severity violations (SOFT LOCK)
        private val MEDIUM_SEVERITY_FIELDS = setOf(
            "build_id",
            "build_number",
            "security_patch_level",
            "fingerprint",
            "os_version",
            "build_type"
        )
        
        // LOW severity violations (LOG ONLY)
        private val LOW_SEVERITY_FIELDS = setOf(
            "battery_level",
            "latitude",
            "longitude",
            "installed_apps_hash"
        )
    }
    
    /**
     * Detect violations from heartbeat response
     */
    fun detectViolations(response: HeartbeatResponse): ViolationReport {
        Log.d(TAG, "🔍 Detecting violations from heartbeat response...")
        
        val report = ViolationReport()
        
        // Check if response indicates changes
        if (response.changesDetected == true) {
            response.changedFields?.forEach { field: String ->
                val severity = getFieldSeverity(field)
                
                val violation = Violation(
                    field = field,
                    severity = severity,
                    reason = "Field changed: $field",
                    timestamp = LocalDateTime.now()
                )
                
                report.addViolation(violation)
                
                Log.d(TAG, "⚠️ Violation detected: ${violation.field} (${violation.severity}) - ${violation.reason}")
            }
        }
        
        // Determine lock type based on violations
        report.lockType = determineLockType(report)
        
        Log.d(TAG, "🔐 Lock type determined: ${report.lockType}")
        
        return report
    }
    
    /**
     * Determine lock type based on violations
     */
    private fun determineLockType(report: ViolationReport): LockType {
        return when {
            // HIGH severity violations → HARD LOCK
            report.highSeverityCount > 0 -> {
                Log.w(TAG, "🔒 HIGH severity violations detected → HARD LOCK")
                LockType.HARD_LOCK
            }
            
            // MEDIUM severity violations → SOFT LOCK
            report.mediumSeverityCount > 0 -> {
                Log.w(TAG, "⚠️ MEDIUM severity violations detected → SOFT LOCK")
                LockType.SOFT_LOCK
            }
            
            // No violations → NO LOCK
            else -> {
                Log.i(TAG, "✅ No violations detected → NO LOCK")
                LockType.NO_LOCK
            }
        }
    }
    
    /**
     * Get lock reason based on violations
     */
    fun getLockReason(report: ViolationReport): String {
        return when (report.lockType) {
            LockType.HARD_LOCK -> {
                val fields = report.highSeverityViolations.map { it.field }.joinToString(", ")
                "Critical security violation detected: $fields"
            }
            
            LockType.SOFT_LOCK -> {
                val fields = report.mediumSeverityViolations.map { it.field }.joinToString(", ")
                "Configuration change detected: $fields"
            }
            
            LockType.NO_LOCK -> "No violations detected"
        }
    }
    
    /**
     * Check if specific field is a violation
     */
    fun isViolation(field: String): Boolean {
        return field in HIGH_SEVERITY_FIELDS || 
               field in MEDIUM_SEVERITY_FIELDS || 
               field in LOW_SEVERITY_FIELDS
    }
    
    /**
     * Get severity of a field
     */
    fun getFieldSeverity(field: String): ViolationSeverity {
        return when (field) {
            in HIGH_SEVERITY_FIELDS -> ViolationSeverity.HIGH
            in MEDIUM_SEVERITY_FIELDS -> ViolationSeverity.MEDIUM
            in LOW_SEVERITY_FIELDS -> ViolationSeverity.LOW
            else -> ViolationSeverity.UNKNOWN
        }
    }
}

/**
 * Violation severity levels
 */
enum class ViolationSeverity {
    HIGH,      // Critical security issue
    MEDIUM,    // Configuration change
    LOW,       // Informational
    UNKNOWN    // Unknown severity
}

/**
 * Lock type to apply
 */
enum class LockType {
    HARD_LOCK,  // Device completely locked
    SOFT_LOCK,  // Payment reminder / warning
    NO_LOCK     // No lock needed
}

/**
 * Individual violation
 */
data class Violation(
    val field: String,
    val severity: ViolationSeverity,
    val reason: String,
    val timestamp: LocalDateTime
)

/**
 * Violation report
 */
class ViolationReport {
    private val violations = mutableListOf<Violation>()
    var lockType: LockType = LockType.NO_LOCK
    
    val highSeverityViolations: List<Violation>
        get() = violations.filter { it.severity == ViolationSeverity.HIGH }
    
    val mediumSeverityViolations: List<Violation>
        get() = violations.filter { it.severity == ViolationSeverity.MEDIUM }
    
    val lowSeverityViolations: List<Violation>
        get() = violations.filter { it.severity == ViolationSeverity.LOW }
    
    val highSeverityCount: Int
        get() = highSeverityViolations.size
    
    val mediumSeverityCount: Int
        get() = mediumSeverityViolations.size
    
    val lowSeverityCount: Int
        get() = lowSeverityViolations.size
    
    val totalViolations: Int
        get() = violations.size
    
    fun addViolation(violation: Violation) {
        violations.add(violation)
    }
    
    fun hasHighSeverityViolations(): Boolean = highSeverityCount > 0
    
    fun hasMediumSeverityViolations(): Boolean = mediumSeverityCount > 0
    
    fun hasViolations(): Boolean = totalViolations > 0
    
    fun getViolationSummary(): String {
        return """
            Violation Report:
            - Total: $totalViolations
            - High Severity: $highSeverityCount
            - Medium Severity: $mediumSeverityCount
            - Low Severity: $lowSeverityCount
            - Lock Type: $lockType
        """.trimIndent()
    }
}
