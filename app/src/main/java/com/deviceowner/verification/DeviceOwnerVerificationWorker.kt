package com.deviceowner.verification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker for periodic device owner verification
 */
class DeviceOwnerVerificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "DeviceOwnerVerificationWorker"
    }
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val service = DeviceOwnerVerificationService(applicationContext)
                val status = service.verifyDeviceOwner()
                
                Log.i(TAG, "Verification completed: ${status.result}")
                
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Verification failed", e)
                Result.retry()
            }
        }
    }
}
