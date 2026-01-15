package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.deviceowner.manager.LockManager
import com.example.deviceowner.data.api.ApiClient
import com.example.deviceowner.managers.CommandQueue
import com.example.deviceowner.managers.OfflineCommand
import kotlinx.coroutines.*

/**
 * Heartbeat Service
 * 
 * Sends device data to backend: POST /api/devices/{device_id}/data/
 * Backend response includes lock status from database
 * Device auto-locks/unlocks based on response
 * 
 * NO Firebase - lock status stored in backend database via POST /api/devices/{device_id}/manage/
 */
class HeartbeatService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val apiService = ApiClient.apiService
    private lateinit var lockManager: LockManager
    private lateinit var commandQueue: CommandQueue
    
    companion object {
        private const val TAG = "HeartbeatService"
        private const val HEARTBEAT_INTERVAL = 60000L // 1 minute
    }
    
    override fun onCreate() {
        super.onCreate()
        lockManager = LockManager.getInstance(this)
        commandQueue = CommandQueue(this)
        startHeartbeat()
        
        // Start CommandQueueService for offline command processing
        startCommandQueueService()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    /**
     * Start heartbeat loop
     */
    private fun startHeartbeat() {
        serviceScope.launch {
            while (isActive) {
                try {
                    sendHeartbeat()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in heartbeat", e)
                }
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }
    
    /**
     * Send heartbeat to backend
     * 
     * Endpoint: POST /api/devices/{device_id}/data/
     * 
     * Response includes lock status from backend database
     * Device auto-locks/unlocks based on response
     */
    private suspend fun sendHeartbeat() {
        try {
            // Get device ID
            val deviceId = getDeviceIdFromPrefs()
            
            // Get current lock status
            val lockStatus = lockManager.getLocalLockStatus()
            
            // Create heartbeat data
            val heartbeatData = mapOf(
                "device_id" to deviceId,
                "timestamp" to System.currentTimeMillis(),
                "device_info" to getDeviceInfo(),
                "lock_status" to lockStatus  // Include current lock status
            )
            
            Log.d(TAG, "Sending heartbeat to /api/devices/$deviceId/data/")
            
            // Send to existing heartbeat endpoint: POST /api/devices/{device_id}/data/
            val response = apiService.sendDeviceData(deviceId, heartbeatData)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                Log.d(TAG, "✓ Heartbeat sent successfully")
                
                // Handle response - includes lock status from backend database
                if (responseBody != null) {
                    @Suppress("UNCHECKED_CAST")
                    val responseMap = responseBody as? Map<String, Any?> ?: emptyMap()
                    handleHeartbeatResponse(responseMap)
                }
            } else {
                Log.e(TAG, "✗ Heartbeat failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending heartbeat", e)
        }
    }
    
    /**
     * Handle heartbeat response
     * 
     * Response from POST /api/devices/{device_id}/data/ includes:
     * - lock_status from backend database
     * - commands from backend (Feature 4.9: Offline Command Queue)
     * 
     * If admin locked device via POST /api/devices/{device_id}/manage/
     * → Backend stored lock status in database
     * → Heartbeat response includes lock_status.is_locked = true
     * → Device AUTO-LOCKS
     * 
     * If admin unlocked device via POST /api/devices/{device_id}/manage/
     * → Backend updated lock status in database
     * → Heartbeat response includes lock_status.is_locked = false
     * → Device AUTO-UNLOCKS
     * 
     * Feature 4.9: Backend can also send commands in heartbeat response
     * → Commands are queued for offline execution
     * → CommandQueueService processes them automatically
     */
    private suspend fun handleHeartbeatResponse(response: Map<String, Any?>) {
        try {
            Log.d(TAG, "Handling heartbeat response from /data/ endpoint")
            
            // Check if response contains lock status from backend database
            val lockStatus = response["lock_status"] as? Map<String, Any?>
            
            if (lockStatus != null) {
                val isLocked = lockStatus["is_locked"] as? Boolean ?: false
                val reason = lockStatus["reason"] as? String ?: ""
                
                Log.d(TAG, "Lock status from backend database: is_locked=$isLocked, reason=$reason")
                
                // Pass to LockManager to handle auto-lock/unlock
                lockManager.handleHeartbeatResponse(response)
                
                if (isLocked) {
                    Log.d(TAG, "→ Backend says: Device should be LOCKED")
                    Log.d(TAG, "→ Device will AUTO-LOCK")
                } else {
                    Log.d(TAG, "→ Backend says: Device should be UNLOCKED")
                    Log.d(TAG, "→ Device will AUTO-UNLOCK")
                }
            } else {
                Log.d(TAG, "No lock status in heartbeat response")
            }
            
            // Feature 4.9: Handle backend commands
            handleBackendCommands(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling heartbeat response", e)
        }
    }
    
    /**
     * Handle backend commands from heartbeat response
     * Feature 4.9: Offline Command Queue
     * 
     * Backend can send commands in heartbeat response:
     * {
     *   "commands": [
     *     {
     *       "id": "cmd-123",
     *       "type": "LOCK_DEVICE",
     *       "device_id": "device-456",
     *       "parameters": {
     *         "lockType": "HARD",
     *         "reason": "Payment overdue",
     *         "message": "Your device has been locked"
     *       },
     *       "signature": "base64-signature",
     *       "expires_at": 1234567890
     *     }
     *   ]
     * }
     */
    private fun handleBackendCommands(response: Map<String, Any?>) {
        try {
            // Parse commands from response
            @Suppress("UNCHECKED_CAST")
            val commands = response["commands"] as? List<Map<String, Any?>>
            
            if (commands.isNullOrEmpty()) {
                Log.d(TAG, "No backend commands in heartbeat response")
                return
            }
            
            Log.d(TAG, "Received ${commands.size} commands from backend")
            
            // Enqueue each command
            commands.forEach { cmdData ->
                try {
                    val commandId = cmdData["id"] as? String ?: return@forEach
                    val type = cmdData["type"] as? String ?: return@forEach
                    val deviceId = cmdData["device_id"] as? String ?: ""
                    
                    @Suppress("UNCHECKED_CAST")
                    val parameters = cmdData["parameters"] as? Map<String, String> ?: emptyMap()
                    val signature = cmdData["signature"] as? String ?: ""
                    val expiresAt = (cmdData["expires_at"] as? Number)?.toLong() ?: 0L
                    
                    val command = OfflineCommand(
                        commandId = commandId,
                        type = type,
                        deviceId = deviceId,
                        parameters = parameters,
                        signature = signature,
                        enqueuedAt = System.currentTimeMillis(),
                        expiresAt = expiresAt
                    )
                    
                    val success = commandQueue.enqueueCommand(command)
                    if (success) {
                        Log.d(TAG, "✓ Command enqueued: $commandId - $type")
                    } else {
                        Log.e(TAG, "✗ Failed to enqueue command: $commandId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing command", e)
                }
            }
            
            // Trigger CommandQueueService to process commands
            startCommandQueueService()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling backend commands", e)
        }
    }
    
    /**
     * Start CommandQueueService for offline command processing
     * Feature 4.9: Offline Command Queue
     */
    private fun startCommandQueueService() {
        try {
            val intent = Intent(this, CommandQueueService::class.java)
            startService(intent)
            Log.d(TAG, "✓ CommandQueueService started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CommandQueueService", e)
        }
    }
    
    /**
     * Get device ID from shared preferences
     */
    private fun getDeviceIdFromPrefs(): String {
        return try {
            val prefs = getSharedPreferences("device_info", MODE_PRIVATE)
            prefs.getString("device_id", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Get device info
     */
    private fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "model" to android.os.Build.MODEL,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "android_version" to android.os.Build.VERSION.RELEASE,
            "sdk_int" to android.os.Build.VERSION.SDK_INT
        )
    }
}
