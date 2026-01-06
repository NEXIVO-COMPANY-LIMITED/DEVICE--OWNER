package com.example.deviceowner.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class to manage stored registration data
 */
class StoredRegistrationManager(private val context: Context) {
    
    private val localDataManager = LocalDataManager(context)
    private val gson = Gson()
    private val TAG = "StoredRegistrationManager"
    
    /**
     * Get stored registration data as a map for easy access
     */
    suspend fun getStoredRegistrationData(): Map<String, String>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = localDataManager.getLatestRegistration()
            if (registration != null) {
                mapOf(
                    "deviceId" to registration.deviceId,
                    "loanId" to registration.loanId,
                    "serialNumber" to registration.serialNumber,
                    "androidId" to registration.androidId,
                    "manufacturer" to registration.manufacturer,
                    "model" to registration.model,
                    "osVersion" to registration.osVersion,
                    "sdkVersion" to registration.sdkVersion,
                    "buildNumber" to registration.buildNumber,
                    "totalStorage" to registration.totalStorage,
                    "installedRam" to registration.installedRam,
                    "machineName" to registration.machineName,
                    "networkOperatorName" to registration.networkOperatorName,
                    "simOperatorName" to registration.simOperatorName,
                    "simState" to registration.simState,
                    "phoneType" to registration.phoneType,
                    "networkType" to registration.networkType,
                    "simSerialNumber" to registration.simSerialNumber,
                    "batteryCapacity" to registration.batteryCapacity,
                    "batteryTechnology" to registration.batteryTechnology,
                    "registrationToken" to (registration.registrationToken ?: ""),
                    "registrationDate" to registration.registrationDate.toString()
                )
            } else {
                Log.w(TAG, "No stored registration found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving stored registration: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get IMEI list from stored registration
     */
    suspend fun getStoredImeiList(): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = localDataManager.getLatestRegistration()
            if (registration != null) {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(registration.imeiList, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving IMEI list: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Check if there's a stored registration
     */
    suspend fun hasStoredRegistration(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            localDataManager.getLatestRegistration() != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking stored registration: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get device count for a shop
     */
    suspend fun getDeviceCountForShop(shopCode: String): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            localDataManager.getRegistrationsByShopCode(shopCode).size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device count: ${e.message}", e)
            0
        }
    }
}
