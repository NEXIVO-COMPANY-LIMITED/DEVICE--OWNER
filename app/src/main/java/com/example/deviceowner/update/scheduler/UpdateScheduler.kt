package com.example.deviceowner.update.scheduler

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.deviceowner.update.config.UpdateConfig
import com.example.deviceowner.update.worker.UpdateCheckWorker
import java.util.concurrent.TimeUnit

/**
 * Schedules periodic update checks via WorkManager.
 * Only active when app is Device Owner.
 */
object UpdateScheduler {

    private const val TAG = "UpdateScheduler"

    /**
     * Schedule periodic update checks. Safe to call multiple times - uses KEEP policy.
     */
    fun schedulePeriodicChecks(context: Context) {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val updateWork = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                UpdateConfig.UPDATE_CHECK_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(UpdateConfig.INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UpdateConfig.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                updateWork
            )

            Log.d(TAG, "Periodic update checks scheduled (every ${UpdateConfig.UPDATE_CHECK_INTERVAL_HOURS}h)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule update checks", e)
        }
    }
}
