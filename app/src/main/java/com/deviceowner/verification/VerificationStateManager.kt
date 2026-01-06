package com.deviceowner.verification

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.deviceowner.verification.VerificationIncident
import com.deviceowner.verification.RecoveryAttempt

/**
 * VerificationStateManager manages verification state and history
 */
class VerificationStateManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("verification_state", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_FAILURE_COUNT = "failure_count"
        private const val PREF_LAST_STATUS = "last_status"
        private const val PREF_HISTORY = "history"
        private const val PREF_INCIDENTS = "incidents"
        private const val PREF_RECOVERY_ATTEMPTS = "recovery_attempts"
    }
    
    /**
     * Increment failure count
     */
    fun incrementFailureCount(): Int {
        val current = prefs.getInt(PREF_FAILURE_COUNT, 0)
        val next = current + 1
        prefs.edit().putInt(PREF_FAILURE_COUNT, next).apply()
        return next
    }
    
    /**
     * Reset failure count
     */
    fun resetFailureCount() {
        prefs.edit().putInt(PREF_FAILURE_COUNT, 0).apply()
    }
    
    /**
     * Get current failure count
     */
    fun getFailureCount(): Int {
        return prefs.getInt(PREF_FAILURE_COUNT, 0)
    }
    
    /**
     * Store verification status
     */
    fun storeVerificationStatus(status: VerificationStatus) {
        val json = JSONObject().apply {
            put("result", status.result.name)
            put("isDeviceOwner", status.isDeviceOwner)
            put("canLock", status.canLock)
            put("canSetPassword", status.canSetPassword)
            put("failureCount", status.failureCount)
            put("lastVerificationTime", status.lastVerificationTime)
            put("errorMessage", status.errorMessage ?: "")
        }
        prefs.edit().putString(PREF_LAST_STATUS, json.toString()).apply()
    }
    
    /**
     * Get last verification status
     */
    fun getLastVerificationStatus(): VerificationStatus? {
        val json = prefs.getString(PREF_LAST_STATUS, null) ?: return null
        return try {
            val obj = JSONObject(json)
            VerificationStatus(
                result = VerificationResult.valueOf(obj.optString("result", "ERROR")),
                isDeviceOwner = obj.optBoolean("isDeviceOwner", false),
                canLock = obj.optBoolean("canLock", false),
                canSetPassword = obj.optBoolean("canSetPassword", false),
                failureCount = obj.optInt("failureCount", 0),
                lastVerificationTime = obj.optLong("lastVerificationTime", System.currentTimeMillis()),
                errorMessage = obj.optString("errorMessage", null)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Record incident
     */
    fun recordIncident(type: String, severity: String, details: Map<String, String>) {
        val incident = JSONObject().apply {
            put("type", type)
            put("severity", severity)
            put("timestamp", System.currentTimeMillis())
            val detailsObj = JSONObject()
            details.forEach { (k, v) -> detailsObj.put(k, v) }
            put("details", detailsObj)
        }
        
        val incidents = JSONArray(prefs.getString(PREF_INCIDENTS, "[]") ?: "[]")
        incidents.put(incident)
        prefs.edit().putString(PREF_INCIDENTS, incidents.toString()).apply()
    }
    
    /**
     * Get incidents
     */
    fun getIncidents(): List<VerificationIncident> {
        val json = prefs.getString(PREF_INCIDENTS, "[]") ?: "[]"
        val incidents = mutableListOf<VerificationIncident>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val detailsObj = obj.optJSONObject("details") ?: JSONObject()
                val details = mutableMapOf<String, String>()
                detailsObj.keys().forEach { key ->
                    details[key] = detailsObj.optString(key, "")
                }
                incidents.add(VerificationIncident(
                    type = obj.optString("type", ""),
                    severity = obj.optString("severity", ""),
                    details = details,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return incidents
    }
    
    /**
     * Record recovery attempt
     */
    fun recordRecoveryAttempt(timestamp: Long, success: Boolean, reason: String) {
        val attempt = JSONObject().apply {
            put("timestamp", timestamp)
            put("success", success)
            put("reason", reason)
        }
        
        val attempts = JSONArray(prefs.getString(PREF_RECOVERY_ATTEMPTS, "[]") ?: "[]")
        attempts.put(attempt)
        prefs.edit().putString(PREF_RECOVERY_ATTEMPTS, attempts.toString()).apply()
    }
    
    /**
     * Get recovery attempts
     */
    fun getRecoveryAttempts(): List<RecoveryAttempt> {
        val json = prefs.getString(PREF_RECOVERY_ATTEMPTS, "[]") ?: "[]"
        val attempts = mutableListOf<RecoveryAttempt>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                attempts.add(RecoveryAttempt(
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    success = obj.optBoolean("success", false),
                    reason = obj.optString("reason", "")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return attempts
    }
    
    /**
     * Get verification history
     */
    fun getVerificationHistory(): List<VerificationStatus> {
        val history = mutableListOf<VerificationStatus>()
        val lastStatus = getLastVerificationStatus()
        if (lastStatus != null) {
            history.add(lastStatus)
        }
        return history
    }
    
    /**
     * Clear history
     */
    fun clearHistory() {
        prefs.edit().apply {
            remove(PREF_FAILURE_COUNT)
            remove(PREF_LAST_STATUS)
            remove(PREF_HISTORY)
            remove(PREF_INCIDENTS)
            remove(PREF_RECOVERY_ATTEMPTS)
        }.apply()
    }
}
