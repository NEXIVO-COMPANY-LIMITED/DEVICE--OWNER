package com.microspace.payo.services.lock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microspace.payo.data.models.lock.SoftLockType
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import com.microspace.payo.utils.storage.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Persistent Soft Lock Overlay Service
 * Creates a fullscreen overlay for security reminders (Payment or SIM Change).
 * Data from heartbeat or local detectors.
 */
class SoftLockOverlayService : Service() {

    companion object {
        private const val TAG = "SoftLockOverlay"

        fun start(context: Context, message: String) {
            startOverlay(context, message)
        }

        fun stop(context: Context) {
            stopOverlay(context)
        }

        fun startOverlay(
            context: Context,
            reason: String,
            triggerAction: String = "",
            nextPaymentDate: String? = null,
            organizationName: String? = null
        ) {
            val intent = Intent(context, SoftLockOverlayService::class.java).apply {
                putExtra("lock_reason", reason)
                putExtra("trigger_action", triggerAction)
                putExtra("next_payment_date", nextPaymentDate)
                putExtra("organization_name", organizationName)
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

    private var windowManager: android.view.WindowManager? = null
    private var overlayView: ComposeView? = null
    private var isOverlayActive = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SoftLockOverlayService created")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService()
        }

        when (intent?.action) {
            "START_OVERLAY" -> {
                val reason = intent.getStringExtra("lock_reason") ?: "Security alert"
                val triggerAction = intent.getStringExtra("trigger_action") ?: ""
                val nextPaymentDate = intent.getStringExtra("next_payment_date")
                val organizationName = intent.getStringExtra("organization_name")
                startOverlay(reason, triggerAction, nextPaymentDate, organizationName)
            }
            "STOP_OVERLAY" -> stopOverlay()
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        try {
            val channelId = "soft_lock_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Device Security Status",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Security monitoring status"
                    setSound(null, null)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(this, channelId)
                    .setContentTitle("Security Monitor Active")
                    .setContentText("Device is protected by organization policy")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setOngoing(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setContentTitle("Security Monitor Active")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setOngoing(true)
                    .build()
            }

            startForeground(1001, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification: ${e.message}")
        }
    }

    private fun startOverlay(
        reason: String,
        triggerAction: String,
        nextPaymentDate: String?,
        organizationName: String?
    ) {
        if (isOverlayActive) {
            Log.d(TAG, "Overlay already active, updating content...")
            stopOverlay() 
        }

        val skipSecurityRestrictions = getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
            .getBoolean("skip_security_restrictions", false)
        if (skipSecurityRestrictions) {
            Log.d(TAG, "Skipping overlay during initial setup/registration")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(this)
            ) {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW permission missing")
                return
            }

            val prefs = SharedPreferencesManager(this)
            val orgName = organizationName?.takeIf { it.isNotBlank() }
                ?: prefs.getOrganizationName()?.ifBlank { "PAYO" } ?: "PAYO"
            val deviceId = prefs.getDeviceIdForHeartbeat() ?: "Device Managed"

            val lockType = SoftLockType.fromTriggerAction(triggerAction, reason)
            
            overlayView = ComposeView(this).apply {
                setContent {
                    DeviceOwnerTheme {
                        SoftLockOverlayScreen(
                            lockType = lockType,
                            reason = reason,
                            nextPaymentDate = nextPaymentDate,
                            organizationName = orgName,
                            deviceId = deviceId,
                            onDismiss = { stopOverlay() },
                            onContactSupport = { openSupportContact() }
                        )
                    }
                }
            }

            val layoutParams = android.view.WindowManager.LayoutParams().apply {
                width = android.view.WindowManager.LayoutParams.MATCH_PARENT
                height = android.view.WindowManager.LayoutParams.MATCH_PARENT

                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }

                flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

                format = android.graphics.PixelFormat.TRANSLUCENT
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            windowManager?.addView(overlayView, layoutParams)
            isOverlayActive = true
            Log.d(TAG, "Soft lock overlay started for type: ${lockType.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay view: ${e.message}", e)
        }
    }

    private fun openSupportContact() {
        val contact = SharedPreferencesManager(this).getSupportContact()?.trim()
        val uri = when {
            contact.isNullOrBlank() -> Uri.parse("tel:")
            contact.lowercase().startsWith("tel:") -> Uri.parse(contact)
            contact.lowercase().startsWith("mailto:") -> Uri.parse(contact)
            contact.contains("@") && !contact.contains(" ") -> Uri.parse("mailto:$contact")
            else -> Uri.parse(if (contact.contains("://")) contact else "https://$contact")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open support contact: ${e.message}")
        }
    }

    private fun stopOverlay() {
        try {
            if (isOverlayActive && overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayActive = false
                Log.d(TAG, "Soft lock overlay removed")
            }
            
            getSharedPreferences("control_prefs", Context.MODE_PRIVATE).edit()
                .putString("state", "unlocked").apply()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }

            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error during overlay removal: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayActive) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun SoftLockOverlayScreen(
    lockType: SoftLockType,
    reason: String,
    nextPaymentDate: String?,
    organizationName: String,
    deviceId: String,
    onDismiss: () -> Unit,
    onContactSupport: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        showContent = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(600)) +
                    slideInVertically(initialOffsetY = { it / 4 })
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp)
                ) {
                    StatusBadge(lockType)
                    Spacer(modifier = Modifier.height(24.dp))
                    LockIcon(lockType = lockType, pulseAlpha = pulseAlpha)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = lockType.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = lockType.color,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (lockType == SoftLockType.PAYMENT_REMINDER && reason.contains("Payment Reminder")) "Payment Reminder" else lockType.primaryMessage,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // FIX: Use the 'reason' parameter if it contains detailed info, otherwise fallback to default warning
                    val displayMessage = if (reason.isNotBlank() && reason.length > 10 && !reason.equals("Security alert", true)) {
                        reason
                    } else {
                        lockType.warningMessage
                    }

                    Text(
                        text = displayMessage,
                        fontSize = 16.sp,
                        color = Color(0xFF424242),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    WarningCard(lockType)
                    Spacer(modifier = Modifier.height(20.dp))

                    InfoSection(
                        lockType = lockType,
                        nextPaymentDate = nextPaymentDate,
                        organizationName = organizationName,
                        deviceId = deviceId
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))

                    ActionButtons(
                        lockType = lockType,
                        isProcessing = isProcessing,
                        onAcknowledge = {
                            isProcessing = true
                            onDismiss()
                        },
                        onContactSupport = onContactSupport
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    FooterSection(organizationName = organizationName)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(lockType: SoftLockType) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = lockType.color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(lockType.color, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Security Notification",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = lockType.color
            )
        }
    }
}

@Composable
private fun LockIcon(lockType: SoftLockType, pulseAlpha: Float) {
    Box(contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(90.dp).alpha(pulseAlpha * 0.2f),
            shape = CircleShape,
            color = lockType.color
        ) {}
        Surface(
            modifier = Modifier.size(70.dp).shadow(10.dp, CircleShape),
            shape = CircleShape,
            color = lockType.color
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = lockType.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun WarningCard(lockType: SoftLockType) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = lockType.color.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, lockType.color.copy(alpha = 0.2f))
    ) {
        Text(
            text = lockType.actionAdvice,
            fontSize = 14.sp,
            color = Color(0xFF616161),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun InfoSection(
    lockType: SoftLockType,
    nextPaymentDate: String?,
    organizationName: String,
    deviceId: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfoRow("Status", if (lockType == SoftLockType.SIM_CHANGE) "Tamper Alert" else "Payment Pending", isAlert = true, color = lockType.color)
        InfoRow("Organization", organizationName)
        if (lockType == SoftLockType.PAYMENT_REMINDER) {
            InfoRow("Due Date", nextPaymentDate ?: "Check App")
        }
        InfoRow("Device ID", deviceId.take(12), showDivider = false)
    }
}

@Composable
private fun InfoRow(label: String, value: String, isAlert: Boolean = false, color: Color = Color.Gray, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, color = Color(0xFF757575))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAlert) color else Color(0xFF212121)
            )
        }
        if (showDivider) HorizontalDivider(color = Color(0xFFEEEEEE))
    }
}

@Composable
private fun ActionButtons(
    lockType: SoftLockType,
    isProcessing: Boolean,
    onAcknowledge: () -> Unit,
    onContactSupport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onAcknowledge,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = lockType.color),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(lockType.buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onContactSupport,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, lockType.color),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = lockType.color),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("CONTACT SUPPORT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FooterSection(organizationName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "This device is protected by Nexivo Security for $organizationName",
            fontSize = 11.sp,
            color = Color(0xFF9E9E9E),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFE0E0E0), modifier = Modifier.size(20.dp))
    }
}
