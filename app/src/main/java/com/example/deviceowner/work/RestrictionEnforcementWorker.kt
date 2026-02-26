package com.microspace.payo.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.microspace.payo.core.SilentDeviceOwnerManager
import com.microspace.payo.device.DeviceOwnerManager
import com.microspace.payo.security.mode.CompleteSilentMode
import com.microspace.payo.utils.storage.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Re-enforces Developer Options block periodically.
 * Keeps DEVELOPMENT_SETTINGS_ENABLED=0 so Build Number tap never enables debug mode.
 * Silent – no user notification.
 */
class RestrictionEnforcementWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val dm = DeviceOwnerManager(applicationContext)
            val prefsManager = SharedPreferencesManager(applicationContext)
            
            if (!dm.isDeviceOwner()) {
                Log.w(TAG, "Not device owner - skipping enforcement")
                return@withContext Result.success()
            }
            
            // Policy: Block keyboard/kiosk only on server hard lock or tamper. Otherwise setup-only.
            if (!prefsManager.isDeviceRegistered()) {
                dm.applyRestrictionsForSetupOnly()
                Log.d(TAG, "Device not registered - setup-only restrictions applied")
                return@withContext Result.success()
            }
            dm.applyRestrictionsForSetupOnly()
            // Silent verification: re-apply any missing restrictions without user messages
            try {
                SilentDeviceOwnerManager(applicationContext).verifySilentRestrictionsIntact()
                SilentDeviceOwnerManager(applicationContext).verifyFactoryResetStillBlocked()
                CompleteSilentMode(applicationContext).maintainSilentMode()
            } catch (e: Exception) {
                Log.w(TAG, "Silent verification error: ${e.message}")
            }
            Log.d(TAG, "Periodic setup-only + silent verification – keyboard/touch stay enabled until hard lock")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Periodic enforcement error: ${e.message}", e)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "RestrictionEnforcementWorker"
        const val WORK_NAME = "RestrictionEnforcement"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RestrictionEnforcementWorker>(
                15, TimeUnit.MINUTES // Increased to 15m (min allowed by WorkManager)
            )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "✅ Scheduled periodic restriction enforcement")
        }
    }
}
