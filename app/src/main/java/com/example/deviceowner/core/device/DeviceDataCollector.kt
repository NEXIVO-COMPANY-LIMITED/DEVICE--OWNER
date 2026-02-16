package com.example.deviceowner.core.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.deviceowner.core.frp.manager.FrpManager
import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest
import com.example.deviceowner.data.models.registration.DeviceRegistrationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.File
import java.security.MessageDigest

/**
 * Enhanced Device Data Collector for Device Owner Apps.
 * Specifically optimized to retrieve REAL IMEI and Serial Number
 * using privileged system access.
 */
class DeviceDataCollector(private val context: Context) {
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * Collects all device data into a SINGLE FLAT MAP for Django compatibility.
     * This avoids the HTTP 400 error caused by nested objects.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun collectFlattenedRegistrationData(loanNumber: String): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>()

        // 1. Basic Info
        data["loan_number"] = loanNumber
        data["android_id"] = getAndroidId()

        // 2. Hardware Info (Flattened)
        val serial = getRealSerialNumber()
        data["serial"] = serial
        data["manufacturer"] = Build.MANUFACTURER
        data["model"] = Build.MODEL
        data["brand"] = Build.BRAND
        data["product"] = Build.PRODUCT
        data["device"] = Build.DEVICE
        data["board"] = Build.BOARD
        data["hardware"] = Build.HARDWARE
        data["fingerprint"] = Build.FINGERPRINT
        data["bootloader"] = if (Build.BOOTLOADER.length > 100) Build.BOOTLOADER.substring(0, 100) else Build.BOOTLOADER

        // 3. Android Info (Flattened)
        data["version_release"] = Build.VERSION.RELEASE
        data["version_sdk_int"] = Build.VERSION.SDK_INT
        data["security_patch"] = (Build.VERSION.SECURITY_PATCH ?: "unknown")

        // 4. IMEI Info (Flattened)
        val imeis = getRealImeis()
        data["phone_count"] = telephonyManager.phoneCount
        if (imeis.isNotEmpty()) {
            data["imei1"] = imeis[0]
            if (imeis.size > 1) data["imei2"] = imeis[1]
            data["device_imeis"] = imeis.joinToString(",") // For backup
        }

        // 5. Storage Info (Flattened)
        data["total_storage"] = getTotalStorage()
        data["installed_ram"] = getInstalledRam()

        // 6. Security Info
        data["is_device_rooted"] = false
        data["integrity"] = "passed"
        data["package_name"] = context.packageName

        Log.d("Collector", "✅ Collected FLAT registration data for Django: $data")
        return data
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun collectDeviceData(loanNumber: String): DeviceRegistrationRequest {
        // Keeping original for backward compatibility with local DB entities
        return DeviceRegistrationRequest(
            loanNumber = loanNumber,
            deviceId = null,
            deviceInfo = buildDeviceInfo(),
            androidInfo = buildAndroidInfo(),
            imeiInfo = buildImeiInfo(),
            storageInfo = buildStorageInfo(),
            locationInfo = getLocationInfo(),
            appInfo = mapOf("package_name" to context.packageName),
            securityInfo = mapOf("is_device_rooted" to false),
            systemIntegrity = mapOf("integrity" to "passed")
        )
    }

    /**
     * Collect device data for heartbeat (same structure as registration, no loan_number).
     * CRITICAL: Includes serial_number for backend comparison against registration baseline.
     * deviceId is passed for logging; heartbeat body does not include device_id (it's in URL).
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    fun collectHeartbeatData(deviceId: String): HeartbeatRequest {
        Log.d("Collector", "🔍 Collecting heartbeat data for device: $deviceId")
        
        try {
            val deviceInfo = buildDeviceInfo().toMutableMap()
            // CRITICAL: Add serial_number to deviceInfo for backend comparison
            deviceInfo["serial_number"] = getRealSerialNumber()
            
            val androidInfo = buildAndroidInfo()
            val imeiInfo = buildImeiInfo()
            val storageInfo = buildStorageInfo()
            
            Log.d("Collector", "✅ Heartbeat data collected:")
            Log.d("Collector", "   - Serial: ${deviceInfo["serial_number"]}")
            Log.d("Collector", "   - Model: ${deviceInfo["model"]}")
            Log.d("Collector", "   - Manufacturer: ${deviceInfo["manufacturer"]}")
            Log.d("Collector", "   - IMEIs: ${imeiInfo["device_imeis"]}")
            Log.d("Collector", "   - Storage: ${storageInfo["total_storage"]}")
            Log.d("Collector", "   - RAM: ${storageInfo["installed_ram"]}")
            Log.d("Collector", "   - Android: ${androidInfo["version_release"]}")
            
            val deviceTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())
            
            val locationInfo = getLocationInfo()
            val securityInfo = buildSecurityInfo()
            val imeiList = (imeiInfo["device_imeis"] as? List<*>)?.map { it?.toString() ?: "NO_IMEI_FOUND" }
                ?: listOf("NO_IMEI_FOUND")
            val serialNum = getRealSerialNumber()
            val lat = (locationInfo["latitude"] as? Number)?.toDouble()
            val lon = (locationInfo["longitude"] as? Number)?.toDouble()
            // Sanitize coords so server never gets NaN/Infinity (can cause 500 when storing JSON).
            val safeLat = if (lat != null && !lat.isNaN() && lat.isFinite()) lat else null
            val safeLon = if (lon != null && !lon.isNaN() && lon.isFinite()) lon else null
            // Security info: only keys Django uses; avoid frp_hours_remaining etc. that might be non-JSON-safe.
            val heartbeatSecurityInfo = mapOf<String, Any?>(
                "is_device_rooted" to (securityInfo["is_device_rooted"] == true),
                "is_usb_debugging_enabled" to (securityInfo["is_usb_debugging_enabled"] == true),
                "is_developer_mode_enabled" to (securityInfo["is_developer_mode_enabled"] == true),
                "is_bootloader_unlocked" to (securityInfo["is_bootloader_unlocked"] == true),
                "is_custom_rom" to (securityInfo["is_custom_rom"] == true)
            )
            val bootloaderVal = if (Build.BOOTLOADER.length > 100) Build.BOOTLOADER.substring(0, 100) else Build.BOOTLOADER
            return HeartbeatRequest(
                heartbeatTimestamp = deviceTimestamp,
                deviceId = null, // Do not send device_id in body; it is in the URL only (Django expects this).
                serialNumber = serialNum,
                serial = serialNum,
                fingerprint = Build.FINGERPRINT,
                bootloader = bootloaderVal,
                model = deviceInfo["model"] as? String,
                manufacturer = deviceInfo["manufacturer"] as? String,
                androidId = deviceInfo["android_id"] as? String,
                deviceImeis = imeiList,
                installedRam = storageInfo["installed_ram"] as? String,
                totalStorage = storageInfo["total_storage"] as? String,
                latitude = safeLat,
                longitude = safeLon,
                isDeviceRooted = securityInfo["is_device_rooted"] as? Boolean,
                isUsbDebuggingEnabled = securityInfo["is_usb_debugging_enabled"] as? Boolean,
                isDeveloperModeEnabled = securityInfo["is_developer_mode_enabled"] as? Boolean,
                isBootloaderUnlocked = securityInfo["is_bootloader_unlocked"] as? Boolean,
                isCustomRom = securityInfo["is_custom_rom"] as? Boolean,
                sdkVersion = (androidInfo["version_sdk_int"] as? String)?.toIntOrNull(),
                osVersion = androidInfo["version_release"] as? String,
                securityPatchLevel = androidInfo["security_patch"] as? String,
                deviceInfo = deviceInfo,
                androidInfo = androidInfo,
                imeiInfo = imeiInfo,
                storageInfo = storageInfo,
                locationInfo = locationInfo,
                securityInfo = heartbeatSecurityInfo,
                systemIntegrity = mapOf("integrity" to "passed"),
                appInfo = mapOf("package_name" to context.packageName)
            )
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error collecting heartbeat data: ${e.message}", e)
            throw e
        }
    }
    
    private fun buildAndroidInfo(): Map<String, String> {
        return try {
            mapOf(
                "version_release" to Build.VERSION.RELEASE,
                "version_sdk_int" to Build.VERSION.SDK_INT.toString(),
                "security_patch" to (Build.VERSION.SECURITY_PATCH ?: "unknown")
            ).also {
                Log.d("Collector", "✅ Android info collected: release=${it["version_release"]}, sdk=${it["version_sdk_int"]}")
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error building Android info: ${e.message}", e)
            mapOf(
                "version_release" to Build.VERSION.RELEASE,
                "version_sdk_int" to Build.VERSION.SDK_INT.toString(),
                "security_patch" to "unknown"
            )
        }
    }
    
    private fun getLocationInfo(): Map<String, Any?> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return mapOf("latitude" to 0.0, "longitude" to 0.0, "has_location" to false)
        }
        return try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val (lat, lon) = runBlocking {
                withTimeoutOrNull(10_000L) {
                    suspendCancellableCoroutine { cont ->
                        val cts = CancellationTokenSource()
                        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                            .addOnSuccessListener { location ->
                                if (location != null) cont.resume(Pair(location.latitude, location.longitude))
                                else cont.resume(Pair(0.0, 0.0))
                            }
                            .addOnFailureListener { cont.resume(Pair(0.0, 0.0)) }
                        cont.invokeOnCancellation { cts.cancel() }
                    }
                } ?: Pair(0.0, 0.0)
            }
            mapOf("latitude" to lat, "longitude" to lon, "has_location" to (lat != 0.0 || lon != 0.0))
        } catch (e: Exception) {
            mapOf("latitude" to 0.0, "longitude" to 0.0, "has_location" to false)
        }
    }

    private fun buildDeviceInfo(): Map<String, Any?> {
        return try {
            mapOf(
                "android_id" to getAndroidId(),
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "serial" to getRealSerialNumber(),
                "fingerprint" to Build.FINGERPRINT
            ).also {
                Log.d("Collector", "✅ Device info collected: model=${it["model"]}, mfg=${it["manufacturer"]}")
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error building device info: ${e.message}", e)
            mapOf(
                "android_id" to "UNKNOWN",
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "serial" to "UNKNOWN_SERIAL",
                "fingerprint" to Build.FINGERPRINT
            )
        }
    }
    
    private fun buildImeiInfo(): Map<String, Any?> {
        val imeis = getRealImeis()
        val info = mutableMapOf<String, Any?>()
        
        try {
            info["phone_count"] = telephonyManager.phoneCount
            
            // Send device_imeis as List so Django comparison matches registration (same format as registration)
            if (imeis.isNotEmpty() && imeis[0] != "NO_IMEI_FOUND") {
                info["imei1"] = imeis.getOrNull(0)
                if (imeis.size > 1) {
                    info["imei2"] = imeis.getOrNull(1)
                }
                info["device_imeis"] = imeis
                Log.d("Collector", "✅ IMEIs added to heartbeat: $imeis")
            } else {
                info["device_imeis"] = listOf("NO_IMEI_FOUND")
                Log.w("Collector", "⚠️ No valid IMEIs - using NO_IMEI_FOUND")
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error building IMEI info: ${e.message}", e)
            info["device_imeis"] = listOf("NO_IMEI_FOUND")
        }

        return info
    }
    
    private fun getRealSerialNumber(): String {
        return try {
            val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
            
            if (!serial.isNullOrBlank() && serial != "unknown") {
                Log.d("Collector", "✅ Serial Number: $serial")
                serial
            } else {
                Log.w("Collector", "⚠️ Serial number is empty or unknown")
                "UNKNOWN_SERIAL"
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error reading serial number: ${e.message}", e)
            "UNKNOWN_SERIAL"
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getRealImeis(): List<String> {
        val imeis = mutableListOf<String>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (i in 0 until telephonyManager.phoneCount) {
                    try {
                        val imei = telephonyManager.getImei(i)
                        if (!imei.isNullOrBlank()) {
                            imeis.add(imei)
                            Log.d("Collector", "✅ IMEI[$i]: $imei")
                        }
                    } catch (e: Exception) {
                        Log.w("Collector", "⚠️ Could not get IMEI[$i]: ${e.message}")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val deviceId = telephonyManager.deviceId
                if (!deviceId.isNullOrBlank()) {
                    imeis.add(deviceId)
                    Log.d("Collector", "✅ IMEI (legacy): $deviceId")
                }
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ IMEI extraction failed: ${e.message}", e)
        }
        
        if (imeis.isEmpty()) {
            Log.w("Collector", "⚠️ No IMEIs found - device may not have cellular capability")
            imeis.add("NO_IMEI_FOUND")
        }
        
        return imeis
    }

    private fun getAndroidId(): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank()) {
                Log.d("Collector", "✅ Android ID: $androidId")
                androidId
            } else {
                Log.w("Collector", "⚠️ Android ID is empty")
                "unknown"
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error reading Android ID: ${e.message}")
            "unknown"
        }
    }

    private fun buildSecurityInfo(): Map<String, Any?> {
        // Collect ACTUAL security state – same source as tamper detector, so heartbeat matches tamper
        val isDeveloperMode = isDeveloperOptionsEnabled()
        val isUsbDebug = isUsbDebuggingEnabled()
        val isBootloaderUnlocked = isBootloaderUnlockedCheck()
        val isRooted = isDeviceRootedCheck()
        val isCustomRom = isCustomRomCheck()
        val base = mapOf(
            "is_device_rooted" to isRooted,
            "is_usb_debugging_enabled" to isUsbDebug,
            "is_developer_mode_enabled" to isDeveloperMode,
            "is_bootloader_unlocked" to isBootloaderUnlocked,
            "is_custom_rom" to isCustomRom
        )
        return try {
            val frpStatus = FrpManager(context).getFrpStatus()
            base + mapOf(
                "frp_enabled" to frpStatus.enabled,
                "frp_fully_activated" to frpStatus.fullyActivated,
                "frp_hours_remaining" to frpStatus.hoursRemaining,
                "frp_gms_available" to frpStatus.gmsAvailable
            )
        } catch (e: Exception) {
            base
        }
    }

    private fun isDeveloperOptionsEnabled(): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    } catch (_: Exception) { false }

    private fun isUsbDebuggingEnabled(): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    } catch (_: Exception) { false }

    private fun isBootloaderUnlockedCheck(): Boolean = try {
        com.example.deviceowner.security.enforcement.bootloader.BootloaderLockEnforcer(context).isBootloaderUnlocked()
    } catch (_: Exception) { false }

    private fun isDeviceRootedCheck(): Boolean = try {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/local/su", "/data/local/bin/su", "/system/app/Superuser.apk")
        paths.any { File(it).exists() } || (Build.TAGS?.contains("test-keys") == true)
    } catch (_: Exception) { false }

    private fun isCustomRomCheck(): Boolean = try {
        Build.TAGS?.contains("test-keys") == true || Build.FINGERPRINT?.contains("generic") == true
    } catch (_: Exception) { false }

    private fun buildStorageInfo(): Map<String, String> {
        return try {
            val totalStorage = getTotalStorage()
            val installedRam = getInstalledRam()
            mapOf(
                "total_storage" to totalStorage,
                "installed_ram" to installedRam
            ).also {
                Log.d("Collector", "✅ Storage info collected: storage=$totalStorage, ram=$installedRam")
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error building storage info: ${e.message}", e)
            mapOf(
                "total_storage" to "Unknown",
                "installed_ram" to "Unknown"
            )
        }
    }

    private fun getInstalledRam(): String {
        return try {
            val memInfo = File("/proc/meminfo").readText()
            val totalMem = memInfo.lines().find { it.startsWith("MemTotal:") }
                ?.replace(Regex("[^0-9]"), "")?.toLongOrNull()
            if (totalMem != null) {
                val gb = totalMem / 1024 / 1024
                "${gb}GB"
            } else {
                Log.w("Collector", "⚠️ Could not parse RAM from /proc/meminfo")
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error reading RAM: ${e.message}")
            "Unknown"
        }
    }

    private fun getTotalStorage(): String {
        return try {
            val internalStorage = File(context.filesDir.absolutePath)
            val totalBytes = internalStorage.totalSpace
            if (totalBytes > 0) {
                val gb = totalBytes / 1024 / 1024 / 1024
                "${gb}GB"
            } else {
                Log.w("Collector", "⚠️ Could not determine total storage")
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e("Collector", "❌ Error reading storage: ${e.message}")
            "Unknown"
        }
    }
}
