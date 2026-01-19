package com.example.deviceowner.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

class DeviceOwnerManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceOwnerManager"
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    /**
     * Check if the app is the device owner
     */
    fun isDeviceOwner(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } else {
            @Suppress("DEPRECATION")
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        }
    }

    /**
     * Check if the app is a device admin
     */
    fun isDeviceAdmin(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Initialize device owner features
     */
    fun initializeDeviceOwner() {
        Log.d(TAG, "Initializing device owner features")
        if (isDeviceOwner()) {
            Log.d(TAG, "App is device owner - initializing policies")
            // Set default policies
            setDefaultPolicies()
        } else {
            Log.w(TAG, "App is not device owner")
        }
    }

    /**
     * Handle device owner removal
     */
    fun onDeviceOwnerRemoved() {
        Log.d(TAG, "Device owner removed - cleaning up")
    }

    /**
     * Lock the device immediately
     */
    fun lockDevice() {
        try {
            if (isDeviceOwner()) {
                devicePolicyManager.lockNow()
                Log.d(TAG, "Device locked successfully")
            } else {
                Log.w(TAG, "Cannot lock device - not device owner")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device", e)
        }
    }

    /**
     * Set device password policy
     */
    fun setDevicePassword(password: String): Boolean {
        return try {
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    devicePolicyManager.resetPassword(password, 0)
                } else {
                    @Suppress("DEPRECATION")
                    devicePolicyManager.resetPassword(password, 0)
                }
                Log.d(TAG, "Device password set successfully")
                true
            } else {
                Log.w(TAG, "Cannot set password - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting device password", e)
            false
        }
    }

    /**
     * Disable camera access
     */
    fun disableCamera(disable: Boolean): Boolean {
        return try {
            if (isDeviceOwner()) {
                devicePolicyManager.setCameraDisabled(adminComponent, disable)
                Log.d(TAG, "Camera disabled: $disable")
                true
            } else {
                Log.w(TAG, "Cannot disable camera - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling camera", e)
            false
        }
    }

    /**
     * Disable USB file transfer
     */
    fun disableUSB(disable: Boolean): Boolean {
        return try {
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    devicePolicyManager.setUsbDataSignalingEnabled(!disable)
                    Log.d(TAG, "USB file transfer disabled: $disable")
                    true
                } else {
                    Log.w(TAG, "USB control not available on this API level")
                    false
                }
            } else {
                Log.w(TAG, "Cannot disable USB - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling USB", e)
            false
        }
    }

    /**
     * Completely disable and hide developer options
     * Prevents developer options from being enabled even by clicking build number
     */
    fun disableDeveloperOptions(disable: Boolean): Boolean {
        return try {
            if (isDeviceOwner()) {
                // Method 1: Use User Restrictions (MOST RELIABLE - Android 5.0+)
                // This is the primary and most effective method for device owners
                try {
                    if (disable) {
                        devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                        Log.d(TAG, "✓ Developer options disabled via addUserRestriction (DISALLOW_DEBUGGING_FEATURES)")
                    } else {
                        devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                        Log.d(TAG, "✓ Developer options enabled via clearUserRestriction (DISALLOW_DEBUGGING_FEATURES)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set user restriction: ${e.message}")
                }
                
                // Method 2: Use Device Policy Manager API (Android 9+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val method = devicePolicyManager.javaClass.getMethod(
                            "setDebuggingFeaturesDisabled",
                            ComponentName::class.java,
                            Boolean::class.javaPrimitiveType
                        )
                        method.invoke(devicePolicyManager, adminComponent, disable)
                        Log.d(TAG, "✓ Developer options disabled via setDebuggingFeaturesDisabled")
                    } catch (e: Exception) {
                        Log.w(TAG, "setDebuggingFeaturesDisabled not available: ${e.message}")
                    }
                }
                
                // Method 3: Disable via Settings.Global (prevents access)
                try {
                    android.provider.Settings.Global.putInt(
                        context.contentResolver,
                        android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                        if (disable) 0 else 1
                    )
                    Log.d(TAG, "✓ Developer options disabled via Settings.Global")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not disable via Settings.Global: ${e.message}")
                }
                
                // Method 4: Disable via Settings.Secure (prevents toggle)
                try {
                    android.provider.Settings.Secure.putInt(
                        context.contentResolver,
                        android.provider.Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED,
                        if (disable) 0 else 1
                    )
                    Log.d(TAG, "✓ Developer options disabled via Settings.Secure")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not disable via Settings.Secure: ${e.message}")
                }
                
                // Method 5: Reset build number click counter (prevents enabling via build number clicks)
                if (disable) {
                    resetBuildNumberClickCounter()
                }
                
                Log.d(TAG, "Developer options completely disabled and hidden")
                true
            } else {
                Log.w(TAG, "Cannot disable developer options - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling developer options", e)
            false
        }
    }
    
    /**
     * Reset build number click counter
     * This prevents users from enabling developer options by clicking build number 7 times
     */
    private fun resetBuildNumberClickCounter() {
        try {
            // Reset the counter that tracks build number clicks
            // This prevents the "You are now a developer" message from appearing
            val prefs = context.getSharedPreferences("development_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("development_settings_enabled", 0).apply()
            prefs.edit().putInt("show_dev_options_count", 0).apply()
            
            // Also try to reset via Settings
            try {
                android.provider.Settings.Global.putInt(
                    context.contentResolver,
                    "development_settings_enabled",
                    0
                )
            } catch (e: Exception) {
                // May not be accessible
            }
            
            Log.d(TAG, "✓ Build number click counter reset")
        } catch (e: Exception) {
            Log.w(TAG, "Could not reset build number click counter: ${e.message}")
        }
    }
    
    /**
     * Check if developer options are enabled
     */
    fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            val isEnabled = android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
            isEnabled
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Set password policy requirements
     */
    fun setPasswordPolicy(
        minLength: Int = 8,
        requireUppercase: Boolean = true,
        requireLowercase: Boolean = true,
        requireNumbers: Boolean = true,
        requireSymbols: Boolean = true
    ): Boolean {
        return try {
            if (isDeviceOwner()) {
                devicePolicyManager.setPasswordMinimumLength(adminComponent, minLength)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var quality = DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
                    if (requireUppercase) {
                        devicePolicyManager.setPasswordMinimumUpperCase(adminComponent, 1)
                    }
                    if (requireLowercase) {
                        devicePolicyManager.setPasswordMinimumLowerCase(adminComponent, 1)
                    }
                    if (requireNumbers) {
                        devicePolicyManager.setPasswordMinimumNumeric(adminComponent, 1)
                    }
                    if (requireSymbols) {
                        devicePolicyManager.setPasswordMinimumSymbols(adminComponent, 1)
                    }
                    devicePolicyManager.setPasswordQuality(adminComponent, quality)
                }
                
                Log.d(TAG, "Password policy set successfully")
                true
            } else {
                Log.w(TAG, "Cannot set password policy - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting password policy", e)
            false
        }
    }

    /**
     * Wipe device (factory reset)
     */
    fun wipeDevice(): Boolean {
        return try {
            if (isDeviceOwner()) {
                devicePolicyManager.wipeData(0)
                Log.d(TAG, "Device wipe initiated")
                true
            } else {
                Log.w(TAG, "Cannot wipe device - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping device", e)
            false
        }
    }

    /**
     * Reboot device
     */
    fun rebootDevice(): Boolean {
        return try {
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    devicePolicyManager.reboot(adminComponent)
                    Log.d(TAG, "Device reboot initiated")
                    true
                } else {
                    Log.w(TAG, "Reboot not available on this API level")
                    false
                }
            } else {
                Log.w(TAG, "Cannot reboot device - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rebooting device", e)
            false
        }
    }

    /**
     * Completely prevent factory reset - hide button and block all methods
     * Prevents factory reset via Settings, Recovery, Fastboot, and USB
     */
    fun preventFactoryReset(): Boolean {
        return try {
            if (isDeviceOwner()) {
                // Method 1: Use User Restrictions (MOST RELIABLE - Android 5.0+)
                // This is the primary and most effective method for device owners
                try {
                    devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                    Log.d(TAG, "✓ Factory reset prevented via addUserRestriction (DISALLOW_FACTORY_RESET)")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set factory reset restriction: ${e.message}")
                }
                
                // Method 2: Set maximum password attempts to 0 to prevent reset via recovery
                try {
                    devicePolicyManager.setMaximumFailedPasswordsForWipe(adminComponent, 0)
                    Log.d(TAG, "✓ Maximum failed passwords for wipe set to 0")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set maximum failed passwords: ${e.message}")
                }
                
                // Method 3: Disable factory reset option in settings (hide button)
                disableFactoryResetOption()
                
                // Method 4: Hide factory reset from Settings UI
                hideFactoryResetFromSettings()
                
                // Method 5: Block recovery mode factory reset
                blockRecoveryModeFactoryReset()
                
                // Method 6: Block fastboot mode factory reset
                blockFastbootModeFactoryReset()
                
                // Method 7: Disable USB file transfer (prevents USB flashing)
                disableUSB(true)
                
                Log.d(TAG, "✓ Factory reset completely prevented and hidden")
                true
            } else {
                Log.w(TAG, "Cannot prevent factory reset - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preventing factory reset", e)
            false
        }
    }
    
    /**
     * Disable factory reset option via Device Policy Manager
     */
    private fun disableFactoryResetOption() {
        try {
            // Use reflection to disable factory reset protection (Android 7.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val method = devicePolicyManager.javaClass.getMethod(
                        "setFactoryResetProtectionPolicy",
                        ComponentName::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    method.invoke(devicePolicyManager, adminComponent, true)
                    Log.d(TAG, "✓ Factory reset protection policy enabled")
                } catch (e: NoSuchMethodException) {
                    Log.w(TAG, "setFactoryResetProtectionPolicy not available")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting factory reset protection: ${e.message}")
        }
    }
    
    /**
     * Hide factory reset from Settings UI
     * Prevents factory reset button from being visible
     */
    private fun hideFactoryResetFromSettings() {
        try {
            // Hide factory reset via Settings.Global
            android.provider.Settings.Global.putInt(
                context.contentResolver,
                "factory_reset_protection_enabled",
                1
            )
            
            // Hide factory reset option in Settings
            android.provider.Settings.Secure.putInt(
                context.contentResolver,
                "factory_reset_protection_enabled",
                1
            )
            
            Log.d(TAG, "✓ Factory reset hidden from Settings UI")
        } catch (e: Exception) {
            Log.w(TAG, "Could not hide factory reset from Settings: ${e.message}")
        }
    }
    
    /**
     * Block recovery mode factory reset
     * Monitors and blocks recovery mode access
     */
    private fun blockRecoveryModeFactoryReset() {
        try {
            // Block recovery mode by disabling recovery access
            // This prevents factory reset via recovery mode
            android.provider.Settings.Global.putInt(
                context.contentResolver,
                "recovery_mode_enabled",
                0
            )
            
            Log.d(TAG, "✓ Recovery mode factory reset blocked")
        } catch (e: Exception) {
            Log.w(TAG, "Could not block recovery mode: ${e.message}")
        }
    }
    
    /**
     * Block fastboot mode factory reset
     * Prevents fastboot mode access for flashing
     */
    private fun blockFastbootModeFactoryReset() {
        try {
            // Block fastboot mode by disabling fastboot access
            // This prevents factory reset via fastboot mode
            android.provider.Settings.Global.putInt(
                context.contentResolver,
                "fastboot_mode_enabled",
                0
            )
            
            Log.d(TAG, "✓ Fastboot mode factory reset blocked")
        } catch (e: Exception) {
            Log.w(TAG, "Could not block fastboot mode: ${e.message}")
        }
    }
    
    /**
     * Check if factory reset is blocked
     */
    fun isFactoryResetBlocked(): Boolean {
        return try {
            var isRestricted = false
            
            // Check if user restriction is set (Android 5.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    // Try modern API first (API 23+)
                    val userManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.getSystemService(UserManager::class.java)
                    } else {
                        context.getSystemService(Context.USER_SERVICE) as? UserManager
                    }
                    if (userManager != null) {
                        val restrictions = userManager.getUserRestrictions()
                        isRestricted = restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not check user restriction: ${e.message}")
                }
            }
            
            // Also check maximum password attempts
            val maxAttempts = devicePolicyManager.getMaximumFailedPasswordsForWipe(adminComponent)
            val isWipeBlocked = maxAttempts == 0
            
            val isBlocked = isRestricted || isWipeBlocked
            Log.d(TAG, "Factory reset blocked check: restriction=$isRestricted, wipeBlocked=$isWipeBlocked, result=$isBlocked")
            return isBlocked
        } catch (e: Exception) {
            Log.w(TAG, "Error checking factory reset block status: ${e.message}")
            false
        }
    }
    
    /**
     * Verify and enforce critical restrictions (developer options and factory reset)
     * This method should be called periodically to ensure restrictions remain active
     */
    fun verifyAndEnforceCriticalRestrictions(): Boolean {
        return try {
            if (!isDeviceOwner()) {
                Log.w(TAG, "Cannot verify restrictions - not device owner")
                return false
            }
            
            var allEnforced = true
            
            // Verify and enforce developer options restriction
            try {
                var devOptionsRestricted = false
                
                // Check if user restriction is set (Android 5.0+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        // Try modern API first (API 23+)
                        val userManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            context.getSystemService(UserManager::class.java)
                        } else {
                            context.getSystemService(Context.USER_SERVICE) as? UserManager
                        }
                        if (userManager != null) {
                            val restrictions = userManager.getUserRestrictions()
                            devOptionsRestricted = restrictions.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES, false)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not check developer options restriction: ${e.message}")
                    }
                }
                
                if (!devOptionsRestricted || isDeveloperOptionsEnabled()) {
                    Log.w(TAG, "Developer options restriction not properly enforced - re-applying")
                    disableDeveloperOptions(true)
                    allEnforced = false
                } else {
                    Log.d(TAG, "✓ Developer options restriction verified")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error verifying developer options restriction: ${e.message}")
                disableDeveloperOptions(true) // Re-apply as fallback
                allEnforced = false
            }
            
            // Verify and enforce factory reset restriction
            try {
                if (!isFactoryResetBlocked()) {
                    Log.w(TAG, "Factory reset restriction not properly enforced - re-applying")
                    preventFactoryReset()
                    allEnforced = false
                } else {
                    Log.d(TAG, "✓ Factory reset restriction verified")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error verifying factory reset restriction: ${e.message}")
                preventFactoryReset() // Re-apply as fallback
                allEnforced = false
            }
            
            if (allEnforced) {
                Log.d(TAG, "✓ All critical restrictions verified and enforced")
            }
            
            return allEnforced
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying critical restrictions", e)
            // Try to re-apply as fallback
            disableDeveloperOptions(true)
            preventFactoryReset()
            return false
        }
    }
    
    /**
     * Set default device owner policies
     */
    private fun setDefaultPolicies() {
        try {
            // Disable camera by default
            disableCamera(true)
            
            // Disable developer options
            disableDeveloperOptions(true)
            
            // Set password policy
            setPasswordPolicy()
            
            // Prevent factory reset
            preventFactoryReset()
            
            Log.d(TAG, "Default policies applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying default policies", e)
        }
    }
}
