package com.example.deviceowner

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.deviceowner.data.local.database.AppDatabase
import com.example.deviceowner.data.local.repository.LocalDeviceRepository
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import com.example.deviceowner.services.sync.OfflineSyncWorker
import com.example.deviceowner.ui.activities.registration.RegistrationSuccessActivity
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.security.mode.CompleteSilentMode
import com.example.deviceowner.security.monitoring.sim.SIMChangeDetector
import com.example.deviceowner.frp.CompleteFRPManager
import com.example.deviceowner.update.github.GitHubUpdateManager
import com.example.deviceowner.update.scheduler.UpdateScheduler
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.ui.activities.lock.HardLockActivity
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.example.deviceowner.utils.logging.LogManager
import com.example.deviceowner.services.reporting.ServerBugAndLogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application class for Device Owner app
 * Initializes logging system, offline/online sync, and other global components
 */
class DeviceOwnerApplication : Application() {

    companion object {
        private const val TAG = "DeviceOwnerApplication"
        @Volatile
        var currentActivity: Activity? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "DeviceOwner Application starting...")

        // Complete Silent Mode: Hide all management messages on every app start (only when Device Owner)
        try {
            if (DeviceOwnerManager(this).isDeviceOwner()) {
                CompleteSilentMode(this).enableCompleteSilentMode()
                Log.d(TAG, "Silent mode enabled on app start")
                // FRP (Factory Reset Protection): setup on first run or verify on later runs
                runCompleteFRPSetupOrVerify()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Silent mode failed: ${e.message}", e)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
                // When device is hard locked, HardLock must stay on top – bring it back if user reached another screen
                if (activity !is HardLockActivity && RemoteDeviceControlManager(this@DeviceOwnerApplication).isHardLocked()) {
                    val lockIntent = Intent(this@DeviceOwnerApplication, HardLockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        putExtra("lock_reason", RemoteDeviceControlManager(this@DeviceOwnerApplication).getLockReason())
                        putExtra("lock_timestamp", RemoteDeviceControlManager(this@DeviceOwnerApplication).getLockTimestamp())
                    }
                    try {
                        activity.startActivity(lockIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not bring HardLock to front: ${e.message}")
                    }
                }
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) currentActivity = null
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) currentActivity = null
            }
        })
        
        // Set up global exception handler
        setupGlobalExceptionHandler()
        
        // CRITICAL: Keep keyboard and touch working until user has completed registration (RegistrationSuccessActivity).
        // Only then allow server lock. Prevents keyboard/touch from breaking right after Device Owner install.
        val regPrefs = getSharedPreferences("device_registration", Context.MODE_PRIVATE)
        val controlPrefs = getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
        val hasDeviceId = !regPrefs.getString("device_id", null).isNullOrBlank()
        val registrationFlowComplete = controlPrefs.getBoolean("registration_flow_complete", false)
        val skipLock = !(hasDeviceId && registrationFlowComplete)
        getSharedPreferences("control_prefs", Context.MODE_PRIVATE).edit().putBoolean("skip_security_restrictions", skipLock).apply()
        getSharedPreferences("device_owner_prefs", Context.MODE_PRIVATE).edit().putBoolean("skip_security_restrictions", skipLock).apply()
        
        // Initialize logging system
        try {
            LogManager.initialize(this)
            ServerBugAndLogReporter.init(this)
            LogManager.setOnRemoteLogCallback { category, logLevel, message, extra ->
                ServerBugAndLogReporter.postLog(
                    ServerBugAndLogReporter.categoryToLogType(category.name),
                    logLevel,
                    message,
                    extra
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize logging system: ${e.message}", e)
        }
        
        // Start Local JSON Data Server
        try {
            com.example.deviceowner.services.data.LocalDataServerService.startService(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Local Data Server: ${e.message}")
        }
        
        // Network callback for offline sync
        registerNetworkCallbackForOfflineSync()

        // Auto-update: schedule periodic checks when Device Owner
        try {
            if (DeviceOwnerManager(this).isDeviceOwner()) {
                UpdateScheduler.schedulePeriodicChecks(this)
                CoroutineScope(Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(10000) // 10 seconds delay
                    Log.d(TAG, "Performing startup update check...")
                    try {
                        GitHubUpdateManager(this@DeviceOwnerApplication).checkAndUpdate()
                    } catch (e: Exception) {
                        Log.e(TAG, "Startup update check failed: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update scheduler init failed: ${e.message}", e)
        }

        // Initialize Heartbeat Service and SIM detection if registered
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverDeviceId = regPrefs.getString("device_id", null)
                if (!serverDeviceId.isNullOrEmpty()) {
                    getSharedPreferences("device_data", Context.MODE_PRIVATE).edit()
                        .putString("device_id_for_heartbeat", serverDeviceId).apply()
                    SharedPreferencesManager(this@DeviceOwnerApplication).setDeviceIdForHeartbeat(serverDeviceId)
                    com.example.deviceowner.monitoring.SecurityMonitorService.startService(this@DeviceOwnerApplication)

                    // SIM Change Detection: Initialize when device is registered
                    if (DeviceOwnerManager(this@DeviceOwnerApplication).isDeviceOwner()) {
                        try {
                            SIMChangeDetector(this@DeviceOwnerApplication).initialize()
                            Log.d(TAG, "SIM change detection initialized")
                        } catch (e: Exception) {
                            Log.e(TAG, "SIM detection init failed: ${e.message}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling heartbeat: ${e.message}")
            }
        }
    }

    /**
     * Complete FRP (email-based): setup on first run, verify on later runs.
     * After factory reset only abubakariabushekhe87@gmail.com can unlock.
     */
    private fun runCompleteFRPSetupOrVerify() {
        try {
            val frpManager = CompleteFRPManager(this)
            if (!frpManager.isFRPAlreadySetup()) {
                Log.i(TAG, "First run detected - setting up Complete FRP...")
                val success = frpManager.setupCompleteFRP()
                if (success) {
                    Log.i(TAG, "✓ Complete FRP configured. Email: ${CompleteFRPManager.COMPANY_FRP_EMAIL}. Wait 72h for full activation.")
                } else {
                    Log.e(TAG, "Complete FRP setup failed")
                }
            } else {
                frpManager.verifyFRPStillActive()
                if (frpManager.isFRPFullyActive()) {
                    Log.d(TAG, "✓ Complete FRP is fully active")
                } else {
                    val status = frpManager.getFRPStatus()
                    val hoursRemaining = status["hours_remaining"] as? Long ?: 72L
                    Log.d(TAG, "Complete FRP activation in progress ($hoursRemaining hours remaining)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Complete FRP setup/verify failed: ${e.message}", e)
        }
    }

    private fun registerNetworkCallbackForOfflineSync() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    val work = OneTimeWorkRequestBuilder<OfflineSyncWorker>().setConstraints(constraints).build()
                    WorkManager.getInstance(this@DeviceOwnerApplication).enqueueUniqueWork(
                        "OfflineSync",
                        ExistingWorkPolicy.REPLACE,
                        work
                    )
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback: ${e.message}")
        }
    }

    /**
     * Set up global exception handler to catch and log uncaught exceptions.
     * Removed the automatic redirect to RegistrationSuccessActivity to avoid masking registration errors.
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "❌ UNCAUGHT EXCEPTION in thread: ${thread.name}", throwable)
                
                // Log the error remotely for debugging
                ServerBugAndLogReporter.postException(throwable, "Uncaught exception in ${thread.name}")
                
                // If it's a background thread, don't crash the whole app process if possible
                val isBackgroundThread = thread.name?.let { name ->
                    name.contains("DefaultDispatcher", ignoreCase = true) ||
                    name.contains("Worker", ignoreCase = true) ||
                    name.contains("pool-", ignoreCase = true) ||
                    name.contains("heartbeat", ignoreCase = true)
                } ?: false
                
                if (isBackgroundThread) {
                    Log.w(TAG, "⚠️ Background exception handled, app process continues.")
                    return@setDefaultUncaughtExceptionHandler
                }

                // For main thread crashes, let the default handler take over or show a specific error activity.
                // We NO LONGER redirect to RegistrationSuccessActivity here because it masks registration bugs.
                defaultHandler?.uncaughtException(thread, throwable)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in exception handler: ${e.message}", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
