package com.example.deviceowner.services.heartbeat

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
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.services.lock.SoftLockOverlayService
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import java.util.concurrent.TimeUnit

/**
 * HeartbeatWorker - Periodic background task for device health reporting.
 * 
 * ✅ RESPONSIBILITIES:
 * 1. Collect and send device integrity data to server.
 * 2. Process server response for payment status and security locks.
 * 3. Cache critical data (next payment, unlock password) for offline use.
 * 4. Handle deactivation and tamper commands.
 */
class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val WORK_NAME = "HeartbeatPeriodicWork"

        /**
         * Enqueues the periodic heartbeat work.
         * Default interval: 15 minutes (WorkManager minimum).
         */
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
            Log.d(TAG, "✅ HeartbeatWorker enqueued")
        }
        
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "🛑 HeartbeatWorker stopped")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "🔄 Heartbeat execution started...")
        val manager = HeartbeatManager(applicationContext)
        val response = manager.sendHeartbeat()

        return if (response != null) {
            // ✅ PROCESS AND PERSIST SERVER RESPONSE
            handleActions(response)
            Result.success()
        } else {
            Log.w(TAG, "⚠️ Heartbeat failed, scheduled for retry")
            Result.retry()
        }
    }

    /**
     * Processes actions from server and persists critical data.
     */
    private fun handleActions(response: com.example.deviceowner.data.models.heartbeat.HeartbeatResponse) {
        val context = applicationContext
        val prefs = SharedPreferencesManager(context)
        val dom = DeviceOwnerManager(context)

        Log.i(TAG, "📥 Processing server actions (Success: ${response.success})")

        // 1. PERSIST CRITICAL DATA FOR OFFLINE ACCESS
        // Extract next payment date and unlock password from response
        val nextPaymentDate = response.getNextPaymentDateTime()
        val unlockPassword = response.getUnlockPassword()
        val serverTime = response.serverTime
        val isLocked = response.isDeviceLocked()
        val lockReason = response.getLockReason()

        Log.d(TAG, "💾 Saving heartbeat data: Next Payment: $nextPaymentDate, Locked: $isLocked")
        prefs.saveHeartbeatResponse(
            nextPaymentDate = nextPaymentDate,
            unlockPassword = unlockPassword,
            isLocked = isLocked,
            serverTime = serverTime,
            lockReason = lockReason
        )

        // 2. HANDLE HARD LOCK (Payment Overdue or Security Breach)
        if (isLocked || response.hasHighSeverityMismatches()) {
            Log.w(TAG, "🔒 HARD LOCK TRIGGERED: $lockReason")
            // Implement Hard Lock Logic (e.g., Suspend Packages via DPM)
            // Example: dom.applyHardLock(lockReason)
        }

        // 3. HANDLE SOFT LOCK (Payment Reminder Overlay)
        if (response.isSoftLockRequested()) {
            val message = response.actions?.reminder?.message ?: "Payment Reminder"
            Log.i(TAG, "🔔 SOFT LOCK TRIGGERED: $message")
            SoftLockOverlayService.start(context, message)
        } else {
            // Remove soft lock if not requested (and device is not hard locked)
            if (!isLocked) {
                SoftLockOverlayService.stop(context)
            }
        }

        // 4. HANDLE DEACTIVATION COMMAND
        if (response.isDeactivationRequested()) {
            Log.w(TAG, "🔓 DEACTIVATION REQUESTED BY SERVER")
            try {
                dom.selfDestructProvisioning()
                Log.i(TAG, "✅ Device Owner cleared successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Deactivation failed: ${e.message}")
            }
        }
    }
}
