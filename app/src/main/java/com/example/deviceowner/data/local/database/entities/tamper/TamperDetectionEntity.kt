package com.example.deviceowner.data.local.database.entities.tamper

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tamper_detections")
data class TamperDetectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val tamperType: String,
    val severity: String,
    val detectedAt: Long,
    val details: String?,
    val syncStatus: String = "pending"
)
