package com.deviceowner.models

import android.R

/**
 * Data class representing complete registration information.
 * Contains all user and device information collected during setup.
 */
data class RegistrationData(
    val userId: String,
    val shopId: String,
    val imei: String,
    val serialNumber: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val deviceId: String = "",
    val status: String = "pending",
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if all required fields are present
     */
    fun isValid(): Boolean {
        return userId.isNotEmpty() &&
                shopId.isNotEmpty() &&
                imei.isNotEmpty() &&
                serialNumber.isNotEmpty() &&
                model.isNotEmpty() &&
                androidVersion.isNotEmpty()
    }

    /**
     * Convert to map for API request
     */
    fun toMap(): Map<String, String> {
        return mapOf(
            "user_id" to userId,
            "shop_id" to shopId,
            "imei" to imei,
            "serial_number" to serialNumber,
            "model" to model,
            "m anufacturer" to manufacturer,
            "android_version" to androidVersion
        )
    }

    /**
     * Get summary string for display
     */
    fun getSummary(): String {
        return """
            User ID: $userId
            Shop ID: $shopId
            IMEI: $imei
            Serial: $serialNumber
            Model: $model
            Manufacturer: $manufacturer
            Android: $androidVersion
        """.trimIndent()
    }
}
