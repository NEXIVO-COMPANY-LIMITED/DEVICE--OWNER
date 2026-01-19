package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.managers.UninstallPreventionManager
import com.example.deviceowner.managers.BootVerificationManager
import com.example.deviceowner.managers.IdentifierAuditLog
import com.example.deviceowner.services.UnifiedHeartbeatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles device boot completion
 * Verifies app persistence and device owner status
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed - starting verification")
            handleBootCompleted(context)
        }
    }

    /**
     * Handle device boot completion
     */
    private fun handleBootCompleted(context: Context) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                Log.d(TAG, "Starting post-boot verification...")

                val uninstallManager = UninstallPreventionManager(context)
                val bootVerificationManager = BootVerificationManager(context)
                val auditLog = IdentifierAuditLog(context)

                // Verify app is still installed
                val appInstalled = uninstallManager.verifyAppInstalled()
                if (!appInstalled) {
                    Log.e(TAG, "CRITICAL: App not found after boot!")
                    auditLog.logIncident(
                        type = "APP_MISSING_AFTER_BOOT",
                        severity = "CRITICAL",
                        details = "App was not found in package manager after device boot"
                    )
                    return@launch
                }

                // Verify device owner is still enabled
                val deviceOwnerEnabled = uninstallManager.verifyDeviceOwnerEnabled()
                if (!deviceOwnerEnabled) {
                    Log.e(TAG, "CRITICAL: Device owner lost after boot!")
                    auditLog.logIncident(
                        type = "DEVICE_OWNER_LOST_AFTER_BOOT",
                        severity = "CRITICAL",
                        details = "Device owner status was lost after device boot"
                    )
                    return@launch
                }

                // Verify device identifiers
                val deviceVerified = bootVerificationManager.verifyOnBoot()
                if (!deviceVerified) {
                    Log.e(TAG, "Device verification failed after boot")
                    auditLog.logIncident(
                        type = "DEVICE_VERIFICATION_FAILED",
                        severity = "CRITICAL",
                        details = "Device identifiers do not match stored values"
                    )
                    return@launch
                }

                // Re-enable uninstall prevention
                val preventionEnabled = uninstallManager.enableUninstallPrevention()
                if (!preventionEnabled) {
                    Log.w(TAG, "Warning: Could not re-enable uninstall prevention after boot")
                    auditLog.logAction(
                        "PREVENTION_RENABLE_FAILED",
                        "Could not re-enable uninstall prevention after boot"
                    )
                }

                // Start heartbeat verification service
                startHeartbeatService(context)
                
                // Start command queue service (Feature 4.9)
                startCommandQueueService(context)
                
                // Start comprehensive security service
                startComprehensiveSecurityService(context)

                Log.d(TAG, "✓ Post-boot verification completed successfully")
                auditLog.logAction(
                    "BOOT_VERIFICATION_COMPLETE",
                    "Device boot verification completed - app persisted successfully"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error during boot verification", e)
                val auditLog = IdentifierAuditLog(context)
                auditLog.logIncident(
                    type = "BOOT_VERIFICATION_ERROR",
                    severity = "HIGH",
                    details = "Error during boot verification: ${e.message}"
                )
            }
        }
    }

    /**
     * Start heartbeat verification service
     */
    private fun startHeartbeatService(context: Context) {
        try {
            val intent = Intent(context, UnifiedHeartbeatService::class.java)
            context.startService(intent)
            Log.d(TAG, "Heartbeat verification service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting heartbeat service", e)
        }
    }
    
    /**
     * Start command queue service
     * Feature 4.9: Offline Command Queue
     */
    private fun startCommandQueueService(context: Context) {
        try {
            val intent = Intent(context, com.example.deviceowner.services.CommandQueueService::class.java)
            context.startService(intent)
            Log.d(TAG, "✓ CommandQueueService started after boot")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CommandQueueService", e)
        }
    }
    
    /**
     * Start comprehensive security service
     * Continuous security monitoring and enforcement
     */
    private fun startComprehensiveSecurityService(context: Context) {
        try {
            val intent = Intent(context, com.example.deviceowner.services.ComprehensiveSecurityService::class.java)
            context.startService(intent)
            Log.d(TAG, "✓ ComprehensiveSecurityService started after boot")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ComprehensiveSecurityService", e)
        }
    }
}
