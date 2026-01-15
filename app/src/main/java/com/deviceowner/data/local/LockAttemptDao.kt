package com.deviceowner.data.local

import androidx.room.*

/**
 * Lock Attempt DAO
 * Database access for lock attempt tracking
 */
@Dao
interface LockAttemptDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: LockAttemptEntity)
    
    @Query("SELECT * FROM lock_attempts WHERE lock_id = :lockId ORDER BY timestamp DESC")
    suspend fun getAttempts(lockId: String): List<LockAttemptEntity>
    
    @Query("SELECT * FROM lock_attempts WHERE lock_id = :lockId AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getRecentAttempts(lockId: String, since: Long): List<LockAttemptEntity>
    
    @Query("SELECT * FROM lock_attempts WHERE lock_id = :lockId AND success = 0 AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getFailedAttempts(lockId: String, since: Long): List<LockAttemptEntity>
    
    @Query("SELECT * FROM lock_attempts WHERE device_id = :deviceId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getDeviceAttempts(deviceId: String, limit: Int = 100): List<LockAttemptEntity>
    
    @Query("SELECT COUNT(*) FROM lock_attempts WHERE lock_id = :lockId")
    suspend fun getAttemptCount(lockId: String): Int
    
    @Query("SELECT COUNT(*) FROM lock_attempts WHERE lock_id = :lockId AND success = 0 AND timestamp >= :since")
    suspend fun getFailedAttemptCount(lockId: String, since: Long): Int
    
    @Query("DELETE FROM lock_attempts WHERE timestamp < :before")
    suspend fun deleteOldAttempts(before: Long)
    
    @Query("DELETE FROM lock_attempts WHERE lock_id = :lockId")
    suspend fun deleteAttemptsForLock(lockId: String)
}

/**
 * Lock Attempt Entity
 * Database table for lock attempts
 */
@Entity(tableName = "lock_attempts")
data class LockAttemptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "lock_id") val lockId: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "attempt_type") val attemptType: String,
    @ColumnInfo(name = "success") val success: Boolean,
    @ColumnInfo(name = "reason") val reason: String?,
    @ColumnInfo(name = "admin_id") val adminId: String?,
    @ColumnInfo(name = "ip_address") val ipAddress: String?,
    @ColumnInfo(name = "user_agent") val userAgent: String?
)
