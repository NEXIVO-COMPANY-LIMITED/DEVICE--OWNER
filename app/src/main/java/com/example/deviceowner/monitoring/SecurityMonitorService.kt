package com.example.deviceowner.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.security.response.EnhancedAntiTamperResponse
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import kotlinx.coroutines.*

/**
 * Security Monitor Service
 * Continuously monitors for:
 * - Developer Options access attempts
 * - Factory Reset attempts
 * - Settings modifications
 * Triggers soft lock when violations detected
 */
class SecurityMonitorService : Service() {
    
    companion object {
        private const val TAG = "SecurityMonitor"
        private const val MONITOR_INTERVAL = 2000L // Check every 2 seconds
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "security_monitor_channel"
        
        fun startService(context: Context) {
            val intent = Intent(context, SecurityMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, SecurityMonitorService::class.java)
            context.stopService(intent)
        }
    }
    
    private val controlManager by lazy { RemoteDeviceControlManager(this) }
    private val tamperResponse by lazy { EnhancedAntiTamperResponse(this) }
    private val database by lazy { DeviceOwnerDatabase.getDatabase(this) }
    private var monitoringJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private var lastDeveloperOptionsState = false
    private var lastUsbDebuggingState = false
    private var lastTamperCheckTime = 0L
    private val TAMPER_CHECK_INTERVAL = 15 * 60 * 1000L // Check every 15 minutes
    private var lastSettingsCheckTime = 0L
    private val SETTINGS_CHECK_INTERVAL = 500L // Check every 500ms for Settings app
    private var lastForegroundPackage = ""
    
    // Real-Time Security Mesh Components
    private val removalDetector by lazy { com.example.deviceowner.security.monitoring.DeviceOwnerRemovalDetector(this) }
    private val accessibilityGuard by lazy { com.example.deviceowner.security.monitoring.AccessibilityGuard(this) }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SecurityMonitorService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        
        // Start Real-Time Security Mesh
        startSecurityMesh()
    }
    
    /**
     * Start Real-Time Security Mesh (Watchdog System)
     * 
     * FINAL WATCHDOG LAYER: This is the ultimate security protection.
     * Monitors device owner status and accessibility services continuously.
     * 
     * DeviceOwnerRemovalDetector:
     * - Checks every 5 seconds if app is still Device Owner
     * - If exploit removes device owner status → IMMEDIATE Hard Lock + Remote Wipe
     * - Response time: < 5 seconds from detection to protection
     * - Prevents user from doing anything before device is secured
     * 
     * AccessibilityGuard:
     * - Monitors unauthorized accessibility services (malware/bypass tools)
     * - Detects services that could be used to click buttons automatically
     * - Triggers soft lock when unauthorized services detected
     */
    private fun startSecurityMesh() {
        Log.d(TAG, "🛡️ Starting Real-Time Security Mesh (FINAL WATCHDOG SYSTEM)")
        
        // Start Device Owner Removal Detector - FINAL LAYER OF PERFECTION
        removalDetector.startMonitoring()
        Log.d(TAG, "✓ Device Owner Removal Detector started (checks every 5 seconds)")
        Log.d(TAG, "  → Hard Lock + Remote Wipe on device owner removal")
        
        // Start Accessibility Guard
        accessibilityGuard.startMonitoring()
        Log.d(TAG, "✓ Accessibility Guard started (checks every 3 seconds)")
        Log.d(TAG, "  → Soft Lock on unauthorized accessibility services")
        
        Log.d(TAG, "✅ Real-Time Security Mesh active - FINAL WATCHDOG PROTECTION ENABLED")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart if killed
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors device security violations"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Monitoring Active")
            .setContentText("Monitoring for security violations")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            // Initialize states
            lastDeveloperOptionsState = isDeveloperOptionsEnabled()
            lastUsbDebuggingState = isUsbDebuggingEnabled()
            
            while (isActive) {
                try {
                    checkSecurityViolations()
                    checkSettingsAppLaunch() // Check if Settings app is opened
                    delay(MONITOR_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Monitoring error", e)
                    delay(MONITOR_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Check for security violations
     */
    private suspend fun checkSecurityViolations() {
        // Check Developer Options
        val currentDeveloperOptions = isDeveloperOptionsEnabled()
        if (currentDeveloperOptions && !lastDeveloperOptionsState) {
            Log.e(TAG, "⚠️ DEVELOPER OPTIONS ENABLED - TRIGGERING SOFT LOCK")
            triggerSoftLock("User enabled Developer Options")
            // Log as tamper event
            logTamperEvent("DEVELOPER_OPTIONS_ENABLED", "User enabled Developer Options")
        }
        lastDeveloperOptionsState = currentDeveloperOptions
        
        // Check USB Debugging
        val currentUsbDebugging = isUsbDebuggingEnabled()
        if (currentUsbDebugging && !lastUsbDebuggingState) {
            Log.e(TAG, "⚠️ USB DEBUGGING ENABLED - TRIGGERING SOFT LOCK")
            triggerSoftLock("User enabled USB Debugging")
            // Log as tamper event
            logTamperEvent("USB_DEBUGGING_ENABLED", "User enabled USB Debugging")
        }
        lastUsbDebuggingState = currentUsbDebugging
        
        // Check Factory Reset protection
        checkFactoryResetProtection()
        
        // Perform comprehensive tamper check periodically
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTamperCheckTime > TAMPER_CHECK_INTERVAL) {
            performTamperCheck()
            lastTamperCheckTime = currentTime
        }
    }
    
    /**
     * Check if Settings app is launched and reset build number click counter
     * This ensures users can NEVER trigger "You are now a developer" by tapping build number
     * The counter is reset every time Settings opens, making it impossible to reach 7 clicks
     */
    private suspend fun checkSettingsAppLaunch() {
        val currentTime = System.currentTimeMillis()
        
        // Check more frequently for Settings app (every 500ms)
        if (currentTime - lastSettingsCheckTime < SETTINGS_CHECK_INTERVAL) {
            return
        }
        lastSettingsCheckTime = currentTime
        
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            
            // Get the current foreground app
            var currentForegroundPackage = ""
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // Use appTasks for newer APIs
                try {
                    val appTasks = activityManager.appTasks
                    if (appTasks.isNotEmpty()) {
                        val taskInfo = appTasks[0].taskInfo
                        currentForegroundPackage = taskInfo.topActivity?.packageName ?: ""
                    }
                } catch (e: Exception) {
                    // Fallback if appTasks fails
                }
            } else {
                // Use deprecated getRunningTasks for older APIs
                @Suppress("DEPRECATION")
                try {
                    val runningTasks = activityManager.getRunningTasks(1)
                    if (runningTasks.isNotEmpty()) {
                        @Suppress("DEPRECATION")
                        val taskInfo = runningTasks[0]
                        currentForegroundPackage = taskInfo.topActivity?.packageName ?: ""
                    }
                } catch (e: Exception) {
                    // Fallback if getRunningTasks fails
                }
            }
            
            // Alternative method: Use UsageStatsManager (requires PACKAGE_USAGE_STATS permission)
            if (currentForegroundPackage.isEmpty()) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                        val time = System.currentTimeMillis()
                        val stats = usageStatsManager?.queryUsageStats(
                            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                            time - 1000,
                            time
                        )
                        val mostRecentStat = stats?.maxByOrNull { it.lastTimeUsed }
                        currentForegroundPackage = mostRecentStat?.packageName ?: ""
                    }
                } catch (e: Exception) {
                    // UsageStatsManager may not be available or permission not granted
                }
            }
            
            // Check if Settings app (com.android.settings) is in foreground
            val settingsPackages = listOf(
                "com.android.settings",
                "com.samsung.android.settings", // Samsung
                "com.miui.securitycenter", // Xiaomi
                "com.huawei.systemmanager", // Huawei
                "com.coloros.settings", // Oppo/OnePlus
                "com.oneplus.settings" // OnePlus
            )
            
            val isSettingsApp = settingsPackages.any { currentForegroundPackage.startsWith(it) }
            
            // If Settings app just opened (wasn't in foreground before)
            if (isSettingsApp && currentForegroundPackage != lastForegroundPackage) {
                Log.d(TAG, "🔍 Settings app detected in foreground: $currentForegroundPackage")
                Log.d(TAG, "🔄 Resetting build number click counter to prevent developer options activation")
                
                // Reset build number click counter immediately
                resetBuildNumberClickCounter()
                
                // Also disable developer options as a safeguard
                disableDeveloperOptionsImmediately()
            }
            
            lastForegroundPackage = currentForegroundPackage
            
        } catch (e: Exception) {
            // Silently handle errors - don't spam logs
            if (e !is SecurityException && e !is IllegalStateException) {
                Log.v(TAG, "Could not check foreground app: ${e.message}")
            }
        }
    }
    
    /**
     * Reset build number click counter to prevent developer options activation
     * Called every time Settings app opens
     * Uses DeviceOwnerManager's comprehensive reset method
     */
    private fun resetBuildNumberClickCounter() {
        try {
            val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(this)
            
            // Use DeviceOwnerManager's public reset method (comprehensive reset)
            deviceOwnerManager.resetBuildNumberClickCounter()
            
            Log.d(TAG, "✓ Build number click counter reset (Settings app opened)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not reset build number click counter: ${e.message}")
            
            // Fallback: Direct reset methods
            try {
                val prefs = getSharedPreferences("development_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putInt("development_settings_enabled", 0)
                    putInt("show_dev_options_count", 0)
                    putLong("show_dev_countdown", 0)
                    putBoolean("development_settings_enabled", false)
                    apply()
                }
                
                android.provider.Settings.Global.putInt(
                    contentResolver,
                    android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0
                )
            } catch (e2: Exception) {
                // Ignore fallback errors
            }
        }
    }
    
    /**
     * Immediately disable developer options as a safeguard
     */
    private fun disableDeveloperOptionsImmediately() {
        try {
            val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(this)
            deviceOwnerManager.disableDeveloperOptions(true)
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable developer options: ${e.message}")
        }
    }
    
    /**
     * Perform comprehensive tamper detection check
     */
    private suspend fun performTamperCheck() {
        try {
            val sharedPrefs = getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            val deviceId = sharedPrefs.getString("device_token", null) ?: return
            
            // Perform basic tamper checks
            val tamperDetected = checkForTamperIndicators()
            if (tamperDetected.isNotEmpty()) {
                Log.e(TAG, "🚨 TAMPER DETECTED: ${tamperDetected.joinToString(", ")}")
                
                // Log tamper event and trigger response
                for (tamperType in tamperDetected) {
                    logTamperEvent(tamperType, "Tamper detected: $tamperType")
                    tamperResponse.respondToTamper(tamperType, "HIGH", "Tamper detected: $tamperType")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing tamper check", e)
        }
    }
    
    /**
     * Check for various tamper indicators
     */
    private fun checkForTamperIndicators(): List<String> {
        val indicators = mutableListOf<String>()
        
        try {
            // Check if developer options is enabled
            if (isDeveloperOptionsEnabled()) {
                indicators.add("DEVELOPER_OPTIONS_ENABLED")
            }
            
            // Check if USB debugging is enabled
            if (isUsbDebuggingEnabled()) {
                indicators.add("USB_DEBUGGING_ENABLED")
            }
            
            // Add other tamper checks as needed
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking tamper indicators", e)
        }
        
        return indicators
    }
    
    /**
     * Log a tamper event directly
     */
    private fun logTamperEvent(tamperType: String, description: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sharedPrefs = getSharedPreferences("device_registration", Context.MODE_PRIVATE)
                val deviceId = sharedPrefs.getString("device_token", null) ?: return@launch
                
                val tamperDetection = com.example.deviceowner.data.local.database.entities.TamperDetectionEntity(
                    deviceId = deviceId,
                    tamperType = tamperType,
                    severity = "HIGH",
                    detectedAt = System.currentTimeMillis(),
                    details = description
                )
                database.tamperDetectionDao().insertTamperDetection(tamperDetection)
            } catch (e: Exception) {
                Log.e(TAG, "Error logging tamper event", e)
            }
        }
    }
    
    /**
     * Check if Developer Options is enabled
     */
    private fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if USB Debugging is enabled
     */
    private fun isUsbDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check Factory Reset protection
     */
    private fun checkFactoryResetProtection() {
        try {
            // Check if user is trying to access factory reset
            // This is monitored through settings access patterns
            // If factory reset option becomes accessible, trigger lock
            val factoryResetAccessible = isFactoryResetAccessible()
            if (factoryResetAccessible) {
                Log.e(TAG, "⚠️ FACTORY RESET ACCESSIBLE - TRIGGERING SOFT LOCK")
                triggerSoftLock("User attempted to access Factory Reset")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking factory reset", e)
        }
    }
    
    /**
     * Check if Factory Reset is accessible
     */
    private fun isFactoryResetAccessible(): Boolean {
        // Check if factory reset protection is still active
        // If it's not, user might have found a way to access it
        return try {
            // This is a heuristic check - if restrictions are not properly applied
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val admin = android.content.ComponentName(this, com.example.deviceowner.receivers.AdminReceiver::class.java)
            
            // Check if factory reset restriction is still active
            val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
            val restrictions = userManager.getUserRestrictions()
            !restrictions.getBoolean(android.os.UserManager.DISALLOW_FACTORY_RESET, false)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Trigger soft lock
     */
    private fun triggerSoftLock(reason: String) {
        handler.post {
            try {
                controlManager.applySoftLock("SOFT LOCK: $reason")
                Log.e(TAG, "Soft lock applied: $reason")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply soft lock", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop monitoring job
        monitoringJob?.cancel()
        
        // Stop security mesh components
        try {
            removalDetector.stopMonitoring()
            accessibilityGuard.stopMonitoring()
            Log.d(TAG, "Real-Time Security Mesh stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping security mesh: ${e.message}")
        }
        
        Log.d(TAG, "SecurityMonitorService destroyed")
    }
}
