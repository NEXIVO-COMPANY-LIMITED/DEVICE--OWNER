package com.microspace.payo.data.local

import android.content.Context
import com.microspace.payo.security.crypto.EncryptedPreferencesManager
import com.microspace.payo.security.crypto.SecureDataEncryption

/**
 * Local preferences for loan ID and device token with dual-layer encryption.
 * Uses EncryptedSharedPreferences + SecureDataEncryption for maximum security.
 */
class LoanPreferences(private val context: Context) {

    companion object {
        private const val KEY_LOAN_ID = "loan_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
    }

    private val prefsManager = EncryptedPreferencesManager(context)
    private val encryption = SecureDataEncryption(context)

    fun getStoredLoanId(): String? {
        return prefsManager.retrieveEncryptedString(
            EncryptedPreferencesManager.PreferencesType.LOAN,
            KEY_LOAN_ID
        )
    }

    fun isLoanIdSet(): Boolean = !getStoredLoanId().isNullOrBlank()

    fun setLoanId(loanId: String, deviceToken: String? = null) {
        if (loanId.isBlank()) return
        
        prefsManager.storeEncryptedString(
            EncryptedPreferencesManager.PreferencesType.LOAN,
            KEY_LOAN_ID,
            loanId
        )
        
        prefsManager.storeEncryptedString(
            EncryptedPreferencesManager.PreferencesType.LOAN,
            KEY_DEVICE_TOKEN,
            deviceToken ?: "android_${android.os.Build.ID}"
        )
    }

    fun getDeviceToken(): String? {
        return prefsManager.retrieveEncryptedString(
            EncryptedPreferencesManager.PreferencesType.LOAN,
            KEY_DEVICE_TOKEN
        )
    }

    fun getLoanInfo(): Map<String, Any> {
        val loanId = getStoredLoanId() ?: "Not set"
        return mapOf(
            "loanId" to loanId,
            "deviceToken" to (getDeviceToken() ?: "Not set")
        )
    }

    /**
     * Securely clears all loan data
     */
    fun clearLoanData() {
        val prefs = prefsManager.getLoanPreferences()
        prefs.edit().clear().apply()
    }
}
