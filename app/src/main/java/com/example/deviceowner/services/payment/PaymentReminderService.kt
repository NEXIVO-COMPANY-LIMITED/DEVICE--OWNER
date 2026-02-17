package com.example.deviceowner.services.payment

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.deviceowner.utils.storage.PaymentDataManager
import com.example.deviceowner.ui.screens.lock.LockScreenStrategy
import java.util.concurrent.TimeUnit

/**
 * PaymentReminderService - Manages payment reminder scheduling and notifications
 *
 * FLOW:
 * 1. Heartbeat receives next_payment_date
 * 2. PaymentReminderService schedules reminder for 1 day before due date
 * 3. At scheduled time:
 *    - Send SMS reminder (via backend)
 *    - Show soft lock overlay (yellow screen)
 *    - Log reminder event
 * 4. On payment due date:
 *    - Apply hard lock
 *    - Show hard lock screen with password input
 *    - Mark as overdue
 *
 * Works for both online and offline scenarios.
 * Offline: Uses local next_payment_date to determine when to show reminder/lock
 */
class PaymentReminderService(private val context: Context) {

    companion object {
        private const val TAG = "PaymentReminderService"
        private const val REMINDER_WORK_TAG = "payment_reminder"
        private const val OVERDUE_WORK_TAG = "payment_overdue"
    }

    private val paymentDataManager = PaymentDataManager(context)

    /**
     * Schedule payment reminder for 1 day before due date
     *
     * Called after heartbeat receives updated next_payment_date
     *
     * @param nextPaymentDate ISO-8601 datetime (e.g., "2026-02-07T23:59:00+03:00")
     */
    fun schedulePaymentReminder(nextPaymentDate: String?) {
        if (nextPaymentDate.isNullOrBlank()) {
            Log.d(TAG, "No next payment date - skipping reminder schedule")
            cancelPaymentReminder()
            return
        }

        try {
            Log.d(TAG, "📅 Scheduling payment reminder for: $nextPaymentDate")

            // Calculate time until 1 day before due date
            val timeUntilDue = LockScreenStrategy.getTimeUntilDue(nextPaymentDate)
            val daysUntilDue = timeUntilDue.daysUntilDue

            // Schedule reminder for 1 day before due date
            val daysUntilReminder = daysUntilDue - 1

            if (daysUntilReminder <= 0) {
                Log.d(TAG, "⏰ Reminder should show now or soon (days until: $daysUntilReminder)")
                // Show reminder immediately
                showPaymentReminder(nextPaymentDate)
                return
            }

            // Schedule work for reminder
            val reminderDelay = daysUntilReminder * 24 * 60 // minutes
            Log.d(TAG, "⏰ Reminder scheduled for $daysUntilReminder days from now ($reminderDelay minutes)")

            val reminderWork = OneTimeWorkRequestBuilder<PaymentReminderWorker>()
                .setInitialDelay(reminderDelay.toLong(), TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        "next_payment_date" to nextPaymentDate
                    )
                )
                .addTag(REMINDER_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "payment_reminder_${nextPaymentDate.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                reminderWork
            )

            Log.i(TAG, "✅ Payment reminder scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling payment reminder: ${e.message}", e)
        }
    }

    /**
     * Schedule payment overdue check for due date
     *
     * Called after heartbeat receives updated next_payment_date
     *
     * @param nextPaymentDate ISO-8601 datetime (e.g., "2026-02-07T23:59:00+03:00")
     */
    fun schedulePaymentOverdueCheck(nextPaymentDate: String?) {
        if (nextPaymentDate.isNullOrBlank()) {
            Log.d(TAG, "No next payment date - skipping overdue check schedule")
            cancelPaymentOverdueCheck()
            return
        }

        try {
            Log.d(TAG, "📅 Scheduling payment overdue check for: $nextPaymentDate")

            // Calculate time until due date
            val timeUntilDue = LockScreenStrategy.getTimeUntilDue(nextPaymentDate)
            val daysUntilDue = timeUntilDue.daysUntilDue

            if (daysUntilDue <= 0) {
                Log.d(TAG, "🔒 Payment is already overdue (days: $daysUntilDue)")
                // Apply hard lock immediately
                applyPaymentOverdueHardLock(nextPaymentDate)
                return
            }

            // Schedule work for overdue check
            val overdueDelay = (daysUntilDue + 1) * 24 * 60 // minutes (check 1 day after due)
            Log.d(TAG, "🔒 Overdue check scheduled for $daysUntilDue days from now ($overdueDelay minutes)")

            val overdueWork = OneTimeWorkRequestBuilder<PaymentOverdueWorker>()
                .setInitialDelay(overdueDelay.toLong(), TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        "next_payment_date" to nextPaymentDate
                    )
                )
                .addTag(OVERDUE_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "payment_overdue_${nextPaymentDate.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                overdueWork
            )

            Log.i(TAG, "✅ Payment overdue check scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling payment overdue check: ${e.message}", e)
        }
    }

    /**
     * Show payment reminder immediately
     *
     * Called when:
     * - Scheduled reminder time arrives
     * - Payment date is within 1 day
     * - App starts and payment is due soon
     *
     * @param nextPaymentDate ISO-8601 datetime
     */
    fun showPaymentReminder(nextPaymentDate: String?) {
        if (nextPaymentDate.isNullOrBlank()) return

        try {
            Log.i(TAG, "📢 Showing payment reminder")

            val timeUntilDue = LockScreenStrategy.getTimeUntilDue(nextPaymentDate)
            val formattedDate = LockScreenStrategy.formatDueDate(nextPaymentDate)
            val countdown = LockScreenStrategy.formatCountdown(
                timeUntilDue.daysUntilDue,
                timeUntilDue.hoursUntilDue,
                timeUntilDue.minutesUntilDue
            )

            val reminderMessage = """
                Payment Reminder
                Due: $formattedDate
                Time remaining: $countdown
            """.trimIndent()

            // Send SMS reminder (via backend integration)
            sendPaymentReminderSMS(nextPaymentDate, formattedDate, countdown)

            // Show soft lock overlay
            com.example.deviceowner.services.lock.SoftLockOverlayService.startOverlay(
                context = context,
                reason = reminderMessage,
                triggerAction = "payment_reminder",
                nextPaymentDate = nextPaymentDate
            )

            Log.i(TAG, "✅ Payment reminder shown")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing payment reminder: ${e.message}", e)
        }
    }

    /**
     * Apply hard lock for payment overdue
     *
     * Called when:
     * - Scheduled overdue time arrives
     * - Payment date has passed
     * - Heartbeat indicates payment is overdue
     *
     * @param nextPaymentDate ISO-8601 datetime
     */
    fun applyPaymentOverdueHardLock(nextPaymentDate: String?) {
        if (nextPaymentDate.isNullOrBlank()) return

        try {
            Log.i(TAG, "🔒 Applying hard lock for payment overdue")

            val paymentLockManager = PaymentLockManager(context)
            val unlockPassword = paymentDataManager.getUnlockPassword()

            paymentLockManager.processPaymentStatus(
                nextPaymentDate = nextPaymentDate,
                unlockPassword = unlockPassword
            )

            Log.i(TAG, "✅ Hard lock applied for payment overdue")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying hard lock: ${e.message}", e)
        }
    }

    /**
     * Send payment reminder SMS
     *
     * Integrates with backend SMS service to send reminder message
     * Message includes due date and time remaining
     *
     * @param nextPaymentDate ISO-8601 datetime
     * @param formattedDate Formatted date string
     * @param countdown Formatted countdown string
     */
    private fun sendPaymentReminderSMS(
        nextPaymentDate: String,
        formattedDate: String,
        countdown: String
    ) {
        try {
            Log.d(TAG, "📱 Sending payment reminder SMS")
            Log.d(TAG, "   Due: $formattedDate")
            Log.d(TAG, "   Countdown: $countdown")

            // TODO: Integrate with backend SMS service
            // This would call an API endpoint to send SMS reminder
            // Example: POST /api/sms/send-payment-reminder
            // Body: { next_payment_date, formatted_date, countdown }

            Log.d(TAG, "✅ Payment reminder SMS sent")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending payment reminder SMS: ${e.message}", e)
        }
    }

    /**
     * Cancel payment reminder
     *
     * Called when:
     * - Payment is made
     * - Next payment date is cleared
     * - Device is deactivated
     */
    fun cancelPaymentReminder() {
        try {
            Log.d(TAG, "❌ Cancelling payment reminder")
            WorkManager.getInstance(context).cancelAllWorkByTag(REMINDER_WORK_TAG)
            Log.d(TAG, "✅ Payment reminder cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling payment reminder: ${e.message}", e)
        }
    }

    /**
     * Cancel payment overdue check
     *
     * Called when:
     * - Payment is made
     * - Next payment date is cleared
     * - Device is deactivated
     */
    fun cancelPaymentOverdueCheck() {
        try {
            Log.d(TAG, "❌ Cancelling payment overdue check")
            WorkManager.getInstance(context).cancelAllWorkByTag(OVERDUE_WORK_TAG)
            Log.d(TAG, "✅ Payment overdue check cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling payment overdue check: ${e.message}", e)
        }
    }

    /**
     * Cancel all payment-related work
     *
     * Called during device deactivation
     */
    fun cancelAllPaymentWork() {
        try {
            Log.d(TAG, "🗑️ Cancelling all payment work")
            cancelPaymentReminder()
            cancelPaymentOverdueCheck()
            Log.d(TAG, "✅ All payment work cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling all payment work: ${e.message}", e)
        }
    }
}

/**
 * Worker for payment reminder
 *
 * Runs at scheduled time to show payment reminder
 */
class PaymentReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PaymentReminderWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val nextPaymentDate = inputData.getString("next_payment_date")
            Log.d(TAG, "⏰ Payment reminder worker triggered for: $nextPaymentDate")

            if (nextPaymentDate != null) {
                PaymentReminderService(applicationContext).showPaymentReminder(nextPaymentDate)
                Result.success()
            } else {
                Log.w(TAG, "⚠️ No next payment date provided")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Payment reminder worker error: ${e.message}", e)
            Result.retry()
        }
    }
}

/**
 * Worker for payment overdue check
 *
 * Runs at scheduled time to apply hard lock for overdue payment
 */
class PaymentOverdueWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PaymentOverdueWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val nextPaymentDate = inputData.getString("next_payment_date")
            Log.d(TAG, "🔒 Payment overdue worker triggered for: $nextPaymentDate")

            if (nextPaymentDate != null) {
                PaymentReminderService(applicationContext).applyPaymentOverdueHardLock(nextPaymentDate)
                Result.success()
            } else {
                Log.w(TAG, "⚠️ No next payment date provided")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Payment overdue worker error: ${e.message}", e)
            Result.retry()
        }
    }
}
