package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.local.LocalDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages registration data protection and startup navigation
 * Ensures registration data cannot be deleted by user
 * Handles app startup flow based on registration data existence
 */
class RegistrationDataProtectionManager(private val context: Context) {
    
    private val localDataManager = LocalDataManager(context)
    private val TAG = "RegistrationDataProtectionManager"
    
    /**
     * Check if device has valid registration data
     * Returns true if protected registration exists and is active
     */
    suspend fun hasValidRegistration(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = localDataManager.getLatestRegistration()
            
            if (registration == null) {
                Log.d(TAG, "No registration data found")
                return@withContext false
            }
            
            if (!registration.isActive) {
                Log.d(TAG, "Registration is inactive")
                return@withContext false
            }
            
            if (!registration.isProtected) {
                Log.w(TAG, "Registration is not protected - marking as protected")
                // This shouldn't happen, but if it does, we should protect it
                return@withContext false
            }
            
            Log.d(TAG, "✓ Valid protected registration found: ${registration.deviceId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking registration: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get stored registration data for navigation
     */
    suspend fun getStoredRegistrationData(): RegistrationData? = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = localDataManager.getLatestRegistration()
            
            if (registration == null || !registration.isActive || !registration.isProtected) {
                return@withContext null
            }
            
            // Parse IMEI list from JSON
            val imeiList = try {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                gson.fromJson(registration.imeiList, type) as? List<String> ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            
            RegistrationData(
                deviceId = registration.deviceId,
                loanId = registration.loanId,
                imeiList = imeiList,
                serialNumber = registration.serialNumber,
                registrationDate = registration.registrationDate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving registration data: ${e.message}", e)
            null
        }
    }
    
    /**
     * Determine startup navigation screen based on registration status
     * Returns screen name to navigate to
     */
    suspend fun getStartupScreen(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val hasRegistration = hasValidRegistration()
            
            if (hasRegistration) {
                Log.d(TAG, "Navigating to home screen - registration found")
                "home"
            } else {
                Log.d(TAG, "Navigating to welcome screen - no registration found")
                "welcome"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determining startup screen: ${e.message}", e)
            "welcome"
        }
    }
    
    /**
     * Log registration protection status
     */
    suspend fun logProtectionStatus() = withContext(Dispatchers.IO) {
        try {
            val registration = localDataManager.getLatestRegistration()
            
            if (registration != null) {
                Log.d(TAG, """
                    Registration Protection Status:
                    - Device ID: ${registration.deviceId}
                    - Protected: ${registration.isProtected}
                    - Active: ${registration.isActive}
                    - Registration Date: ${registration.registrationDate}
                """.trimIndent())
            } else {
                Log.d(TAG, "No registration data to log")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging protection status: ${e.message}", e)
        }
    }
}

/**
 * Data class for registration information used in navigation
 */
data class RegistrationData(
    val deviceId: String,
    val loanId: String,
    val imeiList: List<String>,
    val serialNumber: String,
    val registrationDate: Long
)
