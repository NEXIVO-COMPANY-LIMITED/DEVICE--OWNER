package com.example.deviceowner.data.local.database.dao

import androidx.room.*
import com.example.deviceowner.data.local.database.entities.DeviceDataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Device Data DAO - Data Access Object for device data
 * 
 * Handles all database operations for device registration data
 */
@Dao
interface DeviceDataDao {
    
    /**
     * Insert device data
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceData(deviceData: DeviceDataEntity): Long
    
    /**
     * Update device data
     */
    @Update
    suspend fun updateDeviceData(deviceData: DeviceDataEntity)
    
    /**
     * Delete device data
     */
    @Delete
    suspend fun deleteDeviceData(deviceData: DeviceDataEntity)
    
    /**
     * Get device data by ID
     */
    @Query("SELECT * FROM device_data WHERE id = :id")
    suspend fun getDeviceDataById(id: Int): DeviceDataEntity?
    
    /**
     * Get device data by server device ID
     */
    @Query("SELECT * FROM device_data WHERE server_device_id = :serverDeviceId")
    suspend fun getDeviceDataByServerDeviceId(serverDeviceId: String): DeviceDataEntity?
    
    /**
     * Get device data by loan number
     */
    @Query("SELECT * FROM device_data WHERE loan_number = :loanNumber")
    suspend fun getDeviceDataByLoanNumber(loanNumber: String): DeviceDataEntity?
    
    /**
     * Get all device data
     */
    @Query("SELECT * FROM device_data ORDER BY registered_at DESC")
    suspend fun getAllDeviceData(): List<DeviceDataEntity>
    
    /**
     * Get all device data as Flow (for real-time updates)
     */
    @Query("SELECT * FROM device_data ORDER BY registered_at DESC")
    fun getAllDeviceDataFlow(): Flow<List<DeviceDataEntity>>
    
    /**
     * Get latest device data
     */
    @Query("SELECT * FROM device_data ORDER BY registered_at DESC LIMIT 1")
    suspend fun getLatestDeviceData(): DeviceDataEntity?
    
    /**
     * Get latest device data as Flow
     */
    @Query("SELECT * FROM device_data ORDER BY registered_at DESC LIMIT 1")
    fun getLatestDeviceDataFlow(): Flow<DeviceDataEntity?>
    
    /**
     * Get locked devices
     */
    @Query("SELECT * FROM device_data WHERE is_locked = 1 ORDER BY registered_at DESC")
    suspend fun getLockedDevices(): List<DeviceDataEntity>
    
    /**
     * Get locked devices as Flow
     */
    @Query("SELECT * FROM device_data WHERE is_locked = 1 ORDER BY registered_at DESC")
    fun getLockedDevicesFlow(): Flow<List<DeviceDataEntity>>
    
    /**
     * Get unsynced device data
     */
    @Query("SELECT * FROM device_data WHERE sync_status = 'pending' OR sync_status = 'failed'")
    suspend fun getUnsyncedDeviceData(): List<DeviceDataEntity>
    
    /**
     * Update sync status
     */
    @Query("UPDATE device_data SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: String)
    
    /**
     * Update lock status
     */
    @Query("UPDATE device_data SET is_locked = :isLocked, lock_reason = :reason WHERE id = :id")
    suspend fun updateLockStatus(id: Int, isLocked: Boolean, reason: String?)
    
    /**
     * Update online status
     */
    @Query("UPDATE device_data SET is_online = :isOnline, last_online_at = :timestamp WHERE id = :id")
    suspend fun updateOnlineStatus(id: Int, isOnline: Boolean, timestamp: Long)
    
    /**
     * Update server device ID
     */
    @Query("UPDATE device_data SET server_device_id = :serverDeviceId WHERE id = :id")
    suspend fun updateServerDeviceId(id: Int, serverDeviceId: String)
    
    /**
     * Count all device data
     */
    @Query("SELECT COUNT(*) FROM device_data")
    suspend fun countDeviceData(): Int
    
    /**
     * Delete all device data
     */
    @Query("DELETE FROM device_data")
    suspend fun deleteAllDeviceData()
}
