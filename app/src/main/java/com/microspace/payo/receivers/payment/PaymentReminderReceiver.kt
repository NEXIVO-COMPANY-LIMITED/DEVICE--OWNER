package com.microspace.payo.receivers.payment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microspace.payo.control.RemoteDeviceControlManager

/**
 * Receiver for Payment Reminder events.
 * Redirects to RemoteDeviceControlManager to ensure Soft Lock UI is applied correctly.
 */
class PaymentReminderReceiver : BroadcastReceiver() {
    
    private val TAG = "PaymentReminderReceiver"
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        
        Log.d(TAG, "ðŸ”” PAYMENT_REMINDER EVENT RECEIVED")
        
        val message = intent?.getStringExtra("message") ?: "Payment reminder"
        val controlManager = RemoteDeviceControlManager(context)
        
        // Use the unified manager to apply the soft lock
        controlManager.applySoftLock(
            reason = message
        )
    }
}





