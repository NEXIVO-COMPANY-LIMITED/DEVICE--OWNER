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
                    "deviceId" to registration.device_id,
                    "loanId" to registration.loan_number,
                    "serialNumber" to registration.serial_number,
                    "androidId" to registration.android_id,
                    "manufacturer" to registration.manufacturer,
                    "model" to registration.model,
                    "osVersion" to registration.os_version,
                    "sdkVersion" to registration.sdk_version.toString(),
                    "buildNumber" to registration.build_number.toString(),
                    "totalStorage" to registration.total_storage,
                    "installedRam" to registration.installed_ram,
                    "machineName" to registration.machine_name,
                    "processor" to registration.processor,
                    "bootloader" to registration.bootloader,
                    "deviceFingerprint" to registration.device_fingerprint,
                    "securityPatchLevel" to registration.security_patch_level,
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
                gson.fromJson(registration.device_imeis, type) ?: emptyList()
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
     * Get device count for a loan
     */
    suspend fun getDeviceCountForLoan(loanNumber: String): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            localDataManager.getRegistrationsByLoanNumber(loanNumber).size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device count: ${e.message}", e)
            0
        }
    }
}
