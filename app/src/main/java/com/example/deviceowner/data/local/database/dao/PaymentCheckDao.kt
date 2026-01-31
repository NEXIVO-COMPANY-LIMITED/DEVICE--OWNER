package com.example.deviceowner.data.local.database.dao

import androidx.room.*
import com.example.deviceowner.data.local.database.entities.PaymentCheckEntity
import com.example.deviceowner.data.local.database.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentCheckDao {
    
    @Query("SELECT * FROM payment_checks WHERE syncStatus = :status ORDER BY checkedAt ASC LIMIT :limit")
    suspend fun getPendingPaymentChecks(status: SyncStatus = SyncStatus.PENDING, limit: Int = 50): List<PaymentCheckEntity>
    
    @Query("SELECT * FROM payment_checks WHERE loanId = :loanId ORDER BY checkedAt DESC LIMIT :limit")
    fun getRecentPaymentChecks(loanId: String, limit: Int = 100): Flow<List<PaymentCheckEntity>>
    
    @Query("SELECT COUNT(*) FROM payment_checks WHERE syncStatus = :status")
    suspend fun getPendingCount(status: SyncStatus = SyncStatus.PENDING): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentCheck(paymentCheck: PaymentCheckEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentChecks(paymentChecks: List<PaymentCheckEntity>)
    
    @Update
    suspend fun updatePaymentCheck(paymentCheck: PaymentCheckEntity)
    
    @Query("UPDATE payment_checks SET syncStatus = :status, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markAsSynced(id: Long, status: SyncStatus = SyncStatus.SYNCED, syncedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE payment_checks SET syncStatus = :status, retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markAsFailed(id: Long, status: SyncStatus = SyncStatus.FAILED, error: String?)
    
    @Query("DELETE FROM payment_checks WHERE syncStatus = :status AND syncedAt > 0 AND syncedAt < :beforeTimestamp")
    suspend fun deleteOldSyncedPaymentChecks(status: SyncStatus = SyncStatus.SYNCED, beforeTimestamp: Long)
    
    @Query("DELETE FROM payment_checks WHERE id = :id")
    suspend fun deletePaymentCheck(id: Long)
}
