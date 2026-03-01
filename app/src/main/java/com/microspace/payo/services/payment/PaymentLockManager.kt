package com.microspace.payo.services.payment

import android.content.Context
import android.util.Log
import com.microspace.payo.ui.screens.lock.LockScreenState
import com.microspace.payo.ui.screens.lock.LockScreenStrategy
import com.microspace.payo.ui.screens.lock.LockScreenType
import com.microspace.payo.utils.storage.PaymentDataManager
import com.microspace.payo.utils.storage.SharedPreferencesManager
import com.microspace.payo.control.HardLockManager
import com.microspace.payo.control.RemoteDeviceControlManager
import com.microspace.payo.services.lock.SoftLockOverlayService
import java.time.ZonedDateTime

/**
 * PaymentLockManager - Manages payment-driven lock/unlock logic
 *
 * FLOW:
 * 1. User makes payment online â†’ next_payment_date changes
 * 2. Heartbeat receives updated next_payment_date + unlock_password
 * 3. PaymentLockManager saves this data locally
 * 4. Automatically generates:
 *    - Reminder SMS (1 day before next_payment_date)
 *    - Hard lock (on or after next_payment_date)
 *    - Overdue status (after next_payment_date)
 * 5. When offline + hard locked:
 *    - Show hard lock screen with password input
 *    - User enters password sent via heartbeat
 *    - System compares with stored password
 *    - If correct â†’ unlock device
 *    - If incorrect â†’ remain locked
 *
 * Works for both online and offline scenarios.
 */
class PaymentLockManager(private val context: Context) {

    companion object {
        private const val TAG = "PaymentLockManager"
    }

    private val paymentDataManager = PaymentDataManager(context)
    private val sharedPreferencesManager = SharedPreferencesManager(context)
    private val hardLockManager = HardLockManager(context)
    private val remoteControl = RemoteDeviceControlManager(context)

    /**
     * Process payment status and apply appropriate lock/unlock
     *
     * Called after:
     * - Heartbeat response received with updated next_payment_date
     * - User makes payment online
     * - App starts (check cached payment data)
     *
     * @param nextPaymentDate ISO-8601 datetime (e.g., "2026-02-07T23:59:00+03:00")
     * @param unlockPassword Password sent by server for offline unlock
     * @param shopName Organization name for display
     */
    fun processPaymentStatus(
        nextPaymentDate: String?,
        unlockPassword: String?,
        shopName: String? = null
    ) {
        Log.d(TAG, "ðŸ”„ Processing payment status...")
        Log.d(TAG, "   Next payment: $nextPaymentDate")
        Log.d(TAG, "   Password: ${if (unlockPassword != null) "***saved" else "none"}")

        if (nextPaymentDate.isNullOrBlank()) {
            Log.d(TAG, "âœ… No next payment date - device unlocked")
            handleUnlocked()
            return
        }

        // Save payment data locally for offline use
        paymentDataManager.saveNextPaymentInfo(
            dateTime = nextPaymentDate,
            unlockPassword = unlockPassword
        )

        // Determine lock state based on payment date
        val lockState = LockScreenStrategy.determineLockScreenStateFromLocal(
            nextPaymentDate = nextPaymentDate,
            unlockPassword = unlockPassword
        )

        if (lockState == null) {
            Log.d(TAG, "âœ… No lock state determined - device unlocked")
            handleUnlocked()
            return
        }

        when (lockState.type) {
            LockScreenType.UNLOCKED -> {
                Log.d(TAG, "âœ… Device unlocked - normal operation")
                handleUnlocked()
            }

            LockScreenType.SOFT_LOCK_REMINDER -> {
                Log.i(TAG, "â° Payment reminder - 1 day before due")
                handleSoftLockReminder(lockState, shopName)
            }

            LockScreenType.HARD_LOCK_PAYMENT -> {
                Log.i(TAG, "ðŸ”’ Payment overdue - applying hard lock")
                handleHardLockPayment(lockState, shopName)
            }

            else -> {
                Log.d(TAG, "â„¹ï¸ Other lock type: ${lockState.type}")
            }
        }
    }

    /**
     * Handle unlocked state - remove any locks
     */
    private fun handleUnlocked() {
        try {
            // Clear lock state via RemoteDeviceControlManager to ensure full system unlock
            remoteControl.unlockDevice()
            hardLockManager.clearLockState()
            Log.d(TAG, "âœ… Device unlocked - lock state cleared")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error handling unlock: ${e.message}", e)
        }
    }

    /**
     * Handle soft lock reminder (1 day before payment due)
     *
     * Shows yellow overlay with payment reminder message
     * User can dismiss after acknowledging
     * Device is NOT locked - normal operation continues
     */
    private fun handleSoftLockReminder(lockState: LockScreenState, shopName: String?) {
        try {
            Log.i(TAG, "ðŸ“¢ Showing soft lock reminder overlay")

            val daysUntil = lockState.daysUntilDue ?: 1
            val hoursUntil = lockState.hoursUntilDue ?: 24
            val minutesUntil = lockState.minutesUntilDue ?: 0

            val formattedDate = LockScreenStrategy.formatDueDate(lockState.nextPaymentDate)
            val countdown = LockScreenStrategy.formatCountdown(daysUntil, hoursUntil, minutesUntil)

            val reminderMessage = """
                Attention: Your upcoming payment installment is due soon.
                To maintain uninterrupted device service, please ensure your payment is completed.

                Due On: $formattedDate
                Time Remaining: $countdown
            """.trimIndent()

            // Show soft lock overlay (yellow background, dismissible)
            SoftLockOverlayService.startOverlay(
                context = context,
                reason = reminderMessage,
                triggerAction = "payment_reminder",
                nextPaymentDate = lockState.nextPaymentDate,
                organizationName = shopName
            )

            Log.d(TAG, "âœ… Soft lock reminder shown")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error showing soft lock reminder: ${e.message}", e)
        }
    }

    /**
     * Handle hard lock for payment overdue (on or after due date)
     *
     * Applies system-level hard lock via DevicePolicyManager
     * Shows lock overlay with password input field
     * Device is LOCKED - user cannot use device
     * User must enter password sent via heartbeat to unlock
     *
     * For offline scenarios:
     * - Password was sent during last online heartbeat
     * - Stored locally in SharedPreferences
     * - User enters password on hard lock screen
     * - System compares with stored password
     * - If correct â†’ unlock device
     */
    private fun handleHardLockPayment(lockState: LockScreenState, shopName: String?) {
        try {
            Log.i(TAG, "ðŸ”’ Applying hard lock for payment overdue")

            val daysOverdue = lockState.daysUntilDue ?: 0
            val hoursOverdue = lockState.hoursUntilDue ?: 0

            val lockReason = when {
                daysOverdue < 0 -> "Payment overdue by ${Math.abs(daysOverdue)} days"
                daysOverdue == 0L && hoursOverdue < 24 -> "Payment due today"
                else -> "Payment overdue"
            }

            // Apply hard lock via DevicePolicyManager
            hardLockManager.applyHardLock(
                reason = lockReason,
                recommendation = "Please make payment to unlock device",
                source = "payment_overdue"
            )

            // Save unlock password for offline verification
            if (!lockState.unlockPassword.isNullOrBlank()) {
                saveOfflineUnlockPassword(lockState.unlockPassword)
                Log.d(TAG, "ðŸ’¾ Offline unlock password saved")
            }

            Log.i(TAG, "âœ… Hard lock applied - payment overdue")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error applying hard lock: ${e.message}", e)
        }
    }

    /**
     * Verify offline unlock password
     *
     * Called when user enters password on hard lock screen while offline
     * Compares entered password with password sent via heartbeat
     *
     * @param enteredPassword Password entered by user
     * @return true if password is correct, false otherwise
     */
    fun verifyOfflineUnlockPassword(enteredPassword: String): Boolean {
        try {
            val storedPassword = getOfflineUnlockPassword()

            if (storedPassword.isNullOrBlank()) {
                Log.w(TAG, "âš ï¸ No offline unlock password stored")
                return false
            }

            val isCorrect = enteredPassword == storedPassword

            if (isCorrect) {
                Log.i(TAG, "âœ… Offline unlock password verified - CORRECT")
                // Clear lock state after successful unlock using RemoteDeviceControlManager
                remoteControl.unlockDevice()
                hardLockManager.clearLockState()
                clearOfflineUnlockPassword()
            } else {
                Log.w(TAG, "âŒ Offline unlock password verification FAILED")
            }

            return isCorrect
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error verifying offline password: ${e.message}", e)
            return false
        }
    }

    /**
     * Save offline unlock password securely
     *
     * Note: In production, this should be encrypted using Android Keystore
     * For now, stored in SharedPreferences (same as current implementation)
     *
     * @param password Password to save
     */
    private fun saveOfflineUnlockPassword(password: String) {
        try {
            sharedPreferencesManager.saveHeartbeatResponse(
                nextPaymentDate = null,
                unlockPassword = password,
                serverTime = null,
                isLocked = false,
                lockReason = null
            )
            Log.d(TAG, "ðŸ’¾ Offline unlock password saved")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error saving offline password: ${e.message}", e)
        }
    }

    /**
     * Get offline unlock password
     *
     * @return Stored password or null if not available
     */
    private fun getOfflineUnlockPassword(): String? {
        return try {
            sharedPreferencesManager.getUnlockPassword()
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error getting offline password: ${e.message}", e)
            null
        }
    }

    /**
     * Clear offline unlock password after successful unlock
     */
    private fun clearOfflineUnlockPassword() {
        try {
            // Clear by saving empty string
            sharedPreferencesManager.saveHeartbeatResponse(
                nextPaymentDate = null,
                unlockPassword = "",
                serverTime = null,
                isLocked = false,
                lockReason = null
            )
            Log.d(TAG, "ðŸ—‘ï¸ Offline unlock password cleared")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error clearing offline password: ${e.message}", e)
        }
    }

    /**
     * Get current payment lock status
     *
     * @return LockScreenState describing current lock status
     */
    fun getCurrentPaymentLockStatus(): LockScreenState? {
        return try {
            val nextPaymentDate = paymentDataManager.getNextPaymentDate()
            val unlockPassword = paymentDataManager.getUnlockPassword()

            LockScreenStrategy.determineLockScreenStateFromLocal(
                nextPaymentDate = nextPaymentDate,
                unlockPassword = unlockPassword
            )
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error getting payment lock status: ${e.message}", e)
            null
        }
    }

    /**
     * Check if device is currently hard-locked due to payment
     *
     * @return true if device is hard-locked for payment
     */
    fun isPaymentHardLocked(): Boolean {
        return try {
            val status = getCurrentPaymentLockStatus()
            status?.type == LockScreenType.HARD_LOCK_PAYMENT
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error checking payment hard lock: ${e.message}", e)
            false
        }
    }

    /**
     * Check if payment reminder should be shown
     *
     * @return true if device is in soft lock reminder state
     */
    fun isPaymentReminderActive(): Boolean {
        return try {
            val status = getCurrentPaymentLockStatus()
            status?.type == LockScreenType.SOFT_LOCK_REMINDER
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error checking payment reminder: ${e.message}", e)
            false
        }
    }

    /**
     * Get formatted payment due date for display
     *
     * @return Formatted date string or null
     */
    fun getFormattedPaymentDueDate(): String? {
        return try {
            val nextPaymentDate = paymentDataManager.getNextPaymentDate()
            if (nextPaymentDate != null) {
                LockScreenStrategy.formatDueDate(nextPaymentDate)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error formatting payment due date: ${e.message}", e)
            null
        }
    }

    /**
     * Get time remaining until payment due
     *
     * @return Formatted countdown string or null
     */
    fun getPaymentCountdown(): String? {
        return try {
            val nextPaymentDate = paymentDataManager.getNextPaymentDate()
            if (nextPaymentDate != null) {
                val timeUntilDue = LockScreenStrategy.getTimeUntilDue(nextPaymentDate)
                LockScreenStrategy.formatCountdown(
                    timeUntilDue.daysUntilDue,
                    timeUntilDue.hoursUntilDue,
                    timeUntilDue.minutesUntilDue
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error getting payment countdown: ${e.message}", e)
            null
        }
    }
}




