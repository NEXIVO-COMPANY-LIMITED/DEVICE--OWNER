package com.example.deviceowner.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.*
import com.example.deviceowner.models.*
import com.example.deviceowner.overlay.OverlayController
import kotlinx.coroutines.*

/**
 * Service for enforcing locks based on payment status
 * Feature 4.4: Remote Lock/Unlock - Payment Enforcement
 */
class LockEnforcementService : Service() {

    companion object {
        private const val TAG = "LockEnforcementService"
        private const val CHECK_INTERVAL_MS = 60000L // 1 minute
    }

    private lateinit var loanManager: LoanManager
    private lateinit var paymentManager: PaymentManager
    private lateinit var remoteLockManager: RemoteLockManager
    private lateinit var auditLog: IdentifierAuditLog
    private lateinit var overlayController: OverlayController
    private lateinit var deviceIdentifier: DeviceIdentifier

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var enforcementJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LockEnforcementService created")

        loanManager = LoanManager(this)
        paymentManager = PaymentManager(this)
        remoteLockManager = RemoteLockManager(this)
        auditLog = IdentifierAuditLog(this)
        overlayController = OverlayController(this)
        deviceIdentifier = DeviceIdentifier(this)

        startEnforcementChecks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LockEnforcementService started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LockEnforcementService destroyed")
        enforcementJob?.cancel()
        serviceScope.cancel()
    }

    /**
     * Start periodic enforcement checks
     */
    private fun startEnforcementChecks() {
        enforcementJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkPaymentStatus()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in enforcement check", e)
                }
            }
        }
    }

    /**
     * Check payment status and enforce locks
     */
    private suspend fun checkPaymentStatus() {
        try {
            Log.d(TAG, "Checking payment status...")

            val payments = paymentManager.getAllPayments()
            for (payment in payments) {
                when (payment.paymentStatus) {
                    "OVERDUE" -> enforceOverduePayment(payment)
                    "DEFAULTED" -> enforceDefaultedPayment(payment)
                    else -> {
                        // Check if lock exists and should be removed
                        val activeLocks = remoteLockManager.getActiveLocks()
                        activeLocks.values.forEach { lock ->
                            if (lock.deviceId == payment.deviceId && 
                                (lock.lockType == LockType.SOFT || lock.lockType == LockType.HARD)) {
                                remoteLockManager.removeLock(lock.lockId)
                                Log.d(TAG, "Removed lock for paid payment: ${payment.paymentId}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking payment status", e)
        }
    }

    /**
     * Enforce lock for overdue payment
     */
    private suspend fun enforceOverduePayment(payment: PaymentRecord) {
        try {
            val daysOverdue = paymentManager.getDaysOverdue(payment.paymentId)
            val lockType = if (daysOverdue >= 14) LockType.HARD else LockType.SOFT

            Log.w(TAG, "Enforcing ${lockType} lock for overdue payment: ${payment.paymentId} (${daysOverdue} days)")

            // Check if lock already exists
            val existingLocks = remoteLockManager.getActiveLocks()
            val lockExists = existingLocks.values.any { 
                it.deviceId == payment.deviceId && it.lockType == lockType
            }

            if (!lockExists) {
                val lock = DeviceLock(
                    lockId = "lock_${payment.paymentId}_${System.currentTimeMillis()}",
                    deviceId = payment.deviceId,
                    lockType = lockType,
                    lockStatus = LockStatus.ACTIVE,
                    lockReason = LockReason.LOAN_OVERDUE,
                    message = paymentManager.getLockMessage(payment.paymentId),
                    pinRequired = lockType == LockType.SOFT,
                    backendUnlockOnly = false
                )

                remoteLockManager.applyLock(lock)
                auditLog.logAction(
                    "OVERDUE_LOCK_ENFORCED",
                    "Lock enforced for overdue payment: ${payment.paymentId}"
                )

                // Show payment reminder overlay
                overlayController.showPaymentReminder(
                    amount = "${payment.amount} ${payment.currency}",
                    dueDate = formatDate(payment.dueDate),
                    loanId = payment.loanId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing overdue payment lock", e)
        }
    }

    /**
     * Enforce lock for defaulted payment
     */
    private suspend fun enforceDefaultedPayment(payment: PaymentRecord) {
        try {
            Log.e(TAG, "Enforcing permanent lock for defaulted payment: ${payment.paymentId}")

            // Check if permanent lock already exists
            val existingLocks = remoteLockManager.getActiveLocks()
            val lockExists = existingLocks.values.any { 
                it.deviceId == payment.deviceId && it.lockType == LockType.PERMANENT
            }

            if (!lockExists) {
                val lock = DeviceLock(
                    lockId = "lock_${payment.paymentId}_${System.currentTimeMillis()}",
                    deviceId = payment.deviceId,
                    lockType = LockType.PERMANENT,
                    lockStatus = LockStatus.ACTIVE,
                    lockReason = LockReason.PAYMENT_DEFAULT,
                    message = paymentManager.getLockMessage(payment.paymentId),
                    pinRequired = false,
                    backendUnlockOnly = true
                )

                remoteLockManager.applyLock(lock)
                auditLog.logIncident(
                    type = "DEFAULTED_LOCK_ENFORCED",
                    severity = "CRITICAL",
                    details = "Permanent lock enforced for defaulted payment: ${payment.paymentId}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing defaulted payment lock", e)
        }
    }

    /**
     * Format date for display
     */
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
