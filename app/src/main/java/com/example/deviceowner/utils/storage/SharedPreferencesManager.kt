package com.example.deviceowner.utils.storage

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(private val context: Context) {

    companion object {
        private const val PREF_NAME = "device_owner_prefs"
        private const val KEY_DEVICE_REGISTERED = "device_registered"
        private const val KEY_LOAN_NUMBER = "loan_number"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_REGISTRATION_COMPLETED = "registration_completed"
        private const val KEY_DEVICE_MODEL = "device_model"
        private const val KEY_DEVICE_MANUFACTURER = "device_manufacturer"
        private const val KEY_DEVICE_TYPE = "device_type"
        private const val KEY_SERIAL_NUMBER = "serial_number"
        private const val KEY_REGISTRATION_DATE = "registration_date"
        private const val KEY_REGISTRATION_STATUS = "registration_status"
        private const val KEY_REGISTRATION_ERROR = "registration_error"
        private const val KEY_REGISTRATION_ATTEMPTS = "registration_attempts"
        private const val KEY_DEVICE_ID_FOR_HEARTBEAT = "device_id_for_heartbeat"
        private const val KEY_HEARTBEAT_ENABLED = "heartbeat_enabled"
        private const val KEY_LAST_HEARTBEAT_TIME = "last_heartbeat_time"
        private const val KEY_DEVICE_IMEI = "device_imei"
        private const val KEY_SUPPORT_CONTACT = "support_contact"
        private const val KEY_ORGANIZATION_NAME = "organization_name"

        // Heartbeat response cache keys
        const val KEY_NEXT_PAYMENT_DATE = "next_payment_date"
        const val KEY_UNLOCK_PASSWORD = "unlock_password"
        const val KEY_SERVER_TIME = "server_time"
        const val KEY_IS_LOCKED = "is_locked"
        const val KEY_LOCK_REASON = "lock_reason"
        const val KEY_LAST_RESPONSE_TIME = "last_response_time"
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

    // Device Information Methods
    fun setDeviceModel(model: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_MODEL, model).apply()
    }

    fun getDeviceModel(): String? {
        return sharedPreferences.getString(KEY_DEVICE_MODEL, null)
    }

    fun setDeviceManufacturer(manufacturer: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_MANUFACTURER, manufacturer).apply()
    }

    fun getDeviceManufacturer(): String? {
        return sharedPreferences.getString(KEY_DEVICE_MANUFACTURER, null)
    }

    fun setDeviceType(deviceType: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_TYPE, deviceType).apply()
    }

    fun getDeviceType(): String? {
        return sharedPreferences.getString(KEY_DEVICE_TYPE, null)
    }

    fun setSerialNumber(serialNumber: String) {
        sharedPreferences.edit().putString(KEY_SERIAL_NUMBER, serialNumber).apply()
    }

    fun getSerialNumber(): String? {
        return sharedPreferences.getString(KEY_SERIAL_NUMBER, null)
    }

    fun setRegistrationDate(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_REGISTRATION_DATE, timestamp).apply()
    }

    fun getRegistrationDate(): Long {
        return sharedPreferences.getLong(KEY_REGISTRATION_DATE, 0)
    }

    fun setRegistrationStatus(status: String) {
        sharedPreferences.edit().putString(KEY_REGISTRATION_STATUS, status).apply()
    }

    fun getRegistrationStatus(): String? {
        return sharedPreferences.getString(KEY_REGISTRATION_STATUS, null)
    }

    fun setRegistrationError(error: String) {
        sharedPreferences.edit().putString(KEY_REGISTRATION_ERROR, error).apply()
    }

    fun getRegistrationError(): String? {
        return sharedPreferences.getString(KEY_REGISTRATION_ERROR, null)
    }

    fun clearRegistrationError() {
        sharedPreferences.edit().remove(KEY_REGISTRATION_ERROR).apply()
    }

    fun setRegistrationAttempts(attempts: Int) {
        sharedPreferences.edit().putInt(KEY_REGISTRATION_ATTEMPTS, attempts).apply()
    }

    fun getRegistrationAttempts(): Int {
        return sharedPreferences.getInt(KEY_REGISTRATION_ATTEMPTS, 0)
    }

    fun setRegistrationComplete(complete: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_REGISTRATION_COMPLETED, complete).apply()
    }

    fun isRegistrationComplete(): Boolean {
        return sharedPreferences.getBoolean(KEY_REGISTRATION_COMPLETED, false)
    }

    // Heartbeat Methods
    fun setDeviceIdForHeartbeat(deviceId: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_ID_FOR_HEARTBEAT, deviceId).apply()
    }

    fun getDeviceIdForHeartbeat(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ID_FOR_HEARTBEAT, null)
            ?: context.getSharedPreferences("device_data", Context.MODE_PRIVATE)
                .getString("device_id_for_heartbeat", null)
            ?: context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
                .getString("device_id", null)
    }

    fun setHeartbeatEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_HEARTBEAT_ENABLED, enabled).apply()
    }

    fun isHeartbeatEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_HEARTBEAT_ENABLED, false)
    }

    fun setLastHeartbeatTime(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_HEARTBEAT_TIME, timestamp).apply()
    }

    fun getLastHeartbeatTime(): Long {
        return sharedPreferences.getLong(KEY_LAST_HEARTBEAT_TIME, 0)
    }

    // IMEI Methods
    fun setDeviceImei(imei: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_IMEI, imei).apply()
    }

    fun getDeviceImei(): String? {
        return sharedPreferences.getString(KEY_DEVICE_IMEI, null)
    }

    fun getDeviceImeiList(): List<String> {
        val imeiJson = sharedPreferences.getString(KEY_DEVICE_IMEI, null) ?: return emptyList()
        return try {
            com.google.gson.Gson().fromJson(imeiJson, Array<String>::class.java).toList()
        } catch (e: Exception) {
            if (imeiJson.contains(",")) {
                imeiJson.split(",").map { it.trim() }
            } else {
                listOf(imeiJson)
            }
        }
    }

    /** Support contact for soft lock "Contact Support": URL, tel:..., or mailto:... */
    fun getSupportContact(): String? = sharedPreferences.getString(KEY_SUPPORT_CONTACT, null)
    fun setSupportContact(value: String?) {
        sharedPreferences.edit().putString(KEY_SUPPORT_CONTACT, value).apply()
    }

    /** Organization name (e.g. PAYO) shown in soft lock overlay. Default "PAYO". */
    fun getOrganizationName(): String =
        sharedPreferences.getString(KEY_ORGANIZATION_NAME, null)?.takeIf { it.isNotBlank() } ?: "PAYO"
    fun setOrganizationName(value: String?) {
        sharedPreferences.edit().putString(KEY_ORGANIZATION_NAME, value).apply()
    }

    // --- Heartbeat response cache (next_payment, server_time, etc.) ---
    private val heartbeatPrefs: SharedPreferences =
        context.getSharedPreferences("heartbeat_response", Context.MODE_PRIVATE)

    fun saveHeartbeatResponse(
        nextPaymentDate: String?,
        unlockPassword: String?,
        serverTime: String?,
        isLocked: Boolean,
        lockReason: String?
    ) {
        heartbeatPrefs.edit().apply {
            putString(KEY_NEXT_PAYMENT_DATE, nextPaymentDate)
            putString(KEY_UNLOCK_PASSWORD, unlockPassword)
            putString(KEY_SERVER_TIME, serverTime)
            putBoolean(KEY_IS_LOCKED, isLocked)
            putString(KEY_LOCK_REASON, lockReason)
            putLong(KEY_LAST_RESPONSE_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun getNextPaymentDate(): String? = heartbeatPrefs.getString(KEY_NEXT_PAYMENT_DATE, null)
    fun getUnlockPassword(): String? = heartbeatPrefs.getString(KEY_UNLOCK_PASSWORD, null)
    fun getServerTime(): String? = heartbeatPrefs.getString(KEY_SERVER_TIME, null)
    fun isDeviceLockedFromResponse(): Boolean = heartbeatPrefs.getBoolean(KEY_IS_LOCKED, false)
    fun getLockReasonFromResponse(): String? = heartbeatPrefs.getString(KEY_LOCK_REASON, null)
    fun getLastHeartbeatResponseTime(): Long = heartbeatPrefs.getLong(KEY_LAST_RESPONSE_TIME, 0L)
}
