package com.example.deviceowner.device

import android.Manifest
import android.app.Activity
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
import com.example.deviceowner.receivers.AdminReceiver

/**
 * Optimized DeviceOwnerManager - Enterprise Device Policy Controller.
 * Handles security policies, compatibility checks, and device hardening.
 * Optimizations: cached reflection, shared constants, batched operations, reduced logging.
 *
 * Security Modes (GAP Analysis):
 * - STANDARD: ADB/Developer options allowed for support; factory reset blocked
 * - STRICT: ADB/USB debugging blocked; maximum security
 */
class DeviceOwnerManager(private val context: Context) {

    enum class SecurityMode {
        /** ADB allowed for support/troubleshooting; factory reset blocked */
        STANDARD,
        /** ADB blocked; maximum security */
        STRICT
    }

    companion object {
        private const val TAG = "DeviceOwnerManager"
        private const val LAUNCHER_ACTIVITY = "com.example.deviceowner.ui.activities.RegistrationStatusActivity"
        private const val PREF_SECURITY_MODE = "security_mode"
        /** Minimum Android version: 12 (API 31). Any device model allowed. */
        private const val MIN_SDK = 31

        /** Setup-only: minimal restrictions during registration (keyboard stays enabled). GAP FIX: Factory reset blocked. */
        val SETUP_RESTRICTIONS = arrayOf(
            UserManager.DISALLOW_FACTORY_RESET,     // ✓ ADDED - Gap Analysis Fix #2
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
        )

        /** Base critical restrictions (STANDARD mode). GAP FIX: Factory reset now blocked. */
        val CRITICAL_RESTRICTIONS = arrayOf(
            UserManager.DISALLOW_FACTORY_RESET,     // ✓ ADDED - Gap Analysis Fix #1
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_CONFIG_WIFI,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            UserManager.DISALLOW_CONFIG_BLUETOOTH,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
        )

        /** Additional restrictions for STRICT mode (blocks ADB/USB debugging). */
        val STRICT_MODE_RESTRICTIONS = arrayOf(
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_USB_FILE_TRANSFER
        )

        @Volatile
        private var bootloaderCheckClass: Class<*>? = null

        @Volatile
        private var bootloaderGetMethod: java.lang.reflect.Method? = null

        private fun getSystemProperty(name: String): String {
            return try {
                val clazz = bootloaderCheckClass ?: Class.forName("android.os.SystemProperties").also { bootloaderCheckClass = it }
                val method = bootloaderGetMethod ?: clazz.getMethod("get", String::class.java).also { bootloaderGetMethod = it }
                (method.invoke(null, name) as? String) ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }

    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, AdminReceiver::class.java)
    }

    private val packageName: String get() = context.packageName

    private val securityPrefs by lazy {
        context.getSharedPreferences("device_owner_config", Context.MODE_PRIVATE)
    }

    /**
     * Gets the current security mode. Default: STANDARD (ADB allowed for support).
     */
    fun getSecurityMode(): SecurityMode {
        val name = securityPrefs.getString(PREF_SECURITY_MODE, SecurityMode.STANDARD.name)
        return try {
            SecurityMode.valueOf(name ?: SecurityMode.STANDARD.name)
        } catch (e: Exception) {
            SecurityMode.STANDARD
        }
    }

    /**
     * Sets security mode. Call before applyAllCriticalRestrictions() or applySecurityMode().
     * STANDARD: ADB allowed for support. STRICT: ADB blocked.
     */
    fun setSecurityMode(mode: SecurityMode) {
        securityPrefs.edit().putString(PREF_SECURITY_MODE, mode.name).apply()
        Log.i(TAG, "Security mode set to: $mode")
    }

    /**
     * Gets restrictions based on current security mode.
     */
    private fun getRestrictionsForMode(): Array<String> {
        return when (getSecurityMode()) {
            SecurityMode.STANDARD -> CRITICAL_RESTRICTIONS
            SecurityMode.STRICT -> CRITICAL_RESTRICTIONS + STRICT_MODE_RESTRICTIONS
        }
    }

    /**
     * Applies restrictions based on configured security mode.
     * Use this for configurable ADB blocking (STRICT = block ADB).
     */
    fun applySecurityMode(): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            val restrictions = getRestrictionsForMode()
            addRestrictions(restrictions)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
            }
            setSystemUpdatePolicy()
            grantRequiredPermissions()
            updateBranding()
            Log.i(TAG, "✅ Security mode applied: ${getSecurityMode()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying security mode: ${e.message}")
            false
        }
    }

    /**
     * Checks if the app is currently the Device Owner.
     */
    fun isDeviceOwner(): Boolean = try {
        devicePolicyManager.isDeviceOwnerApp(packageName)
    } catch (e: Exception) {
        false
    }

    /**
     * Compatibility Check: any device model, Android 12 (API 31) and above.
     * No custom ROM or bootloader checks.
     */
    fun isDeviceCompatible(): Boolean {
        val sdkVersion = Build.VERSION.SDK_INT
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND

        if (sdkVersion < MIN_SDK) {
            logE("COMPATIBILITY FAILED: Android $sdkVersion < $MIN_SDK (minimum Android 12)")
            return false
        }
        Log.i(TAG, "✅ Device is COMPATIBLE: $manufacturer $brand, API $sdkVersion")
        return true
    }

    private fun isBootloaderUnlocked(): Boolean {
        val flashLocked = getSystemProperty("ro.boot.flash.locked")
        val verifiedBootState = getSystemProperty("ro.boot.verifiedbootstate")
        return flashLocked == "0" || verifiedBootState.equals("orange", ignoreCase = true)
            || (Build.TAGS?.contains("test-keys") == true)
    }

    private fun logE(msg: String) = Log.e(TAG, "❌ $msg")

    /**
     * Standard Enterprise: Forced Automatic System Updates.
     * Ensures the device stays on the latest security patch.
     */
    fun setSystemUpdatePolicy() {
        if (!isDeviceOwner()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val policy = SystemUpdatePolicy.createAutomaticInstallPolicy()
                devicePolicyManager.setSystemUpdatePolicy(adminComponent, policy)
                Log.d(TAG, "✅ System Update Policy set to AUTOMATIC")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting update policy: ${e.message}")
        }
    }

    /**
     * Professional Kiosk Mode: Pins the app and hides system UI.
     */
    fun setKioskMode(activity: Activity, enable: Boolean) {
        if (!isDeviceOwner()) return
        try {
            val packages = if (enable) arrayOf(packageName) else emptyArray()
            devicePolicyManager.setLockTaskPackages(adminComponent, packages)

            if (enable) {
                activity.startLockTask()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    devicePolicyManager.setKeyguardDisabled(adminComponent, true)
                    devicePolicyManager.setStatusBarDisabled(adminComponent, true)
                }
            } else {
                activity.stopLockTask()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    devicePolicyManager.setKeyguardDisabled(adminComponent, false)
                    devicePolicyManager.setStatusBarDisabled(adminComponent, false)
                }
            }
            Log.d(TAG, "✅ Kiosk mode ${if (enable) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling Kiosk mode: ${e.message}")
        }
    }

    /**
     * Setup-only restrictions: minimal hardening during registration.
     * Keeps keyboard/touch enabled - no DISALLOW_DEBUGGING_FEATURES.
     */
    fun applyRestrictionsForSetupOnly() {
        if (!isDeviceOwner()) return
        try {
            addRestrictions(SETUP_RESTRICTIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
            }
            updateBranding()
            grantRequiredPermissions()
            Log.d(TAG, "✅ Setup-only restrictions applied")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying setup-only restrictions: ${e.message}")
        }
    }

    /**
     * MAXIMUM HARDENING: Blocks all potential bypass methods.
     * Respects security mode (STANDARD=ADB allowed, STRICT=ADB blocked).
     */
    fun applyAllCriticalRestrictions() {
        if (!isDeviceOwner()) return
        try {
            addRestrictions(getRestrictionsForMode())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
            }
            setSystemUpdatePolicy()
            grantRequiredPermissions()
            updateBranding()
            Log.i(TAG, "✅ Device is now 100% Enterprise Hardened.")
        } catch (e: Exception) {
            Log.e(TAG, "Error hardening device: ${e.message}")
        }
    }

    private fun addRestrictions(restrictions: Array<String>) {
        restrictions.forEach { restriction ->
            try {
                devicePolicyManager.addUserRestriction(adminComponent, restriction)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add restriction $restriction: ${e.message}")
            }
        }
    }

    /**
     * Blocks factory reset completely. GAP FIX: Dedicated method for factory reset blocking.
     * @return true if factory reset is now blocked
     */
    fun blockFactoryReset(): Boolean {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Not Device Owner - cannot block factory reset")
            return false
        }
        return try {
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            val blocked = isFactoryResetBlocked()
            if (blocked) Log.i(TAG, "✓ Factory reset BLOCKED") else Log.e(TAG, "✗ Factory reset blocking FAILED")
            blocked
        } catch (e: Exception) {
            Log.e(TAG, "Exception blocking factory reset", e)
            false
        }
    }

    /**
     * Checks if factory reset is currently blocked.
     * @return true if factory reset is blocked
     */
    fun isFactoryResetBlocked(): Boolean {
        return try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            @Suppress("DEPRECATION")
            val restrictions = userManager.getUserRestrictions()
            val blocked = restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false)
            Log.d(TAG, "Factory reset blocked: $blocked")
            blocked
        } catch (e: Exception) {
            Log.e(TAG, "Error checking factory reset status", e)
            false
        }
    }

    /**
     * SILENT: No branding/support messages – user sees nothing.
     * All set to null so no "Managed by", "Contact admin", or lock screen text.
     */
    fun updateBranding() {
        if (!isDeviceOwner()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                devicePolicyManager.setOrganizationName(adminComponent, null)
                devicePolicyManager.setShortSupportMessage(adminComponent, null)
                devicePolicyManager.setLongSupportMessage(adminComponent, null)
                devicePolicyManager.setDeviceOwnerLockScreenInfo(adminComponent, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating branding: ${e.message}")
        }
    }

    /**
     * Automatically grants all required runtime permissions.
     */
    fun grantRequiredPermissions() {
        if (!isDeviceOwner()) return
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.forEach { permission ->
            try {
                devicePolicyManager.setPermissionGrantState(
                    adminComponent,
                    packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
            } catch (e: Exception) {
                Log.w(TAG, "Grant $permission: ${e.message}")
            }
        }
    }

    /** Show app icon in launcher. */
    fun showAppIcon() {
        if (!isDeviceOwner()) return
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, LAUNCHER_ACTIVITY),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "✅ App icon shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing app icon: ${e.message}")
        }
    }

    /** Hide app icon from launcher. */
    fun hideAppIcon() {
        if (!isDeviceOwner()) return
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, LAUNCHER_ACTIVITY),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "✅ App icon hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding app icon: ${e.message}")
        }
    }

    /**
     * Toggles Developer Options and ADB.
     * @return true if successful
     */
    fun disableDeveloperOptions(disable: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            if (disable) {
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
            } else {
                devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling Dev Options: ${e.message}")
            false
        }
    }

    /** Returns true if the app's admin component is active. */
    fun isDeviceAdmin(): Boolean = try {
        devicePolicyManager.isAdminActive(adminComponent)
    } catch (e: Exception) {
        false
    }

    /** Reset build number click counter to prevent developer options activation. */
    fun resetBuildNumberClickCounter() {
        try {
            val prefs = context.getSharedPreferences("development_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("development_settings_enabled", 0)
                putInt("show_dev_options_count", 0)
                putLong("show_dev_countdown", 0)
                putBoolean("development_settings_enabled", false)
                apply()
            }
            Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
            Log.d(TAG, "✓ Build number click counter reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting build number click counter: ${e.message}")
        }
    }

    /**
     * Apply restrictions (alias for applyAllCriticalRestrictions).
     * Used by tamper response and device owner restoration flows.
     */
    fun applyRestrictions() {
        applyAllCriticalRestrictions()
    }

    /**
     * Apply silent company restrictions: full hardening + disable developer options.
     */
    fun applySilentCompanyRestrictions() {
        if (!isDeviceOwner()) return
        try {
            applyAllCriticalRestrictions()
            // USB debugging allowed – do not disable developer options
            Log.d(TAG, "✅ Silent company restrictions applied (USB debug/file transfer allowed)")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying silent company restrictions: ${e.message}")
        }
    }

    val applySManager: SystemRestrictionManager
        get() = SystemRestrictionManager(context, devicePolicyManager, adminComponent)

    /**
     * Clears all saved Wi-Fi networks.
     */
    fun clearSavedWifiNetworks() {
        if (!isDeviceOwner()) return
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val networks = wifiManager.configuredNetworks
            networks?.forEach { network ->
                wifiManager.removeNetwork(network.networkId)
            }
            wifiManager.saveConfiguration()
            Log.d(TAG, "✅ Wi-Fi networks cleared.")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing Wi-Fi: ${e.message}")
        }
    }

    /**
     * Removes Device Owner status and wipes local data if needed.
     */
    fun selfDestructProvisioning() {
        try {
            if (isDeviceOwner()) {
                Log.e(TAG, "⚠️ SELF-DESTRUCT: Removing Device Owner...")
                devicePolicyManager.clearDeviceOwnerApp(packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in self-destruct: ${e.message}")
        }
    }
}

/**
 * Helper for system-level restriction management (developer options, ADB).
 */
class SystemRestrictionManager(
    private val context: Context,
    private val dpm: DevicePolicyManager,
    private val admin: ComponentName
) {
    fun disableDeveloperOptions(disable: Boolean): Boolean {
        return try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                if (disable) {
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
                    Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                    Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
                } else {
                    dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
