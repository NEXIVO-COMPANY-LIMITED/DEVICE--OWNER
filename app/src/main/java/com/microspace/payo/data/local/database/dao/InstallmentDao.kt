package com.microspace.payo.data.local.database.dao

import androidx.room.*
import com.microspace.payo.data.local.database.entities.payment.InstallmentEntity

@Dao
interface InstallmentDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(installments: List<InstallmentEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(installment: InstallmentEntity)
    
    @Query("SELECT * FROM installments WHERE deviceId = :deviceId ORDER BY installmentNumber ASC")
    suspend fun getInstallmentsByDeviceId(deviceId: String): List<InstallmentEntity>
    
    @Query("SELECT * FROM installments WHERE loanNumber = :loanNumber ORDER BY installmentNumber ASC")
    suspend fun getInstallmentsByLoanNumber(loanNumber: String): List<InstallmentEntity>
    
    @Query("SELECT * FROM installments WHERE deviceId = :deviceId AND status = 'pending' ORDER BY installmentNumber ASC LIMIT 1")
    suspend fun getNextPendingInstallment(deviceId: String): InstallmentEntity?
    
    @Query("SELECT * FROM installments WHERE deviceId = :deviceId AND status IN ('pending', 'partial', 'overdue') ORDER BY installmentNumber ASC")
    suspend fun getUnpaidInstallments(deviceId: String): List<InstallmentEntity>
    
    @Query("SELECT * FROM installments WHERE deviceId = :deviceId AND status = 'paid' ORDER BY installmentNumber ASC")
    suspend fun getPaidInstallments(deviceId: String): List<InstallmentEntity>
    
    @Query("SELECT * FROM installments WHERE deviceId = :deviceId AND status = 'overdue' ORDER BY installmentNumber ASC")
    suspend fun getOverdueInstallments(deviceId: String): List<InstallmentEntity>
    
    @Query("SELECT SUM(amountDue) FROM installments WHERE deviceId = :deviceId")
    suspend fun getTotalLoanAmount(deviceId: String): Double?
    
    @Query("SELECT SUM(amountPaid) FROM installments WHERE deviceId = :deviceId")
    suspend fun getTotalAmountPaid(deviceId: String): Double?
    
    @Query("SELECT COUNT(*) FROM installments WHERE deviceId = :deviceId AND status = 'paid'")
    suspend fun getPaidInstallmentCount(deviceId: String): Int
    
    @Query("SELECT COUNT(*) FROM installments WHERE deviceId = :deviceId")
    suspend fun getTotalInstallmentCount(deviceId: String): Int
    
    @Query("DELETE FROM installments WHERE deviceId = :deviceId")
    suspend fun deleteByDeviceId(deviceId: String)
    
    @Query("DELETE FROM installments WHERE loanNumber = :loanNumber")
    suspend fun deleteByLoanNumber(loanNumber: String)
    
    @Query("SELECT * FROM installments WHERE deviceId = :deviceId ORDER BY dueDate ASC LIMIT 1")
    suspend fun getNextDueInstallment(deviceId: String): InstallmentEntity?
}




