package com.microspace.payo.data.local.database.dao.heartbeat

import androidx.room.*
import com.microspace.payo.data.local.database.entities.heartbeat.HeartbeatEntity

@Dao
interface HeartbeatDao {

    @Query("SELECT * FROM heartbeats WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    suspend fun getHeartbeatsByDeviceId(deviceId: String): List<HeartbeatEntity>

    @Query("SELECT * FROM heartbeats WHERE syncStatus = 'pending' ORDER BY timestamp ASC")
    suspend fun getPendingSyncHeartbeats(): List<HeartbeatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartbeat(heartbeat: HeartbeatEntity): Long

    @Update
    suspend fun updateHeartbeat(heartbeat: HeartbeatEntity)

    @Query("UPDATE heartbeats SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("DELETE FROM heartbeats WHERE timestamp < :cutoffTime")
    suspend fun deleteOldHeartbeats(cutoffTime: Long)
}
