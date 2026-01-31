package com.example.deviceowner.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.deviceowner.security.firmware.FirmwareSecurity
import kotlinx.coroutines.*

/**
 * Firmware security monitoring. See docs/android-firmware-security-complete,
 * docs/deviceowner-firmware-integration-complete. Start after successful registration.
 */
class FirmwareSecurityMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "FirmwareSecurityMonitor"
        private const val MAX_VIOLATIONS_BEFORE_LOCK = 10
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "firmware_security_monitor_channel"
        private const val CHANNEL_NAME = "Firmware Security"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        Log.i(TAG, "Firmware Security Monitor Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Firmware security monitoring started")
        // Required on O+: startForeground within ~5s when started via startForegroundService (e.g. from BootReceiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        scope.launch { monitorSecurityViolations() }
        scope.launch { periodicStatusCheck() }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Firmware security monitoring (bootloader, violations)"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Firmware Security Active")
        .setContentText("Monitoring bootloader and security violations")
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setAutoCancel(false)
        .build()

    private suspend fun monitorSecurityViolations() {
        FirmwareSecurity.monitorViolations(this@FirmwareSecurityMonitorService) { v ->
            handleViolation(v)
        }
    }

    private suspend fun periodicStatusCheck() {
        while (scope.isActive) {
            try {
                val status = FirmwareSecurity.checkSecurityStatus()
                if (status != null) {
                    Log.d(TAG, "Security: secured=${status.isFullySecured}, violations=${status.violations.total}")
                }
                delay(60_000)
            } catch (e: Exception) {
                Log.e(TAG, "Error in periodic status check", e)
                delay(120_000)
            }
        }
    }

    private fun handleViolation(v: FirmwareSecurity.Violation) {
        Log.w(TAG, "Violation: ${v.type} severity=${v.severity} details=${v.details}")
        scope.launch {
            try {
                reportViolationToServer(v)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report violation", e)
            }
        }
        val status = FirmwareSecurity.checkSecurityStatus()
        val total = status?.violations?.total ?: 0
        if (total > MAX_VIOLATIONS_BEFORE_LOCK) handleExcessiveViolations(total)
        if (v.severity == "CRITICAL") handleCriticalViolation(v)
    }

    private suspend fun reportViolationToServer(v: FirmwareSecurity.Violation) {
        withContext(Dispatchers.IO) {
            try {
                val apiClient = com.example.deviceowner.data.remote.ApiClient()
                val deviceId = getCurrentDeviceId()
                
                // Create violation report payload
                val violationData = mapOf(
                    "device_id" to deviceId,
                    "timestamp" to v.timestamp,
                    "violation_type" to v.type,
                    "severity" to v.severity,
                    "details" to v.details,
                    "security_status" to (FirmwareSecurity.checkSecurityStatus()?.let { status ->
                        mapOf(
                            "bootloaderLocked" to status.bootloaderLocked,
                            "securityEnabled" to status.securityEnabled,
                            "buttonBlocking" to status.buttonBlocking,
                            "violations" to mapOf(
                                "total" to status.violations.total,
                                "recovery" to status.violations.recovery,
                                "fastboot" to status.violations.fastboot
                            ),
                            "lastViolation" to status.lastViolation,
                            "timestamp" to status.timestamp
                        )
                    } ?: emptyMap())
                )
                
                // Send to server using existing endpoint
                val response = apiClient.reportSecurityViolation(deviceId, violationData)
                
                if (response.isSuccessful) {
                    Log.i(TAG, "Violation reported successfully: ${v.type}")
                } else {
                    Log.e(TAG, "Failed to report violation: ${response.code()}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting violation to server", e)
                // Store for retry later
                storeViolationForRetry(v)
            }
        }
    }
    
    private fun getCurrentDeviceId(): String {
        return try {
            val prefsManager = com.example.deviceowner.utils.SharedPreferencesManager(this)
            prefsManager.getDeviceId() ?: android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device ID", e)
            "unknown_device"
        }
    }
    
    private fun storeViolationForRetry(v: FirmwareSecurity.Violation) {
        // Store in local database for retry when network is available
        scope.launch {
            try {
                // You can implement this using your existing Room database
                // For now, just log the violation for manual review
                Log.w(TAG, "Storing violation for retry: ${v.type} - ${v.details} at ${v.timestamp}")
                
                // TODO: Implement proper database storage
                // val database = AppDatabase.getDatabase(this@FirmwareSecurityMonitorService)
                // database.violationDao().insertViolation(v.toEntity())
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store violation for retry", e)
            }
        }
    }

    private fun handleExcessiveViolations(count: Long) {
        Log.e(TAG, "EXCESSIVE VIOLATIONS DETECTED: $count")
        
        scope.launch {
            try {
                // 1. Notify server of excessive violations
                val violationData = mapOf(
                    "device_id" to getCurrentDeviceId(),
                    "violation_type" to "EXCESSIVE_VIOLATIONS",
                    "severity" to "CRITICAL",
                    "details" to "Total violations exceeded threshold: $count",
                    "timestamp" to System.currentTimeMillis(),
                    "action_required" to true
                )
                
                val apiClient = com.example.deviceowner.data.remote.ApiClient()
                apiClient.reportSecurityViolation(getCurrentDeviceId(), violationData)
                
                // 2. Trigger enhanced security measures
                val enhancedSecurity = com.example.deviceowner.security.enforcement.EnhancedSecurityManager(this@FirmwareSecurityMonitorService)
                enhancedSecurity.apply100PercentPerfectSecurity()
                
                // 3. Trigger hard lock as last resort
                triggerHardLock()
                
                Log.i(TAG, "Enhanced security measures applied due to excessive violations")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle excessive violations", e)
            }
        }
    }

    private fun handleCriticalViolation(v: FirmwareSecurity.Violation) {
        Log.e(TAG, "CRITICAL SECURITY VIOLATION: ${v.type} - ${v.details}")
        
        scope.launch {
            try {
                // 1. Immediate server notification
                val violationData = mapOf(
                    "device_id" to getCurrentDeviceId(),
                    "violation_type" to "CRITICAL_SECURITY_BREACH",
                    "severity" to "CRITICAL",
                    "details" to "Critical violation: ${v.type} - ${v.details}",
                    "timestamp" to v.timestamp,
                    "immediate_action_required" to true,
                    "original_violation" to mapOf(
                        "type" to v.type,
                        "severity" to v.severity,
                        "details" to v.details,
                        "timestamp" to v.timestamp
                    )
                )
                
                val apiClient = com.example.deviceowner.data.remote.ApiClient()
                apiClient.reportSecurityViolation(getCurrentDeviceId(), violationData)
                
                // 2. Trigger immediate security response based on violation type
                when (v.type) {
                    "BOOTLOADER_UNLOCKED", "FASTBOOT_UNLOCKED" -> {
                        Log.e(TAG, "DEVICE COMPROMISED - bootloader/fastboot unlocked")
                        triggerHardLock()
                    }
                    "RECOVERY_ATTEMPT" -> {
                        Log.e(TAG, "Recovery mode access attempt detected")
                        triggerHardLock()
                    }
                    "EDL_ATTEMPT" -> {
                        Log.e(TAG, "EDL mode access attempt - CRITICAL SECURITY BREACH")
                        triggerHardLock()
                    }
                    "ADB_ROOT_ATTEMPT" -> {
                        Log.e(TAG, "ADB root access attempt detected")
                        // Apply enhanced restrictions but don't hard lock yet
                        val enhancedSecurity = com.example.deviceowner.security.enforcement.EnhancedSecurityManager(this@FirmwareSecurityMonitorService)
                        enhancedSecurity.apply100PercentPerfectSecurity()
                    }
                    else -> {
                        Log.w(TAG, "Unknown critical violation type: ${v.type}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle critical violation", e)
            }
        }
    }

    private fun triggerHardLock() {
        try {
            Log.e(TAG, "TRIGGERING HARD LOCK DUE TO CRITICAL SECURITY VIOLATION")
            
            val intent = Intent(this, com.example.deviceowner.ui.activities.lock.HardLockActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra("lock_reason", "FIRMWARE_SECURITY_VIOLATION")
            intent.putExtra("lock_timestamp", System.currentTimeMillis())
            startActivity(intent)
            
            Log.i(TAG, "Hard lock triggered successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "FAILED TO TRIGGER HARD LOCK - CRITICAL ERROR", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
