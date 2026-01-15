package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import com.deviceowner.utils.PreferencesManager
import com.example.deviceowner.managers.DeviceMismatchHandler
import com.example.deviceowner.managers.IdentifierAuditLog
import com.example.deviceowner.managers.MismatchDetails
import com.example.deviceowner.managers.MismatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BootVerificationManager(private val context: Context) {

    companion object {
        private const val TAG = "BootVerificationManager"
    }

    private val deviceIdentifier = DeviceIdentifier(context)
    private val mismatchHandler = DeviceMismatchHandler(context)
    private val auditLog = IdentifierAuditLog(context)
    private val preferencesManager = PreferencesManager(context)

    /**
     * Verify device on app boot
     * Checks if device identifiers match stored values
     */
    suspend fun verifyOnBoot(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting boot verification...")
                
                // Get stored fingerprint
                val storedFingerprint = preferencesManager.getDeviceFingerprint()
                
                if (storedFingerprint == null) {
                    // First boot - store fingerprint
                    Log.d(TAG, "First boot detected - storing device profile")
                    storeInitialProfile()
                    auditLog.logVerification("FIRST_BOOT", "Device profile stored on first boot")
                    return@withContext true
                }
                
                // Verify fingerprint
                val currentFingerprint = deviceIdentifier.getDeviceFingerprint()
                val fingerprintMatches = deviceIdentifier.verifyFingerprint(storedFingerprint)
                
                if (!fingerprintMatches) {
                    Log.e(TAG, "Fingerprint mismatch detected on boot!")
                    
                    val mismatchDetails = MismatchDetails(
                        type = MismatchType.FINGERPRINT_MISMATCH,
                        description = "Device fingerprint mismatch on boot",
                        storedValue = storedFingerprint,
                        currentValue = currentFingerprint,
                        severity = "CRITICAL"
                    )
                    
                    mismatchHandler.handleMismatch(mismatchDetails)
                    return@withContext false
                }
                
                // Verify complete device profile
                val storedProfile = getStoredProfile()
                if (storedProfile != null) {
                    val currentProfile = deviceIdentifier.createDeviceProfile()
                    val differences = deviceIdentifier.compareProfiles(storedProfile, currentProfile)
                    
                    if (differences.isNotEmpty()) {
                        Log.w(TAG, "Device profile differences detected: $differences")
                        
                        // Determine mismatch type
                        val mismatchType = determineMismatchType(differences)
                        
                        val mismatchDetails = MismatchDetails(
                            type = mismatchType,
                            description = "Device profile mismatch: ${differences.joinToString(", ")}",
                            storedValue = storedProfile.deviceFingerprint,
                            currentValue = currentProfile.deviceFingerprint,
                            severity = if (differences.size > 2) "CRITICAL" else "HIGH"
                        )
                        
                        mismatchHandler.handleMismatch(mismatchDetails)
                        return@withContext false
                    }
                }
                
                // All checks passed
                Log.d(TAG, "Boot verification passed - device identifiers match")
                auditLog.logVerification("PASS", "Boot verification successful - all identifiers match")
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during boot verification", e)
                auditLog.logVerification("ERROR", "Boot verification error: ${e.message}")
                return@withContext false
            }
        }
    }

    /**
     * Store initial device profile on first boot
     */
    private fun storeInitialProfile() {
        try {
            val profile = deviceIdentifier.createDeviceProfile()
            
            // Store fingerprint
            preferencesManager.saveDeviceFingerprint(profile.deviceFingerprint)
            
            // Store complete profile as JSON
            val profileJson = profileToJson(profile)
            val sharedPrefs = context.getSharedPreferences("identifier_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("device_profile", profileJson).apply()
            
            // Store individual identifiers
            preferencesManager.saveIMEI(profile.imei)
            preferencesManager.saveSerialNumber(profile.serialNumber)
            sharedPrefs.edit().putString("android_id", profile.androidId).apply()
            sharedPrefs.edit().putString("manufacturer", profile.manufacturer).apply()
            sharedPrefs.edit().putString("model", profile.model).apply()
            sharedPrefs.edit().putString("android_version", profile.androidVersion).apply()
            sharedPrefs.edit().putString("api_level", profile.apiLevel.toString()).apply()
            sharedPrefs.edit().putString("sim_serial", profile.simSerialNumber).apply()
            sharedPrefs.edit().putString("build_number", profile.buildNumber).apply()
            
            Log.d(TAG, "Initial device profile stored")
            auditLog.logAction("PROFILE_STORED", "Initial device profile stored on first boot")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing initial profile", e)
        }
    }

    /**
     * Get stored device profile
     */
    private fun getStoredProfile(): DeviceProfile? {
        return try {
            val sharedPrefs = context.getSharedPreferences("identifier_prefs", Context.MODE_PRIVATE)
            val profileJson = sharedPrefs.getString("device_profile", null) ?: return null
            jsonToProfile(profileJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stored profile", e)
            null
        }
    }

    /**
     * Determine mismatch type based on differences
     */
    private fun determineMismatchType(differences: List<String>): MismatchType {
        return when {
            differences.size > 2 -> MismatchType.DEVICE_SWAP_DETECTED
            differences.contains("IMEI mismatch") && differences.contains("Serial number mismatch") -> 
                MismatchType.DEVICE_SWAP_DETECTED
            differences.contains("Android ID mismatch") && differences.contains("SIM serial mismatch") -> 
                MismatchType.DEVICE_CLONE_DETECTED
            differences.size > 1 -> MismatchType.MULTIPLE_MISMATCHES
            else -> MismatchType.FINGERPRINT_MISMATCH
        }
    }

    /**
     * Update device profile (for legitimate changes)
     */
    suspend fun updateDeviceProfile() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Updating device profile...")
                storeInitialProfile()
                auditLog.logAction("PROFILE_UPDATED", "Device profile updated")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating profile", e)
            }
        }
    }

    /**
     * Get verification status
     */
    fun getVerificationStatus(): String {
        return try {
            val storedFingerprint = preferencesManager.getDeviceFingerprint()
            val currentFingerprint = deviceIdentifier.getDeviceFingerprint()
            
            val status = """
                ===== DEVICE VERIFICATION STATUS =====
                Stored Fingerprint: ${storedFingerprint?.take(16) ?: "Not set"}****
                Current Fingerprint: ${currentFingerprint.take(16)}****
                Status: ${if (storedFingerprint == currentFingerprint) "VERIFIED" else "MISMATCH"}
                Last Verified: ${preferencesManager.getString("last_verified") ?: "Never"}
            """.trimIndent()
            
            Log.d(TAG, status)
            status
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verification status", e)
            "Error getting status"
        }
    }

    // ==================== JSON Serialization ====================

    private fun profileToJson(profile: DeviceProfile): String {
        return """
            {
                "imei":"${profile.imei}",
                "serialNumber":"${profile.serialNumber}",
                "androidId":"${profile.androidId}",
                "manufacturer":"${profile.manufacturer}",
                "model":"${profile.model}",
                "androidVersion":"${profile.androidVersion}",
                "apiLevel":${profile.apiLevel},
                "deviceFingerprint":"${profile.deviceFingerprint}",
                "simSerialNumber":"${profile.simSerialNumber}",
                "buildNumber":"${profile.buildNumber}",
                "timestamp":${profile.timestamp}
            }
        """.trimIndent()
    }

    private fun jsonToProfile(json: String): DeviceProfile? {
        return try {
            // Simple JSON parsing (in production, use a JSON library)
            val imei = extractJsonValue(json, "imei")
            val serialNumber = extractJsonValue(json, "serialNumber")
            val androidId = extractJsonValue(json, "androidId")
            val manufacturer = extractJsonValue(json, "manufacturer")
            val model = extractJsonValue(json, "model")
            val androidVersion = extractJsonValue(json, "androidVersion")
            val apiLevel = extractJsonValue(json, "apiLevel").toIntOrNull() ?: 0
            val deviceFingerprint = extractJsonValue(json, "deviceFingerprint")
            val simSerialNumber = extractJsonValue(json, "simSerialNumber")
            val buildNumber = extractJsonValue(json, "buildNumber")
            val timestamp = extractJsonValue(json, "timestamp").toLongOrNull() ?: System.currentTimeMillis()
            
            DeviceProfile(
                imei = imei,
                serialNumber = serialNumber,
                androidId = androidId,
                manufacturer = manufacturer,
                model = model,
                androidVersion = androidVersion,
                apiLevel = apiLevel,
                deviceFingerprint = deviceFingerprint,
                simSerialNumber = simSerialNumber,
                buildNumber = buildNumber,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing profile JSON", e)
            null
        }
    }

    private fun extractJsonValue(json: String, key: String): String {
        return try {
            val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
            val match = pattern.find(json)
            match?.groupValues?.get(1) ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
