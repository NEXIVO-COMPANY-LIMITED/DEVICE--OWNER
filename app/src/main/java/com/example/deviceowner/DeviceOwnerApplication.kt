package com.microspace.payo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.microspace.payo.data.DeviceIdProvider
import com.microspace.payo.device.DeviceOwnerManager
import com.microspace.payo.security.mode.CompleteSilentMode

import com.microspace.payo.services.data.LocalDataServerService
import com.microspace.payo.services.heartbeat.HeartbeatWorker
import com.microspace.payo.services.reporting.ServerBugAndLogReporter
import com.microspace.payo.services.sync.OfflineSyncWorker
import com.microspace.payo.update.scheduler.UpdateScheduler

class DeviceOwnerApplication : Application() {

    companion object {
        private const val TAG = "DeviceOwnerApplication"
        var currentActivity: Activity? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        
        // 1. Set up global exception handler FIRST
        setupGlobalExceptionHandler()

        Log.d(TAG, "DeviceOwner Application starting...")

        // 2. Repair data consistency
        DeviceIdProvider.verifyAndRepairConsistency(this)

        // 3. Initialize critical components
        try {
            if (DeviceOwnerManager(this).isDeviceOwner()) {
                CompleteSilentMode(this).enableCompleteSilentMode()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Silent mode failed: ${e.message}", e)
        }

        registerActivityLifecycleCallbacks(AppActivityLifecycleCallbacks())
        
        // 4. Start services and other tasks
        startServicesAndTasks()
    }

    private fun startServicesAndTasks() {
        // Start Local JSON Data Server
        LocalDataServerService.startService(this)

        // Network callback for offline sync
        registerNetworkCallbackForOfflineSync()

        // Auto-update scheduler
        if (DeviceOwnerManager(this).isDeviceOwner()) {
            UpdateScheduler.schedulePeriodicChecks(this)
        }

        // Initialize Monitoring Service if registered
        val regPrefs = getSharedPreferences("device_registration", Context.MODE_PRIVATE)
        val serverDeviceId = regPrefs.getString("device_id", null)
        if (!serverDeviceId.isNullOrEmpty()) {
            com.microspace.payo.monitoring.SecurityMonitorService.startService(this)
            // ✅ START PERIODIC HEARTBEAT WORKER
            HeartbeatWorker.enqueue(this)
        }
    }

    private fun registerNetworkCallbackForOfflineSync() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val work = OneTimeWorkRequestBuilder<OfflineSyncWorker>().build()
                WorkManager.getInstance(this@DeviceOwnerApplication).enqueueUniqueWork("OfflineSync", ExistingWorkPolicy.REPLACE, work)
            }
        })
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "❌❌❌ UNCAUGHT EXCEPTION ❌❌❌", throwable)
            
            // Post bug report to server
            ServerBugAndLogReporter.postException(throwable, "FATAL CRASH: ${throwable.javaClass.simpleName}")
            
            // Give the report a moment to send before crashing
            Thread.sleep(1000)

            // Let the default handler take over to crash the app
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private class AppActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {
            currentActivity = activity
        }
        override fun onActivityPaused(activity: Activity) {
            if (currentActivity == activity) currentActivity = null
        }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            if (currentActivity == activity) currentActivity = null
        }
    }
}
