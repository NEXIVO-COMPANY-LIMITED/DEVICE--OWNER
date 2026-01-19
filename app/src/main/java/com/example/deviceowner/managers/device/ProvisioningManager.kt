package com.example.deviceowner.managers.device

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.managers.DeviceOwnerManager
import com.example.deviceowner.managers.UninstallPreventionManager
import com.example.deviceowner.ui.activities.DeviceOwnerInstallationStatusActivity
import com.example.deviceowner.utils.logging.FileLogger
import kotlinx.coroutines.*

/**
 * Comprehensive Provisioning Manager
 * Coordinates the entire device owner provisioning and initialization flow
 * Ensures all services, policies, and features are properly initialized
 * 
 * FIXED: Uses startForegroundService() for Android 8.0+ and application context
 * to ensure services start correctly during provisioning
 */
class ProvisioningManager(private val context: Context) {

    companion object {
        private const val TAG = "ProvisioningManager"
        
        // Provisioning phases
        private const val PHASE_VERIFICATION = 1
        private const val PHASE_CRITICAL_POLICIES = 2
        private const val PHASE_SERVICES = 3
        private const val PHASE_FEATURES = 4
        private const val PHASE_VERIFICATION_FINAL = 5
    }

    // FIXED: Use application context to avoid issues during provisioning
    private val appContext: Context = context.applicationContext
    private val deviceOwnerManager = DeviceOwnerManager(appContext)
    private val statusTracker = ProvisioningStatusTracker(appContext)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Complete provisioning initialization
     * CRASH-PROOF: This method NEVER throws exceptions
     * All errors are caught and logged, provisioning continues
     */
    fun initializeProvisioning(isProvisioning: Boolean = true, fileLogger: FileLogger? = null) {
        val logger = fileLogger ?: FileLogger.getInstance(appContext)
        
        try {
            statusTracker.markProvisioningStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking provisioning started: ${e.message}", e)
            logger.e(TAG, "Error marking provisioning started: ${e.message}", e)
            // Continue anyway
        }
        
        Log.d(TAG, "=== STARTING COMPREHENSIVE PROVISIONING INITIALIZATION ===")
        Log.d(TAG, "Is provisioning: $isProvisioning")
        logger.logInstallationStep("Provisioning Initialization", "Starting comprehensive provisioning - Is provisioning: $isProvisioning")
        logger.i(TAG, "=== STARTING COMPREHENSIVE PROVISIONING INITIALIZATION ===")
        
        scope.launch {
            try {
                // Phase 1: Verification
                try {
                    statusTracker.updatePhase("Verification")
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating phase: ${e.message}")
                    logger.w(TAG, "Error updating phase: ${e.message}")
                }
                
                logger.logProvisioningPhase("Phase 1: Verification", "Starting")
                val verificationPassed = try {
                    verifyDeviceOwnerStatus(logger)
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL ERROR in verification: ${e.message}", e)
                    logger.e(TAG, "CRITICAL ERROR in verification: ${e.message}", e)
                    false
                }
                
                if (!verificationPassed) {
                    Log.e(TAG, "CRITICAL: Device owner verification failed!")
                    logger.logProvisioningPhase("Phase 1: Verification", "FAILED")
                    logger.e(TAG, "CRITICAL: Device owner verification failed!")
                    try {
                        statusTracker.markProvisioningCompleted(false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking provisioning failed: ${e.message}", e)
                        logger.e(TAG, "Error marking provisioning failed: ${e.message}", e)
                    }
                    return@launch
                }
                logger.logProvisioningPhase("Phase 1: Verification", "SUCCESS")
                
                // FIXED: Increased delay during provisioning to ensure system is fully ready
                // This happens during "check for updates" / "Google services" screens
                if (isProvisioning) {
                    try {
                        Log.d(TAG, "Waiting 5 seconds for system to stabilize during provisioning...")
                        delay(5000) // Increased from 2000 to 5000
                    } catch (e: CancellationException) {
                        throw e // Re-throw cancellation
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during delay: ${e.message}", e)
                        // Continue without delay
                    }
                }
                
                // Phase 2: Critical Policies (IMMEDIATE) - MUST SUCCEED
                try {
                    statusTracker.updatePhase("Critical Policies")
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating phase: ${e.message}")
                    logger.w(TAG, "Error updating phase: ${e.message}")
                }
                logger.logProvisioningPhase("Phase 2: Critical Policies", "Starting")
                try {
                    applyCriticalPolicies(logger)
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL ERROR in applyCriticalPolicies: ${e.message}", e)
                    logger.e(TAG, "CRITICAL ERROR in applyCriticalPolicies: ${e.message}", e)
                    // Retry in background but continue
                    retryPoliciesInBackground()
                }
                
                // Phase 3: Services (STAGGERED) - MUST ALL START
                try {
                    statusTracker.updatePhase("Service Startup")
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating phase: ${e.message}")
                }
                try {
                    startAllServicesStaggered(isProvisioning, logger)
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL ERROR in startAllServicesStaggered: ${e.message}", e)
                    logger.e(TAG, "CRITICAL ERROR in startAllServicesStaggered: ${e.message}", e)
                    // Retry in background but continue
                    val services = listOf(
                        ServiceInfo("UnifiedHeartbeatService", "com.example.deviceowner.services.UnifiedHeartbeatService", 0),
                        ServiceInfo("ComprehensiveSecurityService", "com.example.deviceowner.services.ComprehensiveSecurityService", 0),
                        ServiceInfo("CommandQueueService", "com.example.deviceowner.services.CommandQueueService", 0),
                        ServiceInfo("TamperDetectionService", "com.example.deviceowner.services.TamperDetectionService", 0),
                        ServiceInfo("FlashingDetectionService", "com.example.deviceowner.services.FlashingDetectionService", 0),
                        ServiceInfo("DeviceOwnerRecoveryService", "com.example.deviceowner.services.DeviceOwnerRecoveryService", 0),
                    )
                    retryServicesInBackground(services)
                }
                
                // Phase 4: Features - MUST ALL INITIALIZE
                try {
                    statusTracker.updatePhase("Feature Initialization")
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating phase: ${e.message}")
                }
                try {
                    initializeAllFeatures()
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL ERROR in initializeAllFeatures: ${e.message}", e)
                    // Retry in background but continue
                    retryFeaturesInBackground()
                }
                
                // Phase 5: Final Verification - VERIFY EVERYTHING IS INSTALLED
                try {
                    statusTracker.updatePhase("Final Verification")
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating phase: ${e.message}")
                }
                
                var success = try {
                    verifyCompleteInitialization(logger)
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL ERROR in verifyCompleteInitialization: ${e.message}", e)
                    false
                }
                
                // If verification failed, keep retrying until FULL installation
                if (!success) {
                    Log.w(TAG, "Initial verification incomplete - starting aggressive retry for FULL installation...")
                    logger.w(TAG, "Initial verification incomplete - starting aggressive retry for FULL installation...")
                    success = retryUntilFullInstallation(logger)
                }
                
                // Only mark as complete if FULL installation is achieved
                if (success) {
                    try {
                        statusTracker.markProvisioningCompleted(true)
                        Log.d(TAG, "=== FULL PROVISIONING INSTALLATION COMPLETE ===")
                        Log.d(TAG, "✓✓✓ ALL COMPONENTS INSTALLED AND VERIFIED ✓✓✓")
                        logger.logInstallationStep("Provisioning Complete", "SUCCESS - Launching installation status screen")
                        
                        // Launch installation status screen automatically after a short delay
                        // This ensures the system is ready and UI can be displayed
                        scope.launch {
                            delay(2000) // Wait 2 seconds for system to stabilize
                            launchInstallationStatusScreen(appContext, true)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking provisioning completed: ${e.message}", e)
                        logger.e(TAG, "Error marking provisioning completed: ${e.message}", e)
                    }
                } else {
                    Log.e(TAG, "=== PROVISIONING INCOMPLETE ===")
                    Log.e(TAG, "Background retry will continue until full installation...")
                        logger.logInstallationStep("Provisioning Complete", "FAILED - Launching installation status screen")
                    try {
                        statusTracker.markProvisioningCompleted(false)
                        statusTracker.logStatus("INCOMPLETE", "Background retry active")
                        
                        // Launch installation status screen to show error after a short delay
                        scope.launch {
                            delay(2000) // Wait 2 seconds for system to stabilize
                            launchInstallationStatusScreen(appContext, false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking provisioning incomplete: ${e.message}", e)
                        logger.e(TAG, "Error marking provisioning incomplete: ${e.message}", e)
                    }
                    
                    // Continue background retry
                    scope.launch {
                        retryUntilFullInstallation(logger)
                    }
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "Provisioning cancelled")
                throw e // Re-throw cancellation
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "CRITICAL: Out of memory during provisioning!", e)
                try {
                    statusTracker.logStatus("ERROR", "Out of memory")
                    statusTracker.markProvisioningCompleted(false)
                } catch (e2: Exception) {
                    Log.e(TAG, "Error logging OOM: ${e2.message}", e2)
                }
                // Don't attempt recovery on OOM
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR during provisioning initialization", e)
                try {
                    logger.e(TAG, "CRITICAL ERROR during provisioning initialization", e)
                    statusTracker.logStatus("ERROR", e.message)
                    statusTracker.markProvisioningCompleted(false)
                } catch (e2: Exception) {
                    Log.e(TAG, "Error logging error status: ${e2.message}", e2)
                }
                // Attempt recovery
                try {
                    attemptRecovery(logger)
                } catch (e3: Exception) {
                    Log.e(TAG, "Error in recovery attempt: ${e3.message}", e3)
                    // Even recovery failed - but we don't crash
                }
            }
        }
    }
    
    /**
     * Phase 1: Verify device owner status
     */
    private suspend fun verifyDeviceOwnerStatus(logger: FileLogger): Boolean {
        Log.d(TAG, "--- Phase 1: Device Owner Verification ---")
        logger.logInstallationStep("Device Owner Verification", "Starting verification")
        
        return withContext(Dispatchers.Default) {
            try {
                val isDeviceOwner = try {
                    deviceOwnerManager.isDeviceOwner()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking device owner status: ${e.message}", e)
                    false
                }
                
                val isDeviceAdmin = try {
                    deviceOwnerManager.isDeviceAdmin()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking device admin status: ${e.message}", e)
                    false
                }
                
                Log.d(TAG, "Device Owner: $isDeviceOwner")
                Log.d(TAG, "Device Admin: $isDeviceAdmin")
                
                if (!isDeviceOwner) {
                    Log.e(TAG, "✗ CRITICAL: Not device owner! Provisioning may have failed.")
                    return@withContext false
                }
                
                if (!isDeviceAdmin) {
                    Log.e(TAG, "✗ CRITICAL: Not device admin! This is unexpected.")
                    return@withContext false
                }
                
                Log.d(TAG, "✓ Device owner status verified")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR in verifyDeviceOwnerStatus: ${e.message}", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Phase 2: Apply critical policies immediately
     * RETRIES UNTIL SUCCESS - Full installation required
     * These must be set before user can access anything
     */
    private suspend fun applyCriticalPolicies(logger: FileLogger) {
        Log.d(TAG, "--- Phase 2: Critical Policies Application (FULL INSTALLATION) ---")
        logger.logInstallationStep("Critical Policies Application", "Starting policy enforcement")
        
        withContext(Dispatchers.Default) {
            try {
                var allPoliciesApplied = false
                var totalAttempts = 0
                val maxAttempts = 20 // Increased retries for full installation
                
                while (!allPoliciesApplied && totalAttempts < maxAttempts) {
                    totalAttempts++
                    Log.d(TAG, "Applying critical policies (attempt $totalAttempts/$maxAttempts)...")
                    
                    var devOptionsSuccess = false
                    var factoryResetSuccess = false
                    var uninstallPreventionSuccess = false
                    
                    try {
                        // 1. Developer Options - CRITICAL (RETRY UNTIL SUCCESS)
                        var devAttempts = 0
                        while (!devOptionsSuccess && devAttempts < 5) {
                            devAttempts++
                            devOptionsSuccess = try {
                                val result = deviceOwnerManager.disableDeveloperOptions(true)
                                if (result) {
                                    Log.d(TAG, "✓ Developer options disabled (attempt $devAttempts)")
                                    true
                                } else {
                                    Log.w(TAG, "⚠ Failed to disable developer options (attempt $devAttempts)")
                                    if (devAttempts < 5) delay(500)
                                    false
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception disabling developer options (attempt $devAttempts): ${e.message}", e)
                                if (devAttempts < 5) delay(500)
                                false
                            }
                        }
                        
                        // 2. Factory Reset - CRITICAL (RETRY UNTIL SUCCESS)
                        var factoryAttempts = 0
                        while (!factoryResetSuccess && factoryAttempts < 5) {
                            factoryAttempts++
                            factoryResetSuccess = try {
                                val result = deviceOwnerManager.preventFactoryReset()
                                if (result) {
                                    Log.d(TAG, "✓ Factory reset prevented (attempt $factoryAttempts)")
                                    true
                                } else {
                                    Log.w(TAG, "⚠ Failed to prevent factory reset (attempt $factoryAttempts)")
                                    if (factoryAttempts < 5) delay(500)
                                    false
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception preventing factory reset (attempt $factoryAttempts): ${e.message}", e)
                                if (factoryAttempts < 5) delay(500)
                                false
                            }
                        }
                        
                        // 3. Uninstall Prevention - CRITICAL (RETRY UNTIL SUCCESS)
                        var uninstallAttempts = 0
                        while (!uninstallPreventionSuccess && uninstallAttempts < 5) {
                            uninstallAttempts++
                            uninstallPreventionSuccess = try {
                                val uninstallManager = UninstallPreventionManager(appContext)
                                uninstallManager.enableUninstallPrevention()
                                val verified = uninstallManager.isUninstallBlocked()
                                if (verified) {
                                    Log.d(TAG, "✓ Uninstall prevention enabled (attempt $uninstallAttempts)")
                                    true
                                } else {
                                    Log.w(TAG, "⚠ Uninstall prevention not verified (attempt $uninstallAttempts)")
                                    if (uninstallAttempts < 5) delay(500)
                                    false
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception enabling uninstall prevention (attempt $uninstallAttempts): ${e.message}", e)
                                if (uninstallAttempts < 5) delay(500)
                                false
                            }
                        }
                        
                        // Verify all policies are applied
                        val restrictionsVerified = try {
                            deviceOwnerManager.verifyAndEnforceCriticalRestrictions()
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception verifying restrictions: ${e.message}", e)
                            false
                        }
                        
                        // Check if all policies are successfully applied
                        allPoliciesApplied = devOptionsSuccess && factoryResetSuccess && uninstallPreventionSuccess && restrictionsVerified
                        
                        if (allPoliciesApplied) {
                            Log.d(TAG, "✓✓✓ ALL CRITICAL POLICIES SUCCESSFULLY APPLIED ✓✓✓")
                            Log.d(TAG, "  - Developer Options: ✓")
                            Log.d(TAG, "  - Factory Reset: ✓")
                            Log.d(TAG, "  - Uninstall Prevention: ✓")
                            Log.d(TAG, "  - Restrictions Verified: ✓")
                        } else {
                            Log.w(TAG, "⚠ Some policies not applied:")
                            Log.w(TAG, "  - Developer Options: ${if (devOptionsSuccess) "✓" else "✗"}")
                            Log.w(TAG, "  - Factory Reset: ${if (factoryResetSuccess) "✓" else "✗"}")
                            Log.w(TAG, "  - Uninstall Prevention: ${if (uninstallPreventionSuccess) "✓" else "✗"}")
                            Log.w(TAG, "  - Restrictions Verified: ${if (restrictionsVerified) "✓" else "✗"}")
                            Log.w(TAG, "Retrying in 1 second...")
                            delay(1000)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying critical policies: ${e.message}", e)
                        delay(1000)
                    }
                }
                
                if (!allPoliciesApplied) {
                    Log.e(TAG, "✗✗✗ CRITICAL: Failed to apply all policies after $maxAttempts attempts")
                    Log.e(TAG, "Continuing with background retry...")
                }
                
                // Continuous enforcement and retry in background until all succeed
                scope.launch {
                    try {
                        var backgroundAttempts = 0
                        while (backgroundAttempts < 30) { // Retry for 60 seconds
                            delay(2000)
                            backgroundAttempts++
                            
                            try {
                                val verified = deviceOwnerManager.verifyAndEnforceCriticalRestrictions()
                                if (verified) {
                                    Log.d(TAG, "✓ Background verification: All policies confirmed")
                                } else {
                                    Log.w(TAG, "⚠ Background retry $backgroundAttempts: Re-applying policies...")
                                    deviceOwnerManager.disableDeveloperOptions(true)
                                    deviceOwnerManager.preventFactoryReset()
                                    try {
                                        UninstallPreventionManager(appContext).enableUninstallPrevention()
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Background uninstall prevention retry failed: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error in background enforcement: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in continuous enforcement loop: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR in applyCriticalPolicies: ${e.message}", e)
                // Retry in background
                scope.launch {
                    retryPoliciesInBackground()
                }
            }
        }
    }
    
    /**
     * Background retry for policies until all succeed
     */
    private fun retryPoliciesInBackground() {
        scope.launch {
            var attempts = 0
            while (attempts < 30) {
                delay(2000)
                attempts++
                try {
                    deviceOwnerManager.disableDeveloperOptions(true)
                    deviceOwnerManager.preventFactoryReset()
                    UninstallPreventionManager(appContext).enableUninstallPrevention()
                    val verified = deviceOwnerManager.verifyAndEnforceCriticalRestrictions()
                    if (verified) {
                        Log.d(TAG, "✓ Background retry succeeded: All policies applied")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background policy retry $attempts failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Phase 3: Start all services in staggered manner
     * RETRIES UNTIL ALL SERVICES ARE RUNNING - Full installation required
     * Prevents overwhelming the system during provisioning
     */
    private suspend fun startAllServicesStaggered(isProvisioning: Boolean, logger: FileLogger) {
        Log.d(TAG, "--- Phase 3: Staggered Service Startup (FULL INSTALLATION) ---")
        logger.logInstallationStep("Service Startup", "Starting all services in staggered manner")
        
        withContext(Dispatchers.Default) {
            val services = listOf(
                ServiceInfo("UnifiedHeartbeatService", "com.example.deviceowner.services.UnifiedHeartbeatService", 0),
                ServiceInfo("ComprehensiveSecurityService", "com.example.deviceowner.services.ComprehensiveSecurityService", 500),
                ServiceInfo("CommandQueueService", "com.example.deviceowner.services.CommandQueueService", 1000),
                ServiceInfo("TamperDetectionService", "com.example.deviceowner.services.TamperDetectionService", 1500),
                ServiceInfo("FlashingDetectionService", "com.example.deviceowner.services.FlashingDetectionService", 2000),
                ServiceInfo("DeviceOwnerRecoveryService", "com.example.deviceowner.services.DeviceOwnerRecoveryService", 2500),
            )
            
            // Start all services first
            services.forEach { serviceInfo ->
                try {
                    delay(serviceInfo.delayMs.toLong())
                    val started = startService(serviceInfo)
                    if (started) {
                        Log.d(TAG, "✓ ${serviceInfo.name} started")
                    } else {
                        Log.w(TAG, "⚠ ${serviceInfo.name} failed to start - will retry")
                    }
                } catch (e: CancellationException) {
                    throw e // Re-throw cancellation
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Exception starting ${serviceInfo.name}: ${e.message}", e)
                }
            }
            
            // Wait a bit for services to start
            delay(3000)
            
            // Verify and retry failed services until all are running
            val serviceHealthManager = ServiceHealthManager(appContext)
            var allServicesRunning = false
            var verificationAttempts = 0
            val maxVerificationAttempts = 15
            
            while (!allServicesRunning && verificationAttempts < maxVerificationAttempts) {
                verificationAttempts++
                Log.d(TAG, "Verifying services (attempt $verificationAttempts/$maxVerificationAttempts)...")
                
                try {
                    val healthReport = serviceHealthManager.verifyAllServicesRunning()
                    
                    if (healthReport.allHealthy) {
                        Log.d(TAG, "✓✓✓ ALL SERVICES ARE RUNNING ✓✓✓")
                        logger.logInstallationStep("Service Startup", "SUCCESS - All services running")
                        logger.logProvisioningPhase("Phase 3: Service Startup", "SUCCESS")
                        allServicesRunning = true
                    } else {
                        Log.w(TAG, "⚠ ${healthReport.failedServices.size} service(s) not running: ${healthReport.failedServices}")
                        
                        // FIXED: Retry failed services using startServiceSafely
                        healthReport.failedServices.forEach { serviceName ->
                            Log.d(TAG, "Retrying service: $serviceName")
                            try {
                                val serviceClass = Class.forName(serviceName)
                                startServiceSafely(serviceClass, serviceName)
                                Log.d(TAG, "✓ Retry started: $serviceName")
                            } catch (e: Exception) {
                                Log.e(TAG, "✗ Retry failed for $serviceName: ${e.message}", e)
                            }
                        }
                        
                        if (verificationAttempts < maxVerificationAttempts) {
                            delay(2000) // Wait before next verification
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verifying services: ${e.message}", e)
                    delay(2000)
                }
            }
            
            if (!allServicesRunning) {
                Log.e(TAG, "✗✗✗ WARNING: Some services failed to start after $maxVerificationAttempts attempts")
                Log.e(TAG, "Continuing with background retry...")
                // Start background retry
                retryServicesInBackground(services)
            } else {
                Log.d(TAG, "✓✓✓ ALL SERVICES SUCCESSFULLY STARTED ✓✓✓")
            }
        }
    }
    
    /**
     * Background retry for services until all are running
     */
    private fun retryServicesInBackground(services: List<ServiceInfo>) {
        scope.launch {
            val serviceHealthManager = ServiceHealthManager(appContext)
            var attempts = 0
            while (attempts < 30) { // Retry for 60 seconds
                delay(2000)
                attempts++
                try {
                    val healthReport = serviceHealthManager.verifyAllServicesRunning()
                    if (healthReport.allHealthy) {
                        Log.d(TAG, "✓ Background retry succeeded: All services running")
                        break
                    } else {
                        // FIXED: Use startServiceSafely for retry
                        healthReport.failedServices.forEach { serviceName ->
                            try {
                                val serviceClass = Class.forName(serviceName)
                                startServiceSafely(serviceClass, serviceName)
                            } catch (e: Exception) {
                                // Ignore individual failures
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background service retry $attempts failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Helper method to start a service properly
     * FIXED: Uses startForegroundService() for Android 8.0+ and application context
     * This ensures services actually start during the quick UI transitions
     */
    private fun startServiceSafely(serviceClass: Class<*>, serviceName: String): Boolean {
        return try {
            val intent = android.content.Intent(appContext, serviceClass)
            
            // Use startForegroundService() for Android 8.0+ (API 26+)
            // This is CRITICAL - regular startService() fails silently on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
                Log.d(TAG, "✓ Started $serviceName as foreground service (Android 8.0+)")
            } else {
                appContext.startService(intent)
                Log.d(TAG, "✓ Started $serviceName (Android < 8.0)")
            }
            true
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException starting service $serviceName: ${e.message}", e)
            // Try fallback for older Android versions
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    val intent = android.content.Intent(appContext, serviceClass)
                    appContext.startService(intent)
                    Log.d(TAG, "✓ Started $serviceName (fallback)")
                    true
                } else {
                    false
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback startService also failed: ${e2.message}", e2)
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting service $serviceName: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service $serviceName: ${e.message}", e)
            false
        }
    }
    
    /**
     * Start a service by class name
     * FIXED: Uses startForegroundService() for Android 8.0+ and application context
     * NEVER throws - always returns success/failure
     */
    private fun startService(serviceInfo: ServiceInfo): Boolean {
        return try {
            val serviceClass = Class.forName(serviceInfo.className)
            startServiceSafely(serviceClass, serviceInfo.name)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Service class not found: ${serviceInfo.className}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service ${serviceInfo.name}: ${e.message}", e)
            false
        }
    }
    
    /**
     * Phase 4: Initialize all features
     * RETRIES UNTIL SUCCESS - Full installation required
     * All features must be initialized
     */
    private suspend fun initializeAllFeatures() {
        Log.d(TAG, "--- Phase 4: Feature Initialization (FULL INSTALLATION) ---")
        
        withContext(Dispatchers.Default) {
            try {
                var powerManagementSuccess = false
                var powerLossMonitorSuccess = false
                var overlaySystemSuccess = false
                
                // Power Management - RETRY UNTIL SUCCESS
                var powerAttempts = 0
                while (!powerManagementSuccess && powerAttempts < 10) {
                    powerAttempts++
                    try {
                        val powerManagementClass = Class.forName("com.example.deviceowner.managers.PowerManagementManager")
                        val powerConstructor = powerManagementClass.getConstructor(Context::class.java)
                        val powerManager = powerConstructor.newInstance(appContext)
                        val powerInitMethod = powerManagementClass.getMethod("initializePowerManagement")
                        powerInitMethod.invoke(powerManager)
                        powerManagementSuccess = true
                        Log.d(TAG, "✓ Power management initialized (attempt $powerAttempts)")
                    } catch (e: ClassNotFoundException) {
                        Log.w(TAG, "⚠ PowerManagementManager class not found (attempt $powerAttempts)")
                        if (powerAttempts < 10) delay(1000)
                    } catch (e: NoSuchMethodException) {
                        Log.w(TAG, "⚠ PowerManagementManager method not found (attempt $powerAttempts)")
                        if (powerAttempts < 10) delay(1000)
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠ Power management initialization failed (attempt $powerAttempts): ${e.message}", e)
                        if (powerAttempts < 10) delay(1000)
                    }
                }
                
                // Power Loss Monitor - RETRY UNTIL SUCCESS
                var powerLossAttempts = 0
                while (!powerLossMonitorSuccess && powerLossAttempts < 10) {
                    powerLossAttempts++
                    try {
                        val powerLossClass = Class.forName("com.example.deviceowner.managers.PowerLossMonitor")
                        val powerLossConstructor = powerLossClass.getConstructor(Context::class.java)
                        val powerLossMonitor = powerLossConstructor.newInstance(appContext)
                        val startMonitoringMethod = powerLossClass.getMethod("startMonitoring")
                        startMonitoringMethod.invoke(powerLossMonitor)
                        powerLossMonitorSuccess = true
                        Log.d(TAG, "✓ Power loss monitoring started (attempt $powerLossAttempts)")
                    } catch (e: ClassNotFoundException) {
                        Log.w(TAG, "⚠ PowerLossMonitor class not found (attempt $powerLossAttempts)")
                        if (powerLossAttempts < 10) delay(1000)
                    } catch (e: NoSuchMethodException) {
                        Log.w(TAG, "⚠ PowerLossMonitor method not found (attempt $powerLossAttempts)")
                        if (powerLossAttempts < 10) delay(1000)
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠ Power loss monitoring failed (attempt $powerLossAttempts): ${e.message}", e)
                        if (powerLossAttempts < 10) delay(1000)
                    }
                }
                
                // Overlay System - RETRY UNTIL SUCCESS
                var overlayAttempts = 0
                while (!overlaySystemSuccess && overlayAttempts < 10) {
                    overlayAttempts++
                    try {
                        val overlayClass = Class.forName("com.example.deviceowner.overlay.OverlayController")
                        val overlayConstructor = overlayClass.getConstructor(Context::class.java)
                        val overlayController = overlayConstructor.newInstance(appContext)
                        val overlayInitMethod = overlayClass.getMethod("initializeOverlaySystem")
                        overlayInitMethod.invoke(overlayController)
                        overlaySystemSuccess = true
                        Log.d(TAG, "✓ Overlay system initialized (attempt $overlayAttempts)")
                    } catch (e: ClassNotFoundException) {
                        Log.w(TAG, "⚠ OverlayController class not found (attempt $overlayAttempts)")
                        if (overlayAttempts < 10) delay(1000)
                    } catch (e: NoSuchMethodException) {
                        Log.w(TAG, "⚠ OverlayController method not found (attempt $overlayAttempts)")
                        if (overlayAttempts < 10) delay(1000)
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠ Overlay system initialization failed (attempt $overlayAttempts): ${e.message}", e)
                        if (overlayAttempts < 10) delay(1000)
                    }
                }
                
                if (powerManagementSuccess && powerLossMonitorSuccess && overlaySystemSuccess) {
                    Log.d(TAG, "✓✓✓ ALL FEATURES SUCCESSFULLY INITIALIZED ✓✓✓")
                    Log.d(TAG, "  - Power Management: ✓")
                    Log.d(TAG, "  - Power Loss Monitor: ✓")
                    Log.d(TAG, "  - Overlay System: ✓")
                } else {
                    Log.w(TAG, "⚠ Some features not initialized:")
                    Log.w(TAG, "  - Power Management: ${if (powerManagementSuccess) "✓" else "✗"}")
                    Log.w(TAG, "  - Power Loss Monitor: ${if (powerLossMonitorSuccess) "✓" else "✗"}")
                    Log.w(TAG, "  - Overlay System: ${if (overlaySystemSuccess) "✓" else "✗"}")
                    // Retry in background
                    retryFeaturesInBackground()
                }
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR in initializeAllFeatures: ${e.message}", e)
                // Retry in background
                retryFeaturesInBackground()
            }
        }
    }
    
    /**
     * Background retry for features until all succeed
     */
    private fun retryFeaturesInBackground() {
        scope.launch {
            var attempts = 0
            while (attempts < 20) {
                delay(3000)
                attempts++
                try {
                    // Retry power management
                    try {
                        val powerManagementClass = Class.forName("com.example.deviceowner.managers.PowerManagementManager")
                        val powerConstructor = powerManagementClass.getConstructor(Context::class.java)
                        val powerManager = powerConstructor.newInstance(appContext)
                        val powerInitMethod = powerManagementClass.getMethod("initializePowerManagement")
                        powerInitMethod.invoke(powerManager)
                    } catch (e: Exception) {
                        // Ignore
                    }
                    
                    // Retry power loss monitor
                    try {
                        val powerLossClass = Class.forName("com.example.deviceowner.managers.PowerLossMonitor")
                        val powerLossConstructor = powerLossClass.getConstructor(Context::class.java)
                        val powerLossMonitor = powerLossConstructor.newInstance(appContext)
                        val startMonitoringMethod = powerLossClass.getMethod("startMonitoring")
                        startMonitoringMethod.invoke(powerLossMonitor)
                    } catch (e: Exception) {
                        // Ignore
                    }
                    
                    // Retry overlay system
                    try {
                        val overlayClass = Class.forName("com.example.deviceowner.overlay.OverlayController")
                        val overlayConstructor = overlayClass.getConstructor(Context::class.java)
                        val overlayController = overlayConstructor.newInstance(appContext)
                        val overlayInitMethod = overlayClass.getMethod("initializeOverlaySystem")
                        overlayInitMethod.invoke(overlayController)
                    } catch (e: Exception) {
                        // Ignore
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background feature retry $attempts failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Phase 5: Final verification
     * VERIFIES FULL INSTALLATION - Retries until everything is verified
     * Only returns true when ALL components are installed and verified
     */
    private suspend fun verifyCompleteInitialization(logger: FileLogger): Boolean {
        Log.d(TAG, "--- Phase 5: Final Verification (FULL INSTALLATION CHECK) ---")
        
        return withContext(Dispatchers.Default) {
            try {
                delay(5000) // Wait for services to fully start
                
                var verificationAttempts = 0
                val maxVerificationAttempts = 10
                var allVerified = false
                
                while (!allVerified && verificationAttempts < maxVerificationAttempts) {
                    verificationAttempts++
                    Log.d(TAG, "Full installation verification (attempt $verificationAttempts/$maxVerificationAttempts)...")
                    
                    // Verify device owner
                    val isDeviceOwner = try {
                        deviceOwnerManager.isDeviceOwner()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error verifying device owner: ${e.message}", e)
                        false
                    }
                    Log.d(TAG, "Device Owner Status: $isDeviceOwner")
                    
                    if (!isDeviceOwner) {
                        Log.e(TAG, "✗ CRITICAL: Device owner lost!")
                        return@withContext false
                    }
                    
                    // Verify critical restrictions
                    val restrictionsVerified = try {
                        deviceOwnerManager.verifyAndEnforceCriticalRestrictions()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error verifying restrictions: ${e.message}", e)
                        // Re-apply restrictions
                        deviceOwnerManager.disableDeveloperOptions(true)
                        deviceOwnerManager.preventFactoryReset()
                        false
                    }
                    Log.d(TAG, "Critical Restrictions Verified: $restrictionsVerified")
                    
                    // Verify uninstall prevention
                    var uninstallBlocked = false
                    try {
                        val uninstallManager = UninstallPreventionManager(appContext)
                        uninstallBlocked = uninstallManager.isUninstallBlocked()
                        if (!uninstallBlocked) {
                            uninstallManager.enableUninstallPrevention()
                            uninstallBlocked = uninstallManager.isUninstallBlocked()
                        }
                        Log.d(TAG, "Uninstall Prevention: $uninstallBlocked")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not verify uninstall prevention: ${e.message}", e)
                        try {
                            UninstallPreventionManager(appContext).enableUninstallPrevention()
                        } catch (e2: Exception) {
                            // Ignore
                        }
                    }
                    
                    // Verify service health
                    var healthReport: ServiceHealthManager.ServiceHealthReport? = null
                    try {
                        val serviceHealthManager = ServiceHealthManager(appContext)
                        healthReport = serviceHealthManager.verifyAllServicesRunning()
                        Log.d(TAG, "Service Health: ${if (healthReport.allHealthy) "HEALTHY" else "UNHEALTHY"}")
                        
                        if (!healthReport.allHealthy) {
                            Log.w(TAG, "Failed services: ${healthReport.failedServices}")
                            // FIXED: Retry failed services using startServiceSafely
                            healthReport.failedServices.forEach { serviceName ->
                                try {
                                    val serviceClass = Class.forName(serviceName)
                                    startServiceSafely(serviceClass, serviceName)
                                    Log.d(TAG, "✓ Retried service: $serviceName")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to retry service: $serviceName", e)
                                }
                            }
                            // Wait a bit for services to start
                            delay(2000)
                            // Re-check
                            healthReport = serviceHealthManager.verifyAllServicesRunning()
                        }
                        
                        // Start continuous health monitoring
                        try {
                            serviceHealthManager.startHealthMonitoring(30000) // Check every 30 seconds
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not start health monitoring: ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error verifying service health: ${e.message}", e)
                        healthReport = ServiceHealthManager.ServiceHealthReport()
                    }
                    
                    // Check if everything is verified
                    val allHealthy = healthReport?.allHealthy ?: false
                    allVerified = isDeviceOwner && restrictionsVerified && uninstallBlocked && allHealthy
                    
                if (allVerified) {
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "✓✓✓ FULL INSTALLATION VERIFIED ✓✓✓")
                    logger.logInstallationStep("Final Verification", "SUCCESS - Full installation verified")
                    logger.logProvisioningPhase("Phase 5: Final Verification", "SUCCESS")
                    logger.critical(TAG, "✓✓✓ FULL INSTALLATION VERIFIED ✓✓✓")
                    Log.d(TAG, "  - Device Owner: ✓")
                    Log.d(TAG, "  - Critical Restrictions: ✓")
                    Log.d(TAG, "  - Uninstall Prevention: ✓")
                    Log.d(TAG, "  - Service Health: ✓")
                    Log.d(TAG, "========================================")
                    logger.i(TAG, "  - Device Owner: ✓")
                    logger.i(TAG, "  - Critical Restrictions: ✓")
                    logger.i(TAG, "  - Uninstall Prevention: ✓")
                    logger.i(TAG, "  - Service Health: ✓")
                    statusTracker.logStatus("SUCCESS", "Full installation verified")
                    break
                } else {
                        Log.w(TAG, "========================================")
                        Log.w(TAG, "⚠ Installation incomplete:")
                        Log.w(TAG, "  - Device Owner: ${if (isDeviceOwner) "✓" else "✗"}")
                        Log.w(TAG, "  - Critical Restrictions: ${if (restrictionsVerified) "✓" else "✗"}")
                        Log.w(TAG, "  - Uninstall Prevention: ${if (uninstallBlocked) "✓" else "✗"}")
                        Log.w(TAG, "  - Service Health: ${if (allHealthy) "✓" else "✗"}")
                        Log.w(TAG, "Retrying in 2 seconds...")
                        Log.w(TAG, "========================================")
                        
                        if (verificationAttempts < maxVerificationAttempts) {
                            delay(2000)
                        }
                    }
                }
                
                if (allVerified) {
                    return@withContext true
                } else {
                    Log.e(TAG, "✗✗✗ Verification incomplete after $maxVerificationAttempts attempts")
                    Log.e(TAG, "Starting aggressive background retry...")
                    statusTracker.logStatus("INCOMPLETE", "Verification incomplete - background retry active")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR in verifyCompleteInitialization: ${e.message}", e)
                // Return false but don't crash - background retry will continue
                return@withContext false
            }
        }
    }
    
    /**
     * Attempt recovery if provisioning fails
     * RETRIES UNTIL FULL INSTALLATION - Never gives up
     */
    private suspend fun attemptRecovery(logger: FileLogger) {
        Log.d(TAG, "--- Attempting Recovery (FULL INSTALLATION) ---")
        logger.logInstallationStep("Recovery", "Starting recovery attempt")
        
        withContext(Dispatchers.Default) {
            try {
                delay(5000) // Wait before recovery attempt
                
                // Re-verify device owner
                val isDeviceOwner = try {
                    deviceOwnerManager.isDeviceOwner()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking device owner during recovery: ${e.message}", e)
                    logger.e(TAG, "Error checking device owner during recovery: ${e.message}", e)
                    false
                }
                
                if (!isDeviceOwner) {
                    Log.e(TAG, "CRITICAL: Device owner lost - cannot recover automatically")
                    return@withContext
                }
                
                // Re-apply critical policies (with retries)
                try {
                    applyCriticalPolicies(logger)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reapplying policies during recovery: ${e.message}", e)
                    retryPoliciesInBackground()
                }
                
                // Re-start services (with retries)
                try {
                    startAllServicesStaggered(false, logger)
                } catch (e: Exception) {
                    Log.e(TAG, "Error restarting services during recovery: ${e.message}", e)
                    val services = listOf(
                        ServiceInfo("UnifiedHeartbeatService", "com.example.deviceowner.services.UnifiedHeartbeatService", 0),
                        ServiceInfo("ComprehensiveSecurityService", "com.example.deviceowner.services.ComprehensiveSecurityService", 0),
                        ServiceInfo("CommandQueueService", "com.example.deviceowner.services.CommandQueueService", 0),
                        ServiceInfo("TamperDetectionService", "com.example.deviceowner.services.TamperDetectionService", 0),
                        ServiceInfo("FlashingDetectionService", "com.example.deviceowner.services.FlashingDetectionService", 0),
                        ServiceInfo("DeviceOwnerRecoveryService", "com.example.deviceowner.services.DeviceOwnerRecoveryService", 0),
                    )
                    retryServicesInBackground(services)
                }
                
                // Re-initialize features (with retries)
                try {
                    initializeAllFeatures()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reinitializing features during recovery: ${e.message}", e)
                    retryFeaturesInBackground()
                }
                
                Log.d(TAG, "Recovery attempt completed")
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR in attemptRecovery: ${e.message}", e)
                logger.e(TAG, "CRITICAL ERROR in attemptRecovery: ${e.message}", e)
                // Start aggressive retry
                retryUntilFullInstallation(logger)
            }
        }
    }
    
    /**
     * Aggressive retry until FULL installation is achieved
     * Keeps retrying all components until everything is installed
     * Returns true only when FULL installation is verified
     */
    private suspend fun retryUntilFullInstallation(logger: FileLogger): Boolean {
        Log.d(TAG, "=== STARTING AGGRESSIVE RETRY FOR FULL INSTALLATION ===")
        logger.logInstallationStep("Retry Until Full Installation", "Starting aggressive retry")
        
        var attempts = 0
        val maxAttempts = 30 // Retry for up to 60 seconds
        
        while (attempts < maxAttempts) {
            attempts++
            delay(2000)
            
            Log.d(TAG, "Full installation retry attempt $attempts/$maxAttempts...")
            
            try {
                // Verify device owner
                val isDeviceOwner = try {
                    deviceOwnerManager.isDeviceOwner()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking device owner: ${e.message}", e)
                    false
                }
                
                if (!isDeviceOwner) {
                    Log.e(TAG, "✗ Device owner lost - cannot continue")
                    return false
                }
                
                // Retry and verify policies
                var policiesOk = false
                try {
                    policiesOk = deviceOwnerManager.verifyAndEnforceCriticalRestrictions()
                    if (!policiesOk) {
                        // Re-apply policies
                        deviceOwnerManager.disableDeveloperOptions(true)
                        deviceOwnerManager.preventFactoryReset()
                        try {
                            UninstallPreventionManager(appContext).enableUninstallPrevention()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error enabling uninstall prevention: ${e.message}")
                        }
                        delay(1000)
                        policiesOk = deviceOwnerManager.verifyAndEnforceCriticalRestrictions()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verifying policies: ${e.message}", e)
                    // Re-apply
                    deviceOwnerManager.disableDeveloperOptions(true)
                    deviceOwnerManager.preventFactoryReset()
                }
                
                // Retry and verify services
                val serviceHealthManager = ServiceHealthManager(appContext)
                var healthReport = serviceHealthManager.verifyAllServicesRunning()
                
                if (!healthReport.allHealthy) {
                    Log.w(TAG, "Retrying failed services: ${healthReport.failedServices}")
                    // FIXED: Use startServiceSafely for retry
                    healthReport.failedServices.forEach { serviceName ->
                        try {
                            val serviceClass = Class.forName(serviceName)
                            startServiceSafely(serviceClass, serviceName)
                            Log.d(TAG, "✓ Retried service: $serviceName")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to retry service: $serviceName", e)
                        }
                    }
                    delay(2000) // Wait for services to start
                    healthReport = serviceHealthManager.verifyAllServicesRunning()
                }
                
                // Verify uninstall prevention
                var uninstallBlocked = false
                try {
                    val uninstallManager = UninstallPreventionManager(appContext)
                    uninstallBlocked = uninstallManager.isUninstallBlocked()
                    if (!uninstallBlocked) {
                        uninstallManager.enableUninstallPrevention()
                        delay(1000)
                        uninstallBlocked = uninstallManager.isUninstallBlocked()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error verifying uninstall prevention: ${e.message}", e)
                }
                
                // Check if EVERYTHING is installed
                val allInstalled = isDeviceOwner && policiesOk && uninstallBlocked && healthReport.allHealthy
                
                if (allInstalled) {
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "✓✓✓ FULL INSTALLATION ACHIEVED ✓✓✓")
                    Log.d(TAG, "  - Device Owner: ✓")
                    Log.d(TAG, "  - Policies: ✓")
                    Log.d(TAG, "  - Uninstall Prevention: ✓")
                    Log.d(TAG, "  - All Services: ✓")
                    Log.d(TAG, "========================================")
                    try {
                        statusTracker.markProvisioningCompleted(true)
                        statusTracker.logStatus("SUCCESS", "Full installation completed after retry")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating status: ${e.message}")
                    }
                    return true
                } else {
                    Log.w(TAG, "Installation still incomplete:")
                    Log.w(TAG, "  - Device Owner: ${if (isDeviceOwner) "✓" else "✗"}")
                    Log.w(TAG, "  - Policies: ${if (policiesOk) "✓" else "✗"}")
                    Log.w(TAG, "  - Uninstall Prevention: ${if (uninstallBlocked) "✓" else "✗"}")
                    Log.w(TAG, "  - Services: ${if (healthReport.allHealthy) "✓" else "✗"}")
                    Log.w(TAG, "Continuing retry...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in aggressive retry: ${e.message}", e)
            }
        }
        
        if (attempts >= maxAttempts) {
            Log.e(TAG, "✗✗✗ Full installation retry exhausted after $maxAttempts attempts")
            Log.e(TAG, "Background monitoring will continue...")
            // Start continuous background retry
            scope.launch {
                continuousBackgroundRetry()
            }
        }
        
        return false
    }
    
    /**
     * Continuous background retry - never gives up
     * Keeps retrying until full installation is achieved
     */
    private suspend fun continuousBackgroundRetry() {
        Log.d(TAG, "=== STARTING CONTINUOUS BACKGROUND RETRY ===")
        
        while (true) {
            try {
                delay(5000) // Check every 5 seconds
                
                val isDeviceOwner = deviceOwnerManager.isDeviceOwner()
                if (!isDeviceOwner) {
                    Log.e(TAG, "Device owner lost - stopping background retry")
                    break
                }
                
                // Verify and retry policies
                val policiesOk = deviceOwnerManager.verifyAndEnforceCriticalRestrictions()
                if (!policiesOk) {
                    deviceOwnerManager.disableDeveloperOptions(true)
                    deviceOwnerManager.preventFactoryReset()
                    UninstallPreventionManager(appContext).enableUninstallPrevention()
                }
                
                // Verify and retry services
                val serviceHealthManager = ServiceHealthManager(appContext)
                val healthReport = serviceHealthManager.verifyAllServicesRunning()
                if (!healthReport.allHealthy) {
                    // FIXED: Use startServiceSafely for retry
                    healthReport.failedServices.forEach { serviceName ->
                        try {
                            val serviceClass = Class.forName(serviceName)
                            startServiceSafely(serviceClass, serviceName)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
                
                // Check if full installation achieved
                val uninstallBlocked = try {
                    UninstallPreventionManager(appContext).isUninstallBlocked()
                } catch (e: Exception) {
                    false
                }
                
                if (isDeviceOwner && policiesOk && uninstallBlocked && healthReport.allHealthy) {
                    Log.d(TAG, "✓✓✓ FULL INSTALLATION ACHIEVED IN BACKGROUND ✓✓✓")
                    try {
                        statusTracker.markProvisioningCompleted(true)
                        statusTracker.logStatus("SUCCESS", "Full installation completed in background")
                        
                        // Launch installation status screen automatically after a short delay
                        scope.launch {
                            delay(2000) // Wait 2 seconds for system to stabilize
                            launchInstallationStatusScreen(appContext, true)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error in continuous background retry: ${e.message}")
            }
        }
    }
    
    /**
     * Launch installation status screen automatically after provisioning completes
     * This shows success or error immediately after Device Owner initialization
     */
    private fun launchInstallationStatusScreen(context: Context, success: Boolean) {
        try {
            val intent = Intent(context, DeviceOwnerInstallationStatusActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("installation_success", success)
            }
            context.startActivity(intent)
            Log.d(TAG, "✓ Installation status screen launched (success: $success)")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching installation status screen: ${e.message}", e)
        }
    }
    
    /**
     * Data class for service information
     */
    private data class ServiceInfo(
        val name: String,
        val className: String,
        val delayMs: Int
    )
}