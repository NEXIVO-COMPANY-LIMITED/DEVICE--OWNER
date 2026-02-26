package com.microspace.payo.security.monitoring.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.microspace.payo.control.RemoteDeviceControlManager
import com.microspace.payo.device.DeviceOwnerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Accessibility Guard
 * Monitors for unauthorized Accessibility Services that could be used to bypass security
 * Malware or bypass tools often use accessibility services to automatically click buttons,
 * grant permissions, or uninstall apps
 * 
 * This guard detects and blocks unauthorized accessibility services
 */
class AccessibilityGuard(private val context: Context) {
    
    companion object {
        private const val TAG = "AccessibilityGuard"
        private const val CHECK_INTERVAL_MS = 3000L // Check every 3 seconds
        
        // Whitelist of allowed accessibility services (system services)
        private val ALLOWED_ACCESSIBILITY_SERVICES = setOf(
            "com.google.android.marvin.talkback", // Google TalkBack (screen reader)
            "com.samsung.android.accessibility", // Samsung accessibility
            "com.android.talkback", // Android TalkBack
            "com.google.android.accessibility", // Google accessibility
            // Add your own app's accessibility service if you have one
        )
    }
    
    private val accessibilityManager: AccessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    
    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val controlManager = RemoteDeviceControlManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isMonitoring = false
    private var lastAccessibilityServices = setOf<String>()
    
    /**
     * Start continuous monitoring of accessibility services
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Monitoring already started")
            return
        }
        
        isMonitoring = true
        Log.d(TAG, "🛡️ Starting Accessibility Guard Monitoring")
        
        scope.launch {
            // Initialize state
            lastAccessibilityServices = getEnabledAccessibilityServices()
            
            while (isMonitoring) {
                try {
                    checkAccessibilityServices()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking accessibility services", e)
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
        Log.d(TAG, "Accessibility Guard Monitoring stopped")
    }
    
    /**
     * Check for unauthorized accessibility services
     */
    private suspend fun checkAccessibilityServices() {
        val currentServices = getEnabledAccessibilityServices()
        
        // Check if any new services were enabled
        val newServices = currentServices - lastAccessibilityServices
        
        if (newServices.isNotEmpty()) {
            Log.w(TAG, "⚠️ New accessibility services detected: $newServices")
            
            // Check each new service
            newServices.forEach { serviceId ->
                if (!isAllowedService(serviceId)) {
                    Log.e(TAG, "🚨 UNAUTHORIZED ACCESSIBILITY SERVICE DETECTED: $serviceId")
                    handleUnauthorizedAccessibilityService(serviceId)
                } else {
                    Log.d(TAG, "✓ Allowed accessibility service: $serviceId")
                }
            }
        }
        
        // Check all current services for unauthorized ones
        val unauthorizedServices = currentServices.filter { !isAllowedService(it) }
        
        if (unauthorizedServices.isNotEmpty()) {
            Log.e(TAG, "🚨 UNAUTHORIZED ACCESSIBILITY SERVICES ACTIVE: $unauthorizedServices")
            unauthorizedServices.forEach { serviceId ->
                handleUnauthorizedAccessibilityService(serviceId)
            }
        }
        
        lastAccessibilityServices = currentServices
    }
    
    /**
     * Get list of enabled accessibility service IDs
     */
    private fun getEnabledAccessibilityServices(): Set<String> {
        return try {
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            
            enabledServices.map { it.id }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting enabled accessibility services: ${e.message}")
            emptySet()
        }
    }
    
    /**
     * Check if an accessibility service is allowed
     */
    private fun isAllowedService(serviceId: String): Boolean {
        // Check whitelist
        if (ALLOWED_ACCESSIBILITY_SERVICES.any { serviceId.startsWith(it) }) {
            return true
        }
        
        // Check if it's a system service (package starts with android or com.android)
        if (serviceId.startsWith("android/") || serviceId.startsWith("com.android.")) {
            // Allow system services but log them
            Log.d(TAG, "System accessibility service detected: $serviceId")
            return true
        }
        
        // Check if it's our own app
        if (serviceId.startsWith(context.packageName)) {
            return true
        }
        
        // All other services are unauthorized
        return false
    }
    
    /**
     * Handle unauthorized accessibility service detection
     */
    private suspend fun handleUnauthorizedAccessibilityService(serviceId: String) {
        Log.e(TAG, "═══════════════════════════════════════════════════════")
        Log.e(TAG, "🚨 UNAUTHORIZED ACCESSIBILITY SERVICE DETECTED")
        Log.e(TAG, "Service ID: $serviceId")
        Log.e(TAG, "═══════════════════════════════════════════════════════")
        
        try {
            // Step 1: Immediately trigger soft lock
            Log.e(TAG, "Step 1: Triggering SOFT LOCK...")
            controlManager.applySoftLock("SECURITY: Unauthorized accessibility service detected: $serviceId")
            
            // Step 2: Attempt to disable the accessibility service
            Log.e(TAG, "Step 2: Attempting to disable accessibility service...")
            disableAccessibilityService(serviceId)
            
            // Step 3: Log security event locally
            Log.e(TAG, "Step 3: Logging security event...")
            logSecurityBreach("UNAUTHORIZED_ACCESS", "Unauthorized accessibility service detected: $serviceId")
            
            // Step 4: Send tamper to backend (POST api/devices/{id}/tamper/ + tech log)
            Log.e(TAG, "Step 4: Sending tamper to backend...")
            com.microspace.payo.security.response.EnhancedAntiTamperResponse(context)
                .sendTamperToBackendOnly("UNAUTHORIZED_ACCESS", "HIGH", "Unauthorized accessibility service: $serviceId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling unauthorized accessibility service", e)
        }
    }
    
    /**
     * Attempt to disable an accessibility service
     * Note: This may not work if we don't have device owner privileges
     */
    private suspend fun disableAccessibilityService(serviceId: String) {
        try {
            if (!deviceOwnerManager.isDeviceOwner()) {
                Log.w(TAG, "Cannot disable accessibility service - not device owner")
                return
            }
            
            // Try to disable via Settings (requires device owner)
            try {
                val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                // Note: We can't programmatically disable accessibility services directly
                // But we can guide the user or use device owner restrictions
                Log.w(TAG, "Accessibility service disabling requires user interaction or system-level access")
                
                // Alternative: Use device owner to block accessibility service installation
                // This would prevent new services from being installed
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val adminComponent = android.content.ComponentName(context, com.microspace.payo.receivers.AdminReceiver::class.java)
                
                // Block installation of unknown apps (prevents new accessibility services)
                devicePolicyManager.addUserRestriction(
                    adminComponent,
                    android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES
                )
                
                Log.d(TAG, "Blocked unknown app installation to prevent new accessibility services")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error attempting to disable accessibility service: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in disableAccessibilityService: ${e.message}")
        }
    }
    
    /**
     * Log security breach to database
     */
    private suspend fun logSecurityBreach(breachType: String, description: String) {
        try {
            val deviceId = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
                .getString("device_id", null)
                ?: context.getSharedPreferences("device_data", Context.MODE_PRIVATE)
                    .getString("device_id_for_heartbeat", null)
            
            if (deviceId != null) {
                val database = com.microspace.payo.data.local.database.DeviceOwnerDatabase.getDatabase(context)
                
                val tamperDetection = com.microspace.payo.data.local.database.entities.tamper.TamperDetectionEntity(
                    deviceId = deviceId,
                    tamperType = breachType,
                    severity = "HIGH",
                    detectedAt = System.currentTimeMillis(),
                    details = description
                )
                database.tamperDetectionDao().insertTamperDetection(tamperDetection)
                Log.d(TAG, "Security breach logged to database")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging security breach", e)
        }
    }
    
    /**
     * Check accessibility services immediately (synchronous)
     */
    fun checkNow(): Set<String> {
        return getEnabledAccessibilityServices()
    }
    
    /**
     * Get list of unauthorized accessibility services
     */
    fun getUnauthorizedServices(): List<String> {
        val allServices = getEnabledAccessibilityServices()
        return allServices.filter { !isAllowedService(it) }
    }
}
