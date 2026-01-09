package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.managers.HeartbeatDataManager
import com.example.deviceowner.data.api.HeartbeatApiService
import com.example.deviceowner.managers.DeviceMismatchHandler
import com.example.deviceowner.managers.UpdateManager
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Service for continuous device heartbeat verification
 * Sends heartbeat data to backend and checks for data changes
 * Processes blocking commands from backend
 */
class HeartbeatVerificationService : Service() {
    
    private lateinit var heartbeatDataManager: HeartbeatDataManager
    private lateinit var apiService: HeartbeatApiService
    private lateinit var mismatchHandler: DeviceMismatchHandler
    private lateinit var updateManager: UpdateManager
    private lateinit var locationManager: com.example.deviceowner.managers.LocationManager
    private var heartbeatJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        private const val TAG = "HeartbeatVerificationService"
        private const val HEARTBEAT_INTERVAL = 60000L // 1 minute
        private const val VERIFICATION_INTERVAL = 300000L // 5 minutes
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "HeartbeatVerificationService CREATED")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        heartbeatDataManager = HeartbeatDataManager(this)
        mismatchHandler = DeviceMismatchHandler(this)
        updateManager = UpdateManager(this)
        locationManager = com.example.deviceowner.managers.LocationManager(this)
        
        // Initialize Retrofit API service with proper configuration
        val retrofit = Retrofit.Builder()
            .baseUrl("http://82.29.168.120/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(createOkHttpClient())
            .build()
        
        apiService = retrofit.create(HeartbeatApiService::class.java)
        
        // Start CommandQueueService for offline command processing
        startCommandQueueService()
        
        // Start MismatchAlertRetryService for retrying failed alerts
        startMismatchAlertRetryService()
    }
    
    /**
     * Create OkHttpClient with proper timeout and retry configuration
     */
    private fun createOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }
    
    /**
     * Start CommandQueueService for offline command processing
     */
    private fun startCommandQueueService() {
        try {
            val commandQueueIntent = Intent(this, com.example.deviceowner.services.CommandQueueService::class.java)
            startService(commandQueueIntent)
            Log.d(TAG, "✓ CommandQueueService started")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to start CommandQueueService: ${e.message}", e)
        }
    }
    
    /**
     * Start MismatchAlertRetryService for retrying failed alerts
     */
    private fun startMismatchAlertRetryService() {
        try {
            val retryServiceIntent = Intent(this, com.example.deviceowner.services.MismatchAlertRetryService::class.java)
            startService(retryServiceIntent)
            Log.d(TAG, "✓ MismatchAlertRetryService started")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to start MismatchAlertRetryService: ${e.message}", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "HeartbeatVerificationService onStartCommand() called")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        // Start heartbeat verification
        startHeartbeat()
        
        // Return START_STICKY to ensure service restarts if killed
        return START_STICKY
    }
    
    /**
     * Start periodic heartbeat verification
     */
    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) {
            Log.d(TAG, "⚠ Heartbeat already running")
            return
        }
        
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Starting heartbeat loop")
        Log.d(TAG, "Interval: ${HEARTBEAT_INTERVAL}ms (${HEARTBEAT_INTERVAL/1000}s)")
        Log.d(TAG, "Verification: ${VERIFICATION_INTERVAL}ms (${VERIFICATION_INTERVAL/1000}s)")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        heartbeatJob = serviceScope.launch {
            var verificationCounter = 0
            var registrationVerified = false
            
            try {
                // Verify registration on first iteration
                val authRepository = com.example.deviceowner.data.repository.AuthRepository(this@HeartbeatVerificationService)
                Log.d(TAG, "→ Checking stored registration...")
                val storedRegistration = authRepository.getStoredRegistration()
                
                if (storedRegistration == null) {
                    Log.e(TAG, "═══════════════════════════════════════════════════════════")
                    Log.e(TAG, "✗ HEARTBEAT BLOCKED: No stored registration found")
                    Log.e(TAG, "═══════════════════════════════════════════════════════════")
                    Log.e(TAG, "Device must be registered first via registration flow")
                    Log.e(TAG, "Heartbeat will not start until device registration is complete")
                    Log.e(TAG, "═══════════════════════════════════════════════════════════")
                    return@launch
                }
                
                if (storedRegistration.deviceId.isEmpty()) {
                    Log.e(TAG, "═══════════════════════════════════════════════════════════")
                    Log.e(TAG, "✗ HEARTBEAT BLOCKED: Device ID is empty")
                    Log.e(TAG, "═══════════════════════════════════════════════════════════")
                    Log.e(TAG, "Registration incomplete - device_id field is blank")
                    Log.e(TAG, "═══════════════════════════════════════════════════════════")
                    return@launch
                }
                
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                Log.d(TAG, "✓ Registration verified successfully")
                Log.d(TAG, "Device ID: ${storedRegistration.deviceId}")
                Log.d(TAG, "Loan ID: ${storedRegistration.loanId}")
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                registrationVerified = true
                
                while (isActive) {
                    try {
                        // Send heartbeat every minute
                        performHeartbeat()
                        
                        // Perform full verification every 5 minutes
                        verificationCounter++
                        if (verificationCounter >= 5) {
                            performFullVerification()
                            verificationCounter = 0
                        }
                        
                        delay(HEARTBEAT_INTERVAL)
                    } catch (e: CancellationException) {
                        // Expected when service is destroyed, don't log as error
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "✗ Error in heartbeat loop: ${e.message}", e)
                        delay(HEARTBEAT_INTERVAL)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Heartbeat loop cancelled")
            }
        }
    }
    
    /**
     * Perform heartbeat - send data to backend
     */
    private suspend fun performHeartbeat() {
        try {
            // Get stored registration data first
            val authRepository = com.example.deviceowner.data.repository.AuthRepository(this)
            val storedRegistration = authRepository.getStoredRegistration()
            
            if (storedRegistration == null) {
                Log.w(TAG, "⚠ Heartbeat skipped: No stored registration found")
                return
            }
            
            val deviceId = storedRegistration.deviceId
            
            if (deviceId.isEmpty()) {
                Log.w(TAG, "⚠ Heartbeat skipped: Device ID is empty")
                return
            }
            
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            Log.d(TAG, "→ SENDING HEARTBEAT")
            Log.d(TAG, "Device ID: $deviceId")
            Log.d(TAG, "Endpoint: POST /api/devices/$deviceId/data/")
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            
            // Collect heartbeat data with stored registration
            val heartbeatData = heartbeatDataManager.collectHeartbeatData(storedRegistration)
            Log.d(TAG, "Collected data:")
            Log.d(TAG, "  - Android ID: ${heartbeatData.androidId}")
            Log.d(TAG, "  - Fingerprint: ${heartbeatData.deviceFingerprint.take(16)}...")
            Log.d(TAG, "  - Manufacturer: ${heartbeatData.manufacturer}")
            Log.d(TAG, "  - Model: ${heartbeatData.model}")
            Log.d(TAG, "  - Rooted: ${heartbeatData.isDeviceRooted}")
            
            // Convert to API payload format with registration data
            val payload = convertToHeartbeatPayload(heartbeatData, storedRegistration)
            
            val response = apiService.sendHeartbeatData(deviceId, payload)
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                Log.d(TAG, "✓ HEARTBEAT SENT SUCCESSFULLY")
                Log.d(TAG, "Response: ${body?.message}")
                Log.d(TAG, "Verified: ${body?.verified}")
                Log.d(TAG, "Data Matches: ${body?.dataMatches}")
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                
                // Save heartbeat data locally
                heartbeatDataManager.saveHeartbeatData(heartbeatData)
                
                // Check if backend detected changes
                if (body?.dataMatches == false) {
                    Log.w(TAG, "⚠ Backend detected data mismatch!")
                    handleBackendMismatch(body)
                }
                
                // Process any blocking commands
                body?.command?.let { command ->
                    Log.w(TAG, "⚠ Received blocking command: ${command.type}")
                    processBlockingCommand(command, deviceId)
                }
                
                // Check for updates during heartbeat
                checkForUpdates(deviceId)
            } else {
                Log.e(TAG, "═══════════════════════════════════════════════════════════")
                Log.e(TAG, "✗ HEARTBEAT FAILED")
                Log.e(TAG, "HTTP Status: ${response.code()}")
                Log.e(TAG, "Message: ${response.message()}")
                Log.e(TAG, "Error Body: ${response.errorBody()?.string()}")
                Log.e(TAG, "═══════════════════════════════════════════════════════════")
            }
        } catch (e: Exception) {
            Log.e(TAG, "═══════════════════════════════════════════════════════════")
            Log.e(TAG, "✗ HEARTBEAT EXCEPTION")
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "═══════════════════════════════════════════════════════════")
            Log.e(TAG, "Stack trace:", e)
        }
    }
    
    /**
     * Perform full verification - check local changes and backend status
     */
    private suspend fun performFullVerification() {
        try {
            Log.d(TAG, "Performing full verification")
            
            // Detect local data changes
            val changes = heartbeatDataManager.detectDataChanges()
            
            if (changes.isNotEmpty()) {
                Log.w(TAG, "Detected ${changes.size} data changes locally")
                
                // Report each change to backend
                for (change in changes) {
                    reportDataChangeToBackend(change)
                }
                
                // Handle critical changes locally
                val criticalChanges = changes.filter { it.severity == "CRITICAL" }
                if (criticalChanges.isNotEmpty()) {
                    Log.e(TAG, "Critical data changes detected: ${criticalChanges.size}")
                    handleLocalDataChanges(criticalChanges)
                }
            }
            
            // Get verification status from backend
            val heartbeatData = heartbeatDataManager.collectHeartbeatData()
            val deviceId = heartbeatData.deviceId
            
            if (deviceId.isNotEmpty()) {
                val statusResponse = apiService.getVerificationStatus(deviceId)
                
                if (statusResponse.isSuccessful) {
                    val status = statusResponse.body()
                    Log.d(TAG, "Verification status: ${status?.dataStatus}")
                    
                    // Process pending commands
                    status?.pendingCommands?.forEach { command ->
                        Log.w(TAG, "Processing pending command: ${command.type}")
                        processBlockingCommand(command, deviceId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in full verification", e)
        }
    }
    
    /**
     * Handle data mismatch detected by backend
     */
    private suspend fun handleBackendMismatch(response: com.example.deviceowner.data.api.HeartbeatVerificationResponse) {
        try {
            Log.e(TAG, "Backend mismatch detected: ${response.message}")
            
            // Alert user
            sendMismatchNotification("Data Mismatch", response.message ?: "Data mismatch detected")
            
            // Log incident
            val auditLog = com.example.deviceowner.managers.IdentifierAuditLog(this)
            auditLog.logIncident(
                type = "BACKEND_MISMATCH",
                severity = "CRITICAL",
                details = response.message ?: "Backend detected data mismatch"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling backend mismatch", e)
        }
    }
    
    /**
     * Handle local data changes
     */
    private fun handleLocalDataChanges(changes: List<com.example.deviceowner.managers.DataChange>) {
        try {
            Log.e(TAG, "Handling ${changes.size} local data changes")
            
            // Lock device for critical changes
            val deviceOwnerManager = com.example.deviceowner.managers.DeviceOwnerManager(this)
            deviceOwnerManager.lockDevice()
            
            // Alert backend
            val auditLog = com.example.deviceowner.managers.IdentifierAuditLog(this)
            for (change in changes) {
                auditLog.logIncident(
                    type = "LOCAL_DATA_CHANGE",
                    severity = change.severity,
                    details = "Field: ${change.field}, Previous: ${change.previousValue}, Current: ${change.currentValue}"
                )
            }
            
            // Send notification
            sendMismatchNotification(
                "Critical Data Change",
                "Device data has changed. Device will be locked."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling local data changes", e)
        }
    }
    
    /**
     * Report data change to backend
     */
    private suspend fun reportDataChangeToBackend(change: com.example.deviceowner.managers.DataChange) {
        try {
            val heartbeatData = heartbeatDataManager.collectHeartbeatData()
            val deviceId = heartbeatData.deviceId
            
            if (deviceId.isEmpty()) return
            
            val changeReport = com.example.deviceowner.data.api.DataChangeReport(
                field = change.field,
                previousValue = change.previousValue,
                currentValue = change.currentValue,
                severity = change.severity,
                detectedAt = change.timestamp
            )
            
            val response = apiService.reportDataChange(deviceId, changeReport)
            
            if (response.isSuccessful) {
                Log.d(TAG, "Data change reported: ${change.field}")
                
                // Process any command from backend
                response.body()?.command?.let { command ->
                    processBlockingCommand(command, deviceId)
                }
            } else {
                Log.e(TAG, "Failed to report data change: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting data change", e)
        }
    }
    
    /**
     * Process blocking command from backend
     */
    private suspend fun processBlockingCommand(
        command: com.example.deviceowner.data.api.BlockingCommand,
        deviceId: String
    ) {
        try {
            Log.w(TAG, "Processing blocking command: ${command.type}")
            
            val deviceOwnerManager = com.example.deviceowner.managers.DeviceOwnerManager(this)
            
            when (command.type) {
                "LOCK_DEVICE" -> {
                    Log.e(TAG, "Locking device due to: ${command.reason}")
                    deviceOwnerManager.lockDevice()
                    sendMismatchNotification("Device Locked", command.reason)
                }
                
                "DISABLE_FEATURES" -> {
                    Log.w(TAG, "Disabling features: ${command.parameters}")
                    deviceOwnerManager.disableCamera(true)
                    deviceOwnerManager.disableUSB(true)
                    deviceOwnerManager.disableDeveloperOptions(true)
                    sendMismatchNotification("Features Disabled", command.reason)
                }
                
                "WIPE_DATA" -> {
                    Log.e(TAG, "Wiping sensitive data due to: ${command.reason}")
                    // TODO: Implement sensitive data wipe
                    sendMismatchNotification("Data Wiped", command.reason)
                }
                
                "ALERT_ONLY" -> {
                    Log.w(TAG, "Alert command: ${command.reason}")
                    sendMismatchNotification("Security Alert", command.reason)
                }
                
                else -> {
                    Log.w(TAG, "Unknown command type: ${command.type}")
                }
            }
            
            // Acknowledge command to backend
            acknowledgeCommand(command.commandId, "COMPLETED", deviceId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing blocking command", e)
            try {
                acknowledgeCommand(command.commandId, "FAILED", deviceId, e.message ?: "Unknown error")
            } catch (ackError: Exception) {
                Log.e(TAG, "Error acknowledging command failure", ackError)
            }
        }
    }
    
    /**
     * Acknowledge command execution to backend
     */
    private suspend fun acknowledgeCommand(
        commandId: String,
        status: String,
        deviceId: String,
        details: String = ""
    ) {
        try {
            val ackData = com.example.deviceowner.data.api.CommandAcknowledgment(
                commandId = commandId,
                status = status,
                details = details
            )
            
            val response = apiService.acknowledgeCommand(deviceId, ackData)
            
            if (response.isSuccessful) {
                Log.d(TAG, "Command acknowledged: $commandId")
            } else {
                Log.e(TAG, "Failed to acknowledge command: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acknowledging command", e)
        }
    }
    
    /**
     * Send notification about mismatch
     */
    private fun sendMismatchNotification(title: String, message: String) {
        try {
            // Create notification
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) 
                as android.app.NotificationManager
            
            val channelId = "heartbeat_verification"
            val channel = android.app.NotificationChannel(
                channelId,
                "Heartbeat Verification",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
            
            val notification = android.app.Notification.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "HeartbeatVerificationService DESTROYED")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        heartbeatJob?.cancel()
        serviceScope.cancel()
    }
    
    /**
     * Check for available updates during heartbeat
     */
    private suspend fun checkForUpdates(deviceId: String) {
        try {
            Log.d(TAG, "Checking for updates...")
            
            // Create UpdateApiService for update operations
            val retrofit = Retrofit.Builder()
                .baseUrl("http://82.29.168.120/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(createOkHttpClient())
                .build()
            
            val updateApiService = retrofit.create(com.example.deviceowner.managers.UpdateApiService::class.java)
            
            // Check for updates
            val updateCheck = updateManager.checkForUpdates(updateApiService)
            
            if (updateCheck.available) {
                Log.d(TAG, "✓ Update available: ${updateCheck.targetVersion}")
                Log.d(TAG, "  Type: ${updateCheck.updateType}")
                Log.d(TAG, "  Critical: ${updateCheck.critical}")
                
                // Download update
                if (updateCheck.targetVersion == null) {
                    Log.e(TAG, "Target version is null, skipping update")
                    return
                }
                val downloadResult = updateManager.downloadUpdate(updateApiService, updateCheck.targetVersion)
                
                if (downloadResult.success) {
                    Log.d(TAG, "✓ Update download info received")
                    Log.d(TAG, "  URL: ${downloadResult.downloadUrl}")
                    Log.d(TAG, "  Size: ${downloadResult.fileSize} bytes")
                    
                    // Queue update for installation
                    val targetVersion = updateCheck.targetVersion
                    val downloadUrl = downloadResult.downloadUrl
                    if (targetVersion != null && downloadUrl != null && targetVersion.isNotEmpty() && downloadUrl.isNotEmpty()) {
                        updateManager.queueUpdate(targetVersion, downloadUrl)
                        
                        Log.d(TAG, "✓ Update queued for installation")
                        
                        // Start UpdateInstallationService to process pending updates
                        val updateServiceIntent = Intent(this, UpdateInstallationService::class.java)
                        startService(updateServiceIntent)
                    } else {
                        Log.e(TAG, "✗ Cannot queue update: targetVersion or downloadUrl is null")
                    }
                } else {
                    Log.e(TAG, "✗ Update download failed: ${downloadResult.error}")
                }
            } else {
                Log.d(TAG, "✓ No update available: ${updateCheck.reason}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}", e)
        }
    }
    
    /**
     * Convert HeartbeatData to HeartbeatDataPayload for API submission
     * Includes Loan ID from stored registration data
     * Includes location data (latitude/longitude only) for device tracking
     * Includes tamper detection and security status
     * NOTE: IMEI and Serial Number are NOT included for security/privacy reasons
     */
    private fun convertToHeartbeatPayload(
        data: com.example.deviceowner.managers.HeartbeatData,
        registration: com.example.deviceowner.data.local.DeviceRegistrationEntity?
    ): com.example.deviceowner.data.api.HeartbeatDataPayload {
        return com.example.deviceowner.data.api.HeartbeatDataPayload(
            device_id = registration?.deviceId ?: data.deviceId,
            device_type = "phone",
            manufacturer = registration?.manufacturer ?: data.manufacturer,
            model = registration?.model ?: data.model,
            platform = "Android",
            os_version = registration?.osVersion ?: data.androidVersion,
            processor = data.hardware,  // Use hardware field from HeartbeatData
            installed_ram = registration?.installedRam ?: "Unknown",
            total_storage = registration?.totalStorage ?: "Unknown",
            machine_name = registration?.machineName ?: data.deviceFingerprint,
            build_number = (registration?.buildNumber?.toIntOrNull() ?: data.buildNumber.toIntOrNull()) ?: 0,
            sdk_version = registration?.sdkVersion?.toIntOrNull() ?: data.apiLevel,
            android_id = data.androidId,
            device_fingerprint = data.deviceFingerprint,
            bootloader = data.bootloader,
            security_patch_level = data.securityPatchLevel,
            system_uptime = data.systemUptime,
            battery_level = data.batteryLevel,
            installed_apps_hash = data.installedAppsHash,
            system_properties_hash = data.systemPropertiesHash,
            is_device_rooted = data.isDeviceRooted,
            is_usb_debugging_enabled = data.isUSBDebuggingEnabled,
            is_developer_mode_enabled = data.isDeveloperModeEnabled,
            is_bootloader_unlocked = data.isBootloaderUnlocked,
            is_custom_rom = data.isCustomROM,
            latitude = data.latitude,
            longitude = data.longitude,
            tamper_severity = data.tamperSeverity,
            tamper_flags = emptyList(),
            loan_status = "loaned",
            is_online = true,
            is_trusted = !data.isDeviceRooted && !data.isBootloaderUnlocked && !data.isCustomROM,
            is_locked = false
        )
    }
}
