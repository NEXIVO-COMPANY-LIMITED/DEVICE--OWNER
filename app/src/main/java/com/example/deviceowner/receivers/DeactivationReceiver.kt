package com.microspace.payo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microspace.payo.control.RemoteDeviceControlManager

/**
 * Receiver for Deactivation events.
 * Redirects to RemoteDeviceControlManager to ensure Kiosk Mode and UI are applied correctly.
 */
class DeactivationReceiver : BroadcastReceiver() {
    
    private val TAG = "DeactivationReceiver"
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        
        Log.d(TAG, "🔓 DEACTIVATE_DEVICE EVENT RECEIVED")
        
        val reason = intent?.getStringExtra("reason") ?: "Device deactivation requested by administrator"
        val controlManager = RemoteDeviceControlManager(context)
        
        // Use the unified manager to apply the deactivation screen in hard lock mode
        controlManager.applyHardLock(
            reason = reason,
            lockType = RemoteDeviceControlManager.TYPE_DEACTIVATION,
            forceFromServerOrMismatch = true
        )
    }
}
