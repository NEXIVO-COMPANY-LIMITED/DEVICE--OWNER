package com.example.deviceowner.utils

import android.content.Context
import com.example.deviceowner.ui.activities.lock.EnhancedSoftLockActivity

/**
 * Demonstration of different soft lock screens for each detection type
 * Each violation gets its own completely customized screen
 */
object SoftLockDemo {
    
    /**
     * Demo: App Uninstall Attempt Detection
     */
    fun showUninstallAttemptScreen(context: Context) {
        EnhancedSoftLockActivity.startSoftLock(
            context = context,
            reason = "App uninstall attempt blocked",
            triggerAction = "UNINSTALL_ATTEMPT: User attempted to uninstall the device management application. This is strictly prohibited and violates the device agreement."
        )
    }
    
    /**
     * Demo: Data Clear Attempt Detection
     */
    fun showDataClearAttemptScreen(context: Context) {
        EnhancedSoftLockActivity.startSoftLock(
            context = context,
            reason = "App data clearing blocked",
            triggerAction = "DATA_CLEAR_ATTEMPT: User attempted to clear application data while device was restricted. This could disrupt device management and loan tracking."
        )
    }
    
    /**
     * Demo: USB Debug Mode Attempt Detection
     */
    fun showUsbDebugAttemptScreen(context: Context) {
        EnhancedSoftLockActivity.startSoftLock(
            context = context,
            reason = "USB debugging activation blocked",
            triggerAction = "USB_DEBUG_ATTEMPT: User attempted to enable USB debugging while device was restricted. This violates security policies."
        )
    }
    
    /**
     * Demo: Developer Mode Attempt Detection
     */
    fun showDeveloperModeAttemptScreen(context: Context) {
        EnhancedSoftLockActivity.startSoftLock(
            context = context,
            reason = "Developer options access blocked",
            triggerAction = "DEVELOPER_MODE_ATTEMPT: User attempted to enable developer options while device was restricted. This provides unauthorized system access."
        )
    }
    
    /**
     * Demo: Payment Overdue Screen
     */
    fun showPaymentOverdueScreen(context: Context) {
        EnhancedSoftLockActivity.startSoftLock(
            context = context,
            reason = "Payment overdue - please contact support",
            triggerAction = "PAYMENT_OVERDUE: Your payment is overdue and device access has been limited until payment is received."
        )
    }
    
    /**
     * Demo: Payment Reminder Screen
     */
    fun showPaymentReminderScreen(context: Context) {
        EnhancedSoftLockActivity.startSoftLock(
            context = context,
            reason = "Payment due soon",
            triggerAction = "PAYMENT_REMINDER: This is a reminder that your payment is due soon. Please make payment to avoid restrictions."
        )
    }
    
    /**
     * Demo: Root Detection Screen
     */
    fun showRootDetectionScreen(context: Context) {
        EnhancedSoftLockActivity.startSoftLock(
            context = context,
            reason = "Device rooting detected",
            triggerAction = "ROOT_DETECTION: Device rooting has been detected. Rooted devices are strictly prohibited under your device agreement."
        )
    }
    
    /**
     * Demo: Custom ROM Detection Screen
     */
    fun showCustomRomDetectionScreen(context: Context) {
        EnhancedSoftLockActivity.startSoftLock(
            context = context,
            reason = "Custom ROM detected",
            triggerAction = "CUSTOM_ROM_DETECTION: Custom ROM installation detected. Please restore original manufacturer firmware."
        )
    }
}

/**
 * Expected Screen Customizations:
 * 
 * UNINSTALL_ATTEMPT:
 * - Title: "APP UNINSTALL BLOCKED" (Red)
 * - Icon: Block icon (Red)
 * - Primary: "DO NOT UNINSTALL THE APPLICATION"
 * - Warning: "This application is required for device management and cannot be removed."
 * - Button: "I WILL NOT UNINSTALL"
 * 
 * DATA_CLEAR_ATTEMPT:
 * - Title: "DATA CLEARING BLOCKED" (Red)
 * - Icon: Delete icon (Red)
 * - Primary: "DO NOT CLEAR APPLICATION DATA"
 * - Warning: "Clearing app data will disrupt device management and is not permitted."
 * - Button: "I WILL NOT CLEAR DATA"
 * 
 * USB_DEBUG_ATTEMPT:
 * - Title: "DEBUG MODE BLOCKED" (Red)
 * - Icon: Developer mode icon (Red)
 * - Primary: "DO NOT ENABLE USB DEBUGGING"
 * - Warning: "USB debugging allows unauthorized access to device internals and is strictly prohibited."
 * - Button: "I WILL NOT ENABLE DEBUG"
 * 
 * PAYMENT_OVERDUE:
 * - Title: "PAYMENT REQUIRED" (Orange)
 * - Icon: Payment icon (Orange)
 * - Primary: "YOUR PAYMENT IS OVERDUE"
 * - Warning: "Device access is limited until payment is received."
 * - Button: "I UNDERSTAND"
 * 
 * Each screen is completely separate and customized for its specific detection!
 */