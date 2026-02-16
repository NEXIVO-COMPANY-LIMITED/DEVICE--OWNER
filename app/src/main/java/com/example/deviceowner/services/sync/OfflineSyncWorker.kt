package com.example.deviceowner.services.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.core.device.DeviceDataCollector
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest
import com.example.deviceowner.data.models.heartbeat.HeartbeatResponse
import com.example.deviceowner.data.models.tamper.TamperEventRequest
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Worker responsible for synchronizing offline events to the backend.
 * Runs when network is available. Processes server lock/unlock so device state matches server 100%.
 * After syncing queued heartbeats, sends one fresh heartbeat and applies server state (lock sync).
 */
class OfflineSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "OfflineSyncWorker"
    }

    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val offlineEventDao = database.offlineEventDao()
    private val apiClient = ApiClient()
    private val gson = Gson()
    private val controlManager = RemoteDeviceControlManager(context)
    private val deviceDataCollector = DeviceDataCollector(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val events = offlineEventDao.getAllEvents()
            if (events.isEmpty()) {
                Log.d(TAG, "No offline events to synchronize.")
                syncFreshHeartbeatForLockState()
                return@withContext Result.success()
            }

            Log.i(TAG, "Starting synchronization of ${events.size} offline events.")

            var successCount = 0
            for (event in events) {
                val success = when (event.eventType) {
                    "HEARTBEAT" -> syncHeartbeat(event.jsonData)
                    "TAMPER_SIGNAL" -> syncTamperEvent(event.jsonData)
                    else -> {
                        Log.w(TAG, "Unknown event type: ${event.eventType}. Skipping.")
                        true
                    }
                }

                if (success) {
                    offlineEventDao.deleteEvent(event)
                    successCount++
                } else {
                    Log.w(TAG, "Failed to sync event ID: ${event.id}. Will retry later.")
                }
            }

            Log.i(TAG, "Successfully synchronized $successCount/${events.size} events.")

            // When back online: send one fresh heartbeat and apply server lock state (100% device-server match)
            if (successCount > 0) {
                syncFreshHeartbeatForLockState()
            }

            if (successCount < events.size) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during offline synchronization: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Send one heartbeat with current device data and apply server response (lock/unlock).
     * Ensures device lock state matches online database when device comes back online.
     */
    private suspend fun syncFreshHeartbeatForLockState() {
        val deviceId = applicationContext.getSharedPreferences("device_data", Context.MODE_PRIVATE)
            .getString("device_id_for_heartbeat", null)
        if (deviceId.isNullOrBlank()) return
        try {
            val request = deviceDataCollector.collectHeartbeatData(deviceId)
            val response = apiClient.sendHeartbeat(deviceId, request)
            if (response.isSuccessful) {
                SharedPreferencesManager(applicationContext).setLastHeartbeatTime(System.currentTimeMillis())
                response.body()?.let { body -> processHeartbeatResponse(deviceId, body) }
                Log.d(TAG, "Fresh heartbeat sent – device lock state synced with server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fresh heartbeat for lock sync failed: ${e.message}", e)
        }
    }

    private suspend fun syncHeartbeat(jsonData: String): Boolean {
        return try {
            val heartbeatRequest = gson.fromJson(jsonData, HeartbeatRequest::class.java)
                ?: return false

            val sharedPref = applicationContext.getSharedPreferences("device_data", Context.MODE_PRIVATE)
            val deviceId = sharedPref.getString("device_id_for_heartbeat", null)
            if (deviceId.isNullOrBlank()) {
                Log.w(TAG, "device_id_for_heartbeat missing – keep event for retry when registered")
                return false
            }

            val response = apiClient.sendHeartbeat(deviceId, heartbeatRequest)
            if (response.isSuccessful) {
                SharedPreferencesManager(applicationContext).setLastHeartbeatTime(System.currentTimeMillis())
                response.body()?.let { body -> processHeartbeatResponse(deviceId, body) }
                Log.d(TAG, "Synced heartbeat – last_heartbeat_time updated, lock state applied")
            }
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing heartbeat: ${e.message}", e)
            false
        }
    }

    private suspend fun syncTamperEvent(jsonData: String): Boolean {
        return try {
            val request = gson.fromJson(jsonData, TamperEventRequest::class.java) ?: return false
            val deviceId = SharedPreferencesManager(applicationContext).getDeviceIdForHeartbeat()
                ?: SharedPreferencesManager(applicationContext).getDeviceId()
                ?: applicationContext.getSharedPreferences("device_registration", Context.MODE_PRIVATE).getString("device_id", null)
                ?: applicationContext.getSharedPreferences("device_data", Context.MODE_PRIVATE).getString("device_id_for_heartbeat", null)
            if (deviceId.isNullOrBlank()) {
                Log.w(TAG, "device_id missing – keep tamper event for retry")
                return false
            }
            val response = apiClient.postTamperEvent(deviceId, request)
            if (response.isSuccessful) Log.d(TAG, "Synced tamper event: ${request.tamperType}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing tamper event: ${e.message}", e)
            false
        }
    }

    /**
     * Apply server lock/unlock/deactivation so device state matches online 100%.
     */
    private suspend fun processHeartbeatResponse(deviceId: String, response: HeartbeatResponse) {
        withContext(Dispatchers.Main) {
            try {
                // Save response data (next_payment, server_time, lock state)
                SharedPreferencesManager(applicationContext).saveHeartbeatResponse(
                    nextPaymentDate = response.getNextPaymentDateTime(),
                    unlockPassword = response.getUnlockPassword(),
                    serverTime = response.serverTime,
                    isLocked = response.isDeviceLocked(),
                    lockReason = response.getLockReason().takeIf { it.isNotBlank() }
                )

                if (response.isDeactivationRequested()) {
                    Log.i(TAG, "Deactivation requested by server (from sync – deactivation/loan_complete/payment_complete) – starting immediately")
                    val appContext = applicationContext
                    CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                        try {
                            com.example.deviceowner.deactivation.DeviceOwnerDeactivationManager(appContext).deactivateDeviceOwner()
                            Log.i(TAG, "Deactivation completed from sync")
                        } catch (e: Exception) {
                            Log.e(TAG, "Deactivation failed: ${e.message}", e)
                        }
                    }
                    return@withContext
                }
                val shouldBlock = response.shouldBlockDevice()
                val blockReason = response.getBlockReason()
                if (shouldBlock) {
                    Log.e(TAG, "Server lock applied after sync – hard lock")
                    controlManager.applyHardLock(blockReason, forceRestart = false, forceFromServerOrMismatch = true)
                    return@withContext
                }
                if (controlManager.isHardLocked()) {
                    Log.i(TAG, "Server cleared lock – unlocking device after sync")
                    controlManager.unlockDevice()
                }
            } catch (e: Exception) {
                Log.e(TAG, "processHeartbeatResponse error: ${e.message}", e)
            }
        }
    }
}
