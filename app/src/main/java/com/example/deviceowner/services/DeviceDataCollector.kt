package com.example.deviceowner.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.deviceowner.data.models.DeviceRegistrationRequest
import java.io.File
import java.security.MessageDigest

class DeviceDataCollector(private val context: Context) {
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun collectDeviceData(loanNumber: String): DeviceRegistrationRequest {
        // Try to grant Device Owner permissions if available, but don't fail if not
        try {
            grantDeviceOwnerPermissions()
        } catch (e: Exception) {
            Log.i("DeviceDataCollector", "Device Owner permissions not available - collecting basic data only: ${e.message}")
        }
        
        return DeviceRegistrationRequest(
            loanNumber = loanNumber,
            deviceInfo = buildDeviceInfo(),
            androidInfo = buildAndroidInfo(),
            imeiInfo = buildImeiInfo(),
            storageInfo = buildStorageInfo(),
            locationInfo = buildLocationInfo(),
            securityInfo = buildSecurityInfo(),
            systemIntegrity = buildSystemIntegrity(),
            appInfo = buildAppInfo()
        )
    }
    
    /**
     * Grant required permissions to this app using Device Owner privileges (if available)
     */
    private fun grantDeviceOwnerPermissions() {
        try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, com.example.deviceowner.receivers.AdminReceiver::class.java)
            
            // Check if we are device owner (optional - don't fail if not)
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.i("DeviceDataCollector", "App is not Device Owner - will collect basic data without special permissions")
                return
            }
            
            Log.d("DeviceDataCollector", "App is Device Owner - granting required permissions")
            
            // Grant READ_PHONE_STATE permission to self
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    devicePolicyManager.setPermissionGrantState(
                        adminComponent,
                        context.packageName,
                        Manifest.permission.READ_PHONE_STATE,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                    Log.d("DeviceDataCollector", "READ_PHONE_STATE permission granted via Device Owner")
                } catch (e: Exception) {
                    Log.w("DeviceDataCollector", "Could not grant READ_PHONE_STATE permission: ${e.message}")
                }
                
                // Grant location permissions to self
                try {
                    devicePolicyManager.setPermissionGrantState(
                        adminComponent,
                        context.packageName,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                    devicePolicyManager.setPermissionGrantState(
                        adminComponent,
                        context.packageName,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                    
                    // Grant background location permission for Android 10+ (API 29+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            devicePolicyManager.setPermissionGrantState(
                                adminComponent,
                                context.packageName,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                            )
                            Log.d("DeviceDataCollector", "Background location permission granted via Device Owner")
                        } catch (e: Exception) {
                            Log.w("DeviceDataCollector", "Could not grant background location permission: ${e.message}")
                        }
                    }
                    
                    Log.d("DeviceDataCollector", "Location permissions granted via Device Owner")
                } catch (e: Exception) {
                    Log.w("DeviceDataCollector", "Could not grant location permissions: ${e.message}")
                }
            } else {
                Log.w("DeviceDataCollector", "App is not Device Owner - cannot grant permissions")
            }
        } catch (e: Exception) {
            Log.e("DeviceDataCollector", "Error granting Device Owner permissions: ${e.message}")
        }
    }
    
    private fun buildAndroidInfo(): Map<String, Any?> {
        return mapOf(
            "version_release" to Build.VERSION.RELEASE,
            "version_sdk_int" to Build.VERSION.SDK_INT,
            "version_codename" to Build.VERSION.CODENAME,
            "version_incremental" to Build.VERSION.INCREMENTAL,
            "security_patch" to (Build.VERSION.SECURITY_PATCH ?: "unknown")
        )
    }
    
    private fun buildDeviceInfo(): Map<String, Any?> {
        return mapOf(
            "device_id" to null,
            "android_id" to getAndroidId(),
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "fingerprint" to Build.FINGERPRINT,
            "bootloader" to Build.BOOTLOADER,
            "serial" to getSerialNumber(),
            "machine_name" to Build.DEVICE,
            "imeis" to getDeviceImeis()
        )
    }
    
    private fun buildImeiInfo(): Map<String, Any?> {
        return try {
            val phoneCount = telephonyManager.phoneCount
            val imeis = getDeviceImeis()
            mapOf(
                "device_imeis" to imeis,
                "phone_count" to phoneCount
            )
        } catch (e: Exception) {
            mapOf(
                "device_imeis" to null,
                "phone_count" to 1
            )
        }
    }
    
    private fun buildStorageInfo(): Map<String, String> {
        return mapOf(
            "total_storage" to getTotalStorage(),
            "installed_ram" to getInstalledRam()
        )
    }
    
    private fun buildLocationInfo(): Map<String, Any?>? {
        val latitude = getLatitude()
        val longitude = getLongitude()
        
        return if (latitude != null && longitude != null) {
            mapOf(
                "latitude" to latitude,
                "longitude" to longitude
            )
        } else {
            null
        }
    }
    
    private fun buildAppInfo(): Map<String, String> {
        return mapOf(
            "package_name" to context.packageName,
            "data_dir" to context.dataDir.absolutePath
        )
    }
    
    private fun buildSecurityInfo(): Map<String, Any?> {
        return mapOf(
            "is_device_rooted" to isDeviceRooted(),
            "is_usb_debugging_enabled" to isUsbDebuggingEnabled(),
            "is_developer_mode_enabled" to isDeveloperModeEnabled(),
            "is_bootloader_unlocked" to isBootloaderUnlocked(),
            "is_custom_rom" to isCustomRom()
        )
    }
    
    private fun buildSystemIntegrity(): Map<String, String> {
        return mapOf(
            "installed_apps_hash" to getInstalledAppsHash(),
            "system_properties_hash" to getSystemPropertiesHash()
        )
    }
    
    private fun getDeviceId(): String? {
        // Return null during registration - server will assign device ID
        return null
    }
    
    private fun getSerialNumber(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Device Owner can access serial number without READ_PHONE_STATE permission
                    val serial = Build.getSerial()
                    if (!serial.isNullOrEmpty() && serial != "unknown") {
                        Log.d("DeviceDataCollector", "Serial number collected via Device Owner privilege")
                        serial
                    } else {
                        Log.i("DeviceDataCollector", "Serial number is empty or unknown - this is normal for some devices")
                        null
                    }
                } catch (e: SecurityException) {
                    Log.i("DeviceDataCollector", "Serial number access denied - this is normal for some devices: ${e.message}")
                    null
                }
            } else {
                try {
                    // For older Android versions, serial is always accessible
                    @Suppress("DEPRECATION")
                    val serial = Build.SERIAL
                    if (!serial.isNullOrEmpty() && serial != "unknown") {
                        Log.d("DeviceDataCollector", "Legacy serial number collected")
                        serial
                    } else {
                        Log.i("DeviceDataCollector", "Legacy serial number is empty or unknown")
                        null
                    }
                } catch (e: Exception) {
                    Log.i("DeviceDataCollector", "Cannot access legacy serial - this is normal: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.i("DeviceDataCollector", "Error getting serial number - this is normal for some devices: ${e.message}")
            null
        }
    }
    
    private fun getLatitude(): Double? {
        return try {
            Log.d("DeviceDataCollector", "Attempting to collect latitude...")
            
            // Check if location permissions are granted
            val fineLocationGranted = ActivityCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val coarseLocationGranted = ActivityCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d("DeviceDataCollector", "Location permissions - Fine: $fineLocationGranted, Coarse: $coarseLocationGranted")
            
            if (!fineLocationGranted && !coarseLocationGranted) {
                Log.w("DeviceDataCollector", "No location permissions granted - attempting Device Owner permission grant")
                
                // Try to grant permissions via Device Owner if available
                grantDeviceOwnerPermissions()
                
                // Re-check permissions after attempting to grant
                val fineLocationGrantedAfter = ActivityCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                val coarseLocationGrantedAfter = ActivityCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!fineLocationGrantedAfter && !coarseLocationGrantedAfter) {
                    Log.w("DeviceDataCollector", "Location permissions still not available - location data will be null")
                    return null
                }
                
                Log.d("DeviceDataCollector", "Location permissions granted via Device Owner")
            }
            
            // Try multiple location providers for better accuracy
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            
            var bestLocation: android.location.Location? = null
            var bestAccuracy = Float.MAX_VALUE
            
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        val location = locationManager.getLastKnownLocation(provider)
                        if (location != null) {
                            val locationAge = System.currentTimeMillis() - location.time
                            val maxAge = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
                            
                            if (locationAge <= maxAge) {
                                if (location.hasAccuracy() && location.accuracy < bestAccuracy) {
                                    bestLocation = location
                                    bestAccuracy = location.accuracy
                                    Log.d("DeviceDataCollector", "Better location found via $provider: accuracy=${location.accuracy}m, age=${locationAge/1000}s")
                                } else if (bestLocation == null) {
                                    bestLocation = location
                                    Log.d("DeviceDataCollector", "Location found via $provider: age=${locationAge/1000}s")
                                }
                            } else {
                                Log.d("DeviceDataCollector", "Location from $provider too old (${locationAge/1000}s), skipping")
                            }
                        } else {
                            Log.d("DeviceDataCollector", "No last known location available from $provider")
                        }
                    } else {
                        Log.d("DeviceDataCollector", "Provider $provider is not enabled")
                    }
                } catch (e: SecurityException) {
                    Log.w("DeviceDataCollector", "Security exception for provider $provider: ${e.message}")
                } catch (e: Exception) {
                    Log.w("DeviceDataCollector", "Error getting location from provider $provider: ${e.message}")
                }
            }
            
            if (bestLocation != null) {
                Log.i("DeviceDataCollector", "✅ Latitude collected successfully: ${bestLocation.latitude} (accuracy: ${if (bestLocation.hasAccuracy()) "${bestLocation.accuracy}m" else "unknown"})")
                bestLocation.latitude
            } else {
                Log.w("DeviceDataCollector", "❌ No valid location found from any provider")
                null
            }
            
        } catch (e: Exception) {
            Log.e("DeviceDataCollector", "Error getting latitude: ${e.message}", e)
            null
        }
    }
    
    private fun getLongitude(): Double? {
        return try {
            Log.d("DeviceDataCollector", "Attempting to collect longitude...")
            
            // Check if location permissions are granted
            val fineLocationGranted = ActivityCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val coarseLocationGranted = ActivityCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d("DeviceDataCollector", "Location permissions - Fine: $fineLocationGranted, Coarse: $coarseLocationGranted")
            
            if (!fineLocationGranted && !coarseLocationGranted) {
                Log.w("DeviceDataCollector", "No location permissions granted - attempting Device Owner permission grant")
                
                // Try to grant permissions via Device Owner if available
                grantDeviceOwnerPermissions()
                
                // Re-check permissions after attempting to grant
                val fineLocationGrantedAfter = ActivityCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                val coarseLocationGrantedAfter = ActivityCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!fineLocationGrantedAfter && !coarseLocationGrantedAfter) {
                    Log.w("DeviceDataCollector", "Location permissions still not available - location data will be null")
                    return null
                }
                
                Log.d("DeviceDataCollector", "Location permissions granted via Device Owner")
            }
            
            // Try multiple location providers for better accuracy
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            
            var bestLocation: android.location.Location? = null
            var bestAccuracy = Float.MAX_VALUE
            
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        val location = locationManager.getLastKnownLocation(provider)
                        if (location != null) {
                            val locationAge = System.currentTimeMillis() - location.time
                            val maxAge = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
                            
                            if (locationAge <= maxAge) {
                                if (location.hasAccuracy() && location.accuracy < bestAccuracy) {
                                    bestLocation = location
                                    bestAccuracy = location.accuracy
                                    Log.d("DeviceDataCollector", "Better location found via $provider: accuracy=${location.accuracy}m, age=${locationAge/1000}s")
                                } else if (bestLocation == null) {
                                    bestLocation = location
                                    Log.d("DeviceDataCollector", "Location found via $provider: age=${locationAge/1000}s")
                                }
                            } else {
                                Log.d("DeviceDataCollector", "Location from $provider too old (${locationAge/1000}s), skipping")
                            }
                        } else {
                            Log.d("DeviceDataCollector", "No last known location available from $provider")
                        }
                    } else {
                        Log.d("DeviceDataCollector", "Provider $provider is not enabled")
                    }
                } catch (e: SecurityException) {
                    Log.w("DeviceDataCollector", "Security exception for provider $provider: ${e.message}")
                } catch (e: Exception) {
                    Log.w("DeviceDataCollector", "Error getting location from provider $provider: ${e.message}")
                }
            }
            
            if (bestLocation != null) {
                Log.i("DeviceDataCollector", "✅ Longitude collected successfully: ${bestLocation.longitude} (accuracy: ${if (bestLocation.hasAccuracy()) "${bestLocation.accuracy}m" else "unknown"})")
                bestLocation.longitude
            } else {
                Log.w("DeviceDataCollector", "❌ No valid location found from any provider")
                null
            }
            
        } catch (e: Exception) {
            Log.e("DeviceDataCollector", "Error getting longitude: ${e.message}", e)
            null
        }
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
            val imeis = mutableListOf<String>()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // For Android 6.0+, try to get IMEI with available permissions
                try {
                    val phoneCount = telephonyManager.phoneCount
                    Log.d("DeviceDataCollector", "Device has $phoneCount SIM slots")
                    
                    if (phoneCount > 0) {
                        for (i in 0 until phoneCount) {
                            try {
                                val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    telephonyManager.getImei(i)
                                } else {
                                    @Suppress("DEPRECATION")
                                    telephonyManager.getDeviceId(i)
                                }
                                
                                if (!imei.isNullOrEmpty() && imei != "null" && imei.length >= 14) {
                                    imeis.add(imei)
                                    Log.d("DeviceDataCollector", "IMEI $i collected successfully")
                                } else {
                                    Log.i("DeviceDataCollector", "IMEI $i is empty or invalid")
                                }
                            } catch (e: SecurityException) {
                                Log.i("DeviceDataCollector", "IMEI $i access denied - normal for non-Device Owner apps: ${e.message}")
                            } catch (e: Exception) {
                                Log.i("DeviceDataCollector", "Error getting IMEI $i: ${e.message}")
                            }
                        }
                    } else {
                        Log.i("DeviceDataCollector", "Device reports 0 phone slots - may be tablet or WiFi-only device")
                    }
                } catch (e: Exception) {
                    Log.i("DeviceDataCollector", "Error accessing phone count: ${e.message}")
                }
            } else {
                try {
                    // For older Android versions
                    @Suppress("DEPRECATION")
                    val imei = telephonyManager.deviceId
                    if (!imei.isNullOrEmpty() && imei != "null" && imei.length >= 14) {
                        imeis.add(imei)
                        Log.d("DeviceDataCollector", "Legacy IMEI collected successfully")
                    } else {
                        Log.i("DeviceDataCollector", "Legacy IMEI is empty or invalid")
                    }
                } catch (e: SecurityException) {
                    Log.i("DeviceDataCollector", "Legacy IMEI access denied - normal for apps without READ_PHONE_STATE permission: ${e.message}")
                } catch (e: Exception) {
                    Log.i("DeviceDataCollector", "Error getting legacy IMEI: ${e.message}")
                }
            }
            
            if (imeis.isNotEmpty()) {
                Log.d("DeviceDataCollector", "Successfully collected ${imeis.size} valid IMEI(s)")
                imeis
            } else {
                Log.i("DeviceDataCollector", "No IMEI data collected - normal for tablets, WiFi devices, or apps without Device Owner privileges")
                null
            }
        } catch (e: Exception) {
            Log.i("DeviceDataCollector", "Error collecting IMEI data: ${e.message}")
            null
        }
    }
    
    private fun getAndroidId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    private fun getSystemType(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    
    private fun getInstalledAppsHash(): String {
        return try {
            val packageManager = context.packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val appNames = installedApps.map { it.packageName }.sorted()
            hashString(appNames.joinToString(","))
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getSystemPropertiesHash(): String {
        return try {
            val properties = listOf(
                Build.FINGERPRINT,
                Build.BOOTLOADER,
                Build.HARDWARE,
                Build.PRODUCT,
                Build.DEVICE,
                Build.BOARD,
                Build.CPU_ABI,
                Build.DISPLAY,
                Build.HOST,
                Build.ID,
                Build.USER
            )
            hashString(properties.joinToString(","))
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
                "lineage", "cyanogen", "paranoid", "aokp", "omni", "resurrection",
                "carbon", "slim", "liquid", "pac", "mahdi", "euphoria"
            )
            val buildFingerprint = Build.FINGERPRINT.lowercase()
            val buildDisplay = Build.DISPLAY.lowercase()
            val buildProduct = Build.PRODUCT.lowercase()
            
            customRomIndicators.any { 
                buildFingerprint.contains(it) || buildDisplay.contains(it) || buildProduct.contains(it)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getMacAddress(): String? {
        return try {
            // Note: MAC address access is restricted in newer Android versions
            // This will return a default value for privacy reasons
            "02:00:00:00:00:00" // Default MAC for privacy
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Collect comprehensive device data for local JSON server
     * This data is available even before device registration
     */
    suspend fun collectDeviceDataForLocalServer(): Map<String, Any?> {
        Log.d("DeviceDataCollector", "🔍 Collecting comprehensive device data for local server...")
        
        val deviceData = mutableMapOf<String, Any?>()
        
        try {
            // Basic device information
            deviceData["device_info"] = mapOf(
                "device_id" to getDeviceId(),
                "android_id" to getAndroidId(),
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "brand" to Build.BRAND,
                "product" to Build.PRODUCT,
                "device" to Build.DEVICE,
                "board" to Build.BOARD,
                "hardware" to Build.HARDWARE,
                "serial" to getSerialNumber(),
                "build_id" to Build.ID,
                "build_type" to Build.TYPE,
                "build_tags" to Build.TAGS,
                "build_time" to Build.TIME,
                "build_user" to Build.USER,
                "build_host" to Build.HOST,
                "fingerprint" to Build.FINGERPRINT,
                "bootloader" to Build.BOOTLOADER
            )
            
            // Android version information
            deviceData["android_info"] = mapOf(
                "version_release" to Build.VERSION.RELEASE,
                "version_sdk_int" to Build.VERSION.SDK_INT,
                "version_codename" to Build.VERSION.CODENAME,
                "version_incremental" to Build.VERSION.INCREMENTAL,
                "security_patch" to Build.VERSION.SECURITY_PATCH
            )
            
            // IMEI information (Device Owner can access this)
            deviceData["imei_info"] = mapOf(
                "device_imeis" to getDeviceImeis(),
                "phone_count" to telephonyManager.phoneCount
            )
            
            // Storage and memory information
            deviceData["storage_info"] = mapOf(
                "total_storage" to getTotalStorage(),
                "installed_ram" to getInstalledRam()
            )
            
            // Location information (if available)
            deviceData["location_info"] = mapOf(
                "latitude" to getLatitude(),
                "longitude" to getLongitude()
            )
            
            // App information
            deviceData["app_info"] = mapOf(
                "package_name" to context.packageName,
                "data_dir" to context.applicationInfo.dataDir,
                "source_dir" to context.applicationInfo.sourceDir
            )
            
            // Security information
            deviceData["security_info"] = mapOf(
                "is_device_rooted" to isDeviceRooted(),
                "is_usb_debugging_enabled" to isUsbDebuggingEnabled(),
                "is_developer_mode_enabled" to isDeveloperModeEnabled(),
                "is_bootloader_unlocked" to isBootloaderUnlocked(),
                "is_custom_rom" to isCustomRom()
            )
            
            // System hashes for integrity checking
            deviceData["system_integrity"] = mapOf(
                "installed_apps_hash" to getInstalledAppsHash(),
                "system_properties_hash" to getSystemPropertiesHash()
            )
            
            // Collection metadata
            deviceData["collection_metadata"] = mapOf(
                "collected_at" to System.currentTimeMillis(),
                "formatted_time" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                "collection_source" to "DeviceDataCollector",
                "data_version" to "1.0"
            )
            
            Log.d("DeviceDataCollector", "✅ Device data collection completed (${deviceData.size} categories)")
            
        } catch (e: Exception) {
            Log.e("DeviceDataCollector", "❌ Error collecting device data: ${e.message}", e)
            deviceData["collection_error"] = mapOf(
                "error" to e.message,
                "error_type" to e.javaClass.simpleName,
                "timestamp" to System.currentTimeMillis()
            )
        }
        
        return deviceData
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
    
    // ===== NEW HELPER FUNCTIONS FOR BACKEND SPEC =====
    
    private fun getSystemUptime(): Long {
        return try {
            android.os.SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } catch (e: Exception) {
            Log.w("DeviceDataCollector", "Could not get battery level: ${e.message}")
            0
        }
    }
    
    private fun getLanguage(): String {
        return try {
            java.util.Locale.getDefault().language
        } catch (e: Exception) {
            "en"
        }
    }
}