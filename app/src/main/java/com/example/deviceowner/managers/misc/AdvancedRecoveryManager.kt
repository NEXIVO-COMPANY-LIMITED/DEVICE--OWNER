package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Advanced Recovery Manager
 * 
 * Implements advanced recovery mechanisms with sophisticated issue identification
 * Feature 4.7 Enhancement #7: Advanced Recovery Mechanisms
 * 
 * Features:
 * - Sophisticated issue identification
 * - Multi-step recovery sequences
 * - Recovery verification
 * - Fallback mechanisms
 * - Comprehensive logging
 */
class AdvancedRecoveryManager(private val context: Context) {
    
    private val preventionManager = UninstallPreventionManager(context)
    private val auditLog = IdentifierAuditLog(context)
    private val deviceOwnerRecovery = DeviceOwnerRecoveryManager(context)
    private val verificationManager = ComprehensiveVerificationManager(context)
    private val encryptedStatus = EncryptedProtectionStatus.getInstance(context)
    private val prefs = context.getSharedPreferences("advanced_recovery", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "AdvancedRecoveryManager"
        private const val KEY_RECOVERY_ATTEMPTS = "recovery_attempts"
        private const val KEY_LAST_RECOVERY = "last_recovery"
        private const val KEY_SUCCESSFUL_RECOVERIES = "successful_recoveries"
        private const val KEY_FAILED_RECOVERIES = "failed_recoveries"
        
        @Volatile
        private var instance: AdvancedRecoveryManager? = null
        
        fun getInstance(context: Context): AdvancedRecoveryManager {
            return instance ?: synchronized(this) {
                instance ?: AdvancedRecoveryManager(context).also { instance = it }
            }
        }
    }
    
    /**
     * Execute recovery sequence
     * Comprehensive recovery with issue identification and resolution
     */
    suspend fun executeRecoverySequence(): RecoveryResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Executing advanced recovery sequence")
                
                // Increment recovery attempt counter
                val attemptCount = prefs.getInt(KEY_RECOVERY_ATTEMPTS, 0) + 1
                prefs.edit()
                    .putInt(KEY_RECOVERY_ATTEMPTS, attemptCount)
                    .putLong(KEY_LAST_RECOVERY, System.currentTimeMillis())
                    .apply()
                
                auditLog.logAction(
                    "RECOVERY_SEQUENCE_STARTED",
                    "Advanced recovery sequence started (Attempt #$attemptCount)"
                )
                
                // Step 1: Verify current state
                Log.d(TAG, "Step 1: Verifying current protection state")
                val currentState = getCurrentProtectionState()
                Log.d(TAG, "Current state: $currentState")
                
                // Step 2: Identify issues
                Log.d(TAG, "Step 2: Identifying issues")
                val issues = identifyIssues(currentState)
                Log.d(TAG, "Identified ${issues.size} issues: $issues")
                
                if (issues.isEmpty()) {
                    Log.d(TAG, "No issues found - protection is intact")
                    return@withContext RecoveryResult(
                        success = true,
                        issuesFound = emptyList(),
                        issuesResolved = emptyList(),
                        issuesFailed = emptyList(),
                        message = "No issues found - protection is intact"
                    )
                }
                
                // Step 3: Execute recovery steps
                Log.d(TAG, "Step 3: Executing recovery steps")
                val resolvedIssues = mutableListOf<String>()
                val failedIssues = mutableListOf<String>()
                
                for (issue in issues) {
                    Log.d(TAG, "Attempting to recover from: $issue")
                    val recovered = recoverFromIssue(issue)
                    
                    if (recovered) {
                        resolvedIssues.add(issue)
                        Log.d(TAG, "✓ Recovered from: $issue")
                    } else {
                        failedIssues.add(issue)
                        Log.e(TAG, "✗ Failed to recover from: $issue")
                    }
                }
                
                // Step 4: Verify recovery
                Log.d(TAG, "Step 4: Verifying recovery")
                val newState = getCurrentProtectionState()
                val recoverySuccessful = verifyRecovery(currentState, newState)
                
                // Step 5: Update statistics
                if (recoverySuccessful) {
                    val successCount = prefs.getInt(KEY_SUCCESSFUL_RECOVERIES, 0) + 1
                    prefs.edit().putInt(KEY_SUCCESSFUL_RECOVERIES, successCount).apply()
                    
                    Log.d(TAG, "✓ Recovery sequence completed successfully")
                    auditLog.logAction(
                        "RECOVERY_SUCCESSFUL",
                        "Advanced recovery completed - ${resolvedIssues.size} issues resolved"
                    )
                } else {
                    val failCount = prefs.getInt(KEY_FAILED_RECOVERIES, 0) + 1
                    prefs.edit().putInt(KEY_FAILED_RECOVERIES, failCount).apply()
                    
                    Log.e(TAG, "✗ Recovery sequence failed")
                    auditLog.logIncident(
                        "RECOVERY_FAILED",
                        "CRITICAL",
                        "Advanced recovery failed - ${failedIssues.size} issues unresolved"
                    )
                }
                
                // Store recovery result
                storeRecoveryResult(recoverySuccessful, issues.size, resolvedIssues.size)
                
                return@withContext RecoveryResult(
                    success = recoverySuccessful,
                    issuesFound = issues,
                    issuesResolved = resolvedIssues,
                    issuesFailed = failedIssues,
                    message = if (recoverySuccessful) {
                        "Recovery successful - ${resolvedIssues.size}/${issues.size} issues resolved"
                    } else {
                        "Recovery failed - ${failedIssues.size}/${issues.size} issues unresolved"
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error executing recovery sequence", e)
                
                auditLog.logIncident(
                    "RECOVERY_ERROR",
                    "CRITICAL",
                    "Recovery sequence error: ${e.message}"
                )
                
                return@withContext RecoveryResult(
                    success = false,
                    issuesFound = emptyList(),
                    issuesResolved = emptyList(),
                    issuesFailed = emptyList(),
                    message = "Recovery error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Get current protection state
     */
    private suspend fun getCurrentProtectionState(): ProtectionState {
        return try {
            ProtectionState(
                appInstalled = preventionManager.verifyAppInstalled(),
                deviceOwnerEnabled = preventionManager.verifyDeviceOwnerEnabled(),
                uninstallBlocked = preventionManager.isUninstallBlocked(),
                forceStopBlocked = preventionManager.isForceStopBlocked(),
                encryptedStatusValid = encryptedStatus.retrieveProtectionStatus() != null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current protection state", e)
            ProtectionState(
                appInstalled = false,
                deviceOwnerEnabled = false,
                uninstallBlocked = false,
                forceStopBlocked = false,
                encryptedStatusValid = false
            )
        }
    }
    
    /**
     * Identify issues
     */
    private fun identifyIssues(state: ProtectionState): List<String> {
        val issues = mutableListOf<String>()
        
        if (!state.appInstalled) {
            issues.add("APP_NOT_INSTALLED")
        }
        
        if (!state.deviceOwnerEnabled) {
            issues.add("DEVICE_OWNER_DISABLED")
        }
        
        if (!state.uninstallBlocked) {
            issues.add("UNINSTALL_NOT_BLOCKED")
        }
        
        if (!state.forceStopBlocked) {
            issues.add("FORCE_STOP_NOT_BLOCKED")
        }
        
        if (!state.encryptedStatusValid) {
            issues.add("ENCRYPTED_STATUS_INVALID")
        }
        
        return issues
    }
    
    /**
     * Recover from specific issue
     */
    private suspend fun recoverFromIssue(issue: String): Boolean {
        return try {
            when (issue) {
                "APP_NOT_INSTALLED" -> {
                    Log.w(TAG, "Cannot recover: app not installed")
                    false
                }
                
                "DEVICE_OWNER_DISABLED" -> {
                    Log.w(TAG, "Attempting to restore device owner")
                    deviceOwnerRecovery.secureDeviceOwnerRestore()
                }
                
                "UNINSTALL_NOT_BLOCKED" -> {
                    Log.w(TAG, "Re-enabling uninstall prevention")
                    preventionManager.disableUninstall()
                    true
                }
                
                "FORCE_STOP_NOT_BLOCKED" -> {
                    Log.w(TAG, "Re-enabling force-stop prevention")
                    preventionManager.disableForceStop()
                    true
                }
                
                "ENCRYPTED_STATUS_INVALID" -> {
                    Log.w(TAG, "Recreating encrypted status")
                    preventionManager.updateEncryptedProtectionStatus()
                    true
                }
                
                else -> {
                    Log.w(TAG, "Unknown issue: $issue")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recovering from issue: $issue", e)
            false
        }
    }
    
    /**
     * Verify recovery
     */
    private fun verifyRecovery(before: ProtectionState, after: ProtectionState): Boolean {
        return try {
            // Check if all critical protections are now in place
            val criticalProtectionsRestored = 
                after.appInstalled && 
                after.deviceOwnerEnabled && 
                after.uninstallBlocked && 
                after.forceStopBlocked
            
            if (criticalProtectionsRestored) {
                Log.d(TAG, "✓ All critical protections restored")
                return true
            }
            
            // Check if at least some improvements were made
            val improvements = 
                (after.appInstalled && !before.appInstalled) ||
                (after.deviceOwnerEnabled && !before.deviceOwnerEnabled) ||
                (after.uninstallBlocked && !before.uninstallBlocked) ||
                (after.forceStopBlocked && !before.forceStopBlocked)
            
            if (improvements) {
                Log.d(TAG, "✓ Some protections restored")
                return true
            }
            
            Log.e(TAG, "✗ No improvements made")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying recovery", e)
            return false
        }
    }
    
    /**
     * Store recovery result
     */
    private fun storeRecoveryResult(success: Boolean, issuesFound: Int, issuesResolved: Int) {
        try {
            prefs.edit()
                .putBoolean("last_recovery_success", success)
                .putInt("last_issues_found", issuesFound)
                .putInt("last_issues_resolved", issuesResolved)
                .putLong("last_recovery_timestamp", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error storing recovery result", e)
        }
    }
    
    /**
     * Get recovery statistics
     */
    fun getRecoveryStatistics(): RecoveryStatistics {
        return try {
            RecoveryStatistics(
                totalAttempts = prefs.getInt(KEY_RECOVERY_ATTEMPTS, 0),
                successfulRecoveries = prefs.getInt(KEY_SUCCESSFUL_RECOVERIES, 0),
                failedRecoveries = prefs.getInt(KEY_FAILED_RECOVERIES, 0),
                lastRecoveryTimestamp = prefs.getLong(KEY_LAST_RECOVERY, 0),
                lastRecoverySuccess = prefs.getBoolean("last_recovery_success", false),
                lastIssuesFound = prefs.getInt("last_issues_found", 0),
                lastIssuesResolved = prefs.getInt("last_issues_resolved", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recovery statistics", e)
            RecoveryStatistics(0, 0, 0, 0, false, 0, 0)
        }
    }
    
    /**
     * Reset recovery statistics
     */
    fun resetRecoveryStatistics() {
        try {
            prefs.edit()
                .putInt(KEY_RECOVERY_ATTEMPTS, 0)
                .putInt(KEY_SUCCESSFUL_RECOVERIES, 0)
                .putInt(KEY_FAILED_RECOVERIES, 0)
                .apply()
            
            Log.d(TAG, "Recovery statistics reset")
            auditLog.logAction("RECOVERY_STATS_RESET", "Recovery statistics reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting recovery statistics", e)
        }
    }
    
    /**
     * Quick recovery check
     * Fast check to see if recovery is needed
     */
    suspend fun quickRecoveryCheck(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val state = getCurrentProtectionState()
                val needsRecovery = !state.appInstalled || 
                                   !state.deviceOwnerEnabled || 
                                   !state.uninstallBlocked || 
                                   !state.forceStopBlocked
                
                if (needsRecovery) {
                    Log.w(TAG, "Quick check: Recovery needed")
                } else {
                    Log.d(TAG, "Quick check: No recovery needed")
                }
                
                return@withContext needsRecovery
            } catch (e: Exception) {
                Log.e(TAG, "Error in quick recovery check", e)
                return@withContext true // Assume recovery needed on error
            }
        }
    }
}

/**
 * Protection State Data Class
 */
data class ProtectionState(
    val appInstalled: Boolean,
    val deviceOwnerEnabled: Boolean,
    val uninstallBlocked: Boolean,
    val forceStopBlocked: Boolean,
    val encryptedStatusValid: Boolean
)

/**
 * Recovery Result Data Class
 */
data class RecoveryResult(
    val success: Boolean,
    val issuesFound: List<String>,
    val issuesResolved: List<String>,
    val issuesFailed: List<String>,
    val message: String
)

/**
 * Recovery Statistics Data Class
 */
data class RecoveryStatistics(
    val totalAttempts: Int,
    val successfulRecoveries: Int,
    val failedRecoveries: Int,
    val lastRecoveryTimestamp: Long,
    val lastRecoverySuccess: Boolean,
    val lastIssuesFound: Int,
    val lastIssuesResolved: Int
) {
    val successRate: Double
        get() = if (totalAttempts > 0) {
            (successfulRecoveries.toDouble() / totalAttempts.toDouble()) * 100
        } else {
            0.0
        }
}
