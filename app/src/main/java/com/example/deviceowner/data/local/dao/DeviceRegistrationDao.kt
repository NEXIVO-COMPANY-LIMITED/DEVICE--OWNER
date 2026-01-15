package com.example.deviceowner.data.local

import androidx.room.*

@Dao
interface DeviceRegistrationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistration(registration: DeviceRegistrationEntity): Long
    
    @Query("SELECT * FROM device_registrations WHERE isActive = 1 ORDER BY registrationDate DESC LIMIT 1")
    suspend fun getLatestRegistration(): DeviceRegistrationEntity?
    
    @Query("SELECT * FROM device_registrations WHERE device_id = :deviceId AND isActive = 1")
    suspend fun getRegistrationByDeviceId(deviceId: String): DeviceRegistrationEntity?
    
    @Query("SELECT * FROM device_registrations WHERE loan_number = :loanNumber AND isActive = 1")
    suspend fun getRegistrationsByLoanNumber(loanNumber: String): List<DeviceRegistrationEntity>
    
    @Query("SELECT * FROM device_registrations WHERE isActive = 1 ORDER BY registrationDate DESC")
    suspend fun getAllRegistrations(): List<DeviceRegistrationEntity>
    
    @Update
    suspend fun updateRegistration(registration: DeviceRegistrationEntity)
    
    @Query("UPDATE device_registrations SET isActive = 0 WHERE device_id = :deviceId")
    suspend fun deactivateRegistration(deviceId: String)
    
    @Query("DELETE FROM device_registrations WHERE device_id = :deviceId")
    suspend fun deleteRegistration(deviceId: String)
    
    @Query("DELETE FROM device_registrations")
    suspend fun deleteAllRegistrations()
    
    @Query("DELETE FROM device_registrations WHERE device_id = :deviceId AND isProtected = 0")
    suspend fun deleteUnprotectedRegistration(deviceId: String)
    
    @Query("DELETE FROM device_registrations WHERE isProtected = 0")
    suspend fun deleteUnprotectedRegistrations()
    
    @Query("SELECT COUNT(*) FROM device_registrations WHERE isProtected = 1 AND isActive = 1")
    suspend fun hasProtectedRegistration(): Int
}
