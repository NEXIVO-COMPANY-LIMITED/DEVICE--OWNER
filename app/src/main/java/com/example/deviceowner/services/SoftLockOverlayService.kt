package com.example.deviceowner.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.deviceowner.R
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.data.models.SoftLockType

/**
 * Persistent Soft Lock Overlay Service
 * Creates a fullscreen overlay that cannot be dismissed by normal means
 * Monitors for unauthorized activities and prevents app manipulation
 */
class SoftLockOverlayService : Service() {
    
    companion object {
        private const val TAG = "SoftLockOverlay"
        
        fun startOverlay(context: Context, reason: String, triggerAction: String = "") {
            val intent = Intent(context, SoftLockOverlayService::class.java).apply {
                putExtra("lock_reason", reason)
                putExtra("trigger_action", triggerAction)
                action = "START_OVERLAY"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopOverlay(context: Context) {
            val intent = Intent(context, SoftLockOverlayService::class.java).apply {
                action = "STOP_OVERLAY"
            }
            context.startService(intent)
        }
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayActive = false
    private lateinit var controlManager: RemoteDeviceControlManager
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SoftLockOverlayService created")
        
        controlManager = RemoteDeviceControlManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground service immediately to avoid crash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService()
        }
        
        when (intent?.action) {
            "START_OVERLAY" -> {
                val reason = intent.getStringExtra("lock_reason") ?: "Device access restricted"
                val triggerAction = intent.getStringExtra("trigger_action") ?: ""
                startOverlay(reason, triggerAction)
            }
            "STOP_OVERLAY" -> {
                stopOverlay()
            }
        }
        
        return START_STICKY // Restart service if killed
    }
    
    private fun startForegroundService() {
        try {
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "soft_lock_channel"
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                
                // Create notification channel
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Device Security Lock",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Device security monitoring and lock notifications"
                    setSound(null, null)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)
                
                android.app.Notification.Builder(this, channelId)
                    .setContentTitle("Device Security Active")
                    .setContentText("Device access is currently restricted")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setOngoing(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setContentTitle("Device Security Active")
                    .setContentText("Device access is currently restricted")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setOngoing(true)
                    .build()
            }
            
            startForeground(1001, notification)
            Log.d(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
        }
    }
    
    private fun startOverlay(reason: String, triggerAction: String) {
        if (isOverlayActive) {
            Log.d(TAG, "Overlay already active")
            return
        }
        
        try {
            // Check if we have overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Overlay permission not granted - cannot create soft lock overlay")
                // Still keep the service running for monitoring
                return
            }
            
            // Determine soft lock type based on trigger action and reason
            val lockType = SoftLockType.fromTriggerAction(triggerAction, reason)
            
            // Create overlay view with specific lock type
            overlayView = createOverlayView(lockType, reason)
            
            // Create layout parameters for fullscreen overlay
            val layoutParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                
                // Use appropriate window type based on Android version and permissions
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                
                // Flags to make overlay persistent and block interactions
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            
            // Add overlay to window manager
            windowManager?.addView(overlayView, layoutParams)
            isOverlayActive = true
            
            Log.d(TAG, "Soft lock overlay started: $reason (Type: ${lockType.name})")
            
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied for overlay: ${e.message}")
            // Continue running service for monitoring even without overlay
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay: ${e.message}", e)
        }
    }
    
    private fun createOverlayView(lockType: SoftLockType, reason: String): View {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.soft_lock_overlay, null)
        
        // Customize UI based on lock type
        customizeOverlayForLockType(view, lockType, reason)
        
        // Set current time
        val timeText = view.findViewById<TextView>(R.id.tvLockTime)
        val currentTime = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        timeText.text = "Restricted at: $currentTime"
        
        // Handle dismiss button (only works if server allows)
        val dismissButton = view.findViewById<Button>(R.id.btnDismiss)
        dismissButton.text = lockType.buttonText
        dismissButton.setOnClickListener {
            // Check if soft lock can be dismissed
            if (canDismissSoftLock()) {
                Log.d(TAG, "User acknowledged soft lock overlay: ${lockType.name}")
                stopOverlay()
            } else {
                Log.w(TAG, "Soft lock dismissal blocked - persistent mode active")
                // Show message that dismissal is not allowed
                showPersistentMessage(lockType)
            }
        }
        
        // Handle contact support button
        val supportButton = view.findViewById<Button>(R.id.btnContactSupport)
        supportButton.setOnClickListener {
            // TODO: Implement contact support functionality
            Log.d(TAG, "User requested support contact for: ${lockType.name}")
        }
        
        return view
    }
    
    private fun customizeOverlayForLockType(view: View, lockType: SoftLockType, reason: String) {
        // Update title
        val titleText = view.findViewById<TextView>(R.id.tvTitle)
        titleText.text = lockType.title
        
        // Update primary message
        val primaryMessageText = view.findViewById<TextView>(R.id.tvPrimaryMessage)
        primaryMessageText.text = lockType.primaryMessage
        // Convert Compose Color to Android Color
        val colorInt = when (lockType) {
            SoftLockType.UNINSTALL_ATTEMPT,
            SoftLockType.DATA_CLEAR_ATTEMPT,
            SoftLockType.USB_DEBUG_ATTEMPT,
            SoftLockType.DEVELOPER_MODE_ATTEMPT,
            SoftLockType.SECURITY_VIOLATION,
            SoftLockType.ROOT_DETECTION,
            SoftLockType.CUSTOM_ROM_DETECTION -> 0xFFD32F2F.toInt()
            SoftLockType.PAYMENT_OVERDUE,
            SoftLockType.PAYMENT_REMINDER -> 0xFFF57C00.toInt()
            else -> 0xFF757575.toInt()
        }
        primaryMessageText.setTextColor(colorInt)
        
        // Update warning message
        val warningMessageText = view.findViewById<TextView>(R.id.tvWarningMessage)
        warningMessageText.text = lockType.warningMessage
        
        // Update action advice
        val actionAdviceText = view.findViewById<TextView>(R.id.tvActionAdvice)
        actionAdviceText.text = lockType.actionAdvice
        
        // Set lock reason (original reason from server)
        val reasonText = view.findViewById<TextView>(R.id.tvLockReason)
        reasonText.text = reason
        
        // Update icon color based on lock type
        val iconView = view.findViewById<ImageView>(R.id.ivLockIcon)
        iconView.setColorFilter(colorInt)
        
        // Set background color tint based on severity
        val backgroundOverlay = view.findViewById<View>(R.id.backgroundOverlay)
        when (lockType) {
            SoftLockType.UNINSTALL_ATTEMPT,
            SoftLockType.DATA_CLEAR_ATTEMPT,
            SoftLockType.USB_DEBUG_ATTEMPT,
            SoftLockType.DEVELOPER_MODE_ATTEMPT,
            SoftLockType.SECURITY_VIOLATION,
            SoftLockType.ROOT_DETECTION,
            SoftLockType.CUSTOM_ROM_DETECTION -> {
                // High severity - red tint
                backgroundOverlay.setBackgroundColor(0x33FF0000)
            }
            SoftLockType.PAYMENT_OVERDUE,
            SoftLockType.PAYMENT_REMINDER -> {
                // Medium severity - orange tint
                backgroundOverlay.setBackgroundColor(0x33FF8000)
            }
            else -> {
                // Low severity - default dark tint
                backgroundOverlay.setBackgroundColor(0x66000000)
            }
        }
    }
    
    private fun canDismissSoftLock(): Boolean {
        // Check if device is still in soft lock state
        val lockState = controlManager.getLockState()
        return lockState != RemoteDeviceControlManager.LOCK_SOFT
    }
    
    private fun showPersistentMessage(lockType: SoftLockType) {
        try {
            val messageText = overlayView?.findViewById<TextView>(R.id.tvPersistentMessage)
            messageText?.visibility = View.VISIBLE
            
            // Customize persistent message based on lock type
            val persistentMessage = when (lockType) {
                SoftLockType.UNINSTALL_ATTEMPT -> 
                    "This application CANNOT be uninstalled. It is required for device management and loan compliance."
                SoftLockType.DATA_CLEAR_ATTEMPT -> 
                    "Application data CANNOT be cleared. This would disrupt device management and violate your agreement."
                SoftLockType.USB_DEBUG_ATTEMPT -> 
                    "USB debugging CANNOT be enabled. This poses security risks and is strictly prohibited."
                SoftLockType.DEVELOPER_MODE_ATTEMPT -> 
                    "Developer options CANNOT be accessed. These settings are restricted under your device agreement."
                SoftLockType.ROOT_DETECTION -> 
                    "Device rooting is STRICTLY PROHIBITED. Please restore device to original state immediately."
                SoftLockType.CUSTOM_ROM_DETECTION -> 
                    "Custom firmware is NOT PERMITTED. Please restore original manufacturer firmware."
                else -> 
                    "This restriction cannot be dismissed. Please contact support or resolve the underlying issue."
            }
            
            messageText?.text = persistentMessage
            
            // Hide message after 8 seconds for security violations, 5 seconds for others
            val hideDelay = if (lockType in listOf(
                SoftLockType.UNINSTALL_ATTEMPT,
                SoftLockType.DATA_CLEAR_ATTEMPT,
                SoftLockType.USB_DEBUG_ATTEMPT,
                SoftLockType.DEVELOPER_MODE_ATTEMPT,
                SoftLockType.ROOT_DETECTION,
                SoftLockType.CUSTOM_ROM_DETECTION
            )) 8000L else 5000L
            
            messageText?.postDelayed({
                messageText.visibility = View.GONE
            }, hideDelay)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing persistent message: ${e.message}")
        }
    }
    
    private fun stopOverlay() {
        try {
            if (isOverlayActive && overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayActive = false
                Log.d(TAG, "Soft lock overlay stopped")
            }
            
            // Stop foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping overlay: ${e.message}", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SoftLockOverlayService destroyed")
        
        // Clean up overlay
        try {
            if (isOverlayActive && overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayActive = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up overlay in onDestroy: ${e.message}")
        }
    }
}