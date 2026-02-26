package com.microspace.payo.data.models.heartbeat

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.microspace.payo.data.db.converters.StringListConverter

@Entity(tableName = "heartbeats")
@TypeConverters(StringListConverter::class)
data class Heartbeat(
    @PrimaryKey
    val heartbeatTimestamp: String,
    val serialNumber: String?,
    val model: String?,
    val manufacturer: String?,
    val androidId: String?,
    val deviceImeis: List<String>?,
    val installedRam: String?,
    val totalStorage: String?,
    val latitude: Double?,
    val longitude: Double?,
    val isDeviceRooted: Boolean?,
    val isUsbDebuggingEnabled: Boolean?,
    val isDeveloperModeEnabled: Boolean?,
    val isBootloaderUnlocked: Boolean?,
    val isCustomRom: Boolean?,
    val sdkVersion: Int?,
    val osVersion: String?,
    val securityPatchLevel: String?,
    val batteryLevel: Int?,
    val language: String?,
    val deviceFingerprint: String?,
    val bootloader: String?
)
