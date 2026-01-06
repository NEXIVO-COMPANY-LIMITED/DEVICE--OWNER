package com.example.deviceowner.overlay

/**
 * Data class for overlay information
 * Feature 4.6: Pop-up Screens / Overlay UI
 */
data class OverlayData(
    val id: String,
    val type: OverlayType,
    val title: String,
    val message: String,
    val actionButtonText: String = "OK",
    val actionButtonColor: Int = 0xFF2196F3.toInt(),
    val dismissible: Boolean = false,
    val priority: Int = 0,
    val expiryTime: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isExpired(): Boolean {
        return expiryTime != null && System.currentTimeMillis() > expiryTime
    }

    fun toJson(): String {
        return com.google.gson.Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): OverlayData? {
            return try {
                com.google.gson.Gson().fromJson(json, OverlayData::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
