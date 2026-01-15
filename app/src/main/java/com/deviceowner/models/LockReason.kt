package com.deviceowner.models

/**
 * Lock Reason Enumeration
 * 
 * Categorizes lock reasons for better tracking and analytics
 * Feature 4.4 Enhancement #2: Lock Reason Categorization
 */
enum class LockReason(
    val category: String,
    val severity: LockSeverity,
    val requiresAdminAction: Boolean,
    val displayMessage: String
) {
    PAYMENT_OVERDUE(
        category = "FINANCIAL",
        severity = LockSeverity.MEDIUM,
        requiresAdminAction = true,
        displayMessage = "Payment is overdue. Please contact support to unlock your device."
    ),
    
    LOAN_DEFAULT(
        category = "FINANCIAL",
        severity = LockSeverity.HIGH,
        requiresAdminAction = true,
        displayMessage = "Loan is in default. Device is locked until payment is received."
    ),
    
    COMPLIANCE_VIOLATION(
        category = "POLICY",
        severity = LockSeverity.HIGH,
        requiresAdminAction = true,
        displayMessage = "Compliance violation detected. Device locked for security."
    ),
    
    SECURITY_BREACH(
        category = "SECURITY",
        severity = LockSeverity.CRITICAL,
        requiresAdminAction = true,
        displayMessage = "Security breach detected. Device locked immediately."
    ),
    
    DEVICE_TAMPERING(
        category = "SECURITY",
        severity = LockSeverity.CRITICAL,
        requiresAdminAction = true,
        displayMessage = "Device tampering detected. Device locked for protection."
    ),
    
    ADMIN_ACTION(
        category = "ADMINISTRATIVE",
        severity = LockSeverity.MEDIUM,
        requiresAdminAction = true,
        displayMessage = "Admin action. Device locked by administrator."
    ),
    

    SUSPICIOUS_ACTIVITY(
        category = "SECURITY",
        severity = LockSeverity.HIGH,
        requiresAdminAction = true,
        displayMessage = "Suspicious activity detected. Device locked for investigation."
    ),
    
    GEOFENCE_VIOLATION(
        category = "POLICY",
        severity = LockSeverity.MEDIUM,
        requiresAdminAction = true,
        displayMessage = "Device outside allowed area. Device locked."
    ),
    
    UNAUTHORIZED_ACCESS(
        category = "SECURITY",
        severity = LockSeverity.HIGH,
        requiresAdminAction = true,
        displayMessage = "Unauthorized access attempt. Device locked."
    ),
    
    CONTRACT_BREACH(
        category = "LEGAL",
        severity = LockSeverity.HIGH,
        requiresAdminAction = true,
        displayMessage = "Contract breach detected. Device locked."
    ),
    
    EMERGENCY_LOCK(
        category = "EMERGENCY",
        severity = LockSeverity.CRITICAL,
        requiresAdminAction = true,
        displayMessage = "Emergency lock activated. Contact support immediately."
    ),
    
    OTHER(
        category = "GENERAL",
        severity = LockSeverity.MEDIUM,
        requiresAdminAction = true,
        displayMessage = "Device locked. Please contact support for assistance."
    );
    
    /**
     * Get user-friendly message with optional custom details
     */
    fun getMessage(customDetails: String? = null): String {
        return if (customDetails != null) {
            "$displayMessage\n\nDetails: $customDetails"
        } else {
            displayMessage
        }
    }
    
    /**
     * Check if this reason requires immediate action
     */
    fun isUrgent(): Boolean {
        return severity == LockSeverity.CRITICAL || severity == LockSeverity.HIGH
    }
    
    /**
     * Get support contact message
     */
    fun getSupportMessage(): String {
        return when (severity) {
            LockSeverity.CRITICAL -> "Contact support immediately: [SUPPORT_NUMBER]"
            LockSeverity.HIGH -> "Contact support as soon as possible: [SUPPORT_NUMBER]"
            LockSeverity.MEDIUM -> "Contact support for assistance: [SUPPORT_NUMBER]"
            LockSeverity.LOW -> "Contact support if needed: [SUPPORT_NUMBER]"
        }
    }
}

/**
 * Lock Severity Levels
 */
enum class LockSeverity(val level: Int, val color: String) {
    LOW(1, "#4CAF50"),      // Green
    MEDIUM(2, "#FF9800"),   // Orange
    HIGH(3, "#F44336"),     // Red
    CRITICAL(4, "#9C27B0")  // Purple
}

/**
 * Categorized Lock Command
 * Enhanced lock command with categorization
 */
data class CategorizedLockCommand(
    val id: String,
    val deviceId: String,
    val lockType: String,              // SOFT, HARD, PERMANENT
    val reason: LockReason,
    val description: String,
    val customMessage: String? = null,
    val timestamp: Long,
    val adminId: String? = null,
    val expiresAt: Long? = null,       // For scheduled locks
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Get full display message
     */
    fun getDisplayMessage(): String {
        return reason.getMessage(customMessage)
    }
    
    /**
     * Check if lock has expired
     */
    fun isExpired(): Boolean {
        return expiresAt != null && System.currentTimeMillis() > expiresAt
    }
    
    /**
     * Get time until expiration
     */
    fun getTimeUntilExpiration(): Long? {
        return expiresAt?.let { it - System.currentTimeMillis() }
    }
}

/**
 * Lock Reason Statistics
 * For analytics and reporting
 */
data class LockReasonStatistics(
    val reason: LockReason,
    val count: Int,
    val lastOccurrence: Long,
    val averageDuration: Long,
    val totalDuration: Long
)

/**
 * Lock Reason Category Summary
 * Aggregated statistics by category
 */
data class LockReasonCategorySummary(
    val category: String,
    val totalLocks: Int,
    val reasons: List<LockReasonStatistics>,
    val mostCommonReason: LockReason?,
    val averageSeverity: Double
)
