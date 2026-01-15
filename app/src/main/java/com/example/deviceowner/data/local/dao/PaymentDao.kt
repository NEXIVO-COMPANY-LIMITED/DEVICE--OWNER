package com.example.deviceowner.data.local

import androidx.room.*

/**
 * Data Access Object for payment records
 * Handles all database operations for payments
 */
@Dao
interface PaymentDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)
    
    @Query("SELECT * FROM payments WHERE paymentId = :paymentId AND isActive = 1")
    suspend fun getPaymentById(paymentId: String): PaymentEntity?
    
    @Query("SELECT * FROM payments WHERE loanId = :loanId AND isActive = 1")
    suspend fun getPaymentsByLoanId(loanId: String): List<PaymentEntity>
    
    @Query("SELECT * FROM payments WHERE deviceId = :deviceId AND isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getPaymentsByDeviceId(deviceId: String): List<PaymentEntity>
    
    @Query("SELECT * FROM payments WHERE isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getAllPayments(): List<PaymentEntity>
    
    @Query("SELECT * FROM payments WHERE syncStatus = 'PENDING' AND isActive = 1 ORDER BY createdAt ASC")
    suspend fun getPendingSyncPayments(): List<PaymentEntity>
    
    @Query("SELECT * FROM payments WHERE paymentStatus = :status AND isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getPaymentsByStatus(status: String): List<PaymentEntity>
    
    @Update
    suspend fun updatePayment(payment: PaymentEntity)
    
    @Query("UPDATE payments SET syncStatus = :syncStatus, lastSyncAttempt = :timestamp WHERE paymentId = :paymentId")
    suspend fun updateSyncStatus(paymentId: String, syncStatus: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE payments SET isActive = 0 WHERE paymentId = :paymentId")
    suspend fun deactivatePayment(paymentId: String)
    
    @Query("DELETE FROM payments WHERE paymentId = :paymentId")
    suspend fun deletePayment(paymentId: String)
    
    @Query("DELETE FROM payments WHERE loanId = :loanId")
    suspend fun deletePaymentsByLoanId(loanId: String)
    
    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()
    
    @Query("SELECT COUNT(*) FROM payments WHERE syncStatus = 'PENDING' AND isActive = 1")
    suspend fun getPendingSyncCount(): Int
}
