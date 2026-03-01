package com.microspace.payo.services.heartbeat

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * HeartbeatWorker - Periodic background task for device health reporting.
 */
class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val WORK_NAME = "HeartbeatPeriodicWork"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                15, TimeUnit.MINUTES
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("heartbeat")
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "âœ… HeartbeatWorker enqueued")
        }
        
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "ðŸ›‘ HeartbeatWorker stopped")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "ðŸ”„ Heartbeat execution started...")
        val manager = HeartbeatManager(applicationContext)
        val responseHandler = HeartbeatResponseHandler_v2(applicationContext)
        
        return try {
            val response = manager.sendHeartbeat()
            if (response != null) {
                responseHandler.handle(response)
                Result.success()
            } else {
                Log.w(TAG, "âš ï¸ Heartbeat failed, scheduled for retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "âŒ HeartbeatWorker Error: ${e.message}")
            Result.retry()
        }
    }
}




