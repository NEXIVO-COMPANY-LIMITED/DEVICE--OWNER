package com.example.deviceowner.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.managers.UninstallPreventionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device admin enabled - Intent action: ${intent.action}")
        
        // Check if this is during provisioning
        val isProvisioning = intent.action == "android.app.action.PROVISION_MANAGED_DEVICE"
        Log.d(TAG, "Is provisioning: $isProvisioning")
        
        // During provisioning, keep initialization minimal to avoid failures
        // Full initialization will happen after provisioning completes
        try {
            // Always initialize DeviceOwnerManager first to verify device owner status
            val managerClass = Class.forName("com.example.deviceowner.managers.DeviceOwnerManager")
            val constructor = managerClass.getConstructor(Context::class.java)
            val manager = constructor.newInstance(context)
            val initMethod = managerClass.getMethod("initializeDeviceOwner")
            initMethod.invoke(manager)
            Log.d(TAG, "DeviceOwnerManager initialized successfully")
            
            // Only start services if not during provisioning
            // During provisioning, services will be started after setup completes
            if (!isProvisioning) {
                // Start heartbeat service for continuous device monitoring
                startHeartbeatService(context)
                
                // Enable uninstall prevention (Feature 4.7)
                val scope = CoroutineScope(Dispatchers.Default)
                scope.launch {
                    try {
                        val uninstallManager = UninstallPreventionManager(context)
                        uninstallManager.enableUninstallPrevention()
                        Log.d(TAG, "Uninstall prevention enabled")
                        
                        // Initialize power management (Feature 4.5)
                        try {
                            val powerManagementClass = Class.forName("com.example.deviceowner.managers.PowerManagementManager")
                            val powerConstructor = powerManagementClass.getConstructor(Context::class.java)
                            val powerManager = powerConstructor.newInstance(context)
                            val powerInitMethod = powerManagementClass.getMethod("initializePowerManagement")
                            powerInitMethod.invoke(powerManager)
                            Log.d(TAG, "Power management initialized")
                        } catch (e: Exception) {
                            Log.w(TAG, "Power management initialization skipped: ${e.message}")
                        }
                        
                        // Start power loss monitoring
                        try {
                            val powerLossClass = Class.forName("com.example.deviceowner.managers.PowerLossMonitor")
                            val powerLossConstructor = powerLossClass.getConstructor(Context::class.java)
                            val powerLossMonitor = powerLossConstructor.newInstance(context)
                            val startMonitoringMethod = powerLossClass.getMethod("startMonitoring")
                            startMonitoringMethod.invoke(powerLossMonitor)
                            Log.d(TAG, "Power loss monitoring started")
                        } catch (e: Exception) {
                            Log.w(TAG, "Power loss monitoring skipped: ${e.message}")
                        }
                        
                        // Initialize overlay system (Feature 4.6)
                        try {
                            val overlayClass = Class.forName("com.example.deviceowner.overlay.OverlayController")
                            val overlayConstructor = overlayClass.getConstructor(Context::class.java)
                            val overlayController = overlayConstructor.newInstance(context)
                            val overlayInitMethod = overlayClass.getMethod("initializeOverlaySystem")
                            overlayInitMethod.invoke(overlayController)
                            Log.d(TAG, "Overlay system initialized")
                        } catch (e: Exception) {
                            Log.w(TAG, "Overlay system initialization skipped: ${e.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enabling device owner features", e)
                        // Don't throw - allow provisioning to continue
                    }
                }
            } else {
                Log.d(TAG, "Skipping service initialization during provisioning")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing device owner", e)
            // Don't throw exceptions during provisioning - let it complete
            // The error is logged but won't block provisioning
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
     * Start the UnifiedHeartbeatService to begin sending heartbeat data to backend
     */
    private fun startHeartbeatService(context: Context) {
        try {
            val heartbeatIntent = Intent(context, com.example.deviceowner.services.UnifiedHeartbeatService::class.java)
            context.startService(heartbeatIntent)
            Log.d(TAG, "✓ UnifiedHeartbeatService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to start UnifiedHeartbeatService: ${e.message}", e)
        }
    }
}
