package com.example.deviceowner.data.local

import androidx.room.*

/**
 * Data Access Object for device lock records
 * Handles all database operations for locks
 */
@Dao
interface DeviceLockDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLock(lock: DeviceLockEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocks(locks: List<DeviceLockEntity>)
    
    @Query("SELECT * FROM device_locks WHERE lockId = :lockId AND isActive = 1")
    suspend fun getLockById(lockId: String): DeviceLockEntity?
    
    @Query("SELECT * FROM device_locks WHERE deviceId = :deviceId AND lockStatus = 'ACTIVE' AND isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveLockByDeviceId(deviceId: String): DeviceLockEntity?
    
    @Query("SELECT * FROM device_locks WHERE deviceId = :deviceId AND isActive = 1 ORDER BY createdAt DESC")
    suspend fun getLocksByDeviceId(deviceId: String): List<DeviceLockEntity>
    
    @Query("SELECT * FROM device_locks WHERE lockType = :lockType AND isActive = 1 ORDER BY createdAt DESC")
    suspend fun getLocksByType(lockType: String): List<DeviceLockEntity>
    
    @Query("SELECT * FROM device_locks WHERE lockReason = :reason AND isActive = 1 ORDER BY createdAt DESC")
    suspend fun getLocksByReason(reason: String): List<DeviceLockEntity>
    
    @Query("SELECT * FROM device_locks WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getAllLocks(): List<DeviceLockEntity>
    
    @Query("SELECT * FROM device_locks WHERE syncStatus = 'PENDING' AND isActive = 1 ORDER BY createdAt ASC")
    suspend fun getPendingSyncLocks(): List<DeviceLockEntity>
    
    @Query("SELECT * FROM device_locks WHERE lockStatus = 'ACTIVE' AND isActive = 1 ORDER BY createdAt DESC")
    suspend fun getActiveLocks(): List<DeviceLockEntity>
    
    @Query("SELECT * FROM device_locks WHERE lockStatus = 'ACTIVE' AND expiresAt < :currentTime AND isActive = 1")
    suspend fun getExpiredLocks(currentTime: Long = System.currentTimeMillis()): List<DeviceLockEntity>
    
    @Update
    suspend fun updateLock(lock: DeviceLockEntity)
    
    @Query("UPDATE device_locks SET lockStatus = :status, updatedAt = :timestamp WHERE lockId = :lockId")
    suspend fun updateLockStatus(lockId: String, status: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE device_locks SET syncStatus = :syncStatus, lastSyncAttempt = :timestamp WHERE lockId = :lockId")
    suspend fun updateSyncStatus(lockId: String, syncStatus: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE device_locks SET isActive = 0 WHERE lockId = :lockId")
    suspend fun deactivateLock(lockId: String)
    
    @Query("DELETE FROM device_locks WHERE lockId = :lockId")
    suspend fun deleteLock(lockId: String)
    
    @Query("DELETE FROM device_locks WHERE deviceId = :deviceId")
    suspend fun deleteDeviceLocks(deviceId: String)
    
    @Query("DELETE FROM device_locks")
    suspend fun deleteAllLocks()
    
    @Query("SELECT COUNT(*) FROM device_locks WHERE syncStatus = 'PENDING' AND isActive = 1")
    suspend fun getPendingSyncCount(): Int
    
    @Query("SELECT COUNT(*) FROM device_locks WHERE lockStatus = 'ACTIVE' AND deviceId = :deviceId AND isActive = 1")
    suspend fun getActiveLocksCount(deviceId: String): Int
}
