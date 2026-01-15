package com.example.deviceowner.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalDataManager(private val context: Context) {
    
    private val database by lazy { AppDatabase.getInstance(context) }
    private val dao by lazy { database.deviceRegistrationDao() }
    private val gson = Gson()
    private val TAG = "LocalDataManager"
    
    /**
     * Save device registration data locally
     */
    suspend fun saveDeviceRegistration(
        device_id: String,
        serial_number: String,
        device_type: String,
        manufacturer: String,
        system_type: String,
        model: String,
        platform: String,
        os_version: String,
        os_edition: String,
        processor: String,
        installed_ram: String,
        total_storage: String,
        build_number: Int,
        sdk_version: Int,
        device_imeis: String,
        loan_number: String,
        machine_name: String,
        android_id: String,
        device_fingerprint: String,
        bootloader: String,
        security_patch_level: String,
        system_uptime: Long,
        installed_apps_hash: String,
        system_properties_hash: String,
        is_device_rooted: Boolean,
        is_usb_debugging_enabled: Boolean,
        is_developer_mode_enabled: Boolean,
        is_bootloader_unlocked: Boolean,
        is_custom_rom: Boolean,
        latitude: Double,
        longitude: Double,
        tamper_severity: String,
        tamper_flags: String,
        battery_level: Int
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = DeviceRegistrationEntity(
                device_id = device_id,
                serial_number = serial_number,
                device_type = device_type,
                manufacturer = manufacturer,
                system_type = system_type,
                model = model,
                platform = platform,
                os_version = os_version,
                os_edition = os_edition,
                processor = processor,
                installed_ram = installed_ram,
                total_storage = total_storage,
                build_number = build_number,
                sdk_version = sdk_version,
                device_imeis = device_imeis,
                loan_number = loan_number,
                machine_name = machine_name,
                android_id = android_id,
                device_fingerprint = device_fingerprint,
                bootloader = bootloader,
                security_patch_level = security_patch_level,
                system_uptime = system_uptime,
                installed_apps_hash = installed_apps_hash,
                system_properties_hash = system_properties_hash,
                is_device_rooted = is_device_rooted,
                is_usb_debugging_enabled = is_usb_debugging_enabled,
                is_developer_mode_enabled = is_developer_mode_enabled,
                is_bootloader_unlocked = is_bootloader_unlocked,
                is_custom_rom = is_custom_rom,
                latitude = latitude,
                longitude = longitude,
                tamper_severity = tamper_severity,
                tamper_flags = tamper_flags,
                battery_level = battery_level
            )
            
            dao.insertRegistration(registration)
            Log.d(TAG, "✓ Device registration saved locally: $device_id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error saving registration: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get the latest device registration
     */
    suspend fun getLatestRegistration(): DeviceRegistrationEntity? = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = dao.getLatestRegistration()
            if (registration != null) {
                Log.d(TAG, "✓ Retrieved latest registration: ${registration.device_id}")
            }
            registration
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving registration: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get registration by device ID
     */
    suspend fun getRegistrationByDeviceId(deviceId: String): DeviceRegistrationEntity? = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.getRegistrationByDeviceId(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving registration by device ID: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get all registrations for a loan
     */
    suspend fun getRegistrationsByLoanNumber(loanNumber: String): List<DeviceRegistrationEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.getRegistrationsByLoanNumber(loanNumber)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving registrations by loan number: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get all registrations
     */
    suspend fun getAllRegistrations(): List<DeviceRegistrationEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.getAllRegistrations()
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving all registrations: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Check if device is already registered
     */
    suspend fun isDeviceRegistered(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.getRegistrationByDeviceId(deviceId) != null
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error checking device registration: ${e.message}", e)
            false
        }
    }
    
    /**
     * Update registration token
     */
    suspend fun updateRegistrationToken(deviceId: String, token: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = dao.getRegistrationByDeviceId(deviceId)
            if (registration != null) {
                dao.updateRegistration(registration)
                Log.d(TAG, "✓ Registration updated for device: $deviceId")
                true
            } else {
                Log.w(TAG, "Registration not found for device: $deviceId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error updating registration: ${e.message}", e)
            false
        }
    }
    
    /**
     * Deactivate a registration
     */
    suspend fun deactivateRegistration(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.deactivateRegistration(deviceId)
            Log.d(TAG, "✓ Registration deactivated: $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error deactivating registration: ${e.message}", e)
            false
        }
    }
    
    /**
     * Delete a registration - PROTECTED: Cannot delete protected registrations
     */
    suspend fun deleteRegistration(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = dao.getRegistrationByDeviceId(deviceId)
            if (registration?.isProtected == true) {
                Log.w(TAG, "✗ Cannot delete protected registration: $deviceId")
                return@withContext false
            }
            dao.deleteUnprotectedRegistration(deviceId)
            Log.d(TAG, "✓ Registration deleted: $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error deleting registration: ${e.message}", e)
            false
        }
    }
    
    /**
     * Clear all registrations - PROTECTED: Cannot delete protected registrations
     */
    suspend fun clearAllRegistrations(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.deleteUnprotectedRegistrations()
            Log.d(TAG, "✓ All unprotected registrations cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error clearing registrations: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if any protected registration exists
     */
    suspend fun hasProtectedRegistration(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.hasProtectedRegistration() > 0
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error checking protected registration: ${e.message}", e)
            false
        }
    }
}
