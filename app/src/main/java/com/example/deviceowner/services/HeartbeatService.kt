package com.example.deviceowner.services

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.work.*
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.core.device.DeviceDataCollector
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.OfflineEvent
import com.example.deviceowner.data.models.HeartbeatRequest
import com.example.deviceowner.data.models.HeartbeatResponse
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.services.sync.OfflineSyncWorker
import com.example.deviceowner.utils.SharedPreferencesManager
import com.example.deviceowner.ui.screens.LockScreenState
import com.example.deviceowner.ui.screens.LockScreenStrategy
import com.example.deviceowner.ui.screens.LockScreenType
import com.google.gson.Gson
import kotlinx.coroutines.*
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * HeartbeatService - Manages periodic device heartbeat collection and sending.
 * Runs inside SecurityMonitorService (FOREGROUND SERVICE) so heartbeat continues when app is closed.
 * - Foreground = notification shown, system does not kill; 30s interval reliable.
 * - Background would be throttled/killed = heartbeat would stop or have big gaps.
 * Uses WakeLock during send so Doze does not delay execution (strict 30s interval).
 * Supports offline queuing and automatic synchronization.
 */
class HeartbeatService(
    private val context: Context,
    private val apiClient: ApiClient,
    private val deviceDataCollector: DeviceDataCollector
) {
    
    companion object {
        private const val TAG = "HeartbeatService"
        /** Interval between heartbeats – Django expects data at this interval. */
        private const val HEARTBEAT_INTERVAL_MILLIS = 30000L // 30 seconds
        /**
         * Delay before FIRST heartbeat after registration/schedule.
         * IMMEDIATE START: No delay - send first heartbeat right away
         * Server baseline is already saved during registration
         */
        private const val FIRST_HEARTBEAT_DELAY_MILLIS = 0L // IMMEDIATE - NO DELAY
        
        /** Maximum allowed interval violation before logging warning (milliseconds) */
        private const val MAX_INTERVAL_VIOLATION_MS = 1000L // 1 second
    }
    
    private val heartbeatsSent = AtomicInteger(0)
    private val heartbeatsFailed = AtomicInteger(0)
    
    private val controlManager = RemoteDeviceControlManager(context)
    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val offlineEventDao = database.offlineEventDao()
    private val gson = Gson()
    
    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private var isScheduled = false
    private var serviceScope: CoroutineScope? = null
    
    // INTERVAL CONSISTENCY TRACKING
    private var lastHeartbeatTime = 0L
    private var lastHeartbeatTimestamp = ""
    
    fun schedulePeriodicHeartbeat(deviceId: String) {
        if (isScheduled) return

        // SupervisorJob: one failed heartbeat does not cancel the scope – loop keeps running
        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        heartbeatRunnable = object : Runnable {
            override fun run() {
                // FIXED: Calculate next heartbeat based on LAST heartbeat time for exact intervals
                // This eliminates timing drift and ensures consistent 30-second intervals
                val now = System.currentTimeMillis()
                val timeSinceLastHeartbeat = if (lastHeartbeatTime > 0) {
                    now - lastHeartbeatTime
                } else {
                    HEARTBEAT_INTERVAL_MILLIS // First run
                }
                
                // Calculate delay to maintain exact 30-second intervals
                val delayUntilNext = HEARTBEAT_INTERVAL_MILLIS - timeSinceLastHeartbeat
                val actualDelay = maxOf(delayUntilNext, 0L)
                
                // Schedule NEXT run with exact interval calculation
                handler.postDelayed(this, actualDelay)
                
                // Log interval consistency
                if (lastHeartbeatTime > 0) {
                    val violation = Math.abs(timeSinceLastHeartbeat - HEARTBEAT_INTERVAL_MILLIS)
                    if (violation > MAX_INTERVAL_VIOLATION_MS) {
                        Log.w(TAG, "⚠️ INTERVAL VIOLATION: Expected ${HEARTBEAT_INTERVAL_MILLIS}ms, got ${timeSinceLastHeartbeat}ms (±${violation}ms)")
                    }
                }
                
                serviceScope?.launch {
                    try {
                        performHeartbeat(deviceId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Heartbeat error (loop continues): ${e.message}", e)
                    }
                }
            }
        }

        // First heartbeat after gap so server has baseline; then every 30s with exact intervals
        handler.postDelayed(heartbeatRunnable!!, FIRST_HEARTBEAT_DELAY_MILLIS)
        isScheduled = true
        scheduleOfflineSyncWorker()
        Log.i(TAG, "✅ Periodic heartbeat scheduled – first in ${FIRST_HEARTBEAT_DELAY_MILLIS / 1000}s (baseline gap), then every 30s")
    }

    private suspend fun performHeartbeat(deviceId: String) {
        if (deviceId.isBlank() || deviceId.equals("unknown", ignoreCase = true)) {
            Log.w(TAG, "Heartbeat skipped – device_id missing or unknown (will retry when registered)")
            return
        }
        if (com.example.deviceowner.deactivation.DeviceOwnerDeactivationManager(context).isDeactivationInProgress()) {
            Log.d(TAG, "Heartbeat skipped – deactivation in progress (fast response to backend)")
            return
        }
        
        // TRACK HEARTBEAT TIMING FOR CONSISTENCY ANALYSIS
        val heartbeatStartTime = System.currentTimeMillis()
        lastHeartbeatTime = heartbeatStartTime
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::heartbeat")?.apply {
            acquire(60_000) // max 60s so we never hold forever
        }
        try {
            val request = try {
                collectHeartbeatData()
            } catch (e: Exception) {
                Log.e(TAG, "Heartbeat data collection failed (will retry next interval): ${e.message}", e)
                return
            }

            try {
                val response = apiClient.sendHeartbeat(deviceId, request)

            if (response.isSuccessful) {
                val count = heartbeatsSent.incrementAndGet()
                // Update last heartbeat time so Device Info page shows "Last heartbeat: Xm ago"
                try {
                    SharedPreferencesManager(context).setLastHeartbeatTime(System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update last heartbeat time: ${e.message}")
                }
                if (count % 10 == 1) {
                    Log.i(TAG, "✅ Heartbeat sent successfully (count: $count)")
                }
                response.body()?.let { body ->
                    try {
                        processHeartbeatResponse(deviceId, body)
                    } catch (e: Exception) {
                        Log.e(TAG, "Heartbeat response handling failed (data was sent): ${e.message}", e)
                    }
                }
            } else {
                Log.e(TAG, "❌ Heartbeat HTTP ${response.code()}: ${response.message()} – queueing for offline sync")
                handleHeartbeatFailure(request)
            }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Heartbeat send failed – queueing for offline sync: ${e.javaClass.simpleName} - ${e.message}")
                handleHeartbeatFailure(request)
            }
        } finally {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock release: ${e.message}")
            }
        }
    }

    private suspend fun processHeartbeatResponse(deviceId: String, response: HeartbeatResponse) {
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "📥 Processing heartbeat response...")
                Log.d(TAG, "   - Device locked: ${response.isDeviceLocked()}")
                Log.d(TAG, "   - Deactivation: ${response.isDeactivationRequested()}")
                Log.d(TAG, "   - Next payment: ${response.getNextPaymentDateTime()}")
                Log.d(TAG, "   - Server time: ${response.serverTime}")
                Log.d(TAG, "   - Mismatches: ${response.comparisonResult?.totalMismatches ?: 0}")

                // Save response data for UI and offline reference
                SharedPreferencesManager(context).saveHeartbeatResponse(
                    nextPaymentDate = response.getNextPaymentDateTime(),
                    unlockPassword = response.getUnlockPassword(),
                    serverTime = response.serverTime,
                    isLocked = response.isDeviceLocked(),
                    lockReason = response.getLockReason().takeIf { it.isNotBlank() }
                )
                
                // PRIORITY 1: Deactivation requested – start immediately (deactivation, loan complete, payment complete)
                if (response.isDeactivationRequested()) {
                    val source = when {
                        response.deactivateRequested == true || response.deactivation?.status == "requested" -> "deactivation_requested"
                        response.paymentComplete == true -> "payment_complete"
                        response.loanComplete == true -> "loan_complete"
                        response.loanStatus != null -> "loan_status=${response.loanStatus}"
                        else -> "content.reason"
                    }
                    Log.i(TAG, "🔓 Deactivation requested by server (source=$source) – starting immediately (non-blocking)")
                    val appContext = context.applicationContext
                    CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                        try {
                            val deactivationManager = com.example.deviceowner.deactivation.DeviceOwnerDeactivationManager(appContext)
                            deactivationManager.deactivateDeviceOwner()
                            Log.i(TAG, "✅ Device deactivation completed")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Deactivation failed: ${e.message}", e)
                        }
                    }
                    return@withContext
                }

                // PRIORITY 2: Use LockScreenStrategy (next_payment.date_time from heartbeat) for payment locks
                // Strategy: HARD_LOCK when overdue, SOFT_LOCK reminder when due soon, UNLOCKED otherwise
                val lockState = LockScreenStrategy.determineLockScreenState(response)
                Log.d(TAG, "Heartbeat lock state: ${lockState.type}, daysUntilDue=${lockState.daysUntilDue}")

                when (lockState.type) {
                    LockScreenType.HARD_LOCK_SECURITY -> {
                        val reason = lockState.reason ?: response.getBlockReason()
                        Log.e(TAG, "🚨 HARDLOCK (Security) – Applying hard lock.")
                        try {
                            controlManager.applyHardLock(reason, forceRestart = false, forceFromServerOrMismatch = true)
                            Log.i(TAG, "✅ Hard lock (security) applied from heartbeat")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to apply hard lock: ${e.message}", e)
                        }
                        return@withContext
                    }
                    LockScreenType.HARD_LOCK_PAYMENT -> {
                        val reason = buildPaymentOverdueReason(lockState)
                        val formattedDate = lockState.nextPaymentDate?.let { LockScreenStrategy.formatDueDate(it) }
                        val orgName = lockState.shopName ?: com.example.deviceowner.utils.SharedPreferencesManager(context).getOrganizationName()
                        Log.e(TAG, "🚨 HARDLOCK (Payment Overdue) – Applying hard lock.")
                        try {
                            controlManager.applyHardLock(
                                reason = reason,
                                forceRestart = false,
                                forceFromServerOrMismatch = true,
                                nextPaymentDate = formattedDate,
                                organizationName = orgName
                            )
                            Log.i(TAG, "✅ Hard lock (payment overdue) applied from heartbeat")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to apply hard lock: ${e.message}", e)
                        }
                        return@withContext
                    }
                    LockScreenType.SOFT_LOCK_REMINDER -> {
                        // Throttle: show reminder at most once per 4 hours to avoid spam on every 30s heartbeat
                        val prefs = context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
                        val lastShown = prefs.getLong("last_payment_reminder_shown", 0L)
                        val throttleMs = 4 * 60 * 60 * 1000L // 4 hours
                        val shouldShow = (System.currentTimeMillis() - lastShown) > throttleMs
                        if (!shouldShow) {
                            Log.d(TAG, "🔔 Payment reminder throttled (last shown ${(System.currentTimeMillis() - lastShown) / 60_000} min ago)")
                            return@withContext
                        }
                        prefs.edit().putLong("last_payment_reminder_shown", System.currentTimeMillis()).apply()
                        val reason = buildPaymentReminderReason(lockState)
                        val formattedDate = lockState.nextPaymentDate?.let { LockScreenStrategy.formatDueDate(it) }
                        val orgName = lockState.shopName ?: com.example.deviceowner.utils.SharedPreferencesManager(context).getOrganizationName()
                        Log.w(TAG, "🔔 SOFT LOCK (Payment Reminder) – Showing reminder overlay (next_payment=$formattedDate)")
                        try {
                            controlManager.applySoftLock(
                                reason = reason,
                                triggerAction = "payment_reminder",
                                nextPaymentDate = formattedDate,
                                organizationName = orgName
                            )
                            Log.i(TAG, "✅ Soft lock (payment reminder) applied from heartbeat")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to apply soft lock: ${e.message}", e)
                        }
                        return@withContext
                    }
                    LockScreenType.UNLOCKED -> {
                        if (controlManager.isHardLocked() || controlManager.getLockState() == RemoteDeviceControlManager.LOCK_SOFT) {
                            Log.i(TAG, "🔓 Server: device OK – Unlocking device")
                            try {
                                controlManager.unlockDevice()
                                SoftLockOverlayService.stopOverlay(context)
                                Log.i(TAG, "✅ Device unlocked successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Unlock failed: ${e.message}", e)
                            }
                        } else {
                            Log.d(TAG, "✅ Device status OK - no lock changes needed")
                        }
                        return@withContext
                    }
                    LockScreenType.DEACTIVATION -> {
                        // Handled above as PRIORITY 1
                        return@withContext
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "processHeartbeatResponse error (heartbeat continues): ${e.message}", e)
            }
        }
    }

    private fun buildPaymentOverdueReason(lockState: LockScreenState): String {
        val base = "Payment overdue"
        val dateStr = lockState.nextPaymentDate?.let { LockScreenStrategy.formatDueDate(it) }
        return if (dateStr != null) "$base. Next payment was due: $dateStr. Please contact support to unlock." else base
    }

    private fun buildPaymentReminderReason(lockState: LockScreenState): String {
        val dateStr = lockState.nextPaymentDate?.let { LockScreenStrategy.formatDueDate(it) }
            ?: "soon"
        return "Payment reminder: Your next payment is due $dateStr. Please pay before the due date to avoid device restrictions."
    }

    private suspend fun handleHeartbeatFailure(request: HeartbeatRequest?) {
        heartbeatsFailed.incrementAndGet()
        if (request == null) return

        try {
            val json = gson.toJson(request)
            val event = OfflineEvent(eventType = "HEARTBEAT", jsonData = json)
            offlineEventDao.insertEvent(event)
            scheduleOfflineSyncWorker()
            Log.d(TAG, "Heartbeat queued for offline sync – will send when online")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save offline event: ${e.message}", e)
        }
    }
    
    private fun scheduleOfflineSyncWorker() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncRequest = OneTimeWorkRequestBuilder<OfflineSyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork("OfflineSync", ExistingWorkPolicy.KEEP, syncRequest)
    }
    
    private suspend fun collectHeartbeatData(): HeartbeatRequest {
        val serverDeviceId = SharedPreferencesManager(context).getDeviceIdForHeartbeat()
            ?: context.getSharedPreferences("device_data", Context.MODE_PRIVATE).getString("device_id_for_heartbeat", null)
            ?: context.getSharedPreferences("device_registration", Context.MODE_PRIVATE).getString("device_id", null)
            ?: "unknown"
        return deviceDataCollector.collectHeartbeatData(serverDeviceId)
    }

    fun cancelPeriodicHeartbeat() {
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        serviceScope?.cancel()
        isScheduled = false
    }
}
