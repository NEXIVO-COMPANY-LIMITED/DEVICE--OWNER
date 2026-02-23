package com.example.deviceowner.ui.screens.lock

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.data.models.heartbeat.HeartbeatResponse
import com.example.deviceowner.kiosk.KioskModeManager
import com.example.deviceowner.ui.activities.lock.payment.SoftLockReminderActivity
import com.example.deviceowner.ui.activities.lock.payment.PaymentOverdueActivity
import com.example.deviceowner.ui.activities.lock.security.SecurityViolationActivity
import com.example.deviceowner.ui.activities.lock.system.DeactivationActivity

/**
 * Lock Screen Manager
 */
object LockScreenManager {

    private const val TAG = "LockScreenManager"

    fun handleHeartbeatResponse(context: Context, response: HeartbeatResponse) {
        val lockState = LockScreenStrategy.determineLockScreenState(response)

        when (lockState.type) {
            LockScreenType.DEACTIVATION -> showDeactivationScreen(context)
            LockScreenType.HARD_LOCK_SECURITY -> showHardLockSecurityScreen(context, lockState)
            LockScreenType.HARD_LOCK_PAYMENT -> showHardLockPaymentScreen(context, lockState)
            LockScreenType.SOFT_LOCK_REMINDER -> showSoftLockReminderScreen(context, lockState)
            LockScreenType.UNLOCKED -> dismissLockScreen(context)
        }
    }

    private fun showSoftLockReminderScreen(context: Context, lockState: LockScreenState) {
        val intent = Intent(context, SoftLockReminderActivity::class.java).apply {
            putExtra("next_payment_date", lockState.nextPaymentDate)
            putExtra("days_remaining", lockState.daysUntilDue ?: 0L)
            putExtra("hours_remaining", lockState.hoursUntilDue ?: 0L)
            putExtra("minutes_remaining", lockState.minutesUntilDue ?: 0L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun showHardLockPaymentScreen(context: Context, lockState: LockScreenState) {
        val intent = Intent(context, PaymentOverdueActivity::class.java).apply {
            putExtra("next_payment_date", lockState.nextPaymentDate)
            putExtra("days_overdue", -(lockState.daysUntilDue ?: 0L))
            putExtra("hours_overdue", -(lockState.hoursUntilDue ?: 0L))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun showHardLockSecurityScreen(context: Context, lockState: LockScreenState) {
        val intent = Intent(context, SecurityViolationActivity::class.java).apply {
            putExtra("lock_reason", lockState.reason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun showDeactivationScreen(context: Context) {
        val intent = Intent(context, DeactivationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun dismissLockScreen(context: Context) {
        context.sendBroadcast(Intent("com.example.deviceowner.DISMISS_LOCK_SCREEN"))
        KioskModeManager.disableKioskMode(context)
    }
}
