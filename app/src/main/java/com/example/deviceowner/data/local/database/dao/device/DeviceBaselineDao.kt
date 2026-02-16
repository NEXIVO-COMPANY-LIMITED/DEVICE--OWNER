package com.example.deviceowner.data.local.database.dao.device

import androidx.room.*
import com.example.deviceowner.data.local.database.entities.device.DeviceBaselineEntity

@Dao
interface DeviceBaselineDao {

    @Query("SELECT * FROM device_baselines WHERE deviceId = :deviceId")
    suspend fun getBaselineByDeviceId(deviceId: String): DeviceBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(baseline: DeviceBaselineEntity)

    @Update
    suspend fun updateBaseline(baseline: DeviceBaselineEntity)

    @Query("DELETE FROM device_baselines WHERE deviceId = :deviceId")
    suspend fun deleteBaseline(deviceId: String)

    @Query("SELECT COUNT(*) FROM device_baselines")
    suspend fun getBaselineCount(): Int
}
