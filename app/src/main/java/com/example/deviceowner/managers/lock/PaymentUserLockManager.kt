package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.deviceowner.models.*
import com.example.deviceowner.overlay.OverlayController
import com.example.deviceowner.overlay.OverlayData
import com.example.deviceowner.overlay.OverlayType
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * Manages lock enforcement for payment users on loans
 * Feature 4.4: Remote Lock/Unlock with Payment Integration
 * 
 * Lock Triggers:
 * SOFT LOCKS:
 *   - Developer options access attempt
 *   - Safe mode boot attempt
 *   - Payment reminder (2 days before due date)
 * 
 * HARD LOCKS:
 *   - System tampering detected
 *   - Payment overdue
 *   - Device mismatch/swap detected
 *   - Unauthorized app removal attempts
 */
class PaymentUserLockManager(private val context: Context) {

    companion object {
        private const val TAG = "PaymentUserLockManager"
        private const val PREFS_NAME = "payment_user_lock_prefs"
        private const val KEY_PAYMENT_LOCKS = "payment_locks"
        private const val KEY_LOCK_HISTORY = "lock_history"
        private const val PAYMENT_REMINDER_DAYS = 2 // Days before due date for soft lock
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val loanManager = LoanManager(context)
    private val remoteLockManager = RemoteLockManager(context)
    private val overlayController = OverlayController(context)
    private val auditLog = IdentifierAuditLog(context)

    /**
     * Enforce lock based on payment status
     * Called by LockEnforcementService periodically
     * 
     * SOFT LOCK: Payment due in 2 days
     * HARD LOCK: Payment overdue
     */
    fun enforceLockForPaymentUser(deviceId: String): Boolean {
        return try {
            Log.d(TAG, "Enforcing lock for payment user: $deviceId")

            val loan = loanManager.getLoanByDeviceId(deviceId)
            val payment = loanManager.getPaymentByDeviceId(deviceId)

            if (loan == null || payment == null) {
                Log.w(TAG, "No loan or payment found for device: $deviceId")
                return false
            }

            // Check current lock status
            val existingLock = getPaymentLock(deviceId)

            when (loan.loanStatus) {
                "ACTIVE" -> {
                    val daysUntilDue = loanManager.getDaysUntilDue(loan.loanId)
                    
                    // Apply soft lock if payment due in 2 days
                    if (daysUntilDue <= PAYMENT_REMINDER_DAYS && daysUntilDue > 0) {
                        if (existingLock?.lockType != LockType.SOFT) {
                            applySoftLockForPaymentReminder(deviceId, loan, payment, daysUntilDue)
                        }
                    } else if (daysUntilDue <= 0) {
                        // Payment is overdue, apply hard lock
                        applyHardLockForPaymentOverdue(deviceId, loan, payment)
                    } else {
                        // Remove any existing lock
                        removePaymentLock(deviceId)
                    }
                }

                "OVERDUE" -> {
                    // Apply hard lock for overdue payment
                    if (existingLock?.lockType != LockType.HARD) {
                        applyHardLockForPaymentOverdue(deviceId, loan, payment)
                    }
                }

                "PAID" -> {
                    // Remove lock for paid loans
                    removePaymentLock(deviceId)
                }
                
                else -> {
                    // Unknown status, do nothing
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing lock for payment user", e)
            auditLog.logIncident(
                type = "PAYMENT_LOCK_ENFORCEMENT_ERROR",
                severity = "HIGH",
                details = "Device: $deviceId, Error: ${e.message}"
            )
            false
        }
    }

    /**
     * Apply soft lock for payment reminder
     * Shows warning overlay with information about payment due
     * NO PIN - just a warning notification
     * Triggered when payment is due in 2 days
     * If user doesn't take action, device will be hard locked when payment is overdue
     */
    private fun applySoftLockForPaymentReminder(
        deviceId: String,
        loan: LoanRecord,
        payment: PaymentRecord,
        daysUntilDue: Int
    ) {
        try {
            Log.d(TAG, "Applying soft lock for payment reminder: $deviceId")

            val lockId = "SOFT_LOCK_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("⚠ Payment Due Soon\n\n")
                append("Loan Amount: ${String.format("%.0f", loan.loanAmount)} ${loan.currency}\n")
                append("Days Until Due: $daysUntilDue\n")
                append("Due Date: ${formatDate(loan.dueDate)}\n\n")
                append("Your payment is due in $daysUntilDue days.\n")
                append("Please make your payment to avoid device lock.\n\n")
                append("⚠ CAUTION:\n")
                append("If payment is not made by the due date,\n")
                append("your device will be locked and cannot be used.\n\n")
                append("Contact support for payment assistance:\n")
                append("Phone: +255 XXX XXX XXX\n")
                append("Email: support@company.com")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.SOFT,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.PAYMENT_REMINDER,
                message = message,
                expiresAt = loan.dueDate,
                maxAttempts = 0,
                pinRequired = false,
                pinHash = null
            )

            // Save payment lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "SOFT_LOCK_APPLIED",
                "Device: $deviceId, Loan: ${loan.loanId}, Reason: PAYMENT_REMINDER, Days until due: $daysUntilDue, Warning notification only"
            )

            Log.d(TAG, "✓ Soft lock warning applied for device: $deviceId (warning notification)")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying soft lock", e)
        }
    }

    /**
     * Apply hard lock for overdue payment
     * Device is locked, NO PIN - requires customer support contact
     * Triggered when payment is overdue
     */
    private fun applyHardLockForPaymentOverdue(
        deviceId: String,
        loan: LoanRecord,
        payment: PaymentRecord
    ) {
        try {
            Log.d(TAG, "Applying hard lock for overdue payment: $deviceId")

            val daysOverdue = loanManager.getDaysOverdue(loan.loanId)
            val lockId = "HARD_LOCK_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("Payment Overdue - Device Locked\n\n")
                append("Loan Amount: ${String.format("%.0f", loan.loanAmount)} ${loan.currency}\n")
                append("Days Overdue: $daysOverdue\n")
                append("Original Due Date: ${formatDate(loan.dueDate)}\n\n")
                append("Your device has been locked due to overdue payment.\n\n")
                append("To unlock your device:\n")
                append("1. Contact Customer Support\n")
                append("2. Make payment arrangement\n")
                append("3. Support will unlock your device\n\n")
                append("Support Phone: +255 XXX XXX XXX\n")
                append("Support Email: support@company.com")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.HARD,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.PAYMENT_OVERDUE,
                message = message,
                expiresAt = null, // No expiry for hard lock
                maxAttempts = 0,
                pinRequired = false, // NO PIN - requires support contact
                pinHash = null
            )

            // Save payment lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "HARD_LOCK_APPLIED",
                "Device: $deviceId, Loan: ${loan.loanId}, Reason: PAYMENT_OVERDUE, Days overdue: $daysOverdue, Requires support contact"
            )

            Log.d(TAG, "✓ Hard lock applied for device: $deviceId (requires support contact)")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock", e)
        }
    }

    /**
     * Apply soft lock when user tries to access developer options
     * Shows warning overlay - NO PIN
     * Triggered by developer options access attempt
     */
    fun applySoftLockForDeveloperOptions(deviceId: String): Boolean {
        return try {
            Log.w(TAG, "Developer options access attempt detected: $deviceId")

            val lockId = "SOFT_LOCK_DEV_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("⚠ Developer Options Disabled\n\n")
                append("Access to developer options is restricted.\n")
                append("This is a security measure to protect your device.\n\n")
                append("If you continue attempting to access\n")
                append("restricted features, your device will be locked.\n\n")
                append("Contact support if you need assistance:\n")
                append("Phone: +255 XXX XXX XXX\n")
                append("Email: support@company.com")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.SOFT,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.DEVELOPER_OPTIONS_ATTEMPT,
                message = message,
                expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24 hours
                maxAttempts = 0,
                pinRequired = false,  // NO PIN - just a warning
                pinHash = null
            )

            // Save lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "SOFT_LOCK_APPLIED",
                "Device: $deviceId, Reason: DEVELOPER_OPTIONS_ATTEMPT, Warning notification only"
            )

            Log.d(TAG, "✓ Soft lock warning applied for developer options attempt: $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying soft lock for developer options", e)
            false
        }
    }

    /**
     * Apply soft lock when user tries to boot into safe mode
     * Shows warning overlay - NO PIN
     * Triggered by safe mode boot attempt
     */
    fun applySoftLockForSafeMode(deviceId: String): Boolean {
        return try {
            Log.w(TAG, "Safe mode boot attempt detected: $deviceId")

            val lockId = "SOFT_LOCK_SAFE_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("⚠ Safe Mode Disabled\n\n")
                append("Safe mode boot is not allowed on this device.\n")
                append("This is a security measure to protect your device.\n\n")
                append("If you continue attempting to access\n")
                append("restricted features, your device will be locked.\n\n")
                append("Contact support if you need assistance:\n")
                append("Phone: +255 XXX XXX XXX\n")
                append("Email: support@company.com")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.SOFT,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.SAFE_MODE_ATTEMPT,
                message = message,
                expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24 hours
                maxAttempts = 0,
                pinRequired = false,  // NO PIN - just a warning
                pinHash = null
            )

            // Save lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "SOFT_LOCK_APPLIED",
                "Device: $deviceId, Reason: SAFE_MODE_ATTEMPT, Warning notification only"
            )

            Log.d(TAG, "✓ Soft lock warning applied for safe mode attempt: $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying soft lock for safe mode", e)
            false
        }
    }

    /**
     * Apply hard lock for system tampering
     * Triggered by tamper detection
     * NO PIN - requires support contact
     */
    fun applyHardLockForTampering(deviceId: String, tamperDetails: String): Boolean {
        return try {
            Log.e(TAG, "System tampering detected: $deviceId")

            val lockId = "HARD_LOCK_TAMPER_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("System Tampering Detected - Device Locked\n\n")
                append("Unauthorized system modifications detected.\n")
                append("Your device has been locked for security.\n\n")
                append("Details: $tamperDetails\n\n")
                append("To unlock your device:\n")
                append("1. Contact Customer Support\n")
                append("2. Verify your identity\n")
                append("3. Support will unlock your device\n\n")
                append("Support Phone: +255 XXX XXX XXX\n")
                append("Support Email: support@company.com")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.HARD,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.SYSTEM_TAMPER,
                message = message,
                expiresAt = null, // No expiry for hard lock
                maxAttempts = 0,
                pinRequired = false, // NO PIN - requires support contact
                pinHash = null
            )

            // Save lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "HARD_LOCK_APPLIED",
                "Device: $deviceId, Reason: SYSTEM_TAMPER, Details: $tamperDetails, Requires support contact"
            )

            Log.d(TAG, "✓ Hard lock applied for tampering: $deviceId (requires support contact)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock for tampering", e)
            false
        }
    }

    /**
     * Apply hard lock for device mismatch
     * Triggered by device swap/clone detection
     * NO PIN - requires support contact
     */
    fun applyHardLockForDeviceMismatch(deviceId: String, mismatchDetails: String): Boolean {
        return try {
            Log.e(TAG, "Device mismatch detected: $deviceId")

            val lockId = "HARD_LOCK_MISMATCH_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("Device Mismatch Detected - Device Locked\n\n")
                append("This device does not match our records.\n")
                append("Your device has been locked for security.\n\n")
                append("Details: $mismatchDetails\n\n")
                append("To unlock your device:\n")
                append("1. Contact Customer Support\n")
                append("2. Verify your identity\n")
                append("3. Support will unlock your device\n\n")
                append("Support Phone: +255 XXX XXX XXX\n")
                append("Support Email: support@company.com")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.HARD,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.DEVICE_MISMATCH,
                message = message,
                expiresAt = null, // No expiry for hard lock
                maxAttempts = 0,
                pinRequired = false, // NO PIN - requires support contact
                pinHash = null
            )

            // Save lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "HARD_LOCK_APPLIED",
                "Device: $deviceId, Reason: DEVICE_MISMATCH, Details: $mismatchDetails, Requires support contact"
            )

            Log.d(TAG, "✓ Hard lock applied for device mismatch: $deviceId (requires support contact)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock for device mismatch", e)
            false
        }
    }

    /**
     * Apply hard lock for unauthorized app removal
     * Triggered by multiple removal attempts
     * NO PIN - requires support contact
     */
    fun applyHardLockForUnauthorizedRemoval(deviceId: String, attemptCount: Int): Boolean {
        return try {
            Log.e(TAG, "Unauthorized app removal detected: $deviceId (Attempt #$attemptCount)")

            val lockId = "HARD_LOCK_REMOVAL_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("Unauthorized App Removal Detected - Device Locked\n\n")
                append("Multiple unauthorized removal attempts detected.\n")
                append("Your device has been locked for security.\n\n")
                append("Removal Attempts: $attemptCount\n\n")
                append("To unlock your device:\n")
                append("1. Contact Customer Support\n")
                append("2. Verify your identity\n")
                append("3. Support will unlock your device\n\n")
                append("Support Phone: +255 XXX XXX XXX\n")
                append("Support Email: support@company.com")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.HARD,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.COMPLIANCE_VIOLATION,
                message = message,
                expiresAt = null, // No expiry for hard lock
                maxAttempts = 0,
                pinRequired = false, // NO PIN - requires support contact
                pinHash = null
            )

            // Save lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "HARD_LOCK_APPLIED",
                "Device: $deviceId, Reason: COMPLIANCE_VIOLATION, Attempts: $attemptCount, Requires support contact"
            )

            Log.d(TAG, "✓ Hard lock applied for unauthorized removal: $deviceId (requires support contact)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock for unauthorized removal", e)
            false
        }
    }

    /**
     * Apply hard lock for compliance violation
     * Generic compliance violation handler for various violation types
     * NO PIN - requires support contact
     */
    fun applyHardLockForComplianceViolation(
        deviceId: String,
        violationDetails: String,
        attemptCount: Int = 0
    ): Boolean {
        return try {
            Log.e(TAG, "Compliance violation detected: $deviceId - $violationDetails")

            val lockId = "HARD_LOCK_COMPLIANCE_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("Compliance Violation Detected - Device Locked\n\n")
                append("Your device has been locked due to a compliance violation.\n")
                append("Details: $violationDetails\n\n")
                if (attemptCount > 0) {
                    append("Violation Count: $attemptCount\n\n")
                }
                append("To unlock your device:\n")
                append("1. Contact Customer Support\n")
                append("2. Verify your identity\n")
                append("3. Support will unlock your device\n\n")
                append("Support Phone: +255 XXX XXX XXX\n")
                append("Support Email: support@company.com")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.HARD,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.COMPLIANCE_VIOLATION,
                message = message,
                expiresAt = null, // No expiry for hard lock
                maxAttempts = 0,
                pinRequired = false, // NO PIN - requires support contact
                pinHash = null,
                metadata = mapOf(
                    "violation_type" to "compliance_violation",
                    "violation_details" to violationDetails,
                    "attempt_count" to attemptCount.toString()
                )
            )

            // Save lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "HARD_LOCK_APPLIED",
                "Device: $deviceId, Reason: COMPLIANCE_VIOLATION, Details: $violationDetails, Requires support contact"
            )

            Log.d(TAG, "✓ Hard lock applied for compliance violation: $deviceId (requires support contact)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock for compliance violation", e)
            false
        }
    }

    /**
     * Unlock device from backend (admin approval)
     */
    fun unlockFromBackend(deviceId: String, reason: String): Boolean {
        return try {
            Log.d(TAG, "Unlocking device from backend: $deviceId")

            val lock = getPaymentLock(deviceId) ?: return false

            // Remove lock
            removePaymentLock(deviceId)
            remoteLockManager.removeLock(lock.lockId)

            auditLog.logAction(
                "UNLOCK_FROM_BACKEND",
                "Device: $deviceId, Lock: ${lock.lockId}, Reason: $reason"
            )

            Log.d(TAG, "✓ Device unlocked from backend: $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking from backend", e)
            false
        }
    }

    /**
     * Get payment lock for device
     */
    fun getPaymentLock(deviceId: String): DeviceLock? {
        return try {
            val locksJson = prefs.getString(KEY_PAYMENT_LOCKS, "{}")
            val locksMap = gson.fromJson(locksJson, Map::class.java) as? Map<String, Any>
            val lockData = locksMap?.get(deviceId) as? Map<String, Any>

            if (lockData != null) {
                gson.fromJson(gson.toJson(lockData), DeviceLock::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment lock", e)
            null
        }
    }

    /**
     * Get all payment locks
     */
    fun getAllPaymentLocks(): List<DeviceLock> {
        return try {
            val locksJson = prefs.getString(KEY_PAYMENT_LOCKS, "{}")
            val locksMap = gson.fromJson(locksJson, Map::class.java) as? Map<String, Any>

            val locks = mutableListOf<DeviceLock>()
            for ((_, lockData) in locksMap ?: emptyMap()) {
                val lock = gson.fromJson(gson.toJson(lockData), DeviceLock::class.java)
                locks.add(lock)
            }
            locks
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all payment locks", e)
            emptyList()
        }
    }

    /**
     * Save payment lock
     */
    private fun savePaymentLock(lock: DeviceLock) {
        try {
            val locksJson = prefs.getString(KEY_PAYMENT_LOCKS, "{}")
            val locksMap = gson.fromJson(locksJson, Map::class.java) as? MutableMap<String, Any>
                ?: mutableMapOf()

            locksMap[lock.deviceId] = gson.fromJson(gson.toJson(lock), Map::class.java)
            prefs.edit().putString(KEY_PAYMENT_LOCKS, gson.toJson(locksMap)).apply()

            Log.d(TAG, "Payment lock saved for device: ${lock.deviceId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving payment lock", e)
        }
    }

    /**
     * Remove payment lock
     */
    private fun removePaymentLock(deviceId: String) {
        try {
            val locksJson = prefs.getString(KEY_PAYMENT_LOCKS, "{}")
            val locksMap = gson.fromJson(locksJson, Map::class.java) as? MutableMap<String, Any>
                ?: mutableMapOf()

            locksMap.remove(deviceId)
            prefs.edit().putString(KEY_PAYMENT_LOCKS, gson.toJson(locksMap)).apply()

            Log.d(TAG, "Payment lock removed for device: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing payment lock", e)
        }
    }

    /**
     * Format date for display
     */
    private fun formatDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return String.format(
            "%02d/%02d/%04d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )
    }

    /**
     * Get lock status summary for device
     */
    fun getLockStatusSummary(deviceId: String): String {
        return try {
            val lock = getPaymentLock(deviceId)
            val loan = loanManager.getLoanByDeviceId(deviceId)

            if (lock == null || loan == null) {
                "No active lock"
            } else {
                buildString {
                    append("Lock Type: ${lock.lockType}\n")
                    append("Status: ${lock.lockStatus}\n")
                    append("Reason: ${lock.lockReason}\n")
                    append("Loan Status: ${loan.loanStatus}\n")
                    append("Amount: ${String.format("%.0f", loan.loanAmount)} ${loan.currency}")
                }
            }
        } catch (e: Exception) {
            "Error retrieving lock status"
        }
    }

    /**
     * Get mocked payment users for testing
     */
    fun getMockedPaymentUsers(): List<PaymentUserData> {
        return try {
            val loans = loanManager.getAllLoans()
            val payments = loanManager.getAllPayments()

            loans.mapNotNull { loan ->
                val payment = payments.find { it.loanId == loan.loanId }
                if (payment != null) {
                    PaymentUserData(
                        deviceId = loan.deviceId,
                        userRef = loan.userRef,
                        loanId = loan.loanId,
                        paymentId = payment.paymentId,
                        loanAmount = loan.loanAmount,
                        currency = loan.currency,
                        loanStatus = loan.loanStatus,
                        paymentStatus = payment.paymentStatus,
                        daysOverdue = loan.daysOverdue,
                        daysUntilDue = loanManager.getDaysUntilDue(loan.loanId)
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting mocked payment users", e)
            emptyList()
        }
    }
}

/**
 * Data class for payment user information
 */
data class PaymentUserData(
    val deviceId: String,
    val userRef: String,
    val loanId: String,
    val paymentId: String,
    val loanAmount: Double,
    val currency: String,
    val loanStatus: String,
    val paymentStatus: String,
    val daysOverdue: Int,
    val daysUntilDue: Int
)
