package com.example.deviceowner.security.monitoring

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.receivers.AdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Advanced Security Monitor
 * Monitors for:
 * - Screen recording attempts
 * - Accessibility service abuse
 * - Suspicious app installations
 * - Unauthorized system modifications
 */
class AdvancedSecurityMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "AdvancedSecurityMonitor"
        private const val CHECK_INTERVAL_MS = 3000L // Check every 3 seconds
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val controlManager = RemoteDeviceControlManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isMonitoring = false
    private var lastAccessibilityServices = setOf<String>()
    private var lastScreenRecordingState = false
    
    /**
     * Start advanced security monitoring
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Advanced monitoring already started")
            return
        }
        
        isMonitoring = true
        Log.d(TAG, "🔍 Starting Advanced Security Monitoring...")
        
        scope.launch {
            // Initialize states
            lastAccessibilityServices = getEnabledAccessibilityServices()
            lastScreenRecordingState = isScreenRecordingActive()
            
            while (isMonitoring) {
                try {
                    checkSecurityViolations()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in advanced monitoring", e)
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.d(TAG, "Advanced Security Monitoring stopped")
    }
    
    /**
     * Check for security violations
     */
    private suspend fun checkSecurityViolations() {
        // Check accessibility services
        checkAccessibilityServices()
        
        // Check screen recording
        checkScreenRecording()
        
        // Check for suspicious apps
        checkSuspiciousApps()
    }
    
    /**
     * Check for unauthorized accessibility services
     */
    private suspend fun checkAccessibilityServices() {
        try {
            val currentServices = getEnabledAccessibilityServices()
            
            // Check if new accessibility services were enabled
            val newServices = currentServices - lastAccessibilityServices
            if (newServices.isNotEmpty()) {
                Log.e(TAG, "⚠️ UNAUTHORIZED ACCESSIBILITY SERVICE ENABLED: $newServices")
                
                for (service in newServices) {
                    // Check if it's our own service (if we have one)
                    if (!service.contains(context.packageName)) {
                        Log.e(TAG, "🚨 BLOCKING UNAUTHORIZED ACCESSIBILITY SERVICE: $service")
                        disableAccessibilityService(service)
                        triggerSoftLock("Unauthorized accessibility service enabled: $service")
                    }
                }
            }
            
            lastAccessibilityServices = currentServices
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility services", e)
        }
    }
    
    /**
     * Check for screen recording
     */
    private suspend fun checkScreenRecording() {
        try {
            val isCurrentlyRecording = isScreenRecordingActive()
            
            if (isCurrentlyRecording && !lastScreenRecordingState) {
                Log.e(TAG, "⚠️ SCREEN RECORDING DETECTED - TRIGGERING SOFT LOCK")
                triggerSoftLock("Screen recording detected")
                logSecurityEvent("SCREEN_RECORDING_DETECTED", "User attempted to record screen")
            }
            
            lastScreenRecordingState = isCurrentlyRecording
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking screen recording", e)
        }
    }
    
    /**
     * Check for suspicious apps
     */
    private suspend fun checkSuspiciousApps() {
        try {
            val suspiciousApps = listOf(
                "com.termux",                    // Terminal emulator
                "jackpal.androidterm",           // Terminal emulator
                "com.topjohnwu.magisk",         // Root tool
                "com.kingroot.kinguser",        // Root tool
                "eu.chainfire.supersu",          // Root tool
                "com.noshufou.android.su",       // Root tool
                "com.koushikdutta.superuser",    // Root tool
                "com.zachspong.temprootremovejb", // Root tool
                "com.ramdroid.appquarantine",    // App quarantine bypass
                "com.android.vending",           // Google Play (if not allowed)
            )
            
            val packageManager = context.packageManager
            for (packageName in suspiciousApps) {
                try {
                    packageManager.getPackageInfo(packageName, 0)
                    // App is installed
                    Log.w(TAG, "⚠️ Suspicious app detected: $packageName")
                    
                    // Try to uninstall or hide it
                    if (devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                        try {
                            devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
                            Log.d(TAG, "✓ Hidden suspicious app: $packageName")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not hide app: $packageName", e)
                        }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    // App not installed - good
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking suspicious apps", e)
        }
    }
    
    /**
     * Get enabled accessibility services
     */
    private fun getEnabledAccessibilityServices(): Set<String> {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            enabledServices.split(":").filter { it.isNotEmpty() }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting accessibility services", e)
            emptySet()
        }
    }
    
    /**
     * Check if screen recording is active
     */
    private fun isScreenRecordingActive(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                // Note: This is a heuristic check - we can't directly detect if recording is active
                // But we can check for MediaProjection service
                false // Simplified - would need more complex detection
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Disable accessibility service
     */
    private fun disableAccessibilityService(serviceName: String) {
        try {
            // Remove from enabled services
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val servicesList = enabledServices.split(":").toMutableList()
            servicesList.remove(serviceName)
            
            val newEnabledServices = servicesList.joinToString(":")
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newEnabledServices
            )
            
            Log.d(TAG, "✓ Disabled accessibility service: $serviceName")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling accessibility service", e)
        }
    }
    
    /**
     * Trigger soft lock
     */
    private fun triggerSoftLock(reason: String) {
        try {
            controlManager.applySoftLock("SOFT LOCK: $reason")
            Log.e(TAG, "Soft lock applied: $reason")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply soft lock", e)
        }
    }
    
    /**
     * Log security event
     */
    private suspend fun logSecurityEvent(eventType: String, description: String) {
        try {
            val sharedPrefs = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            val deviceId = sharedPrefs.getString("device_token", null)
            
            if (deviceId != null) {
                val database = com.example.deviceowner.data.local.database.DeviceOwnerDatabase.getDatabase(context)
                
                val tamperDetection = com.example.deviceowner.data.local.database.entities.TamperDetectionEntity(
                    deviceId = deviceId,
                    tamperType = eventType,
                    severity = "HIGH",
                    detectedAt = System.currentTimeMillis(),
                    details = description
                )
                database.tamperDetectionDao().insertTamperDetection(tamperDetection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging security event", e)
        }
    }
}
