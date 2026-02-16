package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.ui.activities.registration.RegistrationStatusActivity
import com.example.deviceowner.work.RestrictionEnforcementWorker
import kotlinx.coroutines.*

/**
 * QR Code Provisioning Receiver - Launch Device Registration
 * 
 * Flow:
 * QR Code Scan → onReceive() (returns immediately) → Background coroutine:
 * 1. Verify Device Owner (quick)
 * 2. Apply Restrictions (fast API calls)
 * 3. Launch Device Registration Activity
 * 4. Schedule periodic work
 */
class QRProvisioningReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "QRProvisioningReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        // Return immediately - don't block main thread
        when (intent.action) {
            "android.app.action.PROVISION_MANAGED_DEVICE",
            "android.app.action.PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE",
            "android.app.action.PROVISIONING_SUCCESSFUL" -> {
                Log.d(TAG, "QR Code provisioning detected - launching device registration")
                
                // Use goAsync() to keep receiver alive during async work
                val pendingResult = goAsync()
                
                // Launch background coroutine
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        handleQRProvisioning(context)
                    } finally {
                        // Finish the broadcast receiver
                        pendingResult.finish()
                    }
                }
            }
        }
    }
    
    /**
     * Handle QR Code provisioning - launch device registration
     */
    private suspend fun handleQRProvisioning(context: Context) {
        try {
            Log.d(TAG, "Starting QR provisioning handler - launching registration")
            
            // Step 1: Enable keyboard and touch FIRST – so user can type loan number and complete registration.
            context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("skip_security_restrictions", true).apply()
            context.getSharedPreferences("device_owner_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("skip_security_restrictions", true).apply()
            Log.d(TAG, "🔓 skip_security_restrictions set FIRST – keyboard and touch enabled")
            
            // Step 2: Initialize device owner manager
            val deviceOwnerManager = DeviceOwnerManager(context)
            
            // Step 3: Verify Device Owner status
            if (!deviceOwnerManager.isDeviceOwner()) {
                Log.e(TAG, "Device Owner verification failed")
                return
            }
            Log.d(TAG, "✅ Device Owner verified")

            // Step 4: Setup-only restrictions and block factory reset TOTALLY immediately
            deviceOwnerManager.applyRestrictionsForSetupOnly()
            val factoryResetBlocked = deviceOwnerManager.blockFactoryReset()
            if (factoryResetBlocked) {
                Log.d(TAG, "✅ Factory reset BLOCKED totally immediately after QR provisioning")
            } else {
                Log.e(TAG, "⚠️ Factory reset block failed – check Device Owner")
            }

            // Block USB debugging and Developer Options menu at provisioning (USB file transfer blocked only during hard lock)
            try {
                deviceOwnerManager.disableDeveloperOptions(true)
                deviceOwnerManager.applySManager.disableDeveloperOptions(true)
                Log.d(TAG, "✅ USB debugging and Developer Options menu BLOCKED at QR provisioning")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to block USB/debug at QR provisioning: ${e.message}", e)
            }

            // Step 5: Grant READ_PHONE_STATE so IMEI/serial can be collected
            deviceOwnerManager.grantRequiredPermissions()
            Log.d(TAG, "✅ Runtime permissions granted for phone state (IMEI/serial)")

            // Step 6: Launch Device Registration Activity
            withContext(Dispatchers.Main) {
                launchDeviceRegistration(context)
            }
            
            // Step 7: Schedule periodic work
            schedulePeriodicWork(context)
            
            Log.d(TAG, "QR provisioning completed - device registration launched")
            
        } catch (e: Exception) {
            Log.e(TAG, "QR provisioning failed", e)
        }
    }
    
    /**
     * Launch Registration Status Activity (determines registration flow)
     */
    private fun launchDeviceRegistration(context: Context) {
        try {
            val intent = Intent(context, RegistrationStatusActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Registration Status Activity launched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Registration Status Activity", e)
        }
    }
    
    /**
     * Schedule periodic work for restriction enforcement
     */
    private fun schedulePeriodicWork(context: Context) {
        try {
            RestrictionEnforcementWorker.schedule(context)
            Log.d(TAG, "Periodic work scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule periodic work", e)
        }
    }
}