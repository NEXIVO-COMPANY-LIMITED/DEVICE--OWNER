package com.microspace.payo.data.local.database.entities.device

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "complete_device_registrations",
    indices = [
        Index(value = ["deviceId"], unique = true),
        Index(value = ["loanNumber"], unique = true)
    ]
)
data class CompleteDeviceRegistrationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val loanNumber: String,
    val manufacturer: String,
    val model: String,
    val serialNumber: String?,
    val androidId: String?,
    val deviceImeis: String?, // JSON string of IMEI list
    val osVersion: String?,
    val sdkVersion: Int?,
    val buildNumber: Int?,
    val securityPatchLevel: String?,
    val bootloader: String?,
    val installedRam: String?,
    val totalStorage: String?,
    val language: String?,
    val deviceFingerprint: String?,
    val systemUptime: Long?,
    val installedAppsHash: String?,
    val systemPropertiesHash: String?,

    // Security data (hidden from user but stored)
    val isDeviceRooted: Boolean?,
    val isUsbDebuggingEnabled: Boolean?,
    val isDeveloperModeEnabled: Boolean?,
    val isBootloaderUnlocked: Boolean?,
    val isCustomRom: Boolean?,
    val tamperSeverity: String?,
    val tamperFlags: String?,

    // Location data
    val latitude: Double?,
    val longitude: Double?,

    // Registration metadata
    val registrationStatus: String, // "PENDING", "SUCCESS", "FAILED"
    val registeredAt: Long,
    val lastSyncAt: Long? = null,
    val serverResponse: String? = null // JSON response from server
)




