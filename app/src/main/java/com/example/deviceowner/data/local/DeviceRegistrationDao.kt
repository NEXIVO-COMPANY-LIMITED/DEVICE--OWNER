package com.example.deviceowner.data.local

import androidx.room.*

@Dao
interface DeviceRegistrationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistration(registration: DeviceRegistrationEntity): Long
    
    @Query("SELECT * FROM device_registrations WHERE isActive = 1 ORDER BY registrationDate DESC LIMIT 1")
    suspend fun getLatestRegistration(): DeviceRegistrationEntity?
    
    @Query("SELECT * FROM device_registrations WHERE deviceId = :deviceId AND isActive = 1")
    suspend fun getRegistrationByDeviceId(deviceId: String): DeviceRegistrationEntity?
    
    @Query("SELECT * FROM device_registrations WHERE loanId = :shopCode AND isActive = 1")
    suspend fun getRegistrationsByShopCode(shopCode: String): List<DeviceRegistrationEntity>
    
    @Query("SELECT * FROM device_registrations WHERE isActive = 1 ORDER BY registrationDate DESC")
    suspend fun getAllRegistrations(): List<DeviceRegistrationEntity>
    
    @Update
    suspend fun updateRegistration(registration: DeviceRegistrationEntity)
    
    @Query("UPDATE device_registrations SET isActive = 0 WHERE deviceId = :deviceId")
    suspend fun deactivateRegistration(deviceId: String)
    
    @Query("DELETE FROM device_registrations WHERE deviceId = :deviceId")
    suspend fun deleteRegistration(deviceId: String)
    
    @Query("DELETE FROM device_registrations")
    suspend fun deleteAllRegistrations()
    
    // Protected deletion - only delete non-protected registrations
    @Query("DELETE FROM device_registrations WHERE deviceId = :deviceId AND isProtected = 0")
    suspend fun deleteUnprotectedRegistration(deviceId: String)
    
    @Query("DELETE FROM device_registrations WHERE isProtected = 0")
    suspend fun deleteUnprotectedRegistrations()
    
    // Check if any protected registration exists
    @Query("SELECT COUNT(*) FROM device_registrations WHERE isProtected = 1 AND isActive = 1")
    suspend fun hasProtectedRegistration(): Int
}
