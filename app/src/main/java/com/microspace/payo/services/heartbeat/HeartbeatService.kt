package com.microspace.payo.services.heartbeat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.microspace.payo.data.DeviceIdProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HeartbeatService - Persistent Background Service with Location Transparency
 */
class HeartbeatService : Service() {
    
    companion object {
        private const val TAG = "HeartbeatService"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "heartbeat_channel_v3"
        private const val HEARTBEAT_INTERVAL_MS = 10_000L  // 10 SECONDS
        
        fun start(context: Context, deviceId: String? = null) {
            val intent = Intent(context, HeartbeatService::class.java)
            if (deviceId != null) intent.putExtra("device_id", deviceId)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, HeartbeatService::class.java))
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var heartbeatManager: HeartbeatManager
    private lateinit var responseHandler: HeartbeatResponseHandler_v2
    private var heartbeatRunnable: Runnable? = null
    private val isRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        heartbeatManager = HeartbeatManager(this)
        responseHandler = HeartbeatResponseHandler_v2(this)
        createNotificationChannel()
        
        // Start foreground immediately with a visible notification for compliance
        val notification = createNotification("System is monitoring device security and location")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires explicit types in startForeground
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ requires location type if location is accessed
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceId = intent?.getStringExtra("device_id") ?: DeviceIdProvider.getDeviceId(this)
        
        if (deviceId.isNullOrBlank()) {
            Log.e(TAG, "❌ Device ID not found. Stopping HeartbeatService.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning.getAndSet(true)) {
            Log.i(TAG, "🚀 Heartbeat Loop Started for: $deviceId")
            startHeartbeatLoop()
        }
        
        return START_STICKY
    }
    
    private fun startHeartbeatLoop() {
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (!isRunning.get()) return
                
                serviceScope.launch {
                    try {
                        val response = heartbeatManager.sendHeartbeat()
                        if (response != null) {
                            responseHandler.handle(response)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Loop Error: ${e.message}")
                    }
                }
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
        handler.post(heartbeatRunnable!!)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Using IMPORTANCE_LOW (visible in drawer, no sound/pop-up) for privacy compliance
            val channel = NotificationChannel(CHANNEL_ID, "Security Monitoring", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Ensures device security and location synchronization."
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Location icon for transparency
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        isRunning.set(false)
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        serviceScope.cancel()
        super.onDestroy()
    }
}
