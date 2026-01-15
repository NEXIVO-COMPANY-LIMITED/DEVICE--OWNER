package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

/**
 * Manages device heartbeat data collection
 * Collects ONLY fields that can be reliably obtained on Android devices
 * Aligned with heartbeat JSON schema
 * REMOVED: product, hardware, device, brand (not needed for detection)
 */
class HeartbeatDataManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "heartbeat_data",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val tamperDetector = TamperDetector(context)
    
    private val protectedCacheDir: File by lazy {
        val cacheDir = File(context.cacheDir, "protected_heartbeat_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
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
        private const val KEY_LAST_VERIFIED_DATA = "last_verified_data"
        private const val CACHE_HEARTBEAT_FILE = "hb_data.cache"
        private const val CACHE_VERIFIED_FILE = "hb_verified.cache"
    }
    
    /**
     * Collect current device heartbeat data
     * ONLY collects fields from heartbeat JSON schema
     * device_type is always "phone"
     */
    suspend fun collectHeartbeatData(
        storedRegistration: com.example.deviceowner.data.local.DeviceRegistrationEntity? = null,
        isOnline: Boolean = isDeviceOnline()
    ): HeartbeatData {
        val timestamp = System.currentTimeMillis()
        val deviceId = storedRegistration?.device_id ?: getDeviceId()
        
        return HeartbeatData(
            deviceId = deviceId,
            timestamp = timestamp,
            serialNumber = Build.SERIAL,
            deviceType = "phone",  // Always "phone"
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            platform = "Android",
            osVersion = Build.VERSION.RELEASE,
            osEdition = "Mobile",
            processor = Build.HARDWARE,
            installedRam = getInstalledRam(),
            totalStorage = getTotalStorage(),
            machineName = Build.DEVICE,
            macAddress = getMacAddress(),
            buildNumber = Build.VERSION.SDK_INT,
            sdkVersion = Build.VERSION.SDK_INT,
            deviceImeis = listOf(getDeviceImei()),
            loanStatus = storedRegistration?.loan_number ?: "unknown",
            isOnline = isOnline,  // Track online/offline status
            isTrusted = !tamperDetector.isTampered(),
            isLocked = false,
            androidId = getAndroidId(),
            deviceFingerprint = getDeviceFingerprint(),
            bootloader = Build.BOOTLOADER,
            securityPatchLevel = getSecurityPatchLevel(),
            systemUptime = getSystemUptime(),
            installedAppsHash = getInstalledAppsHash(),
            systemPropertiesHash = getSystemPropertiesHash(),
            isDeviceRooted = tamperDetector.isRooted(),
            isUSBDebuggingEnabled = tamperDetector.isUSBDebuggingEnabled(),
            isDeveloperModeEnabled = tamperDetector.isDeveloperModeEnabled(),
            isBootloaderUnlocked = tamperDetector.isBootloaderUnlocked(),
            isCustomROM = tamperDetector.isCustomROM(),
            latitude = "0.0",
            longitude = "0.0",
            tamperSeverity = tamperDetector.getTamperStatus().severity.name,
            tamperFlags = tamperDetector.getTamperStatus().tamperFlags,
            batteryLevel = getBatteryLevel()
        )
    }
    
    /**
     * Check if device is online
     */
    private fun isDeviceOnline(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            network != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking online status: ${e.message}")
            false
        }
    }
    
    private fun getDeviceId(): String {
        return prefs.getString("device_id", Build.SERIAL) ?: Build.SERIAL
    }
    
    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Android ID: ${e.message}")
            ""
        }
    }
    
    private fun getDeviceFingerprint(): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val fingerprint = "${Build.FINGERPRINT}${Build.DEVICE}${Build.PRODUCT}"
            val hash = digest.digest(fingerprint.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating fingerprint: ${e.message}")
            ""
        }
    }
    
    private fun getSecurityPatchLevel(): String {
        return try {
            Build.VERSION.SECURITY_PATCH ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getSystemUptime(): Long {
        return try {
            android.os.SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getInstalledAppsHash(): String {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            val appNames = apps.map { it.packageName }.sorted().joinToString(",")
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(appNames.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting apps hash: ${e.message}")
            ""
        }
    }
    
    private fun getSystemPropertiesHash(): String {
        return try {
            val props = "${Build.FINGERPRINT}${Build.DEVICE}${Build.PRODUCT}${Build.HARDWARE}"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(props.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system properties hash: ${e.message}")
            ""
        }
    }
    
    private fun getDeviceImei(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.imei ?: "unknown"
            } else {
                @Suppress("DEPRECATION")
                tm.deviceId ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getInstalledRam(): String {
        return try {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory() / (1024 * 1024)
            "${totalMemory}MB"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getTotalStorage(): String {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total = stat.blockCount.toLong() * stat.blockSize / (1024 * 1024 * 1024)
            "${total}GB"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getMacAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name == "wlan0") {
                    val mac = iface.hardwareAddress
                    return mac?.joinToString(":") { "%02x".format(it) } ?: "unknown"
                }
            }
            "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: 100
            (level * 100 / scale)
        } catch (e: Exception) {
            0
        }
    }
    
    fun saveHeartbeatData(data: HeartbeatData) {
        try {
            val json = gson.toJson(data)
            val cacheFile = File(protectedCacheDir, CACHE_HEARTBEAT_FILE)
            cacheFile.writeText(json)
            prefs.edit().putString(KEY_LAST_HEARTBEAT, json).apply()
            Log.d(TAG, "Heartbeat data saved")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving heartbeat data", e)
        }
    }
    
    fun getLastHeartbeatData(): HeartbeatData? {
        return try {
            val cacheFile = File(protectedCacheDir, CACHE_HEARTBEAT_FILE)
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                return gson.fromJson(json, HeartbeatData::class.java)
            }
            val json = prefs.getString(KEY_LAST_HEARTBEAT, null) ?: return null
            gson.fromJson(json, HeartbeatData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving heartbeat data", e)
            null
        }
    }
    
    fun saveVerifiedData(data: HeartbeatData) {
        try {
            val json = gson.toJson(data)
            val cacheFile = File(protectedCacheDir, CACHE_VERIFIED_FILE)
            cacheFile.writeText(json)
            prefs.edit().putString(KEY_LAST_VERIFIED_DATA, json).apply()
            Log.d(TAG, "Verified data saved")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving verified data", e)
        }
    }
    
    fun getLastVerifiedData(): HeartbeatData? {
        return try {
            val cacheFile = File(protectedCacheDir, CACHE_VERIFIED_FILE)
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                return gson.fromJson(json, HeartbeatData::class.java)
            }
            val json = prefs.getString(KEY_LAST_VERIFIED_DATA, null) ?: return null
            gson.fromJson(json, HeartbeatData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving verified data", e)
            null
        }
    }
    
    /**
     * Compare sent data with backend response
     * ONLY compares immutable fields that cannot change
     * Detects tampering by comparing what we sent vs what backend verified
     */
    suspend fun compareWithBackendResponse(sentData: HeartbeatData, response: com.example.deviceowner.data.api.HeartbeatVerificationResponse): TamperDetectionResult {
        // Backend response doesn't include immutable fields for comparison
        // Return no tampering detected
        return TamperDetectionResult(
            isTampered = false,
            mismatches = emptyList(),
            severity = "NONE"
        )
    }
    
    /**
     * Compare with local stored data (for offline mode)
     * Uses same logic as backend comparison but with local data
     */
    suspend fun compareWithLocalData(currentData: HeartbeatData): TamperDetectionResult {
        val lastVerifiedData = getLastVerifiedData()
        
        if (lastVerifiedData == null) {
            Log.d(TAG, "No local verified data available for offline comparison")
            return TamperDetectionResult(
                isTampered = false,
                mismatches = emptyList(),
                severity = "NONE"
            )
        }
        
        val mismatches = mutableListOf<TamperMismatch>()
        var maxSeverity = "NONE"
        
        // Compare ONLY immutable fields (same as backend comparison)
        
        if (currentData.serialNumber != lastVerifiedData.serialNumber) {
            mismatches.add(TamperMismatch(
                field = "Serial Number",
                sentValue = currentData.serialNumber,
                verifiedValue = lastVerifiedData.serialNumber,
                severity = "CRITICAL"
            ))
            maxSeverity = "CRITICAL"
        }
        
        if (currentData.manufacturer != lastVerifiedData.manufacturer) {
            mismatches.add(TamperMismatch(
                field = "Manufacturer",
                sentValue = currentData.manufacturer,
                verifiedValue = lastVerifiedData.manufacturer,
                severity = "CRITICAL"
            ))
            maxSeverity = "CRITICAL"
        }
        
        if (currentData.model != lastVerifiedData.model) {
            mismatches.add(TamperMismatch(
                field = "Model",
                sentValue = currentData.model,
                verifiedValue = lastVerifiedData.model,
                severity = "CRITICAL"
            ))
            maxSeverity = "CRITICAL"
        }
        
        if (currentData.androidId != lastVerifiedData.androidId) {
            mismatches.add(TamperMismatch(
                field = "Android ID",
                sentValue = currentData.androidId,
                verifiedValue = lastVerifiedData.androidId,
                severity = "CRITICAL"
            ))
            maxSeverity = "CRITICAL"
        }
        
        if (currentData.deviceFingerprint != lastVerifiedData.deviceFingerprint) {
            mismatches.add(TamperMismatch(
                field = "Device Fingerprint",
                sentValue = currentData.deviceFingerprint,
                verifiedValue = lastVerifiedData.deviceFingerprint,
                severity = "CRITICAL"
            ))
            maxSeverity = "CRITICAL"
        }
        
        if (currentData.bootloader != lastVerifiedData.bootloader) {
            mismatches.add(TamperMismatch(
                field = "Bootloader",
                sentValue = currentData.bootloader,
                verifiedValue = lastVerifiedData.bootloader,
                severity = "CRITICAL"
            ))
            maxSeverity = "CRITICAL"
        }
        
        if (currentData.processor != lastVerifiedData.processor) {
            mismatches.add(TamperMismatch(
                field = "Processor",
                sentValue = currentData.processor,
                verifiedValue = lastVerifiedData.processor,
                severity = "CRITICAL"
            ))
            maxSeverity = "CRITICAL"
        }
        
        return TamperDetectionResult(
            isTampered = mismatches.isNotEmpty(),
            mismatches = mismatches,
            severity = maxSeverity
        )
    }
    
    /**
     * Create registration payload from collected data
     * device_type is ALWAYS "phone"
     */
    fun createRegistrationPayload(
        data: HeartbeatData,
        deviceId: String,
        loanNumber: String
    ): com.example.deviceowner.data.api.DeviceRegistrationPayload {
        // Convert HeartbeatData to UnifiedHeartbeatData
        val heartbeatData = com.example.deviceowner.data.models.UnifiedHeartbeatData(
            deviceId = deviceId,
            serialNumber = data.serialNumber,
            androidId = data.androidId,
            deviceFingerprint = data.deviceFingerprint,
            manufacturer = data.manufacturer,
            model = data.model,
            bootloader = data.bootloader,
            processor = data.processor,
            deviceImeis = data.deviceImeis,
            platform = data.platform,
            osVersion = data.osVersion,
            sdkVersion = data.sdkVersion,
            buildNumber = data.buildNumber,
            securityPatchLevel = data.securityPatchLevel,
            installedRam = data.installedRam,
            totalStorage = data.totalStorage,
            machineName = data.machineName,
            isDeviceRooted = data.isDeviceRooted,
            isBootloaderUnlocked = data.isBootloaderUnlocked,
            isCustomRom = data.isCustomROM,
            isUsbDebuggingEnabled = data.isUSBDebuggingEnabled,
            isDeveloperModeEnabled = data.isDeveloperModeEnabled,
            installedAppsHash = data.installedAppsHash,
            systemPropertiesHash = data.systemPropertiesHash,
            tamperSeverity = data.tamperSeverity,
            tamperFlags = data.tamperFlags,
            timestamp = data.timestamp,
            loanNumber = loanNumber,
            batteryLevel = data.batteryLevel,
            systemUptime = data.systemUptime,
            latitude = data.latitude.toDoubleOrNull() ?: 0.0,
            longitude = data.longitude.toDoubleOrNull() ?: 0.0,
            loanStatus = data.loanStatus,
            isOnline = data.isOnline,
            isTrusted = data.isTrusted,
            isLocked = data.isLocked
        )
        
        return com.example.deviceowner.data.api.DeviceRegistrationPayload(
            device_id = deviceId,
            serial_number = data.serialNumber,
            device_type = data.deviceType,
            manufacturer = data.manufacturer,
            system_type = "Android",
            model = data.model,
            platform = data.platform,
            os_version = data.osVersion,
            os_edition = data.osEdition,
            processor = data.processor,
            installed_ram = data.installedRam,
            total_storage = data.totalStorage,
            build_number = data.buildNumber,
            sdk_version = data.sdkVersion,
            device_imeis = data.deviceImeis.joinToString(","),
            loan_number = loanNumber,
            machine_name = data.machineName,
            android_id = data.androidId,
            device_fingerprint = data.deviceFingerprint,
            bootloader = data.bootloader,
            security_patch_level = data.securityPatchLevel,
            system_uptime = data.systemUptime,
            installed_apps_hash = data.installedAppsHash,
            system_properties_hash = data.systemPropertiesHash,
            is_device_rooted = data.isDeviceRooted,
            is_usb_debugging_enabled = data.isUSBDebuggingEnabled,
            is_developer_mode_enabled = data.isDeveloperModeEnabled,
            is_bootloader_unlocked = data.isBootloaderUnlocked,
            is_custom_rom = data.isCustomROM,
            latitude = data.latitude.toDoubleOrNull() ?: 0.0,
            longitude = data.longitude.toDoubleOrNull() ?: 0.0,
            tamper_severity = data.tamperSeverity,
            tamper_flags = data.tamperFlags.joinToString(","),
            battery_level = data.batteryLevel,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Data class for heartbeat information
 * Aligned with heartbeat JSON schema
 */
data class HeartbeatData(
    val deviceId: String,
    val timestamp: Long,
    val serialNumber: String,
    val deviceType: String,
    val manufacturer: String,
    val model: String,
    val platform: String,
    val osVersion: String,
    val osEdition: String,
    val processor: String,
    val installedRam: String,
    val totalStorage: String,
    val machineName: String,
    val macAddress: String,
    val buildNumber: Int,
    val sdkVersion: Int,
    val deviceImeis: List<String>,
    val loanStatus: String,
    val isOnline: Boolean,
    val isTrusted: Boolean,
    val isLocked: Boolean,
    val androidId: String,
    val deviceFingerprint: String,
    val bootloader: String,
    val securityPatchLevel: String,
    val systemUptime: Long,
    val installedAppsHash: String,
    val systemPropertiesHash: String,
    val isDeviceRooted: Boolean,
    val isUSBDebuggingEnabled: Boolean,
    val isDeveloperModeEnabled: Boolean,
    val isBootloaderUnlocked: Boolean,
    val isCustomROM: Boolean,
    val latitude: String,
    val longitude: String,
    val tamperSeverity: String,
    val tamperFlags: List<String>,
    val batteryLevel: Int
)

/**
 * Data class for detected changes
 */
data class DataChange(
    val field: String,
    val previousValue: String,
    val currentValue: String,
    val severity: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tamper mismatch between sent and verified data
 */
data class TamperMismatch(
    val field: String,
    val sentValue: String,
    val verifiedValue: String,
    val severity: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of tamper detection comparison
 */
data class TamperDetectionResult(
    val isTampered: Boolean,
    val mismatches: List<TamperMismatch>,
    val severity: String,
    val timestamp: Long = System.currentTimeMillis()
)
