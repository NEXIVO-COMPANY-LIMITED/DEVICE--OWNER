package com.deviceowner.utils

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.Environment
import android.telephony.TelephonyManager
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.content.IntentFilter
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.bluetooth.BluetoothAdapter
import android.net.wifi.WifiManager
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class DeviceInfo(
    val brand: String,
    val model: String,
    val manufacturer: String,
    val osVersion: String,
    val sdkVersion: Int,
    val buildNumber: String,
    val androidId: String,
    val totalStorage: String,
    val totalRAM: String,
    val totalStorageBytes: Long = 0L,
    val totalRAMBytes: Long = 0L,
    val simNetworkInfo: SimNetworkInfo,
    val batteryInfo: BatteryInfo,
    // Alternative Verification Fields (Android 9+ Compatible)
    val hardwareHash: String = "",
    val buildFingerprint: String = "",
    val deviceName: String = "",
    val boardName: String = "",
    val hardware: String = "",
    val product: String = "",
    val buildId: String = "",
    val macAddress: String? = null,
    val bluetoothMac: String? = null,
    val deviceSerial: String? = null,
    val appDeviceId: String = ""
)

data class SimNetworkInfo(
    val networkOperatorName: String,
    val simOperatorName: String,
    val simState: String,
    val phoneType: String,
    val networkType: String,
    val simSerialNumber: String
)

data class BatteryInfo(
    val capacity: String,
    val technology: String
)

object DeviceUtils {
    
    fun collectDeviceInfo(context: Context): DeviceInfo {
        val totalStorageBytes = getTotalStorageBytes()
        val totalRAMBytes = getTotalRAMBytes(context)
        val androidId = getAndroidId(context)
        val appDeviceId = getOrCreateAppDeviceId(context)
        
        return DeviceInfo(
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            osVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            buildNumber = Build.DISPLAY,
            androidId = androidId,
            totalStorage = formatBytes(totalStorageBytes),
            totalRAM = formatBytes(totalRAMBytes),
            totalStorageBytes = totalStorageBytes,
            totalRAMBytes = totalRAMBytes,
            simNetworkInfo = getSimNetworkInfo(context),
            batteryInfo = getBatteryInfo(context),
            // Alternative Verification Fields
            hardwareHash = generateHardwareHash(),
            buildFingerprint = Build.FINGERPRINT,
            deviceName = Build.DEVICE,
            boardName = Build.BOARD,
            hardware = Build.HARDWARE,
            product = Build.PRODUCT,
            buildId = Build.ID,
            macAddress = getMacAddress(context),
            bluetoothMac = getBluetoothMac(),
            deviceSerial = getDeviceSerial(),
            appDeviceId = appDeviceId
        )
    }
    
    private fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Unknown"
    }
    
    private fun getTotalStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val blockCount: Long
            val blockSize: Long
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                blockCount = stat.blockCountLong
                blockSize = stat.blockSizeLong
            } else {
                @Suppress("DEPRECATION")
                blockCount = stat.blockCount.toLong()
                @Suppress("DEPRECATION")
                blockSize = stat.blockSize.toLong()
            }
            
            blockCount * blockSize
        } catch (e: Exception) {
            Log.e("DeviceUtils", "Error getting total storage: ${e.message}", e)
            0L
        }
    }
    
    private fun getTotalRAMBytes(context: Context): Long {
        return try {
            // Try reading from /proc/meminfo first (most reliable)
            val memInfoFile = File("/proc/meminfo")
            if (memInfoFile.exists()) {
                val lines = memInfoFile.readLines()
                for (line in lines) {
                    if (line.startsWith("MemTotal:")) {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            // Value is in KB, convert to bytes
                            return parts[1].toLongOrNull()?.times(1024) ?: 0L
                        }
                    }
                }
            }
            
            // Fallback: use ActivityManager
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                
                // Try to get totalDeviceMemory (API 16+)
                try {
                    val totalDeviceMemoryField = memInfo.javaClass.getField("totalDeviceMemory")
                    totalDeviceMemoryField.isAccessible = true
                    return totalDeviceMemoryField.getLong(memInfo)
                } catch (e: Exception) {
                    Log.d("DeviceUtils", "totalDeviceMemory not available: ${e.message}")
                }
            }
            
            0L
        } catch (e: Exception) {
            Log.e("DeviceUtils", "Error getting total RAM: ${e.message}", e)
            0L
        }
    }
    
    private fun getTotalStorage(): String {
        return formatBytes(getTotalStorageBytes())
    }
    
    private fun getTotalRAM(context: Context): String {
        return formatBytes(getTotalRAMBytes(context))
    }
    
    private fun getSimNetworkInfo(context: Context): SimNetworkInfo {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            val networkOperatorName = telephonyManager.networkOperatorName ?: "Unknown"
            val simOperatorName = telephonyManager.simOperatorName ?: "Unknown"
            val simState = getSimState(telephonyManager.simState)
            val phoneType = getPhoneType(telephonyManager.phoneType)
            val networkType = getNetworkType(telephonyManager.networkType)
            val simSerialNumber = try {
                telephonyManager.simSerialNumber ?: "Not available"
            } catch (e: Exception) {
                "Not available"
            }
            
            SimNetworkInfo(
                networkOperatorName = networkOperatorName,
                simOperatorName = simOperatorName,
                simState = simState,
                phoneType = phoneType,
                networkType = networkType,
                simSerialNumber = simSerialNumber
            )
        } catch (e: Exception) {
            SimNetworkInfo(
                networkOperatorName = "Unknown",
                simOperatorName = "Unknown",
                simState = "Unknown",
                phoneType = "Unknown",
                networkType = "Unknown",
                simSerialNumber = "Unknown"
            )
        }
    }
    
    private fun getBatteryInfo(context: Context): BatteryInfo {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val technology = try {
                val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, intentFilter)
                batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
            
            BatteryInfo(
                capacity = if (capacity > 0) "${capacity / 1000} mAh" else "Unknown",
                technology = technology
            )
        } catch (e: Exception) {
            BatteryInfo(
                capacity = "Unknown",
                technology = "Unknown"
            )
        }
    }
    
    private fun getSimState(state: Int): String {
        return when (state) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "Absent"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            else -> "Unknown"
        }
    }
    
    private fun getPhoneType(type: Int): String {
        return when (type) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            else -> "Unknown"
        }
    }
    
    private fun getNetworkType(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "Unknown"
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                String.format("%.2f GB", gb)
            }
        }
    }
    
    // ===== ALTERNATIVE VERIFICATION METHODS (Android 9+ Compatible) =====
    
    private fun generateHardwareHash(): String {
        return try {
            val hardwareInfo = "${Build.DEVICE}_${Build.PRODUCT}_${Build.HARDWARE}_${Build.BOARD}_${Build.MANUFACTURER}_${Build.MODEL}"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(hardwareInfo.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e("DeviceUtils", "Error generating hardware hash: ${e.message}", e)
            "unknown_hash"
        }
    }
    
    private fun getMacAddress(context: Context): String? {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val connectionInfo = wifiManager?.connectionInfo
            connectionInfo?.macAddress
        } catch (e: Exception) {
            Log.d("DeviceUtils", "MAC address not available: ${e.message}")
            null
        }
    }
    
    private fun getBluetoothMac(): String? {
        return try {
            BluetoothAdapter.getDefaultAdapter()?.address
        } catch (e: Exception) {
            Log.d("DeviceUtils", "Bluetooth MAC not available: ${e.message}")
            null
        }
    }
    
    private fun getDeviceSerial(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: Exception) {
            Log.d("DeviceUtils", "Device serial not available: ${e.message}")
            null
        }
    }
    
    private fun getOrCreateAppDeviceId(context: Context): String {
        return try {
            val sharedPref = context.getSharedPreferences("device_verification", Context.MODE_PRIVATE)
            var deviceId = sharedPref.getString("app_device_id", null)
            if (deviceId == null) {
                deviceId = UUID.randomUUID().toString()
                sharedPref.edit().putString("app_device_id", deviceId).apply()
                Log.d("DeviceUtils", "Generated new app device ID: $deviceId")
            }
            deviceId
        } catch (e: Exception) {
            Log.e("DeviceUtils", "Error getting/creating app device ID: ${e.message}", e)
            UUID.randomUUID().toString()
        }
    }
    
    fun generateDeviceFingerprint(context: Context): String {
        return try {
            val deviceInfo = collectDeviceInfo(context)
            val fingerprint = "${deviceInfo.androidId}${deviceInfo.brand}${deviceInfo.model}${Build.SERIAL}"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(fingerprint.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown_fingerprint"
        }
    }
    
    fun checkCompatibility(context: Context): CompatibilityResult {
        val deviceInfo = collectDeviceInfo(context)
        val isCompatible = deviceInfo.sdkVersion >= Build.VERSION_CODES.N // API 24+
        
        return CompatibilityResult(
            isCompatible = isCompatible,
            minSdkRequired = 24,
            currentSdk = deviceInfo.sdkVersion,
            message = if (isCompatible) "Device is compatible" else "Device requires Android 7.0 or higher"
        )
    }
}

data class CompatibilityResult(
    val isCompatible: Boolean,
    val minSdkRequired: Int,
    val currentSdk: Int,
    val message: String
)
