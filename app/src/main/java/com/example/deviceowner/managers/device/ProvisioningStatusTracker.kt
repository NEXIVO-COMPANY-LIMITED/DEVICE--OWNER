package com.example.deviceowner.managers.device

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Provisioning Status Tracker
 * Tracks and logs the complete provisioning process for debugging and monitoring
 */
class ProvisioningStatusTracker(private val context: Context) {

    companion object {
        private const val TAG = "ProvisioningStatusTracker"
        private const val PREFS_NAME = "provisioning_status"
        private const val KEY_PROVISIONING_STARTED = "provisioning_started"
        private const val KEY_PROVISIONING_COMPLETED = "provisioning_completed"
        private const val KEY_LAST_STATUS = "last_status"
        private const val KEY_PHASE = "current_phase"
    }

    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Mark provisioning as started
     */
    fun markProvisioningStarted() {
        prefs.edit()
            .putLong(KEY_PROVISIONING_STARTED, System.currentTimeMillis())
            .putBoolean(KEY_PROVISIONING_COMPLETED, false)
            .apply()
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "PROVISIONING STARTED")
        Log.d(TAG, "Timestamp: ${System.currentTimeMillis()}")
        Log.d(TAG, "========================================")
    }

    /**
     * Mark provisioning as completed
     */
    fun markProvisioningCompleted(success: Boolean) {
        val startTime = prefs.getLong(KEY_PROVISIONING_STARTED, 0)
        val duration = if (startTime > 0) {
            System.currentTimeMillis() - startTime
        } else {
            0
        }
        
        prefs.edit()
            .putBoolean(KEY_PROVISIONING_COMPLETED, success)
            .putLong("provisioning_duration_ms", duration)
            .apply()
        
        Log.d(TAG, "========================================")
        Log.d(TAG, if (success) "PROVISIONING COMPLETED SUCCESSFULLY" else "PROVISIONING COMPLETED WITH ERRORS")
        Log.d(TAG, "Duration: ${duration}ms (${duration / 1000.0}s)")
        Log.d(TAG, "========================================")
    }

    /**
     * Update current phase
     */
    fun updatePhase(phase: String) {
        prefs.edit()
            .putString(KEY_PHASE, phase)
            .putLong("phase_${phase}_timestamp", System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "--- Phase: $phase ---")
    }

    /**
     * Log status update
     */
    fun logStatus(status: String, details: String? = null) {
        prefs.edit()
            .putString(KEY_LAST_STATUS, status)
            .putLong("last_status_timestamp", System.currentTimeMillis())
            .apply()
        
        if (details != null) {
            Log.d(TAG, "$status: $details")
        } else {
            Log.d(TAG, status)
        }
    }

    /**
     * Get provisioning status
     */
    fun getProvisioningStatus(): ProvisioningStatus {
        return ProvisioningStatus(
            started = prefs.getLong(KEY_PROVISIONING_STARTED, 0) > 0,
            completed = prefs.getBoolean(KEY_PROVISIONING_COMPLETED, false),
            currentPhase = prefs.getString(KEY_PHASE, "Unknown") ?: "Unknown",
            lastStatus = prefs.getString(KEY_LAST_STATUS, "None") ?: "None",
            duration = prefs.getLong("provisioning_duration_ms", 0)
        )
    }

    /**
     * Data class for provisioning status
     */
    data class ProvisioningStatus(
        val started: Boolean,
        val completed: Boolean,
        val currentPhase: String,
        val lastStatus: String,
        val duration: Long
    )
}
