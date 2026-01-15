package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.DeviceOwnerRecoveryManager
import com.example.deviceowner.managers.IdentifierAuditLog
import kotlinx.coroutines.*

/**
 * Service for continuous device owner status monitoring and recovery
 * Detects when device owner is removed and automatically restores it
 * Runs periodic checks every 5 minutes
 */
class DeviceOwnerRecoveryService : Service() {

    private lateinit var recoveryManager: DeviceOwnerRecoveryManager
    private lateinit var auditLog: IdentifierAuditLog
    private var recoveryJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val TAG = "DeviceOwnerRecoveryService"
        private const val RECOVERY_CHECK_INTERVAL = 300000L // 5 minutes
        private const val INITIAL_DELAY = 60000L // 1 minute after startup
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DeviceOwnerRecoveryService created")

        recoveryManager = DeviceOwnerRecoveryManager(this)
        auditLog = IdentifierAuditLog(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DeviceOwnerRecoveryService started")

        startRecoveryMonitoring()

        return START_STICKY
    }

    /**
     * Start periodic device owner recovery monitoring
     */
    private fun startRecoveryMonitoring() {
        if (recoveryJob?.isActive == true) {
            Log.d(TAG, "Recovery monitoring already running")
            return
        }

        recoveryJob = serviceScope.launch {
            try {
                // Initial delay to allow system stabilization
                delay(INITIAL_DELAY)

                while (isActive) {
                    try {
                        performRecoveryCheck()
                        delay(RECOVERY_CHECK_INTERVAL)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in recovery monitoring loop", e)
                        delay(RECOVERY_CHECK_INTERVAL)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Recovery monitoring cancelled")
            }
        }
    }

    /**
     * Perform device owner status check and recovery if needed
     */
    private suspend fun performRecoveryCheck() {
        try {
            Log.d(TAG, "Performing device owner recovery check...")

            val state = recoveryManager.getRecoveryState()

            // Log current state
            Log.d(TAG, "Recovery state: attempts=${state.attempts}, " +
                    "adminActive=${state.isDeviceAdminActive}, " +
                    "canAttempt=${state.canAttemptRecovery}")

            // If device admin is not active, attempt recovery
            if (!state.isDeviceAdminActive) {
                Log.w(TAG, "Device admin not active, initiating recovery...")

                val recoverySuccess = recoveryManager.secureDeviceOwnerRestore()
                if (recoverySuccess) {
                    Log.d(TAG, "✓ Device owner recovery successful")
                    recoveryManager.resetRecoveryState()
                    auditLog.logIncident(
                        type = "DEVICE_OWNER_RECOVERY_SUCCESS",
                        severity = "INFO",
                        details = "Device owner automatically restored"
                    )
                } else {
                    Log.e(TAG, "✗ Device owner recovery failed")
                    auditLog.logIncident(
                        type = "DEVICE_OWNER_RECOVERY_FAILED",
                        severity = "CRITICAL",
                        details = "Automatic recovery failed after ${state.attempts} attempts"
                    )
                }
            } else {
                Log.d(TAG, "✓ Device admin is active, no recovery needed")

                // Reset attempts if admin is active
                if (state.attempts > 0) {
                    recoveryManager.resetRecoveryState()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing recovery check", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DeviceOwnerRecoveryService destroyed")
        recoveryJob?.cancel()
        serviceScope.cancel()
    }
}
