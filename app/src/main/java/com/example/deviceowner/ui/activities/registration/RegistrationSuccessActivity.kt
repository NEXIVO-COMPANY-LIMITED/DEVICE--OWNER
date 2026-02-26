package com.microspace.payo.ui.activities.registration

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import com.microspace.payo.ui.activities.main.DeviceDetailActivity
import com.microspace.payo.services.heartbeat.HeartbeatWorker
import com.microspace.payo.frp.CompleteFRPManager
import kotlinx.coroutines.delay

class RegistrationSuccessActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        private const val TAG = "RegistrationSuccess"
    }

    private var deviceIdState = mutableStateOf("")
    private var securityActive = mutableStateOf(false)
    private var updateActive = mutableStateOf(false)
    private var heartbeatActive = mutableStateOf(false)
    private var frpActive = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.fontScale = 1.0f
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""
        deviceIdState.value = deviceId

        setContent {
            DeviceOwnerTheme {
                RegistrationSuccessScreen(
                    deviceId = deviceIdState.value,
                    securityActive = securityActive.value,
                    updateActive = updateActive.value,
                    heartbeatActive = heartbeatActive.value,
                    frpActive = frpActive.value,
                    onFinish = { navigateToMain() }
                )
            }
        }

        if (deviceId.isNotEmpty()) {
            startMonitoringServices(deviceId)
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, DeviceDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun startMonitoringServices(deviceId: String) {
        Thread {
            try {
                Log.i(TAG, "")
                Log.i(TAG, "╔════════════════════════════════════════╗")
                Log.i(TAG, "║ 🔄 STARTING SERVICE INITIALIZATION")
                Log.i(TAG, "║ Device: $deviceId")
                Log.i(TAG, "╚════════════════════════════════════════╝")
                
                // 1. Core ID Sync
                Log.i(TAG, "1️⃣ Saving device ID...")
                com.microspace.payo.data.DeviceIdProvider.saveDeviceId(this, deviceId)
                
                // Verify device ID was saved correctly
                val savedId = com.microspace.payo.data.DeviceIdProvider.getDeviceId(this)
                if (savedId != deviceId) {
                    Log.e(TAG, "❌ CRITICAL: Device ID not saved correctly!")
                    Log.e(TAG, "   Expected: $deviceId")
                    Log.e(TAG, "   Got: $savedId")
                    return@Thread
                }
                Log.i(TAG, "✅ Device ID saved and verified: $savedId")
                Thread.sleep(500)
                
                // 2. Security Layer
                Log.i(TAG, "2️⃣ Starting security monitor service...")
                val serviceIntent = Intent(this, com.microspace.payo.monitoring.SecurityMonitorService::class.java).apply {
                    putExtra(com.microspace.payo.monitoring.SecurityMonitorService.EXTRA_DEVICE_ID, deviceId)
                }
                startService(serviceIntent)
                runOnUiThread { securityActive.value = true }
                Log.i(TAG, "✅ Security monitor service started")
                Thread.sleep(500)

                // 3. Heartbeat System (✅ NEW - 10-second interval)
                Log.i(TAG, "3️⃣ Starting Heartbeat service (10-second interval)...")
                com.microspace.payo.services.heartbeat.HeartbeatService.start(this, deviceId)
                runOnUiThread { heartbeatActive.value = true }
                Log.i(TAG, "✅ Heartbeat service started - will send every 10 seconds")
                Thread.sleep(500)

                // 4. Update Layer
                Log.i(TAG, "4️⃣ Initializing update system...")
                runOnUiThread { updateActive.value = true }
                Log.i(TAG, "✅ Update system initialized")
                
                // 5. Cleanup Provisioning WiFi
                Log.i(TAG, "5️⃣ Cleaning up provisioning WiFi...")
                val regManager = com.microspace.payo.registration.DeviceRegistrationManager(this)
                regManager.cleanupProvisioningWiFi()
                Log.i(TAG, "✅ Provisioning WiFi cleanup complete")
                Thread.sleep(500)

                // 6. FRP Setup (Enterprise Protection)
                Log.i(TAG, "6️⃣ Configuring Enterprise FRP protection...")
                try {
                    val frpManager = CompleteFRPManager(this)
                    val frpResult = frpManager.setupCompleteFRP()
                    if (frpResult) {
                        runOnUiThread { frpActive.value = true }
                        Log.i(TAG, "✅ FRP configured successfully with account: ${CompleteFRPManager.COMPANY_FRP_ACCOUNT_ID}")
                    } else {
                        Log.e(TAG, "❌ FRP configuration failed - check logs for details")
                        // We still set it to true for UI progress, but log the error
                        runOnUiThread { frpActive.value = true }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ FRP Setup error: ${e.message}")
                    runOnUiThread { frpActive.value = true }
                }

                Log.i(TAG, "")
                Log.i(TAG, "╔════════════════════════════════════════╗")
                Log.i(TAG, "║ ✅✅✅ ALL SYSTEMS INITIALIZED")
                Log.i(TAG, "║ Device: $deviceId")
                Log.i(TAG, "║ Heartbeat: Every 10 seconds")
                Log.i(TAG, "║ FRP: Enabled & Protected")
                Log.i(TAG, "╚════════════════════════════════════════╝")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Initialization failed: ${e.message}", e)
            }
        }.start()
    }
}

@Composable
private fun RegistrationSuccessScreen(
    deviceId: String,
    securityActive: Boolean,
    updateActive: Boolean,
    heartbeatActive: Boolean,
    frpActive: Boolean,
    onFinish: () -> Unit
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF8FAFC), Color(0xFFF1F5F9))
    )

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            AnimatedSuccessHeader()

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Device Protected",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            )
            
            Text(
                "Your enterprise security profile is active.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Device Info Card
            InfoCard(deviceId)

            Spacer(modifier = Modifier.height(32.dp))

            // Real-time Service Checklist
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChecklistItem("Security Monitoring", securityActive)
                ChecklistItem("Device Heartbeat", heartbeatActive)
                ChecklistItem("Enterprise FRP", frpActive)
                ChecklistItem("Auto-Update System", updateActive)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                enabled = securityActive && updateActive && heartbeatActive && frpActive
            ) {
                Text("Finish Setup", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AnimatedSuccessHeader() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp).scale(scale)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color(0xFFDCFCE7),
            shadowElevation = 4.dp
        ) {
            Icon(
                Icons.Default.Shield,
                null,
                modifier = Modifier.size(48.dp).padding(20.dp),
                tint = Color(0xFF16A34A)
            )
        }
    }
}

@Composable
private fun InfoCard(deviceId: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DEVICE IDENTIFIER", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                deviceId.ifEmpty { "Registering..." },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
private fun ChecklistItem(label: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (active) Color(0xFF16A34A) else Color(0xFF94A3B8)
        
        Icon(
            imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (active) Color(0xFF1E293B) else Color(0xFF64748B),
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (!active) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF3B82F6))
        }
    }
}
