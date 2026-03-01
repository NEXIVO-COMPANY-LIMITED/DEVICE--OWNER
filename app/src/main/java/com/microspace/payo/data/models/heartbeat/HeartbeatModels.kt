package com.microspace.payo.data.models.heartbeat

import com.google.gson.annotations.SerializedName

/**
 * Heartbeat Request model based on HEARTBEAT_API_FLOW_DOCUMENTATION.md
 */
data class HeartbeatRequest(
    @SerializedName("device_imeis") val deviceImeis: List<String>,
    @SerializedName("serial_number") val serialNumber: String,
    @SerializedName("installed_ram") val installedRam: String,
    @SerializedName("total_storage") val totalStorage: String,
    @SerializedName("is_device_rooted") val isDeviceRooted: Boolean,
    @SerializedName("is_usb_debugging_enabled") val isUsbDebuggingEnabled: Boolean,
    @SerializedName("is_developer_mode_enabled") val isDeveloperModeEnabled: Boolean,
    @SerializedName("is_bootloader_unlocked") val isBootloaderUnlocked: Boolean,
    @SerializedName("is_custom_rom") val isCustomRom: Boolean,
    @SerializedName("android_id") val androidId: String,
    @SerializedName("model") val model: String,
    @SerializedName("manufacturer") val manufacturer: String,
    @SerializedName("device_fingerprint") val deviceFingerprint: String,
    @SerializedName("bootloader") val bootloader: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("os_edition") val osEdition: String,
    @SerializedName("sdk_version") val sdkVersion: Int,
    @SerializedName("security_patch_level") val securityPatchLevel: String,
    @SerializedName("system_uptime") val systemUptime: Long,
    @SerializedName("installed_apps_hash") val installedAppsHash: String,
    @SerializedName("system_properties_hash") val systemPropertiesHash: String,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("battery_level") val batteryLevel: Int,
    @SerializedName("language") val language: String? = null
)

/**
 * Heartbeat Response models updated to handle advanced actions and deactivation.
 */
data class HeartbeatResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("content") val content: LockContent? = null,
    @SerializedName("actions") val actions: HeartbeatActions? = null,
    @SerializedName("server_time") val serverTime: String? = null,
    @SerializedName("next_payment") val nextPayment: NextPaymentInfo? = null,
    @SerializedName("deactivation") val deactivation: DeactivationStatus? = null,
    @SerializedName("payment_complete") val paymentComplete: Boolean? = null,
    @SerializedName("loan_complete") val loanComplete: Boolean? = null,
    @SerializedName("loan_status") val loanStatus: String? = null,
    @SerializedName("is_locked") val isLocked: Boolean? = null,
    @SerializedName("management_status") val managementStatus: String? = null,
    @SerializedName("changes_detected") val changesDetected: Boolean? = null,
    @SerializedName("changed_fields") val changedFields: List<String>? = null,
    @SerializedName("deactivate_requested") val deactivateRequested: Boolean? = null
) {
    fun isDeviceLocked(): Boolean {
        if (managementStatus?.lowercase() == "locked") return true
        if (content?.isLocked == true) return true
        if (actions?.hardlock == true) return true
        if (isLocked == true) return true
        return false
    }

    fun isSoftLockRequested(): Boolean = (actions?.softlock == true)

    fun getLockReason(): String {
        var r = reason?.takeIf { it.isNotBlank() }
            ?: content?.reason?.takeIf { it.isNotBlank() }

        if (managementStatus?.lowercase() == "locked" && r.isNullOrBlank()) {
            r = "Device locked by administrator due to security policy violation."
        } else if (r.isNullOrBlank() && isDeviceLocked()) {
            r = "Device locked by administrator."
        }

        return r ?: ""
    }

    fun isDeactivationRequested(): Boolean {
        if (deactivateRequested == true) return true
        if (deactivation?.status == "requested" || deactivation?.status == "active") return true
        if (paymentComplete == true || loanComplete == true) return true
        val status = loanStatus?.lowercase()?.trim()
        if (status in listOf("completed", "paid", "closed", "fully_paid", "complete")) return true
        return false
    }

    fun getNextPaymentDateTime(): String? = nextPayment?.dateTime
    
    // Compatibility helpers for compilation
    fun hasNextPayment(): Boolean = nextPayment?.dateTime != null
    fun getUnlockPassword(): String? = actions?.unlockPassword ?: nextPayment?.unlockPassword
    fun getDeactivationCommand(): String? = deactivation?.command
    fun hasHighSeverityMismatches(): Boolean = false // Placeholder
    fun shouldBlockDevice(): Boolean = isDeviceLocked()
    fun getBlockReason(): String = getLockReason()
}

data class UnlockingPasswordInfo(
    @SerializedName("password") val password: String? = null,
    @SerializedName("message") val message: String? = null
)

data class HeartbeatActions(
    @SerializedName("softlock") val softlock: Boolean? = false,
    @SerializedName("hardlock") val hardlock: Boolean? = false,
    @SerializedName("reminder") val reminder: ReminderInfo? = null,
    @SerializedName("unlock_password") val unlockPassword: String? = null,
    @SerializedName("unlocking_password") val unlocking_password: UnlockingPasswordInfo? = null
)

data class ReminderInfo(
    @SerializedName("send") val send: Boolean? = false,
    @SerializedName("message") val message: String? = null
)

data class LockContent(
    @SerializedName("is_locked") val isLocked: Boolean,
    @SerializedName("management_status") val managementStatus: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("shop") val shop: String? = null,
    @SerializedName("command") val command: String? = null
)

data class NextPaymentInfo(
    @SerializedName("date_time") val dateTime: String? = null,
    @SerializedName("unlock_password") val unlockPassword: String? = null,
    @SerializedName("unlocking_password") val unlockingPassword: String? = null
)

data class DeactivationStatus(
    @SerializedName("status") val status: String? = null,
    @SerializedName("command") val command: String? = null
)

data class HeartbeatLogRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("heartbeat_number") val heartbeatNumber: Int = 0,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("status") val status: String,
    @SerializedName("severity") val severity: String,
    @SerializedName("message") val message: String,
    @SerializedName("response_time_ms") val responseTimeMs: Long = 0,
    @SerializedName("error_type") val errorType: String? = null,
    @SerializedName("error_details") val errorDetails: String? = null,
    @SerializedName("consecutive_failures") val consecutiveFailures: Int = 0,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("is_device_locked") val isDeviceLocked: Boolean = false,
    @SerializedName("additional_data") val additionalData: Map<String, Any>? = null,
    @SerializedName("logs") val logs: List<String>? = null
)

data class HeartbeatLogResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("log_id") val logId: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)




