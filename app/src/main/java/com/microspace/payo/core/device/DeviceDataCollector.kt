package com.microspace.payo.core.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.microspace.payo.data.models.device.DeviceDataSnapshot
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.File
import java.util.Locale

class DeviceDataCollector(private val context: Context) {
    
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @SuppressLint("MissingPermission", "HardwareIds")
    fun collectDeviceSnapshot(): DeviceDataSnapshot {
        Log.d("Collector", "ðŸ” Collecting device data snapshot")
        
        val serial = getRealSerialNumber()
        val imeis = getRealImeis()
        val (lat, lon) = getLocationCoords()
        
        return DeviceDataSnapshot(
            serialNumber = serial,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidId = getAndroidId(),
            deviceImeis = imeis,
            installedRam = getInstalledRam(),
            totalStorage = getTotalStorage(),
            latitude = lat,
            longitude = lon,
            isDeviceRooted = isDeviceRootedCheck(),
            isUsbDebuggingEnabled = isUsbDebuggingEnabled(),
            isDeveloperModeEnabled = isDeveloperOptionsEnabled(),
            isBootloaderUnlocked = isBootloaderUnlockedCheck(),
            isCustomRom = isCustomRomCheck(),
            sdkVersion = Build.VERSION.SDK_INT,
            osVersion = Build.VERSION.RELEASE,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH ?: "unknown",
            batteryLevel = getBatteryLevel(),
            language = Locale.getDefault().language,
            deviceFingerprint = Build.FINGERPRINT,
            bootloader = if (Build.BOOTLOADER.length > 100) Build.BOOTLOADER.substring(0, 100) else Build.BOOTLOADER
        )
    }

    private fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }

    private fun getRealSerialNumber(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Build.getSerial() else Build.SERIAL
        } catch (_: Exception) { "unknown" }
    }

    @SuppressLint("MissingPermission")
    private fun getRealImeis(): List<String> {
        val imeis = mutableListOf<String>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (i in 0 until telephonyManager.phoneCount) {
                    telephonyManager.getImei(i)?.let { imeis.add(it) }
                }
            }
        } catch (_: Exception) {}
        return imeis.ifEmpty { listOf("NO_IMEI_FOUND") }
    }

    private fun getAndroidId(): String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun isDeveloperOptionsEnabled(): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    } catch (_: Exception) { false }

    private fun isUsbDebuggingEnabled(): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    } catch (_: Exception) { false }

    private fun isBootloaderUnlockedCheck(): Boolean = try {
        com.microspace.payo.security.enforcement.bootloader.BootloaderLockEnforcer(context).isBootloaderUnlocked()
    } catch (_: Exception) { false }

    private fun isDeviceRootedCheck(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/local/su", "/data/local/bin/su")
        return paths.any { File(it).exists() }
    }

    private fun isCustomRomCheck(): Boolean = Build.TAGS?.contains("test-keys") == true

    private fun getInstalledRam(): String {
        return try {
            val memInfo = File("/proc/meminfo").readLines().firstOrNull { it.startsWith("MemTotal:") }
            val totalKb = memInfo?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: 0L
            "${totalKb / 1024 / 1024}GB"
        } catch (_: Exception) { "unknown" }
    }

    private fun getTotalStorage(): String {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val bytes = stat.totalBytes
            "${bytes / 1024 / 1024 / 1024}GB"
        } catch (_: Exception) { "unknown" }
    }

    private fun getLocationCoords(): Pair<Double?, Double?> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Pair(null, null)
        }
        return runBlocking {
            try {
                val fused = LocationServices.getFusedLocationProviderClient(context)
                val location = withTimeoutOrNull(5000) {
                    suspendCancellableCoroutine { cont ->
                        fused.lastLocation.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
                    }
                }
                Pair(location?.latitude, location?.longitude)
            } catch (_: Exception) { Pair(null, null) }
        }
    }

    /**
     * Collect heartbeat data for sending to server
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    fun collectHeartbeatData(): com.microspace.payo.data.models.heartbeat.HeartbeatRequest {
        Log.d("Collector", "ðŸ” Collecting heartbeat data")
        
        // Collect device data directly
        val serial = getRealSerialNumber()
        val imeis = getRealImeis()
        val (lat, lon) = getLocationCoords()
        
        return com.microspace.payo.data.models.heartbeat.HeartbeatRequest(
            deviceImeis = imeis,
            serialNumber = serial,
            installedRam = getInstalledRam(),
            totalStorage = getTotalStorage(),
            isDeviceRooted = isDeviceRootedCheck(),
            isUsbDebuggingEnabled = isUsbDebuggingEnabled(),
            isDeveloperModeEnabled = isDeveloperOptionsEnabled(),
            isBootloaderUnlocked = isBootloaderUnlockedCheck(),
            isCustomRom = isCustomRomCheck(),
            androidId = getAndroidId(),
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            deviceFingerprint = Build.FINGERPRINT,
            bootloader = if (Build.BOOTLOADER.length > 100) Build.BOOTLOADER.substring(0, 100) else Build.BOOTLOADER,
            osVersion = Build.VERSION.RELEASE,
            osEdition = "Android",
            sdkVersion = Build.VERSION.SDK_INT,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH ?: "unknown",
            systemUptime = android.os.SystemClock.elapsedRealtime(),
            installedAppsHash = "",
            systemPropertiesHash = "",
            latitude = lat,
            longitude = lon,
            batteryLevel = getBatteryLevel(),
            language = Locale.getDefault().language
        )
    }
}




