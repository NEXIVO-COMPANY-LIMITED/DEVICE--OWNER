package com.microspace.payo.security.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Centralized manager for encrypted SharedPreferences.
 * Provides a single point for managing all encrypted preference files.
 */
class EncryptedPreferencesManager(private val context: Context) {

    companion object {
        private const val PREFS_DEVICE_DATA = "device_data_encrypted"
        private const val PREFS_REGISTRATION = "registration_data_encrypted"
        private const val PREFS_PAYMENT = "payment_data_encrypted"
        private const val PREFS_SECURITY = "security_data_encrypted"
        private const val PREFS_LOAN = "loan_data_encrypted"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * Gets encrypted preferences for device data
     */
    fun getDeviceDataPreferences(): SharedPreferences {
        return createEncryptedPreferences(PREFS_DEVICE_DATA)
    }

    /**
     * Gets encrypted preferences for registration data
     */
    fun getRegistrationPreferences(): SharedPreferences {
        return createEncryptedPreferences(PREFS_REGISTRATION)
    }

    /**
     * Gets encrypted preferences for payment data
     */
    fun getPaymentPreferences(): SharedPreferences {
        return createEncryptedPreferences(PREFS_PAYMENT)
    }

    /**
     * Gets encrypted preferences for security data
     */
    fun getSecurityPreferences(): SharedPreferences {
        return createEncryptedPreferences(PREFS_SECURITY)
    }

    /**
     * Gets encrypted preferences for loan data
     */
    fun getLoanPreferences(): SharedPreferences {
        return createEncryptedPreferences(PREFS_LOAN)
    }

    /**
     * Creates encrypted SharedPreferences with standard encryption scheme
     */
    private fun createEncryptedPreferences(fileName: String): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Stores sensitive string data with additional encryption layer
     */
    fun storeEncryptedString(
        preferencesType: PreferencesType,
        key: String,
        value: String
    ) {
        val prefs = when (preferencesType) {
            PreferencesType.DEVICE_DATA -> getDeviceDataPreferences()
            PreferencesType.REGISTRATION -> getRegistrationPreferences()
            PreferencesType.PAYMENT -> getPaymentPreferences()
            PreferencesType.SECURITY -> getSecurityPreferences()
            PreferencesType.LOAN -> getLoanPreferences()
        }
        
        val encryption = SecureDataEncryption(context)
        val encrypted = encryption.encryptString(value)
        prefs.edit().putString(key, encrypted).apply()
    }

    /**
     * Retrieves and decrypts sensitive string data
     */
    fun retrieveEncryptedString(
        preferencesType: PreferencesType,
        key: String
    ): String? {
        val prefs = when (preferencesType) {
            PreferencesType.DEVICE_DATA -> getDeviceDataPreferences()
            PreferencesType.REGISTRATION -> getRegistrationPreferences()
            PreferencesType.PAYMENT -> getPaymentPreferences()
            PreferencesType.SECURITY -> getSecurityPreferences()
            PreferencesType.LOAN -> getLoanPreferences()
        }
        
        val encrypted = prefs.getString(key, null) ?: return null
        
        return try {
            val encryption = SecureDataEncryption(context)
            encryption.decryptString(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clears all encrypted preferences
     */
    fun clearAllPreferences() {
        getDeviceDataPreferences().edit().clear().apply()
        getRegistrationPreferences().edit().clear().apply()
        getPaymentPreferences().edit().clear().apply()
        getSecurityPreferences().edit().clear().apply()
        getLoanPreferences().edit().clear().apply()
    }

    enum class PreferencesType {
        DEVICE_DATA,
        REGISTRATION,
        PAYMENT,
        SECURITY,
        LOAN
    }
}
