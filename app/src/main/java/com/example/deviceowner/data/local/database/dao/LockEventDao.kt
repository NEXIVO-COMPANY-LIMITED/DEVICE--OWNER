package com.example.deviceowner.data.local.database.dao

import androidx.room.*
import com.example.deviceowner.data.local.database.entities.LockEventEntity
import com.example.deviceowner.data.local.database.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LockEventDao {
    
    @Query("SELECT * FROM lock_events WHERE syncStatus = :status ORDER BY occurredAt ASC LIMIT :limit")
    suspend fun getPendingLockEvents(status: SyncStatus = SyncStatus.PENDING, limit: Int = 50): List<LockEventEntity>
    
    @Query("SELECT * FROM lock_events WHERE deviceId = :deviceId ORDER BY occurredAt DESC LIMIT :limit")
    fun getRecentLockEvents(deviceId: String, limit: Int = 100): Flow<List<LockEventEntity>>
    
    @Query("SELECT COUNT(*) FROM lock_events WHERE syncStatus = :status")
    suspend fun getPendingCount(status: SyncStatus = SyncStatus.PENDING): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockEvent(lockEvent: LockEventEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockEvents(lockEvents: List<LockEventEntity>)
    
    @Update
    suspend fun updateLockEvent(lockEvent: LockEventEntity)
    
    @Query("UPDATE lock_events SET syncStatus = :status, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markAsSynced(id: Long, status: SyncStatus = SyncStatus.SYNCED, syncedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE lock_events SET syncStatus = :status, retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markAsFailed(id: Long, status: SyncStatus = SyncStatus.FAILED, error: String?)
    
    @Query("DELETE FROM lock_events WHERE syncStatus = :status AND syncedAt > 0 AND syncedAt < :beforeTimestamp")
    suspend fun deleteOldSyncedLockEvents(status: SyncStatus = SyncStatus.SYNCED, beforeTimestamp: Long)
    
    @Query("DELETE FROM lock_events WHERE id = :id")
    suspend fun deleteLockEvent(id: Long)
}
