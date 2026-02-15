package com.example.deviceowner.data.models

import com.google.gson.annotations.SerializedName

/**
 * Heartbeat request data model for POST /devices/<device_id>/data/
 * Include heartbeat_timestamp (device-created) so server can use it when arrival is delayed/batched.
 * 
 * Timestamp format: ISO-8601 (e.g., "2026-02-13T10:30:45.123Z")
 * If not provided, server will use server time.
 */
/**
 * Request for POST /api/devices/<device_id>/data/ (heartbeat).
 * Django DeviceDataPostSerializer + _normalize_mobile_heartbeat accept both flat and nested fields.
 * Send flat fields at top level so comparison works: serial_number, device_imeis, installed_ram, total_storage, security flags.
 */
data class HeartbeatRequest(
    @SerializedName("heartbeat_timestamp")
    val heartbeatTimestamp: String? = null,
    @SerializedName("device_id")
    val deviceId: String? = null,
    @SerializedName("android_id")
    val androidId: String? = null,
    @SerializedName("model")
    val model: String? = null,
    @SerializedName("manufacturer")
    val manufacturer: String? = null,
    @SerializedName("serial_number")
    val serialNumber: String? = null,
    @SerializedName("serial")
    val serial: String? = null,
    @SerializedName("device_imeis")
    val deviceImeis: List<String>? = null,
    @SerializedName("installed_ram")
    val installedRam: String? = null,
    @SerializedName("total_storage")
    val totalStorage: String? = null,
    @SerializedName("latitude")
    val latitude: Double? = null,
    @SerializedName("longitude")
    val longitude: Double? = null,
    @SerializedName("is_device_rooted")
    val isDeviceRooted: Boolean? = null,
    @SerializedName("is_usb_debugging_enabled")
    val isUsbDebuggingEnabled: Boolean? = null,
    @SerializedName("is_developer_mode_enabled")
    val isDeveloperModeEnabled: Boolean? = null,
    @SerializedName("is_bootloader_unlocked")
    val isBootloaderUnlocked: Boolean? = null,
    @SerializedName("is_custom_rom")
    val isCustomRom: Boolean? = null,
    @SerializedName("sdk_version")
    val sdkVersion: Int? = null,
    @SerializedName("os_version")
    val osVersion: String? = null,
    @SerializedName("security_patch_level")
    val securityPatchLevel: String? = null,
    @SerializedName("installed_apps_hash")
    val installedAppsHash: String? = null,
    @SerializedName("system_properties_hash")
    val systemPropertiesHash: String? = null,
    @SerializedName("battery_level")
    val batteryLevel: Int? = null,
    @SerializedName("device_info")
    val deviceInfo: Map<String, Any?>? = null,
    @SerializedName("android_info")
    val androidInfo: Map<String, Any?>? = null,
    @SerializedName("imei_info")
    val imeiInfo: Map<String, Any?>? = null,
    @SerializedName("storage_info")
    val storageInfo: Map<String, Any?>? = null,
    @SerializedName("location_info")
    val locationInfo: Map<String, Any?>? = null,
    @SerializedName("security_info")
    val securityInfo: Map<String, Any?>? = null,
    @SerializedName("system_integrity")
    val systemIntegrity: Map<String, Any?>? = null,
    @SerializedName("app_info")
    val appInfo: Map<String, Any?>? = null
) {
    companion object {
        /**
         * Validate timestamp format (ISO-8601)
         */
        fun isValidTimestamp(timestamp: String?): Boolean {
            if (timestamp == null) return true  // Optional
            return try {
                // Check basic ISO-8601 format
                timestamp.matches(Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.*Z?$"""))
            } catch (e: Exception) {
                false
            }
        }
    }
    
    init {
        // Validate timestamp format if provided
        if (heartbeatTimestamp != null) {
            require(isValidTimestamp(heartbeatTimestamp)) { 
                "heartbeat_timestamp must be ISO-8601 format (e.g., 2026-02-13T10:30:45.123Z)" 
            }
        }
    }
}

/**
 * Heartbeat Response model with support for server-side deactivation.
 * Supports multiple Django response formats:
 * - deactivation.status = "requested"
 * - deactivate_requested = true
 * - payment_complete = true (loan fully paid)
 * - loan_complete = true (loan ikimalika)
 * - loan_status = "completed"|"paid"|"closed"|"fully_paid"
 * - content.reason containing "Loan complete", "Payment complete", etc.
 */
data class HeartbeatResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("content") val content: LockContent? = null,
    @SerializedName("server_time") val serverTime: String? = null,
    @SerializedName("next_payment") val nextPayment: NextPaymentInfo? = null,
    @SerializedName("deactivation") val deactivation: DeactivationStatus? = null,
    @SerializedName("deactivate_requested") val deactivateRequested: Boolean? = null,
    @SerializedName("payment_complete") val paymentComplete: Boolean? = null,
    @SerializedName("loan_complete") val loanComplete: Boolean? = null,
    @SerializedName("loan_status") val loanStatus: String? = null,
    @SerializedName("management") val management: ManagementStatus? = null,
    @SerializedName("management_status") val managementStatus: String? = null,
    @SerializedName("is_locked") val isLocked: Boolean? = null,
    @SerializedName("tamper_indicators") val tamperIndicators: List<String>? = null,
    @SerializedName("instructions") val instructions: List<String>? = null,
    @SerializedName("expected_device_data") val expectedDeviceData: ExpectedDeviceData? = null,
    @SerializedName("security_status") val securityStatus: SecurityStatusInfo? = null,
    @SerializedName("changes_detected") val changesDetected: Boolean? = null,
    @SerializedName("changed_fields") val changedFields: List<String>? = null,
    @SerializedName("comparison_result") val comparisonResult: ComparisonResult? = null
) {
    /** True if server says device should be locked. Handles content, management, or top-level is_locked/management_status. */
    fun isDeviceLocked(): Boolean =
        (content?.isLocked == true) || (management?.isLocked == true) ||
        (management?.managementStatus?.lowercase() == "locked") ||
        (isLocked == true) || (managementStatus?.lowercase() == "locked")

    /** Safe lock reason from Django content.reason or management.block_reason. */
    fun getLockReason(): String =
        content?.reason?.takeIf { it.isNotBlank() }
            ?: management?.blockReason?.takeIf { it.isNotBlank() }
            ?: "Device locked by administrator"

    /**
     * True when device must be blocked: server says locked OR high-severity mismatch OR changes_detected.
     * Use this to decide hard lock; then call getBlockReason() for the message.
     */
    fun shouldBlockDevice(): Boolean =
        isDeviceLocked() ||
        hasHighSeverityMismatches() ||
        (changesDetected == true && hasAnyMismatch())

    /** True when comparison_result has at least one mismatch (high or total). */
    fun hasAnyMismatch(): Boolean {
        val cr = comparisonResult ?: return false
        val total = cr.totalMismatches ?: 0
        val high = cr.highSeverityCount ?: 0
        val list = cr.mismatches
        return total > 0 || high > 0 || !list.isNullOrEmpty()
    }

    /**
     * Reason to show on lock screen when blocking: lock reason or mismatch/tampering message.
     */
    fun getBlockReason(): String {
        if (isDeviceLocked()) return getLockReason()
        if (hasHighSeverityMismatches()) {
            val summary = getMismatchSummary()
            return "Security Alert: Device Tampering Detected. $summary"
        }
        if (changesDetected == true) return "Security Alert: Device data mismatch detected. Contact support."
        return "Device locked by administrator"
    }
    
    /** 
     * Checks if deactivation is requested. Supports all Django response formats:
     * - deactivate_requested = true
     * - deactivation.status = "requested"
     * - payment_complete = true (loan fully paid - device should be deactivated)
     * - loan_complete = true (loan ikimalika - device should be deactivated)
     * - loan_status = completed/paid/closed/fully_paid
     * - content.reason contains loan/payment complete keywords
     */
    fun isDeactivationRequested(): Boolean {
        if (deactivateRequested == true || deactivation?.status == "requested") return true
        if (paymentComplete == true || loanComplete == true) return true
        val status = loanStatus?.lowercase()?.trim()
        if (status in listOf("completed", "paid", "closed", "fully_paid", "complete")) return true
        val reason = content?.reason?.lowercase() ?: ""
        val loanPaymentCompleteKeywords = listOf(
            "loan complete", "payment complete", "loan completed", "payment completed",
            "ikimalika", "fully paid", "fully paid off", "loan paid", "payment finished"
        )
        if (loanPaymentCompleteKeywords.any { reason.contains(it) }) return true
        return false
    }

    fun hasNextPayment(): Boolean = nextPayment != null
    fun getNextPaymentDateTime(): String? = nextPayment?.dateTime
    fun getUnlockPassword(): String? = nextPayment?.unlockPassword
    fun getDeactivationCommand(): String? = if (isDeactivationRequested()) deactivation?.command else null
    
    fun hasHighSeverityMismatches(): Boolean {
        return (comparisonResult?.highSeverityCount ?: 0) > 0
    }
    
    fun getMismatches(): List<Mismatch> = comparisonResult?.mismatches ?: emptyList()

    fun getHighSeverityMismatches(): List<Mismatch> {
        return getMismatches().filter { it.severity == "high" }
    }
    
    fun getMismatchSummary(): String {
        val mismatches = getMismatches()
        if (mismatches.isEmpty()) return "No mismatches detected"
        val highCount = comparisonResult?.highSeverityCount ?: 0
        val total = comparisonResult?.totalMismatches ?: 0
        return "Mismatches: $total total (🔴 $highCount high)"
    }

    fun isValid(): Boolean {
        if (!success) return false
        if (content == null && management == null && deactivation == null && deactivateRequested == null) return false
        return true
    }

    fun toDetailedString(): String {
        return "HeartbeatResponse(success=$success, message=$message, locked=${isDeviceLocked()}, deactivation=${isDeactivationRequested()})"
    }
}

data class ManagementStatus(
    @SerializedName("is_locked") val isLocked: Boolean? = null,
    @SerializedName("management_status") val managementStatus: String? = null,
    @SerializedName("block_reason") val blockReason: String? = null
)

data class LockContent(
    @SerializedName("is_locked") val isLocked: Boolean,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("shop") val shop: String? = null
)

data class NextPaymentInfo(
    @SerializedName("date_time") val dateTime: String? = null,
    @SerializedName("unlock_password") val unlockPassword: String? = null
)

data class DeactivationStatus(
    @SerializedName("status") val status: String? = null,
    @SerializedName("command") val command: String? = null
)

data class ComparisonResult(
    @SerializedName("mismatches") val mismatches: List<Mismatch>? = null,
    @SerializedName("high_severity_count") val highSeverityCount: Int? = null,
    @SerializedName("medium_severity_count") val mediumSeverityCount: Int? = null,
    @SerializedName("total_mismatches") val totalMismatches: Int? = null
)

data class Mismatch(
    @SerializedName("field") val field: String? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("expected") val expected: Any? = null,
    @SerializedName("actual") val actual: Any? = null
)

data class ExpectedDeviceData(
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("loan_number") val loanNumber: String? = null
)

data class SecurityStatusInfo(
    @SerializedName("is_rooted") val isRooted: Boolean? = null,
    @SerializedName("security_score") val securityScore: Int? = null,
    @SerializedName("has_custom_rom") val hasCustomRom: Boolean? = null,
    @SerializedName("is_debugging_enabled") val isDebuggingEnabled: Boolean? = null,
    @SerializedName("is_developer_mode_enabled") val isDeveloperModeEnabled: Boolean? = null,
    @SerializedName("has_unknown_sources") val hasUnknownSources: Boolean? = null,
    @SerializedName("bootloader_unlocked") val bootloaderUnlocked: Boolean? = null
)
