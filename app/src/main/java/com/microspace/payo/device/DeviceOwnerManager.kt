package com.microspace.payo.device

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.microspace.payo.receivers.admin.AdminReceiver
import com.microspace.payo.utils.constants.UserManagerConstants

/**
 * Optimized DeviceOwnerManager - Enterprise Device Policy Controller.
 * v7.9 - Added Forget All WiFi Networks for full deactivation.
 */
class DeviceOwnerManager(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, com.microspace.payo.receivers.admin.AdminReceiver::class.java)
    }

    private val packageName: String get() = context.packageName

    companion object {
        private const val TAG = "DeviceOwnerManager"

        val PERMANENT_RESTRICTIONS = arrayOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManagerConstants.DISALLOW_CONFIG_DEVELOPER_OPTS,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
        )
    }

    fun isDeviceOwner(): Boolean = try {
        devicePolicyManager.isDeviceOwnerApp(packageName)
    } catch (e: Exception) {
        false
    }

    fun applyPermanentHardening() {
        if (!isDeviceOwner()) return
        Log.i(TAG, "ðŸ›¡ï¸ Applying permanent hardening policies...")
        try {
            PERMANENT_RESTRICTIONS.forEach { restriction ->
                try {
                    devicePolicyManager.addUserRestriction(adminComponent, restriction)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply $restriction: ${e.message}")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    devicePolicyManager.setAutoTimeRequired(adminComponent, true)
                } catch (e: Exception) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
            }

            disableDeveloperOptions(true)
            Log.i(TAG, "âœ… Permanent hardening applied successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in hardening: ${e.message}")
        }
    }

    /**
     * Compatibility method: Disables/Enables developer options and ADB.
     */
    fun disableDeveloperOptions(disable: Boolean): Boolean {
        return try {
            if (isDeviceOwner()) {
                if (disable) {
                    devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                    devicePolicyManager.addUserRestriction(adminComponent, UserManagerConstants.DISALLOW_CONFIG_DEVELOPER_OPTS)
                    Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
                } else {
                    devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                    devicePolicyManager.clearUserRestriction(adminComponent, UserManagerConstants.DISALLOW_CONFIG_DEVELOPER_OPTS)
                }
                true
            } else false
        } catch (e: Exception) { false }
    }

    /**
     * FORGET ALL WIFI: Removes every saved WiFi network from the device.
     * This ensures no internet trace remains after deactivation.
     */
    fun forgetAllWiFiNetworks() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "âš ï¸ Cannot forget WiFi: Not Device Owner")
            return
        }
        
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val configuredNetworks = wifiManager.configuredNetworks
            
            if (configuredNetworks != null) {
                Log.i(TAG, "ðŸ§¹ Found ${configuredNetworks.size} WiFi networks. Starting full wipe...")
                var successCount = 0
                for (config in configuredNetworks) {
                    @Suppress("DEPRECATION")
                    if (wifiManager.removeNetwork(config.networkId)) {
                        successCount++
                    }
                }
                @Suppress("DEPRECATION")
                wifiManager.saveConfiguration()
                Log.i(TAG, "âœ… Wiped $successCount WiFi networks.")
            } else {
                Log.d(TAG, "â„¹ï¸ No configured WiFi networks found to wipe.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while wiping WiFi networks: ${e.message}")
        }
    }

    /**
     * FORGET WIFI: Specifically target networks used during QR provisioning.
     */
    fun forgetWiFiNetwork(ssid: String) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "âš ï¸ Cannot forget WiFi: Not Device Owner")
            return
        }
        
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val cleanSsid = ssid.replace("\"", "")
            
            Log.d(TAG, "ðŸ§¹ Attempting to forget WiFi: $cleanSsid")

            @Suppress("DEPRECATION")
            val configuredNetworks = wifiManager.configuredNetworks
            if (configuredNetworks != null) {
                for (config in configuredNetworks) {
                    val configSsid = config.SSID?.replace("\"", "")
                    if (configSsid == cleanSsid) {
                        @Suppress("DEPRECATION")
                        val success = wifiManager.removeNetwork(config.networkId)
                        if (success) {
                            @Suppress("DEPRECATION")
                            wifiManager.saveConfiguration()
                            Log.i(TAG, "âœ… Removed WiFi network: $cleanSsid")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while trying to forget WiFi network: ${e.message}")
        }
    }

    fun setSettingsProtected(hide: Boolean) {
        if (!isDeviceOwner()) return
        try {
            devicePolicyManager.setApplicationHidden(adminComponent, "com.android.settings", hide)
        } catch (e: Exception) {}
    }

    fun verifyCriticalRestrictions(): Boolean {
        if (!isDeviceOwner()) return false
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        var allActive = true
        PERMANENT_RESTRICTIONS.forEach { restriction ->
            @Suppress("DEPRECATION")
            val isActive = userManager.getUserRestrictions().getBoolean(restriction, false)
            if (!isActive) {
                try {
                    devicePolicyManager.addUserRestriction(adminComponent, restriction)
                } catch (e: Exception) {
                    allActive = false
                }
            }
        }
        return allActive
    }

    fun blockFactoryReset(): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setSystemUpdatePolicy() {
        if (!isDeviceOwner()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val policy = SystemUpdatePolicy.createAutomaticInstallPolicy()
                devicePolicyManager.setSystemUpdatePolicy(adminComponent, policy)
            }
        } catch (e: Exception) {}
    }

    fun grantRequiredPermissions() {
        if (!isDeviceOwner()) return
        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )
        permissions.forEach { permission ->
            try {
                devicePolicyManager.setPermissionGrantState(adminComponent, packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            } catch (_: Exception) {}
        }
    }

    fun selfDestructProvisioning() {
        try {
            if (isDeviceOwner()) {
                devicePolicyManager.clearDeviceOwnerApp(packageName)
            }
        } catch (e: Exception) {}
    }

    fun isDeviceCompatible(): Boolean = true
    
    fun applyRestrictions() = applyPermanentHardening()
    fun applyAllCriticalRestrictions() = applyPermanentHardening()
    fun applySilentCompanyRestrictions() = applyPermanentHardening()
    fun applyRestrictionsForSetupOnly() = applyPermanentHardening()
    
    val applySManager: SystemRestrictionManager
        get() = SystemRestrictionManager(this)
}

class SystemRestrictionManager(private val dom: DeviceOwnerManager) {
    fun disableDeveloperOptions(disable: Boolean): Boolean = dom.disableDeveloperOptions(disable)
}




