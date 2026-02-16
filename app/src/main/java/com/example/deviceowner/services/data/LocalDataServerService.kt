package com.example.deviceowner.services.data

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
import com.example.deviceowner.R
import com.example.deviceowner.services.data.DeviceDataCollector
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

/**
 * Local JSON Data Server Service
 * Serves device data, logs, and errors in JSON format via HTTP
 * Accessible at http://localhost:8080 or http://device_ip:8080
 */
class LocalDataServerService : Service() {
    
    companion object {
        private const val TAG = "LocalDataServer"
        private const val SERVER_PORT = 8080
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "local_data_server"
        
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
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "🖥️ Local Data Server Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 Starting Local Data Server on port $SERVER_PORT")
        
        startForeground(NOTIFICATION_ID, createNotification())
        startServer()
        
        return START_STICKY // Restart if killed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()
        Log.d(TAG, "🛑 Local Data Server Service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startServer() {
        serviceScope.launch {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                isRunning = true
                
                Log.d(TAG, "✅ Local Data Server started on port $SERVER_PORT")
                Log.d(TAG, "📱 Access at: http://localhost:$SERVER_PORT")
                Log.d(TAG, "🌐 Or from network: http://[device_ip]:$SERVER_PORT")
                
                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        launch { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client connection: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start server: ${e.message}", e)
            }
        }
    }
    
    private fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            Log.d(TAG, "🛑 Local Data Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server: ${e.message}")
        }
    }
    
    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)
            
            // Read HTTP request
            val requestLine = reader.readLine()
            Log.d(TAG, "📥 Request: $requestLine")
            
            // Skip headers
            var line: String?
            do {
                line = reader.readLine()
            } while (line != null && line.isNotEmpty())
            
            // Parse request
            val parts = requestLine?.split(" ") ?: listOf("GET", "/", "HTTP/1.1")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"
            
            // Handle request
            val response = when {
                path == "/" -> handleRootRequest()
                path == "/api/device/data" -> handleDeviceDataRequest()
                path == "/api/device/logs" -> handleLogsRequest()
                path == "/api/device/errors" -> handleErrorsRequest()
                path == "/api/device/status" -> handleStatusRequest()
                path == "/api/device/registration" -> handleRegistrationRequest()
                path == "/api/device/heartbeat" -> handleHeartbeatRequest()
                path == "/api/device/security" -> handleSecurityRequest()
                path == "/api/device/all" -> handleAllDataRequest()
                path.startsWith("/api/") -> handleApiNotFound(path)
                else -> handleStaticFile(path)
            }
            
            // Send HTTP response
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: ${response.contentType}")
            writer.println("Access-Control-Allow-Origin: *")
            writer.println("Access-Control-Allow-Methods: GET, POST, OPTIONS")
            writer.println("Access-Control-Allow-Headers: Content-Type")
            writer.println("Content-Length: ${response.content.toByteArray().size}")
            writer.println()
            writer.print(response.content)
            writer.flush()
            
            Log.d(TAG, "📤 Response sent for $path (${response.content.length} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client: ${e.message}", e)
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private suspend fun handleRootRequest(): ServerResponse {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Device Owner Local Data Server</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
                    .container { max-width: 800px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    h1 { color: #2196F3; text-align: center; }
                    .endpoint { background: #f8f9fa; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #2196F3; }
                    .endpoint h3 { margin: 0 0 10px 0; color: #333; }
                    .endpoint p { margin: 5px 0; color: #666; }
                    .endpoint a { color: #2196F3; text-decoration: none; font-family: monospace; }
                    .endpoint a:hover { text-decoration: underline; }
                    .status { text-align: center; padding: 10px; background: #e8f5e8; border-radius: 5px; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>📱 Device Owner Local Data Server</h1>
                    <div class="status">
                        <strong>✅ Server Running on Port $SERVER_PORT</strong><br>
                        <small>Last Updated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</small>
                    </div>
                    
                    <h2>📊 Available API Endpoints:</h2>
                    
                    <div class="endpoint">
                        <h3>🔍 Device Data</h3>
                        <p>Complete device information (IMEI, Serial, etc.)</p>
                        <a href="/api/device/data">/api/device/data</a>
                    </div>
                    
                    <div class="endpoint">
                        <h3>📋 Application Logs</h3>
                        <p>All application logs and debug information</p>
                        <a href="/api/device/logs">/api/device/logs</a>
                    </div>
                    
                    <div class="endpoint">
                        <h3>❌ Error Logs</h3>
                        <p>Registration errors and exceptions</p>
                        <a href="/api/device/errors">/api/device/errors</a>
                    </div>
                    
                    <div class="endpoint">
                        <h3>📊 Device Status</h3>
                        <p>Device Owner status, registration state, security info</p>
                        <a href="/api/device/status">/api/device/status</a>
                    </div>
                    
                    <div class="endpoint">
                        <h3>📝 Registration Data</h3>
                        <p>Device registration information and history</p>
                        <a href="/api/device/registration">/api/device/registration</a>
                    </div>
                    
                    <div class="endpoint">
                        <h3>💓 Heartbeat Data</h3>
                        <p>Heartbeat logs and server communication</p>
                        <a href="/api/device/heartbeat">/api/device/heartbeat</a>
                    </div>
                    
                    <div class="endpoint">
                        <h3>🔒 Security Information</h3>
                        <p>Security policies, restrictions, and violations</p>
                        <a href="/api/device/security">/api/device/security</a>
                    </div>
                    
                    <div class="endpoint">
                        <h3>📦 All Data (Combined)</h3>
                        <p>Complete device data in single JSON response</p>
                        <a href="/api/device/all">/api/device/all</a>
                    </div>
                    
                    <h2>💡 Usage Examples:</h2>
                    <div class="endpoint">
                        <h3>From Browser:</h3>
                        <p>Visit any endpoint above to view JSON data</p>
                    </div>
                    
                    <div class="endpoint">
                        <h3>From Command Line:</h3>
                        <p><code>curl http://localhost:$SERVER_PORT/api/device/data</code></p>
                        <p><code>curl http://[device_ip]:$SERVER_PORT/api/device/all</code></p>
                    </div>
                    
                    <div class="endpoint">
                        <h3>From Network:</h3>
                        <p>Access from other devices on same network using device IP</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        return ServerResponse(html, "text/html")
    }
    
    private suspend fun handleDeviceDataRequest(): ServerResponse {
        return try {
            val deviceCollector = DeviceDataCollector(this@LocalDataServerService)
            val deviceData = deviceCollector.collectDeviceDataForLocalServer()
            
            val jsonData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "formatted_time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "device_data" to deviceData,
                "collection_status" to "success"
            )
            
            ServerResponse(gson.toJson(jsonData), "application/json")
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting device data: ${e.message}", e)
            val errorData = mapOf(
                "error" to "Failed to collect device data",
                "message" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
            ServerResponse(gson.toJson(errorData), "application/json")
        }
    }
    
    private suspend fun handleLogsRequest(): ServerResponse {
        return try {
            val logs = mutableListOf<Map<String, Any>>()
            
            // Read application logs
            val logFiles = listOf(
                File(filesDir, "device_owner_logs.txt"),
                File(getExternalFilesDir(null), "DeviceOwnerLogs/app_logs.txt")
            )
            
            logFiles.forEach { logFile ->
                if (logFile.exists()) {
                    val logLines = logFile.readLines().takeLast(100) // Last 100 lines
                    logLines.forEach { line ->
                        if (line.isNotBlank()) {
                            logs.add(mapOf(
                                "timestamp" to System.currentTimeMillis(),
                                "source" to logFile.name,
                                "message" to line
                            ))
                        }
                    }
                }
            }
            
            val jsonData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "total_logs" to logs.size,
                "logs" to logs
            )
            
            ServerResponse(gson.toJson(jsonData), "application/json")
        } catch (e: Exception) {
            val errorData = mapOf(
                "error" to "Failed to read logs",
                "message" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
            ServerResponse(gson.toJson(errorData), "application/json")
        }
    }
    
    private suspend fun handleErrorsRequest(): ServerResponse {
        return try {
            val errors = mutableListOf<Map<String, Any>>()
            
            // Read registration errors
            val errorDir = File(getExternalFilesDir(null), "DeviceOwner/RegistrationErrors")
            if (errorDir.exists()) {
                errorDir.listFiles()?.forEach { errorFile ->
                    if (errorFile.isFile() && errorFile.name.endsWith(".html")) {
                        try {
                            val content = errorFile.readText()
                            errors.add(mapOf(
                                "filename" to errorFile.name,
                                "timestamp" to errorFile.lastModified(),
                                "size" to errorFile.length(),
                                "content" to content
                            ))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to read error file ${errorFile.name}: ${e.message}")
                        }
                    }
                }
            }
            
            val jsonData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "total_errors" to errors.size,
                "errors" to errors
            )
            
            ServerResponse(gson.toJson(jsonData), "application/json")
        } catch (e: Exception) {
            val errorData = mapOf(
                "error" to "Failed to read error files",
                "message" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
            ServerResponse(gson.toJson(errorData), "application/json")
        }
    }
    
    private suspend fun handleStatusRequest(): ServerResponse {
        return try {
            val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(this@LocalDataServerService)
            val prefsManager = com.example.deviceowner.utils.storage.SharedPreferencesManager(this@LocalDataServerService)
            
            val statusData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "formatted_time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "device_owner" to mapOf(
                    "is_device_owner" to deviceOwnerManager.isDeviceOwner(),
                    "is_device_admin" to deviceOwnerManager.isDeviceAdmin()
                ),
                "registration" to mapOf(
                    "is_registered" to prefsManager.isDeviceRegistered(),
                    "loan_number" to prefsManager.getLoanNumber()
                ),
                "network" to mapOf(
                    "base_url" to com.example.deviceowner.AppConfig.BASE_URL,
                    "ssl_enabled" to true,
                    "certificate_pinning" to true
                ),
                "services" to mapOf(
                    "local_server_running" to isRunning,
                    "local_server_port" to SERVER_PORT
                )
            )
            
            ServerResponse(gson.toJson(statusData), "application/json")
        } catch (e: Exception) {
            val errorData = mapOf(
                "error" to "Failed to get device status",
                "message" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
            ServerResponse(gson.toJson(errorData), "application/json")
        }
    }
    
    private suspend fun handleRegistrationRequest(): ServerResponse {
        return try {
            val repository = com.example.deviceowner.data.repository.DeviceRegistrationRepository(this@LocalDataServerService)
            val registrationData = repository.getAllRegistrationData()
            
            val jsonData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "total_records" to registrationData.size,
                "registration_data" to registrationData
            )
            
            ServerResponse(gson.toJson(jsonData), "application/json")
        } catch (e: Exception) {
            val errorData = mapOf(
                "error" to "Failed to get registration data",
                "message" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
            ServerResponse(gson.toJson(errorData), "application/json")
        }
    }
    
    private suspend fun handleHeartbeatRequest(): ServerResponse {
        return try {
            // Read heartbeat logs if they exist
            val heartbeatLogs = mutableListOf<Map<String, Any>>()
            
            val heartbeatFile = File(getExternalFilesDir(null), "DeviceOwnerLogs/heartbeat_logs.txt")
            if (heartbeatFile.exists()) {
                val lines = heartbeatFile.readLines().takeLast(50)
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        heartbeatLogs.add(mapOf(
                            "timestamp" to System.currentTimeMillis(),
                            "message" to line
                        ))
                    }
                }
            }
            
            val jsonData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "total_heartbeats" to heartbeatLogs.size,
                "heartbeat_logs" to heartbeatLogs
            )
            
            ServerResponse(gson.toJson(jsonData), "application/json")
        } catch (e: Exception) {
            val errorData = mapOf(
                "error" to "Failed to get heartbeat data",
                "message" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
            ServerResponse(gson.toJson(errorData), "application/json")
        }
    }
    
    private suspend fun handleSecurityRequest(): ServerResponse {
        return try {
            val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(this@LocalDataServerService)
            
            val securityData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "device_owner_status" to mapOf(
                    "is_device_owner" to deviceOwnerManager.isDeviceOwner(),
                    "is_device_admin" to deviceOwnerManager.isDeviceAdmin()
                ),
                "ssl_security" to mapOf(
                    "https_enabled" to true,
                    "certificate_pinning" to true,
                    "certificate_pin" to "y8S/sGw+VDqpDXnu4dKxrnI6nj1tdn0od2WAFM7zvog=",
                    "tls_version" to "1.2+"
                ),
                "restrictions" to mapOf(
                    "factory_reset_blocked" to true,
                    "developer_options_blocked" to true,
                    "safe_mode_blocked" to true,
                    "app_uninstall_blocked" to true
                )
            )
            
            ServerResponse(gson.toJson(securityData), "application/json")
        } catch (e: Exception) {
            val errorData = mapOf(
                "error" to "Failed to get security data",
                "message" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
            ServerResponse(gson.toJson(errorData), "application/json")
        }
    }
    
    private suspend fun handleAllDataRequest(): ServerResponse {
        return try {
            // Collect all data in one response
            val deviceCollector = DeviceDataCollector(this@LocalDataServerService)
            val deviceData = deviceCollector.collectDeviceDataForLocalServer()
            
            val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(this@LocalDataServerService)
            val prefsManager = com.example.deviceowner.utils.storage.SharedPreferencesManager(this@LocalDataServerService)
            val repository = com.example.deviceowner.data.repository.DeviceRegistrationRepository(this@LocalDataServerService)
            
            val allData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "formatted_time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "device_data" to deviceData,
                "status" to mapOf(
                    "is_device_owner" to deviceOwnerManager.isDeviceOwner(),
                    "is_device_admin" to deviceOwnerManager.isDeviceAdmin(),
                    "is_registered" to prefsManager.isDeviceRegistered(),
                    "loan_number" to prefsManager.getLoanNumber()
                ),
                "registration_data" to repository.getAllRegistrationData(),
                "network_config" to mapOf(
                    "base_url" to com.example.deviceowner.AppConfig.BASE_URL,
                    "ssl_enabled" to true,
                    "certificate_pinning" to true,
                    "certificate_pin" to "y8S/sGw+VDqpDXnu4dKxrnI6nj1tdn0od2WAFM7zvog="
                ),
                "server_info" to mapOf(
                    "local_server_running" to isRunning,
                    "local_server_port" to SERVER_PORT,
                    "endpoints" to listOf(
                        "/api/device/data",
                        "/api/device/logs",
                        "/api/device/errors",
                        "/api/device/status",
                        "/api/device/registration",
                        "/api/device/heartbeat",
                        "/api/device/security",
                        "/api/device/all"
                    )
                )
            )
            
            ServerResponse(gson.toJson(allData), "application/json")
        } catch (e: Exception) {
            val errorData = mapOf(
                "error" to "Failed to collect all data",
                "message" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
            ServerResponse(gson.toJson(errorData), "application/json")
        }
    }
    
    private fun handleApiNotFound(path: String): ServerResponse {
        val errorData = mapOf(
            "error" to "API endpoint not found",
            "path" to path,
            "available_endpoints" to listOf(
                "/api/device/data",
                "/api/device/logs", 
                "/api/device/errors",
                "/api/device/status",
                "/api/device/registration",
                "/api/device/heartbeat",
                "/api/device/security",
                "/api/device/all"
            ),
            "timestamp" to System.currentTimeMillis()
        )
        return ServerResponse(gson.toJson(errorData), "application/json")
    }
    
    private fun handleStaticFile(path: String): ServerResponse {
        return ServerResponse("File not found: $path", "text/plain")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local Data Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Local JSON data server for device information"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📱 Local Data Server")
            .setContentText("Running on port $SERVER_PORT - Access at http://localhost:$SERVER_PORT")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private data class ServerResponse(
        val content: String,
        val contentType: String
    )
}