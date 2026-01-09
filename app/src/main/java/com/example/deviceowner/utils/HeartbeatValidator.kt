package com.example.deviceowner.utils

import android.content.Context
import android.util.Log
import com.example.deviceowner.managers.HeartbeatDataManager
import com.example.deviceowner.data.api.HeartbeatApiService
import com.example.deviceowner.data.api.HeartbeatDataPayload
import com.example.deviceowner.data.repository.AuthRepository
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Utility to validate heartbeat data collection and sending
 * Tests that heartbeat works correctly without IMEI/Serial Number
 */
class HeartbeatValidator(private val context: Context) {
    
    companion object {
        private const val TAG = "HeartbeatValidator"
    }
    
    private val heartbeatDataManager = HeartbeatDataManager(context)
    private val authRepository = AuthRepository(context)
    
    /**
     * Validate heartbeat data collection
     * Returns validation report
     * Must be called from a coroutine
     */
    suspend fun validateHeartbeatData(): HeartbeatValidationReport {
        val report = HeartbeatValidationReport()
        
        return withContext(Dispatchers.IO) {
            try {
                // Collect heartbeat data
                val heartbeatData = heartbeatDataManager.collectHeartbeatData()
                
                // Check critical fields
                report.checks.add(ValidationCheck(
                    name = "Device ID",
                    passed = heartbeatData.deviceId.isNotEmpty(),
                    value = heartbeatData.deviceId,
                    required = true
                ))
                
                report.checks.add(ValidationCheck(
                    name = "Android ID",
                    passed = heartbeatData.androidId.isNotEmpty(),
                    value = heartbeatData.androidId,
                    required = true
                ))
                
                report.checks.add(ValidationCheck(
                    name = "Device Fingerprint",
                    passed = heartbeatData.deviceFingerprint.isNotEmpty(),
                    value = heartbeatData.deviceFingerprint,
                    required = true
                ))
                
                report.checks.add(ValidationCheck(
                    name = "Manufacturer",
                    passed = heartbeatData.manufacturer.isNotEmpty(),
                    value = heartbeatData.manufacturer,
                    required = true
                ))
                
                report.checks.add(ValidationCheck(
                    name = "Model",
                    passed = heartbeatData.model.isNotEmpty(),
                    value = heartbeatData.model,
                    required = true
                ))
                
                // Check optional fields (IMEI and Serial should be empty for Android 9+)
                report.checks.add(ValidationCheck(
                    name = "IMEI (should be empty for Android 9+)",
                    passed = heartbeatData.imei.isEmpty(),
                    value = if (heartbeatData.imei.isEmpty()) "Not collected (correct)" else heartbeatData.imei,
                    required = false
                ))
                
                report.checks.add(ValidationCheck(
                    name = "Serial Number (should be empty for Android 9+)",
                    passed = heartbeatData.serialNumber.isEmpty(),
                    value = if (heartbeatData.serialNumber.isEmpty()) "Not collected (correct)" else heartbeatData.serialNumber,
                    required = false
                ))
                
                // Check security flags
                report.checks.add(ValidationCheck(
                    name = "Root Detection",
                    passed = true,
                    value = "Rooted: ${heartbeatData.isDeviceRooted}",
                    required = false
                ))
                
                report.checks.add(ValidationCheck(
                    name = "USB Debugging Detection",
                    passed = true,
                    value = "Enabled: ${heartbeatData.isUSBDebuggingEnabled}",
                    required = false
                ))
                
                report.checks.add(ValidationCheck(
                    name = "Developer Mode Detection",
                    passed = true,
                    value = "Enabled: ${heartbeatData.isDeveloperModeEnabled}",
                    required = false
                ))
                
                // Check hashes
                report.checks.add(ValidationCheck(
                    name = "Installed Apps Hash",
                    passed = heartbeatData.installedAppsHash.isNotEmpty(),
                    value = heartbeatData.installedAppsHash.take(16) + "...",
                    required = true
                ))
                
                report.checks.add(ValidationCheck(
                    name = "System Properties Hash",
                    passed = heartbeatData.systemPropertiesHash.isNotEmpty(),
                    value = heartbeatData.systemPropertiesHash.take(16) + "...",
                    required = true
                ))
                
                report.allChecksPassed = report.checks.all { it.passed || !it.required }
                report.status = if (report.allChecksPassed) "VALID" else "INVALID"
                
            } catch (e: Exception) {
                Log.e(TAG, "Error validating heartbeat data", e)
                report.status = "ERROR"
                report.error = e.message ?: "Unknown error"
            }
            
            report
        }
    }
    
    /**
     * Validate heartbeat payload format
     */
    suspend fun validateHeartbeatPayload(): HeartbeatPayloadValidationReport {
        val report = HeartbeatPayloadValidationReport()
        
        try {
            val storedRegistration = authRepository.getStoredRegistration()
            
            if (storedRegistration == null) {
                report.status = "ERROR"
                report.error = "No stored registration found"
                return report
            }
            
            val heartbeatData = heartbeatDataManager.collectHeartbeatData(storedRegistration)
            
            // Convert to payload
            val payload = convertToHeartbeatPayload(heartbeatData, storedRegistration)
            
            // Validate payload fields
            report.checks.add(PayloadFieldCheck(
                field = "device_id",
                value = payload.device_id,
                isEmpty = payload.device_id.isEmpty(),
                required = true
            ))
            
            report.checks.add(PayloadFieldCheck(
                field = "manufacturer",
                value = payload.manufacturer,
                isEmpty = payload.manufacturer.isEmpty(),
                required = true
            ))
            
            report.checks.add(PayloadFieldCheck(
                field = "model",
                value = payload.model,
                isEmpty = payload.model.isEmpty(),
                required = true
            ))
            
            report.checks.add(PayloadFieldCheck(
                field = "os_version",
                value = payload.os_version,
                isEmpty = payload.os_version.isEmpty(),
                required = true
            ))
            
            report.checks.add(PayloadFieldCheck(
                field = "android_id",
                value = payload.android_id,
                isEmpty = payload.android_id.isEmpty(),
                required = true
            ))
            
            report.checks.add(PayloadFieldCheck(
                field = "device_fingerprint",
                value = payload.device_fingerprint,
                isEmpty = payload.device_fingerprint.isEmpty(),
                required = true
            ))
            
            report.allFieldsValid = report.checks.all { !it.isEmpty || !it.required }
            report.status = if (report.allFieldsValid) "VALID" else "INVALID"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating payload", e)
            report.status = "ERROR"
            report.error = e.message ?: "Unknown error"
        }
        
        return report
    }
    
    /**
     * Test heartbeat sending (dry run)
     */
    suspend fun testHeartbeatSending(): HeartbeatSendingTestResult {
        val result = HeartbeatSendingTestResult()
        
        return withContext(Dispatchers.IO) {
            try {
                val storedRegistration = authRepository.getStoredRegistration()
                
                if (storedRegistration == null) {
                    result.status = "FAILED"
                    result.error = "No stored registration found"
                    return@withContext result
                }
                
                val deviceId = storedRegistration.deviceId
                if (deviceId.isEmpty()) {
                    result.status = "FAILED"
                    result.error = "Device ID is empty"
                    return@withContext result
                }
                
                // Collect data
                val heartbeatData = heartbeatDataManager.collectHeartbeatData(storedRegistration)
                val payload = convertToHeartbeatPayload(heartbeatData, storedRegistration)
                
                // Create API service
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://82.29.168.120/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(createOkHttpClient())
                    .build()
                
                val apiService = retrofit.create(HeartbeatApiService::class.java)
                
                // Send heartbeat
                Log.d(TAG, "Sending test heartbeat for device: $deviceId")
                val response = apiService.sendHeartbeatData(deviceId, payload)
                
                result.statusCode = response.code()
                result.isSuccessful = response.isSuccessful
                
                if (response.isSuccessful) {
                    val body = response.body()
                    result.status = "SUCCESS"
                    result.message = body?.message ?: "Heartbeat sent successfully"
                    result.dataMatches = body?.dataMatches ?: false
                    result.verified = body?.verified ?: false
                } else {
                    result.status = "FAILED"
                    result.error = "HTTP ${response.code()}: ${response.message()}"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error testing heartbeat sending", e)
                result.status = "ERROR"
                result.error = e.message ?: "Unknown error"
            }
            
            result
        }
    }
    
    /**
     * Get heartbeat history
     */
    fun getHeartbeatHistory(): List<HeartbeatHistoryEntry> {
        return try {
            heartbeatDataManager.getHeartbeatHistory().map { data ->
                HeartbeatHistoryEntry(
                    timestamp = data.timestamp,
                    deviceId = data.deviceId,
                    androidId = data.androidId,
                    manufacturer = data.manufacturer,
                    model = data.model,
                    isRooted = data.isDeviceRooted,
                    usbDebugging = data.isUSBDebuggingEnabled,
                    developerMode = data.isDeveloperModeEnabled
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting history", e)
            emptyList()
        }
    }
    
    /**
     * Create OkHttpClient with logging
     */
    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }
    
    /**
     * Convert HeartbeatData to HeartbeatDataPayload
     * Includes tamper detection and security status
     * Location data includes latitude/longitude only
     * NOTE: IMEI and Serial Number are NOT included for security/privacy reasons
     */
    private fun convertToHeartbeatPayload(
        data: com.example.deviceowner.managers.HeartbeatData,
        registration: com.example.deviceowner.data.local.DeviceRegistrationEntity?
    ): HeartbeatDataPayload {
        return HeartbeatDataPayload(
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

// Data classes for validation reports

data class HeartbeatValidationReport(
    var status: String = "PENDING",
    var allChecksPassed: Boolean = false,
    var error: String? = null,
    val checks: MutableList<ValidationCheck> = mutableListOf()
)

data class ValidationCheck(
    val name: String,
    val passed: Boolean,
    val value: String,
    val required: Boolean
)

data class HeartbeatPayloadValidationReport(
    var status: String = "PENDING",
    var allFieldsValid: Boolean = false,
    var error: String? = null,
    val checks: MutableList<PayloadFieldCheck> = mutableListOf()
)

data class PayloadFieldCheck(
    val field: String,
    val value: String,
    val isEmpty: Boolean,
    val required: Boolean
)

data class HeartbeatSendingTestResult(
    var status: String = "PENDING",
    var statusCode: Int = 0,
    var isSuccessful: Boolean = false,
    var message: String? = null,
    var dataMatches: Boolean = false,
    var verified: Boolean = false,
    var error: String? = null
)

data class HeartbeatHistoryEntry(
    val timestamp: Long,
    val deviceId: String,
    val androidId: String,
    val manufacturer: String,
    val model: String,
    val isRooted: Boolean,
    val usbDebugging: Boolean,
    val developerMode: Boolean
)
