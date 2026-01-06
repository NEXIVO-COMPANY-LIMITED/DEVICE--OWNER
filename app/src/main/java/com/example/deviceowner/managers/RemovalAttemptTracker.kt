package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks unauthorized app removal attempts and queues backend alerts
 * Provides comprehensive removal attempt monitoring with escalation
 */
class RemovalAttemptTracker(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("removal_tracking", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val auditLog = IdentifierAuditLog(context)
    private val removalAttemptCount = AtomicInteger(0)
    
    // Protected tracking file
    private val trackingFile: File by lazy {
        val file = File(context.cacheDir, "removal_tracking.dat")
        file.setReadable(true, true)
        file.setWritable(true, true)
        file
    }
    
    companion object {
        private const val TAG = "RemovalAttemptTracker"
        private const val KEY_REMOVAL_ATTEMPTS = "removal_attempts"
        private const val KEY_REMOVAL_HISTORY = "removal_history"
        private const val KEY_LAST_REMOVAL_TIME = "last_removal_time"
        private const val KEY_DEVICE_LOCKED = "device_locked_removal"
        
        // Escalation thresholds
        private const val WARNING_THRESHOLD = 2
        private const val CRITICAL_THRESHOLD = 3
        private const val LOCK_THRESHOLD = 3
        private const val MAX_HISTORY_SIZE = 100
    }
    
    init {
        // Load attempt count from persistent storage
        removalAttemptCount.set(getStoredAttemptCount())
    }
    
    /**
     * Record a removal attempt and queue backend alert
     */
    suspend fun recordRemovalAttempt(): Int = withContext(Dispatchers.IO) {
        try {
            val attemptNumber = removalAttemptCount.incrementAndGet()
            val timestamp = System.currentTimeMillis()
            
            Log.e(TAG, "CRITICAL: Unauthorized app removal detected! Attempt #$attemptNumber")
            
            // Store attempt count
            storeAttemptCount(attemptNumber)
            
            // Add to history
            addToHistory(RemovalAttempt(
                attemptNumber = attemptNumber,
                timestamp = timestamp,
                details = "Unauthorized removal attempt detected"
            ))
            
            // Log incident locally
            auditLog.logIncident(
                type = "UNAUTHORIZED_REMOVAL",
                severity = "CRITICAL",
                details = "App removal detected - attempt #$attemptNumber"
            )
            
            // Queue alert for backend
            queueRemovalAlert(attemptNumber, timestamp)
            
            // Check escalation thresholds
            handleEscalation(attemptNumber)
            
            Log.w(TAG, "Removal attempt #$attemptNumber recorded and queued for backend")
            
            attemptNumber
        } catch (e: Exception) {
            Log.e(TAG, "Error recording removal attempt", e)
            auditLog.logIncident(
                type = "REMOVAL_TRACKING_ERROR",
                severity = "HIGH",
                details = "Error recording removal attempt: ${e.message}"
            )
            -1
        }
    }
    
    /**
     * Queue removal attempt alert for backend notification
     */
    private suspend fun queueRemovalAlert(attemptNumber: Int, timestamp: Long) {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId()
                if (deviceId.isEmpty()) {
                    Log.w(TAG, "Cannot queue alert: device ID not available")
                    return@withContext
                }
                
                val severity = when {
                    attemptNumber < WARNING_THRESHOLD -> "WARNING"
                    attemptNumber < CRITICAL_THRESHOLD -> "HIGH"
                    else -> "CRITICAL"
                }
                
                val alert = RemovalAlert(
                    deviceId = deviceId,
                    attemptNumber = attemptNumber,
                    timestamp = timestamp,
                    details = "Unauthorized removal attempt detected",
                    severity = severity,
                    deviceLocked = attemptNumber >= LOCK_THRESHOLD,
                    escalationLevel = calculateEscalationLevel(attemptNumber)
                )
                
                // Queue for backend sync via heartbeat
                val alertQueue = MismatchAlertQueue(context)
                alertQueue.queueRemovalAlert(alert)
                
                Log.d(TAG, "Removal attempt #$attemptNumber queued for backend (severity: $severity)")
                
                auditLog.logAction(
                    "REMOVAL_ALERT_QUEUED",
                    "Removal attempt #$attemptNumber queued for backend (severity: $severity)"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing removal alert", e)
            }
        }
    }
    
    /**
     * Handle escalation based on attempt count
     */
    private suspend fun handleEscalation(attemptNumber: Int) {
        withContext(Dispatchers.IO) {
            try {
                when {
                    attemptNumber == WARNING_THRESHOLD -> {
                        Log.w(TAG, "WARNING THRESHOLD REACHED: $attemptNumber removal attempts")
                        auditLog.logIncident(
                            type = "REMOVAL_WARNING_THRESHOLD",
                            severity = "HIGH",
                            details = "Warning threshold reached at $attemptNumber attempts"
                        )
                    }
                    
                    attemptNumber == CRITICAL_THRESHOLD -> {
                        Log.e(TAG, "CRITICAL THRESHOLD REACHED: $attemptNumber removal attempts")
                        auditLog.logIncident(
                            type = "REMOVAL_CRITICAL_THRESHOLD",
                            severity = "CRITICAL",
                            details = "Critical threshold reached at $attemptNumber attempts"
                        )
                    }
                    
                    attemptNumber >= LOCK_THRESHOLD -> {
                        Log.e(TAG, "LOCK THRESHOLD REACHED: Locking device after $attemptNumber removal attempts")
                        lockDeviceForRemovalAttempts(attemptNumber)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling escalation", e)
            }
        }
    }
    
    /**
     * Lock device due to excessive removal attempts
     */
    private suspend fun lockDeviceForRemovalAttempts(attemptNumber: Int) {
        withContext(Dispatchers.IO) {
            try {
                val deviceOwnerManager = DeviceOwnerManager(context)
                deviceOwnerManager.lockDevice()
                
                // Mark device as locked due to removal attempts
                prefs.edit().putBoolean(KEY_DEVICE_LOCKED, true).apply()
                
                auditLog.logIncident(
                    type = "DEVICE_LOCKED_REMOVAL",
                    severity = "CRITICAL",
                    details = "Device locked due to $attemptNumber removal attempts"
                )
                
                Log.e(TAG, "✓ Device locked due to excessive removal attempts")
            } catch (e: Exception) {
                Log.e(TAG, "Error locking device for removal attempts", e)
            }
        }
    }
    
    /**
     * Calculate escalation level (0-3)
     */
    private fun calculateEscalationLevel(attemptNumber: Int): Int {
        return when {
            attemptNumber < WARNING_THRESHOLD -> 0
            attemptNumber < CRITICAL_THRESHOLD -> 1
            attemptNumber < LOCK_THRESHOLD -> 2
            else -> 3
        }
    }
    
    /**
     * Get current removal attempt count
     */
    fun getAttemptCount(): Int {
        return removalAttemptCount.get()
    }
    
    /**
     * Get removal attempt history
     */
    fun getRemovalHistory(): List<RemovalAttempt> {
        return try {
            // Try file first
            if (trackingFile.exists()) {
                val json = trackingFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<RemovalAttempt>>() {}.type
                return gson.fromJson(json, type) ?: emptyList()
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_REMOVAL_HISTORY, "[]") ?: "[]"
            val type = object : com.google.gson.reflect.TypeToken<List<RemovalAttempt>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving removal history", e)
            emptyList()
        }
    }
    
    /**
     * Check if device is locked due to removal attempts
     */
    fun isDeviceLockedForRemoval(): Boolean {
        return prefs.getBoolean(KEY_DEVICE_LOCKED, false)
    }
    
    /**
     * Reset removal attempt counter (call after backend confirmation)
     */
    fun resetAttemptCounter() {
        try {
            removalAttemptCount.set(0)
            storeAttemptCount(0)
            prefs.edit().putBoolean(KEY_DEVICE_LOCKED, false).apply()
            
            auditLog.logAction(
                "REMOVAL_ATTEMPTS_RESET",
                "Removal attempt counter reset"
            )
            
            Log.d(TAG, "Removal attempt counter reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting attempt counter", e)
        }
    }
    
    /**
     * Get removal tracking state for diagnostics
     */
    fun getTrackingState(): RemovalTrackingState {
        return RemovalTrackingState(
            attemptCount = removalAttemptCount.get(),
            lastAttemptTime = prefs.getLong(KEY_LAST_REMOVAL_TIME, 0),
            isDeviceLocked = isDeviceLockedForRemoval(),
            escalationLevel = calculateEscalationLevel(removalAttemptCount.get()),
            historySize = getRemovalHistory().size
        )
    }
    
    /**
     * Store attempt count to persistent storage
     */
    private fun storeAttemptCount(count: Int) {
        try {
            prefs.edit().apply {
                putInt(KEY_REMOVAL_ATTEMPTS, count)
                putLong(KEY_LAST_REMOVAL_TIME, System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error storing attempt count", e)
        }
    }
    
    /**
     * Get stored attempt count from persistent storage
     */
    private fun getStoredAttemptCount(): Int {
        return try {
            prefs.getInt(KEY_REMOVAL_ATTEMPTS, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving attempt count", e)
            0
        }
    }
    
    /**
     * Add removal attempt to history
     */
    private fun addToHistory(attempt: RemovalAttempt) {
        try {
            val history = getRemovalHistory().toMutableList()
            history.add(attempt)
            
            // Keep only recent attempts
            if (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(0)
            }
            
            val json = gson.toJson(history)
            prefs.edit().putString(KEY_REMOVAL_HISTORY, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to history", e)
        }
    }
    
    /**
     * Get device ID from SharedPreferences
     */
    private fun getDeviceId(): String {
        return try {
            val prefs = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            prefs.getString("device_id", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Data class for removal attempt
 */
data class RemovalAttempt(
    val attemptNumber: Int,
    val timestamp: Long,
    val details: String
)

/**
 * Data class for removal alert to send to backend
 */
data class RemovalAlert(
    val deviceId: String,
    val attemptNumber: Int,
    val timestamp: Long,
    val details: String,
    val severity: String, // WARNING, HIGH, CRITICAL
    val deviceLocked: Boolean,
    val escalationLevel: Int // 0-3
)

/**
 * Data class for removal tracking state diagnostics
 */
data class RemovalTrackingState(
    val attemptCount: Int,
    val lastAttemptTime: Long,
    val isDeviceLocked: Boolean,
    val escalationLevel: Int,
    val historySize: Int
)
