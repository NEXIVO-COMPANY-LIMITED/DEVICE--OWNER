package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.monitoring.SecurityMonitorService

/**
 * Receiver to wake up the app and send a heartbeat even when device is sleeping (Doze mode).
 * Works in tandem with SecurityMonitorService to ensure 24/7 consistency.
 */
class HeartbeatAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deviceId = intent.getStringExtra("device_id") ?: ""
        Log.d("HeartbeatAlarm", "⏰ Wake-up alarm received - triggering heartbeat check")
        
        // Ensure the foreground service is active and sends the heartbeat
        SecurityMonitorService.startService(context, deviceId)
    }
}