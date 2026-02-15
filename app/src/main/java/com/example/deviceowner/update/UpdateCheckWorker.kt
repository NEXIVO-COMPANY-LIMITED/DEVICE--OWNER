package com.example.deviceowner.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that periodically checks for app updates from GitHub Releases.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting periodic update check from WorkManager...")

        return@withContext try {
            val updateManager = GitHubUpdateManager(applicationContext)
            updateManager.checkAndUpdate()
            Log.d(TAG, "Update check completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
    }
}
