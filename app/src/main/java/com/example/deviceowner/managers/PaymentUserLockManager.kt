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
 * Handles:
 * - Soft locks for payment reminders (ACTIVE loans)
 * - Hard locks for overdue payments (OVERDUE loans)
 * - Permanent locks for defaulted loans (DEFAULTED loans)
 * - PIN-based unlock for soft/hard locks
 * - Backend unlock for permanent locks
 */
class PaymentUserLockManager(private val context: Context) {

    companion object {
        private const val TAG = "PaymentUserLockManager"
        private const val PREFS_NAME = "payment_user_lock_prefs"
        private const val KEY_PAYMENT_LOCKS = "payment_locks"
        private const val KEY_LOCK_HISTORY = "lock_history"
        private const val SOFT_LOCK_PIN = "1234" // Default PIN for soft locks
        private const val HARD_LOCK_PIN = "5678" // Default PIN for hard locks
        private const val OVERDUE_THRESHOLD_DAYS = 5 // Days before hard lock
        private const val DEFAULT_THRESHOLD_DAYS = 30 // Days before permanent lock
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
                    
                    // Show soft lock if payment due within 7 days
                    if (daysUntilDue <= 7 && daysUntilDue > 0) {
                        if (existingLock?.lockType != LockType.SOFT) {
                            applySoftLockForPayment(deviceId, loan, payment, daysUntilDue)
                        }
                    } else if (daysUntilDue <= 0) {
                        // Payment is due, apply hard lock
                        applyHardLockForPayment(deviceId, loan, payment)
                    } else {
                        // Remove any existing lock
                        removePaymentLock(deviceId)
                    }
                }

                "OVERDUE" -> {
                    val daysOverdue = loanManager.getDaysOverdue(loan.loanId)
                    
                    if (daysOverdue < DEFAULT_THRESHOLD_DAYS) {
                        // Apply hard lock for overdue payment
                        if (existingLock?.lockType != LockType.HARD) {
                            applyHardLockForPayment(deviceId, loan, payment)
                        }
                    } else {
                        // Apply permanent lock for long-term default
                        if (existingLock?.lockType != LockType.PERMANENT) {
                            applyPermanentLockForPayment(deviceId, loan, payment)
                        }
                    }
                }

                "DEFAULTED" -> {
                    // Apply permanent lock for defaulted loan
                    if (existingLock?.lockType != LockType.PERMANENT) {
                        applyPermanentLockForPayment(deviceId, loan, payment)
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
     * Device remains usable but shows warning overlay
     */
    private fun applySoftLockForPayment(
        deviceId: String,
        loan: LoanRecord,
        payment: PaymentRecord,
        daysUntilDue: Int
    ) {
        try {
            Log.d(TAG, "Applying soft lock for payment user: $deviceId")

            val lockId = "SOFT_LOCK_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("Payment Reminder\n\n")
                append("Loan Amount: ${String.format("%.0f", loan.loanAmount)} ${loan.currency}\n")
                append("Days Until Due: $daysUntilDue\n")
                append("Due Date: ${formatDate(loan.dueDate)}\n\n")
                append("Please make your payment to avoid device lock.")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.SOFT,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.LOAN_OVERDUE,
                message = message,
                expiresAt = loan.dueDate,
                pinRequired = true,
                pinHash = hashPin(SOFT_LOCK_PIN),
                maxAttempts = 5
            )

            // Save payment lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "SOFT_LOCK_APPLIED",
                "Device: $deviceId, Loan: ${loan.loanId}, Days until due: $daysUntilDue"
            )

            Log.d(TAG, "✓ Soft lock applied for device: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying soft lock", e)
        }
    }

    /**
     * Apply hard lock for overdue payment
     * Device is locked, requires PIN to unlock
     */
    private fun applyHardLockForPayment(
        deviceId: String,
        loan: LoanRecord,
        payment: PaymentRecord
    ) {
        try {
            Log.d(TAG, "Applying hard lock for overdue payment: $deviceId")

            val daysOverdue = loanManager.getDaysOverdue(loan.loanId)
            val lockId = "HARD_LOCK_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("Payment Overdue\n\n")
                append("Loan Amount: ${String.format("%.0f", loan.loanAmount)} ${loan.currency}\n")
                append("Days Overdue: $daysOverdue\n")
                append("Original Due Date: ${formatDate(loan.dueDate)}\n\n")
                append("Your device has been locked due to overdue payment.\n")
                append("Contact support or make payment to unlock.")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.HARD,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.LOAN_OVERDUE,
                message = message,
                expiresAt = null, // No expiry for hard lock
                pinRequired = true,
                pinHash = hashPin(HARD_LOCK_PIN),
                maxAttempts = 3
            )

            // Save payment lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "HARD_LOCK_APPLIED",
                "Device: $deviceId, Loan: ${loan.loanId}, Days overdue: $daysOverdue"
            )

            Log.d(TAG, "✓ Hard lock applied for device: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock", e)
        }
    }

    /**
     * Apply permanent lock for defaulted loan
     * Device is locked, requires backend unlock only
     */
    private fun applyPermanentLockForPayment(
        deviceId: String,
        loan: LoanRecord,
        payment: PaymentRecord
    ) {
        try {
            Log.d(TAG, "Applying permanent lock for defaulted loan: $deviceId")

            val daysOverdue = loanManager.getDaysOverdue(loan.loanId)
            val lockId = "PERM_LOCK_${deviceId}_${System.currentTimeMillis()}"
            val message = buildString {
                append("Device Repossession Notice\n\n")
                append("Loan Amount: ${String.format("%.0f", loan.loanAmount)} ${loan.currency}\n")
                append("Days Overdue: $daysOverdue\n")
                append("Status: DEFAULTED\n\n")
                append("Your device has been locked for loan default.\n")
                append("Contact support for unlock authorization.")
            }

            val lock = DeviceLock(
                lockId = lockId,
                deviceId = deviceId,
                lockType = LockType.PERMANENT,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.PAYMENT_DEFAULT,
                message = message,
                expiresAt = null, // No expiry for permanent lock
                pinRequired = false, // Backend unlock only
                maxAttempts = 0
            )

            // Save payment lock
            savePaymentLock(lock)

            // Apply lock via RemoteLockManager
            remoteLockManager.applyLock(lock)

            // Log action
            auditLog.logAction(
                "PERMANENT_LOCK_APPLIED",
                "Device: $deviceId, Loan: ${loan.loanId}, Days overdue: $daysOverdue"
            )

            Log.d(TAG, "✓ Permanent lock applied for device: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying permanent lock", e)
        }
    }

    /**
     * Unlock device with PIN verification
     */
    fun unlockWithPin(deviceId: String, pin: String): Boolean {
        return try {
            Log.d(TAG, "Attempting unlock with PIN for device: $deviceId")

            val lock = getPaymentLock(deviceId) ?: return false

            // Verify PIN
            if (!verifyPin(pin, lock.pinHash ?: "")) {
                Log.w(TAG, "Invalid PIN for device: $deviceId")
                auditLog.logIncident(
                    type = "INVALID_PIN_ATTEMPT",
                    severity = "MEDIUM",
                    details = "Device: $deviceId"
                )
                return false
            }

            // Remove lock
            removePaymentLock(deviceId)
            remoteLockManager.removeLock(lock.lockId)

            auditLog.logAction(
                "UNLOCK_WITH_PIN",
                "Device: $deviceId, Lock: ${lock.lockId}"
            )

            Log.d(TAG, "✓ Device unlocked with PIN: $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking with PIN", e)
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
     * Verify PIN against hash
     */
    private fun verifyPin(pin: String, pinHash: String): Boolean {
        val hash = hashPin(pin)
        return hash == pinHash
    }

    /**
     * Hash PIN using SHA-256
     */
    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
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
