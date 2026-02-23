package com.example.deviceowner.utils.cache

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages registration cache and allows clearing when user retries with different loan number
 */
object RegistrationCacheManager {
    
    private const val TAG = "RegistrationCacheManager"
    
    /**
     * Clear all registration cache for a fresh start
     * Called when user wants to retry with a different loan number
     */
    suspend fun clearRegistrationCache(context: Context) {
        try {
            withContext(Dispatchers.IO) {
                val database = DeviceOwnerDatabase.getDatabase(context)
                val dao = database.completeDeviceRegistrationDao()
                
                // Delete all registration records
                dao.clearAllRegistrations()
                
                Log.i(TAG, "✅ Registration cache cleared successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing registration cache: ${e.message}", e)
        }
    }
    
    /**
     * Clear registration cache for specific loan number
     */
    suspend fun clearRegistrationCacheForLoan(context: Context, loanNumber: String) {
        try {
            withContext(Dispatchers.IO) {
                val database = DeviceOwnerDatabase.getDatabase(context)
                val dao = database.completeDeviceRegistrationDao()
                
                // Get registration by loan number and delete it
                val registration = dao.getRegistrationByLoanNumber(loanNumber)
                if (registration != null) {
                    dao.clearAllRegistrations()
                }
                
                Log.i(TAG, "✅ Registration cache cleared for loan: $loanNumber")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing cache for loan $loanNumber: ${e.message}", e)
        }
    }
    
    /**
     * Clear device data cache (collected device info)
     * Called when user changes loan number to force re-collection
     */
    fun clearDeviceDataCache(context: Context) {
        try {
            val prefs = context.getSharedPreferences("device_data", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            Log.i(TAG, "✅ Device data cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing device data cache: ${e.message}", e)
        }
    }
    
    /**
     * Clear all registration-related caches
     * Complete reset for fresh registration attempt
     */
    suspend fun clearAllRegistrationCaches(context: Context) {
        try {
            // Clear database
            clearRegistrationCache(context)
            
            // Clear shared preferences
            clearDeviceDataCache(context)
            
            // Clear registration backup
            try {
                val backupPrefs = context.getSharedPreferences("registration_backup", Context.MODE_PRIVATE)
                backupPrefs.edit().clear().apply()
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear backup prefs: ${e.message}")
            }
            
            Log.i(TAG, "✅ All registration caches cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing all caches: ${e.message}", e)
        }
    }
}
