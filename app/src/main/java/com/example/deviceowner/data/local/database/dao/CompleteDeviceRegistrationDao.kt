package com.example.deviceowner.data.local.database.dao

import androidx.room.*
import com.example.deviceowner.data.local.database.entities.CompleteDeviceRegistrationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompleteDeviceRegistrationDao {
    
    @Query("SELECT * FROM complete_device_registrations WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getRegistrationByDeviceId(deviceId: String): CompleteDeviceRegistrationEntity?
    
    @Query("SELECT * FROM complete_device_registrations WHERE loanNumber = :loanNumber LIMIT 1")
    suspend fun getRegistrationByLoanNumber(loanNumber: String): CompleteDeviceRegistrationEntity?
    
    @Query("SELECT * FROM complete_device_registrations WHERE registrationStatus = 'SUCCESS' LIMIT 1")
    suspend fun getSuccessfulRegistration(): CompleteDeviceRegistrationEntity?

    /** Most recent in-progress registration (LOAN_NUMBER_SAVED or PENDING) for resuming flow. */
    @Query("SELECT * FROM complete_device_registrations WHERE registrationStatus != 'SUCCESS' ORDER BY registeredAt DESC LIMIT 1")
    suspend fun getMostRecentInProgressRegistration(): CompleteDeviceRegistrationEntity?
    
    @Query("SELECT * FROM complete_device_registrations LIMIT 1")
    suspend fun getAnyRegistration(): CompleteDeviceRegistrationEntity?
    
    @Query("SELECT COUNT(*) FROM complete_device_registrations WHERE registrationStatus = 'SUCCESS'")
    suspend fun hasSuccessfulRegistration(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistration(registration: CompleteDeviceRegistrationEntity)
    
    @Update
    suspend fun updateRegistration(registration: CompleteDeviceRegistrationEntity)
    
    @Query("UPDATE complete_device_registrations SET registrationStatus = :status, serverResponse = :response, lastSyncAt = :syncTime WHERE deviceId = :deviceId")
    suspend fun updateRegistrationStatus(deviceId: String, status: String, response: String?, syncTime: Long)

    /** Update the registration row for this loan with server-assigned device_id and status (used after successful API register). */
    @Query("UPDATE complete_device_registrations SET deviceId = :serverDeviceId, registrationStatus = :status, serverResponse = :response, lastSyncAt = :syncTime WHERE loanNumber = :loanNumber")
    suspend fun updateRegistrationSuccessByLoan(loanNumber: String, serverDeviceId: String, status: String, response: String?, syncTime: Long)
    
    @Query("DELETE FROM complete_device_registrations")
    suspend fun clearAllRegistrations()
    
    @Query("SELECT * FROM complete_device_registrations")
    fun getAllRegistrationsFlow(): Flow<List<CompleteDeviceRegistrationEntity>>
    
    @Query("SELECT * FROM complete_device_registrations")
    suspend fun getAllRegistrations(): List<CompleteDeviceRegistrationEntity>
}