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
import com.example.deviceowner.core.device.DeviceDataCollector
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.security.response.EnhancedAntiTamperResponse
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.services.heartbeat.HeartbeatService
import com.example.deviceowner.services.reporting.ServerBugAndLogReporter
import kotlinx.coroutines.*

/**
 * Security Monitor Service – FOREGROUND SERVICE (notification shown).
 * Heartbeat must run in a foreground service so it continues when app is closed and keeps 30s interval.
 * Background service would be killed or throttled → heartbeat stops or big gaps between server receipts.
 * Continuously monitors for: Developer Options, Factory Reset, Settings; triggers soft lock on violation.
 */
class SecurityMonitorService : Service() {
    
    companion object {
        private const val TAG = "SecurityMonitor"
        private const val MONITOR_INTERVAL = 2000L // Check every 2 seconds
        /** Heartbeat every 30 seconds so Django receives data at this interval. */
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
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
    private var heartbeatService: HeartbeatService? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private var lastDeveloperOptionsState = false
    private var lastUsbDebuggingState = false
    private var lastTamperCheckTime = 0L
    private val TAMPER_CHECK_INTERVAL = 2000L // Check every 2 seconds (bootloader, etc.) - detect & block hapo hapo
    private var lastSettingsCheckTime = 0L
    private val SETTINGS_CHECK_INTERVAL = 500L // Check every 500ms for Settings app
    private var lastForegroundPackage = ""
    
    // Heartbeat recovery watchdog
    private var heartbeatWatchdogJob: Job? = null
    private val HEARTBEAT_WATCHDOG_INTERVAL = 60_000L // Check every 60 seconds
    // Throttle: report heartbeat block to server at most once per 5 min
    private var lastHeartbeatBlockReportTime = 0L
    private var lastHeartbeatBlockReason: String? = null
    private val HEARTBEAT_BLOCK_REPORT_THROTTLE_MS = 5 * 60 * 1000L
    
    // Real-Time Security Mesh Components
    private val removalDetector by lazy { com.example.deviceowner.security.monitoring.removal.DeviceOwnerRemovalDetector(this) }
    private val accessibilityGuard by lazy { com.example.deviceowner.security.monitoring.accessibility.AccessibilityGuard(this) }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SecurityMonitorService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        startHeartbeatLoop()
        startHeartbeatWatchdog()
        
        // Start Real-Time Security Mesh
        startSecurityMesh()
    }
    
    /**
     * Run heartbeat every 30 seconds so Django receives data at that interval.
     * Uses HeartbeatService in-process (Handler + 30s) so interval is exact. WorkManager
     * cannot run more often than 15 min, so we do NOT enqueue workers here – HeartbeatWorker
     * remains a backup when this service is not running (e.g. after reboot before app open).
     */
    private fun startHeartbeatLoop() {
        try {
            val deviceId = resolveDeviceIdForHeartbeat()
            if (deviceId.isNullOrBlank()) {
                Log.w(TAG, "⚠️ HEARTBEAT BLOCKED: device_id missing – heartbeat will start when device is registered")
                Log.w(TAG, "   Check: device_data.device_id_for_heartbeat, device_registration.device_id, device_owner_prefs")
                logHeartbeatBlockDiagnostics(null)
                reportHeartbeatBlockToServer("device_id_missing", "Heartbeat loop not started: device_id missing. Check device_data.device_id_for_heartbeat, device_registration.device_id.")
                // Schedule retry in 5 seconds
                handler.postDelayed({
                    startHeartbeatLoop()
                }, 5000L)
                return
            }
            
            // Validate device_id format
            if (deviceId.equals("unknown", ignoreCase = true) || deviceId.startsWith("ANDROID-")) {
                Log.w(TAG, "⚠️ HEARTBEAT BLOCKED: device_id is invalid (unknown or ANDROID-*). Value: ${deviceId.take(20)}")
                Log.w(TAG, "   Server expects server-assigned device_id from registration (e.g. DEV-B5AF7F0BEDEB)")
                logHeartbeatBlockDiagnostics(deviceId)
                reportHeartbeatBlockToServer("device_id_invalid", "Heartbeat loop not started: device_id is invalid (unknown or ANDROID-*). Value: ${deviceId.take(20)}. Server expects device_id from registration.")
                handler.postDelayed({
                    startHeartbeatLoop()
                }, 5000L)
                return
            }
            
            Log.i(TAG, "✅ Heartbeat using device_id: ${deviceId.take(8)}...")
            val apiClient = ApiClient()
            val deviceDataCollector = DeviceDataCollector(this)
            heartbeatService = HeartbeatService(this, apiClient, deviceDataCollector)
            
            // Schedule heartbeat with immediate first send
            heartbeatService!!.schedulePeriodicHeartbeat(deviceId)
            Log.i(TAG, "✅ Heartbeat loop started – first heartbeat IMMEDIATE, then every ${HEARTBEAT_INTERVAL_MS / 1000}s (in-process, exact interval)")
            Log.i(TAG, "   Device ID: ${deviceId.take(12)}...")
            Log.i(TAG, "   Interval: ${HEARTBEAT_INTERVAL_MS / 1000} seconds")
            Log.i(TAG, "   Service: In-process (Handler-based, not WorkManager)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Heartbeat start error: ${e.message}", e)
            Log.e(TAG, "   Stack trace: ${e.stackTraceToString()}")
            // Retry after 5 seconds
            handler.postDelayed({
                startHeartbeatLoop()
            }, 5000L)
        }
    }
    
    /** Resolve device_id for heartbeat – must be the server-returned id from registration (saved in device_data/device_registration by RegistrationSuccessActivity). */
    private fun resolveDeviceIdForHeartbeat(): String? {
        // Use the centralized DeviceIdProvider for consistency
        val deviceId = com.example.deviceowner.data.DeviceIdProvider.getDeviceId(this)
        if (!deviceId.isNullOrBlank()) {
            Log.d(TAG, "✅ Device ID resolved from DeviceIdProvider: ${deviceId.take(8)}...")
            return deviceId
        }
        
        // Fallback to direct SharedPreferences access
        val deviceDataPrefs = getSharedPreferences("device_data", Context.MODE_PRIVATE)
        val fromDeviceData = deviceDataPrefs.getString("device_id_for_heartbeat", null)
        if (!fromDeviceData.isNullOrBlank()) {
            Log.d(TAG, "✅ Device ID resolved from device_data: ${fromDeviceData.take(8)}...")
            return fromDeviceData
        }
        
        val deviceReg = getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            .getString("device_id", null)
        if (!deviceReg.isNullOrBlank()) {
            Log.d(TAG, "✅ Device ID resolved from device_registration: ${deviceReg.take(8)}...")
            deviceDataPrefs.edit().putString("device_id_for_heartbeat", deviceReg).apply()
            return deviceReg
        }
        
        val fromPrefs = com.example.deviceowner.utils.storage.SharedPreferencesManager(this).getDeviceIdForHeartbeat()
        if (!fromPrefs.isNullOrBlank()) {
            Log.d(TAG, "✅ Device ID resolved from SharedPreferencesManager: ${fromPrefs.take(8)}...")
            return fromPrefs
        }
        
        Log.w(TAG, "❌ Device ID not found in any location")
        return null
    }
    
    /** Log why heartbeat may be blocked (device_id sources empty or invalid). */
    private fun logHeartbeatBlockDiagnostics(invalidDeviceId: String?) {
        val deviceData = getSharedPreferences("device_data", Context.MODE_PRIVATE).getString("device_id_for_heartbeat", null)
        val deviceReg = getSharedPreferences("device_registration", Context.MODE_PRIVATE).getString("device_id", null)
        val fromPrefs = com.example.deviceowner.utils.storage.SharedPreferencesManager(this).getDeviceIdForHeartbeat()
        Log.d(TAG, "   device_data.device_id_for_heartbeat: ${deviceData?.take(12) ?: "null"}")
        Log.d(TAG, "   device_registration.device_id: ${deviceReg?.take(12) ?: "null"}")
        Log.d(TAG, "   SharedPreferencesManager.getDeviceIdForHeartbeat: ${fromPrefs?.take(12) ?: "null"}")
        if (invalidDeviceId != null) Log.d(TAG, "   invalid device_id was: $invalidDeviceId")
    }

    /** Report heartbeat block to tech logs + bugs API so backend sees why heartbeat is not sending. Throttled. */
    private fun reportHeartbeatBlockToServer(reason: String, message: String) {
        val now = System.currentTimeMillis()
        if (lastHeartbeatBlockReason == reason && (now - lastHeartbeatBlockReportTime) < HEARTBEAT_BLOCK_REPORT_THROTTLE_MS) return
        lastHeartbeatBlockReason = reason
        lastHeartbeatBlockReportTime = now
        ServerBugAndLogReporter.postLog("heartbeat", "Error", message, mapOf("block_reason" to reason))
        ServerBugAndLogReporter.postBug(
            title = "Heartbeat blocked: $reason",
            message = message,
            priority = "high",
            extraData = mapOf("block_reason" to reason)
        )
    }
    
    /**
     * Heartbeat Watchdog - Monitors and recovers heartbeat if it crashes.
     * Checks every 60 seconds if heartbeat is still running.
     * If not, restarts it automatically.
     */
    private fun startHeartbeatWatchdog() {
        heartbeatWatchdogJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            while (isActive) {
                try {
                    delay(HEARTBEAT_WATCHDOG_INTERVAL)
                    
                    // Check if heartbeat service is still running
                    if (heartbeatService == null) {
                        Log.w(TAG, "⚠️ Heartbeat service is null - restarting...")
                        startHeartbeatLoop()
                    } else {
                        // Heartbeat is running, continue monitoring
                        Log.d(TAG, "✓ Heartbeat watchdog: service is healthy")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat watchdog error: ${e.message}", e)
                }
            }
        }
        Log.i(TAG, "✅ Heartbeat watchdog started - will monitor every 60 seconds")
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
     * Check for security violations.
     * When Developer Options or USB Debugging are ON → automatically block user and apply hard lock (kiosk).
     * Bootloader unlocked = CRITICAL → hard lock. Other tamper = periodic check.
     */
    private suspend fun checkSecurityViolations() {
        val devOptEnabled = isDeveloperOptionsEnabled()
        val usbEnabled = isUsbDebuggingEnabled()

        // When developer options or USB debugging are detected ON → block user TOTALLY (hard lock).
        // Kwa muda inacheki tamper; ikigundua kuna tatizo inablock totally device again with hard lock.
        if (devOptEnabled || usbEnabled) {
            val reason = buildString {
                if (devOptEnabled) append("Developer options enabled")
                if (usbEnabled) {
                    if (devOptEnabled) append("; ")
                    append("USB debugging enabled")
                }
            }
            val tamperType = when {
                devOptEnabled && usbEnabled -> "DEVELOPER_MODE"
                devOptEnabled -> "DEVELOPER_MODE"
                else -> "USB_DEBUG"
            }
            Log.e(TAG, "🚨 SECURITY VIOLATION: $reason – inablock totally (hard lock)")
            handler.post {
                try {
                    controlManager.applyHardLock(
                        reason = "Security violation: $reason",
                        forceRestart = false,
                        forceFromServerOrMismatch = true,
                        tamperType = tamperType
                    )
                    // Send tamper event to server (same as other tamper sources – logs, offline queue if no network)
                    tamperResponse.sendTamperToBackendOnly(
                        tamperType = tamperType,
                        severity = "CRITICAL",
                        description = "Security violation: $reason",
                        extraData = mapOf(
                            "lock_applied_on_device" to "hard",
                            "lock_type" to "hard",
                            "tamper_source" to "security_monitor_service"
                        )
                    )
                    Log.i(TAG, "Tamper event sent to server: $tamperType")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply hard lock for developer/USB violation", e)
                }
            }
            disableDeveloperOptionsImmediately()
        }

        lastDeveloperOptionsState = devOptEnabled
        lastUsbDebuggingState = usbEnabled

        // Periodic tamper check: bootloader unlocked, etc.
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
     * Perform tamper detection - block IMMEDIATELY on device, no server wait.
     * Runs even when device not yet registered (blocks locally; server notified when possible).
     */
    private suspend fun performTamperCheck() {
        try {
            val tamperDetected = checkForTamperIndicators()
            if (tamperDetected.isNotEmpty()) {
                Log.e(TAG, "🚨 TAMPER DETECTED: ${tamperDetected.joinToString(", ")} - blocking user immediately")
                for (tamperType in tamperDetected) {
                    logTamperEvent(tamperType, "Tamper detected: $tamperType")
                    val severity = if (tamperType == "BOOTLOADER_UNLOCKED") "CRITICAL" else "HIGH"
                    tamperResponse.respondToTamper(
                        tamperType = tamperType,
                        severity = severity,
                        description = "Tamper detected: $tamperType",
                        extraData = mapOf(
                            "lock_applied_on_device" to "hard",
                            "lock_type" to "hard",
                            "tamper_source" to "security_monitor_periodic_check"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing tamper check", e)
        }
    }
    
    /**
     * Check for various tamper indicators (bootloader unlocked, etc.).
     * Developer options / USB debugging are handled in checkSecurityViolations with CRITICAL → hard lock.
     */
    private fun checkForTamperIndicators(): List<String> {
        val indicators = mutableListOf<String>()
        
        try {
            // Bootloader unlocked (e.g. after fastboot unlock) = CRITICAL
            val bootloaderEnforcer = com.example.deviceowner.security.enforcement.bootloader.BootloaderLockEnforcer(this)
            if (bootloaderEnforcer.isBootloaderUnlocked()) {
                indicators.add("BOOTLOADER_UNLOCKED")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking tamper indicators", e)
        }
        
        return indicators
    }
    
    /**
     * Log tamper event locally. Uses device_id if registered, else "UNREGISTERED" for audit.
     */
    private fun logTamperEvent(tamperType: String, description: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = getSharedPreferences("device_registration", Context.MODE_PRIVATE)
                    .getString("device_id", null)
                    ?: getSharedPreferences("device_data", Context.MODE_PRIVATE)
                        .getString("device_id_for_heartbeat", null)
                    ?: android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                    ?: "UNREGISTERED"
                val entity = com.example.deviceowner.data.local.database.entities.tamper.TamperDetectionEntity(
                    deviceId = deviceId,
                    tamperType = tamperType,
                    severity = "CRITICAL",
                    detectedAt = System.currentTimeMillis(),
                    details = description
                )
                database.tamperDetectionDao().insertTamperDetection(entity)
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
        monitoringJob?.cancel()
        heartbeatWatchdogJob?.cancel()
        heartbeatService?.cancelPeriodicHeartbeat()
        heartbeatService = null
        
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
