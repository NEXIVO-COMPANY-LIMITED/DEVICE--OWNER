package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.device.DeviceOwnerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Boot Receiver - Device Registration Removed
 * 
 * Ensures the app applies security restrictions after device reboots.
 * All device registration and monitoring functionality has been removed.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        // Handle both boot completion actions
        val isBootCompleted = action == Intent.ACTION_BOOT_COMPLETED
        val isLockedBootCompleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        } else {
            false
        }
        val isQuickBoot = action == "android.intent.action.QUICKBOOT_POWERON"
        
        if (isBootCompleted || isLockedBootCompleted || isQuickBoot) {
            val bootMode = if (isLockedBootCompleted) "DIRECT BOOT (Locked)" else "Normal Boot"
            Log.i(TAG, "🚀 Device Boot Detected (Action: $action, Mode: $bootMode) - Registration disabled")
            
            val workingContext = if (isLockedBootCompleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            
            scope.launch {
                try {
                    initializeOnBoot(workingContext, isLockedBootCompleted)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during boot initialization", e)
                }
            }
        }
    }
    
    /**
     * Initialize security restrictions on boot - device registration removed
     */
    private suspend fun initializeOnBoot(context: Context, isDirectBootMode: Boolean = false) {
        if (isDirectBootMode) {
            Log.d(TAG, "🔒 Running in DIRECT BOOT MODE - Device is locked")
        }
        
        val deviceOwnerManager = DeviceOwnerManager(context)
        
        // Step 1: Verify Device Owner status
        if (!deviceOwnerManager.isDeviceOwner()) {
            Log.w(TAG, "⚠️ Not Device Owner - Skipping boot initialization")
            return
        }
        
        Log.d(TAG, "✅ Device Owner verified. Applying security restrictions...")
        
        // Step 2: Re-enforce security restrictions immediately
        try {
            Log.d(TAG, "🔒 Applying security restrictions on boot...")
            
            deviceOwnerManager.applyImmediateSecurityRestrictions()
            deviceOwnerManager.applyAllCriticalRestrictions()
            deviceOwnerManager.applyRestrictions()
            deviceOwnerManager.applySilentCompanyRestrictions()
            
            val allActive = deviceOwnerManager.verifyAllCriticalRestrictionsActive()
            if (allActive) {
                Log.d(TAG, "✅ ALL security restrictions verified active on boot")
            } else {
                Log.w(TAG, "⚠️ Some restrictions need attention - re-applying...")
                deviceOwnerManager.applyRestrictions()
            }
            
            Log.d(TAG, "✅ Security restrictions applied - no device registration")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply security restrictions", e)
            try {
                deviceOwnerManager.applyImmediateSecurityRestrictions()
                deviceOwnerManager.applyAllCriticalRestrictions()
            } catch (e2: Exception) {
                Log.e(TAG, "Critical: Could not apply basic restrictions: ${e2.message}", e2)
            }
        }
        
        // Step 3: Restart firmware security monitoring if device is registered
        try {
            val prefsManager = com.example.deviceowner.utils.SharedPreferencesManager(context)
            if (prefsManager.isDeviceRegistered()) {
                Log.d(TAG, "🔧 Device is registered - restarting firmware security monitoring...")
                
                // Re-activate firmware security mode
                val firmwareActivated = com.example.deviceowner.security.firmware.FirmwareSecurity.activateSecurityMode()
                Log.d(TAG, "Firmware security reactivated on boot: $firmwareActivated")
                
                // Restart firmware security monitoring service
                val firmwareIntent = Intent(context, com.example.deviceowner.services.FirmwareSecurityMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(firmwareIntent)
                } else {
                    context.startService(firmwareIntent)
                }
                
                Log.d(TAG, "✅ Firmware security monitoring restarted on boot")
            } else {
                Log.d(TAG, "Device not registered - skipping firmware security restart")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restart firmware security monitoring (non-fatal): ${e.message}")
        }
        
        Log.i(TAG, "✅ Boot initialization completed - device registration disabled")
    }
}