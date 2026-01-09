package com.example.deviceowner.data.repository

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.api.*
import com.example.deviceowner.data.local.LocalDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                // Create new registration payload with all comparison data
                val registrationPayload = DeviceRegistrationPayload(
                    loan_number = loanId,
                    device_id = deviceId,
                    device_type = "phone",
                    manufacturer = manufacturer ?: "",
                    model = model ?: "",
                    platform = "Android",
                    os_version = osVersion ?: "",
                    processor = "",
                    installed_ram = installedRam ?: "",
                    total_storage = totalStorage ?: "",
                    build_number = buildNumber?.toIntOrNull() ?: 0,
                    sdk_version = sdkVersion ?: 0,
                    machine_name = deviceFingerprint ?: "",
                    system_type = "Mobile",
                    os_edition = "Mobile",
                    // Comparison data
                    android_id = androidId ?: "",
                    device_fingerprint = deviceFingerprint ?: "",
                    bootloader = bootloader ?: "",
                    hardware = hardware ?: "",
                    product = product ?: "",
                    device = device ?: "",
                    brand = brand ?: "",
                    security_patch_level = securityPatchLevel ?: "",
                    system_uptime = systemUptime ?: 0L,
                    battery_level = batteryLevel ?: 0,
                    installed_apps_hash = installedAppsHash ?: "",
                    system_properties_hash = systemPropertiesHash ?: "",
                    // Location
                    latitude = latitude ?: 0.0,
                    longitude = longitude ?: 0.0,
                    // Tamper status
                    is_device_rooted = isDeviceRooted,
                    is_usb_debugging_enabled = isUSBDebuggingEnabled,
                    is_developer_mode_enabled = isDeveloperModeEnabled,
                    is_bootloader_unlocked = isBootloaderUnlocked,
                    is_custom_rom = isCustomROM,
                    registration_timestamp = System.currentTimeMillis()
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
                val retrofit = retrofit2.Retrofit.Builder()
                    .baseUrl("http://82.29.168.120/")
                    .addConverterFactory(com.google.gson.Gson().let { 
                        retrofit2.converter.gson.GsonConverterFactory.create(it)
                    })
                    .build()
                
                val heartbeatApiService = retrofit.create(HeartbeatApiService::class.java)
                val response = heartbeatApiService.registerDevice(registrationPayload)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null) {
                        Log.d(TAG, "✓ Device registration successful")
                        Log.d(TAG, "Response: success=${apiResponse.success}, device_id=${apiResponse.device_id}, message=${apiResponse.message}")
                        Log.d(TAG, "Status: ${apiResponse.verification_status}")
                        
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
                        
                        // Also save to old local database for compatibility
                        val saved = localDataManager.saveDeviceRegistration(
                            deviceId = responseDeviceId,
                            loanId = loanId,
                            imeiList = imeiList ?: emptyList(),
                            serialNumber = serialNumber ?: "",
                            androidId = androidId ?: "",
                            manufacturer = manufacturer ?: "",
                            model = model ?: "",
                            osVersion = osVersion ?: "",
                            sdkVersion = sdkVersion?.toString() ?: "",
                            buildNumber = buildNumber ?: "",
                            totalStorage = totalStorage ?: "",
                            installedRam = installedRam ?: "",
                            machineName = deviceFingerprint ?: "",
                            networkOperatorName = "",
                            simOperatorName = "",
                            simState = "",
                            phoneType = "",
                            networkType = "",
                            simSerialNumber = simSerialNumber ?: "",
                            batteryCapacity = batteryCapacity ?: "",
                            batteryTechnology = batteryTechnology ?: "",
                            registrationToken = null
                        )
                        if (saved) {
                            Log.d(TAG, "✓ Registration data saved to legacy database")
                        }
                        
                        // Return success with converted response
                        ApiResult.Success(DeviceRegistrationResponse(
                            success = true,
                            message = apiResponse.message,
                            device_id = responseDeviceId,
                            registered_at = System.currentTimeMillis(),
                            verification_status = apiResponse.verification_status ?: "PENDING",
                            stored_data_hash = apiResponse.stored_data_hash
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
