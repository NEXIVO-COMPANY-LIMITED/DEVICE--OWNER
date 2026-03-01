package com.microspace.payo.data.local.database.dao.heartbeat

import androidx.room.*
import com.microspace.payo.data.local.database.entities.heartbeat.HeartbeatHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartbeatHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartbeat(heartbeat: HeartbeatHistoryEntity): Long

    @Update
    suspend fun updateHeartbeat(heartbeat: HeartbeatHistoryEntity)

    @Delete
    suspend fun deleteHeartbeat(heartbeat: HeartbeatHistoryEntity)

    @Query("SELECT * FROM heartbeat_history WHERE id = :id")
    suspend fun getHeartbeatById(id: Int): HeartbeatHistoryEntity?

    @Query("SELECT * FROM heartbeat_history WHERE device_data_id = :deviceDataId ORDER BY sent_at DESC")
    suspend fun getHeartbeatsByDeviceId(deviceDataId: Int): List<HeartbeatHistoryEntity>

    @Query("SELECT * FROM heartbeat_history WHERE device_data_id = :deviceDataId ORDER BY sent_at DESC")
    fun getHeartbeatsByDeviceIdFlow(deviceDataId: Int): Flow<List<HeartbeatHistoryEntity>>

    @Query("SELECT * FROM heartbeat_history WHERE device_data_id = :deviceDataId ORDER BY sent_at DESC LIMIT 1")
    suspend fun getLatestHeartbeat(deviceDataId: Int): HeartbeatHistoryEntity?

    @Query("SELECT * FROM heartbeat_history WHERE device_data_id = :deviceDataId ORDER BY sent_at DESC LIMIT 1")
    fun getLatestHeartbeatFlow(deviceDataId: Int): Flow<HeartbeatHistoryEntity?>

    @Query("SELECT * FROM heartbeat_history ORDER BY sent_at DESC")
    suspend fun getAllHeartbeats(): List<HeartbeatHistoryEntity>

    @Query("SELECT * FROM heartbeat_history ORDER BY sent_at DESC")
    fun getAllHeartbeatsFlow(): Flow<List<HeartbeatHistoryEntity>>

    @Query("SELECT * FROM heartbeat_history WHERE mismatches_detected = 1 ORDER BY sent_at DESC")
    suspend fun getHeartbeatsWithMismatches(): List<HeartbeatHistoryEntity>

    @Query("SELECT * FROM heartbeat_history WHERE mismatches_detected = 1 ORDER BY sent_at DESC")
    fun getHeartbeatsWithMismatchesFlow(): Flow<List<HeartbeatHistoryEntity>>

    @Query("SELECT * FROM heartbeat_history WHERE is_locked = 1 ORDER BY sent_at DESC")
    suspend fun getLockedHeartbeats(): List<HeartbeatHistoryEntity>

    @Query("SELECT * FROM heartbeat_history WHERE is_locked = 1 ORDER BY sent_at DESC")
    fun getLockedHeartbeatsFlow(): Flow<List<HeartbeatHistoryEntity>>

    @Query("SELECT * FROM heartbeat_history WHERE sync_status = 'pending' OR sync_status = 'failed'")
    suspend fun getUnsyncedHeartbeats(): List<HeartbeatHistoryEntity>

    @Query("SELECT * FROM heartbeat_history WHERE sync_status = 'pending' OR sync_status = 'failed'")
    fun getUnsyncedHeartbeatsFlow(): Flow<List<HeartbeatHistoryEntity>>

    @Query("UPDATE heartbeat_history SET sync_status = :status, sync_error = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: String, error: String? = null)

    @Query("UPDATE heartbeat_history SET received_at = :timestamp WHERE id = :id")
    suspend fun updateReceivedAt(id: Int, timestamp: Long)

    @Query("UPDATE heartbeat_history SET processed_at = :timestamp WHERE id = :id")
    suspend fun updateProcessedAt(id: Int, timestamp: Long)

    @Query("SELECT COUNT(*) FROM heartbeat_history WHERE device_data_id = :deviceDataId")
    suspend fun countHeartbeats(deviceDataId: Int): Int

    @Query("SELECT COUNT(*) FROM heartbeat_history")
    suspend fun countAllHeartbeats(): Int

    @Query("DELETE FROM heartbeat_history WHERE sent_at < :timestamp")
    suspend fun deleteOldHeartbeats(timestamp: Long)

    @Query("DELETE FROM heartbeat_history")
    suspend fun deleteAllHeartbeats()
}




