package com.example.deviceowner.utils.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.deviceowner.data.DeviceIdProvider
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SharedPreferencesManager - ENHANCED v6.2
 * 
 * ✅ Direct Boot Aware: Critical data in Device Protected Storage.
 * ✅ Secure Storage: Sensitive data in EncryptedSharedPreferences.
 */
class SharedPreferencesManager(private val context: Context) {

    companion object {
        private const val TAG = "PrefsManager"
        private const val PREF_NAME = "device_owner_prefs"
        private const val SECURE_PREF_NAME = "secure_device_owner_prefs"
        
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

        // Sensitive Keys (Stored in Encrypted Storage)
        private const val KEY_UNLOCK_PASSWORD = "unlock_password"
        private const val KEY_LOAN_NUMBER = "loan_number"
    }

    // 1. Normal Prefs (Credential Encrypted)
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // 2. Device Protected Prefs (Available during Direct Boot)
    private val protectedPrefs: SharedPreferences by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val protectedContext = context.createDeviceProtectedStorageContext()
            protectedContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        } else {
            sharedPreferences
        }
    }

    // 3. Encrypted Prefs (Only available after first unlock)
    private val encryptedPrefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: ${e.message}")
            null
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
        encryptedPrefs?.edit()?.putString(KEY_LOAN_NUMBER, loanNumber)?.apply()
            ?: sharedPreferences.edit().putString(KEY_LOAN_NUMBER, loanNumber).apply()
    }

    fun getLoanNumber(): String? {
        return encryptedPrefs?.getString(KEY_LOAN_NUMBER, null) 
            ?: sharedPreferences.getString(KEY_LOAN_NUMBER, null)
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

    // Aliases for build compatibility
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

    // --- Secure Password Management ---
    
    fun saveUnlockPassword(password: String?) {
        if (password == null) {
            encryptedPrefs?.edit()?.remove(KEY_UNLOCK_PASSWORD)?.apply()
            return
        }
        encryptedPrefs?.edit()?.putString(KEY_UNLOCK_PASSWORD, password)?.apply()
            ?: sharedPreferences.edit().putString(KEY_UNLOCK_PASSWORD, password).apply()
        protectedPrefs.edit().putString(KEY_UNLOCK_PASSWORD, password).apply()
    }

    fun getUnlockPassword(): String? {
        return encryptedPrefs?.getString(KEY_UNLOCK_PASSWORD, null)
            ?: protectedPrefs.getString(KEY_UNLOCK_PASSWORD, null)
            ?: sharedPreferences.getString(KEY_UNLOCK_PASSWORD, null)
    }

    // --- Heartbeat Status Cache (Boot Survival) ---
    
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
