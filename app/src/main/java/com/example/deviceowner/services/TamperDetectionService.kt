package com.example.deviceowner.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.HeartbeatDataManager
import com.example.deviceowner.managers.TamperDetector
import com.example.deviceowner.data.api.HeartbeatApiService
import com.example.deviceowner.data.api.HeartbeatDataPayload
import com.example.deviceowner.data.local.AppDatabase
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Service for continuous heartbeat and tamper detection
 * 
 * Flow:
 * 1. Collect device data
 * 2. Send to backend
 * 3. Receive verified data in response
 * 4. Compare sent vs verified data
 * 5. If mismatch detected → Block device immediately
 * 6. No need to fetch commands - detection triggers block
 */
class TamperDetectionService : Service() {
    
    private lateinit var heartbeatDataManager: HeartbeatDataManager
    private lateinit var tamperDetector: TamperDetector
    private lateinit var apiService: HeartbeatApiService
    private lateinit var database: AppDatabase
    private var heartbeatJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        private const val TAG = "TamperDetectionService"
        private const val HEARTBEAT_INTERVAL = 5000L // 5 seconds - REAL-TIME detection
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TamperDetectionService created")
        
        heartbeatDataManager = HeartbeatDataManager(this)
        tamperDetector = TamperDetector(this)
        database = AppDatabase.getInstance(this)
        
        // Initialize Retrofit API service
        val retrofit = Retrofit.Builder()
            .baseUrl(com.example.deviceowner.config.ApiConfig.getBaseUrl(this))
            .addConverterFactory(GsonConverterFactory.create())
            .client(createOkHttpClient())
            .build()
        
        apiService = retrofit.create(HeartbeatApiService::class.java)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TamperDetectionService started")
        startHeartbeatCycle()
        return START_STICKY
    }
    
    /**
     * Start continuous heartbeat cycle
     */
    private fun startHeartbeatCycle() {
        if (heartbeatJob?.isActive == true) {
            Log.d(TAG, "Heartbeat cycle already running")
            return
        }
        
        heartbeatJob = serviceScope.launch {
            Log.d(TAG, "Heartbeat cycle started")
            
            try {
                while (isActive) {
                    try {
                        performHeartbeat()
                        delay(HEARTBEAT_INTERVAL)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in heartbeat cycle", e)
                        delay(HEARTBEAT_INTERVAL)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Heartbeat cycle cancelled")
            }
        }
    }
    
    /**
     * Perform single heartbeat cycle
     * 
     * Online mode:
     * 1. Collect current device data
     * 2. Send to backend
     * 3. Receive verified data
     * 4. Compare and detect tampering
     * 5. Block if tampering detected
     * 
     * Offline mode:
     * 1. Collect current device data
     * 2. Compare with local stored data
     * 3. Detect tampering locally
     * 4. Block if tampering detected
     */
    private suspend fun performHeartbeat() {
        try {
            Log.d(TAG, "Performing heartbeat...")
            
            // Get stored registration
            val registration = database.deviceRegistrationDao().getLatestRegistration()
            if (registration == null) {
                Log.w(TAG, "No registration found, skipping heartbeat")
                return
            }
            
            // Check if device is online
            val isOnline = isDeviceOnline()
            Log.d(TAG, "Device online status: $isOnline")
            
            // Collect current device data
            val currentData = heartbeatDataManager.collectHeartbeatData(registration, isOnline)
            Log.d(TAG, "Collected heartbeat data: ${currentData.deviceId}")
            
            if (isOnline) {
                // Online mode: Send to backend
                performOnlineHeartbeat(registration, currentData)
            } else {
                // Offline mode: Compare with local data
                performOfflineHeartbeat(currentData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing heartbeat", e)
        }
    }
    
    /**
     * Perform heartbeat in online mode
     * Send to backend and compare response
     */
    private suspend fun performOnlineHeartbeat(
        registration: com.example.deviceowner.data.local.DeviceRegistrationEntity,
        currentData: com.example.deviceowner.managers.HeartbeatData
    ) {
        try {
            // Create payload for backend
            val payload = createHeartbeatPayload(currentData)
            
            // Send to backend
            Log.d(TAG, "Sending heartbeat to backend...")
            val response = apiService.sendHeartbeatData(registration.device_id, payload)
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Heartbeat failed: ${response.code()} - ${response.message()}")
                return
            }
            
            val heartbeatResponse = response.body()
            if (heartbeatResponse == null) {
                Log.e(TAG, "Empty heartbeat response")
                return
            }
            
            Log.d(TAG, "Heartbeat response received: success=${heartbeatResponse.success}")
            
            // Compare sent data with verified data from backend
            val tamperResult = heartbeatDataManager.compareWithBackendResponse(currentData, heartbeatResponse)
            
            if (tamperResult.isTampered) {
                Log.w(TAG, "TAMPERING DETECTED (ONLINE)! Severity: ${tamperResult.severity}")
                Log.w(TAG, "Mismatches: ${tamperResult.mismatches.size}")
                
                for (mismatch in tamperResult.mismatches) {
                    Log.w(TAG, "  - ${mismatch.field}: sent=${mismatch.sentValue}, verified=${mismatch.verifiedValue}")
                }
                
                // Block device immediately
                blockDeviceImmediately(tamperResult)
            } else {
                Log.d(TAG, "✓ Heartbeat verified (ONLINE) - no tampering detected")
                
                // Save verified data for offline comparison
                heartbeatDataManager.saveVerifiedData(currentData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in online heartbeat", e)
        }
    }
    
    /**
     * Perform heartbeat in offline mode
     * Compare with local stored data
     */
    private suspend fun performOfflineHeartbeat(currentData: com.example.deviceowner.managers.HeartbeatData) {
        try {
            Log.d(TAG, "Performing offline heartbeat...")
            
            // Compare with local stored data
            val tamperResult = heartbeatDataManager.compareWithLocalData(currentData)
            
            if (tamperResult.isTampered) {
                Log.w(TAG, "TAMPERING DETECTED (OFFLINE)! Severity: ${tamperResult.severity}")
                Log.w(TAG, "Mismatches: ${tamperResult.mismatches.size}")
                
                for (mismatch in tamperResult.mismatches) {
                    Log.w(TAG, "  - ${mismatch.field}: current=${mismatch.sentValue}, stored=${mismatch.verifiedValue}")
                }
                
                // Block device immediately
                blockDeviceImmediately(tamperResult)
            } else {
                Log.d(TAG, "✓ Offline heartbeat verified - no tampering detected")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in offline heartbeat", e)
        }
    }
    
    /**
     * Check if device is online
     */
    private fun isDeviceOnline(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            network != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking online status: ${e.message}")
            false
        }
    }
    
    /**
     * Create heartbeat payload from collected data
     */
    private fun createHeartbeatPayload(data: com.example.deviceowner.managers.HeartbeatData): HeartbeatDataPayload {
        return HeartbeatDataPayload(
            device_id = data.deviceId,
            manufacturer = data.manufacturer,
            model = data.model,
            os_version = data.osVersion,
            sdk_version = data.sdkVersion,
            build_number = data.buildNumber,
            security_patch_level = data.securityPatchLevel,
            processor = data.processor,
            android_id = data.androidId,
            device_fingerprint = data.deviceFingerprint,
            bootloader = data.bootloader,
            installed_apps_hash = data.installedAppsHash,
            system_properties_hash = data.systemPropertiesHash,
            is_device_rooted = data.isDeviceRooted,
            is_usb_debugging_enabled = data.isUSBDebuggingEnabled,
            is_developer_mode_enabled = data.isDeveloperModeEnabled,
            is_bootloader_unlocked = data.isBootloaderUnlocked,
            is_custom_rom = data.isCustomROM,
            tamper_severity = data.tamperSeverity,
            tamper_flags = data.tamperFlags,
            is_trusted = data.isTrusted,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Block device immediately when tampering detected
     * No waiting for commands - immediate action
     * Sends lock notification to backend API
     */
    private suspend fun blockDeviceImmediately(tamperResult: com.example.deviceowner.managers.TamperDetectionResult) {
        try {
            Log.e(TAG, "BLOCKING DEVICE - Tampering detected!")
            
            // Get device ID from registration
            val registration = database.deviceRegistrationDao().getLatestRegistration()
            val deviceId = registration?.device_id ?: return
            
            // Send lock notification to backend
            sendLockNotificationToBackend(deviceId, tamperResult)
            
            // Trigger device lock overlay
            val lockIntent = Intent(this, com.example.deviceowner.overlay.OverlayController::class.java)
            lockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            lockIntent.putExtra("lock_type", "HARD")
            lockIntent.putExtra("reason", "Device tampering detected")
            lockIntent.putExtra("severity", tamperResult.severity)
            lockIntent.putExtra("mismatches", tamperResult.mismatches.size)
            
            startActivity(lockIntent)
            
            // Log tampering incident
            Log.e(TAG, "Device locked due to tampering")
            Log.e(TAG, "Severity: ${tamperResult.severity}")
            Log.e(TAG, "Mismatches detected: ${tamperResult.mismatches.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking device", e)
        }
    }
    
    /**
     * Send lock notification to backend API
     * POST /api/devices/{device_id}/manage/
     */
    private suspend fun sendLockNotificationToBackend(
        deviceId: String,
        tamperResult: com.example.deviceowner.managers.TamperDetectionResult
    ) {
        return withContext(Dispatchers.IO) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(com.example.deviceowner.config.ApiConfig.getBaseUrl(this@TamperDetectionService))
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(createOkHttpClient())
                    .build()
                
                val apiService = retrofit.create(com.example.deviceowner.data.api.HeartbeatApiService::class.java)
                
                val lockCommand = com.example.deviceowner.data.api.DeviceManagementCommand(
                    action = "lock",
                    reason = "Tampering detected - Severity: ${tamperResult.severity}, Mismatches: ${tamperResult.mismatches.size}",
                    timestamp = System.currentTimeMillis()
                )
                
                val response = apiService.sendDeviceManagementCommand(deviceId, lockCommand)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "✓ Lock notification sent to backend successfully")
                    Log.d(TAG, "Response: ${response.body()?.message}")
                } else {
                    Log.e(TAG, "✗ Failed to send lock notification: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending lock notification to backend", e)
            }
        }
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
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TamperDetectionService destroyed")
        heartbeatJob?.cancel()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
