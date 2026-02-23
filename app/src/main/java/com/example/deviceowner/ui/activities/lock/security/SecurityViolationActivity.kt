package com.example.deviceowner.ui.activities.lock.security

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.ui.activities.lock.base.BaseLockActivity
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import java.text.SimpleDateFormat
import java.util.*

private val RedBg = Color(0xFF1A0404)
private val RedCard = Color(0xFFE94560)

class SecurityViolationActivity : BaseLockActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reason = intent.getStringExtra("lock_reason") ?: "Security Violation"
        val lockedAt = intent.getLongExtra("lock_timestamp", System.currentTimeMillis())

        setContent {
            DeviceOwnerTheme {
                HardLockSecurityViolationScreen(
                    reason = reason,
                    lockedAt = lockedAt,
                    onOpenSettings = getSettingsAction(reason)
                )
            }
        }
        
        startLockTaskMode()
        acquireWakeLock("deviceowner:security_lock")
    }

    private fun getSettingsAction(reason: String): (() -> Unit)? {
        val lo = reason.lowercase()
        return when {
            lo.contains("developer") || lo.contains("usb") || lo.contains("adb") -> ({
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
            lo.contains("bootloader") -> ({
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            else -> null
        }
    }
}

@Composable
fun HardLockSecurityViolationScreen(
    reason: String,
    lockedAt: Long,
    onOpenSettings: (() -> Unit)?
) {
    val formattedTime = remember(lockedAt) {
        SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(lockedAt))
    }
    val formattedReason = formatSecurityReason(reason)
    val settingsLabel = getSettingsLabel(reason)
    val showSettingsBtn = onOpenSettings != null && settingsLabel != null

    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breatheA"
    )
    val scanX by rememberInfiniteTransition(label = "scan").animateFloat(
        -0.2f, 1.2f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "scanX"
    )
    val cursorBlink by rememberInfiniteTransition(label = "cur").animateFloat(
        1f, 0f,
        infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cur"
    )

    Box(
        Modifier.fillMaxSize().background(RedBg).drawBehind {
            drawCircle(
                RedCard.copy(alpha = 0.13f * breathe),
                radius = size.width * 1.2f,
                center = Offset(size.width / 2f, size.height * 0.35f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("⚠️", fontSize = 66.sp)
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "SECURITY VIOLATION",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, letterSpacing = 1.2.sp
                )
                Text(
                    "_",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = cursorBlink)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "DEVICE LOCKED",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.5f), letterSpacing = 0.6.sp
            )
            Spacer(Modifier.height(32.dp))

            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RedCard)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = scanX.coerceIn(0f, 1f))
                        .height(2.dp)
                        .align(Alignment.TopStart)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(0.5f), Color.Transparent)
                            )
                        )
                )
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "IMPORTANT NOTICE",
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(0.72f), letterSpacing = 1.6.sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                    Text(
                        formattedReason,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, textAlign = TextAlign.Center,
                        lineHeight = 22.sp, modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        "Contact your service provider or visit an authorized shop to unlock your device and restore full functionality.",
                        fontSize = 12.sp, color = Color.White.copy(0.78f),
                        textAlign = TextAlign.Center, lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            if (showSettingsBtn) {
                Button(
                    onClick = { onOpenSettings?.invoke() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(0.12f),
                        contentColor   = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    border = BorderStroke(1.dp, Color.White.copy(0.16f))
                ) {
                    Text(settingsLabel!!, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
            }

            Text(
                "Locked: $formattedTime",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, color = Color.White.copy(0.32f), letterSpacing = 0.4.sp
            )
        }
    }
}

private fun formatSecurityReason(reason: String): String {
    val lo = reason.lowercase()
    return when {
        lo.contains("developer") ->
            "Developer mode is enabled. This compromises device security. Please disable it in Settings or visit our shop to restore full security."
        lo.contains("usb") || lo.contains("adb") ->
            "USB debugging is enabled. Please disable it in Developer Settings or visit our shop for assistance."
        lo.contains("bootloader") ->
            "Bootloader is unlocked. Your device security has been compromised. Please visit our shop or service provider."
        lo.contains("root") ->
            "Unauthorized system modifications detected. Please visit our shop or service provider."
        lo.contains("tamper") ->
            "Device tampering detected. Please visit our shop immediately."
        reason.isNotBlank() -> reason
        else ->
            "Security violation detected. Please contact your service provider."
    }
}

private fun getSettingsLabel(reason: String): String? {
    val lo = reason.lowercase()
    return when {
        lo.contains("developer") || lo.contains("usb") || lo.contains("adb") ->
            "Open Developer Settings"
        lo.contains("bootloader") ->
            "Open System Settings"
        else -> null
    }
}
