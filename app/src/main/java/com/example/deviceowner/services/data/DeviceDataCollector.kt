package com.example.deviceowner.services.data

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.deviceowner.data.models.registration.DeviceRegistrationRequest
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.File

class DeviceDataCollector(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager? by lazy {
        try {
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        } catch (e: Exception) {
            Log.w("DeviceDataCollector", "DevicePolicyManager not available: ${e.message}")
            null
        }
    }

    private val deviceOwnerManager: DeviceOwnerManager by lazy {
        DeviceOwnerManager(context)
    }

    /** True if this app is Device Owner (can use privileged IMEI/Serial APIs on Android 10+). */
    private fun isDeviceOwnerApp(): Boolean {
        return deviceOwnerManager.isDeviceOwner()
    }

    /**
     * Ensures that required permissions for ID collection are granted if the app is Device Owner.
     */
    private fun ensurePermissionsIfDeviceOwner() {
        if (isDeviceOwnerApp()) {
            Log.d("DeviceDataCollector", "App is Device Owner. Ensuring permissions are granted...")
            deviceOwnerManager.grantRequiredPermissions()
        }
    }
    
    /**
     * Get device IMEI with proper Device Owner privileged access
     * For Android 10+, uses activeModemCount (more reliable than phoneCount)
     * Handles multi-SIM devices and tablets without cellular
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getDeviceImei(): List<String> {
        val imeiList = mutableListOf<String>()
        
        return try {
            // FIRST: Try to get from provisioning prefs (most reliable - saved during Device Owner setup)
            try {
                val prefs = SharedPreferencesManager(context)
                val savedImeis = prefs.getDeviceImeiList()
                val valid = savedImeis.filter { it.isNotBlank() && !it.equals("NO_IMEI_FOUND", ignoreCase = true) }
                if (valid.isNotEmpty()) {
                    Log.d("DeviceDataCollector", "✅ IMEI(s) from provisioning prefs (PRIORITY): $valid")
                    return valid
                }
            } catch (e: Exception) {
                Log.w("DeviceDataCollector", "⚠️ Prefs IMEI lookup failed: ${e.message}")
            }
            
            // SECOND: Try to collect from TelephonyManager
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            
            if (telephonyManager == null) {
                Log.e("DeviceDataCollector", "❌ TelephonyManager is null - cannot collect IMEI")
                return listOf("NO_IMEI_FOUND")
            }

            // Ensure permissions if we are Device Owner
            ensurePermissionsIfDeviceOwner()

            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val isDeviceOwner = isDeviceOwnerApp()
            if (!hasPermission && !isDeviceOwner) {
                Log.e("DeviceDataCollector", "❌ READ_PHONE_STATE permission NOT granted and NOT Device Owner")
                return listOf("NO_IMEI_FOUND")
            }
            
            // Use activeModemCount if available (API 30+), otherwise phoneCount
            val modemCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    telephonyManager.activeModemCount
                } catch (e: Exception) {
                    telephonyManager.phoneCount
                }
            } else {
                telephonyManager.phoneCount
            }
            
            Log.d("DeviceDataCollector", "📱 Modem count: $modemCount")
            
            // Collect IMEI from each slot - available from API 26 (Oreo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (i in 0 until modemCount) {
                    try {
                        val imei = telephonyManager.getImei(i)
                        if (!imei.isNullOrEmpty() && imei != "unknown" && imei != "0") {
                            imeiList.add(imei)
                            Log.d("DeviceDataCollector", "✅ IMEI slot $i: $imei")
                        }
                    } catch (e: Exception) {
                        Log.w("DeviceDataCollector", "⚠️ IMEI slot $i error: ${e.message}")
                    }
                }
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                try {
                    val imei = telephonyManager.deviceId
                    if (!imei.isNullOrEmpty() && imei != "unknown" && imei != "0") {
                        imeiList.add(imei)
                        Log.d("DeviceDataCollector", "✅ IMEI (legacy deviceId): $imei")
                    }
                } catch (e: Exception) {
                    Log.w("DeviceDataCollector", "⚠️ Legacy IMEI error: ${e.message}")
                }
            }
            
            // Try to get MEID for CDMA devices
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val meid = telephonyManager.meid
                    if (!meid.isNullOrEmpty() && meid != "unknown" && meid != "0") {
                        imeiList.add(meid)
                    }
                }
            } catch (e: Exception) {}
            
            if (imeiList.isEmpty()) {
                Log.w("DeviceDataCollector", "⚠️ No IMEI/MEID found")
                listOf("NO_IMEI_FOUND")
            } else {
                imeiList.distinct()
            }
        } catch (e: Exception) {
            Log.e("DeviceDataCollector", "❌ Error collecting IMEI: ${e.message}")
            listOf("NO_IMEI_FOUND")
        }
    }
    
    /**
     * Get device serial number using Device Owner privileged access
     * Device Owner can use Build.getSerial() which is restricted for regular apps
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getDeviceSerialNumber(): String? {
        return try {
            // FIRST: Try to get from provisioning prefs (most reliable)
            try {
                val prefs = SharedPreferencesManager(context)
                val savedSerial = prefs.getSerialNumber()
                if (!savedSerial.isNullOrBlank() && savedSerial != "unknown") {
                    Log.d("DeviceDataCollector", "✅ Serial from provisioning prefs: $savedSerial")
                    return savedSerial
                }
            } catch (e: Exception) {}
            
            ensurePermissionsIfDeviceOwner()

            // SECOND: Try Build.getSerial() - available from API 26 (Oreo)
            // On API 29+ (Android 10), this requires Device Owner status
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val serial = Build.getSerial()
                    if (!serial.isNullOrEmpty() && serial != "unknown") {
                        Log.d("DeviceDataCollector", "✅ Serial from Build.getSerial(): $serial")
                        return serial
                    }
                } catch (e: SecurityException) {
                    Log.w("DeviceDataCollector", "⚠️ SecurityException getting serial: ${e.message}")
                } catch (e: Exception) {
                    Log.w("DeviceDataCollector", "⚠️ Build.getSerial() failed: ${e.message}")
                }
            }
            
            // THIRD: Fallback to Build.SERIAL for older Android versions
            @Suppress("DEPRECATION")
            if (!Build.SERIAL.isNullOrEmpty() && Build.SERIAL != "unknown") {
                Log.d("DeviceDataCollector", "✅ Serial from Build.SERIAL: ${Build.SERIAL}")
                return Build.SERIAL
            }

            // FOURTH: Try system properties (Device Owner can often access these)
            val properties = arrayOf("ro.serialno", "ro.boot.serialno", "ro.hardware.serialno")
            for (prop in properties) {
                try {
                    val process = Runtime.getRuntime().exec("getprop $prop")
                    val serial = process.inputStream.bufferedReader().readText().trim()
                    if (serial.isNotEmpty() && serial != "unknown") {
                        Log.d("DeviceDataCollector", "✅ Serial from $prop: $serial")
                        return serial
                    }
                } catch (e: Exception) {}
            }
            
            null
        } catch (e: Exception) {
            Log.e("DeviceDataCollector", "❌ Error collecting serial number: ${e.message}")
            null
        }
    }
    
    fun isReadPhoneStatePermissionGranted(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getTotalStorage(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.totalBytes
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getInstalledRam(): Long {
        return try {
            val memInfo = java.io.File("/proc/meminfo").readText()
            val totalMem = memInfo.lines()
                .find { it.startsWith("MemTotal:") }
                ?.replace(Regex("[^0-9]"), "")
                ?.toLongOrNull()
            (totalMem ?: 0L) * 1024
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getDeviceLocation(): Pair<Double, Double> {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Pair(0.0, 0.0)
        }
        return try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            runBlocking {
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
        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }
    }
    
    private fun formatStorageSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 GB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes < 1024L * 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
            else -> "${bytes / (1024L * 1024 * 1024 * 1024)} TB"
        }
    }
    
    fun collectDeviceData(loanNumber: String = ""): DeviceRegistrationRequest {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val (latitude, longitude) = getDeviceLocation()
        val totalStorageBytes = getTotalStorage()
        val installedRamBytes = getInstalledRam()

        // Ensure permissions before collection
        ensurePermissionsIfDeviceOwner()

        val serialNumber = getDeviceSerialNumber()
        val imeiList = getDeviceImei()
        
        val totalStorageGB = formatStorageSize(totalStorageBytes)
        val installedRamGB = formatStorageSize(installedRamBytes)

        Log.d("DeviceDataCollector", "=== DATA COLLECTION: DO=${isDeviceOwnerApp()}, IMEI=$imeiList, Serial=$serialNumber")
        
        val serial = serialNumber ?: "NO_SERIAL_FOUND"
        return DeviceRegistrationRequest(
            loanNumber = loanNumber,
            deviceId = null,
            deviceInfo = mapOf(
                "android_id" to (androidId ?: "unknown"),
                "model" to (Build.MODEL ?: "unknown"),
                "manufacturer" to (Build.MANUFACTURER ?: "unknown"),
                "bootloader" to (Build.BOOTLOADER ?: "unknown"),
                "fingerprint" to (Build.FINGERPRINT ?: "unknown"),
                "serial" to serial,
                "serial_number" to serial,
                "brand" to (Build.BRAND ?: "unknown"),
                "product" to (Build.PRODUCT ?: "unknown"),
                "device" to (Build.DEVICE ?: "unknown"),
                "board" to (Build.BOARD ?: "unknown"),
                "hardware" to (Build.HARDWARE ?: "unknown"),
                "build_id" to (Build.ID ?: "unknown"),
                "build_type" to (Build.TYPE ?: "unknown")
            ),
            androidInfo = mapOf(
                "version_release" to (Build.VERSION.RELEASE ?: "unknown"),
                "version_sdk_int" to Build.VERSION.SDK_INT,
                "version_codename" to (Build.VERSION.CODENAME ?: "unknown"),
                "version_incremental" to (Build.VERSION.INCREMENTAL ?: "unknown"),
                "security_patch" to (Build.VERSION.SECURITY_PATCH ?: "unknown")
            ),
            imeiInfo = mapOf(
                "device_imeis" to imeiList,
                "phone_count" to imeiList.size
            ),
            storageInfo = mapOf(
                "total_storage" to totalStorageGB,
                "installed_ram" to installedRamGB
            ),
            locationInfo = mapOf(
                "latitude" to latitude,
                "longitude" to longitude
            ),
            securityInfo = mapOf("is_device_rooted" to false),
            systemIntegrity = mapOf("integrity" to "passed"),
            appInfo = mapOf("package_name" to context.packageName)
        )
    }
    
    fun collectDeviceDataForLocalServer(): Map<String, Any?> {
        ensurePermissionsIfDeviceOwner()
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val serialNumber = getDeviceSerialNumber()
        val imeiList = getDeviceImei()
        
        return mapOf(
            "android_id" to (androidId ?: "unknown"),
            "model" to (Build.MODEL ?: "unknown"),
            "manufacturer" to (Build.MANUFACTURER ?: "unknown"),
            "serial" to (serialNumber ?: "NO_SERIAL_FOUND"),
            "device_imeis" to imeiList,
            "version_release" to (Build.VERSION.RELEASE ?: "unknown"),
            "version_sdk_int" to Build.VERSION.SDK_INT,
            "total_storage" to getTotalStorage(),
            "installed_ram" to getInstalledRam()
        )
    }
}
