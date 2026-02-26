package com.microspace.payo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microspace.payo.control.RemoteDeviceControlManager
import com.microspace.payo.device.DeviceOwnerManager
import com.microspace.payo.security.response.EnhancedAntiTamperResponse

/**
 * Package Removal Receiver
 * Detects attempts to uninstall, clear data, or deactivate the device owner app
 * Triggers immediate security response when detected
 */
class PackageRemovalReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "PackageRemovalReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_REMOVED -> {
                val packageName = intent.data?.schemeSpecificPart
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                
                // Only trigger if not replacing (actual uninstall, not update)
                if (!isReplacing && packageName == context.packageName) {
                    Log.e(TAG, "⚠️ DEVICE OWNER APP UNINSTALL ATTEMPT DETECTED!")
                    handleSecurityThreat(context, "Attempted to uninstall device owner app")
                }
            }
            
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    Log.w(TAG, "Device owner app was replaced - re-applying security restrictions...")
                    // Re-apply all restrictions after app update
                    reapplySecurityRestrictions(context)
                }
            }
            
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    Log.e(TAG, "⚠️ DEVICE OWNER APP FULLY REMOVED!")
                    handleSecurityThreat(context, "Device owner app was removed")
                }
            }
            
            Intent.ACTION_PACKAGE_DATA_CLEARED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    Log.e(TAG, "⚠️ DEVICE OWNER APP DATA CLEAR ATTEMPT DETECTED!")
                    handleSecurityThreat(context, "Attempted to clear device owner app data")
                }
            }
        }
    }
    
    private fun handleSecurityThreat(context: Context, reason: String) {
        try {
            val controlManager = RemoteDeviceControlManager(context)
            controlManager.applyHardLock("TAMPER: $reason", forceRestart = false, forceFromServerOrMismatch = true, tamperType = "PACKAGE_REMOVED")
            Log.e(TAG, "Hard lock applied due to tamper: $reason")
            EnhancedAntiTamperResponse(context).sendTamperToBackendOnly(
                tamperType = "PACKAGE_REMOVED",
                severity = "CRITICAL",
                description = reason,
                extraData = mapOf(
                    "lock_applied_on_device" to "hard",
                    "tamper_source" to "package_removal_receiver"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle security threat", e)
        }
    }
    
    private fun reapplySecurityRestrictions(context: Context) {
        try {
            val deviceOwnerManager = DeviceOwnerManager(context)
            if (deviceOwnerManager.isDeviceOwner()) {
                Log.d(TAG, "Re-applying setup-only restrictions after app update...")
                deviceOwnerManager.applyRestrictionsForSetupOnly()
                deviceOwnerManager.applySilentCompanyRestrictions()
                Log.d(TAG, "✅ Setup-only restrictions re-applied after update (keyboard stays enabled)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reapply security restrictions", e)
        }
    }
}
