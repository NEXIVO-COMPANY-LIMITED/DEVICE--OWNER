package com.microspace.payo.data.local.database.dao.device

import androidx.room.*
import com.microspace.payo.data.local.database.entities.device.DeviceRegistrationEntity

@Dao
interface DeviceRegistrationDao {

    @Query("SELECT * FROM device_registrations WHERE deviceId = :deviceId")
    suspend fun getRegistrationByDeviceId(deviceId: String): DeviceRegistrationEntity?

    @Query("SELECT * FROM device_registrations WHERE loanNumber = :loanNumber")
    suspend fun getRegistrationByLoanNumber(loanNumber: String): DeviceRegistrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistration(registration: DeviceRegistrationEntity)

    @Update
    suspend fun updateRegistration(registration: DeviceRegistrationEntity)

    @Query("UPDATE device_registrations SET lastSyncAt = :syncTime WHERE deviceId = :deviceId")
    suspend fun updateLastSyncTime(deviceId: String, syncTime: Long)

    @Query("DELETE FROM device_registrations WHERE deviceId = :deviceId")
    suspend fun deleteRegistration(deviceId: String)
}




