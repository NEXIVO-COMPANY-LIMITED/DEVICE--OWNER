package com.example.deviceowner.data.models

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /api/tamper/mobile/{device_id}/report/
 * Matches Django TamperEventReportSerializer and TamperEvent model.
 *
 * Django expects: tamper_type, severity (CRITICAL only), description, timestamp (required), extra_data.
 * Valid tamper_type: BOOTLOADER_UNLOCKED, ROOT_DETECTED, DEVELOPER_MODE, USB_DEBUG, CUSTOM_ROM,
 *   SYSTEM_MODIFIED, SECURITY_PATCH_OLD, UNKNOWN_SOURCE, ADB_ENABLED, MOCK_LOCATION, SIM_CHANGE.
 */
data class TamperEventRequest(
    @SerializedName("tamper_type")
    val tamperType: String,

    @SerializedName("severity")
    val severity: String = "CRITICAL",

    @SerializedName("description")
    val description: String,

    @SerializedName("timestamp")
    val timestamp: String,

    @SerializedName("extra_data")
    val extraData: Map<String, Any?>? = null
) {
    companion object {
        /** Django TamperEvent.TAMPER_TYPE_CHOICES */
        val DJANGO_TAMPER_TYPES = setOf(
            "BOOTLOADER_UNLOCKED", "ROOT_DETECTED", "DEVELOPER_MODE", "USB_DEBUG",
            "CUSTOM_ROM", "SYSTEM_MODIFIED", "SECURITY_PATCH_OLD", "UNKNOWN_SOURCE",
            "ADB_ENABLED", "MOCK_LOCATION", "SIM_CHANGE", "PACKAGE_REMOVED"
        )

        /** Map app tamper type to Django tamper_type. Django only accepts CRITICAL severity. */
        fun mapToDjangoTamperType(appTamperType: String): String {
            return when (appTamperType.uppercase()) {
                "BOOTLOADER_UNLOCKED" -> "BOOTLOADER_UNLOCKED"
                "ROOT_DETECTED", "MAGISK_DETECTED" -> "ROOT_DETECTED"
                "DEVELOPER_OPTIONS", "DEVELOPER_MODE" -> "DEVELOPER_MODE"
                "USB_DEBUGGING", "USB_DEBUG", "UNAUTHORIZED_ACCESS" -> "USB_DEBUG"
                "CUSTOM_ROM" -> "CUSTOM_ROM"
                "SYSTEM_MODIFIED", "DEVICE_OWNER_REMOVED", "POLICY_VIOLATION" -> "SYSTEM_MODIFIED"
                "SECURITY_PATCH_OLD" -> "SECURITY_PATCH_OLD"
                "UNKNOWN_SOURCE" -> "UNKNOWN_SOURCE"
                "ADB_ENABLED" -> "ADB_ENABLED"
                "MOCK_LOCATION", "LOCATION_VIOLATION" -> "MOCK_LOCATION"
                "SIM_CHANGE", "SIM_SWAP" -> "SIM_CHANGE"
                "PACKAGE_REMOVED", "PACKAGE_REMOVAL" -> "PACKAGE_REMOVED"
                else -> "SYSTEM_MODIFIED"
            }
        }

        fun nowIso8601(): String {
            return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())
        }

        /** Build request for Django /api/tamper/mobile/{device_id}/report/ */
        fun forDjango(tamperType: String, description: String, extraData: Map<String, Any?>? = null): TamperEventRequest {
            val djangoType = mapToDjangoTamperType(tamperType)
            return TamperEventRequest(
                tamperType = djangoType,
                severity = "CRITICAL",
                description = description.take(1000),
                timestamp = nowIso8601(),
                extraData = extraData
            )
        }
    }
}

/**
 * Response from POST /api/tamper/mobile/{device_id}/report/
 * Django returns: {"locked": true/false}
 */
data class TamperEventResponse(
    @SerializedName("locked")
    val locked: Boolean = false
) {
    fun isValid(): Boolean = true
}
