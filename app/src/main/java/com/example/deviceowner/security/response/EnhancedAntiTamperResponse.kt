package com.example.deviceowner.security.response

import android.content.Context
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.offline.OfflineEvent
import com.example.deviceowner.data.models.tamper.TamperEventRequest
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.services.reporting.ServerBugAndLogReporter
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced Anti-Tamper Response System
 * - On tamper: always apply hard lock (offline or online), then send tamper event to server (or queue if offline).
 * - No tamper: normal flow (heartbeat only).
 */
class EnhancedAntiTamperResponse(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedAntiTamper"
    }
    
    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val controlManager = RemoteDeviceControlManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Respond to tamper: always hard lock (offline or online), then send tamper event automatically.
     * @param extraData Optional (e.g. lock_applied_on_device, tamper_source) for backend/logs
     */
    fun respondToTamper(tamperType: String, severity: String, description: String, extraData: Map<String, Any?>? = null) {
        Log.e(TAG, "🚨 TAMPER DETECTED: $tamperType (Severity: $severity)")
        Log.e(TAG, "Description: $description")
        
        scope.launch {
            try {
                when (severity) {
                    "CRITICAL" -> respondWithHardLockAndNotify(tamperType, "CRITICAL", description, extraData)
                    "HIGH" -> respondWithHardLockAndNotify(tamperType, "HIGH", description, extraData)
                    "MEDIUM" -> respondWithHardLockAndNotify(tamperType, "MEDIUM", description, extraData)
                    else -> respondWithHardLockAndNotify(tamperType, "LOW", description, extraData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error responding to tamper", e)
            }
        }
    }
    
    /**
     * Apply hard lock IMMEDIATELY on device - no wait for server.
     * Server notification is fire-and-forget in background.
     * Device blocks user and applies kiosk mode locally first.
     */
    private suspend fun respondWithHardLockAndNotify(tamperType: String, severity: String, description: String, extraData: Map<String, Any?>? = null) {
        Log.e(TAG, "═══════════════════════════════════════════════════════")
        Log.e(TAG, "🚨 TAMPER ($severity) - APPLYING HARD LOCK IMMEDIATELY (no server wait)")
        Log.e(TAG, "═══════════════════════════════════════════════════════")

        // Step 1: IMMEDIATE local block - kiosk mode, hard lock (do NOT wait for server)
        val reason = "TAMPER: $tamperType - $description"
        controlManager.applyHardLock(reason, forceRestart = false, forceFromServerOrMismatch = true, tamperType = tamperType)
        deviceOwnerManager.applyAllCriticalRestrictions()
        deviceOwnerManager.applyRestrictions()

        Log.e(TAG, "✅ Device blocked locally - kiosk mode applied. User cannot proceed.")

        // Step 2: Send tamper to server in background (fire-and-forget, never blocks)
        val enrichedExtra = (extraData?.toMutableMap() ?: mutableMapOf()).apply {
            putIfAbsent("lock_applied_on_device", "hard")
            putIfAbsent("lock_type", "hard")
        }
        scope.launch { notifyRemoteServer(tamperType, severity, description, enrichedExtra) }
    }
    
    /**
     * Notify remote server about tamper event. Uses device_id from registration prefs.
     * On failure or offline, persists to OfflineEvent for sync when network is available.
     */
    private suspend fun notifyRemoteServer(
        tamperType: String,
        severity: String,
        description: String,
        extraData: Map<String, Any?>? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = SharedPreferencesManager(context).getDeviceIdForHeartbeat()
                    ?: SharedPreferencesManager(context).getDeviceId()
                    ?: context.getSharedPreferences("device_registration", Context.MODE_PRIVATE).getString("device_id", null)
                    ?: context.getSharedPreferences("device_data", Context.MODE_PRIVATE).getString("device_id_for_heartbeat", null)
                val request = TamperEventRequest.forDjango(tamperType, description, extraData)
                if (!deviceId.isNullOrBlank()) {
                    val response = ApiClient().postTamperEvent(deviceId, request)
                    if (response.isSuccessful) {
                        Log.d(TAG, "Tamper event sent to server: $tamperType -> tamper_type=${request.tamperType}")
                        ServerBugAndLogReporter.postLog(
                            "security",
                            "Error",
                            "TAMPER: $tamperType ($severity) - $description",
                            mapOf("tamper_type" to request.tamperType, "severity" to request.severity)
                        )
                        return@withContext
                    }
                }
                // Offline or no device_id or API failed: persist for later sync
                val db = DeviceOwnerDatabase.getDatabase(context)
                db.offlineEventDao().insertEvent(
                    OfflineEvent(
                        eventType = "TAMPER_SIGNAL",
                        jsonData = Gson().toJson(request),
                        timestamp = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Tamper event persisted for offline sync: $tamperType")
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying remote server", e)
                try {
                    val request = TamperEventRequest.forDjango(tamperType, description, extraData)
                    DeviceOwnerDatabase.getDatabase(context).offlineEventDao().insertEvent(
                        OfflineEvent(eventType = "TAMPER_SIGNAL", jsonData = Gson().toJson(request))
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to persist tamper for offline sync", e2)
                }
            }
        }
    }
    
    /**
     * Send tamper event to backend only (no hard lock). Use when lock was already applied (e.g. PackageRemovalReceiver).
     * @param extraData Optional data (e.g. original_sim_serial, new_sim_serial for SIM_CHANGE)
     */
    fun sendTamperToBackendOnly(
        tamperType: String,
        severity: String,
        description: String,
        extraData: Map<String, Any?>? = null
    ) {
        scope.launch {
            try {
                notifyRemoteServer(tamperType, severity, description, extraData)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending tamper to backend", e)
            }
        }
    }

    /**
     * Auto-recover from tamper (setup-only – no keyboard block)
     */
    fun autoRecover() {
        scope.launch {
            try {
                Log.d(TAG, "🔄 Starting auto-recovery (setup-only)...")
                deviceOwnerManager.applyRestrictionsForSetupOnly()
                Log.d(TAG, "✅ Auto-recovery completed – keyboard/touch stay enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-recovery", e)
            }
        }
    }
}
