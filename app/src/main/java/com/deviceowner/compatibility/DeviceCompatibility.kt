package com.deviceowner.compatibility

/**
 * Represents device compatibility information
 */
data class DeviceCompatibility(
    val isCompatible: Boolean,
    val manufacturer: String,
    val model: String,
    val osVersion: Int,
    val apiLevel: Int,
    val features: Set<String>,
    val issues: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a supported device in the compatibility matrix
 */
data class SupportedDevice(
    val manufacturer: String,
    val model: String,
    val minApiLevel: Int,
    val maxApiLevel: Int = Int.MAX_VALUE,
    val requiredFeatures: Set<String> = emptySet(),
    val notes: String = ""
)

/**
 * Represents compatibility check result
 */
data class CompatibilityCheckResult(
    val compatible: Boolean,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)

/**
 * Enum for device manufacturers
 */
enum class Manufacturer {
    SAMSUNG,
    XIAOMI,
    OPPO,
    VIVO,
    REALME,
    MOTOROLA,
    NOKIA,
    GOOGLE,
    ONEPLUS,
    OTHER
}
