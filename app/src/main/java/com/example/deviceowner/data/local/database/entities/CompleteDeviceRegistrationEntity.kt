package com.example.deviceowner.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "complete_device_registrations")
data class CompleteDeviceRegistrationEntity(
    @PrimaryKey
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
    val serverResponse: String? = null, // JSON response from server
    
    // Loan information (basic info for main screen)
    val nextPaymentDate: String? = null,
    val totalAmount: Double? = null,
    val paidAmount: Double? = null,
    val remainingAmount: Double? = null
)