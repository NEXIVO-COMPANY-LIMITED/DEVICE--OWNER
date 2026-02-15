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

        fun startService(context: Context) {
            val intent = Intent(context, FirmwareSecurityMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        private const val NOTIFICATION_ID = 1002
        private const val CRITICAL_ALERT_NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "firmware_security_monitor_channel"
        private const val CHANNEL_NAME = "Firmware Security"
        private const val CRITICAL_CHANNEL_ID = "firmware_security_critical_channel"
        private const val CRITICAL_CHANNEL_NAME = "Security Alerts"
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
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Firmware security monitoring (bootloader, violations)"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
            val criticalChannel = NotificationChannel(
                CRITICAL_CHANNEL_ID,
                CRITICAL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical security violation alerts"
                setShowBadge(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(criticalChannel)
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
        val status = FirmwareSecurity.checkSecurityStatus()
        val total = status?.violations?.total ?: 0
        if (total > MAX_VIOLATIONS_BEFORE_LOCK) handleExcessiveViolations(total)
        if (v.severity == "CRITICAL") handleCriticalViolation(v)
    }

    private fun handleExcessiveViolations(count: Long) {
        Log.e(TAG, "EXCESSIVE VIOLATIONS DETECTED: $count")
        
        scope.launch {
            try {
                // Trigger enhanced security measures
                val enhancedSecurity = com.example.deviceowner.security.enforcement.EnhancedSecurityManager(this@FirmwareSecurityMonitorService)
                enhancedSecurity.apply100PercentPerfectSecurity()
                
                // Trigger hard lock as last resort
                triggerHardLock()
                
                Log.i(TAG, "Enhanced security measures applied due to excessive violations")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle excessive violations", e)
            }
        }
    }

    private fun handleCriticalViolation(v: FirmwareSecurity.Violation) {
        Log.e(TAG, "CRITICAL SECURITY VIOLATION: ${v.type} - ${v.details}")
        showCriticalViolationAlert(v)
        scope.launch {
            try {
                // Trigger immediate security response based on violation type
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

    private fun showCriticalViolationAlert(v: FirmwareSecurity.Violation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = NotificationCompat.Builder(this, CRITICAL_CHANNEL_ID)
                .setContentTitle("Critical security violation")
                .setContentText("${v.type}: ${v.details}")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(CRITICAL_ALERT_NOTIFICATION_ID, notification)
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
