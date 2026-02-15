package com.example.deviceowner.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import com.example.deviceowner.data.models.HeartbeatResponse
import com.example.deviceowner.kiosk.KioskModeManager
import com.example.deviceowner.kiosk.PinManager
import com.example.deviceowner.ui.activities.LockScreenActivity

/**
 * Lock Screen Manager
 * 
 * Handles showing/hiding lock screens based on heartbeat response
 * Integrates with HeartbeatService to display appropriate lock screen
 * 
 * For security violations: Enables kiosk mode to lock device completely
 */
object LockScreenManager {
    
    private const val TAG = "LockScreenManager"
    
    /**
     * Process heartbeat response and show appropriate lock screen
     * 
     * Called from HeartbeatService.processHeartbeatResponse()
     * 
     * @param context Android context
     * @param response HeartbeatResponse from server
     */
    fun handleHeartbeatResponse(context: Context, response: HeartbeatResponse) {
        Log.d(TAG, "Processing heartbeat response for lock screen...")
        
        // Determine lock screen state
        val lockState = LockScreenStrategy.determineLockScreenState(response)
        
        Log.d(TAG, "Lock screen state: ${lockState.type}, Kiosk mode: ${lockState.enableKioskMode}")
        
        // Store PIN if provided in response (for hard lock payment)
        if (lockState.type == LockScreenType.HARD_LOCK_PAYMENT && lockState.unlockPassword != null) {
            Log.d(TAG, "💾 Storing unlock PIN in local database")
            PinManager.storePinLocally(context, lockState.unlockPassword)
        }
        
        when (lockState.type) {
            LockScreenType.DEACTIVATION -> {
                Log.i(TAG, "🔓 Showing deactivation screen")
                showDeactivationScreen(context)
            }
            
            LockScreenType.HARD_LOCK_SECURITY -> {
                Log.i(TAG, "🚨 Showing hard lock - security violation screen")
                
                // Enable kiosk mode for security violations
                if (lockState.enableKioskMode) {
                    Log.i(TAG, "🔒 Enabling KIOSK MODE for security violation")
                    KioskModeManager.enableKioskMode(context, LockScreenActivity::class.java)
                }
                
                showHardLockSecurityScreen(context, lockState)
            }
            
            LockScreenType.HARD_LOCK_PAYMENT -> {
                Log.i(TAG, "💳 Showing hard lock - payment overdue screen")
                showHardLockPaymentScreen(context, lockState)
            }
            
            LockScreenType.SOFT_LOCK_REMINDER -> {
                Log.i(TAG, "⏰ Showing soft lock - payment reminder screen")
                showSoftLockReminderScreen(context, lockState)
            }
            
            LockScreenType.UNLOCKED -> {
                Log.d(TAG, "✅ Device unlocked - dismissing any lock screens")
                
                // Clear stored PIN when device is unlocked
                PinManager.clearPin(context)
                
                // Disable kiosk mode if it was enabled
                if (KioskModeManager.isKioskModeEnabled(context)) {
                    Log.i(TAG, "🔓 Disabling kiosk mode - device unlocked")
                    KioskModeManager.disableKioskMode(context)
                }
                
                dismissLockScreen(context)
            }
        }
    }
    
    /**
     * Show soft lock reminder screen.
     * Device is NOT locked – user dismisses with "Continue" only. NO PASSWORD for soft lock unlock.
     */
    private fun showSoftLockReminderScreen(context: Context, lockState: LockScreenState) {
        try {
            val intent = Intent(context, LockScreenActivity::class.java).apply {
                putExtra("lock_type", "SOFT_LOCK_REMINDER")
                putExtra("next_payment_date", lockState.nextPaymentDate)
                putExtra("days_until_due", lockState.daysUntilDue ?: 0L)
                putExtra("hours_until_due", lockState.hoursUntilDue ?: 0L)
                putExtra("minutes_until_due", lockState.minutesUntilDue ?: 0L)
                putExtra("shop_name", lockState.shopName)
                // Intentionally no unlock_password – soft lock is password-free; user taps Continue to dismiss
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            Log.i(TAG, "✅ Soft lock reminder screen shown (no password)")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing soft lock reminder: ${e.message}", e)
        }
    }
    
    /**
     * Show hard lock payment overdue screen
     * Device is LOCKED - user cannot use device
     */
    private fun showHardLockPaymentScreen(context: Context, lockState: LockScreenState) {
        try {
            val intent = Intent(context, LockScreenActivity::class.java).apply {
                putExtra("lock_type", "HARD_LOCK_PAYMENT")
                putExtra("next_payment_date", lockState.nextPaymentDate)
                putExtra("unlock_password", lockState.unlockPassword)
                putExtra("shop_name", lockState.shopName)
                putExtra("days_until_due", lockState.daysUntilDue ?: 0L)
                putExtra("hours_until_due", lockState.hoursUntilDue ?: 0L)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            context.startActivity(intent)
            Log.i(TAG, "✅ Hard lock payment screen shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing hard lock payment: ${e.message}", e)
        }
    }
    
    /**
     * Show hard lock security violation screen
     * Device is LOCKED - user cannot use device
     */
    private fun showHardLockSecurityScreen(context: Context, lockState: LockScreenState) {
        try {
            val intent = Intent(context, LockScreenActivity::class.java).apply {
                putExtra("lock_type", "HARD_LOCK_SECURITY")
                putExtra("reason", lockState.reason)
                putExtra("shop_name", lockState.shopName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            context.startActivity(intent)
            Log.i(TAG, "✅ Hard lock security screen shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing hard lock security: ${e.message}", e)
        }
    }
    
    /**
     * Show deactivation screen
     * Device is being deactivated
     */
    private fun showDeactivationScreen(context: Context) {
        try {
            val intent = Intent(context, LockScreenActivity::class.java).apply {
                putExtra("lock_type", "DEACTIVATION")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            context.startActivity(intent)
            Log.i(TAG, "✅ Deactivation screen shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing deactivation screen: ${e.message}", e)
        }
    }
    
    /**
     * Dismiss lock screen and return to normal operation
     */
    private fun dismissLockScreen(context: Context) {
        try {
            // Send broadcast to close LockScreenActivity if it's open
            val intent = Intent("com.example.deviceowner.DISMISS_LOCK_SCREEN")
            context.sendBroadcast(intent)
            Log.d(TAG, "Lock screen dismiss broadcast sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing lock screen: ${e.message}", e)
        }
    }
}
