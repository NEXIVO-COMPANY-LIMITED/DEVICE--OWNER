package com.microspace.payo.services.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.microspace.payo.control.RemoteDeviceControlManager
import com.microspace.payo.core.device.DeviceDataCollector
import com.microspace.payo.data.local.database.DeviceOwnerDatabase
import com.microspace.payo.data.models.heartbeat.HeartbeatRequest
import com.microspace.payo.data.models.heartbeat.HeartbeatResponse
import com.microspace.payo.data.models.tamper.TamperEventRequest
import com.microspace.payo.data.remote.ApiClient
import com.microspace.payo.utils.storage.SharedPreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Worker responsible for synchronizing offline events to the backend.
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
            val pendingHeartbeats = heartbeatSyncDao.getLast5Pending()
            val toSend = pendingHeartbeats.sortedBy { it.recordedAt }
            var heartbeatSyncCount = 0
            for (record in toSend) {
                val sent = syncHeartbeatFromRecord(record)
                if (sent) {
                    heartbeatSyncDao.updateSyncStatus(record.id, com.microspace.payo.data.local.database.entities.offline.HeartbeatSyncEntity.STATUS_SYNCED)
                    heartbeatSyncCount++
                }
            }

            val events = offlineEventDao.getAllEvents()
            var eventSyncCount = 0
            for (event in events) {
                val success = when (event.eventType) {
                    "HEARTBEAT" -> syncHeartbeat(event.jsonData)
                    "TAMPER_SIGNAL" -> syncTamperEvent(event.jsonData)
                    else -> true
                }
                if (success) {
                    offlineEventDao.deleteEvent(event)
                    eventSyncCount++
                }
            }

            if (heartbeatSyncCount > 0 || eventSyncCount > 0 || (events.isEmpty() && pendingHeartbeats.isEmpty())) {
                syncFreshHeartbeatForLockState()
            }

            try {
                val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                heartbeatSyncDao.deleteSyncedOlderThan(cutoff)
            } catch (_: Exception) { }

            if (heartbeatSyncDao.getPendingCount() == 0 && eventSyncCount >= events.size) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun syncHeartbeatFromRecord(record: com.microspace.payo.data.local.database.entities.offline.HeartbeatSyncEntity): Boolean {
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
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun syncFreshHeartbeatForLockState() {
        val deviceId = applicationContext.getSharedPreferences("device_data", Context.MODE_PRIVATE)
            .getString("device_id_for_heartbeat", null)
        if (deviceId.isNullOrBlank()) return
        try {
            val request = deviceDataCollector.collectHeartbeatData()
            val response = apiClient.sendHeartbeat(deviceId, request)
            if (response.isSuccessful) {
                SharedPreferencesManager(applicationContext).setLastHeartbeatTime(System.currentTimeMillis())
                response.body()?.let { body -> processHeartbeatResponse(deviceId, body) }
            }
        } catch (_: Exception) { }
    }

    private suspend fun syncHeartbeat(jsonData: String): Boolean {
        return try {
            val heartbeatRequest = gson.fromJson(jsonData, HeartbeatRequest::class.java) ?: return false
            val sharedPref = applicationContext.getSharedPreferences("device_data", Context.MODE_PRIVATE)
            val deviceId = sharedPref.getString("device_id_for_heartbeat", null)
            if (deviceId.isNullOrBlank()) return false
            val response = apiClient.sendHeartbeat(deviceId, heartbeatRequest)
            if (response.isSuccessful) {
                SharedPreferencesManager(applicationContext).setLastHeartbeatTime(System.currentTimeMillis())
                response.body()?.let { body -> processHeartbeatResponse(deviceId, body) }
            }
            response.isSuccessful
        } catch (_: Exception) {
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
            if (deviceId.isNullOrBlank()) return false
            val response = apiClient.postTamperEvent(deviceId, request)
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun processHeartbeatResponse(deviceId: String, response: HeartbeatResponse) {
        withContext(Dispatchers.Main) {
            try {
                val nextPaymentDate = response.getNextPaymentDateTime()
                val unlockPassword = response.getUnlockPassword()
                SharedPreferencesManager(applicationContext).saveHeartbeatResponse(
                    nextPaymentDate = nextPaymentDate,
                    unlockPassword = unlockPassword,
                    serverTime = response.serverTime,
                    isLocked = response.isDeviceLocked(),
                    lockReason = response.getLockReason().takeIf { it.isNotBlank() }
                )
                com.microspace.payo.utils.storage.PaymentDataManager(applicationContext).apply {
                    saveNextPaymentInfo(nextPaymentDate, unlockPassword)
                    saveServerTime(response.serverTime)
                    saveLockState(response.isDeviceLocked(), response.getLockReason().takeIf { it.isNotBlank() })
                }

                if (response.isDeactivationRequested()) {
                    val appContext = applicationContext
                    CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                        try {
                            com.microspace.payo.deactivation.DeviceOwnerDeactivationManager(appContext).deactivateDeviceOwner()
                        } catch (_: Exception) { }
                    }
                    return@withContext
                }
                val shouldBlock = response.isDeviceLocked()
                val blockReason = response.getLockReason()
                if (shouldBlock) {
                    controlManager.applyHardLock(blockReason, forceRestart = false, forceFromServerOrMismatch = true)
                    return@withContext
                }
                if (controlManager.isHardLocked()) {
                    controlManager.unlockDevice()
                }
            } catch (_: Exception) { }
        }
    }
}




