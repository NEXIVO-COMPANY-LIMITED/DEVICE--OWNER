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

    private val heartbeatSyncDao = database.heartbeatSyncDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1) Sync last 5 pending heartbeats from local DB with exact recorded time
            val pendingHeartbeats = heartbeatSyncDao.getLast5Pending()
            val toSend = pendingHeartbeats.sortedBy { it.recordedAt } // chronological order (oldest first)
            var heartbeatSyncCount = 0
            for (record in toSend) {
                val sent = syncHeartbeatFromRecord(record)
                if (sent) {
                    heartbeatSyncDao.updateSyncStatus(record.id, com.example.deviceowner.data.local.database.entities.offline.HeartbeatSyncEntity.STATUS_SYNCED)
                    heartbeatSyncCount++
                    Log.d(TAG, "Synced heartbeat id=${record.id} recordedAt=${record.recordedAt} time=${record.heartbeatTimestampIso}")
                }
            }
            if (heartbeatSyncCount > 0) {
                Log.i(TAG, "Synced $heartbeatSyncCount heartbeat(s) from local DB (exact time recorded).")
            }

            // 2) Sync other offline events (tamper, etc.)
            val events = offlineEventDao.getAllEvents()
            var eventSyncCount = 0
            for (event in events) {
                val success = when (event.eventType) {
                    "HEARTBEAT" -> syncHeartbeat(event.jsonData) // legacy queue if any
                    "TAMPER_SIGNAL" -> syncTamperEvent(event.jsonData)
                    else -> {
                        Log.w(TAG, "Unknown event type: ${event.eventType}. Skipping.")
                        true
                    }
                }
                if (success) {
                    offlineEventDao.deleteEvent(event)
                    eventSyncCount++
                } else {
                    Log.w(TAG, "Failed to sync event ID: ${event.id}. Will retry later.")
                }
            }

            val totalSynced = heartbeatSyncCount + eventSyncCount
            if (totalSynced > 0) {
                Log.i(TAG, "Synchronized $heartbeatSyncCount heartbeat(s) from DB + $eventSyncCount other events.")
                syncFreshHeartbeatForLockState()
            } else if (events.isEmpty() && pendingHeartbeats.isEmpty()) {
                Log.d(TAG, "No offline events to synchronize.")
                syncFreshHeartbeatForLockState()
            }

            // Clean up old SYNCED records (keep last 24h)
            try {
                val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                heartbeatSyncDao.deleteSyncedOlderThan(cutoff)
            } catch (e: Exception) { Log.w(TAG, "Cleanup old heartbeats: ${e.message}") }

            val allPendingSynced = heartbeatSyncDao.getPendingCount() == 0 && eventSyncCount >= events.size
            if (allPendingSynced) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Error during offline synchronization: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Send one heartbeat from local DB record (payload has exact heartbeat_timestamp).
     */
    private suspend fun syncHeartbeatFromRecord(record: com.example.deviceowner.data.local.database.entities.offline.HeartbeatSyncEntity): Boolean {
        return try {
            val request = gson.fromJson(record.payloadJson, HeartbeatRequest::class.java) ?: return false
            val deviceId = record.deviceId
            if (deviceId.isBlank()) return false
            val response = apiClient.sendHeartbeat(deviceId, request)
            if (response.isSuccessful) {
                SharedPreferencesManager(applicationContext).setLastHeartbeatTime(System.currentTimeMillis())
                response.body()?.let { body -> processHeartbeatResponse(deviceId, body) }
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing heartbeat record id=${record.id}: ${e.message}", e)
            false
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
                val nextPaymentDate = response.getNextPaymentDateTime()
                val unlockPassword = response.getUnlockPassword()
                SharedPreferencesManager(applicationContext).saveHeartbeatResponse(
                    nextPaymentDate = nextPaymentDate,
                    unlockPassword = unlockPassword,
                    serverTime = response.serverTime,
                    isLocked = response.isDeviceLocked(),
                    lockReason = response.getLockReason().takeIf { it.isNotBlank() }
                )
                com.example.deviceowner.utils.storage.PaymentDataManager(applicationContext).apply {
                    saveNextPaymentInfo(nextPaymentDate, unlockPassword)
                    saveServerTime(response.serverTime)
                    saveLockState(response.isDeviceLocked(), response.getLockReason().takeIf { it.isNotBlank() })
                }

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
