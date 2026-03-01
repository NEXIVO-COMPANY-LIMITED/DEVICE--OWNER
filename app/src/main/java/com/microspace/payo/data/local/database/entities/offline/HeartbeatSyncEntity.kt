package com.microspace.payo.data.local.database.entities.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local DB record for each heartbeat. Saved when heartbeat is sent (or attempted).
 * When device is offline, record stays PENDING; when back online sync sends
 * the last 5 pending heartbeats with their exact recorded times.
 */
@Entity(
    tableName = "heartbeat_sync",
    indices = [Index(value = ["syncStatus"]), Index(value = ["recordedAt"])]
)
data class HeartbeatSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    /** Full HeartbeatRequest as JSON (includes heartbeat_timestamp for server). */
    val payloadJson: String,
    /** Device time when this heartbeat was recorded (millis). */
    val recordedAt: Long,
    /** ISO-8601 timestamp from device (same as in payload) for exact time. */
    val heartbeatTimestampIso: String?,
    /** PENDING = not yet sent to server; SYNCED = sent successfully. */
    val syncStatus: String = "PENDING"
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCED = "SYNCED"
    }
}




