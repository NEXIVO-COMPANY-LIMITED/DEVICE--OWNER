package com.microspace.payo.core.frp.health

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.microspace.payo.config.FrpConfig
import com.microspace.payo.core.frp.verification.FrpVerificationService
import java.util.concurrent.TimeUnit

/**
 * FRP Health Check Service - Background FRP Monitoring
 *
 * Periodically verifies FRP integrity using WorkManager.
 * Runs every 24 hours after FRP activation.
 * Ensures FRP remains active and operational.
 *
 * @version 1.0.0
 */
class FrpHealthCheckService : Service() {

    companion object {
        private const val TAG = "FrpHealthCheckService"
        private const val WORK_TAG = "frp_health_check"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FRP Health Check Service started")
        scheduleFrpHealthCheck()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleFrpHealthCheck() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val healthCheckRequest = PeriodicWorkRequestBuilder<FrpHealthCheckWorker>(
                FrpConfig.FRP_VERIFICATION_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.MINUTES
                )
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_TAG,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                healthCheckRequest
            )

            Log.i(TAG, "FRP health check scheduled every ${FrpConfig.FRP_VERIFICATION_INTERVAL_HOURS} hours")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule FRP health check", e)
        }
    }
}

/**
 * FRP Health Check Worker - Performs actual verification
 */
class FrpHealthCheckWorker(
    context: android.content.Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "FrpHealthCheckWorker"
    }

    override fun doWork(): Result {
        return try {
            Log.i(TAG, "Running FRP health check...")

            val verificationService = FrpVerificationService(applicationContext)
            val result = verificationService.verifyFrpIntegrity()

            if (result.passed) {
                Log.i(TAG, "✅ FRP health check PASSED")
                Result.success()
            } else {
                Log.e(TAG, "❌ FRP health check FAILED")
                Log.e(TAG, "Failed checks: ${result.failedChecks.map { it.name }}")

                // Retry on failure
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "FRP health check error", e)
            Result.retry()
        }
    }
}

/**
 * GMS Availability Monitor - Ensures Google Play Services stays available
 */
class GmsAvailabilityMonitor(private val context: android.content.Context) {

    companion object {
        private const val TAG = "GmsAvailabilityMonitor"
    }

    fun scheduleGmsMonitoring() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val gmsCheckRequest = PeriodicWorkRequestBuilder<GmsAvailabilityWorker>(
                FrpConfig.GMS_CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    5,
                    TimeUnit.MINUTES
                )
                .addTag("gms_availability_check")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "gms_availability_check",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                gmsCheckRequest
            )

            Log.i(TAG, "GMS availability monitoring scheduled every ${FrpConfig.GMS_CHECK_INTERVAL_MINUTES} minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule GMS monitoring", e)
        }
    }
}

/**
 * GMS Availability Worker - Checks if GMS is available and enabled
 */
class GmsAvailabilityWorker(
    context: android.content.Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "GmsAvailabilityWorker"
    }

    override fun doWork(): Result {
        return try {
            val pm = applicationContext.packageManager
            val gmsInfo = pm.getPackageInfo(FrpConfig.GMS_PACKAGE_NAME, 0)

            if (gmsInfo.applicationInfo?.enabled == true) {
                Log.d(TAG, "✅ Google Play Services available and enabled")
                Result.success()
            } else {
                Log.w(TAG, "⚠️ Google Play Services disabled - attempting to enable")
                // Note: Enabling GMS requires device owner permissions
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "GMS availability check failed", e)
            Result.retry()
        }
    }
}
