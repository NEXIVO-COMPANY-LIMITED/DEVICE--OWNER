package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * Queue system for device owner loss alerts, removal alerts, and other critical alerts
 * Persists alerts locally and syncs with backend via heartbeat
 */
class MismatchAlertQueue(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("alert_queue", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Protected queue files
    private val deviceOwnerQueueFile: File by lazy {
        val file = File(context.cacheDir, "alert_queue_owner.dat")
        file.setReadable(true, true)
        file.setWritable(true, true)
        file
    }
    
    private val removalQueueFile: File by lazy {
        val file = File(context.cacheDir, "alert_queue_removal.dat")
        file.setReadable(true, true)
        file.setWritable(true, true)
        file
    }
    
    companion object {
        private const val TAG = "MismatchAlertQueue"
        private const val KEY_DEVICE_OWNER_QUEUE = "device_owner_alert_queue"
        private const val KEY_REMOVAL_QUEUE = "removal_alert_queue"
        private const val MAX_QUEUE_SIZE = 50
    }
    
    /**
     * Queue a device owner loss alert
     */
    fun queueAlert(alert: com.example.deviceowner.data.api.MismatchAlert) {
        try {
            val queue = getPendingAlerts().toMutableList()
            
            // Convert MismatchAlert to DeviceOwnerLossAlert for storage
            val deviceOwnerAlert = DeviceOwnerLossAlert(
                deviceId = alert.device_id,
                timestamp = alert.timestamp,
                details = alert.alert_type,
                severity = alert.severity,
                recoveryAttempted = false,
                recoverySuccessful = false,
                recoveryAttempts = 0
            )
            
            // Add new alert
            queue.add(deviceOwnerAlert)
            
            // Keep only recent alerts
            if (queue.size > MAX_QUEUE_SIZE) {
                queue.removeAt(0)
            }
            
            saveDeviceOwnerAlertQueue(queue)
            Log.d(TAG, "Device owner alert queued: ${alert.device_id} - ${alert.alert_type}")
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing device owner alert", e)
        }
    }
    
    /**
     * Queue a removal attempt alert
     */
    fun queueRemovalAlert(alert: RemovalAlert) {
        try {
            val queue = getRemovalAlertQueue().toMutableList()
            
            // Add new alert
            queue.add(alert)
            
            // Keep only recent alerts
            if (queue.size > MAX_QUEUE_SIZE) {
                queue.removeAt(0)
            }
            
            saveRemovalAlertQueue(queue)
            Log.d(TAG, "Removal alert queued: ${alert.deviceId} - Attempt #${alert.attemptNumber}")
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing removal alert", e)
        }
    }
    
    /**
     * Get all pending device owner alerts
     */
    fun getPendingAlerts(): List<DeviceOwnerLossAlert> {
        return try {
            getDeviceOwnerAlertQueue()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving device owner alerts", e)
            emptyList()
        }
    }
    
    /**
     * Check if there are pending alerts
     */
    fun hasPendingAlerts(): Boolean {
        return getPendingAlerts().isNotEmpty() || getPendingRemovalAlerts().isNotEmpty()
    }
    
    /**
     * Get the size of the alert queue
     */
    fun getQueueSize(): Int {
        return getPendingAlerts().size + getPendingRemovalAlerts().size
    }
    
    /**
     * Get all pending removal alerts
     */
    fun getPendingRemovalAlerts(): List<RemovalAlert> {
        return try {
            getRemovalAlertQueue()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving removal alerts", e)
            emptyList()
        }
    }
    
    /**
     * Remove device owner alert after successful backend sync
     */
    fun removeAlert(alert: DeviceOwnerLossAlert) {
        try {
            val queue = getDeviceOwnerAlertQueue().toMutableList()
            queue.removeAll { it.deviceId == alert.deviceId && it.timestamp == alert.timestamp }
            saveDeviceOwnerAlertQueue(queue)
            Log.d(TAG, "Device owner alert removed after sync")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing device owner alert", e)
        }
    }
    
    /**
     * Remove removal alert after successful backend sync
     */
    fun removeRemovalAlert(alert: RemovalAlert) {
        try {
            val queue = getRemovalAlertQueue().toMutableList()
            queue.removeAll { it.deviceId == alert.deviceId && it.timestamp == alert.timestamp }
            saveRemovalAlertQueue(queue)
            Log.d(TAG, "Removal alert removed after sync")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing removal alert", e)
        }
    }
    
    /**
     * Process all queued alerts (retry failed ones)
     */
    suspend fun processQueuedAlerts() {
        try {
            val deviceOwnerAlerts = getPendingAlerts()
            val removalAlerts = getPendingRemovalAlerts()
            
            Log.d(TAG, "Processing ${deviceOwnerAlerts.size} device owner alerts and ${removalAlerts.size} removal alerts")
            
            // Process device owner alerts
            for (alert in deviceOwnerAlerts) {
                try {
                    // Convert to MismatchAlert and send to backend
                    val mismatchAlert = com.example.deviceowner.data.api.MismatchAlert(
                        device_id = alert.deviceId,
                        alert_type = alert.details,
                        mismatches = emptyList(), // Empty list for device owner loss alerts
                        severity = alert.severity,
                        timestamp = alert.timestamp
                    )
                    
                    // Try to send to backend (implementation would go here)
                    // For now, just log
                    Log.d(TAG, "Processing device owner alert: ${alert.deviceId}")
                    
                    // Remove from queue after successful processing
                    removeAlert(alert)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing device owner alert", e)
                }
            }
            
            // Process removal alerts
            for (alert in removalAlerts) {
                try {
                    Log.d(TAG, "Processing removal alert: ${alert.deviceId}")
                    
                    // Try to send to backend (implementation would go here)
                    // For now, just log
                    
                    // Remove from queue after successful processing
                    removeRemovalAlert(alert)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing removal alert", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queued alerts", e)
        }
    }

    /**
     * Clear all alerts
     */
    fun clearAlerts() {
        try {
            saveDeviceOwnerAlertQueue(emptyList())
            saveRemovalAlertQueue(emptyList())
            Log.d(TAG, "All alerts cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing alerts", e)
        }
    }
    
    /**
     * Get device owner alert queue from storage
     */
    private fun getDeviceOwnerAlertQueue(): List<DeviceOwnerLossAlert> {
        return try {
            // Try file first
            if (deviceOwnerQueueFile.exists()) {
                val json = deviceOwnerQueueFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<DeviceOwnerLossAlert>>() {}.type
                return gson.fromJson(json, type) ?: emptyList()
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_DEVICE_OWNER_QUEUE, "[]") ?: "[]"
            val type = object : com.google.gson.reflect.TypeToken<List<DeviceOwnerLossAlert>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading device owner alert queue", e)
            emptyList()
        }
    }
    
    /**
     * Get removal alert queue from storage
     */
    private fun getRemovalAlertQueue(): List<RemovalAlert> {
        return try {
            // Try file first
            if (removalQueueFile.exists()) {
                val json = removalQueueFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<RemovalAlert>>() {}.type
                return gson.fromJson(json, type) ?: emptyList()
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_REMOVAL_QUEUE, "[]") ?: "[]"
            val type = object : com.google.gson.reflect.TypeToken<List<RemovalAlert>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading removal alert queue", e)
            emptyList()
        }
    }
    
    /**
     * Save device owner alert queue to storage
     */
    private fun saveDeviceOwnerAlertQueue(queue: List<DeviceOwnerLossAlert>) {
        try {
            val json = gson.toJson(queue)
            
            // Save to file
            deviceOwnerQueueFile.writeText(json)
            
            // Also save to SharedPreferences as backup
            prefs.edit().putString(KEY_DEVICE_OWNER_QUEUE, json).apply()
            
            Log.d(TAG, "Device owner alert queue saved: ${queue.size} alerts")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving device owner alert queue", e)
        }
    }
    
    /**
     * Save removal alert queue to storage
     */
    private fun saveRemovalAlertQueue(queue: List<RemovalAlert>) {
        try {
            val json = gson.toJson(queue)
            
            // Save to file
            removalQueueFile.writeText(json)
            
            // Also save to SharedPreferences as backup
            prefs.edit().putString(KEY_REMOVAL_QUEUE, json).apply()
            
            Log.d(TAG, "Removal alert queue saved: ${queue.size} alerts")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving removal alert queue", e)
        }
    }
}
