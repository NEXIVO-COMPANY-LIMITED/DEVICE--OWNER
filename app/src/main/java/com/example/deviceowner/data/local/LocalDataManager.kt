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
        deviceId: String,
        loanId: String,
        imeiList: List<String>,
        serialNumber: String,
        androidId: String,
        manufacturer: String,
        model: String,
        osVersion: String,
        sdkVersion: String,
        buildNumber: String,
        totalStorage: String,
        installedRam: String,
        machineName: String,
        networkOperatorName: String,
        simOperatorName: String,
        simState: String,
        phoneType: String,
        networkType: String,
        simSerialNumber: String,
        batteryCapacity: String,
        batteryTechnology: String,
        registrationToken: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val imeiJson = gson.toJson(imeiList)
            val registration = DeviceRegistrationEntity(
                deviceId = deviceId,
                loanId = loanId,
                imeiList = imeiJson,
                serialNumber = serialNumber,
                androidId = androidId,
                manufacturer = manufacturer,
                model = model,
                osVersion = osVersion,
                sdkVersion = sdkVersion,
                buildNumber = buildNumber,
                totalStorage = totalStorage,
                installedRam = installedRam,
                machineName = machineName,
                networkOperatorName = networkOperatorName,
                simOperatorName = simOperatorName,
                simState = simState,
                phoneType = phoneType,
                networkType = networkType,
                simSerialNumber = simSerialNumber,
                batteryCapacity = batteryCapacity,
                batteryTechnology = batteryTechnology,
                registrationToken = registrationToken
            )
            
            dao.insertRegistration(registration)
            Log.d(TAG, "✓ Device registration saved locally: $deviceId")
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
                Log.d(TAG, "✓ Retrieved latest registration: ${registration.deviceId}")
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
     * Get all registrations for a shop
     */
    suspend fun getRegistrationsByShopCode(shopCode: String): List<DeviceRegistrationEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.getRegistrationsByShopCode(shopCode)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving registrations by shop code: ${e.message}", e)
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
                dao.updateRegistration(registration.copy(registrationToken = token))
                Log.d(TAG, "✓ Registration token updated for device: $deviceId")
                true
            } else {
                Log.w(TAG, "Registration not found for device: $deviceId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error updating registration token: ${e.message}", e)
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
