package com.example.deviceowner.data.local.database.dao

import androidx.room.*
import com.example.deviceowner.data.local.database.entities.HeartbeatHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Heartbeat History DAO - Data Access Object for heartbeat records
 * 
 * Handles all database operations for heartbeat history
 */
@Dao
interface HeartbeatHistoryDao {
    
    /**
     * Insert heartbeat record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartbeat(heartbeat: HeartbeatHistoryEntity): Long
    
    /**
     * Update heartbeat record
     */
    @Update
    suspend fun updateHeartbeat(heartbeat: HeartbeatHistoryEntity)
    
    /**
     * Delete heartbeat record
     */
    @Delete
    suspend fun deleteHeartbeat(heartbeat: HeartbeatHistoryEntity)
    
    /**
     * Get heartbeat by ID
     */
    @Query("SELECT * FROM heartbeat_history WHERE id = :id")
    suspend fun getHeartbeatById(id: Int): HeartbeatHistoryEntity?
    
    /**
     * Get all heartbeats for device
     */
    @Query("SELECT * FROM heartbeat_history WHERE device_data_id = :deviceDataId ORDER BY sent_at DESC")
    suspend fun getHeartbeatsByDeviceId(deviceDataId: Int): List<HeartbeatHistoryEntity>
    
    /**
     * Get all heartbeats for device as Flow
     */
    @Query("SELECT * FROM heartbeat_history WHERE device_data_id = :deviceDataId ORDER BY sent_at DESC")
    fun getHeartbeatsByDeviceIdFlow(deviceDataId: Int): Flow<List<HeartbeatHistoryEntity>>
    
    /**
     * Get latest heartbeat for device
     */
    @Query("SELECT * FROM heartbeat_history WHERE device_data_id = :deviceDataId ORDER BY sent_at DESC LIMIT 1")
    suspend fun getLatestHeartbeat(deviceDataId: Int): HeartbeatHistoryEntity?
    
    /**
     * Get latest heartbeat for device as Flow
     */
    @Query("SELECT * FROM heartbeat_history WHERE device_data_id = :deviceDataId ORDER BY sent_at DESC LIMIT 1")
    fun getLatestHeartbeatFlow(deviceDataId: Int): Flow<HeartbeatHistoryEntity?>
    
    /**
     * Get all heartbeats
     */
    @Query("SELECT * FROM heartbeat_history ORDER BY sent_at DESC")
    suspend fun getAllHeartbeats(): List<HeartbeatHistoryEntity>
    
    /**
     * Get all heartbeats as Flow
     */
    @Query("SELECT * FROM heartbeat_history ORDER BY sent_at DESC")
    fun getAllHeartbeatsFlow(): Flow<List<HeartbeatHistoryEntity>>
    
    /**
     * Get heartbeats with mismatches
     */
    @Query("SELECT * FROM heartbeat_history WHERE mismatches_detected = 1 ORDER BY sent_at DESC")
    suspend fun getHeartbeatsWithMismatches(): List<HeartbeatHistoryEntity>
    
    /**
     * Get heartbeats with mismatches as Flow
     */
    @Query("SELECT * FROM heartbeat_history WHERE mismatches_detected = 1 ORDER BY sent_at DESC")
    fun getHeartbeatsWithMismatchesFlow(): Flow<List<HeartbeatHistoryEntity>>
    
    /**
     * Get locked heartbeats
     */
    @Query("SELECT * FROM heartbeat_history WHERE is_locked = 1 ORDER BY sent_at DESC")
    suspend fun getLockedHeartbeats(): List<HeartbeatHistoryEntity>
    
    /**
     * Get locked heartbeats as Flow
     */
    @Query("SELECT * FROM heartbeat_history WHERE is_locked = 1 ORDER BY sent_at DESC")
    fun getLockedHeartbeatsFlow(): Flow<List<HeartbeatHistoryEntity>>
    
    /**
     * Get unsynced heartbeats
     */
    @Query("SELECT * FROM heartbeat_history WHERE sync_status = 'pending' OR sync_status = 'failed'")
    suspend fun getUnsyncedHeartbeats(): List<HeartbeatHistoryEntity>
    
    /**
     * Get unsynced heartbeats as Flow
     */
    @Query("SELECT * FROM heartbeat_history WHERE sync_status = 'pending' OR sync_status = 'failed'")
    fun getUnsyncedHeartbeatsFlow(): Flow<List<HeartbeatHistoryEntity>>
    
    /**
     * Update sync status
     */
    @Query("UPDATE heartbeat_history SET sync_status = :status, sync_error = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: String, error: String? = null)
    
    /**
     * Update received timestamp
     */
    @Query("UPDATE heartbeat_history SET received_at = :timestamp WHERE id = :id")
    suspend fun updateReceivedAt(id: Int, timestamp: Long)
    
    /**
     * Update processed timestamp
     */
    @Query("UPDATE heartbeat_history SET processed_at = :timestamp WHERE id = :id")
    suspend fun updateProcessedAt(id: Int, timestamp: Long)
    
    /**
     * Count heartbeats for device
     */
    @Query("SELECT COUNT(*) FROM heartbeat_history WHERE device_data_id = :deviceDataId")
    suspend fun countHeartbeats(deviceDataId: Int): Int
    
    /**
     * Count all heartbeats
     */
    @Query("SELECT COUNT(*) FROM heartbeat_history")
    suspend fun countAllHeartbeats(): Int
    
    /**
     * Delete heartbeats older than timestamp
     */
    @Query("DELETE FROM heartbeat_history WHERE sent_at < :timestamp")
    suspend fun deleteOldHeartbeats(timestamp: Long)
    
    /**
     * Delete all heartbeats
     */
    @Query("DELETE FROM heartbeat_history")
    suspend fun deleteAllHeartbeats()
}
