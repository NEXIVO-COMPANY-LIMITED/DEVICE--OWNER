package com.deviceowner.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.deviceowner.models.RegistrationData

/**
 * SharedPreferences utility for local data storage.
 * Manages registration data, user info, and device info persistence.
 */
class PreferencesManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "PreferencesManager"
        private const val PREFS_NAME = "device_owner_prefs"

        // User Info Keys
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SHOP_ID = "shop_id"

        // Device Info Keys
        private const val KEY_IMEI = "imei"
        private const val KEY_SERIAL_NUMBER = "serial_number"
        private const val KEY_MODEL = "model"
        private const val KEY_MANUFACTURER = "manufacturer"
        private const val KEY_ANDROID_VERSION = "android_version"

        // Registration Status Keys
        private const val KEY_REGISTRATION_STATUS = "registration_status"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_REGISTRATION_TIMESTAMP = "registration_timestamp"

        // Device Owner Status
        private const val KEY_DEVICE_OWNER_ENABLED = "device_owner_enabled"
        private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"
    }

    // ==================== User Info ====================

    /**
     * Save user ID
     */
    fun saveUserId(userId: String) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
    }

    /**
     * Get user ID
     */
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }

    /**
     * Save shop ID
     */
    fun saveShopId(shopId: String) {
        sharedPreferences.edit().putString(KEY_SHOP_ID, shopId).apply()
    }

    /**
     * Get shop ID
     */
    fun getShopId(): String? {
        return sharedPreferences.getString(KEY_SHOP_ID, null)
    }

    /**
     * Save all user info
     */
    fun saveUserInfo(userId: String, shopId: String) {
        sharedPreferences.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_SHOP_ID, shopId)
        }.apply()
    }

    /**
     * Get all user info
     */
    fun getUserInfo(): Pair<String?, String?> {
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val shopId = sharedPreferences.getString(KEY_SHOP_ID, null)
        return Pair(userId, shopId)
    }

    // ==================== Device Info ====================

    /**
     * Save IMEI
     */
    fun saveIMEI(imei: String) {
        sharedPreferences.edit().putString(KEY_IMEI, imei).apply()
    }

    /**
     * Get IMEI
     */
    fun getIMEI(): String? {
        return sharedPreferences.getString(KEY_IMEI, null)
    }

    /**
     * Save Serial Number
     */
    fun saveSerialNumber(serialNumber: String) {
        sharedPreferences.edit().putString(KEY_SERIAL_NUMBER, serialNumber).apply()
    }

    /**
     * Get Serial Number
     */
    fun getSerialNumber(): String? {
        return sharedPreferences.getString(KEY_SERIAL_NUMBER, null)
    }

    /**
     * Save Model
     */
    fun saveModel(model: String) {
        sharedPreferences.edit().putString(KEY_MODEL, model).apply()
    }

    /**
     * Get Model
     */
    fun getModel(): String? {
        return sharedPreferences.getString(KEY_MODEL, null)
    }

    /**
     * Save Manufacturer
     */
    fun saveManufacturer(manufacturer: String) {
        sharedPreferences.edit().putString(KEY_MANUFACTURER, manufacturer).apply()
    }

    /**
     * Get Manufacturer
     */
    fun getManufacturer(): String? {
        return sharedPreferences.getString(KEY_MANUFACTURER, null)
    }

    /**
     * Save Android Version
     */
    fun saveAndroidVersion(version: String) {
        sharedPreferences.edit().putString(KEY_ANDROID_VERSION, version).apply()
    }

    /**
     * Get Android Version
     */
    fun getAndroidVersion(): String? {
        return sharedPreferences.getString(KEY_ANDROID_VERSION, null)
    }

    /**
     * Save all device info
     */
    fun saveDeviceInfo(
        imei: String,
        serialNumber: String,
        model: String,
        manufacturer: String,
        androidVersion: String
    ) {
        sharedPreferences.edit().apply {
            putString(KEY_IMEI, imei)
            putString(KEY_SERIAL_NUMBER, serialNumber)
            putString(KEY_MODEL, model)
            putString(KEY_MANUFACTURER, manufacturer)
            putString(KEY_ANDROID_VERSION, androidVersion)
        }.apply()
    }

    /**
     * Get all device info
     */
    fun getDeviceInfo(): Map<String, String?> {
        return mapOf(
            "imei" to sharedPreferences.getString(KEY_IMEI, null),
            "serialNumber" to sharedPreferences.getString(KEY_SERIAL_NUMBER, null),
            "model" to sharedPreferences.getString(KEY_MODEL, null),
            "manufacturer" to sharedPreferences.getString(KEY_MANUFACTURER, null),
            "androidVersion" to sharedPreferences.getString(KEY_ANDROID_VERSION, null)
        )
    }

    // ==================== Registration Status ====================

    /**
     * Save registration status
     */
    fun saveRegistrationStatus(status: String) {
        sharedPreferences.edit().putString(KEY_REGISTRATION_STATUS, status).apply()
    }

    /**
     * Get registration status
     */
    fun getRegistrationStatus(): String? {
        return sharedPreferences.getString(KEY_REGISTRATION_STATUS, null)
    }

    /**
     * Save device ID
     */
    fun saveDeviceId(deviceId: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    /**
     * Get device ID
     */
    fun getDeviceId(): String? {
        return sharedPreferences.getString(KEY_DEVICE_ID, null)
    }

    /**
     * Save registration timestamp
     */
    fun saveRegistrationTimestamp(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_REGISTRATION_TIMESTAMP, timestamp).apply()
    }

    /**
     * Get registration timestamp
     */
    fun getRegistrationTimestamp(): Long {
        return sharedPreferences.getLong(KEY_REGISTRATION_TIMESTAMP, 0L)
    }

    /**
     * Save all registration data
     */
    fun saveRegistrationData(
        status: String,
        deviceId: String,
        timestamp: Long
    ) {
        sharedPreferences.edit().apply {
            putString(KEY_REGISTRATION_STATUS, status)
            putString(KEY_DEVICE_ID, deviceId)
            putLong(KEY_REGISTRATION_TIMESTAMP, timestamp)
        }.apply()
    }

    /**
     * Get all registration data
     */
    fun getRegistrationData(): Map<String, Any?> {
        return mapOf(
            "status" to sharedPreferences.getString(KEY_REGISTRATION_STATUS, null),
            "deviceId" to sharedPreferences.getString(KEY_DEVICE_ID, null),
            "timestamp" to sharedPreferences.getLong(KEY_REGISTRATION_TIMESTAMP, 0L)
        )
    }

    // ==================== Device Owner Status ====================

    /**
     * Save device owner enabled status
     */
    fun saveDeviceOwnerEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DEVICE_OWNER_ENABLED, enabled).apply()
    }

    /**
     * Get device owner enabled status
     */
    fun isDeviceOwnerEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DEVICE_OWNER_ENABLED, false)
    }

    /**
     * Save device fingerprint
     */
    fun saveDeviceFingerprint(fingerprint: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_FINGERPRINT, fingerprint).apply()
    }

    /**
     * Get device fingerprint
     */
    fun getDeviceFingerprint(): String? {
        return sharedPreferences.getString(KEY_DEVICE_FINGERPRINT, null)
    }

    // ==================== Complete Registration Data ====================

    /**
     * Save complete registration data
     */
    fun saveCompleteRegistrationData(
        userId: String,
        shopId: String,
        imei: String,
        serialNumber: String,
        model: String,
        manufacturer: String,
        androidVersion: String,
        deviceId: String,
        status: String
    ) {
        sharedPreferences.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_SHOP_ID, shopId)
            putString(KEY_IMEI, imei)
            putString(KEY_SERIAL_NUMBER, serialNumber)
            putString(KEY_MODEL, model)
            putString(KEY_MANUFACTURER, manufacturer)
            putString(KEY_ANDROID_VERSION, androidVersion)
            putString(KEY_DEVICE_ID, deviceId)
            putString(KEY_REGISTRATION_STATUS, status)
            putLong(KEY_REGISTRATION_TIMESTAMP, System.currentTimeMillis())
        }.apply()
    }

    /**
     * Get complete registration data
     */
    fun getCompleteRegistrationData(): RegistrationData? {
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val shopId = sharedPreferences.getString(KEY_SHOP_ID, null)
        val imei = sharedPreferences.getString(KEY_IMEI, null)
        val serialNumber = sharedPreferences.getString(KEY_SERIAL_NUMBER, null)
        val model = sharedPreferences.getString(KEY_MODEL, null)
        val manufacturer = sharedPreferences.getString(KEY_MANUFACTURER, null)
        val androidVersion = sharedPreferences.getString(KEY_ANDROID_VERSION, null)
        val deviceId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        val status = sharedPreferences.getString(KEY_REGISTRATION_STATUS, null)

        return if (userId != null && shopId != null && imei != null && serialNumber != null) {
            RegistrationData(
                userId = userId,
                shopId = shopId,
                imei = imei,
                serialNumber = serialNumber,
                model = model ?: "",
                manufacturer = manufacturer ?: "",
                androidVersion = androidVersion ?: "",
                deviceId = deviceId ?: "",
                status = status ?: "pending"
            )
        } else {
            null
        }
    }

    // ==================== Data Clearing ====================

    /**
     * Clear user info
     */
    fun clearUserInfo() {
        sharedPreferences.edit().apply {
            remove(KEY_USER_ID)
            remove(KEY_SHOP_ID)
        }.apply()
    }

    /**
     * Clear device info
     */
    fun clearDeviceInfo() {
        sharedPreferences.edit().apply {
            remove(KEY_IMEI)
            remove(KEY_SERIAL_NUMBER)
            remove(KEY_MODEL)
            remove(KEY_MANUFACTURER)
            remove(KEY_ANDROID_VERSION)
        }.apply()
    }

    /**
     * Clear registration data
     */
    fun clearRegistrationData() {
        sharedPreferences.edit().apply {
            remove(KEY_REGISTRATION_STATUS)
            remove(KEY_DEVICE_ID)
            remove(KEY_REGISTRATION_TIMESTAMP)
        }.apply()
    }

    /**
     * Clear all data
     * PERMANENTLY PROTECTED: Cannot be cleared in any mode
     */
    fun clearAllData() {
        Log.e(TAG, "✗ Cannot clear app data - data protection permanently enabled")
    }

    /**
     * Check if data is corrupted
     */
    fun isDataCorrupted(): Boolean {
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val shopId = sharedPreferences.getString(KEY_SHOP_ID, null)
        val imei = sharedPreferences.getString(KEY_IMEI, null)

        // Data is corrupted if critical fields are missing but others exist
        val hasUserInfo = userId != null || shopId != null
        val hasDeviceInfo = imei != null

        return (hasUserInfo && userId == null) || (hasDeviceInfo && imei == null)
    }

    /**
     * Recover from corrupted data
     */
    fun recoverFromCorruption() {
        if (isDataCorrupted()) {
            clearAllData()
        }
    }

    /**
     * Check if registration is complete
     */
    fun isRegistrationComplete(): Boolean {
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val shopId = sharedPreferences.getString(KEY_SHOP_ID, null)
        val imei = sharedPreferences.getString(KEY_IMEI, null)
        val serialNumber = sharedPreferences.getString(KEY_SERIAL_NUMBER, null)
        val deviceId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        val status = sharedPreferences.getString(KEY_REGISTRATION_STATUS, null)

        return userId != null && shopId != null && imei != null &&
                serialNumber != null && deviceId != null && status == "completed"
    }

    // ==================== Generic String Methods ====================

    /**
     * Save generic string value
     */
    fun saveString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    /**
     * Get generic string value
     */
    fun getString(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    /**
     * Save generic long value
     */
    fun saveLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
    }

    /**
     * Get generic long value
     */
    fun getLong(key: String): Long {
        return sharedPreferences.getLong(key, 0L)
    }

    /**
     * Save generic boolean value
     */
    fun saveBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    /**
     * Get generic boolean value
     */
    fun getBoolean(key: String): Boolean {
        return sharedPreferences.getBoolean(key, false)
    }

    /**
     * Remove a key
     */
    fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
}
