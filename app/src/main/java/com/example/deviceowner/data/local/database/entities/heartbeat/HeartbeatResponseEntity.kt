package com.microspace.payo.data.local.database.entities.heartbeat

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * HeartbeatResponseEntity - Store complete heartbeat responses locally
 * 
 * Stores all heartbeat response data for:
 * - Offline access
 * - Historical tracking
 * - Debugging
 * - Analytics
 */
@Entity(tableName = "heartbeat_responses")
data class HeartbeatResponseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Response metadata
    val heartbeatNumber: Int,
    val timestamp: Long,  // When response was received
    val serverTime: String? = null,
    
    // Success/Message
    val success: Boolean = false,
    val message: String? = null,
    
    // Lock status
    val isLocked: Boolean = false,
    val lockReason: String? = null,
    val managementStatus: String? = null,  // "locked", "normal", etc.
    val shop: String? = null,
    
    // Payment info
    val nextPaymentDate: String? = null,
    val unlockPassword: String? = null,
    val unlockingPassword: String? = null,
    val unlockingPasswordMessage: String? = null,
    
    // Payment status
    val paymentComplete: Boolean = false,
    val loanComplete: Boolean = false,
    val loanStatus: String? = null,  // "completed", "paid", "active", etc.
    
    // Actions
    val softlockRequested: Boolean = false,
    val softlockMessage: String? = null,
    val hardlockRequested: Boolean = false,
    val hardlockMessage: String? = null,
    val reminderMessage: String? = null,
    
    // Deactivation
    val deactivationRequested: Boolean = false,
    val deactivationStatus: String? = null,  // "requested", "active", etc.
    val deactivationCommand: String? = null,
    val deactivationReason: String? = null,
    
    // Tamper/Security
    val changesDetected: Boolean = false,
    val changedFields: String? = null,  // JSON array as string
    
    // Response time metrics
    val responseTimeMs: Long = 0,
    
    // Full response JSON (for backup)
    val fullResponseJson: String? = null,
    
    // Processing status
    val processed: Boolean = false,
    val processedAt: Long? = null
)
