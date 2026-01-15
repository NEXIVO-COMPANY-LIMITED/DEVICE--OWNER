package com.example.deviceowner.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.data.api.HeartbeatApiService
import com.example.deviceowner.data.models.HeartbeatVerificationRequest
import com.example.deviceowner.data.local.AppDatabase
import com.example.deviceowner.data.local.UnifiedHeartbeatStorage
import com.example.deviceowner.data.models.UnifiedHeartbeatData
import com.example.deviceowner.managers.TamperDetector
import com.example.deviceowner.managers.UnifiedHeartbeatComparison
import com.example.deviceowner.managers.RemoteLockManager
import com.example.deviceowner.managers.IdentifierAuditLog
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Unified Heartbeat Service
 * Handles BOTH online and offline heartbeat verification
 * Uses identical data format and comparison logic for both modes
 * 
 * Flow:
 * ONLINE MODE:
 * 1. Collect current heartbeat data
 * 2. Send to server
 * 3. Receive verified data from server
 * 4. Compare using UnifiedHeartbeatComparison
 * 5. Save verified data locally
 * 6. Lock device if tampering detected
 * 
 * OFFLINE MODE:
 * 1. Collect current heartbeat data
 * 2. Compare with locally-stored baseline/verified data
 * 3. Lock device if tampering detected
 * 4. Queue sync for when online
 */
class UnifiedHeartbeatService : Service() {
    
    private lateinit var storage: UnifiedHeartbeatStorage
    private lateinit var comparison: UnifiedHeartbeatComparison
    private lateinit var tamperDetector: TamperDetector
    private lateinit var apiService: HeartbeatApiService
    private lateinit var database: AppDatabase
    private lateinit var remoteLockManager: RemoteLockManager
    private lateinit var auditLog: IdentifierAuditLog
    
    private var heartbeatJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        private const val TAG = "UnifiedHeartbeatService"
        private const val HEARTBEAT_INTERVAL = 60000L // 1 minute
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "UnifiedHeartbeatService created")
        
        storage = UnifiedHeartbeatStorage(this)
        comparison = UnifiedHeartbeatComparison()
        tamperDetector = TamperDetector(this)
        database = AppDatabase.getInstance(this)
        remoteLockManager = RemoteLockManager(this)
        auditLog = IdentifierAuditLog(this)
        
        // Initialize Retrofit API service
        val retrofit = Retrofit.Builder()
            .baseUrl("http://82.29.168.120/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(HeartbeatApiService::class.java)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "UnifiedHeartbeatService started")
        startHeartbeatCycle()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "UnifiedHeartbeatService destroyed")
        heartbeatJob?.cancel()
        serviceScope.cancel()
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
     * Automatically detects online/offline mode
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
            
            // Collect current heartbeat data
            val currentData = collectHeartbeatData(registration)
            Log.d(TAG, "Collected heartbeat data: ${currentData.deviceId}")
            
            // Save current data
            storage.saveCurrentHeartbeat(currentData)
            
            // Check if device is online
            val isOnline = isDeviceOnline()
            Log.d(TAG, "Device online status: $isOnline")
            
            if (isOnline) {
                performOnlineHeartbeat(registration, currentData)
            } else {
                performOfflineHeartbeat(registration, currentData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing heartbeat", e)
        }
    }
    
    /**
     * Perform heartbeat in ONLINE mode
     * Send to server and compare response
     */
    private suspend fun performOnlineHeartbeat(
        registration: com.example.deviceowner.data.local.DeviceRegistrationEntity,
        currentData: UnifiedHeartbeatData
    ) {
        try {
            Log.d(TAG, "Performing ONLINE heartbeat...")
            
            // Create verification request
            val request = HeartbeatVerificationRequest(
                deviceId = registration.device_id,
                heartbeatData = currentData
            )
            
            // Send to server
            Log.d(TAG, "Sending heartbeat to server...")
            val response = apiService.sendHeartbeatData(
                registration.device_id,
                com.example.deviceowner.data.api.HeartbeatDataPayload(
                    device_id = registration.device_id,
                    manufacturer = currentData.manufacturer,
                    model = currentData.model,
                    os_version = currentData.osVersion,
                    sdk_version = currentData.sdkVersion,
                    build_number = currentData.buildNumber,
                    security_patch_level = currentData.securityPatchLevel,
                    processor = currentData.processor,
                    android_id = currentData.androidId,
                    device_fingerprint = currentData.deviceFingerprint,
                    bootloader = currentData.bootloader,
                    installed_apps_hash = currentData.installedAppsHash,
                    system_properties_hash = currentData.systemPropertiesHash,
                    is_device_rooted = currentData.isDeviceRooted,
                    is_usb_debugging_enabled = currentData.isUsbDebuggingEnabled,
                    is_developer_mode_enabled = currentData.isDeveloperModeEnabled,
                    is_bootloader_unlocked = currentData.isBootloaderUnlocked,
                    is_custom_rom = currentData.isCustomRom,
                    tamper_severity = currentData.tamperSeverity,
                    tamper_flags = currentData.tamperFlags,
                    is_trusted = currentData.isTrusted,
                    timestamp = currentData.timestamp
                )
            )
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Heartbeat failed: ${response.code()} - ${response.message()}")
                storage.updateSyncStatus("FAILED")
                return
            }
            
            val heartbeatResponse = response.body()
            if (heartbeatResponse == null) {
                Log.e(TAG, "Empty heartbeat response")
                storage.updateSyncStatus("FAILED")
                return
            }
            
            Log.d(TAG, "Heartbeat response received: success=${heartbeatResponse.success}")
            
            // Convert response to UnifiedHeartbeatData
            val verifiedData = heartbeatResponse.verified_data
            
            // Compare using unified comparison engine
            val comparisonResult = comparison.compareHeartbeats(
                currentData = currentData,
                referenceData = verifiedData,
                mode = "ONLINE"
            )
            
            if (comparisonResult.isTampered) {
                Log.w(TAG, "TAMPERING DETECTED (ONLINE)! Severity: ${comparisonResult.severity}")
                Log.w(TAG, "Mismatches: ${comparisonResult.mismatches.size}")
                
                for (mismatch in comparisonResult.mismatches) {
                    Log.w(TAG, "  - ${mismatch.field}: expected=${mismatch.expectedValue}, actual=${mismatch.actualValue}")
                }
                
                // Log to audit trail
                auditLog.logAction(
                    "TAMPERING_DETECTED_ONLINE",
                    "Severity: ${comparisonResult.severity}, Mismatches: ${comparisonResult.mismatches.size}"
                )
                
                // Lock device immediately
                val action = comparison.getActionForResult(comparisonResult)
                if (action == "HARD_LOCK") {
                    // Send lock notification to backend
                    sendLockNotificationToBackend(
                        registration.device_id,
                        comparisonResult.severity,
                        comparisonResult.mismatches.size
                    )
                    
                    val lock = com.example.deviceowner.models.DeviceLock(
                        lockId = "tamper_lock_${System.currentTimeMillis()}",
                        deviceId = registration.device_id,
                        lockType = com.example.deviceowner.models.LockType.HARD,
                        lockStatus = com.example.deviceowner.models.LockStatus.ACTIVE,
                        lockReason = com.example.deviceowner.models.LockReason.SYSTEM_TAMPER,
                        message = "Tampering detected during online heartbeat verification. Severity: ${comparisonResult.severity}",
                        backendUnlockOnly = true
                    )
                    remoteLockManager.applyLock(lock)
                }
            } else {
                Log.d(TAG, "✓ Heartbeat verified (ONLINE) - no tampering detected")
                
                // Save verified data for offline comparison
                storage.saveVerifiedHeartbeat(verifiedData)
                storage.updateSyncStatus("SYNCED")
                
                // Log to audit trail
                auditLog.logAction(
                    "HEARTBEAT_VERIFIED_ONLINE",
                    "Device verified successfully"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in online heartbeat", e)
            storage.updateSyncStatus("FAILED")
        }
    }
    
    /**
     * Perform heartbeat in OFFLINE mode
     * Compare with locally-stored baseline/verified data
     */
    private suspend fun performOfflineHeartbeat(
        registration: com.example.deviceowner.data.local.DeviceRegistrationEntity,
        currentData: UnifiedHeartbeatData
    ) {
        try {
            Log.d(TAG, "Performing OFFLINE heartbeat...")
            
            // Get reference data (verified or baseline)
            val referenceData = storage.getVerifiedHeartbeat() ?: storage.getBaselineHeartbeat()
            
            if (referenceData == null) {
                Log.w(TAG, "No reference data available for offline comparison")
                return
            }
            
            // Compare using unified comparison engine
            val comparisonResult = comparison.compareHeartbeats(
                currentData = currentData,
                referenceData = referenceData,
                mode = "OFFLINE"
            )
            
            if (comparisonResult.isTampered) {
                Log.w(TAG, "TAMPERING DETECTED (OFFLINE)! Severity: ${comparisonResult.severity}")
                Log.w(TAG, "Mismatches: ${comparisonResult.mismatches.size}")
                
                for (mismatch in comparisonResult.mismatches) {
                    Log.w(TAG, "  - ${mismatch.field}: expected=${mismatch.expectedValue}, actual=${mismatch.actualValue}")
                }
                
                // Log to audit trail
                auditLog.logAction(
                    "TAMPERING_DETECTED_OFFLINE",
                    "Severity: ${comparisonResult.severity}, Mismatches: ${comparisonResult.mismatches.size}"
                )
                
                // Lock device immediately
                val action = comparison.getActionForResult(comparisonResult)
                if (action == "HARD_LOCK") {
                    // Send lock notification to backend
                    sendLockNotificationToBackend(
                        registration.device_id,
                        comparisonResult.severity,
                        comparisonResult.mismatches.size
                    )
                    
                    val lock = com.example.deviceowner.models.DeviceLock(
                        lockId = "tamper_lock_${System.currentTimeMillis()}",
                        deviceId = registration.device_id,
                        lockType = com.example.deviceowner.models.LockType.HARD,
                        lockStatus = com.example.deviceowner.models.LockStatus.ACTIVE,
                        lockReason = com.example.deviceowner.models.LockReason.SYSTEM_TAMPER,
                        message = "Tampering detected during offline heartbeat verification. Severity: ${comparisonResult.severity}",
                        backendUnlockOnly = true
                    )
                    remoteLockManager.applyLock(lock)
                }
            } else {
                Log.d(TAG, "✓ Offline heartbeat verified - no tampering detected")
                
                // Log to audit trail
                auditLog.logAction(
                    "HEARTBEAT_VERIFIED_OFFLINE",
                    "Device verified successfully (offline)"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in offline heartbeat", e)
        }
    }
    
    /**
     * Collect current heartbeat data from device
     */
    private suspend fun collectHeartbeatData(
        registration: com.example.deviceowner.data.local.DeviceRegistrationEntity
    ): UnifiedHeartbeatData {
        
        val isOnline = isDeviceOnline()
        
        return UnifiedHeartbeatData(
            deviceId = registration.device_id,
            serialNumber = registration.serial_number,
            androidId = registration.android_id,
            deviceFingerprint = registration.device_fingerprint,
            manufacturer = registration.manufacturer,
            model = registration.model,
            bootloader = registration.bootloader,
            processor = registration.processor,
            deviceImeis = registration.device_imeis.split(",").filter { it.isNotEmpty() },
            osVersion = registration.os_version,
            sdkVersion = registration.sdk_version,
            buildNumber = registration.build_number,
            securityPatchLevel = registration.security_patch_level,
            installedRam = registration.installed_ram,
            totalStorage = registration.total_storage,
            machineName = registration.machine_name,
            isDeviceRooted = tamperDetector.isRooted(),
            isBootloaderUnlocked = tamperDetector.isBootloaderUnlocked(),
            isCustomRom = tamperDetector.isCustomROM(),
            isUsbDebuggingEnabled = tamperDetector.isUSBDebuggingEnabled(),
            isDeveloperModeEnabled = tamperDetector.isDeveloperModeEnabled(),
            installedAppsHash = getInstalledAppsHash(),
            systemPropertiesHash = getSystemPropertiesHash(),
            tamperSeverity = tamperDetector.getTamperStatus().severity.name,
            tamperFlags = tamperDetector.getTamperStatus().tamperFlags,
            timestamp = System.currentTimeMillis(),
            loanNumber = registration.loan_number,
            batteryLevel = getBatteryLevel(),
            systemUptime = android.os.SystemClock.elapsedRealtime(),
            latitude = 0.0,
            longitude = 0.0,
            loanStatus = registration.loan_number,
            isOnline = isOnline,
            isTrusted = !tamperDetector.isTampered(),
            isLocked = false,
            syncStatus = storage.getSyncStatus()
        )
    }
    
    /**
     * Get installed apps hash
     */
    private fun getInstalledAppsHash(): String {
        return try {
            val pm = packageManager
            val packages = pm.getInstalledPackages(0)
            val packageNames = packages.map { it.packageName }.sorted()
            val combined = packageNames.joinToString(",")
            
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps hash", e)
            ""
        }
    }
    
    /**
     * Get system properties hash
     */
    private fun getSystemPropertiesHash(): String {
        return try {
            val properties = mutableListOf<String>()
            properties.add(android.os.Build.FINGERPRINT)
            properties.add(android.os.Build.DEVICE)
            properties.add(android.os.Build.PRODUCT)
            properties.add(android.os.Build.BRAND)
            properties.add(android.os.Build.HARDWARE)
            
            val combined = properties.sorted().joinToString(",")
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system properties hash", e)
            ""
        }
    }
    
    /**
     * Get battery level
     */
    private fun getBatteryLevel(): Int {
        return try {
            val ifilter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: 100
            (level * 100 / scale)
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Check if device is online
     */
    private fun isDeviceOnline(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            network != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking online status", e)
            false
        }
    }
    
    /**
     * Send lock notification to backend API
     * POST /api/devices/{device_id}/manage/
     */
    private suspend fun sendLockNotificationToBackend(
        deviceId: String,
        severity: String,
        mismatchCount: Int
    ) {
        return withContext(Dispatchers.IO) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://82.29.168.120/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(createOkHttpClient())
                    .build()
                
                val apiService = retrofit.create(HeartbeatApiService::class.java)
                
                val lockCommand = com.example.deviceowner.data.api.DeviceManagementCommand(
                    action = "lock",
                    reason = "Tampering detected - Severity: $severity, Mismatches: $mismatchCount",
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
}
