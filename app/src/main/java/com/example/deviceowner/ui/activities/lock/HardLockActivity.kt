package com.example.deviceowner.ui.activities.lock

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen hard lock activity displayed when device is locked.
 * Prevents all user interaction and displays lock reason with polished UI.
 */
class HardLockActivity : ComponentActivity() {

    companion object {
        private const val TAG = "HardLockActivity"
        private const val LOCK_TASK_RETRY_DELAY_MS = 500L
    }

    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private var kioskNotActive = false
    private var monitoringThread: Thread? = null
    private var shouldMonitor = true
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenOnReceiver: ScreenOnReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin = ComponentName(this, AdminReceiver::class.java)
        
        // Configure window to prevent escape and block screenshots (standard for lock screens)
        window.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        
        val lockReason = intent.getStringExtra("lock_reason") ?: "Device Locked"
        val lockTimestamp = intent.getLongExtra("lock_timestamp", System.currentTimeMillis())
        val lockType = intent.getStringExtra("lock_type")
        val nextPaymentDate = intent.getStringExtra("next_payment_date")
        val organizationName = intent.getStringExtra("organization_name")
            ?: SharedPreferencesManager(this).getOrganizationName()
        val deviceId = SharedPreferencesManager(this).getDeviceIdForHeartbeat()
            ?: Build.SERIAL.take(8).ifEmpty { "N/A" }
        
        Log.e(TAG, "🔒 Hard Lock Activity Started - Reason: $lockReason")

        // Block back gesture / back button (standard API 33+; predictive back on Android 14+)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.w(TAG, "⚠️ Back gesture/button blocked")
            }
        })

        setContent {
            val displayType = parseLockType(lockReason, lockType)
            if (displayType == LockDisplayType.PAYMENT_OVERDUE) {
                DeviceOwnerTheme {
                    PaymentOverdueScreen(
                        reason = lockReason,
                        nextPaymentDate = nextPaymentDate,
                        organizationName = organizationName,
                        deviceId = deviceId,
                        lockTimestamp = lockTimestamp,
                        kioskNotActive = kioskNotActive,
                        onContactSupport = { openSupportContact() }
                    )
                }
            } else {
                HardLockScreen(lockReason, lockTimestamp, kioskNotActive, lockType, this)
            }
        }
        
        // Register receiver to bring hard lock screen to front when device is turned back on
        registerScreenOnReceiver()
        
        // Enforce kiosk mode: Lock Task Mode so user cannot leave the lock screen (gestures/assist cannot escape).
        startLockTaskWithRetry()
        
        // Start monitoring for security violation resolution
        startSecurityMonitoring(lockReason)
    }
    
    private fun startSecurityMonitoring(lockReason: String) {
        // Acquire WakeLock to keep monitoring thread alive even when screen is off
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "deviceowner:hardlock_monitoring"
            ).apply {
                acquire(10 * 60 * 1000) // 10 minute timeout, will be renewed if needed
            }
            Log.d(TAG, "✅ WakeLock acquired for security monitoring")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock: ${e.message}")
        }
        
        monitoringThread = Thread {
            while (shouldMonitor) {
                try {
                    // Use shorter interval (1 second) and keep thread alive even when screen is off
                    Thread.sleep(1000)
                    
                    // Check if the security violation has been resolved
                    if (isSecurityViolationResolved(lockReason)) {
                        Log.i(TAG, "✅ Security violation resolved! Unlocking device...")
                        shouldMonitor = false
                        unlockDevice()
                        break
                    }
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Monitoring thread interrupted")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring security: ${e.message}")
                }
            }
        }
        monitoringThread?.isDaemon = false  // Keep thread alive even if app goes to background
        monitoringThread?.start()
    }
    
    private fun isSecurityViolationResolved(lockReason: String): Boolean {
        return when {
            lockReason.contains("Developer", ignoreCase = true) -> {
                !isDeveloperModeEnabled()
            }
            lockReason.contains("USB", ignoreCase = true) || lockReason.contains("ADB", ignoreCase = true) -> {
                !isUsbDebuggingEnabled()
            }
            lockReason.contains("Bootloader", ignoreCase = true) -> {
                // Bootloader check would require device owner access
                false
            }
            lockReason.contains("Root", ignoreCase = true) -> {
                !isDeviceRooted()
            }
            else -> false
        }
    }
    
    private fun isDeveloperModeEnabled(): Boolean {
        return try {
            android.provider.Settings.Secure.getInt(
                contentResolver,
                android.provider.Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isUsbDebuggingEnabled(): Boolean {
        return try {
            android.provider.Settings.Secure.getInt(
                contentResolver,
                android.provider.Settings.Secure.ADB_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isDeviceRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.inputStream.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun unlockDevice() {
        try {
            stopLockTask()
            Log.i(TAG, "✅ Lock task mode stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping lock task: ${e.message}")
        }
        try {
            // Clear hard lock state (normal + device-protected) and unsuspend packages
            RemoteDeviceControlManager(this).unlockDevice()
            Log.i(TAG, "✅ Hard lock state cleared (including device-protected for boot)")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing lock state: ${e.message}")
        }
        try {
            runOnUiThread { finish() }
            Log.i(TAG, "✅ Device unlocked successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing activity: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        shouldMonitor = false
        monitoringThread?.interrupt()
        
        // Unregister screen-on receiver
        try {
            if (screenOnReceiver != null) {
                unregisterReceiver(screenOnReceiver)
                Log.d(TAG, "✅ Screen-on receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
        
        // Release WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "✅ WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }
    
    /**
     * Start lock task; retry once after delay if it fails. If still not in lock task mode, show "Contact support" for diagnostics.
     */
    private fun startLockTaskWithRetry() {
        fun tryStartLockTask(): Boolean {
            if (!dpm.isDeviceOwnerApp(packageName)) {
                Log.e(TAG, "Cannot start lock task: app is not device owner")
                return false
            }
            return try {
                startLockTask()
                if (isInLockTaskModeCompat()) {
                    Log.d(TAG, "Lock task mode active")
                    true
                } else {
                    Log.w(TAG, "startLockTask returned but lock task mode not active")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "startLockTask failed: ${e.message}", e)
                false
            }
        }
        if (tryStartLockTask()) return
        window.decorView.postDelayed({
            if (tryStartLockTask()) return@postDelayed
            Log.e(TAG, "Kiosk not active after retry – lock task mode could not be started. Contact support.")
            runOnUiThread {
                kioskNotActive = true
                val reason = intent.getStringExtra("lock_reason") ?: "Device Locked"
                val lockType = intent.getStringExtra("lock_type")
                val displayType = parseLockType(reason, lockType)
                setContent {
                    if (displayType == LockDisplayType.PAYMENT_OVERDUE) {
                        DeviceOwnerTheme {
                            PaymentOverdueScreen(
                                reason = reason,
                                nextPaymentDate = intent.getStringExtra("next_payment_date"),
                                organizationName = intent.getStringExtra("organization_name")
                                    ?: SharedPreferencesManager(this@HardLockActivity).getOrganizationName(),
                                deviceId = SharedPreferencesManager(this@HardLockActivity).getDeviceIdForHeartbeat()
                                    ?: Build.SERIAL.take(8).ifEmpty { "N/A" },
                                lockTimestamp = intent.getLongExtra("lock_timestamp", System.currentTimeMillis()),
                                kioskNotActive = true,
                                onContactSupport = { openSupportContact() }
                            )
                        }
                    } else {
                        HardLockScreen(
                            reason,
                            intent.getLongExtra("lock_timestamp", System.currentTimeMillis()),
                            true,
                            lockType,
                            this@HardLockActivity
                        )
                    }
                }
            }
        }, LOCK_TASK_RETRY_DELAY_MS)
    }

    private fun isInLockTaskModeCompat(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityManager?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
            } else {
                // For older APIs, check if we're in lock task mode using ActivityManager
                activityManager?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking lock task mode: ${e.message}")
            false
        }
    }

    private fun registerScreenOnReceiver() {
        try {
            screenOnReceiver = ScreenOnReceiver(this)
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOnReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenOnReceiver, filter)
            }
            Log.d(TAG, "✅ Screen-on receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen-on receiver: ${e.message}")
        }
    }
    
    /**
     * Inner receiver to bring hard lock screen to front when device is turned back on
     */
    private inner class ScreenOnReceiver(private val activity: HardLockActivity) : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON || intent?.action == Intent.ACTION_USER_PRESENT) {
                Log.d(TAG, "📱 Screen turned on - bringing hard lock to foreground")
                try {
                    // Bring this activity to the front
                    val bringToFrontIntent = Intent(context, HardLockActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context?.startActivity(bringToFrontIntent)
                    
                    // Turn screen on and dismiss keyguard
                    val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val screenWakeLock = pm?.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "deviceowner:hardlock_screen_on"
                    )
                    screenWakeLock?.acquire(1000) // 1 second
                    
                    Log.d(TAG, "✅ Hard lock screen brought to foreground")
                } catch (e: Exception) {
                    Log.e(TAG, "Error bringing hard lock to foreground: ${e.message}")
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Block hardware keys that could escape lock; allow in-screen UI (buttons) to work
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Log.w(TAG, "⚠️ Blocked key: $keyCode")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Delegate to content so lock screen buttons (e.g. Open Settings) remain clickable
        return super.onTouchEvent(event)
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
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            Log.w(TAG, "⚠️ Window lost focus - bringing back to foreground")
            // If window loses focus, bring it back
            window.decorView.postDelayed({
                try {
                    val intent = Intent(this, HardLockActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error bringing activity to front: ${e.message}")
                }
            }, 100)
        }
    }
}

/** Detects lock type from reason text for appropriate styling */
private fun parseLockType(reason: String, explicitType: String?): LockDisplayType {
    if (explicitType != null) {
        return when (explicitType.uppercase()) {
            "SECURITY_VIOLATION", "SECURITY" -> LockDisplayType.SECURITY_VIOLATION
            "PAYMENT_OVERDUE", "PAYMENT" -> LockDisplayType.PAYMENT_OVERDUE
            else -> LockDisplayType.GENERIC
        }
    }
    val lower = reason.lowercase()
    return when {
        lower.contains("security violation") || lower.contains("bootloader") ||
        lower.contains("tamper") || lower.contains("critical") || lower.contains("firmware") ||
        lower.contains("adb") || lower.contains("root") || lower.contains("developer mode") -> LockDisplayType.SECURITY_VIOLATION
        lower.contains("payment") || lower.contains("overdue") || lower.contains("loan") -> LockDisplayType.PAYMENT_OVERDUE
        else -> LockDisplayType.GENERIC
    }
}

private enum class LockDisplayType { SECURITY_VIOLATION, PAYMENT_OVERDUE, GENERIC }

/** Formats technical reason into user-friendly display text */
private fun formatReasonForDisplay(reason: String): String {
    return when {
        reason.contains("Bootloader", ignoreCase = true) -> "Bootloader is unlocked. Your device security has been compromised. Please visit our shop or your service provider to restore security and other features."
        reason.contains("USB", ignoreCase = true) || reason.contains("ADB", ignoreCase = true) -> "USB debugging is enabled. This is a security risk. Please disable it in Developer Settings or visit our shop for assistance."
        reason.contains("Developer", ignoreCase = true) -> "Developer mode is enabled. This compromises device security. Please disable it in Settings or visit our shop to restore full security."
        reason.contains("Root", ignoreCase = true) -> "Unauthorized system modifications detected. Your device has been rooted. Please visit our shop or service provider to restore device security and features."
        reason.contains("TAMPER", ignoreCase = true) -> "Device tampering detected. Your device security has been compromised. Please visit our shop immediately for assistance."
        reason.contains("Device Owner removed", ignoreCase = true) -> "Critical security breach detected. Device management has been removed. Please visit our shop or service provider immediately."
        reason.contains("FIRMWARE", ignoreCase = true) -> "Firmware security violation detected. Please visit our shop to restore device security."
        reason.contains("Custom ROM", ignoreCase = true) -> "Unauthorized operating system detected. Please visit our shop to restore the original secure system."
        else -> reason
    }
}

@Composable
private fun HardLockScreen(reason: String, timestamp: Long, kioskNotActive: Boolean = false, lockType: String? = null, context: Context? = null) {
    val lockTime = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp))
    val displayType = parseLockType(reason, lockType)
    val formattedReason = formatReasonForDisplay(reason)
    val settingsAction = if (context != null) getSettingsActionForReason(reason, context) else null
    
    val theme = when (displayType) {
        LockDisplayType.SECURITY_VIOLATION -> LockTheme(
            title = "SECURITY VIOLATION",
            subtitle = "DEVICE LOCKED",
            accentColor = Color(0xFFFFFFFF),
            bgColor = Color(0xFF240606),
            headerBgColor = Color(0xFF1a0404),
            cardBgColor = Color(0xFFe94560),
            iconEmoji = "⚠️"
        )
        LockDisplayType.PAYMENT_OVERDUE -> LockTheme(
            title = "PAYMENT OVERDUE",
            subtitle = "DEVICE LOCKED",
            accentColor = Color(0xFFFFFFFF),
            bgColor = Color(0xFF240606),
            headerBgColor = Color(0xFF1a0404),
            cardBgColor = Color(0xFFe94560),
            iconEmoji = "💳"
        )
        LockDisplayType.GENERIC -> LockTheme(
            title = "DEVICE LOCKED",
            subtitle = "Access Restricted",
            accentColor = Color(0xFFFFFFFF),
            bgColor = Color(0xFF1F2937),
            headerBgColor = Color(0xFF111827),
            cardBgColor = Color(0xFF374151),
            iconEmoji = "🔒"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Text(
                text = theme.iconEmoji,
                fontSize = 64.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Title
            Text(
                text = theme.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            
            // Subtitle
            Text(
                text = theme.subtitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Message card - Single page, no border
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                color = theme.cardBgColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "IMPORTANT NOTICE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.8.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = formattedReason,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Text(
                        text = "Contact your service provider or visit an authorized shop to unlock your device and restore full functionality.",
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Settings button if applicable
            if (settingsAction != null) {
                Button(
                    onClick = { settingsAction.openSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFFFFF),
                        contentColor = Color(0xFF240606)
                    )
                ) {
                    Text(
                        text = settingsAction.buttonText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Timestamp
            Text(
                text = "Locked: $lockTime",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            if (kioskNotActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "System mode issue. Please contact support.",
                    fontSize = 11.sp,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class LockTheme(
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val bgColor: Color,
    val headerBgColor: Color,
    val cardBgColor: Color,
    val iconEmoji: String = "🔒"
)

private data class SettingsAction(
    val buttonText: String,
    val openSettings: () -> Unit
)

/** Determines which settings page to open based on the security violation reason */
private fun getSettingsActionForReason(reason: String, context: Context): SettingsAction? {
    return when {
        reason.contains("Developer", ignoreCase = true) -> {
            SettingsAction("Open Developer Settings") {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to main settings
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                    fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(fallbackIntent)
                }
            }
        }
        reason.contains("USB", ignoreCase = true) || reason.contains("ADB", ignoreCase = true) -> {
            SettingsAction("Open Developer Settings") {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                    fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(fallbackIntent)
                }
            }
        }
        reason.contains("Bootloader", ignoreCase = true) -> {
            SettingsAction("Open System Settings") {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("HardLockActivity", "Could not open settings: ${e.message}")
                }
            }
        }
        else -> null
    }
}

/** Payment overdue screen – white PAYO-style design, Contact Support only (no dismiss) */
@Composable
private fun PaymentOverdueScreen(
    reason: String,
    nextPaymentDate: String?,
    organizationName: String,
    deviceId: String,
    lockTimestamp: Long,
    kioskNotActive: Boolean,
    onContactSupport: () -> Unit
) {
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
    val lockTime = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date(lockTimestamp))

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
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(initialOffsetY = { it / 4 })
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 550.dp)
                ) {
                    PaymentOverdueStatusBadge()
                    Spacer(modifier = Modifier.height(24.dp))
                    PaymentOverdueIcon(pulseAlpha = pulseAlpha)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Payment Overdue",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF57C00),
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your payment is overdue. Please make a payment to continue using this device without restrictions.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF212121),
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PaymentOverdueWarningCard()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Contact $organizationName support immediately to arrange payment and restore full device functionality.",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    PaymentOverdueInfoSection(
                        nextPaymentDate = nextPaymentDate,
                        organizationName = organizationName,
                        lockTime = lockTime,
                        deviceId = deviceId
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = onContactSupport,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF57C00)),
                        border = BorderStroke(2.dp, Color(0xFFF57C00)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("CONTACT SUPPORT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    PaymentOverdueFooter(organizationName = organizationName)
                    if (kioskNotActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "System mode issue. Please contact support.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFD700),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentOverdueStatusBadge() {
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
                text = "Payment Required",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF57C00)
            )
        }
    }
}

@Composable
private fun PaymentOverdueIcon(pulseAlpha: Float) {
    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(96.dp).alpha(pulseAlpha * 0.3f),
            shape = CircleShape,
            color = Color(0xFFF57C00).copy(alpha = 0.2f)
        ) {}
        Surface(
            modifier = Modifier.size(80.dp).alpha(pulseAlpha).shadow(8.dp, CircleShape),
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
private fun PaymentOverdueWarningCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF57C00).copy(alpha = 0.08f),
        border = BorderStroke(0.dp, Color(0xFFF57C00))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                text = "⚠️ Device access may be limited until payment is received. Some features and apps may be temporarily restricted.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun PaymentOverdueInfoSection(
    nextPaymentDate: String?,
    organizationName: String,
    lockTime: String,
    deviceId: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF9FAFB),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            PaymentOverdueInfoRow("Account Status", "Payment Overdue", isWarning = true)
            PaymentOverdueInfoRow("Managed By", organizationName)
            PaymentOverdueInfoRow("Next Payment Was Due", nextPaymentDate ?: "—")
            PaymentOverdueInfoRow("Lock Time", lockTime)
            PaymentOverdueInfoRow("Device ID", deviceId, showDivider = false)
        }
    }
}

@Composable
private fun PaymentOverdueInfoRow(label: String, value: String, isWarning: Boolean = false, showDivider: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
private fun PaymentOverdueFooter(organizationName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = Color(0xFFE5E7EB))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This device is managed by $organizationName. All activity is monitored and logged. Make payment to restore full access to device features.",
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
