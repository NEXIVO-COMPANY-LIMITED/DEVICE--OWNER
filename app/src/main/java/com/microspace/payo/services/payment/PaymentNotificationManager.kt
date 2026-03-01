package com.microspace.payo.services.payment

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Payment Notification Manager - SILENT VERSION
 * 
 * âœ… MODIFICATIONS:
 * - All notification sending is DISABLED to ensure zero user disturbance.
 * - Channels are created with MINIMUM importance.
 */
class PaymentNotificationManager(private val context: Context) {
    
    private val TAG = "PaymentNotificationMgr"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        private const val CHANNEL_ID_PAYMENT = "payment_reminders"
        private const val CHANNEL_ID_OVERDUE = "payment_overdue"
        private const val CHANNEL_ID_URGENT = "payment_urgent"
        
        private const val NOTIFICATION_ID_UPCOMING = 1001
        private const val NOTIFICATION_ID_DUE_SOON = 1002
        private const val NOTIFICATION_ID_OVERDUE = 1003
        private const val NOTIFICATION_ID_DATE_CHANGED = 1004
        private const val NOTIFICATION_ID_PAID = 1005
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Set all to MINIMUM importance so they don't show up on screen or make noise if they were to be sent
            val channels = listOf(
                NotificationChannel(CHANNEL_ID_PAYMENT, "System Updates", NotificationManager.IMPORTANCE_MIN),
                NotificationChannel(CHANNEL_ID_OVERDUE, "System Sync", NotificationManager.IMPORTANCE_MIN),
                NotificationChannel(CHANNEL_ID_URGENT, "Critical System", NotificationManager.IMPORTANCE_MIN)
            )
            channels.forEach { 
                it.setSound(null, null)
                it.enableVibration(false)
                it.setShowBadge(false)
                notificationManager.createNotificationChannel(it)
            }
            Log.d(TAG, "âœ… Silent notification channels created")
        }
    }
    
    fun sendUpcomingPaymentNotification(paymentDateTime: String, daysUntil: Int) {
        Log.d(TAG, "ðŸ”‡ Muted: Upcoming payment notification ($paymentDateTime)")
        // Disabled
    }
    
    fun sendDueSoonPaymentNotification(paymentDateTime: String, daysUntil: Int, hoursUntil: Int) {
        Log.d(TAG, "ðŸ”‡ Muted: Due soon notification ($paymentDateTime)")
        // Disabled
    }
    
    fun sendOverduePaymentNotification(paymentDateTime: String, overdueDays: Int, overdueHours: Int, lockReason: String) {
        Log.d(TAG, "ðŸ”‡ Muted: Overdue notification ($paymentDateTime)")
        // Disabled
    }
    
    fun sendPaymentDateChangedNotification(oldDate: String?, newDate: String) {
        Log.d(TAG, "ðŸ”‡ Muted: Date changed notification ($newDate)")
        // Disabled
    }
    
    fun sendPaymentReceivedNotification() {
        Log.d(TAG, "ðŸ”‡ Muted: Payment received notification")
        // Disabled
    }
    
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, com.microspace.payo.ui.activities.registration.RegistrationStatusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    fun cancelAllNotifications() {
        try {
            notificationManager.cancelAll()
            Log.d(TAG, "âœ… All notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notifications: ${e.message}")
        }
    }
}




