package com.example.deviceowner.overlay

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.managers.IdentifierAuditLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Controller for managing overlay display and lifecycle
 * Feature 4.6: Pop-up Screens / Overlay UI
 */
class OverlayController(private val context: Context) {

    companion object {
        private const val TAG = "OverlayController"
    }

    private val auditLog = IdentifierAuditLog(context)

    /**
     * Initialize overlay system
     */
    fun initializeOverlaySystem() {
        try {
            Log.d(TAG, "Initializing overlay system")

            // Start overlay manager service
            val intent = Intent(context, OverlayManager::class.java)
            context.startService(intent)

            auditLog.logAction(
                "OVERLAY_SYSTEM_INITIALIZED",
                "Overlay system initialized"
            )

            Log.d(TAG, "✓ Overlay system initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing overlay system", e)
            auditLog.logIncident(
                type = "OVERLAY_INIT_ERROR",
                severity = "HIGH",
                details = "Failed to initialize overlay system: ${e.message}"
            )
        }
    }

    /**
     * Show payment reminder overlay
     */
    fun showPaymentReminder(
        amount: String,
        dueDate: String,
        loanId: String
    ) {
        try {
            Log.d(TAG, "Showing payment reminder overlay")

            val overlay = OverlayData(
                id = "payment_reminder_$loanId",
                type = OverlayType.PAYMENT_REMINDER,
                title = "Payment Reminder",
                message = "Amount Due: $amount\nDue Date: $dueDate",
                actionButtonText = "Acknowledge",
                actionButtonColor = 0xFF4CAF50.toInt(),
                dismissible = false,
                priority = 10,
                metadata = mapOf(
                    "loan_id" to loanId,
                    "amount" to amount,
                    "due_date" to dueDate
                )
            )

            showOverlay(overlay)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing payment reminder", e)
        }
    }

    /**
     * Show warning message overlay
     */
    fun showWarningMessage(
        title: String,
        message: String,
        warningId: String
    ) {
        try {
            Log.d(TAG, "Showing warning message overlay")

            val overlay = OverlayData(
                id = "warning_$warningId",
                type = OverlayType.WARNING_MESSAGE,
                title = title,
                message = message,
                actionButtonText = "Understood",
                actionButtonColor = 0xFFFF9800.toInt(),
                dismissible = false,
                priority = 8,
                metadata = mapOf(
                    "warning_id" to warningId
                )
            )

            showOverlay(overlay)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing warning message", e)
        }
    }

    /**
     * Show legal notice overlay
     */
    fun showLegalNotice(
        title: String,
        content: String,
        noticeId: String
    ) {
        try {
            Log.d(TAG, "Showing legal notice overlay")

            val overlay = OverlayData(
                id = "legal_notice_$noticeId",
                type = OverlayType.LEGAL_NOTICE,
                title = title,
                message = content,
                actionButtonText = "I Agree",
                actionButtonColor = 0xFF2196F3.toInt(),
                dismissible = false,
                priority = 9,
                metadata = mapOf(
                    "notice_id" to noticeId
                )
            )

            showOverlay(overlay)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing legal notice", e)
        }
    }

    /**
     * Show compliance alert overlay
     */
    fun showComplianceAlert(
        title: String,
        message: String,
        alertId: String,
        severity: String = "HIGH"
    ) {
        try {
            Log.d(TAG, "Showing compliance alert overlay")

            val buttonColor = when (severity) {
                "CRITICAL" -> 0xFFF44336.toInt()
                "HIGH" -> 0xFFFF5722.toInt()
                else -> 0xFFFF9800.toInt()
            }

            val overlay = OverlayData(
                id = "compliance_alert_$alertId",
                type = OverlayType.COMPLIANCE_ALERT,
                title = title,
                message = message,
                actionButtonText = "Acknowledge",
                actionButtonColor = buttonColor,
                dismissible = false,
                priority = 11,
                metadata = mapOf(
                    "alert_id" to alertId,
                    "severity" to severity
                )
            )

            showOverlay(overlay)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing compliance alert", e)
        }
    }

    /**
     * Show lock notification overlay
     */
    fun showLockNotification(
        reason: String,
        notificationId: String
    ) {
        try {
            Log.d(TAG, "Showing lock notification overlay")

            val overlay = OverlayData(
                id = "lock_notification_$notificationId",
                type = OverlayType.LOCK_NOTIFICATION,
                title = "Device Locked",
                message = "Reason: $reason",
                actionButtonText = "OK",
                actionButtonColor = 0xFFF44336.toInt(),
                dismissible = false,
                priority = 12,
                metadata = mapOf(
                    "notification_id" to notificationId,
                    "reason" to reason
                )
            )

            showOverlay(overlay)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing lock notification", e)
        }
    }

    /**
     * Show custom overlay
     */
    fun showCustomOverlay(overlay: OverlayData) {
        try {
            Log.d(TAG, "Showing custom overlay: ${overlay.id}")
            showOverlay(overlay)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing custom overlay", e)
        }
    }

    /**
     * Show overlay
     */
    fun showOverlay(overlay: OverlayData) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val intent = Intent(context, OverlayManager::class.java).apply {
                    action = "SHOW_OVERLAY"
                    putExtra("overlay_data", overlay.toJson())
                }
                context.startService(intent)

                auditLog.logAction(
                    "OVERLAY_REQUESTED",
                    "Overlay ${overlay.id} (${overlay.type}) requested"
                )

                Log.d(TAG, "Overlay ${overlay.id} requested")
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting overlay", e)
            }
        }
    }

    /**
     * Dismiss overlay
     */
    fun dismissOverlay(overlayId: String) {
        try {
            Log.d(TAG, "Dismissing overlay: $overlayId")

            val intent = Intent(context, OverlayManager::class.java).apply {
                action = "DISMISS_OVERLAY"
                putExtra("overlay_id", overlayId)
            }
            context.startService(intent)

            auditLog.logAction(
                "OVERLAY_DISMISS_REQUESTED",
                "Overlay $overlayId dismiss requested"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing overlay", e)
        }
    }

    /**
     * Clear all overlays
     */
    fun clearAllOverlays() {
        try {
            Log.d(TAG, "Clearing all overlays")

            val intent = Intent(context, OverlayManager::class.java).apply {
                action = "CLEAR_ALL_OVERLAYS"
            }
            context.startService(intent)

            auditLog.logAction(
                "ALL_OVERLAYS_CLEAR_REQUESTED",
                "All overlays clear requested"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing overlays", e)
        }
    }

    /**
     * Show overlay on boot
     */
    fun showOverlayOnBoot(overlay: OverlayData) {
        try {
            Log.d(TAG, "Showing overlay on boot: ${overlay.id}")

            // Save overlay to be shown on boot
            val prefs = context.getSharedPreferences("overlay_boot_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("boot_overlay_${overlay.id}", overlay.toJson()).apply()

            // Show overlay immediately
            showOverlay(overlay)

            auditLog.logAction(
                "BOOT_OVERLAY_SCHEDULED",
                "Overlay ${overlay.id} scheduled for boot display"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling boot overlay", e)
        }
    }

    /**
     * Load and show boot overlays
     */
    fun loadAndShowBootOverlays() {
        try {
            Log.d(TAG, "Loading boot overlays")

            val prefs = context.getSharedPreferences("overlay_boot_prefs", Context.MODE_PRIVATE)
            val allPrefs = prefs.all

            var bootOverlayCount = 0
            for ((key, value) in allPrefs) {
                if (key.startsWith("boot_overlay_") && value is String) {
                    val overlay = OverlayData.fromJson(value)
                    if (overlay != null && !overlay.isExpired()) {
                        showOverlay(overlay)
                        bootOverlayCount++
                    }
                }
            }

            Log.d(TAG, "Loaded and showing $bootOverlayCount boot overlays")

            auditLog.logAction(
                "BOOT_OVERLAYS_LOADED",
                "Loaded and displayed $bootOverlayCount boot overlays"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading boot overlays", e)
        }
    }
}
