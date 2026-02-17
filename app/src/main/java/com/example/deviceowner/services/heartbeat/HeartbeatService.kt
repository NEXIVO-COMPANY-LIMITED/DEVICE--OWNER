package com.example.deviceowner.services.heartbeat

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
import com.example.deviceowner.data.local.database.entities.offline.OfflineEvent
import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest
import com.example.deviceowner.data.models.heartbeat.HeartbeatResponse
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.services.lock.SoftLockOverlayService
import com.example.deviceowner.services.sync.OfflineSyncWorker
import com.example.deviceowner.utils.storage.PaymentDataManager
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.example.deviceowner.services.reporting.ServerBugAndLogReporter
import com.example.deviceowner.ui.screens.lock.LockScreenState
import com.example.deviceowner.ui.screens.lock.LockScreenStrategy
import com.example.deviceowner.ui.screens.lock.LockScreenType
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

    // Throttle: report same failure to server at most once per 5 min so backend sees issue without spam
    private var lastReportedBlockReason: String? = null
    private var lastReportedBlockTime = 0L
    private var lastReportedHttpFailureTime = 0L
    private val REPORT_THROTTLE_MS = 5 * 60 * 1000L
    
    fun schedulePeriodicHeartbeat(deviceId: String) {
        if (isScheduled) return

        // Verify device ID is valid before scheduling (Issue #6 fix - explicit error handling)
        if (deviceId.isBlank() || deviceId.equals("unknown", ignoreCase = true)) {
            Log.e(TAG, "❌ HEARTBEAT NOT SCHEDULED: device_id is invalid or missing")
            Log.e(TAG, "   Device must be registered first with a valid server-assigned device_id")
            reportHeartbeatBlockToServer("invalid_device_id_at_schedule", "Heartbeat not scheduled: device_id is invalid or missing. Device must be registered first.")
            return
        }
        
        if (deviceId.startsWith("ANDROID-")) {
            Log.e(TAG, "❌ HEARTBEAT NOT SCHEDULED: device_id is ANDROID-* (locally generated)")
            Log.e(TAG, "   Use server-assigned device_id (e.g. DEV-B5AF7F0BEDEB) from registration")
            reportHeartbeatBlockToServer("android_prefix_device_id", "Heartbeat not scheduled: device_id is ANDROID-* (locally generated). Use server-assigned device_id.")
            return
        }

        // SupervisorJob: one failed heartbeat does not cancel the scope – loop keeps running
        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        heartbeatRunnable = object : Runnable {
            override fun run() {
                // FIXED: Calculate next heartbeat based on LAST heartbeat time for exact intervals
                // This eliminates timing drift and ensures consistent 30-second intervals
                val now = System.currentTimeMillis()
                
                // Schedule NEXT run FIRST with exact interval calculation
                // This ensures the next run is scheduled before this one executes
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
                
                // NOW execute the heartbeat (after next run is scheduled)
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

    /**
     * Send one heartbeat to the server. Called every 30s by the scheduled runnable.
     * Heartbeat continues even when device is locked (hard/soft lock); lock state only affects
     * how we process the response (apply/unlock), not whether we send.
     */
    private suspend fun performHeartbeat(deviceId: String) {
        // VERIFY device ID is still valid (Issue #5 fix - cache invalidation on app restart)
        val currentDeviceId = com.example.deviceowner.data.DeviceIdProvider.getDeviceId(context)
        if (currentDeviceId == null) {
            Log.e(TAG, "❌ HEARTBEAT BLOCKED: device_id not found in storage (device may have been deactivated)")
            reportHeartbeatBlockToServer("device_id_not_found", "Heartbeat not sent: device_id not found in storage. Device may have been deactivated.")
            return
        }
        
        if (currentDeviceId != deviceId) {
            Log.w(TAG, "⚠️ Device ID changed during heartbeat scheduling")
            Log.w(TAG, "   Scheduled: ${deviceId.take(8)}...")
            Log.w(TAG, "   Current: ${currentDeviceId.take(8)}...")
            // Use current device ID
        }
        
        val effectiveDeviceId = currentDeviceId
        
        if (effectiveDeviceId.isBlank() || effectiveDeviceId.equals("unknown", ignoreCase = true)) {
            Log.w(TAG, "❌ HEARTBEAT BLOCKED: device_id missing or unknown (will retry when registered)")
            return
        }
        
        // Check if device_id is locally generated (not from server)
        if (effectiveDeviceId.startsWith("ANDROID-")) {
            Log.w(TAG, "❌ HEARTBEAT BLOCKED: device_id is ANDROID-* (use server-assigned id e.g. DEV-B5AF7F0BEDEB from registration)")
            reportHeartbeatBlockToServer("device_id_android_prefix", "Heartbeat not sent: device_id is ANDROID-*. Use server-assigned device_id (e.g. DEV-B5AF7F0BEDEB) from registration.")
            return
        }
        
        if (com.example.deviceowner.deactivation.DeviceOwnerDeactivationManager(context).isDeactivationInProgress()) {
            Log.w(TAG, "⏭️ HEARTBEAT BLOCKED: deactivation_in_progress=true in device_owner_deactivation prefs (heartbeat skipped until deactivation finishes or flag is cleared)")
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
                Log.d(TAG, "📊 Collecting heartbeat data...")
                collectHeartbeatData()
            } catch (e: Exception) {
                Log.e(TAG, "❌ HEARTBEAT BLOCKED: data collection failed - ${e.message}", e)
                heartbeatsFailed.incrementAndGet()
                reportHeartbeatBlockToServer("data_collection_failed", "Heartbeat data collection failed: ${e.javaClass.simpleName} - ${e.message}")
                ServerBugAndLogReporter.postException(e, "Heartbeat data collection failed.")
                return
            }

            try {
                Log.d(TAG, "📤 Sending heartbeat to server...")
                Log.d(TAG, "   Device ID: $effectiveDeviceId")
                Log.d(TAG, "   Serial: ${request.serialNumber}")
                Log.d(TAG, "   IMEIs: ${request.deviceImeis}")
                Log.d(TAG, "   Storage: ${request.totalStorage}")
                Log.d(TAG, "   RAM: ${request.installedRam}")
                
                val response = apiClient.sendHeartbeat(effectiveDeviceId, request)

                if (response.isSuccessful) {
                    val count = heartbeatsSent.incrementAndGet()
                    Log.i(TAG, "✅ Heartbeat #$count sent successfully")
                    
                    // Update last heartbeat time
                    try {
                        SharedPreferencesManager(context).setLastHeartbeatTime(System.currentTimeMillis())
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Could not update last heartbeat time: ${e.message}")
                    }
                    
                    response.body()?.let { body ->
                        try {
                            processHeartbeatResponse(effectiveDeviceId, body)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Heartbeat response handling failed: ${e.message}", e)
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Heartbeat HTTP ${response.code()}: ${response.message()}")
                    val errorBody = response.errorBody()?.string() ?: "(no error body)"
                    Log.e(TAG, "   Error: $errorBody")
                    heartbeatsFailed.incrementAndGet()
                    reportHeartbeatHttpFailureToServer(effectiveDeviceId, response.code(), errorBody)
                    handleHeartbeatFailure(request)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Heartbeat send failed: ${e.javaClass.simpleName} - ${e.message}", e)
                heartbeatsFailed.incrementAndGet()
                reportHeartbeatExceptionToServer(effectiveDeviceId, e)
                handleHeartbeatFailure(request)
            }
        } finally {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ WakeLock release error: ${e.message}")
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
                val nextPaymentDate = response.getNextPaymentDateTime()
                val unlockPassword = response.getUnlockPassword()
                SharedPreferencesManager(context).saveHeartbeatResponse(
                    nextPaymentDate = nextPaymentDate,
                    unlockPassword = unlockPassword,
                    serverTime = response.serverTime,
                    isLocked = response.isDeviceLocked(),
                    lockReason = response.getLockReason().takeIf { it.isNotBlank() }
                )
                // Save to local payment DB (next_payment + password) for lock screen and payment UI
                PaymentDataManager(context).apply {
                    saveNextPaymentInfo(nextPaymentDate, unlockPassword)
                    saveServerTime(response.serverTime)
                    saveLockState(response.isDeviceLocked(), response.getLockReason().takeIf { it.isNotBlank() })
                }

                // Process payment status and apply appropriate lock/unlock
                com.example.deviceowner.services.payment.PaymentLockManager(context).processPaymentStatus(
                    nextPaymentDate = nextPaymentDate,
                    unlockPassword = unlockPassword,
                    shopName = response.content?.shop
                )

                // Schedule payment reminder and overdue check
                com.example.deviceowner.services.payment.PaymentReminderService(context).apply {
                    schedulePaymentReminder(nextPaymentDate)
                    schedulePaymentOverdueCheck(nextPaymentDate)
                }
                
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
                        val orgName = lockState.shopName ?: com.example.deviceowner.utils.storage.SharedPreferencesManager(context).getOrganizationName()
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
                        val orgName = lockState.shopName ?: com.example.deviceowner.utils.storage.SharedPreferencesManager(context).getOrganizationName()
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

    /** Report heartbeat block reason to tech logs + bugs API so backend can see why heartbeat is not sending. Throttled to once per 5 min per reason. */
    private fun reportHeartbeatBlockToServer(reason: String, message: String) {
        val now = System.currentTimeMillis()
        if (lastReportedBlockReason == reason && (now - lastReportedBlockTime) < REPORT_THROTTLE_MS) return
        lastReportedBlockReason = reason
        lastReportedBlockTime = now
        ServerBugAndLogReporter.postLog("heartbeat", "Error", message, mapOf("block_reason" to reason))
        ServerBugAndLogReporter.postBug(
            title = "Heartbeat not sending: $reason",
            message = message,
            priority = "high",
            extraData = mapOf("block_reason" to reason)
        )
    }

    /** Report heartbeat HTTP failure (4xx/5xx) to logs + bugs so backend can fix. Throttled to once per 5 min. */
    private fun reportHeartbeatHttpFailureToServer(deviceId: String, httpCode: Int, errorBody: String) {
        val now = System.currentTimeMillis()
        if ((now - lastReportedHttpFailureTime) < REPORT_THROTTLE_MS) return
        lastReportedHttpFailureTime = now
        val msg = "Heartbeat failed: HTTP $httpCode. Body: ${errorBody.take(500)}"
        ServerBugAndLogReporter.postLog("heartbeat", "Error", msg, mapOf("http_code" to httpCode, "device_id" to deviceId.take(20)))
        ServerBugAndLogReporter.postBug(
            title = "Heartbeat HTTP $httpCode",
            message = msg,
            priority = if (httpCode in 400..499) "high" else "medium",
            extraData = mapOf("http_code" to httpCode, "device_id" to deviceId.take(20), "response_snippet" to errorBody.take(300))
        )
    }

    /** Report heartbeat send exception to logs + bugs so backend can see network/SSL errors. Throttled to once per 5 min. */
    private fun reportHeartbeatExceptionToServer(deviceId: String, e: Exception) {
        val now = System.currentTimeMillis()
        if ((now - lastReportedHttpFailureTime) < REPORT_THROTTLE_MS) return
        lastReportedHttpFailureTime = now
        ServerBugAndLogReporter.postLog("heartbeat", "Error", "Heartbeat send failed: ${e.javaClass.simpleName} - ${e.message}", mapOf("device_id" to deviceId.take(20)))
        ServerBugAndLogReporter.postException(e, "Heartbeat send failed. Device: $deviceId")
    }

    private suspend fun handleHeartbeatFailure(request: HeartbeatRequest?) {
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
        // Clear cache on app lifecycle event (Issue #5 fix)
        com.example.deviceowner.data.DeviceIdProvider.clearCache()
        Log.i(TAG, "✅ Periodic heartbeat cancelled and cache cleared")
    }
}
