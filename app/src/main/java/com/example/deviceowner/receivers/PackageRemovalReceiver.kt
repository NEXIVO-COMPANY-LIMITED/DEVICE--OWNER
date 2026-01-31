package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.device.DeviceOwnerManager

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
            val deviceOwnerManager = DeviceOwnerManager(context)
            
            // Re-apply ALL restrictions immediately
            if (deviceOwnerManager.isDeviceOwner()) {
                Log.d(TAG, "Re-applying ALL security restrictions after threat detection...")
                deviceOwnerManager.applyRestrictions()
                deviceOwnerManager.applyAllCriticalRestrictions()
                deviceOwnerManager.applySilentCompanyRestrictions()
            }
            
            // Apply soft lock
            val controlManager = RemoteDeviceControlManager(context)
            controlManager.applySoftLock("SOFT LOCK: $reason")
            Log.e(TAG, "Soft lock applied due to: $reason")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle security threat", e)
        }
    }
    
    private fun reapplySecurityRestrictions(context: Context) {
        try {
            val deviceOwnerManager = DeviceOwnerManager(context)
            if (deviceOwnerManager.isDeviceOwner()) {
                Log.d(TAG, "Re-applying security restrictions after app update...")
                deviceOwnerManager.applyRestrictions()
                deviceOwnerManager.applyAllCriticalRestrictions()
                deviceOwnerManager.applySilentCompanyRestrictions()
                Log.d(TAG, "✅ Security restrictions re-applied after update")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reapply security restrictions", e)
        }
    }
}
