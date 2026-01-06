package com.example.deviceowner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_registrations")
data class DeviceRegistrationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val deviceId: String,
    val loanId: String,
    val imeiList: String, // JSON string
    val serialNumber: String,
    val androidId: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkVersion: String,
    val buildNumber: String,
    val totalStorage: String,
    val installedRam: String,
    val machineName: String,
    val networkOperatorName: String,
    val simOperatorName: String,
    val simState: String,
    val phoneType: String,
    val networkType: String,
    val simSerialNumber: String,
    val batteryCapacity: String,
    val batteryTechnology: String,
    val registrationToken: String? = null,
    val registrationDate: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val isProtected: Boolean = true // Protected from deletion - cannot be deleted by user
)
