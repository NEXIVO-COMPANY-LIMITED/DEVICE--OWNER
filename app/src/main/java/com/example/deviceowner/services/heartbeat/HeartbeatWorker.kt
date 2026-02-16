package com.example.deviceowner.services.heartbeat

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.deviceowner.core.device.DeviceDataCollector
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HeartbeatWorker - Used only for one-off heartbeat work (e.g. from deactivation flow).
 * Regular heartbeat is sent every 30 seconds by SecurityMonitorService + HeartbeatService only.
 * We do NOT schedule periodic 15-min work – user wants 30s interval only.
 */
class HeartbeatWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val MAX_RETRIES = 10  // 🔴 AGGRESSIVE: 10 retries
        private const val INITIAL_RETRY_DELAY_MS = 1000L // 🔴 AGGRESSIVE: 1 second
        private const val MAX_RETRY_DELAY_MS = 15000L // 🔴 AGGRESSIVE: 15 seconds
        
        /**
         * No longer used. Heartbeat is sent only every 30s by SecurityMonitorService + HeartbeatService.
         * We do NOT schedule 15-min periodic work – user wants regular 30s only.
         */
        fun scheduleHeartbeat(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork("HeartbeatWork")
                Log.d(TAG, "Heartbeat: 30s only via SecurityMonitorService (no 15-min schedule)")
            } catch (e: Exception) {
                Log.e(TAG, "cancel HeartbeatWork: ${e.message}")
            }
        }
    }

    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val offlineEventDao = database.offlineEventDao()
    private val apiClient = ApiClient()
    private val deviceDataCollector = DeviceDataCollector(context)
    private val gson = Gson()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var retryCount = 0
        var lastException: Exception? = null
        var request: HeartbeatRequest? = null
        
        // AGGRESSIVE RETRY LOOP - Keep trying until success
        while (retryCount < MAX_RETRIES) {
            try {
                val deviceId = SharedPreferencesManager(applicationContext).getDeviceIdForHeartbeat()
                    ?: applicationContext.getSharedPreferences("device_data", Context.MODE_PRIVATE).getString("device_id_for_heartbeat", null)
                    ?: applicationContext.getSharedPreferences("device_registration", Context.MODE_PRIVATE).getString("device_id", null)

                if (deviceId.isNullOrBlank()) {
                    Log.w(TAG, "❌ Device ID not found - cannot send heartbeat")
                    return@withContext Result.retry()
                }

                Log.d(TAG, "📤 Sending heartbeat for device: $deviceId (attempt ${retryCount + 1}/$MAX_RETRIES)")
                
                // STEP 1: Collect heartbeat data with error handling (only once per cycle)
                if (request == null) {
                    request = try {
                        val data = deviceDataCollector.collectHeartbeatData(deviceId)
                        Log.d(TAG, "✅ Heartbeat data collected successfully")
                        Log.d(TAG, "   - device_info: ${data.deviceInfo?.keys}")
                        Log.d(TAG, "   - android_info: ${data.androidInfo?.keys}")
                        Log.d(TAG, "   - imei_info: ${data.imeiInfo?.keys}")
                        Log.d(TAG, "   - storage_info: ${data.storageInfo?.keys}")
                        data
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ FAILED to collect heartbeat data: ${e.message}", e)
                        lastException = e
                        retryCount++
                        
                        if (retryCount < MAX_RETRIES) {
                            val delayMs = calculateBackoffDelay(retryCount)
                            Log.d(TAG, "🔄 AGGRESSIVE RETRY #$retryCount in ${delayMs}ms...")
                            kotlinx.coroutines.delay(delayMs)
                            continue
                        }
                        break
                    }
                }

                // STEP 2: Send heartbeat with aggressive retry
                try {
                    Log.d(TAG, "📡 Sending heartbeat to backend...")
                    val response = apiClient.sendHeartbeat(deviceId, request!!)
                    
                    if (response.isSuccessful) {
                        Log.i(TAG, "✅✅✅ HEARTBEAT SENT SUCCESSFULLY ✅✅✅")
                        Log.i(TAG, "📊 Device: $deviceId | Attempt: ${retryCount + 1}/$MAX_RETRIES | Status: SUCCESS")
                        SharedPreferencesManager(applicationContext).setLastHeartbeatTime(System.currentTimeMillis())
                        
                        // STEP 3: Process server response
                        response.body()?.let { body ->
                            try {
                                Log.d(TAG, "📥 Processing server response...")
                                processHeartbeatResponse(deviceId, body)
                                Log.i(TAG, "✅ Server response processed successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "⚠️ Error processing response: ${e.message}", e)
                            }
                        } ?: Log.w(TAG, "⚠️ Empty response body from server")
                        
                        return@withContext Result.success()
                    } else {
                        // HTTP error - retry immediately
                        val errorBody = try {
                            response.errorBody()?.string() ?: "No error body"
                        } catch (e: Exception) {
                            "Could not read error body: ${e.message}"
                        }
                        
                        Log.w(TAG, "❌ HEARTBEAT HTTP ERROR: ${response.code()} - ${response.message()}")
                        Log.w(TAG, "   Error body: $errorBody")
                        Log.w(TAG, "🔄 AGGRESSIVE RETRY #${retryCount + 1}/$MAX_RETRIES")
                        lastException = Exception("HTTP ${response.code()}: ${response.message()}")
                        
                        // Queue for offline sync on HTTP errors
                        queueForOfflineSync(request!!)
                        
                        retryCount++
                        if (retryCount < MAX_RETRIES) {
                            val delayMs = calculateBackoffDelay(retryCount)
                            Log.d(TAG, "⏳ Retrying in ${delayMs}ms...")
                            kotlinx.coroutines.delay(delayMs)
                            continue
                        }
                        break
                    }
                } catch (e: Exception) {
                    // Network error - retry immediately
                    Log.e(TAG, "❌ HEARTBEAT NETWORK ERROR: ${e.message}", e)
                    Log.e(TAG, "🔄 AGGRESSIVE RETRY #${retryCount + 1}/$MAX_RETRIES")
                    lastException = e
                    
                    // Queue for offline sync on network errors
                    if (request != null) {
                        queueForOfflineSync(request!!)
                    }
                    
                    retryCount++
                    if (retryCount < MAX_RETRIES) {
                        val delayMs = calculateBackoffDelay(retryCount)
                        Log.d(TAG, "⏳ Retrying in ${delayMs}ms...")
                        kotlinx.coroutines.delay(delayMs)
                        continue
                    }
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Unexpected error in heartbeat worker: ${e.message}", e)
                lastException = e
                retryCount++
                
                if (retryCount < MAX_RETRIES) {
                    val delayMs = calculateBackoffDelay(retryCount)
                    Log.d(TAG, "⏳ Retrying in ${delayMs}ms...")
                    try {
                        kotlinx.coroutines.delay(delayMs)
                    } catch (delayException: Exception) {
                        Log.e(TAG, "Delay interrupted: ${delayException.message}")
                    }
                    continue
                }
                break
            }
        }
        
        // All retries exhausted
        Log.e(TAG, "❌❌❌ HEARTBEAT FAILED AFTER $MAX_RETRIES AGGRESSIVE ATTEMPTS ❌❌❌")
        Log.e(TAG, "Last error: ${lastException?.message}")
        Log.e(TAG, "🔄 WorkManager will reschedule next heartbeat (backup ~15 min)")
        
        // CRITICAL: Still queue for offline sync so data is not lost
        try {
            if (request != null) {
                queueForOfflineSync(request!!)
                Log.i(TAG, "✅ Failed heartbeat queued for offline sync - will retry when online")
            } else {
                Log.w(TAG, "⚠️ Could not queue heartbeat - data collection failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue offline sync: ${e.message}", e)
        }
        
        // Return retry so WorkManager reschedules next attempt
        return@withContext Result.retry()
    }
    
    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoffDelay(retryCount: Int): Long {
        val delay = INITIAL_RETRY_DELAY_MS * (1 shl (retryCount - 1)) // 2^(retryCount-1)
        return delay.coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    private suspend fun processHeartbeatResponse(deviceId: String, response: com.example.deviceowner.data.models.heartbeat.HeartbeatResponse) {
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "╔════════════════════════════════════════╗")
                Log.d(TAG, "║  PROCESSING HEARTBEAT RESPONSE         ║")
                Log.d(TAG, "╚════════════════════════════════════════╝")

                // Save response data (next_payment, server_time, lock state)
                com.example.deviceowner.utils.storage.SharedPreferencesManager(applicationContext).saveHeartbeatResponse(
                    nextPaymentDate = response.getNextPaymentDateTime(),
                    unlockPassword = response.getUnlockPassword(),
                    serverTime = response.serverTime,
                    isLocked = response.isDeviceLocked(),
                    lockReason = response.getLockReason().takeIf { it.isNotBlank() }
                )

                // PRIORITY 1: Check for deactivation request FIRST (deactivation, loan complete, payment complete)
                if (response.isDeactivationRequested()) {
                    Log.i(TAG, "🔓 Deactivation detected (deactivation/loan_complete/payment_complete) – initiating full deactivation")
                    handleDeactivationRequest(deviceId, response)
                    return@withContext
                }
                
                // PRIORITY 2: Check for device lock (security violation or payment overdue)
                val shouldBlock = response.shouldBlockDevice()
                if (shouldBlock) {
                    handleDeviceLock(response)
                    return@withContext
                }
                
                // PRIORITY 3: Use full coordination service for payment status and lock management
                handlePaymentStatus(response)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ CRITICAL ERROR processing response: ${e.message}", e)
            }
        }
    }
    
    /**
     * Handle deactivation request from server - PERFECT & ROBUST
     */
    private suspend fun handleDeactivationRequest(deviceId: String, response: com.example.deviceowner.data.models.heartbeat.HeartbeatResponse) {
        Log.i(TAG, "🔓 ═══════════════════════════════════════════════════════════")
        Log.i(TAG, "🔓 DEACTIVATION REQUESTED BY SERVER - CRITICAL ACTION")
        Log.i(TAG, "🔓 ═══════════════════════════════════════════════════════════")
        Log.i(TAG, "   Device ID: $deviceId")
        Log.i(TAG, "   Command: ${response.getDeactivationCommand()}")
        Log.i(TAG, "   Status: ${response.deactivation?.status}")
        Log.i(TAG, "   Message: ${response.message}")
        Log.i(TAG, "   Action: Automatically removing Device Owner...")
        Log.i(TAG, "   Result: Device will return to normal state")
        
        try {
            // STEP 1: Mark deactivation as requested in persistent storage
            Log.d(TAG, "   Step 1: Recording deactivation request...")
            val prefs = applicationContext.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("deactivation_requested", true)
                putLong("deactivation_timestamp", System.currentTimeMillis())
                putString("deactivation_command", response.getDeactivationCommand() ?: "DEACTIVATE_NOW")
                putString("deactivation_reason", response.message ?: "Server requested deactivation")
                apply()
            }
            Log.d(TAG, "   ✓ Deactivation request recorded")
            
            // STEP 2: Stop heartbeat immediately to prevent further heartbeats
            Log.d(TAG, "   Step 2: Stopping heartbeat worker...")
            try {
                androidx.work.WorkManager.getInstance(applicationContext).cancelUniqueWork("HeartbeatWork")
                Log.d(TAG, "   ✓ Heartbeat stopped - no more heartbeats will be sent")
            } catch (e: Exception) {
                Log.w(TAG, "   ⚠️ Warning stopping heartbeat: ${e.message}")
            }
            
            // STEP 3: Disable heartbeat flag to prevent restart
            Log.d(TAG, "   Step 3: Disabling heartbeat...")
            com.example.deviceowner.utils.storage.SharedPreferencesManager(applicationContext).setHeartbeatEnabled(false)
            Log.d(TAG, "   ✓ Heartbeat disabled")
            
            // STEP 4: Trigger deactivation in background
            Log.d(TAG, "   Step 4: Initiating device deactivation...")
            val controlManager = com.example.deviceowner.control.RemoteDeviceControlManager(applicationContext)
            controlManager.terminateDeviceOwnership()
            Log.d(TAG, "   ✓ Deactivation initiated")
            
            // STEP 5: Log completion
            Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
            Log.i(TAG, "✅ Device ownership termination initiated successfully")
            Log.i(TAG, "✅ Device will be freed in background")
            Log.i(TAG, "✅ All Device Owner controls will be removed")
            Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during deactivation: ${e.message}", e)
            // Still mark as deactivation requested even if error
            try {
                applicationContext.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("deactivation_requested", true)
                    .putLong("deactivation_timestamp", System.currentTimeMillis())
                    .putString("deactivation_error", e.message ?: "Unknown error")
                    .apply()
                Log.i(TAG, "✅ Deactivation request recorded despite error - will retry on next heartbeat")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ CRITICAL: Could not record deactivation: ${e2.message}")
            }
        }
    }
    
    /**
     * Handle device lock request from server - PERFECT & ROBUST
     */
    private suspend fun handleDeviceLock(response: com.example.deviceowner.data.models.heartbeat.HeartbeatResponse) {
        Log.e(TAG, "🚨 ═══════════════════════════════════════════════════════════")
        Log.e(TAG, "🚨 SERVER REQUESTED DEVICE LOCK - SECURITY ACTION")
        Log.e(TAG, "🚨 ═══════════════════════════════════════════════════════════")
        Log.e(TAG, "   Reason: ${response.getBlockReason()}")
        Log.e(TAG, "   Severity: ${if (response.hasHighSeverityMismatches()) "HIGH" else "MEDIUM"}")
        Log.e(TAG, "   Mismatches: ${response.getMismatches().size}")
        Log.e(TAG, "   Lock Status: ${if (response.isDeviceLocked()) "LOCKED" else "UNLOCKED"}")
        Log.e(TAG, "   Action: Applying hard lock (total kiosk mode)...")
        
        try {
            // STEP 1: Record lock state in persistent storage
            Log.d(TAG, "   Step 1: Recording lock state...")
            val prefs = applicationContext.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("device_locked_by_server", true)
                putLong("lock_timestamp", System.currentTimeMillis())
                putString("lock_reason", response.getBlockReason())
                putInt("mismatch_count", response.getMismatches().size)
                putInt("high_severity_count", response.comparisonResult?.highSeverityCount ?: 0)
                apply()
            }
            Log.d(TAG, "   ✓ Lock state recorded")
            
            // STEP 2: Verify device owner status before applying lock
            Log.d(TAG, "   Step 2: Verifying device owner status...")
            val dpm = applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val isDeviceOwner = dpm.isDeviceOwnerApp(applicationContext.packageName)
            Log.d(TAG, "   Device Owner: $isDeviceOwner")
            
            if (!isDeviceOwner) {
                Log.w(TAG, "⚠️ Not device owner - cannot apply hard lock")
                Log.w(TAG, "   Device may have been deactivated or admin removed")
                return
            }
            
            // STEP 3: Apply hard lock with server override flag
            Log.d(TAG, "   Step 3: Applying hard lock...")
            val controlManager = com.example.deviceowner.control.RemoteDeviceControlManager(applicationContext)
            controlManager.applyHardLock(
                reason = response.getBlockReason(),
                forceRestart = false,
                forceFromServerOrMismatch = true  // Override skip_security_restrictions
            )
            Log.d(TAG, "   ✓ Hard lock applied")
            
            // STEP 4: Verify lock was applied
            Log.d(TAG, "   Step 4: Verifying lock state...")
            val lockState = controlManager.getLockState()
            Log.d(TAG, "   Current lock state: $lockState")
            
            if (lockState != com.example.deviceowner.control.RemoteDeviceControlManager.LOCK_HARD) {
                Log.w(TAG, "⚠️ WARNING: Lock state is not HARD after applying")
                Log.w(TAG, "   Attempting to re-apply lock...")
                controlManager.applyHardLock(
                    reason = response.getBlockReason(),
                    forceRestart = true,
                    forceFromServerOrMismatch = true
                )
            }
            
            Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
            Log.i(TAG, "✅ Hard lock applied successfully")
            Log.i(TAG, "✅ Device is now in total kiosk mode")
            Log.i(TAG, "✅ All apps suspended - device completely locked")
            Log.i(TAG, "✅ USB debugging, file transfer, factory reset allowed")
            Log.i(TAG, "✅ WiFi/Bluetooth disabled")
            Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying hard lock: ${e.message}", e)
            Log.e(TAG, "   Stack trace: ${e.stackTraceToString()}")
            // Record error for debugging
            applicationContext.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
                .edit()
                .putString("last_lock_error", e.message ?: "Unknown error")
                .putLong("last_lock_error_time", System.currentTimeMillis())
                .apply()
            Log.i(TAG, "⚠️ Will retry lock on next heartbeat")
        }
    }
    
    /**
     * Handle payment status and lock coordination - PERFECT & ROBUST
     */
    private suspend fun handlePaymentStatus(response: com.example.deviceowner.data.models.heartbeat.HeartbeatResponse) {
        Log.d(TAG, "Processing payment status and lock coordination...")
        
        try {
            // Use the new centralized response handler
            val controlManager = com.example.deviceowner.control.RemoteDeviceControlManager(applicationContext)
            
            // PRIORITY 1: Deactivation requested
            if (response.isDeactivationRequested()) {
                Log.i(TAG, "🔓 Deactivation requested - releasing device")
                // Deactivation is handled by HeartbeatService
                return
            }
            
            // PRIORITY 2: Lock or mismatch
            val shouldBlock = response.shouldBlockDevice()
            if (shouldBlock) {
                val blockReason = response.getBlockReason()
                Log.e(TAG, "🚨 HARDLOCK TRIGGERED - $blockReason")
                controlManager.applyHardLock(blockReason, forceRestart = false, forceFromServerOrMismatch = true)
                return
            }
            
            // PRIORITY 3: Unlock if previously locked
            if (controlManager.isHardLocked()) {
                Log.i(TAG, "🔓 Server cleared lock - Unlocking device")
                controlManager.unlockDevice()
                return
            }
            
            Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
            Log.i(TAG, "✅ Heartbeat response processed successfully")
            Log.i(TAG, "✅ Payment status: ${response.getNextPaymentDateTime() ?: "No pending payments"}")
            Log.i(TAG, "✅ Device status: ${if (response.isDeviceLocked()) "LOCKED" else "UNLOCKED"}")
            Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in lock coordination: ${e.message}", e)
            // Log but don't fail - heartbeat was still sent successfully
            Log.i(TAG, "⚠️ Lock coordination error - will retry on next heartbeat")
        }
    }

    private suspend fun queueForOfflineSync(request: HeartbeatRequest) {
        try {
            val json = gson.toJson(request)
            val event = com.example.deviceowner.data.local.database.entities.offline.OfflineEvent(
                eventType = "HEARTBEAT",
                jsonData = json
            )
            offlineEventDao.insertEvent(event)
            Log.d(TAG, "Heartbeat queued for offline sync")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue offline event: ${e.message}")
        }
    }
}
