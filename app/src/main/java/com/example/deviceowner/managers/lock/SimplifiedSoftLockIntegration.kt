package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import com.example.deviceowner.models.*
import com.example.deviceowner.overlay.OverlayController
import com.example.deviceowner.overlay.OverlayData
import com.example.deviceowner.overlay.OverlayType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Simplified Soft Lock Integration
 * Feature 4.6: Simplified Soft Lock System
 * 
 * Behavior:
 * 1. Normal triggers (dev options, safe mode): Show overlay with reason and buttons
 * 2. Payment reminder: Show ONCE per day only
 * 3. User takes no action: Escalate to HARD_LOCK after timeout
 * 4. Simple flow: Show overlay → User action or timeout → Hard lock
 */
class SimplifiedSoftLockIntegration(private val context: Context) {

    companion object {
        private const val TAG = "SimplifiedSoftLockIntegration"
        private const val PREFS_NAME = "simplified_soft_lock_prefs"
        private const val KEY_SOFT_LOCKS = "soft_locks"
        private const val KEY_LAST_PAYMENT_REMINDER = "last_payment_reminder"
        private const val KEY_TIMEOUT_TRACKER = "timeout_tracker"
        
        // Timeout before escalating to hard lock (24 hours)
        private const val HARD_LOCK_TIMEOUT_MS = 24 * 60 * 60 * 1000L
        
        // Payment reminder shown once per day
        private const val PAYMENT_REMINDER_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val overlayController = OverlayController(context)
    private val auditLog = IdentifierAuditLog(context)
    private val remoteLockManager = RemoteLockManager(context)
    private val deviceId = android.os.Build.SERIAL ?: "unknown_device"

    private var timeoutScope = CoroutineScope(Dispatchers.Main + Job())
    private val timeoutTrackers = ConcurrentHashMap<String, Job>()
    private val prefsLock = Any()  // Thread safety lock for SharedPreferences access

    init {
        // Restore persisted timeouts on initialization
        restorePersistedTimeouts()
    }

    /**
     * Restore timeout timers from persisted state
     * Called on initialization to handle app restart scenarios
     */
    private fun restorePersistedTimeouts() {
        try {
            synchronized(prefsLock) {
                val trackerJson = prefs.getString(KEY_TIMEOUT_TRACKER, "{}")
                if (trackerJson?.isEmpty() != false || trackerJson == "{}") {
                    Log.d(TAG, "No persisted timeouts to restore")
                    return
                }

                val typeToken = object : TypeToken<Map<String, Map<String, String>>>() {}.type
                val trackers: Map<String, Map<String, String>> = gson.fromJson(trackerJson, typeToken)

                val currentTime = System.currentTimeMillis()
                for ((lockId, timeoutData) in trackers) {
                    try {
                        val timeoutAt = timeoutData["timeout_at"]?.toLongOrNull() ?: continue
                        val remainingTime = timeoutAt - currentTime

                        if (remainingTime > 0) {
                            Log.d(TAG, "Restoring timeout for $lockId (${remainingTime}ms remaining)")
                            val softLock = getSoftLock(lockId)
                            if (softLock != null && !softLock.isResolved) {
                                startHardLockTimeoutWithDelay(softLock, remainingTime)
                            }
                        } else {
                            Log.d(TAG, "Timeout already expired for $lockId, escalating to hard lock")
                            val softLock = getSoftLock(lockId)
                            if (softLock != null && !softLock.isResolved) {
                                escalateToHardLock(softLock)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restoring timeout for $lockId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring persisted timeouts", e)
        }
    }

    /**
     * Apply soft lock for normal triggers (developer options, safe mode)
     * Shows overlay with reason and buttons
     * User can dismiss or acknowledge
     * If no action taken for 24 hours, escalates to hard lock
     */
    fun applySoftLockForTrigger(
        lockId: String,
        reason: LockReason,
        title: String,
        message: String
    ): Boolean {
        return try {
            Log.w(TAG, "Applying soft lock for trigger - Reason: $reason")

            val softLock = SimpleSoftLockData(
                lockId = lockId,
                reason = reason,
                title = title,
                message = message,
                type = SoftLockType.TRIGGER,
                createdAt = System.currentTimeMillis(),
                lastShownAt = System.currentTimeMillis()
            )

            saveSoftLock(softLock)

            // Display overlay with reason and buttons
            displaySoftLockOverlay(softLock)

            // Start timeout timer for hard lock escalation
            startHardLockTimeout(softLock)

            auditLog.logAction(
                "SOFT_LOCK_TRIGGER_APPLIED",
                "Soft lock applied for trigger: $reason"
            )

            Log.d(TAG, "✓ Soft lock applied for trigger")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying soft lock for trigger", e)
            false
        }
    }

    /**
     * Apply soft lock for payment reminder
     * Shows ONCE per day only
     * Reminds user to make payment
     * If no action taken for 24 hours, escalates to hard lock
     */
    fun applySoftLockForPaymentReminder(
        loanId: String,
        amount: String,
        dueDate: String,
        daysUntilDue: Int
    ): Boolean {
        return try {
            Log.w(TAG, "Applying soft lock for payment reminder - Loan: $loanId")

            // Check if payment reminder already shown today
            val lastShownTime = prefs.getLong(KEY_LAST_PAYMENT_REMINDER + "_$loanId", 0L)
            val timeSinceLastShown = System.currentTimeMillis() - lastShownTime

            if (timeSinceLastShown < PAYMENT_REMINDER_INTERVAL_MS) {
                Log.d(TAG, "Payment reminder already shown today, skipping")
                return false
            }

            // Create payment reminder soft lock
            val softLock = SimpleSoftLockData(
                lockId = "soft_lock_payment_$loanId",
                reason = LockReason.PAYMENT_REMINDER,
                title = "💰 Payment Reminder",
                message = "Loan ID: $loanId\n" +
                        "Amount Due: $amount\n" +
                        "Due Date: $dueDate\n\n" +
                        "Please make payment to avoid device restrictions.",
                type = SoftLockType.PAYMENT_REMINDER,
                metadata = mapOf(
                    "loan_id" to loanId,
                    "amount" to amount,
                    "due_date" to dueDate,
                    "days_until_due" to daysUntilDue.toString()
                ),
                createdAt = System.currentTimeMillis(),
                lastShownAt = System.currentTimeMillis()
            )

            saveSoftLock(softLock)

            // Update last shown time
            prefs.edit().putLong(KEY_LAST_PAYMENT_REMINDER + "_$loanId", System.currentTimeMillis()).apply()

            // Display overlay
            displaySoftLockOverlay(softLock)

            // Start timeout timer for hard lock escalation
            startHardLockTimeout(softLock)

            auditLog.logAction(
                "SOFT_LOCK_PAYMENT_REMINDER_APPLIED",
                "Payment reminder soft lock applied - Loan: $loanId"
            )

            Log.d(TAG, "✓ Payment reminder soft lock applied (shown once today)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying payment reminder soft lock", e)
            false
        }
    }

    /**
     * Handle developer options access attempt
     * Shows overlay with reason and buttons
     * User can acknowledge or dismiss
     * If no action: escalates to hard lock after 24 hours
     */
    fun handleDeveloperOptionsAttempt() {
        try {
            Log.w(TAG, "Developer options access attempt detected")

            applySoftLockForTrigger(
                lockId = "soft_lock_dev_${System.currentTimeMillis()}",
                reason = LockReason.DEVELOPER_OPTIONS_ATTEMPT,
                title = "⚠️ Developer Options Access Detected",
                message = "You attempted to access developer options.\n\n" +
                        "This action is restricted on this device.\n\n" +
                        "If you need assistance, please contact support."
            )

            auditLog.logIncident(
                type = "DEVELOPER_OPTIONS_ATTEMPT",
                severity = "MEDIUM",
                details = "User attempted to access developer options"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling developer options attempt", e)
        }
    }

    /**
     * Handle safe mode boot
     * Shows overlay with reason and buttons
     * User can acknowledge or dismiss
     * If no action: escalates to hard lock after 24 hours
     */
    fun handleSafeModeBoot() {
        try {
            Log.w(TAG, "Safe mode boot detected")

            applySoftLockForTrigger(
                lockId = "soft_lock_safe_${System.currentTimeMillis()}",
                reason = LockReason.SAFE_MODE_ATTEMPT,
                title = "⚠️ Safe Mode Detected",
                message = "Device booted in safe mode.\n\n" +
                        "This is not allowed on this device.\n\n" +
                        "Please restart your device normally."
            )

            auditLog.logIncident(
                type = "SAFE_MODE_BOOT",
                severity = "MEDIUM",
                details = "Device booted into safe mode"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling safe mode boot", e)
        }
    }

    /**
     * Handle payment reminder
     * Shows ONCE per day only
     * Reminds user to make payment
     * If no action: escalates to hard lock after 24 hours
     */
    fun handlePaymentReminder(
        loanId: String,
        amount: String,
        dueDate: String,
        daysUntilDue: Int
    ) {
        try {
            Log.w(TAG, "Payment reminder triggered - Loan: $loanId, Days until due: $daysUntilDue")

            val success = applySoftLockForPaymentReminder(
                loanId = loanId,
                amount = amount,
                dueDate = dueDate,
                daysUntilDue = daysUntilDue
            )

            if (success) {
                auditLog.logAction(
                    "PAYMENT_REMINDER_SOFT_LOCK",
                    "Payment reminder soft lock applied - Loan: $loanId (shown once today)"
                )
            } else {
                Log.d(TAG, "Payment reminder already shown today for loan: $loanId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling payment reminder", e)
        }
    }

    /**
     * Display soft lock overlay with reason and buttons
     */
    private fun displaySoftLockOverlay(softLock: SimpleSoftLockData) {
        try {
            Log.d(TAG, "Displaying soft lock overlay")

            val overlay = OverlayData(
                id = softLock.lockId,
                type = OverlayType.SOFT_LOCK,
                title = softLock.title,
                message = softLock.message,
                actionButtonText = "Acknowledge",
                actionButtonColor = 0xFF4CAF50.toInt(),  // Green
                dismissible = true,
                priority = 10,
                blockAllInteractions = false,  // Device usable
                metadata = mapOf(
                    "reason" to softLock.reason.name,
                    "type" to softLock.type.name,
                    "lock_id" to softLock.lockId
                )
            )

            overlayController.showOverlay(overlay)

            Log.d(TAG, "✓ Soft lock overlay displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying soft lock overlay", e)
        }
    }

    /**
     * Start timeout timer for hard lock escalation
     * If user doesn't take action within 24 hours, escalate to hard lock
     * Persists timeout state to survive app restart
     */
    private fun startHardLockTimeout(softLock: SimpleSoftLockData) {
        startHardLockTimeoutWithDelay(softLock, HARD_LOCK_TIMEOUT_MS)
    }

    /**
     * Start timeout timer with custom delay
     * Used for both new timeouts and restored timeouts
     */
    private fun startHardLockTimeoutWithDelay(softLock: SimpleSoftLockData, delayMs: Long) {
        // Cancel any existing timeout for this lock
        timeoutTrackers[softLock.lockId]?.cancel()

        // Persist timeout info
        synchronized(prefsLock) {
            val timeoutData = mapOf(
                "lock_id" to softLock.lockId,
                "created_at" to System.currentTimeMillis().toString(),
                "timeout_at" to (System.currentTimeMillis() + delayMs).toString()
            )
            val trackerJson = prefs.getString(KEY_TIMEOUT_TRACKER, "{}")
            val typeToken = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            val trackers: MutableMap<String, Map<String, String>> = gson.fromJson(trackerJson, typeToken) ?: mutableMapOf()
            trackers[softLock.lockId] = timeoutData
            prefs.edit().putString(KEY_TIMEOUT_TRACKER, gson.toJson(trackers)).apply()
        }

        // Start coroutine timeout
        val job = timeoutScope.launch {
            try {
                Log.d(TAG, "Starting hard lock timeout timer (${delayMs}ms) for ${softLock.lockId}")
                delay(delayMs)

                // Check if soft lock still exists and not resolved
                val currentLock = getSoftLock(softLock.lockId)
                if (currentLock != null && !currentLock.isResolved) {
                    Log.w(TAG, "Hard lock timeout reached - escalating to hard lock")
                    escalateToHardLock(currentLock)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Hard lock timeout cancelled for ${softLock.lockId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in hard lock timeout", e)
            }
        }

        timeoutTrackers[softLock.lockId] = job
    }

    /**
     * Escalate soft lock to hard lock
     * Called when user doesn't take action within 24 hours
     */
    private fun escalateToHardLock(softLock: SimpleSoftLockData) {
        try {
            Log.w(TAG, "Escalating soft lock to hard lock - Reason: ${softLock.reason}")

            val hardLock = DeviceLock(
                lockId = "hard_lock_${System.currentTimeMillis()}",
                deviceId = deviceId,  // Use actual device ID instead of hardcoded value
                lockType = LockType.HARD,
                lockStatus = LockStatus.ACTIVE,
                lockReason = softLock.reason,
                message = "Device locked due to unresolved soft lock:\n${softLock.message}",
                expiresAt = null,
                backendUnlockOnly = true
            )

            val success = remoteLockManager.applyLock(hardLock)
            if (success) {
                // Only remove soft lock if hard lock was successfully applied
                removeSoftLock(softLock.lockId)
                clearTimeoutTracker(softLock.lockId)

                auditLog.logIncident(
                    type = "SOFT_LOCK_ESCALATED_TO_HARD",
                    severity = "HIGH",
                    details = "Soft lock escalated to hard lock after 24 hours - Reason: ${softLock.reason}"
                )

                Log.w(TAG, "✓ Escalated to hard lock")
            } else {
                Log.e(TAG, "Failed to apply hard lock, soft lock remains active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escalating to hard lock", e)
        }
    }

    /**
     * Handle soft lock action (user acknowledges or dismisses)
     */
    fun handleSoftLockAction(lockId: String, actionType: String): Boolean {
        return try {
            val softLock = getSoftLock(lockId) ?: return false

            when (actionType) {
                "ACKNOWLEDGE" -> {
                    Log.d(TAG, "Soft lock acknowledged by user")
                    resolveSoftLock(lockId)
                    true
                }
                "DISMISS" -> {
                    Log.d(TAG, "Soft lock dismissed by user")
                    // Just dismiss overlay, soft lock still active
                    // Will escalate to hard lock if no action taken within 24 hours
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling soft lock action", e)
            false
        }
    }

    /**
     * Resolve soft lock (user acknowledged or payment made)
     */
    fun resolveSoftLock(lockId: String): Boolean {
        return try {
            val softLock = getSoftLock(lockId) ?: return false

            val resolved = softLock.copy(
                isResolved = true,
                resolvedAt = System.currentTimeMillis()
            )

            saveSoftLock(resolved)

            // Cancel timeout timer
            timeoutTrackers[lockId]?.cancel()
            timeoutTrackers.remove(lockId)
            clearTimeoutTracker(lockId)

            // Dismiss overlay
            overlayController.dismissOverlay(lockId)

            auditLog.logAction(
                "SOFT_LOCK_RESOLVED",
                "Soft lock resolved - ${softLock.reason}"
            )

            Log.d(TAG, "✓ Soft lock resolved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving soft lock", e)
            false
        }
    }

    /**
     * Resolve soft lock when payment is made
     */
    fun resolveSoftLockForPayment(loanId: String): Boolean {
        val lockId = "soft_lock_payment_$loanId"
        return resolveSoftLock(lockId)
    }

    /**
     * Get soft lock by ID
     */
    fun getSoftLock(lockId: String): SimpleSoftLockData? {
        return try {
            val locks = getSoftLocks()
            locks[lockId]
        } catch (e: Exception) {
            Log.e(TAG, "Error getting soft lock", e)
            null
        }
    }

    /**
     * Get all active soft locks
     */
    fun getSoftLocks(): Map<String, SimpleSoftLockData> {
        return try {
            synchronized(prefsLock) {
                val json = prefs.getString(KEY_SOFT_LOCKS, "{}")
                if (json?.isEmpty() != false || json == "{}") {
                    return emptyMap()
                }
                val typeToken = object : TypeToken<Map<String, SimpleSoftLockData>>() {}.type
                gson.fromJson(json, typeToken) ?: emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting soft locks", e)
            emptyMap()
        }
    }

    /**
     * Get all active soft locks (public API)
     */
    fun getActiveSoftLocks(): Map<String, SimpleSoftLockData> {
        return getSoftLocks()
    }

    /**
     * Check if device has active soft locks
     */
    fun hasActiveSoftLocks(): Boolean {
        return getSoftLocks().isNotEmpty()
    }

    /**
     * Remove soft lock
     */
    fun removeSoftLock(lockId: String): Boolean {
        return try {
            synchronized(prefsLock) {
                val locks = getSoftLocks().toMutableMap()
                locks.remove(lockId)
                prefs.edit().putString(KEY_SOFT_LOCKS, gson.toJson(locks)).apply()
            }

            // Cancel timeout
            timeoutTrackers[lockId]?.cancel()
            timeoutTrackers.remove(lockId)
            clearTimeoutTracker(lockId)

            Log.d(TAG, "Soft lock removed: $lockId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing soft lock", e)
            false
        }
    }

    /**
     * Clear timeout tracker from preferences
     */
    private fun clearTimeoutTracker(lockId: String) {
        try {
            synchronized(prefsLock) {
                val trackerJson = prefs.getString(KEY_TIMEOUT_TRACKER, "{}")
                if (trackerJson?.isEmpty() != false || trackerJson == "{}") {
                    return
                }
                val typeToken = object : TypeToken<Map<String, Map<String, String>>>() {}.type
                val trackers: MutableMap<String, Map<String, String>> = gson.fromJson(trackerJson, typeToken) ?: mutableMapOf()
                trackers.remove(lockId)
                prefs.edit().putString(KEY_TIMEOUT_TRACKER, gson.toJson(trackers)).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing timeout tracker", e)
        }
    }

    /**
     * Save soft lock
     */
    private fun saveSoftLock(softLock: SimpleSoftLockData) {
        try {
            synchronized(prefsLock) {
                val locks = getSoftLocks().toMutableMap()
                locks[softLock.lockId] = softLock
                prefs.edit().putString(KEY_SOFT_LOCKS, gson.toJson(locks)).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving soft lock", e)
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            // Cancel all pending timeouts
            timeoutTrackers.values.forEach { it.cancel() }
            timeoutTrackers.clear()
            
            // Cancel the scope
            timeoutScope.cancel()
            Log.d(TAG, "✓ Cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}

/**
 * Soft lock type
 */
enum class SoftLockType {
    TRIGGER,           // Developer options, safe mode, etc.
    PAYMENT_REMINDER   // Payment reminder
}

/**
 * Simplified soft lock data
 */
data class SimpleSoftLockData(
    val lockId: String,
    val reason: LockReason,
    val title: String,
    val message: String,
    val type: SoftLockType,
    val createdAt: Long = System.currentTimeMillis(),
    val lastShownAt: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val resolvedAt: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)
