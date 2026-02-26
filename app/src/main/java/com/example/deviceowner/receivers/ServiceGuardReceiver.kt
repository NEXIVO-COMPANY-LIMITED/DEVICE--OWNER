package com.microspace.payo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microspace.payo.data.DeviceIdProvider
import com.microspace.payo.services.heartbeat.HeartbeatService
import com.microspace.payo.monitoring.SecurityMonitorService
import com.microspace.payo.services.remote.RemoteManagementService

/**
 * ServiceGuardReceiver - Protects essential services from being permanently killed.
 * Listens for various system events to re-trigger service checks.
 */
class ServiceGuardReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceGuard"
        const val ACTION_GUARD_CHECK = "com.microspace.payo.GUARD_CHECK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "🛡️ Service Guard triggered by: $action")

        val deviceId = DeviceIdProvider.getDeviceId(context)
        if (deviceId.isNullOrBlank()) {
            Log.w(TAG, "Device not registered, skipping guard check.")
            return
        }

        // Restart essential services if they are not running
        try {
            // 1. Heartbeat Service
            HeartbeatService.start(context, deviceId)
            
            // 2. Security Monitor
            SecurityMonitorService.startService(context, deviceId)
            
            // 3. Remote Management
            RemoteManagementService.startService(context, deviceId)
            
            Log.i(TAG, "✅ Essential services re-triggered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error re-triggering services: ${e.message}")
        }
    }
}
