package com.example.deviceowner.data.repository

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.api.ApiClient
import com.example.deviceowner.data.api.ApiResult
import com.example.deviceowner.data.api.DeviceRegistrationResponse
import com.example.deviceowner.data.api.DeviceRegistrationPayload
import com.example.deviceowner.data.api.HeartbeatApiService
import com.example.deviceowner.data.local.LocalDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.converter.gson.GsonConverterFactory

class AuthRepository(private val context: Context) {
    
    private val apiService = ApiClient.apiService
    private val localDataManager = LocalDataManager(context)
    private val TAG = "AuthRepository"
    
    /**
     * Register device with comprehensive device information
     * POST http://82.29.168.120/api/devices/register/
     * 
     * Sends all available device data for better verification and tracking
     * Includes all comparison data used in heartbeat verification
     */
    suspend fun registerDevice(
        loanId: String,
        deviceId: String,
        imeiList: List<String>? = null,
        serialNumber: String? = null,
        deviceFingerprint: String? = null,
        // Extended device information
        androidId: String? = null,
        manufacturer: String? = null,
        model: String? = null,
        osVersion: String? = null,
        sdkVersion: Int? = null,
        buildNumber: String? = null,
        totalStorage: String? = null,
        installedRam: String? = null,
        totalStorageBytes: Long? = null,
        totalRamBytes: Long? = null,
        simSerialNumber: String? = null,
        batteryCapacity: String? = null,
        batteryTechnology: String? = null,
        // Comparison data
        bootloader: String? = null,
        hardware: String? = null,
        product: String? = null,
        device: String? = null,
        brand: String? = null,
        securityPatchLevel: String? = null,
        systemUptime: Long? = null,
        batteryLevel: Int? = null,
        installedAppsHash: String? = null,
        systemPropertiesHash: String? = null,
        // Tamper status
        isDeviceRooted: Boolean = false,
        isUSBDebuggingEnabled: Boolean = false,
        isDeveloperModeEnabled: Boolean = false,
        isBootloaderUnlocked: Boolean = false,
        isCustomROM: Boolean = false,
        // Location
        latitude: Double? = null,
        longitude: Double? = null
    ): ApiResult<DeviceRegistrationResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // Create registration payload with ALL device data for server baseline
                // Ensure all required fields have valid values (not empty strings)
                val registrationPayload = DeviceRegistrationPayload(
                    device_id = deviceId.ifBlank { "UNKNOWN" },
                    serial_number = (serialNumber ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    device_type = "phone",
                    manufacturer = (manufacturer ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    system_type = "Android",
                    model = (model ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    platform = "Android",
                    os_version = (osVersion ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    os_edition = "Mobile",
                    processor = (hardware ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    installed_ram = (installedRam ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    total_storage = (totalStorage ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    build_number = buildNumber?.toIntOrNull() ?: 0,
                    sdk_version = sdkVersion ?: 0,
                    device_imeis = imeiList?.filter { it.isNotBlank() }?.joinToString(",") ?: "",
                    loan_number = loanId.ifBlank { "UNKNOWN" },
                    machine_name = (device ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    android_id = (androidId ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    device_fingerprint = (deviceFingerprint ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    bootloader = (bootloader ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    security_patch_level = (securityPatchLevel ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    system_uptime = android.os.SystemClock.elapsedRealtime(),
                    installed_apps_hash = (installedAppsHash ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    system_properties_hash = (systemPropertiesHash ?: "UNKNOWN").ifBlank { "UNKNOWN" },
                    is_device_rooted = isDeviceRooted,
                    is_usb_debugging_enabled = isUSBDebuggingEnabled,
                    is_developer_mode_enabled = isDeveloperModeEnabled,
                    is_bootloader_unlocked = isBootloaderUnlocked,
                    is_custom_rom = isCustomROM,
                    latitude = latitude ?: 0.0,
                    longitude = longitude ?: 0.0,
                    tamper_severity = "NONE",
                    tamper_flags = "",
                    battery_level = batteryLevel ?: 0
                )
                
                Log.d(TAG, "=== REGISTER DEVICE ===")
                Log.d(TAG, "Endpoint: POST http://82.29.168.120/api/devices/register/")
                Log.d(TAG, "Loan ID: $loanId")
                Log.d(TAG, "Device ID: $deviceId")
                Log.d(TAG, "IMEIs: $imeiList")
                Log.d(TAG, "Serial: $serialNumber")
                Log.d(TAG, "Manufacturer: $manufacturer")
                Log.d(TAG, "Model: $model")
                Log.d(TAG, "OS Version: $osVersion")
                Log.d(TAG, "SDK Version: $sdkVersion")
                Log.d(TAG, "Android ID: $androidId")
                Log.d(TAG, "Bootloader: $bootloader")
                Log.d(TAG, "Hardware: $hardware")
                Log.d(TAG, "Product: $product")
                Log.d(TAG, "Device: $device")
                Log.d(TAG, "Brand: $brand")
                Log.d(TAG, "Root Status: $isDeviceRooted")
                Log.d(TAG, "Bootloader Unlocked: $isBootloaderUnlocked")
                Log.d(TAG, "Custom ROM: $isCustomROM")
                
                // Send to backend using HeartbeatApiService
                val gson = com.google.gson.Gson()
                val retrofit = retrofit2.Retrofit.Builder()
                    .baseUrl("http://82.29.168.120/")
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                
                val heartbeatApiService = retrofit.create(HeartbeatApiService::class.java)
                val response = heartbeatApiService.registerDevice(registrationPayload)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null) {
                        Log.d(TAG, "✓ Device registration successful")
                        Log.d(TAG, "Response: success=${apiResponse.success}, device_id=${apiResponse.device_id}, message=${apiResponse.message}")
                        
                        // Use device_id from response, or fallback to the one we sent
                        val responseDeviceId = apiResponse.device_id ?: deviceId
                        Log.d(TAG, "Using device_id: $responseDeviceId (from response: ${apiResponse.device_id}, sent: $deviceId)")
                        
                        if (responseDeviceId.isBlank()) {
                            Log.e(TAG, "✗ Device ID is empty")
                            return@withContext ApiResult.Error("Device ID is empty", response.code())
                        }
                        
                        // Save registration data locally using RegistrationDataManager
                        val registrationDataManager = com.example.deviceowner.managers.RegistrationDataManager(context)
                        registrationDataManager.saveRegistrationData(registrationPayload)
                        Log.d(TAG, "✓ Registration data saved to local database")
                        
                        // Return success with converted response
                        ApiResult.Success(DeviceRegistrationResponse(
                            success = true,
                            message = apiResponse.message,
                            device_id = responseDeviceId,
                            timestamp = System.currentTimeMillis()
                        ))
                    } else {
                        Log.e(TAG, "✗ Response body is null")
                        ApiResult.Error("Empty response from server", response.code())
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "✗ HTTP Error: ${response.code()} - $errorBody")
                    ApiResult.Error(errorBody, response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Exception: ${e.message}", e)
                ApiResult.Error(e.message ?: "Network error", null)
            }
        }
    }

    /**
     * Get the latest stored device registration from local database
     */
    suspend fun getStoredRegistration() = withContext(Dispatchers.IO) {
        localDataManager?.getLatestRegistration()
    }
    
    /**
     * Check if device is already registered locally
     */
    suspend fun isDeviceRegisteredLocally(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        localDataManager?.isDeviceRegistered(deviceId) ?: false
    }
    
    /**
     * Get all stored registrations
     */
    suspend fun getAllStoredRegistrations() = withContext(Dispatchers.IO) {
        localDataManager?.getAllRegistrations() ?: emptyList()
    }
}
