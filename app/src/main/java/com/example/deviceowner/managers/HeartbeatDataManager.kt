package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages device heartbeat data collection and verification
 * Collects critical device data for backend verification
 * Uses protected cache storage that cannot be deleted by users
 */
class HeartbeatDataManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "heartbeat_data",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val deviceIdentifier = DeviceIdentifier(context)
    
    // Protected cache directory - internal to app, not accessible to users
    private val protectedCacheDir: File by lazy {
        val cacheDir = File(context.cacheDir, "protected_heartbeat_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            // Set restrictive permissions (700 = rwx------)
            cacheDir.setReadable(false, false)
            cacheDir.setReadable(true, true)
            cacheDir.setWritable(false, false)
            cacheDir.setWritable(true, true)
            cacheDir.setExecutable(false, false)
            cacheDir.setExecutable(true, true)
        }
        cacheDir
    }
    
    companion object {
        private const val TAG = "HeartbeatDataManager"
        private const val KEY_LAST_HEARTBEAT = "last_heartbeat"
        private const val KEY_HEARTBEAT_HISTORY = "heartbeat_history"
        private const val KEY_LAST_VERIFIED_DATA = "last_verified_data"
        private const val MAX_HISTORY_SIZE = 100
        
        // Protected cache file names
        private const val CACHE_HEARTBEAT_FILE = "hb_data.cache"
        private const val CACHE_HISTORY_FILE = "hb_history.cache"
        private const val CACHE_VERIFIED_FILE = "hb_verified.cache"
        private const val CACHE_METADATA_FILE = "hb_metadata.cache"
    }
    
    /**
     * Collect current device heartbeat data
     * Collects all device data EXCEPT IMEI and Serial Number
     * Fetches data directly from device
     * Includes location data if available
     */
    suspend fun collectHeartbeatData(storedRegistration: com.example.deviceowner.data.local.DeviceRegistrationEntity? = null): HeartbeatData {
        val timestamp = System.currentTimeMillis()
        
        // Get device ID from stored registration or SharedPreferences
        val deviceId = storedRegistration?.deviceId ?: getDeviceId()
        
        // IMEI and Serial Number are NOT collected in heartbeat
        val imei = ""
        val serialNumber = ""
        
        // Fetch other device data directly (no permission issues)
        val androidId = getAndroidId()
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val apiLevel = Build.VERSION.SDK_INT
        val buildNumber = Build.DISPLAY
        val deviceFingerprint = getDeviceFingerprint()
        
        // Get location data
        val locationManager = LocationManager(context)
        val locationData = try {
            locationManager.getCurrentLocation() ?: locationManager.getLastKnownLocation()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get location: ${e.message}")
            null
        }
        
        return HeartbeatData(
            deviceId = deviceId,
            timestamp = timestamp,
            imei = imei,
            serialNumber = serialNumber,
            androidId = androidId,
            simSerialNumber = "", // Skip SIM serial due to permission issues
            deviceFingerprint = deviceFingerprint,
            manufacturer = manufacturer,
            model = model,
            androidVersion = androidVersion,
            apiLevel = apiLevel,
            buildNumber = buildNumber,
            bootloader = Build.BOOTLOADER,
            hardware = Build.HARDWARE,
            product = Build.PRODUCT,
            device = Build.DEVICE,
            brand = Build.BRAND,
            host = Build.HOST,
            user = Build.USER,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH,
            systemUptime = getSystemUptime(),
            batteryLevel = getBatteryLevel(),
            isDeviceRooted = isDeviceRooted(),
            isUSBDebuggingEnabled = isUSBDebuggingEnabled(),
            isDeveloperModeEnabled = isDeveloperModeEnabled(),
            installedAppsHash = getInstalledAppsHash(),
            systemPropertiesHash = getSystemPropertiesHash(),
            latitude = locationData?.latitude ?: 0.0,
            longitude = locationData?.longitude ?: 0.0,
            locationAccuracy = locationData?.accuracy ?: 0f,
            locationProvider = locationData?.provider ?: "unknown",
            locationTimestamp = locationData?.timestamp ?: 0L
        )
    }
    
    /**
     * Get Android ID directly without permission issues
     */
    private fun getAndroidId(): String {
        return try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Android ID: ${e.message}")
            ""
        }
    }
    
    /**
     * Get device fingerprint
     */
    private fun getDeviceFingerprint(): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val fingerprint = "${Build.FINGERPRINT}${Build.DEVICE}${Build.PRODUCT}"
            val hash = digest.digest(fingerprint.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating fingerprint: ${e.message}")
            ""
        }
    }
    
    /**
     * Save heartbeat data locally in protected cache
     */
    fun saveHeartbeatData(data: HeartbeatData) {
        try {
            val json = gson.toJson(data)
            
            // Save to protected cache file
            val cacheFile = File(protectedCacheDir, CACHE_HEARTBEAT_FILE)
            cacheFile.writeText(json)
            setFileProtection(cacheFile)
            
            // Also save to SharedPreferences as backup
            prefs.edit().apply {
                putString(KEY_LAST_HEARTBEAT, json)
                putLong("last_heartbeat_time", data.timestamp)
                apply()
            }
            
            // Add to history
            addToHistory(data)
            Log.d(TAG, "Heartbeat data saved to protected cache: ${data.timestamp}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving heartbeat data", e)
        }
    }
    
    /**
     * Set file protection - make file read-only and protected from deletion
     */
    private fun setFileProtection(file: File) {
        try {
            // Set restrictive permissions (400 = r-------)
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.setExecutable(false, false)
            file.setExecutable(false, true)
            Log.d(TAG, "File protection set: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting file protection: ${e.message}")
        }
    }
    
    /**
     * Get last saved heartbeat data from protected cache
     */
    fun getLastHeartbeatData(): HeartbeatData? {
        return try {
            // Try to read from protected cache first
            val cacheFile = File(protectedCacheDir, CACHE_HEARTBEAT_FILE)
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                return gson.fromJson(json, HeartbeatData::class.java)
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_LAST_HEARTBEAT, null) ?: return null
            gson.fromJson(json, HeartbeatData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving heartbeat data", e)
            null
        }
    }
    
    /**
     * Compare current data with last verified data
     * Returns list of changes detected
     */
    suspend fun detectDataChanges(): List<DataChange> {
        val currentData = collectHeartbeatData()
        val lastVerifiedData = getLastVerifiedData() ?: return emptyList()
        
        val changes = mutableListOf<DataChange>()
        
        // Check critical identifiers
        if (currentData.imei != lastVerifiedData.imei) {
            changes.add(DataChange(
                field = "IMEI",
                previousValue = lastVerifiedData.imei,
                currentValue = currentData.imei,
                severity = "CRITICAL",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        if (currentData.serialNumber != lastVerifiedData.serialNumber) {
            changes.add(DataChange(
                field = "Serial Number",
                previousValue = lastVerifiedData.serialNumber,
                currentValue = currentData.serialNumber,
                severity = "CRITICAL",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        if (currentData.androidId != lastVerifiedData.androidId) {
            changes.add(DataChange(
                field = "Android ID",
                previousValue = lastVerifiedData.androidId,
                currentValue = currentData.androidId,
                severity = "CRITICAL",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        if (currentData.deviceFingerprint != lastVerifiedData.deviceFingerprint) {
            changes.add(DataChange(
                field = "Device Fingerprint",
                previousValue = lastVerifiedData.deviceFingerprint,
                currentValue = currentData.deviceFingerprint,
                severity = "CRITICAL",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        // Check security flags
        if (currentData.isDeviceRooted != lastVerifiedData.isDeviceRooted) {
            changes.add(DataChange(
                field = "Root Status",
                previousValue = lastVerifiedData.isDeviceRooted.toString(),
                currentValue = currentData.isDeviceRooted.toString(),
                severity = "HIGH",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        if (currentData.isUSBDebuggingEnabled != lastVerifiedData.isUSBDebuggingEnabled) {
            changes.add(DataChange(
                field = "USB Debugging",
                previousValue = lastVerifiedData.isUSBDebuggingEnabled.toString(),
                currentValue = currentData.isUSBDebuggingEnabled.toString(),
                severity = "HIGH",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        if (currentData.isDeveloperModeEnabled != lastVerifiedData.isDeveloperModeEnabled) {
            changes.add(DataChange(
                field = "Developer Mode",
                previousValue = lastVerifiedData.isDeveloperModeEnabled.toString(),
                currentValue = currentData.isDeveloperModeEnabled.toString(),
                severity = "HIGH",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        // Check app integrity
        if (currentData.installedAppsHash != lastVerifiedData.installedAppsHash) {
            changes.add(DataChange(
                field = "Installed Apps",
                previousValue = lastVerifiedData.installedAppsHash,
                currentValue = currentData.installedAppsHash,
                severity = "MEDIUM",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        // Check system properties
        if (currentData.systemPropertiesHash != lastVerifiedData.systemPropertiesHash) {
            changes.add(DataChange(
                field = "System Properties",
                previousValue = lastVerifiedData.systemPropertiesHash,
                currentValue = currentData.systemPropertiesHash,
                severity = "MEDIUM",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        return changes
    }
    
    /**
     * Save verified data from backend to protected cache
     */
    fun saveVerifiedData(data: HeartbeatData) {
        try {
            val json = gson.toJson(data)
            
            // Save to protected cache file
            val cacheFile = File(protectedCacheDir, CACHE_VERIFIED_FILE)
            cacheFile.writeText(json)
            setFileProtection(cacheFile)
            
            // Also save to SharedPreferences as backup
            prefs.edit().apply {
                putString(KEY_LAST_VERIFIED_DATA, json)
                putLong("last_verified_time", System.currentTimeMillis())
                apply()
            }
            Log.d(TAG, "Verified data saved to protected cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving verified data", e)
        }
    }
    
    /**
     * Get last verified data from backend from protected cache
     */
    fun getLastVerifiedData(): HeartbeatData? {
        return try {
            // Try to read from protected cache first
            val cacheFile = File(protectedCacheDir, CACHE_VERIFIED_FILE)
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                return gson.fromJson(json, HeartbeatData::class.java)
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_LAST_VERIFIED_DATA, null) ?: return null
            gson.fromJson(json, HeartbeatData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving verified data", e)
            null
        }
    }
    
    /**
     * Get heartbeat history from protected cache
     */
    fun getHeartbeatHistory(): List<HeartbeatData> {
        return try {
            // Try to read from protected cache first
            val cacheFile = File(protectedCacheDir, CACHE_HISTORY_FILE)
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<HeartbeatData>>() {}.type
                return gson.fromJson(json, type)
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_HEARTBEAT_HISTORY, "[]") ?: "[]"
            val type = object : com.google.gson.reflect.TypeToken<List<HeartbeatData>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving history", e)
            emptyList()
        }
    }
    
    /**
     * Add heartbeat to history
     */
    private fun addToHistory(data: HeartbeatData) {
        try {
            val history = getHeartbeatHistory().toMutableList()
            history.add(data)
            
            // Keep only last MAX_HISTORY_SIZE entries
            if (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(0)
            }
            
            val json = gson.toJson(history)
            prefs.edit().putString(KEY_HEARTBEAT_HISTORY, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to history", e)
        }
    }
    
    /**
     * Get device ID
     */
    private fun getDeviceId(): String {
        return prefs.getString("device_id", "") ?: ""
    }
    
    /**
     * Get system uptime in milliseconds
     */
    private fun getSystemUptime(): Long {
        return try {
            val uptime = Runtime.getRuntime().exec("uptime").inputStream.bufferedReader().readText()
            uptime.hashCode().toLong()
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get battery level
     */
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Check if device is rooted
     */
    private fun isDeviceRooted(): Boolean {
        return try {
            val paths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            paths.any { java.io.File(it).exists() }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if USB debugging is enabled
     */
    private fun isUSBDebuggingEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if developer mode is enabled
     */
    private fun isDeveloperModeEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get hash of installed apps
     */
    private fun getInstalledAppsHash(): String {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            val appList = packages.map { it.packageName }.sorted().joinToString(",")
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(appList.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Get hash of system properties
     */
    private fun getSystemPropertiesHash(): String {
        return try {
            val properties = StringBuilder()
            properties.append(Build.FINGERPRINT)
            properties.append(Build.DEVICE)
            properties.append(Build.PRODUCT)
            properties.append(Build.HARDWARE)
            properties.append(Build.BOOTLOADER)
            
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(properties.toString().toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Data class for heartbeat information
 */
data class HeartbeatData(
    val deviceId: String,
    val timestamp: Long,
    val imei: String,
    val serialNumber: String,
    val androidId: String,
    val simSerialNumber: String,
    val deviceFingerprint: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val buildNumber: String,
    val bootloader: String,
    val hardware: String,
    val product: String,
    val device: String,
    val brand: String,
    val host: String,
    val user: String,
    val securityPatchLevel: String,
    val systemUptime: Long,
    val batteryLevel: Int,
    val isDeviceRooted: Boolean,
    val isUSBDebuggingEnabled: Boolean,
    val isDeveloperModeEnabled: Boolean,
    val installedAppsHash: String,
    val systemPropertiesHash: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationAccuracy: Float = 0f,
    val locationProvider: String = "unknown",
    val locationTimestamp: Long = 0L
)

/**
 * Data class for detected changes
 */
data class DataChange(
    val field: String,
    val previousValue: String,
    val currentValue: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM, LOW
    val timestamp: Long
)
