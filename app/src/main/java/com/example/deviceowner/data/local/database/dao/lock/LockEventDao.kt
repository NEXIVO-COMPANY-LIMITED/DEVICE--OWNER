package com.example.deviceowner.data.local.database.dao.lock

import androidx.room.*
import com.example.deviceowner.data.local.database.entities.common.SyncStatus
import com.example.deviceowner.data.local.database.entities.lock.LockEventEntity
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
    suspend fun insertLockEvent(lockEvent: LockEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockEvents(lockEvents: List<LockEventEntity>)

    @Update
    suspend fun updateLockEvent(lockEvent: LockEventEntity)

    @Query("UPDATE lock_events SET syncStatus = :status, lastSyncAttempt = :attemptAt WHERE id = :id")
    suspend fun markAsSynced(id: String, status: SyncStatus = SyncStatus.SYNCED, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE lock_events SET syncStatus = :status, syncAttempts = syncAttempts + 1, lastSyncAttempt = :attemptAt WHERE id = :id")
    suspend fun markAsFailed(id: String, status: SyncStatus = SyncStatus.FAILED, attemptAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM lock_events WHERE syncStatus = :status AND lastSyncAttempt > 0 AND lastSyncAttempt < :beforeTimestamp")
    suspend fun deleteOldSyncedLockEvents(status: SyncStatus = SyncStatus.SYNCED, beforeTimestamp: Long)

    @Query("DELETE FROM lock_events WHERE id = :id")
    suspend fun deleteLockEvent(id: String)
}
