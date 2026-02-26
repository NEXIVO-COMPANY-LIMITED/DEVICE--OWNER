package com.microspace.payo.data.local.database.entities.heartbeat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.microspace.payo.data.local.database.entities.device.DeviceDataEntity

/**
 * Heartbeat History Entity - Stores heartbeat records locally
 *
 * Stores each heartbeat sent to the server for offline access
 * and comparison history
 */
@Entity(
    tableName = "heartbeat_history",
    foreignKeys = [
        ForeignKey(
            entity = DeviceDataEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_data_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["device_data_id"])
    ]
)
data class HeartbeatHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    // Foreign Key
    @ColumnInfo(name = "device_data_id")
    val deviceDataId: Int,

    // Heartbeat Data
    @ColumnInfo(name = "heartbeat_data_json")
    val heartbeatDataJson: String, // Complete heartbeat data as JSON

    // Comparison Results
    @ColumnInfo(name = "comparison_result_json")
    val comparisonResultJson: String? = null, // Comparison details

    @ColumnInfo(name = "mismatches_detected")
    val mismatchesDetected: Boolean = false,

    @ColumnInfo(name = "high_severity_count")
    val highSeverityCount: Int = 0,

    @ColumnInfo(name = "medium_severity_count")
    val mediumSeverityCount: Int = 0,

    @ColumnInfo(name = "total_mismatches")
    val totalMismatches: Int = 0,

    // Lock Status
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_reason")
    val lockReason: String? = null,

    @ColumnInfo(name = "auto_locked")
    val autoLocked: Boolean = false,

    // Mismatches Details
    @ColumnInfo(name = "mismatches_json")
    val mismatchesJson: String? = null, // Array of mismatches

    // Server Response
    @ColumnInfo(name = "server_response_json")
    val serverResponseJson: String? = null, // Complete server response

    @ColumnInfo(name = "server_heartbeat_history_id")
    val serverHeartbeatHistoryId: Int? = null,

    // Status
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending", // pending, synced, failed

    @ColumnInfo(name = "sync_error")
    val syncError: String? = null,

    // Timestamps
    @ColumnInfo(name = "sent_at")
    val sentAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "received_at")
    val receivedAt: Long? = null,

    @ColumnInfo(name = "processed_at")
    val processedAt: Long? = null
)
