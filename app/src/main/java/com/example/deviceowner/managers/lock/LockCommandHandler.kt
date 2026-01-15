package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import com.example.deviceowner.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles lock/unlock commands from backend
 * Feature 4.4: Remote Lock/Unlock
 */
class LockCommandHandler(private val context: Context) {

    companion object {
        private const val TAG = "LockCommandHandler"
    }

    private val remoteLockManager = RemoteLockManager(context)
    private val loanManager = LoanManager(context)
    private val auditLog = IdentifierAuditLog(context)
    private val deviceIdentifier = DeviceIdentifier(context)

    /**
     * Handle lock command from backend
     */
    fun handleLockCommand(command: LockCommand): CommandResult {
        return try {
            Log.w(TAG, "Handling lock command: ${command.commandId} - ${command.lockType}")

            // Create device lock from command
            val lock = DeviceLock(
                lockId = command.commandId,
                deviceId = command.deviceId,
                lockType = command.lockType,
                lockStatus = LockStatus.ACTIVE,
                lockReason = command.reason,
                message = command.message,
                expiresAt = command.expiresAt,
                backendUnlockOnly = command.lockType == LockType.HARD
            )

            // Apply lock
            val success = remoteLockManager.applyLock(lock)

            if (success) {
                auditLog.logAction(
                    "LOCK_COMMAND_EXECUTED",
                    "Lock command executed: ${command.commandId}"
                )
                CommandResult(
                    success = true,
                    commandId = command.commandId,
                    status = "EXECUTED",
                    message = "Lock applied successfully"
                )
            } else {
                CommandResult(
                    success = false,
                    commandId = command.commandId,
                    status = "FAILED",
                    message = "Failed to apply lock"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling lock command", e)
            auditLog.logIncident(
                type = "LOCK_COMMAND_ERROR",
                severity = "HIGH",
                details = "Failed to handle lock command: ${e.message}"
            )
            CommandResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Handle unlock command from backend
     */
    fun handleUnlockCommand(command: UnlockCommand): CommandResult {
        return try {
            Log.d(TAG, "Handling unlock command: ${command.commandId}")

            // Remove lock
            val success = remoteLockManager.removeLock(command.lockId)

            if (success) {
                auditLog.logAction(
                    "UNLOCK_COMMAND_EXECUTED",
                    "Unlock command executed: ${command.commandId}"
                )
                CommandResult(
                    success = true,
                    commandId = command.commandId,
                    status = "EXECUTED",
                    message = "Device unlocked successfully"
                )
            } else {
                CommandResult(
                    success = false,
                    commandId = command.commandId,
                    status = "FAILED",
                    message = "Failed to unlock device"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling unlock command", e)
            CommandResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Handle loan-based lock
     */
    fun handleLoanLock(deviceId: String): CommandResult {
        return try {
            Log.w(TAG, "Handling loan-based lock for device: $deviceId")

            val loan = loanManager.getLoanByDeviceId(deviceId)
                ?: return CommandResult(
                    success = false,
                    commandId = "LOAN_LOCK_$deviceId",
                    status = "FAILED",
                    message = "No loan found for device"
                )

            // Determine lock type based on loan status
            val (lockType, message) = when (loan.loanStatus) {
                "OVERDUE" -> {
                    val daysOverdue = loanManager.getDaysOverdue(loan.loanId)
                    if (daysOverdue > 14) {
                        LockType.HARD to "Your loan is ${daysOverdue} days overdue. Device locked."
                    } else {
                        LockType.SOFT to "Your loan is ${daysOverdue} days overdue. Please make payment."
                    }
                }
                "DEFAULTED" -> {
                    LockType.HARD to "Loan defaulted. Device locked for repossession. Contact support."
                }
                else -> return CommandResult(
                    success = false,
                    commandId = "LOAN_LOCK_$deviceId",
                    status = "SKIPPED",
                    message = "Loan status does not require lock"
                )
            }

            // Create and apply lock
            val lock = DeviceLock(
                lockId = "LOAN_LOCK_$deviceId",
                deviceId = deviceId,
                lockType = lockType,
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.PAYMENT_OVERDUE,
                message = message,
                backendUnlockOnly = lockType == LockType.HARD,
                metadata = mapOf(
                    "loanId" to loan.loanId,
                    "loanAmount" to loan.loanAmount.toString(),
                    "daysOverdue" to loanManager.getDaysOverdue(loan.loanId).toString()
                )
            )

            val success = remoteLockManager.applyLock(lock)

            if (success) {
                CommandResult(
                    success = true,
                    commandId = "LOAN_LOCK_$deviceId",
                    status = "EXECUTED",
                    message = "Loan-based lock applied"
                )
            } else {
                CommandResult(
                    success = false,
                    commandId = "LOAN_LOCK_$deviceId",
                    status = "FAILED",
                    message = "Failed to apply loan-based lock"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling loan lock", e)
            CommandResult(
                success = false,
                commandId = "LOAN_LOCK_$deviceId",
                status = "FAILED",
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Handle payment received - unlock device
     */
    fun handlePaymentReceived(loanId: String): CommandResult {
        return try {
            Log.d(TAG, "Handling payment received for loan: $loanId")

            val loan = loanManager.getLoan(loanId)
                ?: return CommandResult(
                    success = false,
                    commandId = "PAYMENT_$loanId",
                    status = "FAILED",
                    message = "Loan not found"
                )

            // Update loan status
            loanManager.updateLoanStatus(loanId, "PAID")

            // Remove lock
            val lockId = "LOAN_LOCK_${loan.deviceId}"
            remoteLockManager.removeLock(lockId)

            auditLog.logAction(
                "PAYMENT_RECEIVED",
                "Payment received for loan $loanId - device unlocked"
            )

            CommandResult(
                success = true,
                commandId = "PAYMENT_$loanId",
                status = "EXECUTED",
                message = "Payment processed - device unlocked"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling payment", e)
            CommandResult(
                success = false,
                commandId = "PAYMENT_$loanId",
                status = "FAILED",
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Data class for command result
     */
    data class CommandResult(
        val success: Boolean,
        val commandId: String,
        val status: String,
        val message: String
    )
}
