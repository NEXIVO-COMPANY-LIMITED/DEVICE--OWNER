package com.example.deviceowner.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {
    
    companion object {
        private const val PREF_NAME = "device_owner_prefs"
        private const val KEY_DEVICE_REGISTERED = "device_registered"
        private const val KEY_LOAN_NUMBER = "loan_number"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_REGISTRATION_COMPLETED = "registration_completed"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    fun isDeviceRegistered(): Boolean {
        return sharedPreferences.getBoolean(KEY_DEVICE_REGISTERED, false)
    }
    
    fun setDeviceRegistered(registered: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_DEVICE_REGISTERED, registered)
            .apply()
    }
    
    fun saveLoanNumber(loanNumber: String) {
        sharedPreferences.edit()
            .putString(KEY_LOAN_NUMBER, loanNumber)
            .apply()
    }
    
    fun getLoanNumber(): String? {
        return sharedPreferences.getString(KEY_LOAN_NUMBER, null)
    }
    
    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
    }
    
    fun getDeviceId(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ID, null)
    }
    
    fun setRegistrationCompleted(completed: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_REGISTRATION_COMPLETED, completed)
            .apply()
    }
    
    fun isRegistrationCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_REGISTRATION_COMPLETED, false)
    }
    
    fun clearRegistrationData() {
        sharedPreferences.edit()
            .remove(KEY_DEVICE_REGISTERED)
            .remove(KEY_LOAN_NUMBER)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_REGISTRATION_COMPLETED)
            .apply()
    }
}