package com.deviceowner.services

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.api.ManageRequest
import com.example.deviceowner.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * OfflineLockQueue handles offline lock/unlock commands
 * Queues commands when device is offline
 * Applies commands when device reconnects
 */
class OfflineLockQueue(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("offline_queue", Context.MODE_PRIVATE)
    private val apiService = ApiClient.apiService
    
    companion object {
        private const val TAG = "OfflineLockQueue"
        private const val QUEUE_KEY = "manage_commands"
    }
    
    /**
     * Queue manage command for offline
     */
    suspend fun queueManageCommand(action: String, reason: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get existing queue
                val queue = getQueue()
                
                // Add new command
                val command = JSONObject().apply {
                    put("action", action)
                    put("reason", reason)
                    put("timestamp", System.currentTimeMillis())
                }
                queue.put(command)
                
                // Save queue
                prefs.edit().putString(QUEUE_KEY, queue.toString()).apply()
                
                Log.d(TAG, "✓ Command queued: $action")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing command", e)
                false
            }
        }
    }
    
    /**
     * Apply all queued commands
     */
    suspend fun applyQueuedCommands(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val queue = getQueue()
                val deviceId = getDeviceId()
                
                if (queue.length() == 0) {
                    Log.d(TAG, "No queued commands")
                    return@withContext true
                }
                
                Log.d(TAG, "Applying ${queue.length()} queued commands")
                
                // Apply each command
                for (i in 0 until queue.length()) {
                    try {
                        val command = queue.getJSONObject(i)
                        val action = command.getString("action")
                        val reason = command.getString("reason")
                        
                        // Send to backend
                        val request = ManageRequest(action, reason)
                        val response = apiService.manageDevice(deviceId, request)
                        
                        if (response.isSuccessful) {
                            Log.d(TAG, "✓ Queued command applied: $action")
                        } else {
                            Log.e(TAG, "✗ Failed to apply queued command: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying queued command", e)
                    }
                }
                
                // Clear queue
                clearQueue()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error applying queued commands", e)
                false
            }
        }
    }
    
    /**
     * Get queue
     */
    private fun getQueue(): JSONArray {
        return try {
            val queueStr = prefs.getString(QUEUE_KEY, "[]")
            JSONArray(queueStr)
        } catch (e: Exception) {
            JSONArray()
        }
    }
    
    /**
     * Clear queue
     */
    private fun clearQueue() {
        prefs.edit().remove(QUEUE_KEY).apply()
        Log.d(TAG, "✓ Queue cleared")
    }
    
    /**
     * Get device ID
     */
    private fun getDeviceId(): String {
        return try {
            val devicePrefs = context.getSharedPreferences("device_info", Context.MODE_PRIVATE)
            devicePrefs.getString("device_id", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
