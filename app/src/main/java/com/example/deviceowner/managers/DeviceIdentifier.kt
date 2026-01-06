package com.example.deviceowner.managers

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.deviceowner.utils.DeviceUtils
import java.security.MessageDigest

data class DeviceProfile(
    val imei: String,
    val serialNumber: String,
    val androidId: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val deviceFingerprint: String,
    val simSerialNumber: String,
    val buildNumber: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DeviceIdentifier(private val context: Context) {

    companion object {
        private const val TAG = "DeviceIdentifier"
    }

    /**
     * Get IMEI (International Mobile Equipment Identity)
     * 14-16 digits, unique identifier for mobile devices
     */
    fun getIMEI(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager.imei ?: "Unknown"
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.deviceId ?: "Unknown"
            }
            Log.d(TAG, "IMEI collected: ${imei.take(8)}****")
            imei
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IMEI", e)
            "Unknown"
        }
    }

    /**
     * Get device serial number
     * Unique identifier for the device
     */
    fun getSerialNumber(): String {
        return try {
            val serial = Build.SERIAL ?: "Unknown"
            Log.d(TAG, "Serial number collected: ${serial.take(8)}****")
            serial
        } catch (e: Exception) {
            Log.e(TAG, "Error getting serial number", e)
            "Unknown"
        }
    }

    /**
     * Get Android ID
     * Unique identifier per device per user
     */
    fun getAndroidID(): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Unknown"
            Log.d(TAG, "Android ID collected: ${androidId.take(8)}****")
            androidId
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Android ID", e)
            "Unknown"
        }
    }

    /**
     * Get device manufacturer
     */
    fun getManufacturer(): String {
        return try {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            Log.d(TAG, "Manufacturer: $manufacturer")
            manufacturer
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manufacturer", e)
            "Unknown"
        }
    }

    /**
     * Get device model
     */
    fun getModel(): String {
        return try {
            val model = Build.MODEL
            Log.d(TAG, "Model: $model")
            model
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model", e)
            "Unknown"
        }
    }

    /**
     * Get Android OS version
     */
    fun getAndroidVersion(): String {
        return try {
            val version = Build.VERSION.RELEASE
            Log.d(TAG, "Android version: $version")
            version
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Android version", e)
            "Unknown"
        }
    }

    /**
     * Get API level
     */
    fun getAPILevel(): Int {
        return try {
            val apiLevel = Build.VERSION.SDK_INT
            Log.d(TAG, "API level: $apiLevel")
            apiLevel
        } catch (e: Exception) {
            Log.e(TAG, "Error getting API level", e)
            0
        }
    }

    /**
     * Get SIM serial number
     */
    fun getSimSerialNumber(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val simSerial = telephonyManager.simSerialNumber ?: "Not available"
            Log.d(TAG, "SIM serial collected: ${simSerial.take(8)}****")
            simSerial
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SIM serial", e)
            "Not available"
        }
    }

    /**
     * Get build number
     */
    fun getBuildNumber(): String {
        return try {
            val buildNumber = Build.DISPLAY
            Log.d(TAG, "Build number: $buildNumber")
            buildNumber
        } catch (e: Exception) {
            Log.e(TAG, "Error getting build number", e)
            "Unknown"
        }
    }

    /**
     * Generate device fingerprint
     * SHA-256 hash of key identifiers
     */
    fun getDeviceFingerprint(): String {
        return try {
            val fingerprint = "${getAndroidID()}${getManufacturer()}${getModel()}${getSerialNumber()}"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(fingerprint.toByteArray())
            val hash = digest.joinToString("") { "%02x".format(it) }
            Log.d(TAG, "Device fingerprint generated: ${hash.take(16)}****")
            hash
        } catch (e: Exception) {
            Log.e(TAG, "Error generating fingerprint", e)
            "unknown_fingerprint"
        }
    }

    /**
     * Create complete device profile
     * Aggregates all identifiers into a single profile
     */
    fun createDeviceProfile(): DeviceProfile {
        Log.d(TAG, "Creating device profile...")
        return DeviceProfile(
            imei = getIMEI(),
            serialNumber = getSerialNumber(),
            androidId = getAndroidID(),
            manufacturer = getManufacturer(),
            model = getModel(),
            androidVersion = getAndroidVersion(),
            apiLevel = getAPILevel(),
            deviceFingerprint = getDeviceFingerprint(),
            simSerialNumber = getSimSerialNumber(),
            buildNumber = getBuildNumber(),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Verify device fingerprint against stored value
     * Returns true if fingerprints match, false if mismatch
     */
    fun verifyFingerprint(storedFingerprint: String): Boolean {
        val currentFingerprint = getDeviceFingerprint()
        val matches = currentFingerprint == storedFingerprint
        
        Log.d(TAG, "Fingerprint verification: ${if (matches) "MATCH" else "MISMATCH"}")
        if (!matches) {
            Log.w(TAG, "Stored: ${storedFingerprint.take(16)}****")
            Log.w(TAG, "Current: ${currentFingerprint.take(16)}****")
        }
        
        return matches
    }

    /**
     * Compare two device profiles for differences
     * Returns list of differences
     */
    fun compareProfiles(profile1: DeviceProfile, profile2: DeviceProfile): List<String> {
        val differences = mutableListOf<String>()
        
        if (profile1.imei != profile2.imei) differences.add("IMEI mismatch")
        if (profile1.serialNumber != profile2.serialNumber) differences.add("Serial number mismatch")
        if (profile1.androidId != profile2.androidId) differences.add("Android ID mismatch")
        if (profile1.manufacturer != profile2.manufacturer) differences.add("Manufacturer mismatch")
        if (profile1.model != profile2.model) differences.add("Model mismatch")
        if (profile1.simSerialNumber != profile2.simSerialNumber) differences.add("SIM serial mismatch")
        if (profile1.deviceFingerprint != profile2.deviceFingerprint) differences.add("Fingerprint mismatch")
        
        if (differences.isNotEmpty()) {
            Log.w(TAG, "Profile differences detected: $differences")
        }
        
        return differences
    }

    /**
     * Check if device identifiers are valid
     */
    fun isValid(): Boolean {
        val profile = createDeviceProfile()
        val isValid = profile.imei != "Unknown" &&
                profile.serialNumber != "Unknown" &&
                profile.androidId != "Unknown" &&
                profile.deviceFingerprint != "unknown_fingerprint"
        
        Log.d(TAG, "Device validation: ${if (isValid) "VALID" else "INVALID"}")
        return isValid
    }
}
