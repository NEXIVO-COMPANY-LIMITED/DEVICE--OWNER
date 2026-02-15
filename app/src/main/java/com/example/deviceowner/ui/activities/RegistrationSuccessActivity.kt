package com.example.deviceowner.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.core.SilentDeviceOwnerManager
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.security.CompleteSilentMode
import kotlinx.coroutines.delay
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.example.deviceowner.services.HeartbeatWorker
import java.util.concurrent.TimeUnit

/**
 * Enhanced Registration Success Activity with premium UI.
 * Shows success confirmation with animated elements and status information.
 * Starts background services for device monitoring and heartbeat.
 */
class RegistrationSuccessActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start monitoring immediately in the background
        startMonitoring()

        setContent {
            DeviceOwnerTheme {
                RegistrationSuccessScreen()
            }
        }
    }

    private fun startMonitoring() {
        val prefs = getSharedPreferences("device_registration", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)

        // 0. CLEANUP: Remove WiFi network configured during registration
        try {
            cleanupRegistrationWiFi()
            Log.i("RegistrationSuccess", "✅ Registration WiFi network removed")
        } catch (e: Exception) {
            Log.e("RegistrationSuccess", "Error cleaning up WiFi: ${e.message}")
        }

        // 1. Store device_id_for_heartbeat FIRST (before starting services)
        if (!deviceId.isNullOrBlank()) {
            getSharedPreferences("device_data", Context.MODE_PRIVATE).edit()
                .putString("device_id_for_heartbeat", deviceId).apply()
            com.example.deviceowner.utils.SharedPreferencesManager(this).setDeviceIdForHeartbeat(deviceId)
            Log.i("RegistrationSuccess", "✅ device_id_for_heartbeat saved: $deviceId")
        }

        // 2. Start foreground service (heartbeat will read device_id_for_heartbeat)
        try {
            com.example.deviceowner.monitoring.SecurityMonitorService.startService(this)
            Log.i("RegistrationSuccess", "✅ SecurityMonitorService started")
        } catch (e: Exception) {
            Log.e("RegistrationSuccess", "Error starting SecurityMonitorService: ${e.message}")
        }

        // 3. Apply device restrictions (silent – no user messages)
        try {
            val deviceOwnerManager = DeviceOwnerManager(this)
            if (deviceOwnerManager.isDeviceOwner()) {
                deviceOwnerManager.applyRestrictionsForSetupOnly()
                SilentDeviceOwnerManager(this).applySilentRestrictions()
                CompleteSilentMode(this).enableCompleteSilentMode()
                Log.d("RegistrationSuccess", "🔒 Silent restrictions applied – no messages to user")
            }
        } catch (e: Exception) {
            Log.e("RegistrationSuccess", "Error applying restrictions: ${e.message}")
        }

        // 4. Clear skip security restrictions flag
        try {
            getSharedPreferences("control_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("skip_security_restrictions", false)
                .putBoolean("registration_flow_complete", true).apply()
            Log.i("RegistrationSuccess", "🔒 Security restrictions enabled")
        } catch (e: Exception) {
            Log.e("RegistrationSuccess", "Error clearing skip flag: ${e.message}")
        }

        // 5. Enable and start heartbeat immediately (device_id already saved above)
        if (!deviceId.isNullOrBlank()) {
            try {
                // Enable heartbeat in preferences
                com.example.deviceowner.utils.SharedPreferencesManager(this).setHeartbeatEnabled(true)
                
                // Schedule immediate heartbeat via WorkManager
                val heartbeatWorkRequest = OneTimeWorkRequestBuilder<HeartbeatWorker>()
                    .setInitialDelay(0, TimeUnit.SECONDS)
                    .build()
                
                WorkManager.getInstance(this).enqueueUniqueWork(
                    "immediate_heartbeat",
                    ExistingWorkPolicy.REPLACE,
                    heartbeatWorkRequest
                )
                
                Log.i("RegistrationSuccess", "✅ Heartbeat enabled and scheduled immediately (device: $deviceId)")
            } catch (e: Exception) {
                Log.e("RegistrationSuccess", "Error starting heartbeat: ${e.message}", e)
            }
        }
    }

    private fun cleanupRegistrationWiFi() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val prefs = getSharedPreferences("device_registration", Context.MODE_PRIVATE)

            val registrationWiFiSSID = prefs.getString("registration_wifi_ssid", null)
            val provisioningWiFiSSID = prefs.getString("provisioning_wifi_ssid", null)
            val wifiSSIDsToRemove = listOfNotNull(registrationWiFiSSID, provisioningWiFiSSID).distinct()

            if (wifiSSIDsToRemove.isEmpty()) return

            val configuredNetworks = wifiManager.configuredNetworks
            configuredNetworks?.forEach { network ->
                val networkSSID = network.SSID?.replace("\"", "") ?: ""
                if (wifiSSIDsToRemove.any { it.equals(networkSSID, ignoreCase = true) }) {
                    wifiManager.removeNetwork(network.networkId)
                    Log.i("RegistrationSuccess", "✅ WiFi network removed: $networkSSID")
                }
            }

            wifiManager.saveConfiguration()
            prefs.edit()
                .remove("registration_wifi_ssid")
                .remove("registration_wifi_password")
                .remove("provisioning_wifi_ssid")
                .apply()
        } catch (e: Exception) {
            Log.e("RegistrationSuccess", "Error cleaning up WiFi: ${e.message}", e)
        }
    }
}

@Composable
private fun RegistrationSuccessScreen() {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Animate icon
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        // Fade in content
        delay(200)
        showContent = true
        alpha.animateTo(1f, animationSpec = tween(600))
        Log.i("RegistrationSuccess", "✅ Registration complete - app remains visible")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Main Content - Single page, no card wrapper
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Success Icon with Pulse Effect
            SuccessIcon(scale = scale.value)

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(600)) +
                        slideInVertically(initialOffsetY = { it / 4 })
            ) {
                Text(
                    text = "Registration Successful",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.alpha(alpha.value)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 200))
            ) {
                Text(
                    text = "Your device is now registered and managed by PAYO",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Badge
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 400))
            ) {
                StatusBadge()
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info Section - Compact
            InfoSection(visible = showContent)

            Spacer(modifier = Modifier.height(24.dp))

            // Footer
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 1000))
            ) {
                FooterSection()
            }
        }
    }
}

@Composable
private fun SuccessIcon(scale: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Pulse Ring
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(pulseScale)
                .background(Color(0xFFDCFCE7), CircleShape)
                .alpha(0.3f)
        )

        // Main Circle
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = Color(0xFFDCFCE7)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    modifier = Modifier.size(36.dp),
                    tint = Color(0xFF16A34A)
                )
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
        label = "blink"
    )

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color(0xFFDCFCE7)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF16A34A), CircleShape)
                    .alpha(blinkAlpha)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Active & Monitored",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF15803D)
            )
        }
    }
}

@Composable
private fun InfoSection(visible: Boolean) {
    val infoItems = listOf(
        InfoItem(Icons.Default.CheckCircle, "Security monitoring is active"),
        InfoItem(Icons.Default.Schedule, "Heartbeat service running"),
        InfoItem(Icons.Default.Lock, "Device policies applied"),
        InfoItem(Icons.Default.Description, "Device data synchronized")
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        infoItems.forEachIndexed { index, item ->
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(400, delayMillis = 600 + (index * 100))) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(400, delayMillis = 600 + (index * 100))
                        )
            ) {
                InfoCard(item = item)
            }
            if (index < infoItems.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun InfoCard(item: InfoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color(0xFF16A34A)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.text,
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FooterSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFE2E8F0)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Device is remotely managed for security and compliance",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "POWERED BY PAYO",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2),
            letterSpacing = 0.3.sp
        )
    }
}

private data class InfoItem(
    val icon: ImageVector,
    val text: String
)
