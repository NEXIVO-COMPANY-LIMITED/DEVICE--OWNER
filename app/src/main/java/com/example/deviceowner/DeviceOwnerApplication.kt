package com.example.deviceowner

import android.app.Application
import android.util.Log
import com.example.deviceowner.utils.logging.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application class for Device Owner app
 * Initializes logging system and other global components
 */
class DeviceOwnerApplication : Application() {
    
    companion object {
        private const val TAG = "DeviceOwnerApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "DeviceOwner Application starting...")
        
        // CRITICAL: Check and apply security restrictions IMMEDIATELY on app start
        Thread {
            try {
                val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(this@DeviceOwnerApplication)
                if (deviceOwnerManager.isDeviceOwner()) {
                    Log.d(TAG, "🔒 DEVICE OWNER DETECTED - CHECKING SECURITY RESTRICTIONS...")
                    
                    // Check if developer options are enabled (security breach)
                    val devOptionsEnabled = android.provider.Settings.Global.getInt(
                        contentResolver,
                        android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                        0
                    ) == 1
                    
                    if (devOptionsEnabled) {
                        Log.e(TAG, "🚨 SECURITY BREACH: Developer options are enabled! Applying immediate fix...")
                        
                        // Apply immediate security fix
                        val enhancedSecurity = com.example.deviceowner.security.enforcement.EnhancedSecurityManager(this@DeviceOwnerApplication)
                        val securityApplied = enhancedSecurity.apply100PercentPerfectSecurity()
                        
                        if (securityApplied) {
                            Log.d(TAG, "✅ SECURITY BREACH FIXED - Developer options blocked")
                        } else {
                            Log.e(TAG, "❌ CRITICAL: Could not fix security breach")
                            // Apply fallback
                            deviceOwnerManager.disableDeveloperOptions(true)
                            deviceOwnerManager.preventFactoryReset()
                        }
                    } else {
                        Log.d(TAG, "✅ Security check passed - Developer options properly blocked")
                    }
                    
                    // Verify factory reset is blocked
                    try {
                        val isFactoryResetBlocked = deviceOwnerManager.isFactoryResetBlocked()
                        if (!isFactoryResetBlocked) {
                            Log.e(TAG, "🚨 SECURITY BREACH: Factory reset not blocked! Applying immediate fix...")
                            deviceOwnerManager.preventFactoryReset()
                        } else {
                            Log.d(TAG, "✅ Security check passed - Factory reset properly blocked")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not verify factory reset status: ${e.message}")
                        // Apply as precaution
                        deviceOwnerManager.preventFactoryReset()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in security check: ${e.message}", e)
            }
        }.start()
        
        // Initialize logging system
        try {
            LogManager.initialize(this)
            LogManager.logInfo(LogManager.LogCategory.GENERAL, "Application started successfully", "APP_STARTUP")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize logging system: ${e.message}", e)
        }
        
        // Start Local JSON Data Server (serves device data via HTTP)
        try {
            com.example.deviceowner.services.LocalDataServerService.startService(this)
            Log.d(TAG, "🖥️ Local JSON Data Server started on port 8080")
            Log.d(TAG, "📱 Access at: http://localhost:8080")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Local Data Server: ${e.message}", e)
        }
        
        // Log initial device data for testing
        try {
            val deviceDataLogger = com.example.deviceowner.utils.DeviceDataLogger(this)
            CoroutineScope(Dispatchers.IO).launch {
                deviceDataLogger.logDeviceData("APP_STARTUP")
                deviceDataLogger.createDailySnapshot()
            }
            Log.d(TAG, "📊 Device data logging initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize device data logging: ${e.message}", e)
        }
        
        // Clear old logs (keep last 7 days)
        LogManager.clearOldLogs(7)
        
        Log.d(TAG, "DeviceOwner Application initialization complete")
    }
}