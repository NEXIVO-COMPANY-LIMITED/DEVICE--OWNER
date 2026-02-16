package com.example.deviceowner.data.local.database.entities.device

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_baselines")
data class DeviceBaselineEntity(
    @PrimaryKey
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val securityPatchLevel: String?,
    val isRooted: Boolean,
    val hasCustomRom: Boolean,
    val baselineCreatedAt: Long,
    val lastUpdatedAt: Long
)
