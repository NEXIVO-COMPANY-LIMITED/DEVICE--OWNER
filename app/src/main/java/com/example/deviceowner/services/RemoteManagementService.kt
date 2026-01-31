package com.example.deviceowner.services

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
import com.example.deviceowner.BuildConfig
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.data.remote.ApiEndpoints
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.utils.CustomToast
import com.example.deviceowner.utils.logging.LogManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.*
import retrofit2.Response

/**
 * Background service that polls for remote management commands
 * and executes lock/unlock actions from admin
 */
class RemoteManagementService : Service() {
    
    companion object {
        private const val TAG = "RemoteManagementService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "remote_management_channel"
        private const val CHANNEL_NAME = "Remote Device Management"
        private const val POLL_INTERVAL = 30000L // 30 seconds
        
        private const val PREFS_NAME = "remote_management"
        private const val KEY_LAST_COMMAND_ID = "last_command_id"
        private const val KEY_DEVICE_ID = "device_id"
        
        fun startService(context: Context, deviceId: String) {
            val intent = Intent(context, RemoteManagementService::class.java).apply {
                putExtra("device_id", deviceId)
                action = "START_MANAGEMENT"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, RemoteManagementService::class.java).apply {
                action = "STOP_MANAGEMENT"
            }
            context.startService(intent)
        }
    }
    
    private lateinit var apiClient: ApiClient
    private lateinit var controlManager: RemoteDeviceControlManager
    private var managementJob: Job? = null
    private var deviceId: String? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RemoteManagementService created")
        
        // Initialize services
        apiClient = ApiClient()
        controlManager = RemoteDeviceControlManager(this)
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_MANAGEMENT" -> {
                deviceId = intent.getStringExtra("device_id")
                if (deviceId != null) {
                    startRemoteManagement()
                } else {
                    Log.e(TAG, "No device ID provided")
                    stopSelf()
                }
            }
            "STOP_MANAGEMENT" -> {
                stopRemoteManagement()
            }
        }
        
        return START_STICKY // Restart service if killed
    }
    
    private fun startRemoteManagement() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        deviceId?.let { id ->
            Log.d(TAG, "Starting remote management polling for device: $id")
            saveDeviceId(id)
            
            managementJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        pollForManagementCommands(id)
                        delay(POLL_INTERVAL)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error polling for management commands", e)
                        delay(POLL_INTERVAL) // Continue polling even if one request fails
                    }
                }
            }
        }
    }
    
    private fun stopRemoteManagement() {
        Log.d(TAG, "Stopping remote management service")
        managementJob?.cancel()
        managementJob = null
        stopForeground(true)
        stopSelf()
    }
    
    /**
     * Poll the server for pending management commands
     */
    /**
     * Simple data class for management requests
     */
    private data class ManagementRequest(
        val deviceId: String,
        val action: String,
        val reason: String
    )
    
    private suspend fun pollForManagementCommands(deviceId: String) {
        try {
            // Create a status request to check for pending commands
            val statusRequest = ManagementRequest(
                deviceId = deviceId,
                action = "status",
                reason = "Polling for pending commands"
            )
            
            // Log the management request
            LogManager.logInfo(
                LogManager.LogCategory.API_CALLS,
                "Management polling request: $statusRequest",
                "RemoteManagement"
            )
            
            // For now, simulate a response since we don't have the actual API endpoint
            // In a real implementation, you would make an API call here
            Log.d(TAG, "Polling for management commands for device: $deviceId")
            
            // Simulate checking for pending commands
            // This would be replaced with actual API call when backend is ready
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during management polling", e)
            LogManager.logError(
                LogManager.LogCategory.ERRORS,
                "Management polling failed: ${e.message}",
                "RemoteManagement",
                e
            )
        }
    }
    
    /**
     * Handle management commands from server
     */
    private suspend fun handleManagementResponse(response: Map<String, Any>) {
        withContext(Dispatchers.Main) {
            val actionApplied = response["action_applied"] as? String 
                ?: response["actionApplied"] as? String 
                ?: "unknown"
                
            val message = response["message"] as? String 
                ?: response["reason"] as? String 
                ?: "No reason provided"

            Log.d(TAG, "Received management command: $actionApplied")
            
            when (actionApplied.lowercase()) {
                "lock", "soft_lock" -> {
                    Log.w(TAG, "Applying SOFT LOCK from remote command")
                    controlManager.applySoftLock(message)
                    
                    // Show notification to user
                    CustomToast.showWarning(
                        this@RemoteManagementService,
                        "🔒 Device locked by administrator: $message"
                    )
                    
                    // Send confirmation back to server
                    sendCommandConfirmation(actionApplied, "Soft lock applied successfully")
                }
                
                "hard_lock" -> {
                    Log.e(TAG, "Applying HARD LOCK from remote command")
                    controlManager.applyHardLock(message)
                    
                    // Show notification to user
                    CustomToast.showError(
                        this@RemoteManagementService,
                        "🚫 Device hard locked by administrator: $message"
                    )
                    
                    // Send confirmation back to server
                    sendCommandConfirmation(actionApplied, "Hard lock applied successfully")
                }
                
                "unlock" -> {
                    Log.i(TAG, "Unlocking device from remote command")
                    controlManager.unlockDevice()
                    
                    // Show notification to user
                    CustomToast.showSuccess(
                        this@RemoteManagementService,
                        "🔓 Device unlocked by administrator"
                    )
                    
                    // Send confirmation back to server
                    sendCommandConfirmation(actionApplied, "Device unlocked successfully")
                }
                
                "status" -> {
                    // Status check - no action needed
                    Log.d(TAG, "Status check completed")
                }
                
                else -> {
                    Log.w(TAG, "Unknown management action: $actionApplied")
                }
            }
        }
    }
    
    /**
     * Send confirmation back to server that command was executed
     */
    private suspend fun sendCommandConfirmation(action: String, message: String) {
        try {
            deviceId?.let { id ->
                val confirmationRequest = ManagementRequest(
                    deviceId = id,
                    action = "confirm_$action",
                    reason = message
                )
                
                // Log the confirmation request
                LogManager.logInfo(
                    LogManager.LogCategory.API_CALLS,
                    "Command confirmation: $confirmationRequest",
                    "RemoteManagement"
                )
                
                // For now, just log the confirmation
                // In a real implementation, you would make an API call here
                Log.d(TAG, "Command confirmation logged: $action - $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command confirmation", e)
            LogManager.logError(
                LogManager.LogCategory.ERRORS,
                "Command confirmation failed: ${e.message}",
                "RemoteManagement",
                e
            )
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Remote device management and control"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Management Active")
            .setContentText("Monitoring for admin commands")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    private fun saveDeviceId(deviceId: String) {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RemoteManagementService destroyed")
        managementJob?.cancel()
    }
}