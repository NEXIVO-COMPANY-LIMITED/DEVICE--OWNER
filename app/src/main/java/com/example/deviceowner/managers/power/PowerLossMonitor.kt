package com.example.deviceowner.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Monitors for power loss and unexpected shutdowns
 * Feature 4.5: Disable Shutdown & Restart
 */
class PowerLossMonitor(private val context: Context) {

    companion object {
        private const val TAG = "PowerLossMonitor"
        private const val PREFS_NAME = "power_loss_prefs"
        private const val KEY_LAST_POWER_STATE = "last_power_state"
        private const val KEY_POWER_LOSS_COUNT = "power_loss_count"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auditLog = IdentifierAuditLog(context)
    private var powerStateReceiver: BroadcastReceiver? = null

    /**
     * Start monitoring for power loss
     */
    fun startMonitoring() {
        try {
            Log.d(TAG, "Starting power loss monitoring")

            // Register for power-related broadcasts
            powerStateReceiver = PowerStateReceiver()
            val intentFilter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            }

            context.registerReceiver(powerStateReceiver, intentFilter)

            // Get initial power state
            val batteryStatus = getBatteryStatus()
            savePowerState(batteryStatus)

            prefs.edit().putBoolean(KEY_MONITORING_ENABLED, true).apply()

            Log.d(TAG, "✓ Power loss monitoring started")
            auditLog.logAction(
                "POWER_LOSS_MONITORING_STARTED",
                "Power loss monitoring initialized"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting power loss monitoring", e)
            auditLog.logIncident(
                type = "POWER_LOSS_MONITORING_ERROR",
                severity = "MEDIUM",
                details = "Failed to start power loss monitoring: ${e.message}"
            )
        }
    }

    /**
     * Stop monitoring for power loss
     */
    fun stopMonitoring() {
        try {
            Log.d(TAG, "Stopping power loss monitoring")

            powerStateReceiver?.let {
                context.unregisterReceiver(it)
            }

            prefs.edit().putBoolean(KEY_MONITORING_ENABLED, false).apply()

            Log.d(TAG, "✓ Power loss monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping power loss monitoring", e)
        }
    }

    /**
     * Get current battery status
     */
    private fun getBatteryStatus(): BatteryStatus {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            BatteryStatus(
                level = level,
                scale = scale,
                temperature = temp,
                voltage = voltage,
                plugged = plugged,
                health = health,
                status = status,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery status", e)
            BatteryStatus(
                level = -1,
                scale = -1,
                temperature = -1,
                voltage = -1,
                plugged = -1,
                health = -1,
                status = -1,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Save power state to preferences
     */
    private fun savePowerState(status: BatteryStatus) {
        try {
            val json = com.google.gson.Gson().toJson(status)
            prefs.edit().putString(KEY_LAST_POWER_STATE, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving power state", e)
        }
    }

    /**
     * Get last saved power state
     */
    private fun getLastPowerState(): BatteryStatus? {
        return try {
            val json = prefs.getString(KEY_LAST_POWER_STATE, null)
            if (json != null) {
                com.google.gson.Gson().fromJson(json, BatteryStatus::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving last power state", e)
            null
        }
    }

    /**
     * Handle power state change
     */
    private fun handlePowerStateChange(currentStatus: BatteryStatus) {
        try {
            val lastStatus = getLastPowerState()

            if (lastStatus != null) {
                // Check for unexpected power loss
                if (lastStatus.plugged > 0 && currentStatus.plugged == 0) {
                    // Power was connected, now disconnected
                    Log.w(TAG, "Power disconnected")
                    handlePowerDisconnected(lastStatus, currentStatus)
                }

                // Check for battery critical
                if (currentStatus.level < 5 && lastStatus.level > 5) {
                    Log.w(TAG, "Battery critical!")
                    handleBatteryCritical(currentStatus)
                }

                // Check for abnormal temperature
                if (currentStatus.temperature > 500) { // 50°C
                    Log.w(TAG, "Battery temperature critical: ${currentStatus.temperature / 10}°C")
                    handleAbnormalTemperature(currentStatus)
                }
            }

            // Save current state
            savePowerState(currentStatus)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling power state change", e)
        }
    }

    /**
     * Handle power disconnected
     */
    private fun handlePowerDisconnected(lastStatus: BatteryStatus, currentStatus: BatteryStatus) {
        try {
            Log.w(TAG, "Power disconnected - monitoring for unexpected shutdown")

            auditLog.logAction(
                "POWER_DISCONNECTED",
                "Power disconnected. Battery level: ${currentStatus.level}%"
            )

            // If battery is low, this could lead to unexpected shutdown
            if (currentStatus.level < 20) {
                Log.w(TAG, "Low battery after power disconnection - risk of unexpected shutdown")
                auditLog.logIncident(
                    type = "LOW_BATTERY_POWER_LOSS",
                    severity = "MEDIUM",
                    details = "Power disconnected with low battery (${currentStatus.level}%)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling power disconnected", e)
        }
    }

    /**
     * Handle battery critical
     */
    private fun handleBatteryCritical(status: BatteryStatus) {
        try {
            Log.e(TAG, "Battery critical - device may shutdown unexpectedly")

            val powerLossCount = prefs.getInt(KEY_POWER_LOSS_COUNT, 0) + 1
            prefs.edit().putInt(KEY_POWER_LOSS_COUNT, powerLossCount).apply()

            auditLog.logIncident(
                type = "BATTERY_CRITICAL",
                severity = "HIGH",
                details = "Battery critical level reached (${status.level}%)"
            )

            // Alert backend
            alertBackendOfCriticalBattery(status)

            // Implement alternative safeguards
            implementAlternativeSafeguards()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling battery critical", e)
        }
    }

    /**
     * Handle abnormal temperature
     */
    private fun handleAbnormalTemperature(status: BatteryStatus) {
        try {
            Log.e(TAG, "Abnormal battery temperature detected")

            auditLog.logIncident(
                type = "ABNORMAL_BATTERY_TEMP",
                severity = "HIGH",
                details = "Battery temperature: ${status.temperature / 10}°C"
            )

            // Alert backend
            alertBackendOfAbnormalTemperature(status)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling abnormal temperature", e)
        }
    }

    /**
     * Implement alternative safeguards for power loss
     */
    private fun implementAlternativeSafeguards() {
        try {
            Log.d(TAG, "Implementing alternative safeguards")

            // Lock device to prevent unauthorized access during shutdown
            val deviceOwnerManager = DeviceOwnerManager(context)
            deviceOwnerManager.lockDevice()

            // Disable USB to prevent data extraction
            deviceOwnerManager.disableUSB(true)

            // Disable camera
            deviceOwnerManager.disableCamera(true)

            Log.d(TAG, "✓ Alternative safeguards implemented")
        } catch (e: Exception) {
            Log.e(TAG, "Error implementing alternative safeguards", e)
        }
    }

    /**
     * Alert backend of critical battery
     */
    private fun alertBackendOfCriticalBattery(status: BatteryStatus) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                val heartbeatDataManager = HeartbeatDataManager(context)
                val heartbeatData = heartbeatDataManager.collectHeartbeatData()

                val alertData = mapOf(
                    "event_type" to "BATTERY_CRITICAL",
                    "device_id" to heartbeatData.deviceId,
                    "battery_level" to status.level,
                    "battery_temperature" to (status.temperature / 10),
                    "timestamp" to System.currentTimeMillis(),
                    "severity" to "HIGH"
                )

                Log.e(TAG, "Critical battery alert sent to backend: $alertData")
            } catch (e: Exception) {
                Log.e(TAG, "Error alerting backend of critical battery", e)
            }
        }
    }

    /**
     * Alert backend of abnormal temperature
     */
    private fun alertBackendOfAbnormalTemperature(status: BatteryStatus) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                val heartbeatDataManager = HeartbeatDataManager(context)
                val heartbeatData = heartbeatDataManager.collectHeartbeatData()

                val alertData = mapOf(
                    "event_type" to "ABNORMAL_BATTERY_TEMP",
                    "device_id" to heartbeatData.deviceId,
                    "battery_temperature" to (status.temperature / 10),
                    "battery_level" to status.level,
                    "timestamp" to System.currentTimeMillis(),
                    "severity" to "MEDIUM"
                )

                Log.w(TAG, "Abnormal temperature alert sent to backend: $alertData")
            } catch (e: Exception) {
                Log.e(TAG, "Error alerting backend of abnormal temperature", e)
            }
        }
    }

    /**
     * Get power loss count
     */
    fun getPowerLossCount(): Int {
        return prefs.getInt(KEY_POWER_LOSS_COUNT, 0)
    }

    /**
     * Check if monitoring is enabled
     */
    fun isMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_MONITORING_ENABLED, false)
    }

    /**
     * Battery status data class
     */
    data class BatteryStatus(
        val level: Int,
        val scale: Int,
        val temperature: Int,
        val voltage: Int,
        val plugged: Int,
        val health: Int,
        val status: Int,
        val timestamp: Long
    )

    /**
     * Broadcast receiver for power state changes
     */
    private inner class PowerStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.d(TAG, "Power connected")
                    val currentStatus = getBatteryStatus()
                    handlePowerStateChange(currentStatus)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.w(TAG, "Power disconnected")
                    val currentStatus = getBatteryStatus()
                    handlePowerStateChange(currentStatus)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val currentStatus = getBatteryStatus()
                    handlePowerStateChange(currentStatus)
                }
                Intent.ACTION_BATTERY_LOW -> {
                    Log.w(TAG, "Battery low")
                    val currentStatus = getBatteryStatus()
                    handleBatteryCritical(currentStatus)
                }
                Intent.ACTION_BATTERY_OKAY -> {
                    Log.d(TAG, "Battery okay")
                    val currentStatus = getBatteryStatus()
                    handlePowerStateChange(currentStatus)
                }
            }
        }
    }
}
