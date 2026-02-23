package com.example.deviceowner.data.models.device

import com.google.gson.annotations.SerializedName

/**
 * A snapshot of current device state and metadata.
 * Used for registration, logging, and bug reporting.
 */
data class DeviceDataSnapshot(
    @SerializedName("serial_number") val serialNumber: String?,
    @SerializedName("model") val model: String?,
    @SerializedName("manufacturer") val manufacturer: String?,
    @SerializedName("android_id") val androidId: String?,
    @SerializedName("device_imeis") val deviceImeis: List<String>?,
    @SerializedName("installed_ram") val installedRam: String?,
    @SerializedName("total_storage") val totalStorage: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("is_device_rooted") val isDeviceRooted: Boolean?,
    @SerializedName("is_usb_debugging_enabled") val isUsbDebuggingEnabled: Boolean?,
    @SerializedName("is_developer_mode_enabled") val isDeveloperModeEnabled: Boolean?,
    @SerializedName("is_bootloader_unlocked") val isBootloaderUnlocked: Boolean?,
    @SerializedName("is_custom_rom") val isCustomRom: Boolean?,
    @SerializedName("sdk_version") val sdkVersion: Int?,
    @SerializedName("os_version") val osVersion: String?,
    @SerializedName("security_patch_level") val securityPatchLevel: String?,
    @SerializedName("battery_level") val batteryLevel: Int?,
    @SerializedName("language") val language: String?,
    @SerializedName("device_fingerprint") val deviceFingerprint: String?,
    @SerializedName("bootloader") val bootloader: String?
)
