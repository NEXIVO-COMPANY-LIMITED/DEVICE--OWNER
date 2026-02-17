package com.example.deviceowner.data.local.database.dao.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.deviceowner.data.local.database.entities.offline.HeartbeatSyncEntity

@Dao
interface HeartbeatSyncDao {

    @Insert
    suspend fun insert(entity: HeartbeatSyncEntity): Long

    @Update
    suspend fun update(entity: HeartbeatSyncEntity)

    @Query("UPDATE heartbeat_sync SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    /** Last 5 PENDING heartbeats by recorded time (oldest first for correct order when sending). */
    @Query("SELECT * FROM heartbeat_sync WHERE syncStatus = 'PENDING' ORDER BY recordedAt DESC LIMIT 5")
    suspend fun getLast5Pending(): List<HeartbeatSyncEntity>

    /** All PENDING heartbeats (oldest first). */
    @Query("SELECT * FROM heartbeat_sync WHERE syncStatus = 'PENDING' ORDER BY recordedAt ASC")
    suspend fun getAllPending(): List<HeartbeatSyncEntity>

    @Query("SELECT COUNT(*) FROM heartbeat_sync WHERE syncStatus = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("DELETE FROM heartbeat_sync WHERE syncStatus = 'SYNCED' AND recordedAt < :beforeMillis")
    suspend fun deleteSyncedOlderThan(beforeMillis: Long)

    @Query("DELETE FROM heartbeat_sync")
    suspend fun deleteAll()
}
