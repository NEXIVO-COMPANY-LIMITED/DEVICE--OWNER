package com.example.deviceowner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.deviceowner.data.models.heartbeat.Heartbeat
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartbeatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartbeat(heartbeat: Heartbeat)

    @Query("SELECT * FROM heartbeats ORDER BY heartbeatTimestamp DESC")
    fun getAllHeartbeats(): Flow<List<Heartbeat>>

    @Query("SELECT * FROM heartbeats ORDER BY heartbeatTimestamp DESC LIMIT :limit")
    fun getLatestHeartbeats(limit: Int): Flow<List<Heartbeat>>

    @Query("DELETE FROM heartbeats")
    suspend fun deleteAll()
}
