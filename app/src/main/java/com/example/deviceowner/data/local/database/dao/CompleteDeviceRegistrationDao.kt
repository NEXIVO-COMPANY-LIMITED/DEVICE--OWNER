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
    
    @Query("SELECT * FROM complete_device_registrations LIMIT 1")
    suspend fun getAnyRegistration(): CompleteDeviceRegistrationEntity?
    
    @Query("SELECT COUNT(*) FROM complete_device_registrations WHERE registrationStatus = 'SUCCESS'")
    suspend fun hasSuccessfulRegistration(): Int
    
    @Query("SELECT loanNumber, nextPaymentDate, totalAmount, paidAmount, remainingAmount FROM complete_device_registrations WHERE registrationStatus = 'SUCCESS' LIMIT 1")
    suspend fun getBasicLoanInfo(): BasicLoanInfo?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistration(registration: CompleteDeviceRegistrationEntity)
    
    @Update
    suspend fun updateRegistration(registration: CompleteDeviceRegistrationEntity)
    
    @Query("UPDATE complete_device_registrations SET registrationStatus = :status, serverResponse = :response, lastSyncAt = :syncTime WHERE deviceId = :deviceId")
    suspend fun updateRegistrationStatus(deviceId: String, status: String, response: String?, syncTime: Long)
    
    @Query("UPDATE complete_device_registrations SET nextPaymentDate = :nextPayment, totalAmount = :total, paidAmount = :paid, remainingAmount = :remaining WHERE deviceId = :deviceId")
    suspend fun updateLoanInfo(deviceId: String, nextPayment: String?, total: Double?, paid: Double?, remaining: Double?)
    
    @Query("DELETE FROM complete_device_registrations")
    suspend fun clearAllRegistrations()
    
    @Query("SELECT * FROM complete_device_registrations")
    fun getAllRegistrationsFlow(): Flow<List<CompleteDeviceRegistrationEntity>>
    
    @Query("SELECT * FROM complete_device_registrations")
    suspend fun getAllRegistrations(): List<CompleteDeviceRegistrationEntity>
}

data class BasicLoanInfo(
    val loanNumber: String,
    val nextPaymentDate: String?,
    val totalAmount: Double?,
    val paidAmount: Double?,
    val remainingAmount: Double?
)