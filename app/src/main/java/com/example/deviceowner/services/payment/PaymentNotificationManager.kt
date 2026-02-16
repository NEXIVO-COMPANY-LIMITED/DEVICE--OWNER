package com.example.deviceowner.services.payment

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Payment Notification Manager
 * 
 * Handles sending notifications for:
 * - Upcoming payments
 * - Payments due soon
 * - Overdue payments
 * - Payment date changes
 * - Payment received
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
    
    /**
     * Create notification channels for different payment statuses
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Upcoming payment channel
            val upcomingChannel = NotificationChannel(
                CHANNEL_ID_PAYMENT,
                "Payment Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for upcoming payments"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(upcomingChannel)
            
            // Overdue payment channel
            val overdueChannel = NotificationChannel(
                CHANNEL_ID_OVERDUE,
                "Payment Overdue",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for overdue payments"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(overdueChannel)
            
            // Urgent channel
            val urgentChannel = NotificationChannel(
                CHANNEL_ID_URGENT,
                "Urgent Payment Alerts",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Urgent payment notifications"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(urgentChannel)
            
            Log.d(TAG, "✅ Notification channels created")
        }
    }
    
    /**
     * Send upcoming payment notification
     */
    fun sendUpcomingPaymentNotification(paymentDateTime: String, daysUntil: Int) {
        try {
            Log.d(TAG, "📤 Sending upcoming payment notification")
            
            val title = "💳 Payment Reminder"
            val message = "Your payment is due in $daysUntil days ($paymentDateTime)"
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_PAYMENT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(createPendingIntent())
                .build()
            
            notificationManager.notify(NOTIFICATION_ID_UPCOMING, notification)
            Log.d(TAG, "✅ Upcoming payment notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending upcoming payment notification: ${e.message}")
        }
    }
    
    /**
     * Send payment due soon notification
     */
    fun sendDueSoonPaymentNotification(paymentDateTime: String, daysUntil: Int, hoursUntil: Int) {
        try {
            Log.d(TAG, "📤 Sending due soon payment notification")
            
            val title = "⏰ Payment Due Soon!"
            val timeStr = when {
                daysUntil > 0 -> "$daysUntil days and $hoursUntil hours"
                hoursUntil > 0 -> "$hoursUntil hours"
                else -> "Less than 1 hour"
            }
            val message = "Your payment is due in $timeStr. Due date: $paymentDateTime"
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_PAYMENT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(createPendingIntent())
                .setVibrate(longArrayOf(0, 500, 250, 500))
                .build()
            
            notificationManager.notify(NOTIFICATION_ID_DUE_SOON, notification)
            Log.d(TAG, "✅ Due soon payment notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending due soon notification: ${e.message}")
        }
    }
    
    /**
     * Send overdue payment notification
     */
    fun sendOverduePaymentNotification(
        paymentDateTime: String,
        overdueDays: Int,
        overdueHours: Int,
        lockReason: String
    ) {
        try {
            Log.d(TAG, "📤 Sending overdue payment notification")
            
            val title = "🚨 Payment Overdue!"
            val timeStr = when {
                overdueDays > 0 -> "$overdueDays days and $overdueHours hours"
                overdueHours > 0 -> "$overdueHours hours"
                else -> "Less than 1 hour"
            }
            val message = "Your payment was due on $paymentDateTime. " +
                    "It is now $timeStr overdue. " +
                    "Reason: $lockReason. " +
                    "Please make payment immediately to avoid device lock."
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_URGENT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(false)
                .setContentIntent(createPendingIntent())
                .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
                .setLights(0xFFFF0000.toInt(), 1000, 1000)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID_OVERDUE, notification)
            Log.d(TAG, "✅ Overdue payment notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending overdue notification: ${e.message}")
        }
    }
    
    /**
     * Send payment date changed notification
     */
    fun sendPaymentDateChangedNotification(oldDate: String?, newDate: String) {
        try {
            Log.d(TAG, "📤 Sending payment date changed notification")
            
            val title = "📅 Payment Date Changed"
            val message = "Your payment date has been updated. " +
                    "Old date: $oldDate. " +
                    "New date: $newDate. " +
                    "Please check your payment schedule."
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_PAYMENT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(createPendingIntent())
                .build()
            
            notificationManager.notify(NOTIFICATION_ID_DATE_CHANGED, notification)
            Log.d(TAG, "✅ Payment date changed notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending date changed notification: ${e.message}")
        }
    }
    
    /**
     * Send payment received notification
     */
    fun sendPaymentReceivedNotification() {
        try {
            Log.d(TAG, "📤 Sending payment received notification")
            
            val title = "✅ Payment Received"
            val message = "Your payment has been received successfully. " +
                    "Your device will be unlocked shortly."
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_PAYMENT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(createPendingIntent())
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .build()
            
            notificationManager.notify(NOTIFICATION_ID_PAID, notification)
            Log.d(TAG, "✅ Payment received notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending payment received notification: ${e.message}")
        }
    }
    
    /**
     * Create pending intent for notification. Opens RegistrationStatusActivity (launcher).
     */
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, com.example.deviceowner.ui.activities.registration.RegistrationStatusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Cancel all payment notifications
     */
    fun cancelAllNotifications() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_UPCOMING)
            notificationManager.cancel(NOTIFICATION_ID_DUE_SOON)
            notificationManager.cancel(NOTIFICATION_ID_OVERDUE)
            notificationManager.cancel(NOTIFICATION_ID_DATE_CHANGED)
            notificationManager.cancel(NOTIFICATION_ID_PAID)
            Log.d(TAG, "✅ All notifications cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notifications: ${e.message}")
        }
    }
}
