package com.microspace.payo.data.local.database.dao.heartbeat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.microspace.payo.data.local.database.entities.heartbeat.HeartbeatResponseEntity
import kotlinx.coroutines.flow.Flow

/**
 * HeartbeatResponseDao - Database access for heartbeat responses
 */
@Dao
interface HeartbeatResponseDao {
    
    /**
     * Insert a new heartbeat response
     */
    @Insert
    suspend fun insert(response: HeartbeatResponseEntity): Long
    
    /**
     * Update an existing heartbeat response
     */
    @Update
    suspend fun update(response: HeartbeatResponseEntity)
    
    /**
     * Get the latest heartbeat response
     */
    @Query("SELECT * FROM heartbeat_responses ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): HeartbeatResponseEntity?
    
    /**
     * Get last N heartbeat responses
     */
    @Query("SELECT * FROM heartbeat_responses ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestN(limit: Int): List<HeartbeatResponseEntity>
    
    /**
     * Get heartbeat responses within time range
     */
    @Query("SELECT * FROM heartbeat_responses WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<HeartbeatResponseEntity>
    
    /**
     * Get all locked responses
     */
    @Query("SELECT * FROM heartbeat_responses WHERE isLocked = 1 ORDER BY timestamp DESC")
    suspend fun getLockedResponses(): List<HeartbeatResponseEntity>
    
    /**
     * Get all deactivation responses
     */
    @Query("SELECT * FROM heartbeat_responses WHERE deactivationRequested = 1 ORDER BY timestamp DESC")
    suspend fun getDeactivationResponses(): List<HeartbeatResponseEntity>
    
    /**
     * Get all soft lock responses
     */
    @Query("SELECT * FROM heartbeat_responses WHERE softlockRequested = 1 ORDER BY timestamp DESC")
    suspend fun getSoftlockResponses(): List<HeartbeatResponseEntity>
    
    /**
     * Get all hard lock responses
     */
    @Query("SELECT * FROM heartbeat_responses WHERE hardlockRequested = 1 ORDER BY timestamp DESC")
    suspend fun getHardlockResponses(): List<HeartbeatResponseEntity>
    
    /**
     * Get all tamper detection responses
     */
    @Query("SELECT * FROM heartbeat_responses WHERE changesDetected = 1 ORDER BY timestamp DESC")
    suspend fun getTamperResponses(): List<HeartbeatResponseEntity>
    
    /**
     * Get unprocessed responses
     */
    @Query("SELECT * FROM heartbeat_responses WHERE processed = 0 ORDER BY timestamp ASC")
    suspend fun getUnprocessed(): List<HeartbeatResponseEntity>
    
    /**
     * Mark response as processed
     */
    @Query("UPDATE heartbeat_responses SET processed = 1, processedAt = :processedAt WHERE id = :id")
    suspend fun markAsProcessed(id: Long, processedAt: Long)
    
    /**
     * Get responses with payment info
     */
    @Query("SELECT * FROM heartbeat_responses WHERE nextPaymentDate IS NOT NULL ORDER BY timestamp DESC")
    suspend fun getWithPaymentInfo(): List<HeartbeatResponseEntity>
    
    /**
     * Get responses with unlock password
     */
    @Query("SELECT * FROM heartbeat_responses WHERE unlockPassword IS NOT NULL OR unlockingPassword IS NOT NULL ORDER BY timestamp DESC")
    suspend fun getWithUnlockPassword(): List<HeartbeatResponseEntity>
    
    /**
     * Get responses by heartbeat number
     */
    @Query("SELECT * FROM heartbeat_responses WHERE heartbeatNumber = :heartbeatNumber")
    suspend fun getByHeartbeatNumber(heartbeatNumber: Int): HeartbeatResponseEntity?
    
    /**
     * Get all responses (flow for real-time updates)
     */
    @Query("SELECT * FROM heartbeat_responses ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<HeartbeatResponseEntity>>
    
    /**
     * Get latest responses (flow)
     */
    @Query("SELECT * FROM heartbeat_responses ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestFlow(limit: Int): Flow<List<HeartbeatResponseEntity>>
    
    /**
     * Delete old responses (older than specified timestamp)
     */
    @Query("DELETE FROM heartbeat_responses WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)
    
    /**
     * Delete all responses
     */
    @Query("DELETE FROM heartbeat_responses")
    suspend fun deleteAll()
    
    /**
     * Get count of responses
     */
    @Query("SELECT COUNT(*) FROM heartbeat_responses")
    suspend fun getCount(): Int
    
    /**
     * Get count of locked responses
     */
    @Query("SELECT COUNT(*) FROM heartbeat_responses WHERE isLocked = 1")
    suspend fun getLockedCount(): Int
    
    /**
     * Get count of deactivation responses
     */
    @Query("SELECT COUNT(*) FROM heartbeat_responses WHERE deactivationRequested = 1")
    suspend fun getDeactivationCount(): Int
    
    /**
     * Get statistics
     */
    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN isLocked = 1 THEN 1 ELSE 0 END) as locked_count,
            SUM(CASE WHEN softlockRequested = 1 THEN 1 ELSE 0 END) as softlock_count,
            SUM(CASE WHEN hardlockRequested = 1 THEN 1 ELSE 0 END) as hardlock_count,
            SUM(CASE WHEN deactivationRequested = 1 THEN 1 ELSE 0 END) as deactivation_count,
            SUM(CASE WHEN changesDetected = 1 THEN 1 ELSE 0 END) as tamper_count,
            AVG(responseTimeMs) as avg_response_time,
            MAX(responseTimeMs) as max_response_time,
            MIN(responseTimeMs) as min_response_time
        FROM heartbeat_responses
    """)
    suspend fun getStatistics(): HeartbeatResponseStats?
}

/**
 * Statistics data class
 */
data class HeartbeatResponseStats(
    val total: Int,
    val locked_count: Int,
    val softlock_count: Int,
    val hardlock_count: Int,
    val deactivation_count: Int,
    val tamper_count: Int,
    val avg_response_time: Double,
    val max_response_time: Long,
    val min_response_time: Long
)




