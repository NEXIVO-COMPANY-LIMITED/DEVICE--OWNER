package com.microspace.payo.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.microspace.payo.receivers.AdminReceiver

/**
 * SILENT DEVICE OWNER MANAGER
 *
 * All restrictions work SILENTLY – no popups, no dialogs, no messages to user.
 * User hawezi kufanya blocked actions – that's it. NO message inaonekana.
 *
 * @version 1.0.0 - Silent Mode
 */
class SilentDeviceOwnerManager(private val context: Context) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, AdminReceiver::class.java)
    }

    companion object {
        private const val TAG = "SilentDeviceOwner"

        /**
         * All critical restrictions – applied silently, no user messages.
         * Excludes DISALLOW_CONFIG_WIFI/DISALLOW_CONFIG_MOBILE_NETWORKS so user can manage data.
         */
        val SILENT_RESTRICTIONS = listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_REMOVE_USER
        )
    }

    /**
     * Applies all restrictions silently. No messages, no popups.
     */
    fun applySilentRestrictions(): Boolean {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "Not Device Owner")
            return false
        }

        var allSuccess = true
        SILENT_RESTRICTIONS.forEach { restriction ->
            try {
                dpm.addUserRestriction(adminComponent, restriction)
            } catch (e: Exception) {
                Log.e(TAG, "Failed: $restriction", e)
                allSuccess = false
            }
        }

        protectSelfSilently()
        protectGooglePlayServicesSilently()

        Log.d(TAG, "Silent restrictions applied: $allSuccess")
        return allSuccess
    }

    /**
     * Blocks factory reset silently. User tries → nothing happens, no message.
     */
    fun blockFactoryResetSilently(): Boolean {
        return try {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Factory reset block failed", e)
            false
        }
    }

    /**
     * Verifies factory reset still blocked. Re-applies silently if missing.
     */
    fun verifyFactoryResetStillBlocked(): Boolean {
        val blocked = isFactoryResetBlocked()
        if (!blocked) blockFactoryResetSilently()
        return blocked
    }

    private fun isFactoryResetBlocked(): Boolean {
        return try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            @Suppress("DEPRECATION")
            userManager.getUserRestrictions().getBoolean(UserManager.DISALLOW_FACTORY_RESET, false)
        } catch (e: Exception) {
            false
        }
    }

    private fun protectSelfSilently(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Self-protection failed", e)
            false
        }
    }

    private fun protectGooglePlayServicesSilently(): Boolean {
        return try {
            val gmsPackage = "com.google.android.gms"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dpm.setUninstallBlocked(adminComponent, gmsPackage, true)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "GMS protection failed", e)
            false
        }
    }

    /**
     * Suspends all user apps silently. Apps stop working, no notification.
     */
    fun suspendAllAppsSilently(): Int {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return 0
        var suspended = 0
        try {
            val pm = context.packageManager
            pm.getInstalledApplications(0).forEach { app ->
                if (!isSystemApp(app.flags) && app.packageName != context.packageName) {
                    try {
                        val result = dpm.setPackagesSuspended(
                            adminComponent, arrayOf(app.packageName), true
                        )
                        if (result.isEmpty()) suspended++
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "App suspension failed", e)
        }
        return suspended
    }

    /**
     * Unsuspends all apps silently.
     */
    fun unsuspendAllAppsSilently(): Int {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return 0
        var unsuspended = 0
        try {
            val pm = context.packageManager
            pm.getInstalledApplications(0).forEach { app ->
                if (!isSystemApp(app.flags) && app.packageName != context.packageName) {
                    try {
                        dpm.setPackagesSuspended(adminComponent, arrayOf(app.packageName), false)
                        unsuspended++
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "App unsuspension failed", e)
        }
        return unsuspended
    }

    /**
     * Applies payment overdue restrictions silently.
     */
    fun applyPaymentRestrictionsSilently(): Boolean {
        listOf(
            UserManager.DISALLOW_OUTGOING_CALLS,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_INSTALL_APPS
        ).forEach { restriction ->
            try { dpm.addUserRestriction(adminComponent, restriction) } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Removes payment restrictions silently.
     */
    fun removePaymentRestrictionsSilently(): Boolean {
        listOf(
            UserManager.DISALLOW_OUTGOING_CALLS,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_INSTALL_APPS
        ).forEach { restriction ->
            try { dpm.clearUserRestriction(adminComponent, restriction) } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Verifies all restrictions intact. Re-applies missing ones silently.
     */
    fun verifySilentRestrictionsIntact(): Boolean {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false
        var allIntact = true
        try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            @Suppress("DEPRECATION")
            val active = userManager.getUserRestrictions()
            SILENT_RESTRICTIONS.forEach { restriction ->
                if (!active.getBoolean(restriction, false)) {
                    try {
                        dpm.addUserRestriction(adminComponent, restriction)
                    } catch (e: Exception) {
                        allIntact = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verify failed", e)
            return false
        }
        return allIntact
    }

    /**
     * Status for backend/logging only. Not shown to user.
     */
    fun getSilentStatus(): Map<String, Boolean> {
        return try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            @Suppress("DEPRECATION")
            val r = userManager.getUserRestrictions()
            mapOf(
                "factory_reset_blocked" to r.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false),
                "safe_mode_blocked" to r.getBoolean(UserManager.DISALLOW_SAFE_BOOT, false),
                "usb_debugging_blocked" to r.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES, false),
                "uninstall_blocked" to r.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false),
                "is_device_owner" to dpm.isDeviceOwnerApp(context.packageName)
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun isSystemApp(flags: Int): Boolean {
        return (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }
}
