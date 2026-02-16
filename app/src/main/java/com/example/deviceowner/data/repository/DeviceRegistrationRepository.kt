package com.example.deviceowner.data.repository

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.device.CompleteDeviceRegistrationEntity
import com.example.deviceowner.data.models.registration.DeviceRegistrationRequest
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.data.remote.ApiService
import com.example.deviceowner.utils.logging.LogManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.example.deviceowner.AppConfig
import com.example.deviceowner.data.remote.api.ApiHeadersInterceptor


class DeviceRegistrationRepository(private val context: Context) {
    
    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val dao = database.completeDeviceRegistrationDao()
    private val gson = GsonBuilder().setLenient().create()
    private val registrationBackup = com.example.deviceowner.data.local.RegistrationDataBackup(context)
    
    // Public API client for logging errors
    val apiClient = ApiClient()
    
    private val apiService: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (AppConfig.ENABLE_LOGGING) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(ApiHeadersInterceptor())
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        retrofit.create(ApiService::class.java)
    }
    
    companion object {
        private const val TAG = "DeviceRegistrationRepo"
    }
    
    /**
     * Save loan number for future use (called when user first enters it)
     */
    suspend fun saveLoanNumberForRegistration(loanNumber: String, deviceId: String? = null) = withContext(Dispatchers.IO) {
        try {
            // Create a minimal registration entry with just the loan number
            // Use Android ID as temporary identifier if deviceId is null
            val tempDeviceId: String = deviceId ?: android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            val entity = CompleteDeviceRegistrationEntity(
                deviceId = tempDeviceId,
                loanNumber = loanNumber,
                manufacturer = "Unknown", // Will be updated during full registration
                model = "Unknown",
                serialNumber = null,
                androidId = null,
                deviceImeis = null,
                osVersion = null,
                sdkVersion = null,
                buildNumber = null,
                securityPatchLevel = null,
                bootloader = null,
                installedRam = null,
                totalStorage = null,
                language = null,
                deviceFingerprint = null,
                systemUptime = null,
                installedAppsHash = null,
                systemPropertiesHash = null,
                isDeviceRooted = null,
                isUsbDebuggingEnabled = null,
                isDeveloperModeEnabled = null,
                isBootloaderUnlocked = null,
                isCustomRom = null,
                tamperSeverity = null,
                tamperFlags = null,
                latitude = null,
                longitude = null,
                registrationStatus = "LOAN_NUMBER_SAVED",
                registeredAt = System.currentTimeMillis()
            )
            
            dao.insertRegistration(entity)
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Loan number saved for future registration: $loanNumber")
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error saving loan number: ${e.message}", throwable = e)
        }
    }
    
    /**
     * Save complete device registration data to local database
     * Also backs up data for recovery after app reinstallation
     */
    suspend fun saveRegistrationData(
        deviceRegistrationRequest: DeviceRegistrationRequest,
        status: String = "PENDING"
    ) = withContext(Dispatchers.IO) {
        try {
            // Use Android ID as fallback if server deviceId is null
            val finalDeviceId: String = deviceRegistrationRequest.deviceId ?: android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            // Check if we already have a loan number saved for this device
            val existingRegistration = dao.getRegistrationByDeviceId(finalDeviceId)
            
            // Extract values from nested maps
            val deviceInfo = deviceRegistrationRequest.deviceInfo ?: emptyMap()
            val androidInfo = deviceRegistrationRequest.androidInfo ?: emptyMap()
            val imeiInfo = deviceRegistrationRequest.imeiInfo ?: emptyMap()
            val storageInfo = deviceRegistrationRequest.storageInfo ?: emptyMap()
            val locationInfo = deviceRegistrationRequest.locationInfo ?: emptyMap()
            val securityInfo = deviceRegistrationRequest.securityInfo ?: emptyMap()
            val systemIntegrity = deviceRegistrationRequest.systemIntegrity ?: emptyMap()
            val appInfo = deviceRegistrationRequest.appInfo ?: emptyMap()
            
            // Parse version_sdk_int: Gson may return Int or Double from JSON number
            val sdkVersionValue = androidInfo["version_sdk_int"]
            val sdkVersion = when (sdkVersionValue) {
                is Number -> sdkVersionValue.toInt()
                is String -> sdkVersionValue.toIntOrNull()
                else -> null
            }
            val entity = CompleteDeviceRegistrationEntity(
                deviceId = finalDeviceId,
                loanNumber = deviceRegistrationRequest.loanNumber ?: "UNKNOWN",
                manufacturer = (deviceInfo["manufacturer"] as? String) ?: "UNKNOWN",
                model = (deviceInfo["model"] as? String) ?: "UNKNOWN",
                serialNumber = deviceInfo["serial"] as? String,
                androidId = deviceInfo["android_id"] as? String,
                deviceImeis = (imeiInfo["device_imeis"] as? List<*>)?.joinToString(","),
                osVersion = androidInfo["version_release"] as? String,
                sdkVersion = sdkVersion,
                buildNumber = (androidInfo["version_incremental"] as? String)?.toIntOrNull(),
                securityPatchLevel = androidInfo["security_patch"] as? String,
                bootloader = deviceInfo["bootloader"] as? String,
                installedRam = storageInfo["installed_ram"] as? String,
                totalStorage = storageInfo["total_storage"] as? String,
                language = null, // Not available in DeviceRegistrationRequest
                deviceFingerprint = deviceInfo["fingerprint"] as? String,
                systemUptime = null, // Not in new model
                installedAppsHash = systemIntegrity["installed_apps_hash"] as? String,
                systemPropertiesHash = systemIntegrity["system_properties_hash"] as? String,
                
                // Security data (stored but hidden from user)
                isDeviceRooted = securityInfo["is_device_rooted"] as? Boolean,
                isUsbDebuggingEnabled = securityInfo["is_usb_debugging_enabled"] as? Boolean,
                isDeveloperModeEnabled = securityInfo["is_developer_mode_enabled"] as? Boolean,
                isBootloaderUnlocked = securityInfo["is_bootloader_unlocked"] as? Boolean,
                isCustomRom = securityInfo["is_custom_rom"] as? Boolean,
                tamperSeverity = null, // Not available in DeviceRegistrationRequest
                tamperFlags = null, // Not available in DeviceRegistrationRequest
                
                // Location data
                latitude = (locationInfo["latitude"] as? Number)?.toDouble(),
                longitude = (locationInfo["longitude"] as? Number)?.toDouble(),
                
                // Registration metadata - preserve original registration time if updating
                registrationStatus = status,
                registeredAt = existingRegistration?.registeredAt ?: System.currentTimeMillis(),
                lastSyncAt = null,
                serverResponse = null
            )
            
            dao.insertRegistration(entity)
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Complete registration data saved to local database (status: $status, deviceId: $finalDeviceId)")
            
            // Backup data if registration is successful
            if (status == "SUCCESS") {
                val backupSuccess = registrationBackup.backupRegistrationData()
                 LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Registration data backup: ${if (backupSuccess) "SUCCESS" else "FAILED"}")
            }
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error saving registration data: ${e.message}", throwable = e)
            throw e
        }
    }
    
    /**
     * Update registration status after server response (by device_id).
     */
    suspend fun updateRegistrationStatus(
        deviceId: String,
        status: String,
        serverResponse: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            dao.updateRegistrationStatus(
                deviceId = deviceId,
                status = status,
                response = serverResponse,
                syncTime = System.currentTimeMillis()
            )
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Registration status updated to: $status")
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error updating registration status: ${e.message}", throwable = e)
        }
    }

    /**
     * Update registration after successful API register: set server-assigned device_id and SUCCESS by loan number.
     * (Local row was inserted with Android ID; this updates it so DB and heartbeat stay in sync.)
     */
    suspend fun updateRegistrationSuccessByLoan(
        loanNumber: String,
        serverDeviceId: String,
        status: String = "SUCCESS",
        serverResponse: String? = null,
        syncTime: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        try {
            dao.updateRegistrationSuccessByLoan(loanNumber, serverDeviceId, status, serverResponse, syncTime)
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Registration success by loan: deviceId=$serverDeviceId")
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error updating registration by loan: ${e.message}", throwable = e)
        }
    }
    
    /**
     * Get stored loan number from successful registration
     */
    suspend fun getStoredLoanNumber(): String? = withContext(Dispatchers.IO) {
        try {
            // First try to get from successful registration
            val successfulRegistration = dao.getSuccessfulRegistration()
            if (successfulRegistration != null) {
                return@withContext successfulRegistration.loanNumber
            }
            
            // If no successful registration, get from any registration (including saved loan numbers)
            val anyRegistration = dao.getAnyRegistration()
            return@withContext anyRegistration?.loanNumber
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error getting stored loan number: ${e.message}", throwable = e)
            null
        }
    }

    /**
     * Get loan number from most recent in-progress registration (for resuming after app close).
     * Returns null if device is fully registered or no in-progress registration exists.
     */
    suspend fun getInProgressLoanNumber(): String? = withContext(Dispatchers.IO) {
        try {
            if (dao.hasSuccessfulRegistration() > 0) return@withContext null
            dao.getMostRecentInProgressRegistration()?.loanNumber
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error getting in-progress loan number: ${e.message}", throwable = e)
            null
        }
    }
    
    /**
     * Check if device is already registered successfully
     */
    suspend fun isDeviceRegistered(): Boolean = withContext(Dispatchers.IO) {
        try {
            val count = dao.hasSuccessfulRegistration()
            count > 0
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error checking registration status: ${e.message}", throwable = e)
            false
        }
    }
    
    /**
     * Save device registration (simplified version for RegistrationResponseHandler)
     */
    suspend fun saveDeviceRegistration(
        deviceId: String,
        model: String,
        manufacturer: String,
        deviceType: String,
        serialNumber: String,
        loanNumber: String,
        registeredAt: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val entity = CompleteDeviceRegistrationEntity(
                deviceId = deviceId,
                loanNumber = loanNumber,
                manufacturer = manufacturer,
                model = model,
                serialNumber = serialNumber,
                androidId = null,
                deviceImeis = null,
                osVersion = null,
                sdkVersion = null,
                buildNumber = null,
                securityPatchLevel = null,
                bootloader = null,
                installedRam = null,
                totalStorage = null,
                language = null,
                deviceFingerprint = null,
                systemUptime = null,
                installedAppsHash = null,
                systemPropertiesHash = null,
                isDeviceRooted = null,
                isUsbDebuggingEnabled = null,
                isDeveloperModeEnabled = null,
                isBootloaderUnlocked = null,
                isCustomRom = null,
                tamperSeverity = null,
                tamperFlags = null,
                latitude = null,
                longitude = null,
                registrationStatus = "SUCCESS",
                registeredAt = registeredAt
            )
            
            dao.insertRegistration(entity)
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Device registration saved: $deviceId")
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error saving device registration: ${e.message}", throwable = e)
        }
    }
    

    
    /**
     * Get complete registration data (for admin/debug purposes only)
     */
    suspend fun getCompleteRegistrationData(): CompleteDeviceRegistrationEntity? = withContext(Dispatchers.IO) {
        try {
            dao.getSuccessfulRegistration()
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error getting complete registration data: ${e.message}", throwable = e)
            null
        }
    }
    
    /**
     * Clear all registration data (for testing/reset purposes)
     */
    suspend fun clearAllRegistrations() = withContext(Dispatchers.IO) {
        try {
            dao.clearAllRegistrations()
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "All registration data cleared")
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error clearing registration data: ${e.message}", throwable = e)
        }
    }
    
    /**
     * Backup registration data for recovery after app reinstallation
     */
    suspend fun backupRegistrationData(): Boolean {
        return registrationBackup.backupRegistrationData()
    }
    
    /**
     * Restore registration data from backup
     * Called during app startup if no local registration exists
     */
    suspend fun restoreRegistrationDataFromBackup(): Boolean {
        return registrationBackup.restoreRegistrationData()
    }
    
    /**
     * Check if backup exists
     */
    suspend fun hasRegistrationBackup(): Boolean {
        return registrationBackup.hasBackup()
    }
    
    /**
     * Get backup metadata
     */
    suspend fun getBackupMetadata(): Map<String, Any?>? {
        return registrationBackup.getBackupInfo()
    }
    
    /**
     * Clear backup data
     */
    suspend fun clearRegistrationBackup(): Boolean {
        return registrationBackup.clearBackup()
    }
    
    /**
     * Get all registration data for local server API
     */
    suspend fun getAllRegistrationData(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val registrations = dao.getAllRegistrations()
            registrations.map { entity ->
                mapOf(
                    "device_id" to entity.deviceId,
                    "loan_number" to entity.loanNumber,
                    "manufacturer" to entity.manufacturer,
                    "model" to entity.model,
                    "serial_number" to entity.serialNumber,
                    "android_id" to entity.androidId,
                    "device_imeis" to entity.deviceImeis,
                    "os_version" to entity.osVersion,
                    "sdk_version" to entity.sdkVersion,
                    "build_number" to entity.buildNumber,
                    "security_patch_level" to entity.securityPatchLevel,
                    "bootloader" to entity.bootloader,
                    "installed_ram" to entity.installedRam,
                    "total_storage" to entity.totalStorage,
                    "device_fingerprint" to entity.deviceFingerprint,
                    "system_uptime" to entity.systemUptime,
                    "installed_apps_hash" to entity.installedAppsHash,
                    "system_properties_hash" to entity.systemPropertiesHash,
                    "is_device_rooted" to entity.isDeviceRooted,
                    "is_usb_debugging_enabled" to entity.isUsbDebuggingEnabled,
                    "is_developer_mode_enabled" to entity.isDeveloperModeEnabled,
                    "is_bootloader_unlocked" to entity.isBootloaderUnlocked,
                    "is_custom_rom" to entity.isCustomRom,
                    "latitude" to entity.latitude,
                    "longitude" to entity.longitude,
                    "registration_status" to entity.registrationStatus,
                    "registered_at" to entity.registeredAt,
                    "last_sync_at" to entity.lastSyncAt
                )
            }
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error getting all registration data: ${e.message}", throwable = e)
            emptyList()
        }
    }

    suspend fun registerDevice(request: DeviceRegistrationRequest) = withContext(Dispatchers.IO) {
        try {
            apiService.registerDevice(request)
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "API Error: ${e.message}", throwable = e)
            throw e
        }
    }
}
