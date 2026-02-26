package com.microspace.payo.data.repository

import android.content.Context
import android.util.Log
import com.microspace.payo.data.local.database.DeviceOwnerDatabase
import com.microspace.payo.data.local.database.entities.heartbeat.HeartbeatResponseEntity
import com.microspace.payo.data.models.heartbeat.HeartbeatResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HeartbeatResponseRepository - Manage heartbeat response storage
 * 
 * Handles:
 * - Storing heartbeat responses
 * - Retrieving response history
 * - Querying by type (lock, deactivation, tamper, etc.)
 * - Analytics and statistics
 */
class HeartbeatResponseRepository(context: Context) {
    
    private val TAG = "HeartbeatResponseRepository"
    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val dao = database.heartbeatResponseDao()
    private val gson = Gson()
    
    /**
     * Save a heartbeat response
     */
    suspend fun saveResponse(
        response: HeartbeatResponse,
        heartbeatNumber: Int,
        responseTimeMs: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val entity = HeartbeatResponseEntity(
                heartbeatNumber = heartbeatNumber,
                timestamp = System.currentTimeMillis(),
                serverTime = response.serverTime,
                success = response.success,
                message = response.message,
                
                // Lock status
                isLocked = response.isDeviceLocked(),
                lockReason = response.getLockReason(),
                managementStatus = response.managementStatus,
                shop = response.content?.shop,
                
                // Payment info
                nextPaymentDate = response.getNextPaymentDateTime(),
                unlockPassword = response.nextPayment?.unlockPassword,
                unlockingPassword = response.nextPayment?.unlockingPassword,
                unlockingPasswordMessage = response.actions?.unlocking_password?.message,
                
                // Payment status
                paymentComplete = response.paymentComplete ?: false,
                loanComplete = response.loanComplete ?: false,
                loanStatus = response.loanStatus,
                
                // Actions
                softlockRequested = response.isSoftLockRequested(),
                softlockMessage = response.actions?.reminder?.message,
                hardlockRequested = response.actions?.hardlock ?: false,
                hardlockMessage = response.actions?.unlocking_password?.message,
                reminderMessage = response.actions?.reminder?.message,
                
                // Deactivation
                deactivationRequested = response.isDeactivationRequested(),
                deactivationStatus = response.deactivation?.status,
                deactivationCommand = response.getDeactivationCommand(),
                deactivationReason = response.content?.reason,
                
                // Tamper/Security
                changesDetected = response.changesDetected ?: false,
                changedFields = response.changedFields?.let { gson.toJson(it) },
                
                // Response time
                responseTimeMs = responseTimeMs,
                
                // Full response backup
                fullResponseJson = gson.toJson(response),
                
                // Processing status
                processed = false
            )
            
            val id = dao.insert(entity)
            Log.d(TAG, "✅ Saved heartbeat response #$heartbeatNumber (id=$id)")
            id
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving response: ${e.message}")
            -1L
        }
    }
    
    /**
     * Get latest response
     */
    suspend fun getLatestResponse(): HeartbeatResponseEntity? = withContext(Dispatchers.IO) {
        try {
            dao.getLatest()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting latest response: ${e.message}")
            null
        }
    }
    
    /**
     * Get last N responses
     */
    suspend fun getLastNResponses(limit: Int): List<HeartbeatResponseEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getLatestN(limit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting last $limit responses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all locked responses
     */
    suspend fun getLockedResponses(): List<HeartbeatResponseEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getLockedResponses()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting locked responses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all deactivation responses
     */
    suspend fun getDeactivationResponses(): List<HeartbeatResponseEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getDeactivationResponses()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting deactivation responses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all soft lock responses
     */
    suspend fun getSoftlockResponses(): List<HeartbeatResponseEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getSoftlockResponses()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting softlock responses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all hard lock responses
     */
    suspend fun getHardlockResponses(): List<HeartbeatResponseEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getHardlockResponses()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting hardlock responses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all tamper detection responses
     */
    suspend fun getTamperResponses(): List<HeartbeatResponseEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getTamperResponses()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting tamper responses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get responses with payment info
     */
    suspend fun getResponsesWithPaymentInfo(): List<HeartbeatResponseEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getWithPaymentInfo()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting payment info responses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get responses with unlock password
     */
    suspend fun getResponsesWithUnlockPassword(): List<HeartbeatResponseEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getWithUnlockPassword()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting unlock password responses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Mark response as processed
     */
    suspend fun markAsProcessed(id: Long) = withContext(Dispatchers.IO) {
        try {
            dao.markAsProcessed(id, System.currentTimeMillis())
            Log.d(TAG, "✅ Marked response $id as processed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking response as processed: ${e.message}")
        }
    }
    
    /**
     * Get statistics
     */
    suspend fun getStatistics() = withContext(Dispatchers.IO) {
        try {
            dao.getStatistics()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting statistics: ${e.message}")
            null
        }
    }
    
    /**
     * Delete old responses
     */
    suspend fun deleteOlderThan(beforeTime: Long) = withContext(Dispatchers.IO) {
        try {
            dao.deleteOlderThan(beforeTime)
            Log.d(TAG, "✅ Deleted responses older than $beforeTime")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting old responses: ${e.message}")
        }
    }
    
    /**
     * Clean up old responses (keep last 30 days)
     */
    suspend fun cleanupOldResponses() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        deleteOlderThan(thirtyDaysAgo)
    }
}
