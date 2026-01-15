package com.example.deviceowner.data.local

import androidx.room.*

/**
 * Data Access Object for loan records
 * Handles all database operations for loans
 */
@Dao
interface LoanDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoans(loans: List<LoanEntity>)
    
    @Query("SELECT * FROM loans WHERE loanId = :loanId AND isActive = 1")
    suspend fun getLoanById(loanId: String): LoanEntity?
    
    @Query("SELECT * FROM loans WHERE deviceId = :deviceId AND isActive = 1")
    suspend fun getLoanByDeviceId(deviceId: String): LoanEntity?
    
    @Query("SELECT * FROM loans WHERE userRef = :userRef AND isActive = 1")
    suspend fun getLoansByUserRef(userRef: String): List<LoanEntity>
    
    @Query("SELECT * FROM loans WHERE isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getAllLoans(): List<LoanEntity>
    
    @Query("SELECT * FROM loans WHERE syncStatus = 'PENDING' AND isActive = 1 ORDER BY createdAt ASC")
    suspend fun getPendingSyncLoans(): List<LoanEntity>
    
    @Query("SELECT * FROM loans WHERE loanStatus = :status AND isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getLoansByStatus(status: String): List<LoanEntity>
    
    @Query("SELECT * FROM loans WHERE loanStatus = 'ACTIVE' AND isActive = 1")
    suspend fun getActiveLoans(): List<LoanEntity>
    
    @Query("SELECT * FROM loans WHERE loanStatus = 'OVERDUE' AND isActive = 1")
    suspend fun getOverdueLoans(): List<LoanEntity>
    
    @Update
    suspend fun updateLoan(loan: LoanEntity)
    
    @Query("UPDATE loans SET syncStatus = :syncStatus, lastSyncAttempt = :timestamp WHERE loanId = :loanId")
    suspend fun updateSyncStatus(loanId: String, syncStatus: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE loans SET loanStatus = :status, updatedAt = :timestamp WHERE loanId = :loanId")
    suspend fun updateLoanStatus(loanId: String, status: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE loans SET isActive = 0 WHERE loanId = :loanId")
    suspend fun deactivateLoan(loanId: String)
    
    @Query("DELETE FROM loans WHERE loanId = :loanId")
    suspend fun deleteLoan(loanId: String)
    
    @Query("DELETE FROM loans")
    suspend fun deleteAllLoans()
    
    @Query("SELECT COUNT(*) FROM loans WHERE syncStatus = 'PENDING' AND isActive = 1")
    suspend fun getPendingSyncCount(): Int
}
