package com.microspace.payo.receivers.admin

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
import com.microspace.payo.device.DeviceOwnerManager
import com.microspace.payo.security.mode.CompleteSilentMode
import com.microspace.payo.utils.storage.SharedPreferencesManager

/**
 * AdminReceiver - The Foundation of Device Ownership
 */
class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AdminReceiver"
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Device admin disable requested - blocking")
        return "Device Owner cannot be disabled"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.e(TAG, "Device admin disabled - attempting to re-enable")
        try {
            val dm = DeviceOwnerManager(context)
            if (!dm.isDeviceOwner()) {
                Log.e(TAG, "Lost device owner status!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error on disable: ${e.message}")
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "DEVICE OWNER ENABLED")
        
        try {
            val dm = DeviceOwnerManager(context)
            val isOwner = dm.isDeviceOwner()
            Log.i(TAG, "Is Device Owner: $isOwner")
            
            if (isOwner) {
                Log.i(TAG, "Applying critical restrictions...")
                dm.applyAllCriticalRestrictions()
                dm.blockFactoryReset()
                dm.disableDeveloperOptions(true)
                Log.i(TAG, "✅ Device owner setup complete")
            } else {
                Log.w(TAG, "Not recognized as device owner yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Owner persistence failed: ${e.message}", e)
        }
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.d(TAG, "✅ Provisioning complete - starting critical setup")
        
        // Use goAsync() to prevent ANR during provisioning handshake
        val pendingResult = goAsync()
        
        try {
            val dm = DeviceOwnerManager(context)
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, AdminReceiver::class.java)
            val isOwner = dm.isDeviceOwner()
            Log.i(TAG, "Is Device Owner: $isOwner")
            
            if (isOwner) {
                // CRITICAL: Only do the most essential operations here
                // Block uninstall immediately
                dpm.setUninstallBlocked(admin, context.packageName, true)
                Log.d(TAG, "✓ Uninstall blocked")
                
                // Block factory reset immediately
                dm.blockFactoryReset()
                Log.d(TAG, "✓ Factory reset blocked")
                
                // Grant critical permissions
                dm.grantRequiredPermissions()
                Log.d(TAG, "✓ Permissions granted")
                
                // Schedule background workers for remaining setup
                // This prevents timeout during provisioning handshake
                com.microspace.payo.work.RestrictionEnforcementWorker.schedule(context)
                Log.d(TAG, "✓ Background setup scheduled")
                
                Log.i(TAG, "✅ Critical provisioning setup complete")
            } else {
                Log.e(TAG, "❌ Not device owner after provisioning")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Provisioning setup failed: ${e.message}", e)
        } finally {
            // Always finish the async operation
            pendingResult.finish()
        }
    }

    private fun applyAdvancedPolicies(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        try {
            // Prevent uninstalling this app
            dpm.setUninstallBlocked(admin, context.packageName, true)
            Log.d(TAG, "✓ Uninstall blocked for this app")
            
            // Disable account management
            dpm.setAccountManagementDisabled(admin, "com.google", true)
            Log.d(TAG, "✓ Google account management disabled")
            
            // Disable USB debugging (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    dpm.setUsbDataSignalingEnabled(false)
                    Log.d(TAG, "✓ USB data signaling disabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set USB data signaling: ${e.message}")
                }
            }
            
            // Set screen capture disabled
            dpm.setScreenCaptureDisabled(admin, true)
            Log.d(TAG, "✓ Screen capture disabled")
            
            Log.i(TAG, "✅ Advanced policies applied successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Some advanced policies could not be applied: ${e.message}")
        }
    }

    private fun collectDeviceIdentifiers(context: Context) {
        try {
            val prefs = SharedPreferencesManager(context)
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            @SuppressLint("HardwareIds", "MissingPermission")
            val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Build.getSerial() else Build.SERIAL
            
            prefs.setSerialNumber(serial)
            Log.d(TAG, "✅ Device identifiers collected")
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting IDs: ${e.message}")
        }
    }
}
