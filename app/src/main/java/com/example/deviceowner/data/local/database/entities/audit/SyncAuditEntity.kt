package com.microspace.payo.data.local.database.entities.audit

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records every heartbeat sync action taken by HeartbeatResponseHandler_v2.
 * This creates a detailed audit trail for debugging device state issues.
 */
@Entity(tableName = "sync_audit_log")
data class SyncAuditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Server state from response
    val serverState: String, // e.g., "LOCKED", "UNLOCKED", "SOFT_LOCK", "DEACTIVATION"
    
    // Device state
    val deviceStateBefore: String,
    val deviceStateAfter: String,

    // Action taken
    val actionTaken: String, // e.g., "APPLY_HARD_LOCK", "TRIGGER_UNLOCK", "NO_CHANGE"
    
    // Additional details
    val details: String,
    val lockReason: String? = null,
    
    val success: Boolean = true
)
