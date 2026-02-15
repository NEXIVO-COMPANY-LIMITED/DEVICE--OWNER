package com.example.deviceowner.services

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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.data.models.SoftLockType
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Persistent Soft Lock Overlay Service
 * Creates a fullscreen overlay for payment reminders (1 day before due).
 * Data from heartbeat: next_payment.date_time, organization name.
 * User can dismiss after acknowledging the message.
 */
class SoftLockOverlayService : Service() {

    companion object {
        private const val TAG = "SoftLockOverlay"

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
                val reason = intent.getStringExtra("lock_reason") ?: "Payment required"
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
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "soft_lock_channel"
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

                val channel = android.app.NotificationChannel(
                    channelId,
                    "Device Security Lock",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Payment reminder notifications"
                    setSound(null, null)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)

                android.app.Notification.Builder(this, channelId)
                    .setContentTitle("Payment Reminder Active")
                    .setContentText("Please review payment notification")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setContentTitle("Payment Reminder Active")
                    .setContentText("Please review payment notification")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .build()
            }

            startForeground(1001, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
        }
    }

    private fun startOverlay(
        reason: String,
        triggerAction: String,
        nextPaymentDate: String?,
        organizationName: String?
    ) {
        if (isOverlayActive) return

        val skipSecurityRestrictions = getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
            .getBoolean("skip_security_restrictions", false)
        if (skipSecurityRestrictions) {
            Log.d(TAG, "Skipping overlay during registration")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(this)
            ) {
                Log.w(TAG, "Overlay permission not granted")
                return
            }

            val prefs = SharedPreferencesManager(this)
            val orgName = organizationName?.takeIf { it.isNotBlank() }
                ?: prefs.getOrganizationName()
            val deviceId = prefs.getDeviceIdForHeartbeat() ?: Build.SERIAL.take(8).ifEmpty { "N/A" }

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
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

                format = android.graphics.PixelFormat.TRANSLUCENT
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }

            windowManager?.addView(overlayView, layoutParams)
            isOverlayActive = true
            Log.d(TAG, "Soft lock overlay started: $reason (next_payment=$nextPaymentDate)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay: ${e.message}", e)
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
            startActivity(Intent.createChooser(intent, "Contact support"))
        } catch (e: Exception) {
            Log.e(TAG, "Could not open support contact", e)
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
        try {
            if (isOverlayActive && overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayActive = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up overlay: ${e.message}")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 550.dp)
                ) {
                    StatusBadge()
                    Spacer(modifier = Modifier.height(24.dp))
                    PaymentIcon(pulseAlpha = pulseAlpha)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Payment Reminder",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF57C00),
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Your next payment is due soon. Please make a payment before the due date to continue using this device without restrictions.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF212121),
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WarningCard()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Contact $organizationName support or make a payment now to avoid device restrictions.",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    InfoSection(
                        nextPaymentDate = nextPaymentDate,
                        organizationName = organizationName,
                        deviceId = deviceId
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    ActionButtons(
                        isProcessing = isProcessing,
                        onAcknowledge = {
                            isProcessing = true
                            onDismiss()
                        },
                        onContactSupport = onContactSupport
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    FooterSection(organizationName = organizationName)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color(0xFFF57C00).copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFFF57C00), CircleShape)
                    .alpha(blinkAlpha)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Payment Due Soon",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF57C00)
            )
        }
    }
}

@Composable
private fun PaymentIcon(pulseAlpha: Float) {
    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(96.dp)
                .alpha(pulseAlpha * 0.3f),
            shape = CircleShape,
            color = Color(0xFFF57C00).copy(alpha = 0.2f)
        ) {}
        Surface(
            modifier = Modifier
                .size(80.dp)
                .alpha(pulseAlpha)
                .shadow(8.dp, CircleShape),
            shape = CircleShape,
            color = Color(0xFFF57C00)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = "Payment",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun WarningCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF57C00).copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(0.dp, Color(0xFFF57C00))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFF57C00))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Device access may be limited until payment is received. Some features and apps may be temporarily restricted.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun InfoSection(
    nextPaymentDate: String?,
    organizationName: String,
    deviceId: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InfoRow("Account Status", "Payment Due Soon", isWarning = true)
            InfoRow("Managed By", organizationName)
            InfoRow("Next Payment Due", nextPaymentDate ?: "—")
            InfoRow("Lock Time", SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
            InfoRow("Device ID", deviceId, showDivider = false)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isWarning: Boolean = false, showDivider: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF999999)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isWarning) Color(0xFFF57C00) else Color(0xFF212121)
            )
        }
        if (showDivider) HorizontalDivider(color = Color(0xFFE5E7EB))
    }
}

@Composable
private fun ActionButtons(
    isProcessing: Boolean,
    onAcknowledge: () -> Unit,
    onContactSupport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onAcknowledge,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF57C00),
                disabledContainerColor = Color(0xFFF57C00).copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 2.dp
            )
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Processing...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("I UNDERSTAND", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onContactSupport,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF57C00)),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF57C00)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("CONTACT SUPPORT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FooterSection(organizationName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = Color(0xFFE5E7EB))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This device is managed by $organizationName. All activity is monitored and logged. Make payment before the due date to restore full access to device features.",
            fontSize = 12.sp,
            color = Color(0xFF999999),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "POWERED BY $organizationName",
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFF57C00),
            letterSpacing = 1.sp
        )
    }
}
