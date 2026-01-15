package com.example.deviceowner.overlay

/**
 * Enum for different overlay types
 * Feature 4.6: Pop-up Screens / Overlay UI
 */
enum class OverlayType {
    PAYMENT_REMINDER,
    WARNING_MESSAGE,
    LEGAL_NOTICE,
    COMPLIANCE_ALERT,
    LOCK_NOTIFICATION,
    HARD_LOCK,           // Full device lock - blocks all interactions
    SOFT_LOCK,           // Warning lock - allows PIN unlock
    CUSTOM_MESSAGE
}
