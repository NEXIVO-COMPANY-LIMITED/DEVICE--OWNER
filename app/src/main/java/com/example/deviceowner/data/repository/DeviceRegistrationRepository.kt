package com.example.deviceowner.data.repository

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.dao.BasicLoanInfo
import com.example.deviceowner.data.local.database.entities.CompleteDeviceRegistrationEntity
import com.example.deviceowner.data.models.DeviceRegistrationRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRegistrationRepository(private val context: Context) {
    
    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val dao = database.completeDeviceRegistrationDao()
    private val gson = Gson()
    private val registrationBackup = com.example.deviceowner.data.backup.RegistrationDataBackup(context)
    
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
            Log.d(TAG, "Loan number saved for future registration: $loanNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving loan number: ${e.message}", e)
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
            
            val entity = CompleteDeviceRegistrationEntity(
                deviceId = finalDeviceId,
                loanNumber = deviceRegistrationRequest.loanNumber ?: "UNKNOWN",
                manufacturer = (deviceInfo["manufacturer"] as? String) ?: "UNKNOWN",
                model = (deviceInfo["model"] as? String) ?: "UNKNOWN",
                serialNumber = deviceInfo["serial"] as? String,
                androidId = deviceInfo["android_id"] as? String,
                deviceImeis = (imeiInfo["device_imeis"] as? List<*>)?.joinToString(","),
                osVersion = androidInfo["version_release"] as? String,
                sdkVersion = (androidInfo["version_sdk_int"] as? String)?.toIntOrNull(),
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
            Log.d(TAG, "Complete registration data saved to local database (status: $status, deviceId: $finalDeviceId)")
            
            // Backup data if registration is successful
            if (status == "SUCCESS") {
                val backupSuccess = registrationBackup.backupRegistrationData()
                Log.d(TAG, "Registration data backup: ${if (backupSuccess) "SUCCESS" else "FAILED"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving registration data: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Update registration status after server response
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
            Log.d(TAG, "Registration status updated to: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating registration status: ${e.message}", e)
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
            Log.e(TAG, "Error getting stored loan number: ${e.message}", e)
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
            Log.e(TAG, "Error checking registration status: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get basic loan information for main screen (limited data)
     */
    suspend fun getBasicLoanInfo(): BasicLoanInfo? = withContext(Dispatchers.IO) {
        try {
            dao.getBasicLoanInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting basic loan info: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get complete registration data (for admin/debug purposes only)
     */
    suspend fun getCompleteRegistrationData(): CompleteDeviceRegistrationEntity? = withContext(Dispatchers.IO) {
        try {
            dao.getSuccessfulRegistration()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting complete registration data: ${e.message}", e)
            null
        }
    }
    
    /**
     * Update loan information from server
     */
    suspend fun updateLoanInfo(
        deviceId: String,
        nextPaymentDate: String?,
        totalAmount: Double?,
        paidAmount: Double?,
        remainingAmount: Double?
    ) = withContext(Dispatchers.IO) {
        try {
            dao.updateLoanInfo(deviceId, nextPaymentDate, totalAmount, paidAmount, remainingAmount)
            Log.d(TAG, "Loan information updated")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating loan info: ${e.message}", e)
        }
    }
    
    /**
     * Clear all registration data (for testing/reset purposes)
     */
    suspend fun clearAllRegistrations() = withContext(Dispatchers.IO) {
        try {
            dao.clearAllRegistrations()
            Log.d(TAG, "All registration data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing registration data: ${e.message}", e)
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
                    "last_sync_at" to entity.lastSyncAt,
                    "server_response" to entity.serverResponse
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all registration data: ${e.message}", e)
            emptyList()
        }
    }
}