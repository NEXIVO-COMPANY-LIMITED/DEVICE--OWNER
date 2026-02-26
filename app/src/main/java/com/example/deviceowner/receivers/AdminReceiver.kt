package com.microspace.payo.receivers

import android.annotation.SuppressLint
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.telephony.TelephonyManager
import android.util.Log
import com.microspace.payo.core.frp.manager.FrpManager
import com.microspace.payo.core.frp.manager.FrpPolicyManager
import com.microspace.payo.device.DeviceOwnerManager
import com.microspace.payo.security.mode.CompleteSilentMode

import com.microspace.payo.utils.storage.SharedPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AdminReceiver - The Foundation of Device Ownership
 * Collects device identifiers (IMEI, Serial) during provisioning.
 * Security restrictions are applied ONLY when device lock is triggered,
 * not during normal provisioning to avoid disabling user input.
 */
class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AdminReceiver"
    }

    /**
     * Called when Device Owner is enabled (e.g. via dpm set-device-owner).
     * Owner Persistence: Re-apply all restrictions immediately to ensure nothing
     * was manually tampered with via ADB. Standard enterprise practice.
     */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "========================================")
        Log.i(TAG, "DEVICE OWNER ENABLED")
        Log.i(TAG, "App now has Device Owner permissions")
        Log.i(TAG, "========================================")

        // Re-enforcement: Ensure policies survive tampering (ADB, manual clears)
        try {
            val dm = DeviceOwnerManager(context)
            if (dm.isDeviceOwner()) {
                dm.applyAllCriticalRestrictions()
                dm.setSystemUpdatePolicy()
                dm.blockFactoryReset()
                dm.disableDeveloperOptions(true)
                dm.applySManager.disableDeveloperOptions(true)
                Log.i(TAG, "✅ Owner Persistence: Restrictions, Factory Reset, Developer Options blocked immediately")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Owner persistence re-enforcement failed: ${e.message}", e)
        }

        // Complete Silent Mode: Hide all management messages from lock screen and Settings
        try {
            val silentMode = CompleteSilentMode(context)
            silentMode.enableCompleteSilentMode()
            silentMode.printSilentModeStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Silent mode failed: ${e.message}", e)
        }
    }

    /** Called when Device Owner is disabled. */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "========================================")
        Log.i(TAG, "DEVICE OWNER DISABLED")
        Log.i(TAG, "Device Owner permissions removed")
        Log.i(TAG, "========================================")
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "Device password changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "Device password attempt failed")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Device password attempt succeeded")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.d(TAG, "✅ Provisioning complete. Checking compatibility...")

        try {
            val dm = DeviceOwnerManager(context)

            // CRITICAL: Compatibility Check (Pixel, Android 15+, Locked Bootloader)
            if (!dm.isDeviceCompatible()) {
                Log.e(TAG, "❌ DEVICE INCOMPATIBLE: Device does not meet strict requirements.")
                dm.selfDestructProvisioning()
                return
            }

            // SAVE: WiFi SSID configured during provisioning (QR code setup)
            // Use improved persistent storage method
            saveProvisioningWiFiSSID(context)
            Log.i(TAG, "✅ Provisioning WiFi SSID saved for later cleanup")

            // CRITICAL: Grant permissions IMMEDIATELY after Device Owner activation
            // This must happen BEFORE any restrictions are applied
            if (dm.isDeviceOwner()) {
                dm.grantRequiredPermissions()
                Log.i(TAG, "✅ READ_PHONE_STATE and other runtime permissions granted (Device Owner)")
            } else {
                Log.e(TAG, "❌ CRITICAL: Device Owner status not confirmed after provisioning!")
            }

            // SECOND: Collect device identifiers while permissions are fresh
            collectDeviceIdentifiers(context)
            Log.i(TAG, "✅ Device identifiers collected and saved to preferences")

            // THIRD: Enable keyboard and touch for registration
            context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("skip_security_restrictions", true).apply()
            context.getSharedPreferences("device_owner_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("skip_security_restrictions", true).apply()
            Log.i(TAG, "🔓 skip_security_restrictions set – keyboard and touch enabled for registration")

            // FOURTH: Apply setup-only restrictions and block factory reset TOTALLY immediately
            if (dm.isDeviceOwner()) {
                dm.applyRestrictionsForSetupOnly()
                val factoryResetBlocked = dm.blockFactoryReset()
                if (factoryResetBlocked) {
                    Log.i(TAG, "✅ Factory reset BLOCKED totally immediately after provisioning")
                } else {
                    Log.e(TAG, "⚠️ Factory reset block failed – retry or check Device Owner")
                }

                // Block USB debugging and Developer Options menu at provisioning (USB file transfer blocked only during hard lock)
                try {
                    dm.disableDeveloperOptions(true)
                    dm.applySManager.disableDeveloperOptions(true)
                    Log.i(TAG, "✅ USB debugging and Developer Options menu BLOCKED at provisioning")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to block USB/debug at provisioning: ${e.message}", e)
                }

                com.microspace.payo.work.RestrictionEnforcementWorker.schedule(context)
                Log.i(TAG, "✅ Periodic restriction enforcement scheduled")
            }

            Log.i(TAG, "✅ Device Owner provisioning complete. Permissions granted, identifiers collected, restrictions applied.")

            // Complete Silent Mode: Hide all management messages after QR provisioning
            if (dm.isDeviceOwner()) {
                try {
                    CompleteSilentMode(context).enableCompleteSilentMode()
                    Log.i(TAG, "✅ Silent mode enabled - management messages hidden")
                } catch (e: Exception) {
                    Log.e(TAG, "Silent mode failed: ${e.message}", e)
                }


            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed during provisioning: ${e.message}", e)
        }
    }

    /**
     * Apply security restrictions when device lock is triggered.
     * Aligned with RemoteDeviceControlManager.applyHardLock() so lock state is consistent.
     * Called from the lock management system, not during provisioning.
     */
    fun applySecurityRestrictions(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)

        Log.d(TAG, "🔒 Applying security restrictions for device lock (aligned with applyHardLock)...")

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "❌ applySecurityRestrictions skipped: not device owner")
            return
        }

        try {
            // Policy: USB file transfer is ALLOWED – we do not add DISALLOW_USB_FILE_TRANSFER so users can transfer files via USB/MTP.
            val restrictions = arrayOf(
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_CONFIG_BLUETOOTH,
                UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_MODIFY_ACCOUNTS,
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
            )
            var added = 0
            for (r in restrictions) {
                try {
                    dpm.addUserRestriction(admin, r)
                    added++
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to add restriction $r: ${e.message}")
                }
            }
            Log.d(TAG, "✅ User restrictions applied: $added/${restrictions.size}")

            dpm.setUninstallBlocked(admin, context.packageName, true)
            Log.d(TAG, "✅ App uninstall blocked")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setAutoTimeRequired(admin, true)
                dpm.setStatusBarDisabled(admin, true)
                dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL)
                dpm.setPermittedAccessibilityServices(admin, emptyList())
                dpm.setCameraDisabled(admin, true)
                Log.d(TAG, "✅ Auto time, status bar, keyguard, accessibility, camera set for lock")
            }

            Log.i(TAG, "✅ Security restrictions applied. Device is now locked.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to apply security restrictions: ${e.message}")
        }
    }

    /**
     * Remove security restrictions when device is unlocked.
     * Clears the same set applied in applySecurityRestrictions (aligned with unlockDevice).
     */
    fun removeSecurityRestrictions(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)

        Log.d(TAG, "🔓 Removing security restrictions for device unlock...")

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "❌ removeSecurityRestrictions skipped: not device owner")
            return
        }

        try {
            // Clear hard-lock-only restrictions. Keep DISALLOW_FACTORY_RESET and keep
            // DISALLOW_DEBUGGING_FEATURES / DISALLOW_CONFIG_DEVELOPER_OPTS (blocked at provisioning).
            val restrictions = arrayOf(
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_USB_FILE_TRANSFER,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_CONFIG_BLUETOOTH,
                UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_MODIFY_ACCOUNTS,
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
            )
            for (r in restrictions) {
                try {
                    dpm.clearUserRestriction(admin, r)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to clear restriction $r: ${e.message}")
                }
            }
            Log.d(TAG, "✅ User restrictions cleared (factory reset + USB debug/developer options remain blocked)")

            dpm.setUninstallBlocked(admin, context.packageName, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setAutoTimeRequired(admin, false)
                dpm.setStatusBarDisabled(admin, false)
                dpm.setKeyguardDisabledFeatures(admin, 0)
                dpm.setPermittedAccessibilityServices(admin, null)
                dpm.setCameraDisabled(admin, false)
                Log.d(TAG, "✅ Auto time, status bar, keyguard, accessibility, camera restored")
            }

            Log.i(TAG, "✅ Security restrictions removed. Device is now unlocked.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove security restrictions: ${e.message}")
        }
    }

    /**
     * Collect device identifiers (IMEI, Serial Number) during provisioning
     * These are available immediately after Device Owner provisioning completes
     */
    private fun collectDeviceIdentifiers(context: Context) {
        try {
            val prefs = SharedPreferencesManager(context)
            
            // Collect IMEI
            val imeiList = getDeviceImei(context)
            Log.d(TAG, "📱 IMEIs collected: $imeiList")
            
            // Collect Serial Number
            val serialNumber = getSerialNumber()
            Log.d(TAG, "📱 Serial number collected: $serialNumber")
            
            // Save to SharedPreferences for later use
            if (imeiList.isNotEmpty()) {
                // Store as JSON array to preserve list structure
                val imeiJson = com.google.gson.Gson().toJson(imeiList)
                prefs.setDeviceImei(imeiJson)
                Log.d(TAG, "✅ IMEI saved to preferences: $imeiJson")
            }
            
            if (serialNumber.isNotEmpty() && serialNumber != "unknown") {
                prefs.setSerialNumber(serialNumber)
                Log.d(TAG, "✅ Serial number saved to preferences")
            }
            
            Log.d(TAG, "✅ Device identifiers collected and saved")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to collect device identifiers: ${e.message}", e)
        }
    }

    /**
     * Get device IMEI using TelephonyManager.
     * Device Owner has privileged access; READ_PHONE_STATE is granted in onProfileProvisioningComplete before this runs.
     * Supports single SIM and dual SIM devices.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getDeviceImei(context: Context): List<String> {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            
            if (telephonyManager == null) {
                Log.w(TAG, "⚠️ TelephonyManager is null")
                return emptyList()
            }
            
            val imeiList = mutableListOf<String>()
            
            // Get modem count (supports dual SIM)
            val modemCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    telephonyManager.activeModemCount
                } catch (e: Exception) {
                    telephonyManager.phoneCount
                }
            } else {
                telephonyManager.phoneCount
            }
            
            Log.d(TAG, "📱 Device has $modemCount modem(s) - Dual SIM support enabled")
            
            // Collect IMEI from each modem (handles dual SIM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (i in 0 until modemCount) {
                    try {
                        val imei = telephonyManager.getImei(i)
                        if (!imei.isNullOrEmpty() && imei != "unknown" && imei != "0") {
                            imeiList.add(imei)
                            Log.d(TAG, "✅ IMEI $i: $imei")
                        } else {
                            Log.w(TAG, "⚠️ IMEI $i is empty or invalid: $imei")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to get IMEI $i: ${e.message}")
                    }
                }
            } else {
                // Fallback for older Android versions (single SIM only)
                @Suppress("DEPRECATION")
                try {
                    val imei = telephonyManager.deviceId
                    if (!imei.isNullOrEmpty() && imei != "unknown" && imei != "0") {
                        imeiList.add(imei)
                        Log.d(TAG, "✅ IMEI (legacy): $imei")
                    } else {
                        Log.w(TAG, "⚠️ Legacy IMEI is empty or invalid: $imei")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to get IMEI (legacy): ${e.message}")
                }
            }
            
            // If no IMEIs found, mark as tablet or WiFi-only device
            if (imeiList.isEmpty()) {
                Log.w(TAG, "⚠️ No IMEIs found - device may be tablet or cellular disabled")
                imeiList.add("NO_IMEI_FOUND")
            }
            
            Log.d(TAG, "📱 Total IMEIs collected: ${imeiList.size}")
            imeiList
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error collecting IMEI: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get device serial number.
     * Device Owner has privileged access to Build.getSerial().
     */
    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getSerialNumber(): String {
        return try {
            // Build.getSerial() added in API 26 (O), restricted in API 29 (Q)
            // But available to Device Owner on all API 26+ versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serial = Build.getSerial()
                if (!serial.isNullOrEmpty() && serial != "unknown") {
                    Log.d(TAG, "✅ Serial (Build.getSerial): $serial")
                    return serial
                }
            }
            
            // Fallback for older Android versions
            @Suppress("DEPRECATION")
            val serial = Build.SERIAL
            if (!serial.isNullOrEmpty() && serial != "unknown") {
                Log.d(TAG, "✅ Serial (Build.SERIAL): $serial")
                return serial
            }
            
            Log.w(TAG, "⚠️ No valid serial number found")
            "unknown"
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException getting serial: ${e.message}")
            Log.e(TAG, "   Device Owner may not be properly activated or READ_PHONE_STATE not granted")
            "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting serial: ${e.message}", e)
            "unknown"
        }
    }

    /**
     * Save WiFi SSID that was configured during Device Owner provisioning (QR code setup)
     * This SSID will be removed after device registration completes
     */
    private fun saveProvisioningWiFiSSID(context: Context) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val connectionInfo = wifiManager.connectionInfo
            
            if (connectionInfo != null) {
                val currentSSID = connectionInfo.ssid?.replace("\"", "") ?: ""
                
                if (currentSSID.isNotBlank() && currentSSID != "<unknown ssid>") {
                    // Use SharedPreferencesManager for persistent storage that won't be cleared accidentally
                    val prefs = SharedPreferencesManager(context)
                    prefs.saveProvisioningWifiSsid(currentSSID)
                    
                    Log.i(TAG, "✅ Saved provisioning WiFi SSID for cleanup: $currentSSID")
                } else {
                    Log.d(TAG, "No active WiFi connection during provisioning - skipping SSID save")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not save provisioning WiFi SSID: ${e.message}")
        }
    }
}
