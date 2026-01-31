package com.example.deviceowner.core.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.deviceowner.data.models.DeviceRegistrationRequest
import com.example.deviceowner.data.models.HeartbeatRequest
import java.io.File
import java.security.MessageDigest

class DeviceDataCollector(private val context: Context) {
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun collectDeviceData(loanNumber: String): DeviceRegistrationRequest {
        return DeviceRegistrationRequest(
            loanNumber = loanNumber,
            deviceId = null,  // Will be assigned by server
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidId = getAndroidId(),
            buildId = Build.ID,
            buildType = Build.TYPE,
            fingerprint = Build.FINGERPRINT,
            bootloader = if (Build.BOOTLOADER.length > 100) Build.BOOTLOADER.substring(0, 100) else Build.BOOTLOADER,
            deviceInfo = mapOf(
                "device_id" to null,
                "android_id" to getAndroidId(),
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "brand" to Build.BRAND,
                "product" to Build.PRODUCT,
                "device" to Build.DEVICE,
                "board" to Build.BOARD,
                "hardware" to Build.HARDWARE,
                "build_id" to Build.ID,
                "build_type" to Build.TYPE,
                "build_tags" to Build.TAGS,
                "build_time" to Build.TIME,
                "build_user" to Build.USER,
                "build_host" to Build.HOST,
                "fingerprint" to Build.FINGERPRINT,
                "bootloader" to if (Build.BOOTLOADER.length > 100) Build.BOOTLOADER.substring(0, 100) else Build.BOOTLOADER
            ),
            androidInfo = buildAndroidInfo(),
            imeiInfo = buildImeiInfo(),
            storageInfo = buildStorageInfo(),
            locationInfo = buildLocationInfo(),
            appInfo = buildAppInfo(),
            securityInfo = buildSecurityInfo(),
            systemIntegrity = buildSystemIntegrity()
        )
    }
    
    /**
     * Collect device data for heartbeat
     * Uses the same format as registration for consistency
     * The device_id should be obtained from registration response
     * NOTE: loan_number is NOT included in heartbeat (only in registration)
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun collectHeartbeatData(
        deviceId: String?
    ): HeartbeatRequest {
        return HeartbeatRequest(
            deviceId = deviceId,
            androidId = getAndroidId(),
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            product = Build.PRODUCT,
            device = Build.DEVICE,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            buildId = Build.ID,
            buildType = Build.TYPE,
            buildTags = Build.TAGS,
            buildTime = Build.TIME,
            buildUser = Build.USER,
            buildHost = Build.HOST,
            fingerprint = Build.FINGERPRINT,
            bootloader = if (Build.BOOTLOADER.length > 100) Build.BOOTLOADER.substring(0, 100) else Build.BOOTLOADER,
            androidInfo = buildAndroidInfo(),
            imeiInfo = buildImeiInfo(),
            storageInfo = buildStorageInfo(),
            locationInfo = buildLocationInfo(),
            appInfo = buildAppInfo(),
            securityInfo = buildSecurityInfo(),
            systemIntegrity = buildSystemIntegrity()
        )
    }
    
    private fun buildAndroidInfo(): Map<String, String> {
        return mapOf(
            "version_release" to Build.VERSION.RELEASE,
            "version_sdk_int" to Build.VERSION.SDK_INT.toString(),
            "version_codename" to Build.VERSION.CODENAME,
            "version_incremental" to Build.VERSION.INCREMENTAL,
            "security_patch" to (Build.VERSION.SECURITY_PATCH ?: "unknown")
        )
    }
    
    private fun buildImeiInfo(): Map<String, String> {
        return try {
            val phoneCount = telephonyManager.phoneCount
            mapOf("phone_count" to phoneCount.toString())
        } catch (e: Exception) {
            mapOf("phone_count" to "1")
        }
    }
    
    private fun buildStorageInfo(): Map<String, String> {
        return mapOf(
            "total_storage" to getTotalStorage(),
            "installed_ram" to getInstalledRam()
        )
    }
    
    private fun buildLocationInfo(): Map<String, String> {
        val locationMap = mutableMapOf<String, String>()
        
        val latitude = getLatitude()
        val longitude = getLongitude()
        
        if (latitude != null) {
            locationMap["latitude"] = latitude.toString()
        }
        if (longitude != null) {
            locationMap["longitude"] = longitude.toString()
        }
        
        return locationMap
    }
    
    private fun buildAppInfo(): Map<String, String> {
        return mapOf(
            "package_name" to context.packageName,
            "data_dir" to context.dataDir.absolutePath
        )
    }
    
    private fun buildSecurityInfo(): Map<String, String> {
        return mapOf(
            "is_device_rooted" to isDeviceRooted().toString(),
            "is_usb_debugging_enabled" to isUsbDebuggingEnabled().toString(),
            "is_developer_mode_enabled" to isDeveloperModeEnabled().toString(),
            "is_bootloader_unlocked" to isBootloaderUnlocked().toString(),
            "is_custom_rom" to isCustomRom().toString()
        )
    }
    
    private fun buildSystemIntegrity(): Map<String, String> {
        return mapOf(
            "installed_apps_hash" to getInstalledAppsHash(),
            "system_properties_hash" to getSystemPropertiesHash()
        )
    }
    
    private fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    private fun getSerialNumber(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    Build.getSerial()
                } else null
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getSystemType(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    private fun getOsEdition(): String {
        return "Android ${Build.VERSION.RELEASE}"
    }
    
    private fun getInstalledRam(): String {
        return try {
            val memInfo = File("/proc/meminfo").readText()
            val totalMem = memInfo.lines()
                .find { it.startsWith("MemTotal:") }
                ?.replace(Regex("[^0-9]"), "")
                ?.toLongOrNull()
            
            if (totalMem != null) {
                "${totalMem / 1024 / 1024} GB"
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getTotalStorage(): String {
        return try {
            val internalStorage = File(context.filesDir.absolutePath)
            val totalBytes = internalStorage.totalSpace
            "${totalBytes / 1024 / 1024 / 1024} GB"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getMachineName(): String {
        return "${Build.MANUFACTURER}_${Build.MODEL}_${Build.DEVICE}"
    }
    
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getDeviceImeis(): List<String>? {
        return try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val imeis = mutableListOf<String>()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // For dual SIM devices
                    for (i in 0 until telephonyManager.phoneCount) {
                        val imei = telephonyManager.getImei(i)
                        if (!imei.isNullOrEmpty()) {
                            imeis.add(imei)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val imei = telephonyManager.deviceId
                    if (!imei.isNullOrEmpty()) {
                        imeis.add(imei)
                    }
                }
                
                if (imeis.isNotEmpty()) imeis else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getAndroidId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        // Ensure Android ID doesn't exceed 100 characters for data submission compatibility
        return if (androidId.length > 100) androidId.substring(0, 100) else androidId
    }
    
    private fun getInstalledAppsHash(): String {
        return try {
            val packageManager = context.packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val appNames = installedApps.map { it.packageName }.sorted().joinToString(",")
            val hash = hashString(appNames)
            // Ensure hash is exactly 64 characters for server compatibility
            if (hash.length > 64) hash.substring(0, 64) else hash
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getSystemPropertiesHash(): String {
        return try {
            val properties = listOf(
                Build.FINGERPRINT,
                Build.HARDWARE,
                Build.PRODUCT,
                Build.DEVICE,
                Build.BOARD,
                Build.BRAND,
                Build.MODEL,
                Build.BOOTLOADER,
                Build.RADIO
            ).joinToString(",")
            val hash = hashString(properties)
            // Ensure hash is exactly 64 characters for server compatibility
            if (hash.length > 64) hash.substring(0, 64) else hash
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun isDeviceRooted(): Boolean {
        return try {
            // Check for common root indicators
            val rootPaths = arrayOf(
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
            
            rootPaths.any { File(it).exists() }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isUsbDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isDeveloperModeEnabled(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isBootloaderUnlocked(): Boolean {
        return try {
            // This is a simplified check - actual implementation may vary by manufacturer
            val bootloaderStatus = Build.TAGS
            bootloaderStatus?.contains("test-keys") == true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isCustomRom(): Boolean {
        return try {
            // Check for custom ROM indicators
            val customRomIndicators = listOf(
                "lineage", "cyanogen", "paranoid", "resurrection", "carbon", "slim", "omni"
            )
            
            val buildTags = Build.TAGS?.lowercase() ?: ""
            val buildType = Build.TYPE?.lowercase() ?: ""
            val buildUser = Build.USER?.lowercase() ?: ""
            
            customRomIndicators.any { indicator ->
                buildTags.contains(indicator) || buildType.contains(indicator) || buildUser.contains(indicator)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getLatitude(): Double? {
        return try {
            Log.d("DeviceDataCollector", "Attempting to collect latitude...")
            
            val fineLocationGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseLocationGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            Log.d("DeviceDataCollector", "Location permissions - Fine: $fineLocationGranted, Coarse: $coarseLocationGranted")
            
            if (fineLocationGranted || coarseLocationGranted) {
                // Try multiple providers for best location
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                var bestLocation: android.location.Location? = null
                var bestAccuracy = Float.MAX_VALUE
                
                for (provider in providers) {
                    try {
                        if (locationManager.isProviderEnabled(provider)) {
                            val location = locationManager.getLastKnownLocation(provider)
                            if (location != null) {
                                val locationAge = System.currentTimeMillis() - location.time
                                val maxAge = 24 * 60 * 60 * 1000L // 24 hours
                                
                                if (locationAge <= maxAge) {
                                    if (location.hasAccuracy() && location.accuracy < bestAccuracy) {
                                        bestLocation = location
                                        bestAccuracy = location.accuracy
                                    } else if (bestLocation == null) {
                                        bestLocation = location
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("DeviceDataCollector", "Error getting location from $provider: ${e.message}")
                    }
                }
                
                bestLocation?.latitude?.let { lat ->
                    val roundedLat = kotlin.math.round(lat * 1000000.0) / 1000000.0
                    Log.i("DeviceDataCollector", "✅ Latitude collected: $roundedLat")
                    roundedLat
                }
            } else {
                Log.w("DeviceDataCollector", "❌ No location permissions granted")
                null
            }
        } catch (e: Exception) {
            Log.e("DeviceDataCollector", "Error getting latitude: ${e.message}")
            null
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getLongitude(): Double? {
        return try {
            Log.d("DeviceDataCollector", "Attempting to collect longitude...")
            
            val fineLocationGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseLocationGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            Log.d("DeviceDataCollector", "Location permissions - Fine: $fineLocationGranted, Coarse: $coarseLocationGranted")
            
            if (fineLocationGranted || coarseLocationGranted) {
                // Try multiple providers for best location
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                var bestLocation: android.location.Location? = null
                var bestAccuracy = Float.MAX_VALUE
                
                for (provider in providers) {
                    try {
                        if (locationManager.isProviderEnabled(provider)) {
                            val location = locationManager.getLastKnownLocation(provider)
                            if (location != null) {
                                val locationAge = System.currentTimeMillis() - location.time
                                val maxAge = 24 * 60 * 60 * 1000L // 24 hours
                                
                                if (locationAge <= maxAge) {
                                    if (location.hasAccuracy() && location.accuracy < bestAccuracy) {
                                        bestLocation = location
                                        bestAccuracy = location.accuracy
                                    } else if (bestLocation == null) {
                                        bestLocation = location
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("DeviceDataCollector", "Error getting location from $provider: ${e.message}")
                    }
                }
                
                bestLocation?.longitude?.let { lng ->
                    val roundedLng = kotlin.math.round(lng * 1000000.0) / 1000000.0
                    Log.i("DeviceDataCollector", "✅ Longitude collected: $roundedLng")
                    roundedLng
                }
            } else {
                Log.w("DeviceDataCollector", "❌ No location permissions granted")
                null
            }
        } catch (e: Exception) {
            Log.e("DeviceDataCollector", "Error getting longitude: ${e.message}")
            null
        }
    }
    
    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown"
        }
    }
}