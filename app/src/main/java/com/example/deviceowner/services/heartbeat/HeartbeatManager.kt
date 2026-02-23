package com.example.deviceowner.services.heartbeat

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.deviceowner.data.DeviceIdProvider
import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest
import com.example.deviceowner.data.models.heartbeat.HeartbeatResponse
import com.example.deviceowner.data.models.registration.DeviceRegistrationRequest
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.services.data.DeviceDataCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * HeartbeatManager - PERFECT IMPLEMENTATION v3.0
 * 
 * ✅ IMPROVEMENTS:
 * - Precise data extraction from DeviceRegistrationRequest
 * - Consistent heartbeat numbering for better reporting
 * - Enhanced device ID validation
 * - Detailed success and failure logging to API
 * - Proper exception handling and network error detection
 */
class HeartbeatManager(private val context: Context) {
    private val TAG = "HeartbeatManager"
    private val dataCollector = DeviceDataCollector(context)
    private val apiClient = ApiClient()
    private val eventLogger = HeartbeatEventLogger(context)
    
    // Track heartbeat sequence for better reporting
    private var currentHeartbeatNumber = 0

    suspend fun sendHeartbeat(): HeartbeatResponse? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        currentHeartbeatNumber++
        val heartbeatNumber = currentHeartbeatNumber
        var deviceId: String? = null
        
        try {
            // STEP 1: Validate and get device ID
            deviceId = validateAndGetDeviceId()
            if (deviceId == null) {
                val responseTime = System.currentTimeMillis() - startTime
                eventLogger.logFailure(
                    heartbeatNumber = heartbeatNumber,
                    responseTime = responseTime,
                    reason = "Device ID is NULL or BLANK - Registration required",
                    errorType = "MISSING_DEVICE_ID"
                )
                return@withContext null
            }
            
            // STEP 2: Collect fresh device data
            val registrationData = try {
                dataCollector.collectDeviceData()
            } catch (e: Exception) {
                val responseTime = System.currentTimeMillis() - startTime
                eventLogger.logException(
                    heartbeatNumber = heartbeatNumber,
                    responseTime = responseTime,
                    exception = e
                )
                return@withContext null
            }
            
            // STEP 3: Build precise heartbeat request
            val request = try {
                buildHeartbeatRequest(registrationData)
            } catch (e: Exception) {
                val responseTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "❌ Failed to build request: ${e.message}")
                eventLogger.logException(
                    heartbeatNumber = heartbeatNumber,
                    responseTime = responseTime,
                    exception = e
                )
                return@withContext null
            }
            
            // STEP 4: Send heartbeat to API
            Log.d(TAG, "📤 Sending Heartbeat #$heartbeatNumber for device: $deviceId")
            val response = apiClient.sendHeartbeat(deviceId, request)
            val responseTime = System.currentTimeMillis() - startTime
            
            // STEP 5: Process response
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val isLocked = body.isDeviceLocked()
                    eventLogger.logSuccess(
                        heartbeatNumber = heartbeatNumber,
                        responseTime = responseTime,
                        message = "Heartbeat #$heartbeatNumber successful",
                        isLocked = isLocked
                    )
                    Log.d(TAG, "✅ Heartbeat #$heartbeatNumber successful: $deviceId (${responseTime}ms)")
                    return@withContext body
                } else {
                    eventLogger.logFailure(
                        heartbeatNumber = heartbeatNumber,
                        responseTime = responseTime,
                        reason = "Server returned empty body (200 OK but null content)",
                        errorType = "EMPTY_RESPONSE_BODY"
                    )
                    return@withContext null
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message() ?: "Unknown error"
                eventLogger.logServerError(
                    heartbeatNumber = heartbeatNumber,
                    responseTime = responseTime,
                    httpCode = response.code(),
                    errorMessage = errorMsg
                )
                return@withContext null
            }
            
        } catch (e: java.net.UnknownHostException) {
            val responseTime = System.currentTimeMillis() - startTime
            eventLogger.logNetworkError(
                heartbeatNumber = heartbeatNumber,
                responseTime = responseTime,
                errorType = "NO_INTERNET",
                errorMessage = "Device offline: UnknownHostException"
            )
            return@withContext null
        } catch (e: java.net.ConnectException) {
            val responseTime = System.currentTimeMillis() - startTime
            eventLogger.logNetworkError(
                heartbeatNumber = heartbeatNumber,
                responseTime = responseTime,
                errorType = "CONNECTION_FAILED",
                errorMessage = "Failed to connect to server: ${e.message}"
            )
            return@withContext null
        } catch (e: java.net.SocketTimeoutException) {
            val responseTime = System.currentTimeMillis() - startTime
            eventLogger.logNetworkError(
                heartbeatNumber = heartbeatNumber,
                responseTime = responseTime,
                errorType = "TIMEOUT",
                errorMessage = "Request timed out after ${responseTime}ms"
            )
            return@withContext null
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Unexpected Error in Heartbeat: ${e.message}")
            eventLogger.logException(
                heartbeatNumber = heartbeatNumber,
                responseTime = responseTime,
                exception = e
            )
            return@withContext null
        }
    }
    
    /**
     * Precise extraction of device ID with validation
     */
    private fun validateAndGetDeviceId(): String? {
        val deviceId = DeviceIdProvider.getDeviceId(context)
        return when {
            deviceId.isNullOrBlank() -> {
                Log.e(TAG, "❌ Device ID is missing from storage")
                null
            }
            deviceId.length < 10 -> {
                Log.w(TAG, "⚠️ Device ID '$deviceId' looks invalid (too short)")
                deviceId // Still returning it, let server decide
            }
            else -> deviceId
        }
    }
    
    /**
     * Build precise HeartbeatRequest mapping from DeviceRegistrationRequest
     */
    private fun buildHeartbeatRequest(data: DeviceRegistrationRequest): HeartbeatRequest {
        val deviceInfo = data.deviceInfo ?: emptyMap()
        val androidInfo = data.androidInfo ?: emptyMap()
        val imeiInfo = data.imeiInfo ?: emptyMap()
        val storageInfo = data.storageInfo ?: emptyMap()
        val locationInfo = data.locationInfo ?: emptyMap()
        val securityInfo = data.securityInfo ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        val imeiList = (imeiInfo["device_imeis"] as? List<*>)?.map { it.toString() } ?: emptyList<String>()
        
        return HeartbeatRequest(
            deviceImeis = imeiList,
            serialNumber = deviceInfo["serial"]?.toString() ?: deviceInfo["serial_number"]?.toString() ?: Build.SERIAL ?: "unknown",
            installedRam = storageInfo["installed_ram"]?.toString() ?: "unknown",
            totalStorage = storageInfo["total_storage"]?.toString() ?: "unknown",
            isDeviceRooted = securityInfo["is_device_rooted"] as? Boolean ?: false,
            isUsbDebuggingEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1,
            isDeveloperModeEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
            isBootloaderUnlocked = isBootloaderUnlocked(),
            isCustomRom = isCustomRom(),
            androidId = deviceInfo["android_id"]?.toString() ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown",
            model = Build.MODEL ?: deviceInfo["model"]?.toString() ?: "unknown",
            manufacturer = Build.MANUFACTURER ?: deviceInfo["manufacturer"]?.toString() ?: "unknown",
            deviceFingerprint = Build.FINGERPRINT ?: androidInfo["device_fingerprint"]?.toString() ?: "unknown",
            bootloader = Build.BOOTLOADER ?: deviceInfo["bootloader"]?.toString() ?: "unknown",
            osVersion = Build.VERSION.RELEASE ?: androidInfo["version_release"]?.toString() ?: "unknown",
            osEdition = Build.VERSION.INCREMENTAL ?: androidInfo["version_incremental"]?.toString() ?: "unknown",
            sdkVersion = Build.VERSION.SDK_INT,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH ?: androidInfo["security_patch"]?.toString() ?: "unknown",
            systemUptime = android.os.SystemClock.elapsedRealtime(),
            installedAppsHash = com.example.deviceowner.utils.helpers.HashGenerator.generateInstalledAppsHash(context),
            systemPropertiesHash = com.example.deviceowner.utils.helpers.HashGenerator.generateSystemPropertiesHash(),
            latitude = (locationInfo["latitude"] as? Number)?.toDouble(),
            longitude = (locationInfo["longitude"] as? Number)?.toDouble(),
            batteryLevel = getBatteryLevel(),
            language = Locale.getDefault().language
        )
    }

    private fun isBootloaderUnlocked(): Boolean {
        return try {
            Build.TAGS?.contains("test-keys") == true
        } catch (e: Exception) { false }
    }

    private fun isCustomRom(): Boolean {
        return try {
            isBootloaderUnlocked() || 
            Build.DISPLAY.contains("lineage", ignoreCase = true) ||
            Build.FINGERPRINT.contains("custom", ignoreCase = true)
        } catch (e: Exception) { false }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        } catch (e: Exception) { 0 }
    }
}
