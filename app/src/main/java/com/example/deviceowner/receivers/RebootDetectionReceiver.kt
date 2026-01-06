package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.managers.PowerManagementManager
import com.example.deviceowner.managers.IdentifierAuditLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Detects and handles device reboot events
 * Feature 4.5: Disable Shutdown & Restart
 */
class RebootDetectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RebootDetectionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed - initializing reboot detection")
            handleBootCompleted(context)
        }
    }

    /**
     * Handle boot completed event
     */
    private fun handleBootCompleted(context: Context) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                Log.d(TAG, "Starting reboot detection verification...")

                val powerManagementManager = PowerManagementManager(context)
                val auditLog = IdentifierAuditLog(context)

                // Initialize power management features
                powerManagementManager.initializePowerManagement()

                // Get reboot count
                val rebootCount = powerManagementManager.getRebootCount()
                Log.d(TAG, "Device reboot count: $rebootCount")

                auditLog.logAction(
                    "REBOOT_DETECTION_COMPLETE",
                    "Device reboot detected and verified. Total reboots: $rebootCount"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error during reboot detection", e)
                val auditLog = IdentifierAuditLog(context)
                auditLog.logIncident(
                    type = "REBOOT_DETECTION_ERROR",
                    severity = "HIGH",
                    details = "Error during reboot detection: ${e.message}"
                )
            }
        }
    }
}
