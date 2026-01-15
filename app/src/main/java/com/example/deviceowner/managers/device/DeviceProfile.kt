package com.example.deviceowner.managers

/**
 * Device profile containing all device identifiers
 * Used for device verification and mismatch detection
 */
data class DeviceProfile(
    val imei: String,
    val serialNumber: String,
    val androidId: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val deviceFingerprint: String,
    val simSerialNumber: String,
    val buildNumber: String,
    val timestamp: Long = System.currentTimeMillis()
)
