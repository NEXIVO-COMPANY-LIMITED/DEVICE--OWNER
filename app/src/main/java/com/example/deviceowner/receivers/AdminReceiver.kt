package com.example.deviceowner.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.managers.UninstallPreventionManager
import com.example.deviceowner.managers.device.ProvisioningManager
import com.example.deviceowner.utils.logging.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        // Initialize file logger
        val fileLogger = FileLogger.getInstance(context)
        fileLogger.logInstallationStep("Device Admin Enabled", "Starting Device Owner installation process")
        fileLogger.i(TAG, "========================================")
        fileLogger.i(TAG, "Device Admin Enabled")
        fileLogger.i(TAG, "Intent action: ${intent.action}")
        fileLogger.i(TAG, "========================================")
        
        try {
            super.onEnabled(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in super.onEnabled: ${e.message}", e)
            fileLogger.e(TAG, "Error in super.onEnabled: ${e.message}", e)
            // Continue anyway
        }
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "Device Admin Enabled")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "========================================")
        
        // Check if this is during provisioning
        val isProvisioning = try {
            intent.action == "android.app.action.PROVISION_MANAGED_DEVICE" ||
            intent.action == "android.app.action.PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE"
        } catch (e: Exception) {
            Log.e(TAG, "Error checking provisioning status: ${e.message}", e)
            fileLogger.e(TAG, "Error checking provisioning status: ${e.message}", e)
            true // Assume provisioning to be safe
        }
        Log.d(TAG, "Is provisioning: $isProvisioning")
        fileLogger.logInstallationStep("Provisioning Check", "Is provisioning: $isProvisioning")
        
        // CRITICAL: Immediately enforce factory reset and developer options blocking
        // This must happen BEFORE any other initialization to prevent user access
        try {
            fileLogger.logInstallationStep("Immediate Policy Enforcement", "Enforcing factory reset and developer options blocking")
            enforceCriticalPoliciesImmediately(context, fileLogger)
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing critical policies immediately: ${e.message}", e)
            fileLogger.e(TAG, "Error enforcing critical policies immediately: ${e.message}", e)
            // Continue anyway - ProvisioningManager will retry
        }
        
        try {
            // Use the new comprehensive ProvisioningManager
            val provisioningManager = ProvisioningManager(context)
            provisioningManager.initializeProvisioning(isProvisioning)
            
            Log.d(TAG, "✓ ProvisioningManager initialized - comprehensive provisioning started")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "CRITICAL: Out of memory initializing ProvisioningManager", e)
            // Fallback to minimal initialization
            try {
                minimalInitialization(context, isProvisioning)
            } catch (e2: Exception) {
                Log.e(TAG, "CRITICAL: Minimal initialization also failed!", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to initialize ProvisioningManager", e)
            
            // Fallback to legacy initialization if ProvisioningManager fails
            Log.w(TAG, "Falling back to legacy initialization...")
            try {
                legacyInitialization(context, isProvisioning)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "CRITICAL: Legacy initialization also failed!", fallbackError)
                
                // Last resort: minimal initialization
                try {
                    minimalInitialization(context, isProvisioning)
                } catch (e2: Exception) {
                    Log.e(TAG, "CRITICAL: Even minimal initialization failed!", e2)
                    // At this point, we've tried everything - log and continue
                }
            }
        }
    }
    
    /**
     * Minimal initialization - only critical operations
     * Used as last resort if everything else fails
     */
    private fun minimalInitialization(context: Context, isProvisioning: Boolean) {
        Log.d(TAG, "=== MINIMAL INITIALIZATION (Last Resort) ===")
        try {
            // Only try to verify device owner - nothing else
            try {
                val managerClass = Class.forName("com.example.deviceowner.managers.DeviceOwnerManager")
                val constructor = managerClass.getConstructor(Context::class.java)
                val manager = constructor.newInstance(context)
                val isDeviceOwnerMethod = managerClass.getMethod("isDeviceOwner")
                val isDeviceOwner = isDeviceOwnerMethod.invoke(manager) as Boolean
                Log.d(TAG, "Minimal check - Device Owner: $isDeviceOwner")
            } catch (e: Exception) {
                Log.e(TAG, "Error in minimal initialization: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Minimal initialization error: ${e.message}", e)
            // Don't throw - we've exhausted all options
        }
    }
    
    /**
     * Legacy initialization method (fallback)
     * Kept for compatibility if ProvisioningManager fails
     */
    private fun legacyInitialization(context: Context, isProvisioning: Boolean) {
        try {
            val managerClass = Class.forName("com.example.deviceowner.managers.DeviceOwnerManager")
            val constructor = managerClass.getConstructor(Context::class.java)
            val manager = constructor.newInstance(context)
            
            val isDeviceOwnerMethod = managerClass.getMethod("isDeviceOwner")
            val isDeviceOwner = isDeviceOwnerMethod.invoke(manager) as Boolean
            Log.d(TAG, "Device Owner Status: $isDeviceOwner")
            
            if (!isDeviceOwner) {
                Log.e(TAG, "WARNING: Device admin enabled but NOT device owner!")
            } else {
                Log.d(TAG, "✓ Device Owner confirmed")
                
                // Immediately enforce critical restrictions
                try {
                    val disableDevOptionsMethod = managerClass.getMethod("disableDeveloperOptions", Boolean::class.javaPrimitiveType)
                    disableDevOptionsMethod.invoke(manager, true)
                    Log.d(TAG, "✓ Developer options disabled")
                } catch (e: Exception) {
                    Log.e(TAG, "Error disabling developer options: ${e.message}", e)
                }
                
                try {
                    val preventFactoryResetMethod = managerClass.getMethod("preventFactoryReset")
                    preventFactoryResetMethod.invoke(manager)
                    Log.d(TAG, "✓ Factory reset prevented")
                } catch (e: Exception) {
                    Log.e(TAG, "Error preventing factory reset: ${e.message}", e)
                }
            }
            
            val initMethod = managerClass.getMethod("initializeDeviceOwner")
            initMethod.invoke(manager)
            
            // Start services
            val scope = CoroutineScope(Dispatchers.Default)
            scope.launch {
                if (isProvisioning) {
                    delay(2000)
                }
                startAllCriticalServices(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in legacy initialization", e)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device admin disabled")
        // Handle device owner removal
        try {
            val managerClass = Class.forName("com.example.deviceowner.managers.DeviceOwnerManager")
            val constructor = managerClass.getConstructor(Context::class.java)
            val manager = constructor.newInstance(context)
            val onRemoveMethod = managerClass.getMethod("onDeviceOwnerRemoved")
            onRemoveMethod.invoke(manager)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling device owner removal", e)
        }
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.d(TAG, "Lock task mode entering: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.d(TAG, "Lock task mode exiting")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "Received intent: ${intent.action}")
        
        // Handle PROVISION_MANAGED_DEVICE intent explicitly
        if (intent.action == "android.app.action.PROVISION_MANAGED_DEVICE") {
            Log.d(TAG, "PROVISION_MANAGED_DEVICE intent received - provisioning in progress")
            // The system will automatically call onEnabled() after provisioning
        }
    }

    /**
     * Start all critical services required for Device Owner functionality
     * This ensures all services are running after provisioning completes
     * 
     * Services started in order:
     * 1. UnifiedHeartbeatService - Core device monitoring and backend communication
     * 2. CommandQueueService - Offline command processing
     * 3. DeviceOwnerRecoveryService - Device owner status monitoring and recovery
     * 4. TamperDetectionService - Continuous tamper detection and security monitoring
     * 5. FlashingDetectionService - Real-time flashing detection and blocking
     */
    private fun startAllCriticalServices(context: Context) {
        Log.d(TAG, "Starting all critical services...")
        
        // 1. UnifiedHeartbeatService - Core service for device monitoring and backend communication
        startHeartbeatService(context)
        
        // 2. CommandQueueService - Processes offline commands (Feature 4.9)
        startCommandQueueService(context)
        
        // 3. DeviceOwnerRecoveryService - Monitors and recovers device owner status
        startDeviceOwnerRecoveryService(context)
        
        // 4. TamperDetectionService - Continuous tamper detection and security monitoring
        startTamperDetectionService(context)
        
        // 5. FlashingDetectionService - Real-time flashing detection and blocking
        startFlashingDetectionService(context)
        
        // 6. ComprehensiveSecurityService - Continuous security monitoring and enforcement
        startComprehensiveSecurityService(context)
        
        Log.d(TAG, "✓ All critical services started")
    }
    
    /**
     * Helper method to start a service properly
     * FIXED: Uses startForegroundService() for Android 8.0+ and application context
     * This ensures services actually start during the quick UI transitions
     */
    private fun startServiceSafely(context: Context, serviceClass: Class<*>, serviceName: String) {
        try {
            val appContext = context.applicationContext
            val intent = Intent(appContext, serviceClass)
            
            // Use startForegroundService() for Android 8.0+ (API 26+)
            // This is CRITICAL - regular startService() fails silently on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
                Log.d(TAG, "✓ Started $serviceName as foreground service (Android 8.0+)")
            } else {
                appContext.startService(intent)
                Log.d(TAG, "✓ Started $serviceName (Android < 8.0)")
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException starting service $serviceName: ${e.message}", e)
            // Try fallback for older Android versions
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    val appContext = context.applicationContext
                    val intent = Intent(appContext, serviceClass)
                    appContext.startService(intent)
                    Log.d(TAG, "✓ Started $serviceName (fallback)")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "✗ Fallback startService also failed for $serviceName: ${e2.message}", e2)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting service $serviceName: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to start $serviceName: ${e.message}", e)
        }
    }
    
    /**
     * Start the UnifiedHeartbeatService to begin sending heartbeat data to backend
     */
    private fun startHeartbeatService(context: Context) {
        startServiceSafely(context, com.example.deviceowner.services.UnifiedHeartbeatService::class.java, "UnifiedHeartbeatService")
    }
    
    /**
     * Start CommandQueueService for offline command processing
     * Feature 4.9: Offline Command Queue
     */
    private fun startCommandQueueService(context: Context) {
        startServiceSafely(context, com.example.deviceowner.services.CommandQueueService::class.java, "CommandQueueService")
    }
    
    /**
     * Start DeviceOwnerRecoveryService for continuous device owner monitoring
     * Monitors device owner status and automatically recovers if lost
     */
    private fun startDeviceOwnerRecoveryService(context: Context) {
        startServiceSafely(context, com.example.deviceowner.services.DeviceOwnerRecoveryService::class.java, "DeviceOwnerRecoveryService")
    }
    
    /**
     * Start TamperDetectionService for continuous tamper detection
     * Monitors device for tampering (root, bootloader unlock, custom ROM, etc.)
     * and triggers security responses based on severity
     */
    private fun startTamperDetectionService(context: Context) {
        startServiceSafely(context, com.example.deviceowner.services.TamperDetectionService::class.java, "TamperDetectionService")
    }
    
    /**
     * Start FlashingDetectionService for real-time flashing detection
     * Monitors for device flashing attempts and blocks immediately
     */
    private fun startFlashingDetectionService(context: Context) {
        startServiceSafely(context, com.example.deviceowner.services.FlashingDetectionService::class.java, "FlashingDetectionService")
    }
    
    /**
     * Start ComprehensiveSecurityService for continuous security monitoring
     * Monitors and enforces all security measures:
     * - Uninstall prevention
     * - Flashing prevention
     * - USB debugging blocking
     * - Safe mode prevention
     * - Reboot prevention
     */
    private fun startComprehensiveSecurityService(context: Context) {
        startServiceSafely(context, com.example.deviceowner.services.ComprehensiveSecurityService::class.java, "ComprehensiveSecurityService")
    }
    
    /**
     * IMMEDIATELY enforce critical policies (factory reset and developer options)
     * This is called RIGHT AFTER Device Owner is enabled, before any other initialization
     * This prevents users from accessing factory reset or developer options during the brief window
     * between Device Owner installation and full provisioning completion
     */
    private fun enforceCriticalPoliciesImmediately(context: Context, fileLogger: FileLogger) {
        Log.d(TAG, "=== IMMEDIATELY ENFORCING CRITICAL POLICIES ===")
        fileLogger.logInstallationStep("Immediate Policy Enforcement", "Starting immediate enforcement of critical policies")
        
        try {
            val managerClass = Class.forName("com.example.deviceowner.managers.DeviceOwnerManager")
            val constructor = managerClass.getConstructor(Context::class.java)
            val manager = constructor.newInstance(context)
            
            // Check if device owner is active
            val isDeviceOwnerMethod = managerClass.getMethod("isDeviceOwner")
            val isDeviceOwner = isDeviceOwnerMethod.invoke(manager) as Boolean
            
            if (!isDeviceOwner) {
                Log.w(TAG, "⚠ Cannot enforce policies - not device owner yet")
                return
            }
            
            Log.d(TAG, "✓ Device Owner confirmed - enforcing policies IMMEDIATELY")
            
            // 1. IMMEDIATELY disable developer options
            try {
                fileLogger.logPolicyEnforcement("Developer Options", "Attempting to disable")
                val disableDevOptionsMethod = managerClass.getMethod("disableDeveloperOptions", Boolean::class.javaPrimitiveType)
                val devOptionsResult = disableDevOptionsMethod.invoke(manager, true) as Boolean
                if (devOptionsResult) {
                    Log.d(TAG, "✓✓✓ Developer options IMMEDIATELY disabled ✓✓✓")
                    fileLogger.logPolicyEnforcement("Developer Options", "SUCCESS - Disabled")
                } else {
                    Log.e(TAG, "✗ Failed to disable developer options immediately - will retry")
                    fileLogger.logPolicyEnforcement("Developer Options", "FAILED - Will retry")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception disabling developer options immediately: ${e.message}", e)
                fileLogger.e(TAG, "Exception disabling developer options immediately: ${e.message}", e)
            }
            
            // 2. IMMEDIATELY prevent factory reset
            try {
                fileLogger.logPolicyEnforcement("Factory Reset", "Attempting to prevent")
                val preventFactoryResetMethod = managerClass.getMethod("preventFactoryReset")
                val factoryResetResult = preventFactoryResetMethod.invoke(manager) as Boolean
                if (factoryResetResult) {
                    Log.d(TAG, "✓✓✓ Factory reset IMMEDIATELY prevented ✓✓✓")
                    fileLogger.logPolicyEnforcement("Factory Reset", "SUCCESS - Prevented")
                } else {
                    Log.e(TAG, "✗ Failed to prevent factory reset immediately - will retry")
                    fileLogger.logPolicyEnforcement("Factory Reset", "FAILED - Will retry")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception preventing factory reset immediately: ${e.message}", e)
                fileLogger.e(TAG, "Exception preventing factory reset immediately: ${e.message}", e)
            }
            
            // 3. Verify restrictions are enforced
            try {
                fileLogger.logInstallationStep("Policy Verification", "Verifying all restrictions")
                val verifyMethod = managerClass.getMethod("verifyAndEnforceCriticalRestrictions")
                val verified = verifyMethod.invoke(manager) as Boolean
                if (verified) {
                    Log.d(TAG, "✓✓✓ All critical restrictions IMMEDIATELY verified ✓✓✓")
                    fileLogger.logPolicyEnforcement("All Restrictions", "SUCCESS - Verified")
                } else {
                    Log.w(TAG, "⚠ Some restrictions not verified - will retry in ProvisioningManager")
                    fileLogger.logPolicyEnforcement("All Restrictions", "PARTIAL - Will retry")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception verifying restrictions: ${e.message}", e)
                fileLogger.e(TAG, "Exception verifying restrictions: ${e.message}", e)
            }
            
            Log.d(TAG, "=== IMMEDIATE POLICY ENFORCEMENT COMPLETE ===")
            fileLogger.logInstallationStep("Immediate Policy Enforcement", "COMPLETE")
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR enforcing policies immediately: ${e.message}", e)
            // Don't throw - ProvisioningManager will handle retry
        }
    }
}