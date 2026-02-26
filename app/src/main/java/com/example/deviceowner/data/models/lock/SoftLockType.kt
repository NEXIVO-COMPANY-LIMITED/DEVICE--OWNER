package com.microspace.payo.data.models.lock

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Soft Lock Types - Payment Reminder and SIM Change
 */
enum class SoftLockType(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val primaryMessage: String,
    val warningMessage: String,
    val actionAdvice: String,
    val buttonText: String
) {
    // Payment Reminder
    PAYMENT_REMINDER(
        title = "PAYMENT REMINDER",
        icon = Icons.Default.Notifications,
        color = Color(0xFFF57C00),
        primaryMessage = "PAYMENT DUE SOON",
        warningMessage = "Your next payment is due. Please ensure timely payment to avoid restrictions.",
        actionAdvice = "Make your payment before the due date to maintain uninterrupted device access.",
        buttonText = "I UNDERSTAND"
    ),
    // SIM Card Changed – soft lock reminder overlay
    SIM_CHANGE(
        title = "SIM CARD CHANGED",
        icon = Icons.Default.Warning,
        color = Color(0xFFD32F2F),
        primaryMessage = "UNAUTHORIZED SIM DETECTED",
        warningMessage = "A different SIM card was detected. This device is managed by your organization.",
        actionAdvice = "This event has been logged. Tap Continue to dismiss and use the device.",
        buttonText = "CONTINUE"
    );

    companion object {
        /**
         * Determine soft lock type based on trigger action and reason
         */
        fun fromTriggerAction(triggerAction: String, reason: String): SoftLockType {
            if (triggerAction.equals("sim_change", ignoreCase = true) ||
                reason.contains("SIM", ignoreCase = true) && reason.contains("change", ignoreCase = true)) {
                return SIM_CHANGE
            }
            return PAYMENT_REMINDER
        }
        
        /**
         * Determine if violation should trigger hard lock instead of soft lock
         * Hard lock is never triggered - only soft lock payment reminder
         */
        fun shouldTriggerHardLock(triggerAction: String, reason: String): Boolean {
            // Hard lock is disabled - only soft lock payment reminder
            return false
        }
    }
}