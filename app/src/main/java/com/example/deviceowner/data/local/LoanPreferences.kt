package com.example.deviceowner.data.local

import android.content.Context

/**
 * Local preferences for loan ID and device token only.
 * No registration or device data collection.
 */
class LoanPreferences(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_LOAN_ID = "loan_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStoredLoanId(): String? {
        val loanId = prefs.getString(KEY_LOAN_ID, null)
        return if (loanId.isNullOrBlank()) null else loanId
    }

    fun isLoanIdSet(): Boolean = !getStoredLoanId().isNullOrBlank()

    fun setLoanId(loanId: String, deviceToken: String? = null) {
        if (loanId.isBlank()) return
        prefs.edit()
            .putString(KEY_LOAN_ID, loanId)
            .putString(KEY_DEVICE_TOKEN, deviceToken ?: "android_${android.os.Build.ID}")
            .apply()
    }

    fun getDeviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)

    fun getLoanInfo(): Map<String, Any> {
        val loanId = getStoredLoanId() ?: "Not set"
        return mapOf(
            "loanId" to loanId,
            "deviceToken" to (getDeviceToken() ?: "Not set")
        )
    }
}
