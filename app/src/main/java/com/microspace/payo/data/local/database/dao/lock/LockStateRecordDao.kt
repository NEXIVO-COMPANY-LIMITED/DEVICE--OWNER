package com.microspace.payo.data.local.database.dao.lock

import androidx.room.*
import com.microspace.payo.data.local.database.entities.lock.LockStateRecordEntity

@Dao
interface LockStateRecordDao {

    @Query("SELECT * FROM lock_state_records WHERE lockState = 'hard_lock' AND resolvedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestUnresolvedHardLock(): LockStateRecordEntity?

    @Query("SELECT * FROM lock_state_records ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): LockStateRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: LockStateRecordEntity): Long

    @Update
    suspend fun update(record: LockStateRecordEntity)

    @Query("UPDATE lock_state_records SET resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun markResolved(id: Long, resolvedAt: Long)

    @Query("UPDATE lock_state_records SET resolvedAt = :resolvedAt WHERE id = (SELECT id FROM lock_state_records WHERE lockState = 'hard_lock' AND resolvedAt IS NULL ORDER BY createdAt DESC LIMIT 1)")
    suspend fun markLatestUnresolvedHardLockResolved(resolvedAt: Long)
}




