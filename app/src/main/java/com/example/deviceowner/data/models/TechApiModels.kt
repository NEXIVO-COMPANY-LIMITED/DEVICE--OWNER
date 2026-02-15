package com.example.deviceowner.data.models

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /api/tech/devicecategory/logs/
 * Matches sponsa_backend tech/serializers.py DeviceLogSerializer (exact field names: LogType, Loglevel).
 */
data class DeviceLogRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("LogType") val logType: String,
    @SerializedName("message") val message: String,
    @SerializedName("Loglevel") val logLevel: String,
    @SerializedName("extra_data") val extraData: Map<String, Any?>? = null
) {
    companion object {
        val VALID_LOG_TYPES = setOf(
            "heartbeat", "registration", "installation", "authentication",
            "data_sync", "error", "system", "security", "network", "other"
        )
        val VALID_LOG_LEVELS = setOf("Normal", "Warning", "Error")
    }
    
    init {
        require(deviceId.isNotBlank()) { "device_id cannot be blank" }
        require(logType in VALID_LOG_TYPES) { "LogType must be one of: ${VALID_LOG_TYPES.joinToString(", ")}" }
        require(message.isNotBlank()) { "message cannot be blank" }
        require(logLevel in VALID_LOG_LEVELS) { "Loglevel must be one of: ${VALID_LOG_LEVELS.joinToString(", ")}" }
    }
}

/**
 * Response from logs API
 */
data class DeviceLogResponse(
    @SerializedName("recorded") val recorded: Boolean = false
)

/**
 * Request body for POST /api/tech/devicecategory/bugs/
 * Matches sponsa_backend tech/serializers.py BugReportCreateSerializer
 */
data class BugReportRequest(
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("device") val device: String,
    @SerializedName("priority") val priority: String = "medium",  // low, medium, high, critical (Django expects lowercase)
    @SerializedName("extra_data") val extraData: Map<String, Any?>? = null
)

/**
 * Response from bugs API
 */
data class BugReportResponse(
    @SerializedName("recorded") val recorded: Boolean = false
)
