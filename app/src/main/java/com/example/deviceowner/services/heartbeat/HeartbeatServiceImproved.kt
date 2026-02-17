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
import com.example.deviceowner.data.local.database.entities.offline.HeartbeatSyncEntity
import com.example.deviceowner.data.local.database.entities.offline.OfflineEvent
import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest
import com.example.deviceowner.data.models.heartbeat.HeartbeatResponse
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.services.lock.SoftLockOverlayService
import com.example.deviceowner.ui.screens.lock.LockScreenStrategy
import com.example.deviceowner.ui.screens.lock.LockScreenType
import com.example.deviceowner.utils.storage.PaymentDataManager
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.google.gson.Gson
import com.example.deviceowner.services.reporting.ServerBugAndLogReporter
import com.google.gson.Gson
import kotlinx.coroutines.*
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * IMPROVED HeartbeatService - Enhanced version with better response handling
 * 
 * KEY IMPROVEMENTS:
 * 1. ✅ Saves ALL heartbeat response data to local DB (next_payment, password, lock state)
 * 2. ✅ Proper priority-based response handling (deactivation > lock > payment reminder > unlock)
 * 3. ✅ Persistent storage of lock/deactivation state across app restarts
 * 4. ✅ Better error handling and logging
 * 5. ✅ Validates device ID before sending heartbeat
 * 6. ✅ Proper cleanup and resource management
 *
 * OFFLINE: When device is offline, heartbeat is queued (OfflineEvent). When back online,
 * OfflineSyncWorker sends queued heartbeats and processes the response (lock/deactivate/next_payment)
 * the same way. Server-side changes (lock, deactivation) are only applied after the next successful
 * heartbeat when online. Tamper is detected locally and applied immediately; tamper event is
 * queued and sent when online.
 */
class HeartbeatServiceImproved(
    private val context: Context,
    private val apiClient: ApiClient,
    private val deviceDataCollector: DeviceDataCollector
) {
    
    companion object {
        private const val TAG = "HeartbeatServiceImproved"
        private const val HEARTBEAT_INTERVAL_MILLIS = 30000L // 30 seconds
        private const val FIRST_HEARTBEAT_DELAY_MILLIS = 0L // IMMEDIATE
        private const val MAX_INTERVAL_VIOLATION_MS = 1000L // 1 second
    }
    
    private val heartbeatsSent = AtomicInteger(0)
    private val heartbeatsFailed = AtomicInteger(0)
    
    private val controlManager = RemoteDeviceControlManager(context)
    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val offlineEventDao = database.offlineEventDao()
    private val heartbeatSyncDao = database.heartbeatSyncDao()
    private val gson = Gson()
    
    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private var isScheduled = false
    private var serviceScope: CoroutineScope? = null
    
    private var lastHeartbeatTime = 0L
    private var lastReportedBlockReason: String? = null
    private var lastReportedBlockTime = 0L
    private val REPORT_THROTTLE_MS = 5 * 60 * 1000L

    fun schedulePeriodicHeartbeat(deviceId: String) {
        if (isScheduled) return

        // Validate device ID
        if (deviceId.isBlank() || deviceId.equals("unknown", ignoreCase = true)) {
            Log.e(TAG, "❌ HEARTBEAT NOT SCHEDULED: device_id is invalid or missing")
            reportHeartbeatBlockToServer("invalid_device_id_at_schedule", "Device must be registered first with a valid server-assigned device_id")
            return
        }
        
        if (deviceId.startsWith("ANDROID-")) {
            Log.e(TAG, "❌ HEARTBEAT NOT SCHEDULED: device_id is ANDROID-* (locally generated)")
            reportHeartbeatBlockToServer("android_prefix_device_id", "Use server-assigned device_id (e.g. DEV-B5AF7F0BEDEB) from registration")
            return
        }

        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        heartbeatRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                
                val timeSinceLastHeartbeat = if (lastHeartbeatTime > 0) {
                    now - lastHeartbeatTime
                } else {
                    HEARTBEAT_INTERVAL_MILLIS
                }
                
                val delayUntilNext = HEARTBEAT_INTERVAL_MILLIS - timeSinceLastHeartbeat
                val actualDelay = maxOf(delayUntilNext, 0L)
                
                handler.postDelayed(this, actualDelay)
                
                if (lastHeartbeatTime > 0) {
                    val violation = Math.abs(timeSinceLastHeartbeat - HEARTBEAT_INTERVAL_MILLIS)
                    if (violation > MAX_INTERVAL_VIOLATION_MS) {
                        Log.w(TAG, "⚠️ INTERVAL VIOLATION: Expected ${HEARTBEAT_INTERVAL_MILLIS}ms, got ${timeSinceLastHeartbeat}ms")
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

        handler.postDelayed(heartbeatRunnable!!, FIRST_HEARTBEAT_DELAY_MILLIS)
        isScheduled = true
        scheduleOfflineSyncWorker()
        Log.i(TAG, "✅ Periodic heartbeat scheduled – first in ${FIRST_HEARTBEAT_DELAY_MILLIS / 1000}s, then every 30s")
    }

    private suspend fun performHeartbeat(deviceId: String) {
        val currentDeviceId = com.example.deviceowner.data.DeviceIdProvider.getDeviceId(context)
        if (currentDeviceId == null) {
            Log.e(TAG, "❌ HEARTBEAT BLOCKED: device_id not found in storage")
            reportHeartbeatBlockToServer("device_id_not_found", "Device may have been deactivated")
            return
        }
        
        val effectiveDeviceId = currentDeviceId
        
        if (effectiveDeviceId.isBlank() || effectiveDeviceId.equals("unknown", ignoreCase = true)) {
            Log.w(TAG, "❌ HEARTBEAT BLOCKED: device_id missing or unknown")
            return
        }
        
        if (effectiveDeviceId.startsWith("ANDROID-")) {
            Log.w(TAG, "❌ HEARTBEAT BLOCKED: device_id is ANDROID-*")
            reportHeartbeatBlockToServer("device_id_android_prefix", "Use server-assigned device_id from registration")
            return
        }
        
        if (com.example.deviceowner.deactivation.DeviceOwnerDeactivationManager(context).isDeactivationInProgress()) {
            Log.w(TAG, "⏭️ HEARTBEAT BLOCKED: deactivation_in_progress=true")
            return
        }
        
        val heartbeatStartTime = System.currentTimeMillis()
        lastHeartbeatTime = heartbeatStartTime
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::heartbeat")?.apply {
            acquire(60_000)
        }
        
        try {
            val request = try {
                Log.d(TAG, "📊 Collecting heartbeat data...")
                collectHeartbeatData()
            } catch (e: Exception) {
                Log.e(TAG, "❌ HEARTBEAT BLOCKED: data collection failed - ${e.message}", e)
                heartbeatsFailed.incrementAndGet()
                reportHeartbeatBlockToServer("data_collection_failed", "Heartbeat data collection failed: ${e.message}")
                ServerBugAndLogReporter.postException(e, "Heartbeat data collection failed.")
                return
            }

            // Save to local DB with exact recorded time (for sync when back online – last 5 with exact time)
            val payloadJson = gson.toJson(request)
            val recordedAt = System.currentTimeMillis()
            val recordId = heartbeatSyncDao.insert(
                HeartbeatSyncEntity(
                    deviceId = effectiveDeviceId,
                    payloadJson = payloadJson,
                    recordedAt = recordedAt,
                    heartbeatTimestampIso = request.heartbeatTimestamp,
                    syncStatus = HeartbeatSyncEntity.STATUS_PENDING
                )
            )

            try {
                Log.d(TAG, "📤 Sending heartbeat to server...")
                Log.d(TAG, "   Device ID: $effectiveDeviceId")
                Log.d(TAG, "   Serial: ${request.serialNumber}")
                Log.d(TAG, "   IMEIs: ${request.deviceImeis}")
                
                val response = apiClient.sendHeartbeat(effectiveDeviceId, request)

                if (response.isSuccessful) {
                    heartbeatSyncDao.updateSyncStatus(recordId, HeartbeatSyncEntity.STATUS_SYNCED)
                    val count = heartbeatsSent.incrementAndGet()
                    Log.i(TAG, "✅ Heartbeat #$count sent successfully")
                    
                    try {
                        SharedPreferencesManager(context).setLastHeartbeatTime(System.currentTimeMillis())
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Could not update last heartbeat time: ${e.message}")
                    }
                    
                    // Keep local baseline in sync with server (same as Django storing last accepted data)
                    HeartbeatBaselineManager.saveBaselineFromHeartbeat(context, request)
                    
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
                    processOfflineHeartbeat(effectiveDeviceId, request)
                    handleHeartbeatFailure(request)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Heartbeat send failed: ${e.javaClass.simpleName} - ${e.message}", e)
                heartbeatsFailed.incrementAndGet()
                processOfflineHeartbeat(effectiveDeviceId, request)
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
                Log.d(TAG, "╔════════════════════════════════════════════════════════════╗")
                Log.d(TAG, "║         PROCESSING HEARTBEAT RESPONSE                      ║")
                Log.d(TAG, "╚════════════════════════════════════════════════════════════╝")
                Log.d(TAG, "📥 Response Details:")
                Log.d(TAG, "   - Device locked: ${response.isDeviceLocked()}")
                Log.d(TAG, "   - Deactivation: ${response.isDeactivationRequested()}")
                Log.d(TAG, "   - Next payment: ${response.getNextPaymentDateTime()}")
                Log.d(TAG, "   - Unlock password: ${if (response.getUnlockPassword() != null) "***present" else "none"}")
                Log.d(TAG, "   - Mismatches: ${response.comparisonResult?.totalMismatches ?: 0}")

                // ============================================================
                // STEP 1: SAVE HEARTBEAT RESPONSE TO LOCAL DATABASE
                // ============================================================
                Log.d(TAG, "💾 Step 1: Saving heartbeat response to local database...")
                try {
                    val prefs = SharedPreferencesManager(context)
                    
                    val nextPaymentDate = response.getNextPaymentDateTime()
                    val unlockPassword = response.getUnlockPassword()
                    
                    prefs.saveHeartbeatResponse(
                        nextPaymentDate = nextPaymentDate,
                        unlockPassword = unlockPassword,
                        serverTime = response.serverTime,
                        isLocked = response.isDeviceLocked(),
                        lockReason = response.getLockReason().takeIf { it.isNotBlank() }
                    )
                    // Save to local payment DB so lock screen and payment UI have next_payment + password
                    PaymentDataManager(context).apply {
                        saveNextPaymentInfo(nextPaymentDate, unlockPassword)
                        saveServerTime(response.serverTime)
                        saveLockState(response.isDeviceLocked(), response.getLockReason().takeIf { it.isNotBlank() })
                    }
                    
                    Log.d(TAG, "   ✅ Heartbeat response saved:")
                    Log.d(TAG, "      - Next payment: $nextPaymentDate")
                    Log.d(TAG, "      - Unlock password: ${if (unlockPassword != null) "saved" else "none"}")
                    Log.d(TAG, "      - Lock reason: ${response.getLockReason()}")
                    
                    // Save full response metadata
                    val responsePrefs = context.getSharedPreferences("heartbeat_full_response", Context.MODE_PRIVATE)
                    responsePrefs.edit().apply {
                        putString("last_response_json", gson.toJson(response))
                        putLong("last_response_timestamp", System.currentTimeMillis())
                        putBoolean("has_mismatches", response.comparisonResult?.totalMismatches ?: 0 > 0)
                        putInt("mismatch_count", response.comparisonResult?.totalMismatches ?: 0)
                        putInt("high_severity_count", response.comparisonResult?.highSeverityCount ?: 0)
                        apply()
                    }
                    Log.d(TAG, "   ✅ Full response metadata saved")
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Error saving response: ${e.message}", e)
                }

                // ============================================================
                // PRIORITY 1: DEACTIVATION REQUESTED
                // ============================================================
                if (response.isDeactivationRequested()) {
                    val source = when {
                        response.deactivateRequested == true || response.deactivation?.status == "requested" -> "deactivation_requested"
                        response.paymentComplete == true -> "payment_complete"
                        response.loanComplete == true -> "loan_complete"
                        response.loanStatus != null -> "loan_status=${response.loanStatus}"
                        else -> "content.reason"
                    }
                    Log.i(TAG, "🔓 ═══════════════════════════════════════════════════════════")
                    Log.i(TAG, "🔓 PRIORITY 1: DEACTIVATION REQUESTED (source=$source)")
                    Log.i(TAG, "🔓 ═══════════════════════════════════════════════════════════")
                    
                    // Save deactivation request to persistent storage
                    try {
                        val deactivationPrefs = context.getSharedPreferences("device_deactivation", Context.MODE_PRIVATE)
                        deactivationPrefs.edit().apply {
                            putBoolean("deactivation_requested", true)
                            putLong("deactivation_timestamp", System.currentTimeMillis())
                            putString("deactivation_source", source)
                            putString("deactivation_reason", response.message ?: "Server requested deactivation")
                            apply()
                        }
                        Log.d(TAG, "   ✅ Deactivation request saved to persistent storage")
                    } catch (e: Exception) {
                        Log.e(TAG, "   ⚠️ Error saving deactivation request: ${e.message}")
                    }
                    
                    // Start deactivation in background (non-blocking)
                    val appContext = context.applicationContext
                    CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                        try {
                            Log.i(TAG, "   🔄 Starting device deactivation process...")
                            val deactivationManager = com.example.deviceowner.deactivation.DeviceOwnerDeactivationManager(appContext)
                            deactivationManager.deactivateDeviceOwner()
                            Log.i(TAG, "   ✅ Device deactivation completed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "   ❌ Deactivation failed: ${e.message}", e)
                        }
                    }
                    return@withContext
                }

                // ============================================================
                // PRIORITY 2 & 3: LOCK / REMINDER from next_payment (same logic online & offline)
                // Use LockScreenStrategy: overdue → hard lock (with password), 1 day before → reminder, else unlocked
                // ============================================================
                val lockState = LockScreenStrategy.determineLockScreenState(response)
                Log.d(TAG, "   Lock state type: ${lockState.type}, daysUntilDue=${lockState.daysUntilDue}")

                when (lockState.type) {
                    LockScreenType.HARD_LOCK_SECURITY -> {
                        val reason = lockState.reason ?: response.getBlockReason()
                        Log.e(TAG, "🚨 HARD LOCK (Security) – applying")
                        saveDeviceLockState(context, true, reason, response)
                        controlManager.applyHardLock(reason, forceRestart = false, forceFromServerOrMismatch = true)
                        return@withContext
                    }
                    LockScreenType.HARD_LOCK_PAYMENT -> {
                        val reason = lockState.reason ?: "Payment overdue"
                        val formattedDate = lockState.nextPaymentDate?.let { LockScreenStrategy.formatDueDate(it) }
                        Log.e(TAG, "🚨 HARD LOCK (Payment overdue) – next was due: $formattedDate")
                        saveDeviceLockState(context, true, reason, response)
                        controlManager.applyHardLock(
                            reason = reason,
                            forceRestart = false,
                            forceFromServerOrMismatch = true,
                            nextPaymentDate = formattedDate,
                            organizationName = SharedPreferencesManager(context).getOrganizationName()
                        )
                        return@withContext
                    }
                    LockScreenType.SOFT_LOCK_REMINDER -> {
                        val prefs = context.getSharedPreferences("payment_reminder", Context.MODE_PRIVATE)
                        val lastShown = prefs.getLong("last_reminder_shown", 0L)
                        val throttleMs = 4 * 60 * 60 * 1000L
                        if ((System.currentTimeMillis() - lastShown) > throttleMs) {
                            prefs.edit().putLong("last_reminder_shown", System.currentTimeMillis()).apply()
                            val formattedDate = lockState.nextPaymentDate?.let { LockScreenStrategy.formatDueDate(it) }
                            val reason = "Payment reminder: Your next payment is due $formattedDate. Please pay before the due date to avoid device restrictions."
                            Log.w(TAG, "🔔 SOFT LOCK (Payment reminder) – due: $formattedDate")
                            controlManager.applySoftLock(
                                reason = reason,
                                triggerAction = "payment_reminder",
                                nextPaymentDate = formattedDate
                            )
                        }
                        return@withContext
                    }
                    LockScreenType.UNLOCKED, LockScreenType.DEACTIVATION -> { }
                }

                // PRIORITY 4: Device unlocked – clear lock if was locked
                Log.i(TAG, "✅ Device status OK or unlocked – clearing lock if needed")
                try {
                    if (controlManager.isHardLocked() || controlManager.getLockState() == RemoteDeviceControlManager.LOCK_SOFT) {
                        controlManager.unlockDevice()
                        SoftLockOverlayService.stopOverlay(context)
                        context.getSharedPreferences("device_lock_state", Context.MODE_PRIVATE).edit()
                            .putBoolean("device_locked", false)
                            .putLong("unlock_timestamp", System.currentTimeMillis())
                            .apply()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error unlocking: ${e.message}", e)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ CRITICAL ERROR processing heartbeat response: ${e.message}", e)
                Log.e(TAG, "   Stack trace: ${e.stackTraceToString()}")
            }
        }
    }

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

    /**
     * When device is offline (or send failed): run same process as Django locally –
     * compare current heartbeat to saved baseline, store result in local history,
     * apply lock if mismatch (high severity). Next payment/lock state from last
     * successful response remain in prefs and are used by lock screen.
     */
    private suspend fun processOfflineHeartbeat(deviceId: String, request: HeartbeatRequest) {
        withContext(Dispatchers.Main) {
            val baseline = HeartbeatBaselineManager.getBaseline(context)
            if (baseline.isNullOrEmpty() || !HeartbeatBaselineManager.hasBaseline(context)) {
                Log.w(TAG, "📴 Offline heartbeat: no baseline – skip local comparison (will sync when online)")
                return@withContext
            }
            Log.d(TAG, "📴 Offline heartbeat: comparing to local baseline (${baseline.keys.size} fields)")
            val result = OfflineHeartbeatComparator.compare(baseline, request)
            val comparisonJson = gson.toJson(mapOf(
                "mismatches" to result.mismatches,
                "highSeverityCount" to result.highSeverityCount,
                "mediumSeverityCount" to result.mediumSeverityCount,
                "totalMismatches" to result.totalMismatches,
                "shouldAutoLock" to result.shouldAutoLock,
                "lockReason" to result.lockReason,
                "baselineStatus" to result.baselineStatus,
                "comparedAt" to System.currentTimeMillis()
            ))
            HeartbeatBaselineManager.saveLastOfflineComparison(context, comparisonJson)
            val recordJson = gson.toJson(mapOf(
                "deviceId" to deviceId,
                "heartbeatTimestamp" to request.heartbeatTimestamp,
                "comparison" to comparisonJson,
                "sentAt" to System.currentTimeMillis()
            ))
            HeartbeatBaselineManager.appendLocalHeartbeatRecord(context, recordJson)
            if (result.shouldAutoLock && result.lockReason != null) {
                Log.e(TAG, "📴 Offline: mismatch detected (${result.highSeverityCount} high) – applying hard lock")
                try {
                    controlManager.applyHardLock(
                        reason = result.lockReason!!,
                        forceRestart = false,
                        forceFromServerOrMismatch = true
                    )
                    val lockPrefs = context.getSharedPreferences("device_lock_state", Context.MODE_PRIVATE)
                    lockPrefs.edit().apply {
                        putBoolean("device_locked", true)
                        putLong("lock_timestamp", System.currentTimeMillis())
                        putString("lock_reason", result.lockReason)
                        putBoolean("offline_lock", true)
                        apply()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Offline lock apply failed: ${e.message}", e)
                }
            } else if (result.totalMismatches > 0) {
                Log.w(TAG, "📴 Offline: ${result.totalMismatches} mismatch(es) (medium) – logged, no lock")
            } else {
                Log.d(TAG, "📴 Offline: heartbeat matches baseline – no action")
            }

            // Offline: apply lock/reminder from saved next_payment (same logic as online)
            applyPaymentLockFromLocalData()
        }
    }

    /**
     * Use saved next_payment date + password to apply hard lock (overdue) or reminder (1 day before).
     * Works when offline or when using last known data; same rules as LockScreenStrategy.
     */
    private fun applyPaymentLockFromLocalData() {
        val paymentManager = PaymentDataManager(context)
        val nextPaymentDate = paymentManager.getNextPaymentDate()
            ?: SharedPreferencesManager(context).getNextPaymentDate()
        val unlockPassword = paymentManager.getUnlockPassword()
            ?: SharedPreferencesManager(context).getUnlockPassword()
        val localState = LockScreenStrategy.determineLockScreenStateFromLocal(nextPaymentDate, unlockPassword)
            ?: return
        when (localState.type) {
            LockScreenType.HARD_LOCK_PAYMENT -> {
                val reason = localState.reason ?: "Payment overdue"
                val formattedDate = localState.nextPaymentDate?.let { LockScreenStrategy.formatDueDate(it) }
                Log.e(TAG, "📴 Offline: payment overdue (due $formattedDate) – applying hard lock from local data")
                try {
                    context.getSharedPreferences("device_lock_state", Context.MODE_PRIVATE).edit().apply {
                        putBoolean("device_locked", true)
                        putLong("lock_timestamp", System.currentTimeMillis())
                        putString("lock_reason", reason)
                        putBoolean("offline_lock", true)
                        apply()
                    }
                    controlManager.applyHardLock(
                        reason = reason,
                        forceRestart = false,
                        forceFromServerOrMismatch = true,
                        nextPaymentDate = formattedDate
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Offline payment lock failed: ${e.message}", e)
                }
            }
            LockScreenType.SOFT_LOCK_REMINDER -> {
                val prefs = context.getSharedPreferences("payment_reminder", Context.MODE_PRIVATE)
                if ((System.currentTimeMillis() - prefs.getLong("last_reminder_shown", 0L)) > 4 * 60 * 60 * 1000L) {
                    prefs.edit().putLong("last_reminder_shown", System.currentTimeMillis()).apply()
                    val formattedDate = localState.nextPaymentDate?.let { LockScreenStrategy.formatDueDate(it) }
                    val reason = "Payment reminder: Your next payment is due $formattedDate. Please pay before the due date."
                    controlManager.applySoftLock(reason = reason, triggerAction = "payment_reminder", nextPaymentDate = formattedDate)
                    Log.w(TAG, "📴 Offline: payment reminder (1 day before) – shown from local data")
                }
            }
            else -> { }
        }
    }

    private fun saveDeviceLockState(context: Context, isLocked: Boolean, reason: String, response: HeartbeatResponse) {
        try {
            context.getSharedPreferences("device_lock_state", Context.MODE_PRIVATE).edit().apply {
                putBoolean("device_locked", isLocked)
                putLong("lock_timestamp", System.currentTimeMillis())
                putString("lock_reason", reason)
                putInt("high_severity_count", response.comparisonResult?.highSeverityCount ?: 0)
                putInt("total_mismatches", response.comparisonResult?.totalMismatches ?: 0)
                apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Save lock state: ${e.message}")
        }
    }

    private fun handleHeartbeatFailure(request: HeartbeatRequest?) {
        // Heartbeat already saved in heartbeat_sync (local DB) before send – stays PENDING.
        // When back online OfflineSyncWorker sends last 5 pending with exact recorded time.
        scheduleOfflineSyncWorker()
        Log.d(TAG, "Heartbeat in local DB (PENDING) – will sync last 5 when online")
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
        com.example.deviceowner.data.DeviceIdProvider.clearCache()
        Log.i(TAG, "✅ Periodic heartbeat cancelled and cache cleared")
    }
}
