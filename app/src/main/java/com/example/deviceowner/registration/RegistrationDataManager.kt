package com.example.deviceowner.registration

import android.content.Context
import android.util.Log
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * RegistrationDataManager - Manages device registration data storage and retrieval.
 * 
 * Flow:
 * 1. Device Registration API Call → Server returns device_id + device data
 * 2. Store ALL server response data locally
 * 3. During Heartbeat → Retrieve stored data + collect current device state
 * 4. Send to server with exact same format
 */
object RegistrationDataManager {
    private const val TAG = "RegistrationDataManager"
    private const val PREF_NAME = "registration_data"
    private const val KEY_DEVICE_DATA = "device_data_json"
    
    fun saveDeviceData(context: Context, deviceData: JsonObject) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_DEVICE_DATA, deviceData.toString()).apply()
            Log.d(TAG, "Device data saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving device data: ${e.message}", e)
        }
    }
    
    fun getDeviceData(context: Context): JsonObject? {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_DEVICE_DATA, null)
            if (json != null) Gson().fromJson(json, JsonObject::class.java) else null
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving device data: ${e.message}", e)
            null
        }
    }
}