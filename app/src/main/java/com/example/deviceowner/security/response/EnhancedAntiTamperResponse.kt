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
import kotlinx.coroutines.*

/**
 * Enhanced Anti-Tamper Response System - PROPER IMPLEMENTATION
 * - On tamper: applies local hard lock IMMEDIATELY (Offline-ready).
 * - Notifies Django server in background (Fire-and-forget).
 * - Queues events if offline.
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
     */
    fun respondToTamper(tamperType: String, severity: String, description: String, extraData: Map<String, Any?>? = null) {
        Log.e(TAG, "🚨 SECURITY VIOLATION DETECTED: $tamperType (Severity: $severity)")
        
        scope.launch {
            try {
                // Determine severity normalized for reporting
                val finalSeverity = when (severity.uppercase()) {
                    "CRITICAL", "HIGH", "MEDIUM" -> severity.uppercase()
                    else -> "LOW"
                }
                respondWithHardLockAndNotify(tamperType, finalSeverity, description, extraData)
            } catch (e: Exception) {
                Log.e(TAG, "Critical error in tamper response chain", e)
            }
        }
    }
    
    /**
     * 1. IMMEDIATE LOCAL LOCK - Does not wait for server response.
     * 2. BACKGROUND NOTIFICATION - Fire-and-forget reporting.
     */
    private suspend fun respondWithHardLockAndNotify(tamperType: String, severity: String, description: String, extraData: Map<String, Any?>? = null) {
        Log.e(TAG, "═══════════════════════════════════════════════════════")
        Log.e(TAG, "🚨 TAMPER RESPONSE: APPLYING INSTANT LOCKDOWN")
        Log.e(TAG, "═══════════════════════════════════════════════════════")

        // STEP 1: IMMEDIATE LOCAL LOCK (SCORCHED EARTH POLICY)
        // forceRestart = true ensures the lock activity is brought to front immediately
        val reason = "TAMPER: $tamperType - $description"
        controlManager.applyHardLock(reason, forceRestart = true, forceFromServerOrMismatch = true, tamperType = tamperType)
        
        // Apply all critical restrictions to prevent any user bypass
        deviceOwnerManager.applyAllCriticalRestrictions()
        deviceOwnerManager.applyRestrictions()

        Log.i(TAG, "✅ Device locked locally via Kiosk Mode (Tamper: $tamperType)")

        // STEP 2: BACKGROUND NOTIFICATION (OFFLINE PERSISTENT)
        val enrichedExtra = (extraData?.toMutableMap() ?: mutableMapOf()).apply {
            putIfAbsent("lock_applied_on_device", "hard")
            putIfAbsent("lock_type", "hard")
            putIfAbsent("reported_from", "EnhancedAntiTamperResponse")
        }
        
        scope.launch { notifyRemoteServer(tamperType, severity, description, enrichedExtra) }
    }
    
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
                
                val request = TamperEventRequest.forDjango(tamperType, description, extraData)
                
                if (!deviceId.isNullOrBlank()) {
                    val response = ApiClient().postTamperEvent(deviceId, request)
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Tamper report delivered to Django: $tamperType")
                        ServerBugAndLogReporter.postLog("security", "Tamper", "Delivered: $tamperType")
                        return@withContext
                    }
                }
                
                // IF OFFLINE OR API FAILED: Persistence
                queueOfflineTamperEvent(request)
                
            } catch (e: Exception) {
                Log.e(TAG, "Network reporting failed, queuing for offline sync", e)
                val request = TamperEventRequest.forDjango(tamperType, description, extraData)
                queueOfflineTamperEvent(request)
            }
        }
    }

    private suspend fun queueOfflineTamperEvent(request: TamperEventRequest) {
        try {
            val db = DeviceOwnerDatabase.getDatabase(context)
            db.offlineEventDao().insertEvent(
                OfflineEvent(
                    eventType = "TAMPER_SIGNAL",
                    jsonData = Gson().toJson(request),
                    timestamp = System.currentTimeMillis()
                )
            )
            Log.w(TAG, "💾 Tamper event queued for offline sync: ${request.tamperType}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist offline tamper event", e)
        }
    }
    
    fun sendTamperToBackendOnly(tamperType: String, severity: String, description: String, extraData: Map<String, Any?>? = null) {
        scope.launch { notifyRemoteServer(tamperType, severity, description, extraData) }
    }

    fun autoRecover() {
        scope.launch {
            try {
                Log.d(TAG, "🔄 Auto-recovery (re-enforcing restrictions)...")
                deviceOwnerManager.applyRestrictionsForSetupOnly()
            } catch (e: Exception) {
                Log.e(TAG, "Recovery failed", e)
            }
        }
    }
}
