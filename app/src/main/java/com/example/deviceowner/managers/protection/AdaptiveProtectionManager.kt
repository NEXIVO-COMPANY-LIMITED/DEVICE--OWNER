package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages adaptive protection levels based on threat assessment.
 * Dynamically adjusts protection mechanisms according to current threat level.
 */
class AdaptiveProtectionManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "adaptive_protection",
        Context.MODE_PRIVATE
    )

    private val auditLog = IdentifierAuditLog(context)
    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val uninstallPreventionManager = UninstallPreventionManager(context)

    private val currentProtectionLevel: AtomicReference<ProtectionLevel> =
        AtomicReference(ProtectionLevel.STANDARD)

    companion object {
        private const val TAG = "AdaptiveProtectionManager"

        // Heartbeat intervals (milliseconds)
        private const val CRITICAL_HEARTBEAT = 10000L // 10 seconds
        private const val ENHANCED_HEARTBEAT = 30000L // 30 seconds
        private const val STANDARD_HEARTBEAT = 60000L // 1 minute

        // Threat score thresholds
        private const val CRITICAL_THRESHOLD = 80
        private const val ENHANCED_THRESHOLD = 50

        // SharedPreferences keys
        private const val KEY_PROTECTION_LEVEL = "protection_level"
        private const val KEY_THREAT_SCORE = "threat_score"
        private const val KEY_LAST_LEVEL_CHANGE = "last_level_change"
    }

    /**
     * Enum representing protection levels
     */
    enum class ProtectionLevel {
        STANDARD,   // Normal protection
        ENHANCED,   // Extra monitoring and checks
        CRITICAL    // Maximum protection with device lock
    }

    /**
     * Data class for protection state
     */
    data class ProtectionState(
        val currentLevel: ProtectionLevel,
        val threatScore: Int,
        val lastLevelChange: Long,
        val heartbeatInterval: Long
    )

    /**
     * Get current heartbeat interval based on protection level
     */
    private fun getHeartbeatInterval(): Long = when (currentProtectionLevel.get()) {
        ProtectionLevel.CRITICAL -> CRITICAL_HEARTBEAT
        ProtectionLevel.ENHANCED -> ENHANCED_HEARTBEAT
        ProtectionLevel.STANDARD -> STANDARD_HEARTBEAT
    }

    /**
     * Set heartbeat interval for current protection level
     */
    private fun setHeartbeatInterval(interval: Long) {
        Log.d(TAG, "Setting heartbeat interval to: ${interval}ms")
        try {
            prefs.edit().apply {
                putLong("heartbeat_interval", interval)
            }.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting heartbeat interval", e)
        }
    }

    /**
     * Enable all security checks
     */
    private suspend fun enableAllSecurityChecks() {
        Log.d(TAG, "Enabling all security checks")
        try {
            uninstallPreventionManager.enableUninstallPrevention()
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling security checks", e)
        }
    }

    /**
     * Start critical monitoring
     */
    private suspend fun startCriticalMonitoring() {
        Log.d(TAG, "Starting critical monitoring")
        try {
            setHeartbeatInterval(CRITICAL_HEARTBEAT)
            enableAllSecurityChecks()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting critical monitoring", e)
        }
    }

    /**
     * Start enhanced monitoring
     */
    private suspend fun startEnhancedMonitoring() {
        Log.d(TAG, "Starting enhanced monitoring")
        try {
            setHeartbeatInterval(ENHANCED_HEARTBEAT)
            enableAllSecurityChecks()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting enhanced monitoring", e)
        }
    }

    /**
     * Start standard monitoring
     */
    private fun startStandardMonitoring() {
        Log.d(TAG, "Starting standard monitoring")
        try {
            setHeartbeatInterval(STANDARD_HEARTBEAT)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting standard monitoring", e)
        }
    }

    /**
     * Update threat score and adjust protection level accordingly
     */
    suspend fun updateThreatScore(delta: Int): ProtectionLevel = withContext(Dispatchers.IO) {
        val currentScore = prefs.getInt("threat_score", 0)
        val newScore = (currentScore + delta).coerceIn(0, 100)

        try {
            prefs.edit().apply {
                putInt("threat_score", newScore)
            }.apply()

            val newLevel = when {
                newScore >= CRITICAL_THRESHOLD -> ProtectionLevel.CRITICAL
                newScore >= ENHANCED_THRESHOLD -> ProtectionLevel.ENHANCED
                else -> ProtectionLevel.STANDARD
            }

            if (newLevel != currentProtectionLevel.get()) {
                setProtectionLevel(newLevel)
            }

            Log.d(TAG, "Threat score updated: $currentScore -> $newScore (level: $newLevel)")
            newLevel
        } catch (e: Exception) {
            Log.e(TAG, "Error updating threat score", e)
            currentProtectionLevel.get()
        }
    }

    /**
     * Set protection level
     */
    suspend fun setProtectionLevel(level: ProtectionLevel): Boolean = withContext(Dispatchers.IO) {
        try {
            when (level) {
                ProtectionLevel.CRITICAL -> {
                    Log.d(TAG, "✓ Activating critical protection")
                    applyCriticalProtection()
                }
                ProtectionLevel.ENHANCED -> {
                    Log.d(TAG, "✓ Activating enhanced protection")
                    applyEnhancedProtection()
                }
                ProtectionLevel.STANDARD -> {
                    Log.d(TAG, "✓ Activating standard protection")
                    applyStandardProtection()
                }
            }

            currentProtectionLevel.set(level)
            prefs.edit().apply {
                putString(KEY_PROTECTION_LEVEL, level.name)
                putLong(KEY_LAST_LEVEL_CHANGE, System.currentTimeMillis())
            }.apply()

            auditLog.logAction(
                "PROTECTION_LEVEL_CHANGED",
                "Level changed to: ${level.name}"
            )

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting protection level", e)
            false
        }
    }

    /**
     * Apply critical protection
     */
    private suspend fun applyCriticalProtection() {
        try {
            startCriticalMonitoring()
            enableAllSecurityChecks()
            deviceOwnerManager.lockDevice()
            Log.d(TAG, "✓ Critical protection applied")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying critical protection", e)
        }
    }

    /**
     * Apply enhanced protection
     */
    private suspend fun applyEnhancedProtection() {
        try {
            startEnhancedMonitoring()
            enableAllSecurityChecks()
            Log.d(TAG, "✓ Enhanced protection applied")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying enhanced protection", e)
        }
    }

    /**
     * Apply standard protection
     */
    private suspend fun applyStandardProtection() {
        try {
            startStandardMonitoring()
            Log.d(TAG, "✓ Standard protection applied")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying standard protection", e)
        }
    }

    /**
     * Get current protection state
     */
    fun getProtectionState(): ProtectionState {
        val level = currentProtectionLevel.get()
        val threatScore = prefs.getInt("threat_score", 0)
        val lastChange = prefs.getLong(KEY_LAST_LEVEL_CHANGE, 0L)
        val interval = getHeartbeatInterval()

        return ProtectionState(
            currentLevel = level,
            threatScore = threatScore,
            lastLevelChange = lastChange,
            heartbeatInterval = interval
        )
    }

    /**
     * Get current threat score
     */
    fun getThreatScore(): Int = prefs.getInt("threat_score", 0)

    /**
     * Get current protection level
     */
    fun getCurrentProtectionLevel(): ProtectionLevel = currentProtectionLevel.get()

    /**
     * Reset threat score to zero
     */
    fun resetThreatScore() {
        try {
            prefs.edit().apply {
                putInt("threat_score", 0)
            }.apply()
            Log.d(TAG, "Threat score reset to 0")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting threat score", e)
        }
    }
}
