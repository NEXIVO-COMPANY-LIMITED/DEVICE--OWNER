package com.example.deviceowner.data.local.database.dao.sim

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.deviceowner.data.local.database.entities.sim.SimChangeHistoryEntity

@Dao
interface SimChangeHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SimChangeHistoryEntity): Long

    @Query("SELECT * FROM sim_change_history ORDER BY changed_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<SimChangeHistoryEntity>

    @Query("SELECT * FROM sim_change_history ORDER BY changed_at DESC")
    suspend fun getAll(): List<SimChangeHistoryEntity>

    @Query("SELECT COUNT(*) FROM sim_change_history")
    suspend fun getChangeCount(): Int

    @Query("SELECT changed_at FROM sim_change_history ORDER BY changed_at DESC LIMIT 1")
    suspend fun getLastChangeTime(): Long?

    @Query("DELETE FROM sim_change_history WHERE changed_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("DELETE FROM sim_change_history")
    suspend fun deleteAll()
}
