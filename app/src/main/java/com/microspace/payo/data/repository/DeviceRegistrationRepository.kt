package com.microspace.payo.data.repository

import android.content.Context
import android.util.Log
import com.microspace.payo.data.local.database.DeviceOwnerDatabase
import com.microspace.payo.data.local.database.entities.device.CompleteDeviceRegistrationEntity
import com.microspace.payo.data.models.registration.DeviceRegistrationRequest
import com.microspace.payo.data.remote.ApiClient
import com.microspace.payo.data.remote.ApiService
import com.microspace.payo.utils.logging.LogManager
import com.microspace.payo.services.reporting.ServerBugAndLogReporter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.microspace.payo.AppConfig
import com.microspace.payo.data.remote.api.ApiHeadersInterceptor


class DeviceRegistrationRepository(private val context: Context) {
    
    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val dao = database.completeDeviceRegistrationDao()
    private val gson = GsonBuilder().setLenient().create()
    private val registrationBackup = com.microspace.payo.data.local.RegistrationDataBackup(context)
    
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
            val tempDeviceId: String = deviceId ?: android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            val entity = CompleteDeviceRegistrationEntity(
                deviceId = tempDeviceId,
                loanNumber = loanNumber,
                manufacturer = "Unknown",
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
            ServerBugAndLogReporter.postException(e, "Registration: Failed to save loan number locally.")
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
            val finalDeviceId: String = deviceRegistrationRequest.deviceId ?: android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            val existingRegistration = dao.getRegistrationByDeviceId(finalDeviceId)
            
            val deviceInfo = deviceRegistrationRequest.deviceInfo ?: emptyMap()
            val androidInfo = deviceRegistrationRequest.androidInfo ?: emptyMap()
            val imeiInfo = deviceRegistrationRequest.imeiInfo ?: emptyMap()
            val storageInfo = deviceRegistrationRequest.storageInfo ?: emptyMap()
            val locationInfo = deviceRegistrationRequest.locationInfo ?: emptyMap()
            val securityInfo = deviceRegistrationRequest.securityInfo ?: emptyMap()
            val systemIntegrity = deviceRegistrationRequest.systemIntegrity ?: emptyMap()
            
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
                language = androidInfo["language"] as? String,
                deviceFingerprint = deviceInfo["fingerprint"] as? String,
                systemUptime = (systemIntegrity["system_uptime"] as? Number)?.toLong(),
                installedAppsHash = systemIntegrity["installed_apps_hash"] as? String,
                systemPropertiesHash = systemIntegrity["system_properties_hash"] as? String,
                isDeviceRooted = securityInfo["is_device_rooted"] as? Boolean,
                isUsbDebuggingEnabled = securityInfo["is_usb_debugging_enabled"] as? Boolean,
                isDeveloperModeEnabled = securityInfo["is_developer_mode_enabled"] as? Boolean,
                isBootloaderUnlocked = securityInfo["is_bootloader_unlocked"] as? Boolean,
                isCustomRom = securityInfo["is_custom_rom"] as? Boolean,
                tamperSeverity = securityInfo["tamper_severity"] as? String,
                tamperFlags = securityInfo["tamper_flags"] as? String,
                latitude = (locationInfo["latitude"] as? Number)?.toDouble(),
                longitude = (locationInfo["longitude"] as? Number)?.toDouble(),
                registrationStatus = status,
                registeredAt = existingRegistration?.registeredAt ?: System.currentTimeMillis()
            )
            
            dao.insertRegistration(entity)
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Complete registration data saved: $finalDeviceId (Status: $status)")
            
            if (status == "SUCCESS") {
                backupRegistrationData()
            }
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error saving registration data: ${e.message}", throwable = e)
            ServerBugAndLogReporter.postException(e, "Registration: Failed to save registration data.")
            throw e
        }
    }
    
    suspend fun updateRegistrationStatus(
        deviceId: String,
        status: String,
        serverResponse: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            dao.updateRegistrationStatus(deviceId, status, serverResponse, System.currentTimeMillis())
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Status updated to: $status")
            if (status == "SUCCESS") {
                backupRegistrationData()
            }
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error updating status: ${e.message}", throwable = e)
        }
    }

    suspend fun updateRegistrationSuccessByLoan(
        loanNumber: String,
        serverDeviceId: String,
        status: String = "SUCCESS",
        serverResponse: String? = null,
        syncTime: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        try {
            dao.updateRegistrationSuccessByLoan(loanNumber, serverDeviceId, status, serverResponse, syncTime)
            LogManager.logInfo(LogManager.LogCategory.DEVICE_REGISTRATION, "Registration success by loan: $serverDeviceId")
            if (status == "SUCCESS") {
                backupRegistrationData()
            }
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error updating success: ${e.message}", throwable = e)
        }
    }
    
    suspend fun getStoredLoanNumber(): String? = withContext(Dispatchers.IO) {
        try {
            dao.getSuccessfulRegistration()?.loanNumber ?: dao.getAnyRegistration()?.loanNumber
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getInProgressLoanNumber(): String? = withContext(Dispatchers.IO) {
        try {
            if (dao.hasSuccessfulRegistration() > 0) return@withContext null
            dao.getMostRecentInProgressRegistration()?.loanNumber
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun isDeviceRegistered(): Boolean = withContext(Dispatchers.IO) {
        try {
            dao.hasSuccessfulRegistration() > 0
        } catch (e: Exception) {
            false
        }
    }
    
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
            backupRegistrationData()
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error saving device registration: ${e.message}", throwable = e)
        }
    }
    
    suspend fun getCompleteRegistrationData(): CompleteDeviceRegistrationEntity? = withContext(Dispatchers.IO) {
        try {
            dao.getSuccessfulRegistration()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllRegistrationData(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getSuccessfulRegistration()
            if (entity != null) {
                listOf(mapOf(
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
                    "language" to entity.language,
                    "device_fingerprint" to entity.deviceFingerprint,
                    "system_uptime" to entity.systemUptime,
                    "installed_apps_hash" to entity.installedAppsHash,
                    "system_properties_hash" to entity.systemPropertiesHash,
                    "is_device_rooted" to entity.isDeviceRooted,
                    "is_usb_debugging_enabled" to entity.isUsbDebuggingEnabled,
                    "is_developer_mode_enabled" to entity.isDeveloperModeEnabled,
                    "is_bootloader_unlocked" to entity.isBootloaderUnlocked,
                    "is_custom_rom" to entity.isCustomRom,
                    "tamper_severity" to entity.tamperSeverity,
                    "tamper_flags" to entity.tamperFlags,
                    "latitude" to entity.latitude,
                    "longitude" to entity.longitude,
                    "registration_status" to entity.registrationStatus,
                    "registered_at" to entity.registeredAt,
                    "last_sync_at" to entity.lastSyncAt
                ))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Error getting all registration data: ${e.message}", throwable = e)
            emptyList()
        }
    }
    
    suspend fun clearAllRegistrations() = withContext(Dispatchers.IO) {
        try {
            dao.clearAllRegistrations()
            clearRegistrationBackup()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing registrations", e)
        }
    }
    
    suspend fun backupRegistrationData(): Boolean {
        return registrationBackup.backupRegistrationData()
    }
    
    suspend fun restoreRegistrationDataFromBackup(): Boolean {
        return registrationBackup.restoreRegistrationData()
    }
    
    suspend fun hasRegistrationBackup(): Boolean {
        return registrationBackup.hasBackup()
    }
    
    suspend fun clearRegistrationBackup(): Boolean {
        return registrationBackup.clearBackup()
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




