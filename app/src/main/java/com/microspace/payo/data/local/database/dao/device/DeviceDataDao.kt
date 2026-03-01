package com.microspace.payo.data.local.database.dao.device

import androidx.room.*
import com.microspace.payo.data.local.database.entities.device.DeviceDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceData(deviceData: DeviceDataEntity): Long

    @Update
    suspend fun updateDeviceData(deviceData: DeviceDataEntity)

    @Delete
    suspend fun deleteDeviceData(deviceData: DeviceDataEntity)

    @Query("SELECT * FROM device_data WHERE id = :id")
    suspend fun getDeviceDataById(id: Int): DeviceDataEntity?

    @Query("SELECT * FROM device_data WHERE server_device_id = :serverDeviceId")
    suspend fun getDeviceDataByServerDeviceId(serverDeviceId: String): DeviceDataEntity?

    @Query("SELECT * FROM device_data WHERE loan_number = :loanNumber")
    suspend fun getDeviceDataByLoanNumber(loanNumber: String): DeviceDataEntity?

    @Query("SELECT * FROM device_data ORDER BY registered_at DESC")
    suspend fun getAllDeviceData(): List<DeviceDataEntity>

    @Query("SELECT * FROM device_data ORDER BY registered_at DESC")
    fun getAllDeviceDataFlow(): Flow<List<DeviceDataEntity>>

    @Query("SELECT * FROM device_data ORDER BY registered_at DESC LIMIT 1")
    suspend fun getLatestDeviceData(): DeviceDataEntity?

    @Query("SELECT * FROM device_data ORDER BY registered_at DESC LIMIT 1")
    fun getLatestDeviceDataFlow(): Flow<DeviceDataEntity?>

    @Query("SELECT * FROM device_data WHERE is_locked = 1 ORDER BY registered_at DESC")
    suspend fun getLockedDevices(): List<DeviceDataEntity>

    @Query("SELECT * FROM device_data WHERE is_locked = 1 ORDER BY registered_at DESC")
    fun getLockedDevicesFlow(): Flow<List<DeviceDataEntity>>

    @Query("SELECT * FROM device_data WHERE sync_status = 'pending' OR sync_status = 'failed'")
    suspend fun getUnsyncedDeviceData(): List<DeviceDataEntity>

    @Query("UPDATE device_data SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: String)

    @Query("UPDATE device_data SET is_locked = :isLocked, lock_reason = :reason WHERE id = :id")
    suspend fun updateLockStatus(id: Int, isLocked: Boolean, reason: String?)

    @Query("UPDATE device_data SET is_online = :isOnline, last_online_at = :timestamp WHERE id = :id")
    suspend fun updateOnlineStatus(id: Int, isOnline: Boolean, timestamp: Long)

    @Query("UPDATE device_data SET server_device_id = :serverDeviceId WHERE id = :id")
    suspend fun updateServerDeviceId(id: Int, serverDeviceId: String)

    @Query("SELECT COUNT(*) FROM device_data")
    suspend fun countDeviceData(): Int

    @Query("DELETE FROM device_data")
    suspend fun deleteAllDeviceData()
}




