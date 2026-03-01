package com.microspace.payo.registration

import android.content.Context
import android.util.Log
import com.microspace.payo.security.crypto.EncryptionManager
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * RegistrationDataManager - Manages device registration data storage and retrieval.
 * Uses EncryptedSharedPreferences for security.
 */
object RegistrationDataManager {
    private const val TAG = "RegistrationDataManager"
    private const val PREF_NAME = "registration_data_encrypted"
    private const val KEY_DEVICE_DATA = "device_data_json"
    
    private fun getPrefs(context: Context) = 
        EncryptionManager(context).getEncryptedSharedPreferences(PREF_NAME)
    
    fun saveDeviceData(context: Context, deviceData: JsonObject) {
        try {
            getPrefs(context).edit().putString(KEY_DEVICE_DATA, deviceData.toString()).apply()
            Log.d(TAG, "Device data saved successfully (encrypted)")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving device data: ${e.message}", e)
        }
    }
    
    fun getDeviceData(context: Context): JsonObject? {
        return try {
            val json = getPrefs(context).getString(KEY_DEVICE_DATA, null)
            if (json != null) Gson().fromJson(json, JsonObject::class.java) else null
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving device data: ${e.message}", e)
            null
        }
    }
}



