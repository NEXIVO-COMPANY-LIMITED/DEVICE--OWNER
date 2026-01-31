package com.example.deviceowner.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Soft Lock Monitor Service
 * Continuously monitors for unauthorized activities during soft lock:
 * - Debug mode activation
 * - App uninstall attempts
 * - Cache clearing attempts
 * - Developer settings access
 */
class SoftLockMonitorService : Service() {
    
    companion object {
        private const val TAG = "SoftLockMonitor"
        private val MONITOR_INTERVAL = TimeUnit.SECONDS.toMillis(5) // Check every 5 seconds
        
        fun startMonitoring(context: Context) {
            val intent = Intent(context, SoftLockMonitorService::class.java).apply {
                action = "START_MONITORING"
            }
            context.startService(intent)
        }
        
        fun stopMonitoring(context: Context) {
            val intent = Intent(context, SoftLockMonitorService::class.java).apply {
                action = "STOP_MONITORING"
            }
            context.startService(intent)
        }
    }
    
    private lateinit var controlManager: RemoteDeviceControlManager
    private var monitoringJob: Job? = null
    private var packageReceiver: BroadcastReceiver? = null
    
    // Track previous states
    private var wasUsbDebuggingEnabled = false
    private var wasDeveloperModeEnabled = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SoftLockMonitorService created")
        
        controlManager = RemoteDeviceControlManager(this)
        
        // Initialize previous states
        wasUsbDebuggingEnabled = isUsbDebuggingEnabled()
        wasDeveloperModeEnabled = isDeveloperModeEnabled()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_MONITORING" -> {
                startMonitoring()
            }
            "STOP_MONITORING" -> {
                stopMonitoring()
            }
        }
        
        return START_STICKY // Restart service if killed
    }
    
    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) {
            Log.d(TAG, "Monitoring already active")
            return
        }
        
        Log.d(TAG, "Starting soft lock monitoring")
        
        // Register package change receiver
        registerPackageReceiver()
        
        // Start continuous monitoring
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    // Only monitor if device is in soft lock
                    if (controlManager.getLockState() == RemoteDeviceControlManager.LOCK_SOFT) {
                        performSecurityChecks()
                    }
                    
                    delay(MONITOR_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop: ${e.message}", e)
                    delay(MONITOR_INTERVAL) // Continue monitoring even on error
                }
            }
        }
    }
    
    private fun performSecurityChecks() {
        try {
            // Check for debug mode activation
            checkDebugModeActivation()
            
            // Check for developer settings access
            checkDeveloperSettingsAccess()
            
            // Update previous states
            wasUsbDebuggingEnabled = isUsbDebuggingEnabled()
            wasDeveloperModeEnabled = isDeveloperModeEnabled()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing security checks: ${e.message}", e)
        }
    }
    
    private fun checkDebugModeActivation() {
        val currentUsbDebugging = isUsbDebuggingEnabled()
        val currentDeveloperMode = isDeveloperModeEnabled()
        
        // Check if USB debugging was just enabled
        if (currentUsbDebugging && !wasUsbDebuggingEnabled) {
            Log.w(TAG, "USB debugging enabled during soft lock - escalating to hard lock")
            escalateToHardLock(
                "Unauthorized debug mode activation detected",
                "USB_DEBUG_ATTEMPT: User attempted to enable USB debugging while device was restricted. This violates security policies."
            )
        }
        
        // Check if developer mode was just enabled
        if (currentDeveloperMode && !wasDeveloperModeEnabled) {
            Log.w(TAG, "Developer mode enabled during soft lock - escalating to hard lock")
            escalateToHardLock(
                "Unauthorized developer settings access detected",
                "DEVELOPER_MODE_ATTEMPT: User attempted to enable developer options while device was restricted. This provides unauthorized system access."
            )
        }
    }
    
    private fun checkDeveloperSettingsAccess() {
        // This is a simplified check - in a real implementation, you might monitor
        // for specific activities or system events that indicate settings access
        
        // Check if developer options are newly enabled
        val developerOptionsEnabled = isDeveloperModeEnabled()
        if (developerOptionsEnabled && !wasDeveloperModeEnabled) {
            Log.w(TAG, "Developer options accessed during soft lock")
            escalateToHardLock(
                "Unauthorized system settings modification",
                "DEVELOPER_SETTINGS_ACCESS: User accessed developer settings while device was restricted. This could compromise device security."
            )
        }
    }
    
    private fun registerPackageReceiver() {
        if (packageReceiver != null) {
            return // Already registered
        }
        
        packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName == context?.packageName) {
                            Log.e(TAG, "App uninstall attempt detected during soft lock")
                            escalateToHardLock(
                                "Unauthorized app uninstall attempt detected",
                                "UNINSTALL_ATTEMPT: User attempted to uninstall the device management application. This is strictly prohibited and violates the device agreement."
                            )
                        }
                    }
                    Intent.ACTION_PACKAGE_DATA_CLEARED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName == context?.packageName) {
                            Log.e(TAG, "App data clear attempt detected during soft lock")
                            escalateToHardLock(
                                "Unauthorized app data manipulation detected",
                                "DATA_CLEAR_ATTEMPT: User attempted to clear application data while device was restricted. This could disrupt device management and loan tracking."
                            )
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
            addDataScheme("package")
        }
        
        registerReceiver(packageReceiver, filter)
        Log.d(TAG, "Package change receiver registered")
    }
    
    private fun escalateToHardLock(reason: String, triggerAction: String = "") {
        Log.e(TAG, "Escalating to hard lock: $reason (trigger: $triggerAction)")
        
        // Stop soft lock overlay (if using overlay service) and pass trigger action
        SoftLockOverlayService.stopOverlay(this)
        
        // Start soft lock overlay with specific trigger action for customized display
        SoftLockOverlayService.startOverlay(this, reason, triggerAction)
        
        // Apply hard lock with specific trigger action
        controlManager.applyHardLock(reason)
        
        // Stop monitoring since we're now in hard lock
        stopMonitoring()
    }
    
    private fun isUsbDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            Log.e(TAG, "Error checking USB debugging: ${e.message}")
            false
        }
    }
    
    private fun isDeveloperModeEnabled(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (e: Exception) {
            Log.e(TAG, "Error checking developer mode: ${e.message}")
            false
        }
    }
    
    private fun stopMonitoring() {
        Log.d(TAG, "Stopping soft lock monitoring")
        
        // Cancel monitoring job
        monitoringJob?.cancel()
        monitoringJob = null
        
        // Unregister package receiver
        packageReceiver?.let {
            try {
                unregisterReceiver(it)
                packageReceiver = null
                Log.d(TAG, "Package change receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
        
        stopSelf()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SoftLockMonitorService destroyed")
        
        // Clean up monitoring
        stopMonitoring()
    }
}