package com.microspace.payo.core.frp.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.microspace.payo.config.FrpConfig
import com.microspace.payo.receivers.AdminReceiver

/**
 * FRP Policy Manager - Device policies with Customer Account Freedom
 *
 * Applies security restrictions while ALLOWING customers to add their own Gmail.
 * - Factory reset prevention (Settings)
 * - USB debugging blocked
 * - Developer options disabled
 * - Safe mode blocked
 * - Customers CAN add Gmail, use Play Store, normal phone usage
 * - FRP protection remains independent (company account unlocks after factory reset)
 *
 * @version 1.0.0
 */
class FrpPolicyManager(private val context: Context) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, AdminReceiver::class.java)
    }

    companion object {
        private const val TAG = "FrpPolicyManager"
    }

    /**
     * Applies enterprise security policies with customer account freedom.
     * Customers can add Gmail, use Play Store - FRP stays active.
     */
    fun applyEnterprisePolicies(): Boolean {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "Not Device Owner - cannot apply policies")
            return false
        }

        return try {
            Log.i(TAG, "Applying FRP enterprise policies...")

            blockFactoryReset()
            blockSafeBoot()
            blockDeveloperOptions()
            blockUsbDebugging()
            allowCustomerAccounts()
            blockAddRemoveUsers()
            protectAppUninstall()

            Log.i(TAG, "✓ FRP enterprise policies applied (customer accounts allowed)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply policies: ${e.message}", e)
            false
        }
    }

    /**
     * Configures account management for customer freedom.
     * Customers can add their own Gmail - FRP protection remains.
     */
    private fun allowCustomerAccounts() {
        try {
            // Clear DISALLOW_MODIFY_ACCOUNTS so customer can add Gmail
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            Log.i(TAG, "✓ Customer account freedom: users can add Gmail")
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure account freedom: ${e.message}")
        }
    }

    private fun blockFactoryReset() {
        try {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            Log.i(TAG, "✓ Factory reset blocked (Settings)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not block factory reset: ${e.message}")
        }
    }

    private fun blockSafeBoot() {
        try {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            Log.i(TAG, "✓ Safe boot blocked")
        } catch (e: Exception) {
            Log.w(TAG, "Could not block safe boot: ${e.message}")
        }
    }

    private fun blockDeveloperOptions() {
        try {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
            Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
            Log.i(TAG, "✓ Developer options and ADB disabled")
        } catch (e: Exception) {
            Log.w(TAG, "Could not block developer options: ${e.message}")
        }
    }

    private fun blockUsbDebugging() {
        try {
            Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
            Log.d(TAG, "✓ USB debugging disabled")
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable USB debugging: ${e.message}")
        }
    }

    private fun blockAddRemoveUsers() {
        try {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_REMOVE_USER)
            Log.d(TAG, "✓ Add/remove users blocked")
        } catch (e: Exception) {
            Log.w(TAG, "Could not block add/remove users: ${e.message}")
        }
    }

    private fun protectAppUninstall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)
                Log.i(TAG, "✓ Device Owner app uninstall blocked")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not block uninstall: ${e.message}")
        }
    }
}
