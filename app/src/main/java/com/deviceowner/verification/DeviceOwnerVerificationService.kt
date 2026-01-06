package com.deviceowner.verification

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import androidx.work.*
import com.deviceowner.logging.StructuredLogger
import com.deviceowner.manager.DeviceOwnerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Service for periodic device owner verification and recovery.
 * Monitors device owner status continuously and handles automatic recovery.
 * All operations are local - no backend dependencies.
 */
class DeviceOwnerVerificationService(private val context: Context) {

    companion object {
        private const val TAG = "DeviceOwnerVerificationService"
        const val VERIFICATION_INTERVAL_MINUTES = 60L
        const val ALERT_THRESHOLD = 3 // Alert after 3 failed verifications
        private const val WORK_TAG = "device_owner_verification"
        private const val WORK_NAME = "device_owner_verification_work"
        private var instance: DeviceOwnerVerificationService? = null

        fun getInstance(context: Context): DeviceOwnerVerificationService {
            return instance ?: DeviceOwnerVerificationService(context).also { instance = it }
        }
    }

    private val deviceOwnerManager = DeviceOwnerManager.getInstance(context)
    private val logger = deviceOwnerManager.getLogger()
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val verificationStateManager = VerificationStateManager(context)

    /**
     * Start periodic device owner verification
     */
    fun startPeriodicVerification() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<DeviceOwnerVerificationWorker>(
                VERIFICATION_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .addTag(WORK_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15000L,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            logger.logInfo(
                tag = TAG,
                message = "Periodic device owner verification started",
                operation = "START_PERIODIC_VERIFICATION",
                details = mapOf(
                    "interval_minutes" to VERIFICATION_INTERVAL_MINUTES.toString(),
                    "alert_threshold" to ALERT_THRESHOLD.toString()
                )
            )

            Log.i(TAG, "Periodic verification started with interval: $VERIFICATION_INTERVAL_MINUTES minutes")
        } catch (e: Exception) {
            logger.logError(
                tag = TAG,
                code = "VERIFICATION_START_FAILED",
                message = "Failed to start periodic verification",
                operation = "START_PERIODIC_VERIFICATION",
                cause = e.message
            )
            Log.e(TAG, "Failed to start periodic verification", e)
        }
    }

    /**
     * Stop periodic device owner verification
     */
    fun stopPeriodicVerification() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

            logger.logInfo(
                tag = TAG,
                message = "Periodic device owner verification stopped",
                operation = "STOP_PERIODIC_VERIFICATION"
            )

            Log.i(TAG, "Periodic verification stopped")
        } catch (e: Exception) {
            logger.logError(
                tag = TAG,
                code = "VERIFICATION_STOP_FAILED",
                message = "Failed to stop periodic verification",
                operation = "STOP_PERIODIC_VERIFICATION",
                cause = e.message
            )
            Log.e(TAG, "Failed to stop periodic verification", e)
        }
    }

    /**
     * Verify device owner status and capabilities
     */
    suspend fun verifyDeviceOwner(): VerificationStatus {
        return withContext(Dispatchers.Default) {
            try {
                val isDeviceOwner = deviceOwnerManager.isDeviceOwner()

                if (!isDeviceOwner) {
                    handleDeviceOwnerRemoval()
                    return@withContext createFailedStatus(
                        isDeviceOwner = false,
                        failureCount = verificationStateManager.incrementFailureCount()
                    )
                }

                // Verify device owner capabilities
                val canLock = verifyLockCapability()
                val canSetPassword = verifyPasswordCapability()

                val status = if (!canLock || !canSetPassword) {
                    handleCapabilityLoss(canLock, canSetPassword)
                    createDegradedStatus(
                        canLock = canLock,
                        canSetPassword = canSetPassword,
                        failureCount = verificationStateManager.incrementFailureCount()
                    )
                } else {
                    // Reset failure count on successful verification
                    verificationStateManager.resetFailureCount()
                    createSuccessStatus()
                }

                logVerification(status)
                status
            } catch (e: Exception) {
                logger.logError(
                    tag = TAG,
                    code = "VERIFICATION_ERROR",
                    message = "Error during device owner verification",
                    operation = "VERIFY_DEVICE_OWNER",
                    cause = e.message
                )
                Log.e(TAG, "Verification error", e)
                createErrorStatus(failureCount = verificationStateManager.incrementFailureCount())
            }
        }
    }

    /**
     * Handle device owner removal
     */
    private suspend fun handleDeviceOwnerRemoval() {
        try {
            logger.logError(
                tag = TAG,
                code = "DEVICE_OWNER_REMOVED",
                message = "Device owner status has been removed",
                operation = "HANDLE_DEVICE_OWNER_REMOVAL",
                details = mapOf(
                    "timestamp" to System.currentTimeMillis().toString(),
                    "severity" to "CRITICAL"
                )
            )

            // Store incident locally
            verificationStateManager.recordIncident(
                type = "DEVICE_OWNER_REMOVED",
                severity = "CRITICAL",
                details = mapOf(
                    "timestamp" to System.currentTimeMillis().toString(),
                    "action" to "EMERGENCY_LOCK"
                )
            )

            // Lock device as emergency measure
            deviceOwnerManager.lockDevice()

            // Attempt recovery
            attemptRecovery()

            Log.e(TAG, "Device owner removed - emergency lock activated")
        } catch (e: Exception) {
            logger.logError(
                tag = TAG,
                code = "DEVICE_OWNER_REMOVAL_HANDLER_FAILED",
                message = "Failed to handle device owner removal",
                operation = "HANDLE_DEVICE_OWNER_REMOVAL",
                cause = e.message
            )
            Log.e(TAG, "Failed to handle device owner removal", e)
        }
    }

    /**
     * Handle capability loss
     */
    private suspend fun handleCapabilityLoss(canLock: Boolean, canSetPassword: Boolean) {
        try {
            val missingCapabilities = mutableListOf<String>()
            if (!canLock) missingCapabilities.add("LOCK")
            if (!canSetPassword) missingCapabilities.add("SET_PASSWORD")

            logger.logWarning(
                tag = TAG,
                message = "Device owner capabilities degraded",
                operation = "HANDLE_CAPABILITY_LOSS",
                details = mapOf(
                    "missing_capabilities" to missingCapabilities.joinToString(","),
                    "timestamp" to System.currentTimeMillis().toString(),
                    "severity" to "HIGH"
                )
            )

            // Store incident locally
            verificationStateManager.recordIncident(
                type = "CAPABILITY_LOSS",
                severity = "HIGH",
                details = mapOf(
                    "missing_capabilities" to missingCapabilities.joinToString(","),
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )

            Log.w(TAG, "Capability loss detected: $missingCapabilities")
        } catch (e: Exception) {
            logger.logError(
                tag = TAG,
                code = "CAPABILITY_LOSS_HANDLER_FAILED",
                message = "Failed to handle capability loss",
                operation = "HANDLE_CAPABILITY_LOSS",
                cause = e.message
            )
            Log.e(TAG, "Failed to handle capability loss", e)
        }
    }

    /**
     * Attempt to recover device owner status
     */
    private suspend fun attemptRecovery() {
        try {
            logger.logInfo(
                tag = TAG,
                message = "Attempting device owner recovery",
                operation = "ATTEMPT_RECOVERY",
                details = mapOf(
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )

            // Note: Full recovery requires ADB or NFC provisioning
            // This is a placeholder for recovery attempts that can be done programmatically
            // In production, this would trigger a recovery workflow

            verificationStateManager.recordRecoveryAttempt(
                timestamp = System.currentTimeMillis(),
                success = false,
                reason = "Recovery requires ADB/NFC provisioning"
            )

            Log.i(TAG, "Recovery attempt logged - manual intervention may be required")
        } catch (e: Exception) {
            logger.logError(
                tag = TAG,
                code = "RECOVERY_FAILED",
                message = "Device owner recovery failed",
                operation = "ATTEMPT_RECOVERY",
                cause = e.message,
                details = mapOf(
                    "timestamp" to System.currentTimeMillis().toString(),
                    "severity" to "CRITICAL"
                )
            )

            verificationStateManager.recordIncident(
                type = "DEVICE_OWNER_RECOVERY_FAILED",
                severity = "CRITICAL",
                details = mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )

            Log.e(TAG, "Recovery failed", e)
        }
    }

    /**
     * Verify lock capability
     */
    private fun verifyLockCapability(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify lock capability", e)
            false
        }
    }

    /**
     * Verify password capability
     */
    private fun verifyPasswordCapability(): Boolean {
        return try {
            val minLength = dpm.getPasswordMinimumLength(getAdminComponent())
            minLength >= 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify password capability", e)
            false
        }
    }

    /**
     * Get admin component
     */
    private fun getAdminComponent(): android.content.ComponentName {
        return android.content.ComponentName(context, "com.deviceowner.receiver.AdminReceiver")
    }

    /**
     * Log verification result
     */
    private fun logVerification(status: VerificationStatus) {
        val level = when (status.result) {
            VerificationResult.SUCCESS -> "INFO"
            VerificationResult.DEGRADED -> "WARNING"
            VerificationResult.FAILED -> "ERROR"
            VerificationResult.ERROR -> "ERROR"
        }

        val details = mapOf(
            "result" to status.result.name,
            "is_device_owner" to status.isDeviceOwner.toString(),
            "can_lock" to status.canLock.toString(),
            "can_set_password" to status.canSetPassword.toString(),
            "failure_count" to status.failureCount.toString(),
            "timestamp" to status.lastVerificationTime.toString()
        )

        when (level) {
            "INFO" -> logger.logInfo(
                tag = TAG,
                message = "Device owner verification successful",
                operation = "VERIFY_DEVICE_OWNER",
                details = details
            )
            "WARNING" -> logger.logWarning(
                tag = TAG,
                message = "Device owner verification degraded",
                operation = "VERIFY_DEVICE_OWNER",
                details = details
            )
            "ERROR" -> logger.logError(
                tag = TAG,
                code = "VERIFICATION_FAILED",
                message = "Device owner verification failed",
                operation = "VERIFY_DEVICE_OWNER",
                details = details
            )
        }
    }

    /**
     * Create success status
     */
    private fun createSuccessStatus(): VerificationStatus {
        return VerificationStatus(
            result = VerificationResult.SUCCESS,
            isDeviceOwner = true,
            canLock = true,
            canSetPassword = true,
            failureCount = 0,
            lastVerificationTime = System.currentTimeMillis()
        )
    }

    /**
     * Create degraded status
     */
    private fun createDegradedStatus(
        canLock: Boolean,
        canSetPassword: Boolean,
        failureCount: Int
    ): VerificationStatus {
        return VerificationStatus(
            result = VerificationResult.DEGRADED,
            isDeviceOwner = true,
            canLock = canLock,
            canSetPassword = canSetPassword,
            failureCount = failureCount,
            lastVerificationTime = System.currentTimeMillis()
        )
    }

    /**
     * Create failed status
     */
    private fun createFailedStatus(
        isDeviceOwner: Boolean,
        failureCount: Int
    ): VerificationStatus {
        return VerificationStatus(
            result = VerificationResult.FAILED,
            isDeviceOwner = isDeviceOwner,
            canLock = false,
            canSetPassword = false,
            failureCount = failureCount,
            lastVerificationTime = System.currentTimeMillis(),
            errorMessage = "Device owner status lost"
        )
    }

    /**
     * Create error status
     */
    private fun createErrorStatus(failureCount: Int): VerificationStatus {
        return VerificationStatus(
            result = VerificationResult.ERROR,
            isDeviceOwner = false,
            canLock = false,
            canSetPassword = false,
            failureCount = failureCount,
            lastVerificationTime = System.currentTimeMillis(),
            errorMessage = "Error during verification"
        )
    }

    /**
     * Get current verification status
     */
    fun getCurrentStatus(): VerificationStatus? {
        return verificationStateManager.getLastVerificationStatus()
    }

    /**
     * Get verification history
     */
    fun getVerificationHistory(): List<VerificationStatus> {
        return verificationStateManager.getVerificationHistory()
    }

    /**
     * Get incidents
     */
    fun getIncidents(): List<VerificationIncident> {
        return verificationStateManager.getIncidents()
    }

    /**
     * Get recovery attempts
     */
    fun getRecoveryAttempts(): List<RecoveryAttempt> {
        return verificationStateManager.getRecoveryAttempts()
    }

    /**
     * Clear verification history
     */
    fun clearHistory() {
        verificationStateManager.clearHistory()
        logger.logInfo(
            tag = TAG,
            message = "Verification history cleared",
            operation = "CLEAR_HISTORY"
        )
    }
}
