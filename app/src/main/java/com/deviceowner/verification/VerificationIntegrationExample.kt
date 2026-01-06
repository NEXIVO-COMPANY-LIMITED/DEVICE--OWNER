package com.deviceowner.verification

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Example integration of Device Owner Verification Service.
 * Shows how to use the verification service in your application.
 */
class VerificationIntegrationExample(private val context: Context) {

    companion object {
        private const val TAG = "VerificationIntegration"
    }

    private val verificationService = DeviceOwnerVerificationService.getInstance(context)

    /**
     * Initialize verification on app startup
     */
    fun initializeVerification() {
        Log.i(TAG, "Initializing device owner verification")

        // Start periodic verification (runs every 60 minutes)
        verificationService.startPeriodicVerification()

        // Perform immediate verification
        performImmediateVerification()
    }

    /**
     * Perform immediate verification
     */
    private fun performImmediateVerification() {
        // This should be called from a coroutine context
        // Example in ViewModel:
        // viewModelScope.launch {
        //     val status = verificationService.verifyDeviceOwner()
        //     handleVerificationResult(status)
        // }
    }

    /**
     * Handle verification result
     */
    fun handleVerificationResult(status: VerificationStatus) {
        when (status.result) {
            VerificationResult.SUCCESS -> {
                Log.i(TAG, "Device owner verification successful")
                // Device is secure, continue normal operation
            }
            VerificationResult.DEGRADED -> {
                Log.w(TAG, "Device owner capabilities degraded")
                // Some capabilities are missing, alert user
                showDegradedAlert(status)
            }
            VerificationResult.FAILED -> {
                Log.e(TAG, "Device owner verification failed")
                // Device owner status lost, take emergency action
                handleDeviceOwnerLoss(status)
            }
            VerificationResult.ERROR -> {
                Log.e(TAG, "Device owner verification error")
                // Error during verification, retry
                retryVerification()
            }
        }
    }

    /**
     * Show alert for degraded capabilities
     */
    private fun showDegradedAlert(status: VerificationStatus) {
        val missingCapabilities = mutableListOf<String>()
        if (!status.canLock) missingCapabilities.add("Device Lock")
        if (!status.canSetPassword) missingCapabilities.add("Password Management")

        Log.w(TAG, "Missing capabilities: $missingCapabilities")
        // Show UI alert to user
    }

    /**
     * Handle device owner loss
     */
    private fun handleDeviceOwnerLoss(status: VerificationStatus) {
        Log.e(TAG, "Device owner status lost - taking emergency action")

        // Get incident details
        val incidents: List<VerificationIncident> = verificationService.getIncidents()
        val criticalIncidents = incidents.filter { incident: VerificationIncident -> incident.severity == "CRITICAL" }

        Log.e(TAG, "Critical incidents: ${criticalIncidents.size}")

        // Lock device (already done by service)
        // Show emergency UI
        // Notify administrator
    }

    /**
     * Retry verification
     */
    private fun retryVerification() {
        Log.i(TAG, "Retrying verification")
        // Retry logic handled by WorkManager
    }

    /**
     * Get current verification status
     */
    fun getCurrentStatus(): VerificationStatus? {
        return verificationService.getCurrentStatus()
    }

    /**
     * Get verification history
     */
    fun getVerificationHistory(): List<VerificationStatus> {
        return verificationService.getVerificationHistory()
    }

    /**
     * Get incidents
     */
    fun getIncidents(): List<VerificationIncident> {
        return verificationService.getIncidents()
    }

    /**
     * Get recovery attempts
     */
    fun getRecoveryAttempts(): List<RecoveryAttempt> {
        return verificationService.getRecoveryAttempts()
    }

    /**
     * Stop verification
     */
    fun stopVerification() {
        Log.i(TAG, "Stopping device owner verification")
        verificationService.stopPeriodicVerification()
    }

    /**
     * Clear verification history
     */
    fun clearHistory() {
        Log.i(TAG, "Clearing verification history")
        verificationService.clearHistory()
    }
}

/**
 * Example ViewModel for using verification service
 */
class VerificationViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "VerificationViewModel"
    }

    private val verificationService = DeviceOwnerVerificationService.getInstance(context)

    /**
     * Perform verification in coroutine
     */
    fun verifyDeviceOwner() {
        viewModelScope.launch {
            try {
                val status = verificationService.verifyDeviceOwner()
                Log.i(TAG, "Verification result: ${status.result}")

                // Update UI with status
                when (status.result) {
                    VerificationResult.SUCCESS -> {
                        Log.i(TAG, "Device owner verified successfully")
                    }
                    VerificationResult.DEGRADED -> {
                        Log.w(TAG, "Device owner capabilities degraded")
                    }
                    VerificationResult.FAILED -> {
                        Log.e(TAG, "Device owner verification failed")
                    }
                    VerificationResult.ERROR -> {
                        Log.e(TAG, "Device owner verification error")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Verification exception", e)
            }
        }
    }

    /**
     * Get verification statistics
     */
    fun getStatistics(): String {
        val history = verificationService.getVerificationHistory()
        val incidents = verificationService.getIncidents()
        val recoveryAttempts = verificationService.getRecoveryAttempts()

        return """
            Verification Statistics:
            - Total Verifications: ${history.size}
            - Successful: ${history.count { status: VerificationStatus -> status.result == VerificationResult.SUCCESS }}
            - Degraded: ${history.count { status: VerificationStatus -> status.result == VerificationResult.DEGRADED }}
            - Failed: ${history.count { status: VerificationStatus -> status.result == VerificationResult.FAILED }}
            - Errors: ${history.count { status: VerificationStatus -> status.result == VerificationResult.ERROR }}
            
            Incidents:
            - Total: ${incidents.size}
            - Critical: ${incidents.count { incident: VerificationIncident -> incident.severity == "CRITICAL" }}
            - High: ${incidents.count { incident: VerificationIncident -> incident.severity == "HIGH" }}
            
            Recovery Attempts:
            - Total: ${recoveryAttempts.size}
            - Successful: ${recoveryAttempts.count { attempt: RecoveryAttempt -> attempt.success }}
            - Failed: ${recoveryAttempts.count { attempt: RecoveryAttempt -> !attempt.success }}
        """.trimIndent()
    }
}

/**
 * Example usage in Application class
 */
class DeviceOwnerApplication : android.app.Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize verification service on app startup
        val verificationService = DeviceOwnerVerificationService.getInstance(this)
        verificationService.startPeriodicVerification()

        Log.i("DeviceOwnerApplication", "Device owner verification initialized")
    }
}
