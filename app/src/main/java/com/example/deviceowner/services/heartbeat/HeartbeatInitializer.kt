package com.example.deviceowner.services.heartbeat

import android.content.Context
import android.util.Log
import com.example.deviceowner.utils.storage.SharedPreferencesManager

/**
 * HeartbeatInitializer - Ensures heartbeat is properly initialized after registration.
 * Validates device_id, starts SecurityMonitorService, and verifies heartbeat is running.
 */
object HeartbeatInitializer {
    private const val TAG = "HeartbeatInitializer"
    
    /**
     * Initialize heartbeat after successful device registration.
     * MUST be called from RegistrationSuccessActivity after device_id is saved.
     */
    fun initializeHeartbeatAfterRegistration(context: Context, deviceId: String): Boolean {
        Log.i(TAG, "🚀 Initializing heartbeat system after registration...")
        
        // Step 1: Validate device_id
        if (!validateDeviceId(context, deviceId)) {
            Log.e(TAG, "❌ Device ID validation failed")
            return false
        }
        
        // Step 2: Save device_id to all locations
        if (!saveDeviceIdToAllLocations(context, deviceId)) {
            Log.e(TAG, "❌ Failed to save device_id")
            return false
        }
        
        // Step 3: Enable heartbeat
        if (!enableHeartbeat(context)) {
            Log.e(TAG, "❌ Failed to enable heartbeat")
            return false
        }
        
        // Step 4: Start SecurityMonitorService
        if (!startSecurityMonitorService(context)) {
            Log.e(TAG, "❌ Failed to start SecurityMonitorService")
            return false
        }
        
        Log.i(TAG, "✅ Heartbeat initialization complete - heartbeat should start sending in 30 seconds")
        return true
    }
    
    /**
     * Validate device_id format and source.
     */
    private fun validateDeviceId(context: Context, deviceId: String): Boolean {
        Log.d(TAG, "🔍 Validating device_id: $deviceId")
        
        when {
            deviceId.isBlank() -> {
                Log.e(TAG, "❌ Device ID is blank")
                return false
            }
            deviceId.equals("unknown", ignoreCase = true) -> {
                Log.e(TAG, "❌ Device ID is 'unknown' (not from server)")
                return false
            }
            deviceId.startsWith("ANDROID-") -> {
                Log.e(TAG, "❌ Device ID is locally generated (starts with ANDROID-)")
                return false
            }
            deviceId.length < 5 -> {
                Log.e(TAG, "❌ Device ID is too short: $deviceId")
                return false
            }
            else -> {
                Log.i(TAG, "✅ Device ID is valid: ${deviceId.take(8)}...")
                return true
            }
        }
    }
    
    /**
     * Save device_id to all SharedPreferences locations for maximum compatibility.
     */
    private fun saveDeviceIdToAllLocations(context: Context, deviceId: String): Boolean {
        return try {
            // Location 1: device_data
            context.getSharedPreferences("device_data", Context.MODE_PRIVATE).edit()
                .putString("device_id_for_heartbeat", deviceId)
                .apply()
            Log.d(TAG, "✓ Saved to device_data.device_id_for_heartbeat")
            
            // Location 2: device_owner_prefs (via SharedPreferencesManager)
            SharedPreferencesManager(context).setDeviceIdForHeartbeat(deviceId)
            Log.d(TAG, "✓ Saved to device_owner_prefs.device_id_for_heartbeat")
            
            // Location 3: device_registration
            context.getSharedPreferences("device_registration", Context.MODE_PRIVATE).edit()
                .putString("device_id", deviceId)
                .apply()
            Log.d(TAG, "✓ Saved to device_registration.device_id")
            
            Log.i(TAG, "✅ Device ID saved to all locations")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving device_id: ${e.message}", e)
            false
        }
    }
    
    /**
     * Enable heartbeat in preferences.
     */
    private fun enableHeartbeat(context: Context): Boolean {
        return try {
            SharedPreferencesManager(context).setHeartbeatEnabled(true)
            Log.i(TAG, "✅ Heartbeat enabled in preferences")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling heartbeat: ${e.message}", e)
            false
        }
    }
    
    /**
     * Start SecurityMonitorService (which starts HeartbeatService).
     */
    private fun startSecurityMonitorService(context: Context): Boolean {
        return try {
            com.example.deviceowner.monitoring.SecurityMonitorService.startService(context)
            Log.i(TAG, "✅ SecurityMonitorService started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting SecurityMonitorService: ${e.message}", e)
            false
        }
    }
    
    /**
     * Diagnostic: Check if heartbeat is properly initialized.
     */
    fun diagnoseHeartbeat(context: Context): String {
        val sb = StringBuilder()
        sb.append("📊 HEARTBEAT DIAGNOSTIC REPORT\n")
        sb.append("================================\n")
        
        // Check device_id in all locations
        val deviceDataId = context.getSharedPreferences("device_data", Context.MODE_PRIVATE)
            .getString("device_id_for_heartbeat", null)
        val deviceRegId = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            .getString("device_id", null)
        val deviceOwnerPrefsId = SharedPreferencesManager(context).getDeviceIdForHeartbeat()
        
        sb.append("Device ID Status:\n")
        sb.append("  device_data: ${deviceDataId?.take(8) ?: "❌ MISSING"}\n")
        sb.append("  device_registration: ${deviceRegId?.take(8) ?: "❌ MISSING"}\n")
        sb.append("  device_owner_prefs: ${deviceOwnerPrefsId?.take(8) ?: "❌ MISSING"}\n")
        
        // Check heartbeat enabled
        val heartbeatEnabled = SharedPreferencesManager(context).isHeartbeatEnabled()
        sb.append("\nHeartbeat Enabled: ${if (heartbeatEnabled) "✅ YES" else "❌ NO"}\n")
        
        // Check last heartbeat time
        val lastHeartbeat = SharedPreferencesManager(context).getLastHeartbeatTime()
        sb.append("Last Heartbeat: ${if (lastHeartbeat > 0) "✅ ${System.currentTimeMillis() - lastHeartbeat}ms ago" else "❌ NEVER"}\n")
        
        // Check if SecurityMonitorService is running
        val isServiceRunning = isSecurityMonitorServiceRunning(context)
        sb.append("SecurityMonitorService: ${if (isServiceRunning) "✅ RUNNING" else "❌ NOT RUNNING"}\n")
        
        sb.append("\n================================\n")
        return sb.toString()
    }
    
    /**
     * Check if SecurityMonitorService is running.
     */
    private fun isSecurityMonitorServiceRunning(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.getRunningServices(Integer.MAX_VALUE).any {
                it.service.className == "com.example.deviceowner.monitoring.SecurityMonitorService"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check if SecurityMonitorService is running: ${e.message}")
            false
        }
    }
}
