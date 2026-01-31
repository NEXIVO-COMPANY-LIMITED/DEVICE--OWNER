package com.example.deviceowner.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.deviceowner.core.heartbeat.HeartbeatManager
import com.example.deviceowner.monitoring.SecurityMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class HeartbeatService : Service() {
    
    companion object {
        private const val TAG = "HeartbeatService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "device_monitoring_channel"
        private const val CHANNEL_NAME = "Device Monitoring"
        
        // Heartbeat intervals
        private val HEARTBEAT_INTERVAL_NORMAL = TimeUnit.MINUTES.toMillis(15) // 15 minutes
        private val HEARTBEAT_INTERVAL_FREQUENT = TimeUnit.MINUTES.toMillis(5) // 5 minutes for tampered devices
        private val HEARTBEAT_INTERVAL_INITIAL = TimeUnit.MINUTES.toMillis(1) // 1 minute after registration
        
        fun startService(context: Context, deviceId: String) {
            val intent = Intent(context, HeartbeatService::class.java).apply {
                putExtra("device_id", deviceId)
                action = "START_MONITORING"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, HeartbeatService::class.java).apply {
                action = "STOP_MONITORING"
            }
            context.startService(intent)
        }
    }
    
    private var deviceId: String? = null
    private lateinit var heartbeatManager: HeartbeatManager
    private var heartbeatJob: Job? = null
    private var heartbeatInterval = HEARTBEAT_INTERVAL_NORMAL
    private var heartbeatCount = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeartbeatService created")
        
        heartbeatManager = HeartbeatManager(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_MONITORING" -> {
                deviceId = intent.getStringExtra("device_id")
                if (deviceId != null) {
                    startForegroundMonitoring()
                } else {
                    Log.e(TAG, "No device ID provided")
                    stopSelf()
                }
            }
            "STOP_MONITORING" -> {
                stopMonitoring()
            }
            "SEND_HEARTBEAT_NOW" -> {
                // Manual heartbeat trigger
                sendImmediateHeartbeat()
            }
        }
        
        return START_STICKY // Restart service if killed
    }
    
    private fun startForegroundMonitoring() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        deviceId?.let { id ->
            Log.d(TAG, "Starting foreground monitoring for device: $id")
            
            // Start the security monitoring service
            SecurityMonitorService.startService(this)
            
            // Start heartbeat loop
            startHeartbeatLoop()
        }
    }
    
    private fun startHeartbeatLoop() {
        // Cancel existing heartbeat job
        heartbeatJob?.cancel()
        
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if device can send heartbeats
                if (!heartbeatManager.canSendHeartbeat()) {
                    Log.w(TAG, "Device is not registered - cannot send heartbeats")
                    return@launch
                }
                
                Log.d(TAG, "Starting heartbeat loop with interval: ${heartbeatInterval / 1000}s")
                
                // Send initial heartbeat immediately
                sendHeartbeat()
                
                // Continue with regular intervals
                while (true) {
                    delay(heartbeatInterval)
                    sendHeartbeat()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Heartbeat loop error: ${e.message}", e)
            }
        }
    }
    
    private suspend fun sendHeartbeat() {
        try {
            heartbeatCount++
            Log.d(TAG, "Sending heartbeat #$heartbeatCount...")
            
            val response = heartbeatManager.sendHeartbeat()
            
            if (response != null) {
                Log.d(TAG, "Heartbeat #$heartbeatCount sent successfully")
                
                // Adjust heartbeat interval based on server response
                adjustHeartbeatInterval(response)
                
                // Update notification with last heartbeat time
                updateNotification("Last heartbeat: ${System.currentTimeMillis()}")
                
            } else {
                Log.w(TAG, "Heartbeat #$heartbeatCount failed")
                
                // Increase frequency on failure (but not too much)
                if (heartbeatInterval > HEARTBEAT_INTERVAL_FREQUENT) {
                    heartbeatInterval = HEARTBEAT_INTERVAL_FREQUENT
                    Log.d(TAG, "Increased heartbeat frequency due to failure")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending heartbeat: ${e.message}", e)
        }
    }
    
    private fun adjustHeartbeatInterval(response: com.example.deviceowner.data.models.HeartbeatResponse) {
        val oldInterval = heartbeatInterval
        
        // Adjust based on tamper indicators
        val hasTamperIndicators = !response.tamperIndicators.isNullOrEmpty()
        val isDeviceLocked = response.management?.isLocked == true
        
        heartbeatInterval = when {
            isDeviceLocked -> HEARTBEAT_INTERVAL_FREQUENT // More frequent if locked
            hasTamperIndicators -> HEARTBEAT_INTERVAL_FREQUENT // More frequent if tampered
            heartbeatCount < 10 -> HEARTBEAT_INTERVAL_INITIAL // More frequent initially
            else -> HEARTBEAT_INTERVAL_NORMAL // Normal frequency
        }
        
        if (oldInterval != heartbeatInterval) {
            Log.d(TAG, "Heartbeat interval changed from ${oldInterval / 1000}s to ${heartbeatInterval / 1000}s")
            
            // Restart heartbeat loop with new interval
            startHeartbeatLoop()
        }
    }
    
    private fun sendImmediateHeartbeat() {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Sending immediate heartbeat...")
            sendHeartbeat()
        }
    }
    
    private fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring service")
        
        // Cancel heartbeat job
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // Stop security monitoring
        SecurityMonitorService.stopService(this)
        
        stopForeground(true)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Device monitoring and security heartbeat"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String = "Monitoring device security and compliance"): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Device Monitoring Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "HeartbeatService destroyed")
        
        // Cancel heartbeat job
        heartbeatJob?.cancel()
        
        // Stop security monitoring
        SecurityMonitorService.stopService(this)
    }
}