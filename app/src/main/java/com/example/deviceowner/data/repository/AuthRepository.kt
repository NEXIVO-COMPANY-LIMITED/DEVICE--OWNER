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
        batteryTechnology: String? = null
    ): ApiResult<DeviceRegistrationResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = DeviceRegistrationRequest(
                    device_id = deviceId,
                    serial_number = serialNumber ?: "",
                    device_type = "phone",
                    manufacturer = manufacturer ?: "",
                    system_type = "Mobile",
                    model = model ?: "",
                    platform = "Android",
                    os_version = osVersion ?: "",
                    os_edition = "Mobile",
                    processor = "",
                    installed_ram = installedRam ?: "",
                    total_storage = totalStorage ?: "",
                    build_number = buildNumber?.toIntOrNull() ?: 0,
                    sdk_version = sdkVersion ?: 0,
                    device_imeis = imeiList ?: emptyList(),
                    loan_number = loanId
                )
                
                // Validate request before sending
                val validationResult = request.validate()
                if (validationResult is ValidationResult.Error) {
                    Log.e(TAG, "✗ Validation failed: ${validationResult.message}")
                    return@withContext ApiResult.Error(validationResult.message)
                }
                
                Log.d(TAG, "=== REGISTER DEVICE ===")
                Log.d(TAG, "Endpoint: POST http://82.29.168.120/api/devices/register/")
                Log.d(TAG, "Loan Number: $loanId")
                Log.d(TAG, "Device ID: $deviceId")
                Log.d(TAG, "IMEIs: $imeiList")
                Log.d(TAG, "Serial: $serialNumber")
                Log.d(TAG, "Manufacturer: $manufacturer")
                Log.d(TAG, "Model: $model")
                Log.d(TAG, "OS Version: $osVersion")
                Log.d(TAG, "SDK Version: $sdkVersion")
                Log.d(TAG, "Total Storage: $totalStorage")
                Log.d(TAG, "Installed RAM: $installedRam")
                
                val response = apiService.registerDevice(request)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null) {
                        if (apiResponse.device != null && apiResponse.device.device_id != null) {
                            val registeredDeviceId = apiResponse.device.device_id ?: deviceId
                            Log.d(TAG, "✓ Device registration successful")
                            Log.d(TAG, "Device ID: $registeredDeviceId")
                            Log.d(TAG, "Status: ${apiResponse.status}")
                            Log.d(TAG, "Message: ${apiResponse.message}")
                            
                            // Save registration data locally
                            val saved = localDataManager.saveDeviceRegistration(
                                deviceId = registeredDeviceId,
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
                                registrationToken = apiResponse.registration_token?.toString()
                            )
                            if (saved) {
                                Log.d(TAG, "✓ Registration data saved to local database")
                            } else {
                                Log.w(TAG, "⚠ Failed to save registration data locally")
                            }
                            
                            ApiResult.Success(apiResponse)
                        } else {
                            val errorMsg = apiResponse.error ?: apiResponse.message ?: "Registration failed"
                            Log.e(TAG, "✗ Device registration failed: $errorMsg")
                            ApiResult.Error(errorMsg, response.code())
                        }
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
