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

            // UPDATED: WorkManager has a minimum interval of 15 minutes.
            // Even though we set 30 seconds in UpdateConfig for the Foreground Loop,
            // we'll set this backup worker to 15 minutes (the lowest allowed by Android).
            val intervalMinutes = maxOf(15L, UpdateConfig.UPDATE_CHECK_INTERVAL_SECONDS / 60)

            val updateWork = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(UpdateConfig.INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UpdateConfig.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                updateWork
            )

            Log.d(TAG, "Periodic update checks scheduled (WorkManager backup every ${intervalMinutes}m)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule update checks", e)
        }
    }
}
