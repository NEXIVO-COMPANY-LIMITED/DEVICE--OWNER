package com.example.deviceowner.ui.activities.overlay

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.security.monitoring.sim.SIMChangeDetector
import com.example.deviceowner.security.monitoring.sim.SIMStatus
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import kotlinx.coroutines.delay

/**
 * SIM CHANGE OVERLAY ACTIVITY
 *
 * Full-screen security alert when SIM card is changed.
 * Shows original vs new SIM info with PAYO branding.
 * Auto-dismisses after 10 seconds or user can dismiss manually.
 */
class SIMChangeOverlayActivity : ComponentActivity() {

    private lateinit var simDetector: SIMChangeDetector

    companion object {
        private const val ORGANIZATION_NAME = "PAYO"
        private const val AUTO_DISMISS_SECONDS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        simDetector = SIMChangeDetector(this)

        setContent {
            DeviceOwnerTheme {
                SIMChangeOverlayScreen(
                    simDetector = simDetector,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun SIMChangeOverlayScreen(
    simDetector: SIMChangeDetector,
    onDismiss: () -> Unit
) {
    val simStatus = simDetector.getSIMStatus()
    var countdown by remember { mutableStateOf(10) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true

        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        onDismiss()
    }

    // Pulsing animation for warning icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF300707),
                        Color(0xFF1a0404)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp),
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WarningIconSection(pulseAlpha = pulseAlpha, pulseScale = pulseScale)

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "SIM CARD CHANGED",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Unauthorized SIM card detected on this device",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OrganizationCard()

                    Spacer(modifier = Modifier.height(24.dp))

                    SIMDetailsCard(simStatus = simStatus)

                    Spacer(modifier = Modifier.height(24.dp))

                    if (simStatus.changeCount > 1) {
                        AlertBadge(changeCount = simStatus.changeCount)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = "Auto-closing in ${countdown}s",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DismissButton(onDismiss = onDismiss)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "This security event has been logged and reported to PAYO administrators.\nYour device location and activity are being monitored.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningIconSection(pulseAlpha: Float, pulseScale: Float) {
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(140.dp)
                .alpha(pulseAlpha * 0.3f),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f)
        ) {}

        Surface(
            modifier = Modifier
                .size(120.dp)
                .alpha(pulseAlpha),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.2f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color.White,
                    modifier = Modifier
                        .size(72.dp)
                        .alpha(pulseAlpha)
                )
            }
        }
    }
}

@Composable
private fun OrganizationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Managed",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "This device is managed by",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "PAYO",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun SIMDetailsCard(simStatus: SIMStatus) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.3f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = null,
                    tint = Color(0xFF300707),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SIM Card Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF300707)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE0E0E0))
            Spacer(modifier = Modifier.height(16.dp))

            SIMInfoSection(
                title = "Original SIM (Registered)",
                icon = Icons.Default.CheckCircle,
                iconColor = Color(0xFF4CAF50),
                operator = simStatus.originalSIM.operatorName,
                phoneNumber = simStatus.originalSIM.phoneNumber
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Changed",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SIMInfoSection(
                title = "Current SIM (Unauthorized)",
                icon = Icons.Default.Error,
                iconColor = Color(0xFF300707),
                operator = simStatus.currentSIM.operatorName,
                phoneNumber = simStatus.currentSIM.phoneNumber,
                isUnauthorized = true
            )
        }
    }
}

@Composable
private fun SIMInfoSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    operator: String,
    phoneNumber: String,
    isUnauthorized: Boolean = false
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isUnauthorized) Color(0xFF300707) else Color(0xFF757575)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "NETWORK OPERATOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9E9E9E),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = operator,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF212121)
                )
            }

            if (phoneNumber.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PHONE NUMBER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9E9E9E),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = phoneNumber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF212121)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertBadge(changeCount: Int) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color.White.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SIM changed $changeCount times",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    Button(
        onClick = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = Color.Black.copy(alpha = 0.2f)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF300707)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "DISMISS ALERT",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
