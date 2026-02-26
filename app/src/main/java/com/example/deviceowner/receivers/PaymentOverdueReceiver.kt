package com.microspace.payo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microspace.payo.control.RemoteDeviceControlManager

/**
 * Receiver for Payment Overdue events.
 * Redirects to RemoteDeviceControlManager to ensure Kiosk Mode and UI are applied correctly.
 */
class PaymentOverdueReceiver : BroadcastReceiver() {
    
    private val TAG = "PaymentOverdueReceiver"
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        
        Log.d(TAG, "💰 PAYMENT_OVERDUE EVENT RECEIVED")
        
        val reason = intent?.getStringExtra("reason") ?: "Payment Overdue"
        val controlManager = RemoteDeviceControlManager(context)
        
        // Use the unified manager to apply the hard lock
        controlManager.applyHardLock(
            reason = reason,
            lockType = RemoteDeviceControlManager.TYPE_OVERDUE,
            forceFromServerOrMismatch = true
        )
    }
}
