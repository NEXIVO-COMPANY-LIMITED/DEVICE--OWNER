package com.microspace.payo.data.local.database.entities.device

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Device Data Entity - Stores device registration data locally
 *
 * Stores all device information collected during registration
 * for offline access and comparison with heartbeat data
 */
@Entity(tableName = "device_data")
data class DeviceDataEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    // Device Identification
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,

    @ColumnInfo(name = "server_device_id")
    val serverDeviceId: String? = null,

    @ColumnInfo(name = "loan_number")
    val loanNumber: String? = null,

    @ColumnInfo(name = "serial_number")
    val serialNumber: String? = null,

    // Device Info
    @ColumnInfo(name = "android_id")
    val androidId: String? = null,

    @ColumnInfo(name = "model")
    val model: String? = null,

    @ColumnInfo(name = "manufacturer")
    val manufacturer: String? = null,

    @ColumnInfo(name = "fingerprint")
    val fingerprint: String? = null,

    @ColumnInfo(name = "bootloader")
    val bootloader: String? = null,

    @ColumnInfo(name = "device_imeis")
    val deviceImeis: String? = null, // JSON string

    // Android Info
    @ColumnInfo(name = "os_version")
    val osVersion: String? = null,

    @ColumnInfo(name = "os_edition")
    val osEdition: String? = null,

    @ColumnInfo(name = "sdk_version")
    val sdkVersion: Int? = null,

    @ColumnInfo(name = "security_patch_level")
    val securityPatchLevel: String? = null,

    // Storage Info
    @ColumnInfo(name = "installed_ram")
    val installedRam: String? = null,

    @ColumnInfo(name = "total_storage")
    val totalStorage: String? = null,

    // Location Info
    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    // Security Info
    @ColumnInfo(name = "is_device_rooted")
    val isDeviceRooted: Boolean? = null,

    @ColumnInfo(name = "is_usb_debugging_enabled")
    val isUsbDebuggingEnabled: Boolean? = null,

    @ColumnInfo(name = "is_developer_mode_enabled")
    val isDeveloperModeEnabled: Boolean? = null,

    @ColumnInfo(name = "is_bootloader_unlocked")
    val isBootloaderUnlocked: Boolean? = null,

    @ColumnInfo(name = "is_custom_rom")
    val isCustomRom: Boolean? = null,

    // System Integrity
    @ColumnInfo(name = "installed_apps_hash")
    val installedAppsHash: String? = null,

    @ColumnInfo(name = "system_properties_hash")
    val systemPropertiesHash: String? = null,

    // Status
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_reason")
    val lockReason: String? = null,

    @ColumnInfo(name = "is_online")
    val isOnline: Boolean = false,

    @ColumnInfo(name = "is_trusted")
    val isTrusted: Boolean = false,

    // Timestamps
    @ColumnInfo(name = "registered_at")
    val registeredAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_online_at")
    val lastOnlineAt: Long? = null,

    // Metadata
    @ColumnInfo(name = "full_data_json")
    val fullDataJson: String? = null, // Complete JSON for reference

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending" // pending, synced, failed
)
