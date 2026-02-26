package com.microspace.payo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microspace.payo.control.RemoteDeviceControlManager

/**
 * Receiver for Tamper Lock events.
 * Redirects to RemoteDeviceControlManager to ensure Kiosk Mode and UI are applied correctly.
 */
class TamperLockReceiver : BroadcastReceiver() {
    
    private val TAG = "TamperLockReceiver"
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        
        Log.d(TAG, "🔴 TAMPER_LOCK EVENT RECEIVED")
        
        val reason = intent?.getStringExtra("reason") ?: "Security Violation Detected"
        val controlManager = RemoteDeviceControlManager(context)
        
        // Use the unified manager to apply the hard lock
        controlManager.applyHardLock(
            reason = reason,
            lockType = RemoteDeviceControlManager.TYPE_TAMPER,
            forceFromServerOrMismatch = true
        )
    }
}
