package com.example.deviceowner.data.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Soft Lock Types with specific customization for each detection scenario
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
    // App Uninstall Attempt
    UNINSTALL_ATTEMPT(
        title = "APP UNINSTALL BLOCKED",
        icon = Icons.Default.Block,
        color = Color(0xFFD32F2F),
        primaryMessage = "DO NOT UNINSTALL THE APPLICATION",
        warningMessage = "This application is required for device management and cannot be removed.",
        actionAdvice = "Attempting to uninstall this app violates your device agreement and may result in permanent device lock.",
        buttonText = "I WILL NOT UNINSTALL"
    ),
    
    // App Data Clear Attempt
    DATA_CLEAR_ATTEMPT(
        title = "DATA CLEARING BLOCKED",
        icon = Icons.Default.DeleteForever,
        color = Color(0xFFD32F2F),
        primaryMessage = "DO NOT CLEAR APPLICATION DATA",
        warningMessage = "Clearing app data will disrupt device management and is not permitted.",
        actionAdvice = "This action could compromise device security and loan tracking. Please contact support if you're experiencing issues.",
        buttonText = "I WILL NOT CLEAR DATA"
    ),
    
    // USB Debug Mode Attempt
    USB_DEBUG_ATTEMPT(
        title = "DEBUG MODE BLOCKED",
        icon = Icons.Default.DeveloperMode,
        color = Color(0xFFD32F2F),
        primaryMessage = "DO NOT ENABLE USB DEBUGGING",
        warningMessage = "USB debugging allows unauthorized access to device internals and is strictly prohibited.",
        actionAdvice = "Enabling debug mode violates security policies and will result in immediate device lock.",
        buttonText = "I WILL NOT ENABLE DEBUG"
    ),
    
    // Developer Options Attempt
    DEVELOPER_MODE_ATTEMPT(
        title = "DEVELOPER OPTIONS BLOCKED",
        icon = Icons.Default.Settings,
        color = Color(0xFFD32F2F),
        primaryMessage = "DO NOT ACCESS DEVELOPER SETTINGS",
        warningMessage = "Developer options provide system-level access that could compromise device security.",
        actionAdvice = "These settings are restricted under your device agreement. Unauthorized access will trigger security measures.",
        buttonText = "I WILL NOT ACCESS SETTINGS"
    ),
    
    // Payment Overdue
    PAYMENT_OVERDUE(
        title = "PAYMENT REQUIRED",
        icon = Icons.Default.Payment,
        color = Color(0xFFF57C00),
        primaryMessage = "YOUR PAYMENT IS OVERDUE",
        warningMessage = "Device access is limited until payment is received.",
        actionAdvice = "Please make your payment as soon as possible to restore full device functionality.",
        buttonText = "I UNDERSTAND"
    ),
    
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
    
    // Security Violation
    SECURITY_VIOLATION(
        title = "SECURITY VIOLATION",
        icon = Icons.Default.Security,
        color = Color(0xFFD32F2F),
        primaryMessage = "UNAUTHORIZED ACTIVITY DETECTED",
        warningMessage = "Your device has detected unauthorized security modifications.",
        actionAdvice = "Continued violations will result in permanent device restrictions. Please contact support immediately.",
        buttonText = "I UNDERSTAND"
    ),
    
    // Root Detection
    ROOT_DETECTION(
        title = "ROOT ACCESS DETECTED",
        icon = Icons.Default.Warning,
        color = Color(0xFFD32F2F),
        primaryMessage = "DO NOT ROOT THIS DEVICE",
        warningMessage = "Device rooting has been detected and is strictly prohibited.",
        actionAdvice = "Rooted devices pose security risks and violate your agreement. Please restore device to original state.",
        buttonText = "I WILL UNROOT DEVICE"
    ),
    
    // Custom ROM Detection
    CUSTOM_ROM_DETECTION(
        title = "CUSTOM ROM DETECTED",
        icon = Icons.Default.PhoneAndroid,
        color = Color(0xFFD32F2F),
        primaryMessage = "DO NOT USE CUSTOM FIRMWARE",
        warningMessage = "Custom ROM installation has been detected and is not permitted.",
        actionAdvice = "Please restore the original manufacturer firmware to continue using this device.",
        buttonText = "I WILL RESTORE FIRMWARE"
    ),
    
    // General Server Lock
    SERVER_RESTRICTION(
        title = "DEVICE RESTRICTED",
        icon = Icons.Default.Lock,
        color = Color(0xFF757575),
        primaryMessage = "ACCESS TEMPORARILY LIMITED",
        warningMessage = "This device has been restricted by the management server.",
        actionAdvice = "Please contact support for assistance with resolving this restriction.",
        buttonText = "CONTACT SUPPORT"
    );
    
    companion object {
        /**
         * Determine soft lock type based on trigger action and reason
         */
        fun fromTriggerAction(triggerAction: String, reason: String): SoftLockType {
            val actionLower = triggerAction.lowercase()
            val reasonLower = reason.lowercase()
            
            return when {
                // App manipulation attempts
                actionLower.contains("uninstall") -> UNINSTALL_ATTEMPT
                actionLower.contains("clear") || actionLower.contains("data") -> DATA_CLEAR_ATTEMPT
                
                // Debug/Developer attempts
                actionLower.contains("usb") && actionLower.contains("debug") -> USB_DEBUG_ATTEMPT
                actionLower.contains("developer") || actionLower.contains("settings") -> DEVELOPER_MODE_ATTEMPT
                
                // Security violations
                actionLower.contains("root") || reasonLower.contains("root") -> ROOT_DETECTION
                actionLower.contains("custom") || actionLower.contains("rom") -> CUSTOM_ROM_DETECTION
                actionLower.contains("security") || actionLower.contains("violation") -> SECURITY_VIOLATION
                
                // Payment related
                reasonLower.contains("overdue") || reasonLower.contains("payment") && reasonLower.contains("due") -> PAYMENT_OVERDUE
                reasonLower.contains("payment") || reasonLower.contains("reminder") -> PAYMENT_REMINDER
                
                // Default
                else -> SERVER_RESTRICTION
            }
        }
        
        /**
         * Determine if violation should trigger hard lock instead of soft lock
         */
        fun shouldTriggerHardLock(triggerAction: String, reason: String): Boolean {
            val actionLower = triggerAction.lowercase()
            val reasonLower = reason.lowercase()
            
            return when {
                // Payment overdue always triggers hard lock
                reasonLower.contains("overdue") && reasonLower.contains("payment") -> true
                
                // Multiple security violations trigger hard lock
                actionLower.contains("root") -> true
                actionLower.contains("custom") && actionLower.contains("rom") -> true
                
                // Repeated violations (could be detected by server)
                reasonLower.contains("repeated") || reasonLower.contains("multiple") -> true
                
                // Critical security violations
                reasonLower.contains("critical") || reasonLower.contains("severe") -> true
                
                // Default to soft lock
                else -> false
            }
        }
    }
}