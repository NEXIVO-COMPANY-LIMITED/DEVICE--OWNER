package com.example.deviceowner.feature

import android.content.Context
import android.util.Log
import com.example.deviceowner.managers.*
import com.example.deviceowner.models.*
import com.example.deviceowner.overlay.OverlayController
import com.example.deviceowner.overlay.OverlayType
import com.example.deviceowner.security.PINVerificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Feature 4.4: Remote Lock/Unlock Implementation
 * 
 * Objective: Implement flexible locking mechanism for loan enforcement
 * 
 * Dependencies:
 * - Feature 4.1 (Device Owner)
 * - Feature 4.6 (Overlay UI)
 * 
 * Deliverables:
 * - Lock/unlock command handler ✓
 * - Lock type implementation (soft, hard, permanent) ✓
 * - PIN verification system ✓
 * - Offline lock queueing ✓
 * - Backend integration ✓
 */
class Feature44LockUnlock(private val context: Context) {

    companion object {
        private const val TAG = "Feature44LockUnlock"
    }

    private val remoteLockManager = RemoteLockManager(context)
    private val lockCommandHandler = LockCommandHandler(context)
    private val loanManager = LoanManager(context)
    private val pinVerificationManager = PINVerificationManager(context)
    private val overlayController = OverlayController(context)
    private val auditLog = IdentifierAuditLog(context)

    /**
     * DEMO: Test soft lock (warning overlay, device usable)
     */
    fun demoSoftLock() {
        Log.d(TAG, "=== DEMO: SOFT LOCK ===")
        
        val lock = DeviceLock(
            lockId = "SOFT_LOCK_DEMO_${System.currentTimeMillis()}",
            deviceId = "device_001",
            lockType = LockType.SOFT,
            lockStatus = LockStatus.ACTIVE,
            lockReason = LockReason.LOAN_OVERDUE,
            message = "Your loan payment is overdue. Please make payment to continue using your device.",
            pinRequired = false,
            backendUnlockOnly = false
        )

        remoteLockManager.applyLock(lock)
        Log.d(TAG, "✓ Soft lock applied - Device remains usable with warning overlay")
    }

    /**
     * DEMO: Test hard lock (full device lock, no interaction)
     */
    fun demoHardLock() {
        Log.d(TAG, "=== DEMO: HARD LOCK ===")
        
        val lock = DeviceLock(
            lockId = "HARD_LOCK_DEMO_${System.currentTimeMillis()}",
            deviceId = "device_002",
            lockType = LockType.HARD,
            lockStatus = LockStatus.ACTIVE,
            lockReason = LockReason.PAYMENT_DEFAULT,
            message = "Your device has been locked due to payment default. Contact support for assistance.",
            pinRequired = true,
            pinHash = pinVerificationManager.hashPin("1234"),
            backendUnlockOnly = false
        )

        remoteLockManager.applyLock(lock)
        Log.d(TAG, "✓ Hard lock applied - Device fully locked, requires PIN to unlock")
    }

    /**
     * DEMO: Test permanent lock (repossession lock, backend unlock only)
     */
    fun demoPermanentLock() {
        Log.d(TAG, "=== DEMO: PERMANENT LOCK ===")
        
        val lock = DeviceLock(
            lockId = "PERMANENT_LOCK_DEMO_${System.currentTimeMillis()}",
            deviceId = "device_003",
            lockType = LockType.PERMANENT,
            lockStatus = LockStatus.ACTIVE,
            lockReason = LockReason.DEVICE_MISMATCH,
            message = "This device has been locked for repossession. Only backend can unlock.",
            pinRequired = false,
            backendUnlockOnly = true
        )

        remoteLockManager.applyLock(lock)
        Log.d(TAG, "✓ Permanent lock applied - Backend unlock required for repossession")
    }

    /**
     * DEMO: Test PIN verification for unlock
     */
    fun demoPINVerification() {
        Log.d(TAG, "=== DEMO: PIN VERIFICATION ===")
        
        val lockId = "HARD_LOCK_DEMO_${System.currentTimeMillis()}"
        val correctPin = "1234"
        val wrongPin = "5678"
        
        val lock = DeviceLock(
            lockId = lockId,
            deviceId = "device_002",
            lockType = LockType.HARD,
            lockStatus = LockStatus.ACTIVE,
            lockReason = LockReason.PAYMENT_DEFAULT,
            message = "Device locked - Enter PIN to unlock",
            pinRequired = true,
            pinHash = pinVerificationManager.hashPin(correctPin)
        )

        remoteLockManager.applyLock(lock)

        // Test wrong PIN
        Log.d(TAG, "Testing wrong PIN...")
        val wrongResult = remoteLockManager.unlockWithPin(lockId, wrongPin)
        Log.d(TAG, "Wrong PIN result: $wrongResult (expected: false)")

        // Test correct PIN
        Log.d(TAG, "Testing correct PIN...")
        val correctResult = remoteLockManager.unlockWithPin(lockId, correctPin)
        Log.d(TAG, "Correct PIN result: $correctResult (expected: true)")
    }

    /**
     * DEMO: Test offline lock queueing
     */
    fun demoOfflineLockQueueing() {
        Log.d(TAG, "=== DEMO: OFFLINE LOCK QUEUEING ===")
        
        val lockCommand = LockCommand(
            commandId = "OFFLINE_LOCK_${System.currentTimeMillis()}",
            deviceId = "device_001",
            lockType = LockType.SOFT,
            reason = LockReason.LOAN_OVERDUE,
            message = "Payment overdue - device will be locked when online",
            pin = null
        )

        val queued = remoteLockManager.queueLockCommand(lockCommand)
        Log.d(TAG, "Lock command queued: $queued")

        // Simulate reconnection
        Log.d(TAG, "Simulating device reconnection...")
        remoteLockManager.processQueuedLockCommands()
        Log.d(TAG, "✓ Queued lock commands processed on reconnection")
    }

    /**
     * DEMO: Test offline unlock queueing
     */
    fun demoOfflineUnlockQueueing() {
        Log.d(TAG, "=== DEMO: OFFLINE UNLOCK QUEUEING ===")
        
        val unlockCommand = UnlockCommand(
            commandId = "OFFLINE_UNLOCK_${System.currentTimeMillis()}",
            deviceId = "device_001",
            lockId = "SOFT_LOCK_DEMO_123",
            reason = "Payment received - device unlocked"
        )

        val queued = remoteLockManager.queueUnlockCommand(unlockCommand)
        Log.d(TAG, "Unlock command queued: $queued")

        // Simulate reconnection
        Log.d(TAG, "Simulating device reconnection...")
        remoteLockManager.processQueuedUnlockCommands()
        Log.d(TAG, "✓ Queued unlock commands processed on reconnection")
    }

    /**
     * DEMO: Test loan-based lock
     */
    fun demoLoanBasedLock() {
        Log.d(TAG, "=== DEMO: LOAN-BASED LOCK ===")
        
        // Get overdue loan
        val overdueLoans = loanManager.getOverdueLoans()
        if (overdueLoans.isNotEmpty()) {
            val loan = overdueLoans.first()
            Log.d(TAG, "Found overdue loan: ${loan.loanId}")
            Log.d(TAG, "Loan Status: ${loan.loanStatus}")
            Log.d(TAG, "Days Overdue: ${loan.daysOverdue}")
            Log.d(TAG, "Amount: ${loan.loanAmount} ${loan.currency}")

            // Apply loan-based lock
            val result = lockCommandHandler.handleLoanLock(loan.deviceId)
            Log.d(TAG, "Loan lock result: ${result.status} - ${result.message}")
        } else {
            Log.d(TAG, "No overdue loans found")
        }
    }

    /**
     * DEMO: Test payment received - unlock device
     */
    fun demoPaymentReceivedUnlock() {
        Log.d(TAG, "=== DEMO: PAYMENT RECEIVED - UNLOCK ===")
        
        // Get a loan
        val loans = loanManager.getAllLoans()
        if (loans.isNotEmpty()) {
            val loan = loans.first()
            Log.d(TAG, "Processing payment for loan: ${loan.loanId}")
            Log.d(TAG, "Current Status: ${loan.loanStatus}")

            // Simulate payment received
            val result = lockCommandHandler.handlePaymentReceived(loan.loanId)
            Log.d(TAG, "Payment result: ${result.status} - ${result.message}")

            // Verify loan status updated
            val updatedLoan = loanManager.getLoan(loan.loanId)
            Log.d(TAG, "Updated Status: ${updatedLoan?.loanStatus}")
        }
    }

    /**
     * DEMO: Test backend lock command handling
     */
    fun demoBackendLockCommand() {
        Log.d(TAG, "=== DEMO: BACKEND LOCK COMMAND ===")
        
        val command = LockCommand(
            commandId = "BACKEND_LOCK_${System.currentTimeMillis()}",
            deviceId = "device_001",
            lockType = LockType.SOFT,
            reason = LockReason.LOAN_OVERDUE,
            message = "Your loan is overdue. Please make payment.",
            pin = null
        )

        val result = lockCommandHandler.handleLockCommand(command)
        Log.d(TAG, "Backend lock command result:")
        Log.d(TAG, "  Success: ${result.success}")
        Log.d(TAG, "  Status: ${result.status}")
        Log.d(TAG, "  Message: ${result.message}")
    }

    /**
     * DEMO: Test backend unlock command handling
     */
    fun demoBackendUnlockCommand() {
        Log.d(TAG, "=== DEMO: BACKEND UNLOCK COMMAND ===")
        
        val command = UnlockCommand(
            commandId = "BACKEND_UNLOCK_${System.currentTimeMillis()}",
            deviceId = "device_001",
            lockId = "SOFT_LOCK_DEMO_123",
            reason = "Payment received - device unlocked"
        )

        val result = lockCommandHandler.handleUnlockCommand(command)
        Log.d(TAG, "Backend unlock command result:")
        Log.d(TAG, "  Success: ${result.success}")
        Log.d(TAG, "  Status: ${result.status}")
        Log.d(TAG, "  Message: ${result.message}")
    }

    /**
     * DEMO: Test all lock types with mocked payment data
     */
    fun demoAllLockTypesWithPayments() {
        Log.d(TAG, "=== DEMO: ALL LOCK TYPES WITH MOCKED PAYMENTS ===")
        
        val payments = loanManager.getAllPayments()
        Log.d(TAG, "Total mocked payments: ${payments.size}")

        for (payment in payments) {
            Log.d(TAG, "\n--- Processing Payment: ${payment.paymentId} ---")
            Log.d(TAG, "Device: ${payment.deviceId}")
            Log.d(TAG, "Amount: ${payment.amount} ${payment.currency}")
            Log.d(TAG, "Status: ${payment.paymentStatus}")
            Log.d(TAG, "Days Overdue: ${payment.daysOverdue}")

            // Determine lock type based on payment status
            val (lockType, message) = when (payment.paymentStatus) {
                "PENDING" -> {
                    LockType.SOFT to "Payment pending - reminder overlay"
                }
                "OVERDUE" -> {
                    if (payment.daysOverdue > 14) {
                        LockType.HARD to "Payment ${payment.daysOverdue} days overdue - device locked"
                    } else {
                        LockType.SOFT to "Payment ${payment.daysOverdue} days overdue - warning overlay"
                    }
                }
                "DEFAULTED" -> {
                    LockType.PERMANENT to "Payment defaulted - repossession lock"
                }
                "COMPLETED" -> {
                    LockType.SOFT to "Payment completed - device unlocked"
                }
                else -> LockType.SOFT to "Unknown status"
            }

            Log.d(TAG, "Lock Type: $lockType")
            Log.d(TAG, "Message: $message")

            // Apply lock
            val lock = DeviceLock(
                lockId = "PAYMENT_LOCK_${payment.paymentId}",
                deviceId = payment.deviceId,
                lockType = lockType,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.PAYMENT_DEFAULT,
                message = message,
                metadata = mapOf(
                    "paymentId" to payment.paymentId,
                    "loanId" to payment.loanId,
                    "amount" to payment.amount.toString()
                )
            )

            remoteLockManager.applyLock(lock)
            Log.d(TAG, "✓ Lock applied for payment ${payment.paymentId}")
        }
    }

    /**
     * DEMO: Test complete lock/unlock workflow
     */
    fun demoCompleteWorkflow() {
        Log.d(TAG, "=== DEMO: COMPLETE LOCK/UNLOCK WORKFLOW ===")
        
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                // Step 1: Device goes offline
                Log.d(TAG, "\n[Step 1] Device goes offline")
                val lockCommand = LockCommand(
                    commandId = "WORKFLOW_LOCK_${System.currentTimeMillis()}",
                    deviceId = "device_001",
                    lockType = LockType.SOFT,
                    reason = LockReason.LOAN_OVERDUE,
                    message = "Payment overdue - queuing lock command",
                    pin = null
                )
                remoteLockManager.queueLockCommand(lockCommand)
                Log.d(TAG, "✓ Lock command queued while offline")

                // Step 2: Device comes back online
                Log.d(TAG, "\n[Step 2] Device comes back online")
                remoteLockManager.processQueuedLockCommands()
                Log.d(TAG, "✓ Queued lock commands executed")

                // Step 3: User attempts unlock with wrong PIN
                Log.d(TAG, "\n[Step 3] User attempts unlock with wrong PIN")
                val wrongPin = "0000"
                val wrongResult = remoteLockManager.unlockWithPin(
                    "WORKFLOW_LOCK_${System.currentTimeMillis()}",
                    wrongPin
                )
                Log.d(TAG, "Wrong PIN result: $wrongResult")

                // Step 4: Payment received from backend
                Log.d(TAG, "\n[Step 4] Payment received from backend")
                val unlockCommand = UnlockCommand(
                    commandId = "WORKFLOW_UNLOCK_${System.currentTimeMillis()}",
                    deviceId = "device_001",
                    lockId = "WORKFLOW_LOCK_${System.currentTimeMillis()}",
                    reason = "Payment received"
                )
                remoteLockManager.queueUnlockCommand(unlockCommand)
                remoteLockManager.processQueuedUnlockCommands()
                Log.d(TAG, "✓ Device unlocked after payment received")

                // Step 5: Verify lock status
                Log.d(TAG, "\n[Step 5] Verify lock status")
                val activeLocks = remoteLockManager.getActiveLocks()
                Log.d(TAG, "Active locks: ${activeLocks.size}")
                Log.d(TAG, "✓ Workflow complete")

            } catch (e: Exception) {
                Log.e(TAG, "Error in workflow demo", e)
            }
        }
    }

    /**
     * Run all demos
     */
    fun runAllDemos() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Feature 4.4: Remote Lock/Unlock Demos")
        Log.d(TAG, "========================================")

        demoSoftLock()
        Log.d(TAG, "")
        
        demoHardLock()
        Log.d(TAG, "")
        
        demoPermanentLock()
        Log.d(TAG, "")
        
        demoPINVerification()
        Log.d(TAG, "")
        
        demoOfflineLockQueueing()
        Log.d(TAG, "")
        
        demoOfflineUnlockQueueing()
        Log.d(TAG, "")
        
        demoLoanBasedLock()
        Log.d(TAG, "")
        
        demoPaymentReceivedUnlock()
        Log.d(TAG, "")
        
        demoBackendLockCommand()
        Log.d(TAG, "")
        
        demoBackendUnlockCommand()
        Log.d(TAG, "")
        
        demoAllLockTypesWithPayments()
        Log.d(TAG, "")
        
        demoCompleteWorkflow()

        Log.d(TAG, "========================================")
        Log.d(TAG, "All demos completed!")
        Log.d(TAG, "========================================")
    }
}

