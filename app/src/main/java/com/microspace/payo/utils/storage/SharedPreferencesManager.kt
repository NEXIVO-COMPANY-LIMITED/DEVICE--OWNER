package com.microspace.payo.utils.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.microspace.payo.data.DeviceIdProvider
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.microspace.payo.security.crypto.EncryptionManager

/**
 * SharedPreferencesManager - v8.0
 * 
 * âœ… Direct Boot Aware: Critical data in Device Protected Storage.
 * âœ… Encrypted storage using EncryptedSharedPreferences.
 */
class SharedPreferencesManager(private val context: Context) {

    private val encryptionManager = EncryptionManager(context)

    companion object {
        private const val TAG = "PrefsManager"
        private const val PREF_NAME = "device_owner_prefs_encrypted"
        private const val PROTECTED_PREF_NAME = "device_owner_prefs_protected_encrypted"
        
        // Critical Boot Keys (Stored in Protected Storage)
        private const val KEY_DEVICE_REGISTERED = "device_registered"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_REASON = "lock_reason"
        private const val KEY_REGISTRATION_COMPLETED = "registration_completed"
        private const val KEY_HEARTBEAT_ENABLED = "heartbeat_enabled"
        private const val KEY_DEVICE_IMEI = "device_imei"
        private const val KEY_SERIAL_NUMBER = "serial_number"
        private const val KEY_ORGANIZATION_NAME = "organization_name"
        private const val KEY_SUPPORT_CONTACT = "support_contact"
        private const val KEY_DEVICE_TYPE = "device_type"
        private const val KEY_LAST_HEARTBEAT_TIME = "last_heartbeat_time"
        private const val KEY_REGISTRATION_STATUS = "registration_status"
        private const val KEY_PROVISIONING_WIFI_SSID = "provisioning_wifi_ssid"
        private const val KEY_UNLOCK_PASSWORD = "unlock_password"
        private const val KEY_LOAN_NUMBER = "loan_number"
    }

    // 1. Normal Encrypted Prefs
    private val sharedPreferences: SharedPreferences =
        encryptionManager.getEncryptedSharedPreferences(PREF_NAME)

    // 2. Device Protected Prefs (Available during Direct Boot)
    // Note: EncryptedSharedPreferences might have limitations in Direct Boot if the key is not accessible.
    // For now, we use a separate encrypted file for protected storage if possible.
    private val protectedPrefs: SharedPreferences by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val protectedContext = context.createDeviceProtectedStorageContext()
            // We still want encryption even in protected storage
            EncryptionManager(protectedContext).getEncryptedSharedPreferences(PROTECTED_PREF_NAME)
        } else {
            sharedPreferences
        }
    }

    /**
     * Checks if device is registered. Works during Direct Boot.
     */
    fun isDeviceRegistered(): Boolean {
        return protectedPrefs.getBoolean(KEY_DEVICE_REGISTERED, false) || 
               sharedPreferences.getBoolean(KEY_DEVICE_REGISTERED, false) ||
               DeviceIdProvider.isDeviceRegistered(context)
    }

    fun setDeviceRegistered(registered: Boolean) {
        protectedPrefs.edit().putBoolean(KEY_DEVICE_REGISTERED, registered).apply()
        sharedPreferences.edit().putBoolean(KEY_DEVICE_REGISTERED, registered).apply()
    }

    fun saveLoanNumber(loanNumber: String) {
        sharedPreferences.edit().putString(KEY_LOAN_NUMBER, loanNumber).apply()
    }

    fun getLoanNumber(): String? {
        return sharedPreferences.getString(KEY_LOAN_NUMBER, null)
    }

    fun saveDeviceId(deviceId: String) {
        protectedPrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        DeviceIdProvider.saveDeviceId(context, deviceId)
    }

    fun getDeviceId(): String? {
        return protectedPrefs.getString(KEY_DEVICE_ID, null) 
            ?: sharedPreferences.getString(KEY_DEVICE_ID, null)
            ?: DeviceIdProvider.getDeviceId(context)
    }

    fun getDeviceIdForHeartbeat(): String? = getDeviceId()
    fun getDeviceIdForDeviceDetail(): String? = getDeviceId()

    fun setRegistrationCompleted(completed: Boolean) {
        protectedPrefs.edit().putBoolean(KEY_REGISTRATION_COMPLETED, completed).apply()
        sharedPreferences.edit().putBoolean(KEY_REGISTRATION_COMPLETED, completed).apply()
    }

    fun isRegistrationCompleted(): Boolean {
        return protectedPrefs.getBoolean(KEY_REGISTRATION_COMPLETED, false) ||
               sharedPreferences.getBoolean(KEY_REGISTRATION_COMPLETED, false)
    }

    fun isRegistrationComplete(): Boolean = isRegistrationCompleted()

    fun setHeartbeatEnabled(enabled: Boolean) {
        protectedPrefs.edit().putBoolean(KEY_HEARTBEAT_ENABLED, enabled).apply()
    }

    fun setDeviceImei(imeiJson: String) {
        protectedPrefs.edit().putString(KEY_DEVICE_IMEI, imeiJson).apply()
    }

    fun getDeviceImeiList(): List<String> {
        val json = protectedPrefs.getString(KEY_DEVICE_IMEI, null) ?: return emptyList()
        return try {
            Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setSerialNumber(serial: String) {
        protectedPrefs.edit().putString(KEY_SERIAL_NUMBER, serial).apply()
    }

    fun getSerialNumber(): String? = protectedPrefs.getString(KEY_SERIAL_NUMBER, null)

    fun getOrganizationName(): String = protectedPrefs.getString(KEY_ORGANIZATION_NAME, "Organization") ?: "Organization"
    
    fun getSupportContact(): String? = protectedPrefs.getString(KEY_SUPPORT_CONTACT, null)

    fun getDeviceType(): String? = protectedPrefs.getString(KEY_DEVICE_TYPE, "Handheld")

    fun setLastHeartbeatTime(time: Long) {
        protectedPrefs.edit().putLong(KEY_LAST_HEARTBEAT_TIME, time).apply()
    }

    fun getRegistrationStatus(): String? = protectedPrefs.getString(KEY_REGISTRATION_STATUS, null)

    fun saveProvisioningWifiSsid(ssid: String) {
        protectedPrefs.edit().putString(KEY_PROVISIONING_WIFI_SSID, ssid).apply()
        Log.d(TAG, "Provisioning WiFi SSID saved: $ssid")
    }

    fun getProvisioningWifiSsid(): String? {
        return protectedPrefs.getString(KEY_PROVISIONING_WIFI_SSID, null)
    }

    fun clearProvisioningWifiSsid() {
        protectedPrefs.edit().remove(KEY_PROVISIONING_WIFI_SSID).apply()
        Log.d(TAG, "Provisioning WiFi SSID cleared from storage")
    }

    fun saveUnlockPassword(password: String?) {
        if (password == null) {
            sharedPreferences.edit().remove(KEY_UNLOCK_PASSWORD).apply()
            protectedPrefs.edit().remove(KEY_UNLOCK_PASSWORD).apply()
            return
        }
        sharedPreferences.edit().putString(KEY_UNLOCK_PASSWORD, password).apply()
        protectedPrefs.edit().putString(KEY_UNLOCK_PASSWORD, password).apply()
    }

    fun getUnlockPassword(): String? {
        return protectedPrefs.getString(KEY_UNLOCK_PASSWORD, null)
            ?: sharedPreferences.getString(KEY_UNLOCK_PASSWORD, null)
    }

    fun saveHeartbeatResponse(
        nextPaymentDate: String?,
        unlockPassword: String?,
        isLocked: Boolean = false,
        serverTime: String? = null,
        lockReason: String? = null
    ) {
        protectedPrefs.edit().apply {
            putBoolean(KEY_IS_LOCKED, isLocked)
            putString(KEY_LOCK_REASON, lockReason)
            putString("heartbeat_next_payment_date", nextPaymentDate)
            apply()
        }
        saveUnlockPassword(unlockPassword)
    }

    fun isDeviceLockedFromHeartbeat(): Boolean {
        return protectedPrefs.getBoolean(KEY_IS_LOCKED, false)
    }

    fun getLockReasonFromResponse(): String? {
        return protectedPrefs.getString(KEY_LOCK_REASON, null)
    }
}




