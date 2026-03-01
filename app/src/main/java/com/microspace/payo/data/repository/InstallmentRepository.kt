package com.microspace.payo.data.repository

import android.content.Context
import android.util.Log
import com.microspace.payo.data.local.database.DeviceOwnerDatabase
import com.microspace.payo.data.local.database.entities.payment.InstallmentEntity
import com.microspace.payo.data.models.payment.InstallmentData
import com.microspace.payo.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstallmentRepository(private val context: Context) {
    
    private val db = DeviceOwnerDatabase.getDatabase(context)
    private val installmentDao = db.installmentDao()
    private val apiClient = ApiClient()
    
    companion object {
        private const val TAG = "InstallmentRepository"
    }
    
    /**
     * Fetch installments from server and save to local database
     */
    suspend fun syncInstallmentsFromServer(deviceId: String): Result<List<InstallmentEntity>> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Fetching installments for device: $deviceId")
            
            val response = apiClient.getDeviceInstallments(deviceId)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    val installments = body.installments.map { data ->
                        InstallmentEntity(
                            id = data.id,
                            deviceId = deviceId,
                            loanNumber = data.loanNumber ?: "",
                            installmentNumber = data.installmentNumber,
                            dueDate = data.dueDate,
                            amountDue = data.amountDue,
                            amountPaid = data.amountPaid,
                            status = data.status,
                            isOverdue = data.isOverdue ?: false,
                            syncedAt = System.currentTimeMillis()
                        )
                    }
                    
                    // Clear old installments and insert new ones
                    installmentDao.deleteByDeviceId(deviceId)
                    installmentDao.insertAll(installments)
                    
                    Log.d(TAG, "âœ… Synced ${installments.size} installments for device $deviceId")
                    Result.success(installments)
                } else {
                    Log.e(TAG, "âŒ API returned success=false")
                    Result.failure(Exception("API returned success=false"))
                }
            } else {
                Log.e(TAG, "âŒ API error: ${response.code()}")
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error syncing installments", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get installments from local database
     */
    suspend fun getInstallmentsLocal(deviceId: String): List<InstallmentEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            installmentDao.getInstallmentsByDeviceId(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local installments", e)
            emptyList()
        }
    }
    
    /**
     * Get next pending installment
     */
    suspend fun getNextPendingInstallment(deviceId: String): InstallmentEntity? = withContext(Dispatchers.IO) {
        return@withContext try {
            installmentDao.getNextPendingInstallment(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting next pending installment", e)
            null
        }
    }
    
    /**
     * Get next due installment (by due date)
     */
    suspend fun getNextDueInstallment(deviceId: String): InstallmentEntity? = withContext(Dispatchers.IO) {
        return@withContext try {
            installmentDao.getNextDueInstallment(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting next due installment", e)
            null
        }
    }
    
    /**
     * Get unpaid installments (pending, partial, overdue)
     */
    suspend fun getUnpaidInstallments(deviceId: String): List<InstallmentEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            installmentDao.getUnpaidInstallments(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unpaid installments", e)
            emptyList()
        }
    }
    
    /**
     * Get paid installments
     */
    suspend fun getPaidInstallments(deviceId: String): List<InstallmentEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            installmentDao.getPaidInstallments(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paid installments", e)
            emptyList()
        }
    }
    
    /**
     * Get overdue installments
     */
    suspend fun getOverdueInstallments(deviceId: String): List<InstallmentEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            installmentDao.getOverdueInstallments(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting overdue installments", e)
            emptyList()
        }
    }
    
    /**
     * Get total loan amount
     */
    suspend fun getTotalLoanAmount(deviceId: String): Double = withContext(Dispatchers.IO) {
        return@withContext try {
            installmentDao.getTotalLoanAmount(deviceId) ?: 0.0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total loan amount", e)
            0.0
        }
    }
    
    /**
     * Get total amount paid
     */
    suspend fun getTotalAmountPaid(deviceId: String): Double = withContext(Dispatchers.IO) {
        return@withContext try {
            installmentDao.getTotalAmountPaid(deviceId) ?: 0.0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total amount paid", e)
            0.0
        }
    }
    
    /**
     * Get payment progress
     */
    suspend fun getPaymentProgress(deviceId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val paid = installmentDao.getPaidInstallmentCount(deviceId)
            val total = installmentDao.getTotalInstallmentCount(deviceId)
            Pair(paid, total)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment progress", e)
            Pair(0, 0)
        }
    }
}




