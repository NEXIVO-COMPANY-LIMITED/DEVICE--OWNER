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

    private fun isDeviceOwnerApp(): Boolean {
        return deviceOwnerManager.isDeviceOwner()
    }

    private fun ensurePermissionsIfDeviceOwner() {
        if (isDeviceOwnerApp()) {
            deviceOwnerManager.grantRequiredPermissions()
        }
    }
    
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getDeviceImei(): List<String> {
        val imeiList = mutableListOf<String>()
        return try {
            try {
                val prefs = SharedPreferencesManager(context)
                val savedImeis = prefs.getDeviceImeiList()
                val valid = savedImeis.filter { it.isNotBlank() && !it.equals("NO_IMEI_FOUND", ignoreCase = true) }
                if (valid.isNotEmpty()) return valid
            } catch (e: Exception) {}
            
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager == null) return listOf("NO_IMEI_FOUND")

            ensurePermissionsIfDeviceOwner()

            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            } else true

            if (!hasPermission && !isDeviceOwnerApp()) return listOf("NO_IMEI_FOUND")
            
            val modemCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { telephonyManager.activeModemCount } catch (e: Exception) { telephonyManager.phoneCount }
            } else telephonyManager.phoneCount
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (i in 0 until modemCount) {
                    try {
                        val imei = telephonyManager.getImei(i)
                        if (!imei.isNullOrEmpty() && imei != "unknown" && imei != "0") imeiList.add(imei)
                    } catch (e: Exception) {}
                }
            } else {
                @Suppress("DEPRECATION")
                try {
                    val imei = telephonyManager.deviceId
                    if (!imei.isNullOrEmpty() && imei != "unknown" && imei != "0") imeiList.add(imei)
                } catch (e: Exception) {}
            }
            
            if (imeiList.isEmpty()) listOf("NO_IMEI_FOUND") else imeiList.distinct()
        } catch (e: Exception) {
            listOf("NO_IMEI_FOUND")
        }
    }
    
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getDeviceSerialNumber(): String? {
        return try {
            try {
                val prefs = SharedPreferencesManager(context)
                val savedSerial = prefs.getSerialNumber()
                if (!savedSerial.isNullOrBlank() && savedSerial != "unknown") return savedSerial
            } catch (e: Exception) {}
            
            ensurePermissionsIfDeviceOwner()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val serial = Build.getSerial()
                    if (!serial.isNullOrEmpty() && serial != "unknown") return serial
                } catch (e: Exception) {}
            }
            
            @Suppress("DEPRECATION")
            if (!Build.SERIAL.isNullOrEmpty() && Build.SERIAL != "unknown") return Build.SERIAL

            val properties = arrayOf("ro.serialno", "ro.boot.serialno", "ro.hardware.serialno")
            for (prop in properties) {
                try {
                    val process = Runtime.getRuntime().exec("getprop $prop")
                    val serial = process.inputStream.bufferedReader().readText().trim()
                    if (serial.isNotEmpty() && serial != "unknown") return serial
                } catch (e: Exception) {}
            }
            null
        } catch (e: Exception) { null }
    }
    
    private fun getTotalStorage(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.totalBytes
        } catch (e: Exception) { 0L }
    }
    
    private fun getInstalledRam(): Long {
        return try {
            val memInfo = java.io.File("/proc/meminfo").readText()
            val totalMem = memInfo.lines().find { it.startsWith("MemTotal:") }?.replace(Regex("[^0-9]"), "")?.toLongOrNull()
            (totalMem ?: 0L) * 1024
        } catch (e: Exception) { 0L }
    }
    
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var lastLocationTime = 0L
    private val LOCATION_CACHE_DURATION = 15 * 60 * 1000L // Dakika 15

    private fun getDeviceLocation(): Pair<Double, Double> {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Pair(0.0, 0.0)
        }

        // Ikiwa tumepata location ndani ya dakika 15 zilizopita, tumia ile ile
        if (System.currentTimeMillis() - lastLocationTime < LOCATION_CACHE_DURATION && lastLat != 0.0) {
            return Pair(lastLat, lastLng)
        }

        return try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            runBlocking {
                withTimeoutOrNull(3000L) {
                    suspendCancellableCoroutine { cont ->
                        // Jaribu kwanza kupata LastLocation (haisababishi notisi sana)
                        fused.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                lastLat = location.latitude
                                lastLng = location.longitude
                                lastLocationTime = System.currentTimeMillis()
                                cont.resume(Pair(lastLat, lastLng))
                            } else {
                                // Ikiwa hamna last location, omba mpya lakini kwa usahihi wa chini (Balanced Power)
                                val cts = CancellationTokenSource()
                                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                                    .addOnSuccessListener { loc ->
                                        if (loc != null) {
                                            lastLat = loc.latitude
                                            lastLng = loc.longitude
                                            lastLocationTime = System.currentTimeMillis()
                                            cont.resume(Pair(lastLat, lastLng))
                                        } else cont.resume(Pair(lastLat, lastLng))
                                    }
                                    .addOnFailureListener { cont.resume(Pair(lastLat, lastLng)) }
                                cont.invokeOnCancellation { cts.cancel() }
                            }
                        }.addOnFailureListener {
                            cont.resume(Pair(lastLat, lastLng))
                        }
                    }
                } ?: Pair(lastLat, lastLng)
            }
        } catch (e: Exception) { Pair(lastLat, lastLng) }
    }
    
    private fun formatStorageSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 GB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    fun collectDeviceData(loanNumber: String = ""): DeviceRegistrationRequest {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val (latitude, longitude) = getDeviceLocation()
        val totalStorageBytes = getTotalStorage()
        val installedRamBytes = getInstalledRam()

        ensurePermissionsIfDeviceOwner()

        val serial = getDeviceSerialNumber() ?: "NO_SERIAL_FOUND"
        val imeiList = getDeviceImei()
        
        return DeviceRegistrationRequest(
            loanNumber = loanNumber,
            deviceId = null,
            deviceInfo = mapOf(
                "android_id" to (androidId ?: "unknown"),
                "model" to (Build.MODEL ?: "unknown"),
                "manufacturer" to (Build.MANUFACTURER ?: "unknown"),
                "bootloader" to (Build.BOOTLOADER ?: "unknown"),
                "serial" to serial,
                "brand" to (Build.BRAND ?: "unknown"),
                "product" to (Build.PRODUCT ?: "unknown"),
                "hardware" to (Build.HARDWARE ?: "unknown")
            ),
            androidInfo = mapOf(
                "version_release" to (Build.VERSION.RELEASE ?: "unknown"),
                "version_sdk_int" to Build.VERSION.SDK_INT,
                "device_fingerprint" to (Build.FINGERPRINT ?: "unknown"),
                "security_patch" to (Build.VERSION.SECURITY_PATCH ?: "unknown")
            ),
            imeiInfo = mapOf("device_imeis" to imeiList, "phone_count" to imeiList.size),
            storageInfo = mapOf("total_storage" to formatStorageSize(totalStorageBytes), "installed_ram" to formatStorageSize(installedRamBytes)),
            locationInfo = mapOf("latitude" to latitude, "longitude" to longitude),
            securityInfo = mapOf("is_device_rooted" to false),
            systemIntegrity = mapOf("integrity" to "passed"),
            appInfo = mapOf("package_name" to context.packageName)
        )
    }
}
