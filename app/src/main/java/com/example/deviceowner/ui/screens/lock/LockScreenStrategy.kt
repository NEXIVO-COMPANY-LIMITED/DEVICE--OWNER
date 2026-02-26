package com.microspace.payo.ui.screens.lock

import android.util.Log
import com.microspace.payo.data.models.heartbeat.HeartbeatResponse
import com.microspace.payo.data.models.heartbeat.NextPaymentInfo
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Lock Screen Strategy based on Payment Due Date
 *
 * LOGIC:
 * ======
 * 1. Parse next_payment.date_time from heartbeat response
 * 2. Calculate days until payment due
 * 3. Determine lock type:
 *    - SOFT LOCK (Reminder): 1 day BEFORE due date
 *    - HARD LOCK (Enforcement): ON or AFTER due date
 *    - UNLOCKED: More than 1 day before due date
 *
 * EXAMPLE:
 * ========
 * Payment Due: 2026-02-07 23:59:00
 * Today: 2026-02-05 14:30:00
 * Days Until: 2 days → UNLOCKED (normal operation)
 *
 * Today: 2026-02-06 14:30:00
 * Days Until: 1 day → SOFT LOCK (reminder notification)
 *
 * Today: 2026-02-07 14:30:00
 * Days Until: 0 days → HARD LOCK (device locked, show unlock password)
 *
 * Today: 2026-02-08 14:30:00
 * Days Until: -1 days → HARD LOCK (overdue, device locked)
 */

enum class LockScreenType {
    UNLOCKED,           // Normal operation (>1 day before due)
    SOFT_LOCK_REMINDER, // Reminder notification (1 day before due)
    HARD_LOCK_PAYMENT,  // Payment overdue (on or after due date)
    HARD_LOCK_SECURITY, // Security violation
    DEACTIVATION        // Device being deactivated
}

data class LockScreenState(
    val type: LockScreenType,
    val nextPaymentDate: String?,
    val unlockPassword: String?,
    val shopName: String?,
    val reason: String?,
    val daysUntilDue: Long?,
    val hoursUntilDue: Long?,
    val minutesUntilDue: Long?,
    val enableKioskMode: Boolean = false  // Enable kiosk mode for security violations
)

object LockScreenStrategy {

    private const val TAG = "LockScreenStrategy"

    /** Reminder shows exactly 1 day before due date. Overdue = exactly on due date (or after). */
    private const val REMINDER_DAYS_BEFORE = 1L

    /**
     * Determine lock screen state from heartbeat response
     *
     * @param response HeartbeatResponse from server
     * @return LockScreenState with type and details
     */
    fun determineLockScreenState(response: HeartbeatResponse): LockScreenState {
        Log.d(TAG, "Determining lock screen state from heartbeat response...")

        // PRIORITY 1: Deactivation requested
        if (response.isDeactivationRequested()) {
            Log.i(TAG, "🔓 Deactivation requested - showing deactivation screen")
            return LockScreenState(
                type = LockScreenType.DEACTIVATION,
                nextPaymentDate = null,
                unlockPassword = null,
                shopName = null,
                reason = "Device deactivation in progress",
                daysUntilDue = null,
                hoursUntilDue = null,
                minutesUntilDue = null,
                enableKioskMode = false
            )
        }

        // PRIORITY 2: IMEI/Management Status Lock (from backend)
        if (response.managementStatus?.lowercase() == "locked") {
            Log.e(TAG, "🚨 IMEI/Management Status Lock detected - showing hard lock screen with KIOSK MODE")
            return LockScreenState(
                type = LockScreenType.HARD_LOCK_SECURITY,
                nextPaymentDate = null,
                unlockPassword = null,
                shopName = response.content?.shop,
                reason = response.getLockReason(),
                daysUntilDue = null,
                hoursUntilDue = null,
                minutesUntilDue = null,
                enableKioskMode = true  // ENABLE KIOSK MODE FOR IMEI CHANGES
            )
        }

        // PRIORITY 3: Security violation (hard lock with kiosk mode)
        // Check for data mismatch, tampering, or security issues
        if (response.hasHighSeverityMismatches() ||
            (response.isDeviceLocked() && response.getLockReason().contains("Security", ignoreCase = true))) {
            Log.i(TAG, "🚨 Security violation detected - showing hard lock screen with KIOSK MODE")
            return LockScreenState(
                type = LockScreenType.HARD_LOCK_SECURITY,
                nextPaymentDate = null,
                unlockPassword = null,
                shopName = response.content?.shop,
                reason = response.getLockReason(),
                daysUntilDue = null,
                hoursUntilDue = null,
                minutesUntilDue = null,
                enableKioskMode = true  // ENABLE KIOSK MODE FOR SECURITY VIOLATIONS
            )
        }

        // PRIORITY 4: Payment-based lock (check next_payment date)
        if (response.hasNextPayment()) {
            val nextPaymentDate = response.getNextPaymentDateTime()
            val unlockPassword = response.getUnlockPassword()

            if (!nextPaymentDate.isNullOrEmpty()) {
                val timeUntilDue = calculateTimeUntilDue(nextPaymentDate)

                Log.d(TAG, "Next payment date: $nextPaymentDate")
                Log.d(TAG, "Days until due: ${timeUntilDue.daysUntilDue}")
                Log.d(TAG, "Hours until due: ${timeUntilDue.hoursUntilDue}")

                return when {
                    // HARD LOCK: On or after due date
                    timeUntilDue.daysUntilDue <= 0 -> {
                        Log.i(TAG, "💳 Payment overdue - showing hard lock screen")
                        LockScreenState(
                            type = LockScreenType.HARD_LOCK_PAYMENT,
                            nextPaymentDate = nextPaymentDate,
                            unlockPassword = unlockPassword,
                            shopName = response.content?.shop,
                            reason = "Payment overdue",
                            daysUntilDue = timeUntilDue.daysUntilDue,
                            hoursUntilDue = timeUntilDue.hoursUntilDue,
                            minutesUntilDue = timeUntilDue.minutesUntilDue,
                            enableKioskMode = false  // Payment lock does NOT use kiosk mode
                        )
                    }

                    // SOFT LOCK: Exactly 1 day before due date – reminder only; NO PASSWORD
                    timeUntilDue.daysUntilDue == REMINDER_DAYS_BEFORE -> {
                        Log.i(TAG, "⏰ Payment reminder - showing soft lock notification")
                        LockScreenState(
                            type = LockScreenType.SOFT_LOCK_REMINDER,
                            nextPaymentDate = nextPaymentDate,
                            unlockPassword = null, // Soft lock never uses password
                            shopName = response.content?.shop,
                            reason = "Payment reminder",
                            daysUntilDue = timeUntilDue.daysUntilDue,
                            hoursUntilDue = timeUntilDue.hoursUntilDue,
                            minutesUntilDue = timeUntilDue.minutesUntilDue,
                            enableKioskMode = false  // Reminder does NOT use kiosk mode
                        )
                    }

                    // UNLOCKED: More than 1 day before due date
                    else -> {
                        Log.d(TAG, "✅ Device unlocked - normal operation")
                        LockScreenState(
                            type = LockScreenType.UNLOCKED,
                            nextPaymentDate = nextPaymentDate,
                            unlockPassword = null,
                            shopName = null,
                            reason = null,
                            daysUntilDue = timeUntilDue.daysUntilDue,
                            hoursUntilDue = timeUntilDue.hoursUntilDue,
                            minutesUntilDue = timeUntilDue.minutesUntilDue,
                            enableKioskMode = false
                        )
                    }
                }
            }
        }

        // FALLBACK: Check is_locked flag
        if (response.isDeviceLocked()) {
            Log.i(TAG, "🔒 Device locked by server - showing hard lock screen")
            return LockScreenState(
                type = LockScreenType.HARD_LOCK_PAYMENT,
                nextPaymentDate = null,
                unlockPassword = null,
                shopName = response.content?.shop,
                reason = response.getLockReason(),
                daysUntilDue = null,
                hoursUntilDue = null,
                minutesUntilDue = null,
                enableKioskMode = false
            )
        }

        // DEFAULT: Unlocked
        Log.d(TAG, "✅ Device unlocked - normal operation")
        return LockScreenState(
            type = LockScreenType.UNLOCKED,
            nextPaymentDate = null,
            unlockPassword = null,
            shopName = null,
            reason = null,
            daysUntilDue = null,
            hoursUntilDue = null,
            minutesUntilDue = null,
            enableKioskMode = false
        )
    }

    /**
     * Determine lock/reminder state from locally saved next_payment (for offline or when using cached data).
     * Same logic as payment part of determineLockScreenState: overdue → hard lock, 1 day before → reminder, else unlocked.
     *
     * @param nextPaymentDate ISO-8601 date_time (e.g. from PaymentDataManager)
     * @param unlockPassword Password for unlock screen when overdue
     * @return LockScreenState or null if no date
     */
    fun determineLockScreenStateFromLocal(nextPaymentDate: String?, unlockPassword: String?): LockScreenState? {
        if (nextPaymentDate.isNullOrBlank()) return null
        val timeUntilDue = getTimeUntilDue(nextPaymentDate)
        return when {
            timeUntilDue.daysUntilDue <= 0 -> LockScreenState(
                type = LockScreenType.HARD_LOCK_PAYMENT,
                nextPaymentDate = nextPaymentDate,
                unlockPassword = unlockPassword,
                shopName = null,
                reason = "Payment overdue",
                daysUntilDue = timeUntilDue.daysUntilDue,
                hoursUntilDue = timeUntilDue.hoursUntilDue,
                minutesUntilDue = timeUntilDue.minutesUntilDue,
                enableKioskMode = false
            )
            timeUntilDue.daysUntilDue == REMINDER_DAYS_BEFORE -> LockScreenState(
                type = LockScreenType.SOFT_LOCK_REMINDER,
                nextPaymentDate = nextPaymentDate,
                unlockPassword = null,
                shopName = null,
                reason = "Payment reminder",
                daysUntilDue = timeUntilDue.daysUntilDue,
                hoursUntilDue = timeUntilDue.hoursUntilDue,
                minutesUntilDue = timeUntilDue.minutesUntilDue,
                enableKioskMode = false
            )
            else -> LockScreenState(
                type = LockScreenType.UNLOCKED,
                nextPaymentDate = nextPaymentDate,
                unlockPassword = null,
                shopName = null,
                reason = null,
                daysUntilDue = timeUntilDue.daysUntilDue,
                hoursUntilDue = timeUntilDue.hoursUntilDue,
                minutesUntilDue = timeUntilDue.minutesUntilDue,
                enableKioskMode = false
            )
        }
    }

    /**
     * Calculate time remaining until payment due date (public for offline/local use).
     *
     * @param dueDateTimeString ISO-8601 datetime string (e.g., "2026-02-07T23:59:00+03:00")
     * @return TimeUntilDue with days, hours, minutes
     */
    fun getTimeUntilDue(dueDateTimeString: String): TimeUntilDue {
        return calculateTimeUntilDue(dueDateTimeString)
    }

    /**
     * Calculate time remaining until payment due date
     *
     * @param dueDateTimeString ISO-8601 datetime string (e.g., "2026-02-07T23:59:00+03:00")
     * @return TimeUntilDue with days, hours, minutes
     */
    private fun calculateTimeUntilDue(dueDateTimeString: String): TimeUntilDue {
        return try {
            // Parse ISO-8601 datetime with timezone
            val dueDateTime = ZonedDateTime.parse(dueDateTimeString)
            val now = ZonedDateTime.now()

            // Calculate differences
            val daysUntil = ChronoUnit.DAYS.between(now, dueDateTime)
            val hoursUntil = ChronoUnit.HOURS.between(now, dueDateTime)
            val minutesUntil = ChronoUnit.MINUTES.between(now, dueDateTime)

            Log.d(TAG, "Time calculation: now=$now, due=$dueDateTime")
            Log.d(TAG, "Days: $daysUntil, Hours: $hoursUntil, Minutes: $minutesUntil")

            TimeUntilDue(
                daysUntilDue = daysUntil,
                hoursUntilDue = hoursUntil,
                minutesUntilDue = minutesUntil
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing due date: $dueDateTimeString - ${e.message}", e)
            TimeUntilDue(daysUntilDue = 0, hoursUntilDue = 0, minutesUntilDue = 0)
        }
    }

    /**
     * Format due date for display
     *
     * @param dueDateTimeString ISO-8601 datetime string
     * @return Formatted string like "Feb 07, 2026 at 11:59 PM"
     */
    fun formatDueDate(dueDateTimeString: String?): String {
        if (dueDateTimeString.isNullOrEmpty()) return "Unknown"

        return try {
            val dueDateTime = ZonedDateTime.parse(dueDateTimeString)
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a")
            dueDateTime.format(formatter)
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting due date: ${e.message}")
            dueDateTimeString
        }
    }

    /**
     * Format countdown for display
     *
     * @param daysUntil Days until due
     * @param hoursUntil Hours until due
     * @return Formatted string like "1 day, 5 hours" or "23 hours, 45 minutes"
     */
    fun formatCountdown(daysUntil: Long?, hoursUntil: Long?, minutesUntil: Long?): String {
        if (daysUntil == null || hoursUntil == null || minutesUntil == null) return "Unknown"

        return when {
            daysUntil > 0 -> {
                val remainingHours = hoursUntil % 24
                "$daysUntil day${if (daysUntil > 1) "s" else ""}, $remainingHours hour${if (remainingHours != 1L) "s" else ""}"
            }
            hoursUntil > 0 -> {
                val remainingMinutes = minutesUntil % 60
                "$hoursUntil hour${if (hoursUntil > 1) "s" else ""}, $remainingMinutes minute${if (remainingMinutes != 1L) "s" else ""}"
            }
            minutesUntil > 0 -> {
                "$minutesUntil minute${if (minutesUntil > 1) "s" else ""}"
            }
            else -> "Overdue"
        }
    }
}

data class TimeUntilDue(
    val daysUntilDue: Long,
    val hoursUntilDue: Long,
    val minutesUntilDue: Long
)
