package com.example.deviceowner.managers

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import java.security.MessageDigest

/**
 * Manages device identifier collection and verification
 * Collects IMEI, serial number, Android ID, and other device identifiers
 */
class DeviceIdentifier(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceIdentifier"
    }
    
    /**
     * Create a complete device profile with all identifiers
     */
    fun createDeviceProfile(): DeviceProfile {
        return DeviceProfile(
            imei = getIMEI(),
            serialNumber = getSerialNumber(),
            androidId = getAndroidId(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            deviceFingerprint = Build.FINGERPRINT,
            simSerialNumber = getSimSerialNumber(),
            buildNumber = Build.DISPLAY,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Get device fingerprint
     */
    fun getDeviceFingerprint(): String {
        return Build.FINGERPRINT
    }
    
    /**
     * Verify fingerprint matches stored value
     */
    fun verifyFingerprint(storedFingerprint: String): Boolean {
        val currentFingerprint = getDeviceFingerprint()
        return currentFingerprint == storedFingerprint
    }
    
    /**
     * Compare two device profiles and return differences
     */
    fun compareProfiles(stored: DeviceProfile, current: DeviceProfile): List<String> {
        val differences = mutableListOf<String>()
        
        if (stored.imei != current.imei) {
            differences.add("IMEI mismatch")
        }
        if (stored.serialNumber != current.serialNumber) {
            differences.add("Serial number mismatch")
        }
        if (stored.androidId != current.androidId) {
            differences.add("Android ID mismatch")
        }
        if (stored.deviceFingerprint != current.deviceFingerprint) {
            differences.add("Device fingerprint mismatch")
        }
        if (stored.simSerialNumber != current.simSerialNumber) {
            differences.add("SIM serial mismatch")
        }
        
        return differences
    }
    
    /**
     * Get IMEI (International Mobile Equipment Identity)
     */
    private fun getIMEI(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.imei ?: "UNKNOWN"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IMEI", e)
            "UNKNOWN"
        }
    }
    
    /**
     * Get device serial number
     */
    private fun getSerialNumber(): String {
        return try {
            Build.SERIAL
        } catch (e: Exception) {
            Log.e(TAG, "Error getting serial number", e)
            "UNKNOWN"
        }
    }
    
    /**
     * Get Android ID
     */
    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Android ID", e)
            "UNKNOWN"
        }
    }
    
    /**
     * Get SIM serial number
     */
    private fun getSimSerialNumber(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.simSerialNumber ?: "UNKNOWN"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SIM serial", e)
            "UNKNOWN"
        }
    }
}
