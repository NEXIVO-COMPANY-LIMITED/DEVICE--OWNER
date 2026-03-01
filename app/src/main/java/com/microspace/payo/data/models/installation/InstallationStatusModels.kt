package com.microspace.payo.data.models.installation

import com.google.gson.annotations.SerializedName

/**
 * Installation Status Request for POST /api/devices/mobile/{device_id}/installation-status/
 * Django accepts either top-level completed/reason or wrapped: installation_status: { completed, reason }.
 * We send both so backend always has required fields.
 */
data class InstallationStatusRequest(
    @SerializedName("installation_status")
    val installationStatus: InstallationStatusInner? = null,
    @SerializedName("completed")
    val completed: Boolean,
    @SerializedName("reason")
    val reason: String,
    @SerializedName("timestamp")
    val timestamp: String? = null
)

data class InstallationStatusInner(
    @SerializedName("completed") val completed: Boolean,
    @SerializedName("reason") val reason: String
)

/**
 * Installation Status Response model from Django backend
 */
data class InstallationStatusResponse(
    @SerializedName("success")
    val success: Boolean = false,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("device_id")
    val deviceId: String? = null,
    
    @SerializedName("installation_status")
    val installationStatus: String? = null,
    
    @SerializedName("completed_at")
    val completedAt: String? = null
)




