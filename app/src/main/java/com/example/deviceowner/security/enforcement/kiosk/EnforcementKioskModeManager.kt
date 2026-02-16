package com.example.deviceowner.security.enforcement.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.ui.activities.lock.HardLockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job

/**
 * Kiosk Mode Manager (enforcement)
 * Enforces kiosk mode by keeping the app in foreground
 * Prevents users from accessing other apps or system settings
 */
class EnforcementKioskModeManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EnforcementKioskModeManager"
        private const val CHECK_INTERVAL_MS = 2000L // Check every 2 seconds
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isKioskModeActive = false
    private var monitoringJob: kotlinx.coroutines.Job? = null
    
    /**
     * Enable kiosk mode
     */
    fun enableKioskMode() {
        if (isKioskModeActive) {
            Log.d(TAG, "Kiosk mode already active")
            return
        }
        
        Log.d(TAG, "🔒 Enabling Kiosk Mode...")
        
        try {
            // Step 1: Set lock task mode packages (Android 5.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val packages = arrayOf(context.packageName)
                devicePolicyManager.setLockTaskPackages(adminComponent, packages)
                Log.d(TAG, "✓ Lock task packages set")
            }
            
            // Step 2: Start monitoring
            startMonitoring()
            
            // Step 3: Launch kiosk activity
            launchKioskActivity()
            
            isKioskModeActive = true
            Log.d(TAG, "✅ Kiosk Mode enabled")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling kiosk mode", e)
        }
    }
    
    /**
     * Disable kiosk mode
     */
    fun disableKioskMode() {
        if (!isKioskModeActive) {
            Log.d(TAG, "Kiosk mode not active")
            return
        }
        
        Log.d(TAG, "Disabling Kiosk Mode...")
        
        try {
            stopMonitoring()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf())
                Log.d(TAG, "✓ Lock task packages cleared")
            }
            
            isKioskModeActive = false
            Log.d(TAG, "✅ Kiosk Mode disabled")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling kiosk mode", e)
        }
    }
    
    /**
     * Start monitoring for app switching
     */
    private fun startMonitoring() {
        monitoringJob?.cancel()
        
        monitoringJob = scope.launch {
            while (isKioskModeActive && isActive) {
                try {
                    enforceKioskMode()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in kiosk monitoring", e)
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }
    
    /**
     * Stop monitoring
     */
    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * Enforce kiosk mode by checking running tasks
     */
    private suspend fun enforceKioskMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val runningTasks = activityManager.appTasks
                if (runningTasks.isNotEmpty()) {
                    val topTask = runningTasks[0]
                    val topActivity = topTask.taskInfo?.topActivity
                    
                    if (topActivity != null && !topActivity.packageName.startsWith(context.packageName)) {
                        // Another app is in foreground - bring our app back
                        Log.w(TAG, "⚠️ Non-kiosk app detected: ${topActivity.packageName} - Bringing kiosk app to foreground")
                        bringKioskAppToForeground()
                    }
                }
            } else {
                // For older Android versions
                @Suppress("DEPRECATION")
                val runningTasks = activityManager.getRunningTasks(1)
                if (runningTasks.isNotEmpty()) {
                    val topTask = runningTasks[0]
                    val topActivity = topTask.topActivity
                    
                    if (topActivity != null && !topActivity.packageName.startsWith(context.packageName)) {
                        Log.w(TAG, "⚠️ Non-kiosk app detected: ${topActivity.packageName} - Bringing kiosk app to foreground")
                        bringKioskAppToForeground()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing kiosk mode", e)
        }
    }
    
    /**
     * Bring kiosk app to foreground
     */
    private fun bringKioskAppToForeground() {
        try {
            val intent = Intent(context, HardLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
            Log.d(TAG, "Kiosk app brought to foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Error bringing kiosk app to foreground", e)
        }
    }
    
    /**
     * Launch kiosk activity
     */
    private fun launchKioskActivity() {
        try {
            val intent = Intent(context, HardLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            Log.d(TAG, "Kiosk activity launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching kiosk activity", e)
        }
    }
    
    /**
     * Start lock task mode on an activity (call from Activity.onResume)
     */
    fun startLockTask(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.startLockTask()
                Log.d(TAG, "Lock task started on activity")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                activity.startLockTask()
                Log.d(TAG, "Lock task started on activity (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting lock task", e)
        }
    }
    
    /**
     * Stop lock task mode on an activity
     */
    fun stopLockTask(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.stopLockTask()
                Log.d(TAG, "Lock task stopped on activity")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                activity.stopLockTask()
                Log.d(TAG, "Lock task stopped on activity (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping lock task", e)
        }
    }
    
    /**
     * Check if kiosk mode is active
     */
    fun isKioskModeActive(): Boolean = isKioskModeActive
}
