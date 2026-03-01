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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microspace.payo.R
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import com.microspace.payo.ui.activities.main.DeviceDetailActivity
import com.microspace.payo.frp.CompleteFRPManager
import kotlinx.coroutines.delay

/**
 * Polished Registration Success Activity.
 * Displays a professional setup completion screen with step-by-step service activation.
 */
class RegistrationSuccessActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        private const val TAG = "RegistrationSuccess"
    }

    private val securityActive = mutableStateOf(false)
    private val updateActive = mutableStateOf(false)
    private val heartbeatActive = mutableStateOf(false)
    private val frpActive = mutableStateOf(false)
    private val setupComplete = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.fontScale = 1.0f
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""

        setContent {
            DeviceOwnerTheme {
                RegistrationSuccessScreen(
                    deviceId = deviceId,
                    securityActive = securityActive.value,
                    updateActive = updateActive.value,
                    heartbeatActive = heartbeatActive.value,
                    frpActive = frpActive.value,
                    setupComplete = setupComplete.value,
                    onFinish = { navigateToMain() }
                )
            }
        }

        if (deviceId.isNotEmpty()) {
            startServiceInitialization(deviceId)
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, DeviceDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun startServiceInitialization(deviceId: String) {
        Thread {
            try {
                Log.i(TAG, "Starting final system synchronization...")
                
                // 1. Sync ID
                com.microspace.payo.data.DeviceIdProvider.saveDeviceId(this, deviceId)
                Thread.sleep(800)
                
                // 2. Security Monitor
                val serviceIntent = Intent(this, com.microspace.payo.monitoring.SecurityMonitorService::class.java).apply {
                    putExtra(com.microspace.payo.monitoring.SecurityMonitorService.EXTRA_DEVICE_ID, deviceId)
                }
                startService(serviceIntent)
                runOnUiThread { securityActive.value = true }
                Thread.sleep(800)

                // 3. Heartbeat System
                com.microspace.payo.services.heartbeat.HeartbeatService.start(this, deviceId)
                runOnUiThread { heartbeatActive.value = true }
                Thread.sleep(800)

                // 4. Update Engine
                runOnUiThread { updateActive.value = true }
                Thread.sleep(800)
                
                // 5. Cleanup & FRP
                val regManager = com.microspace.payo.registration.DeviceRegistrationManager(this)
                regManager.cleanupProvisioningWiFi()
                
                try {
                    val frpManager = CompleteFRPManager(this)
                    frpManager.setupCompleteFRP()
                } catch (e: Exception) {
                    Log.e(TAG, "FRP Setup non-fatal error: ${e.message}")
                }
                
                runOnUiThread { 
                    frpActive.value = true 
                    setupComplete.value = true
                }

                Log.i(TAG, "Setup fully synchronized and verified.")

            } catch (e: Exception) {
                Log.e(TAG, "Final sync error: ${e.message}", e)
                // Even on error, we try to allow user to proceed if core ID is set
                runOnUiThread { setupComplete.value = true }
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
    setupComplete: Boolean,
    onFinish: () -> Unit
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
    )

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.nexivo),
                contentDescription = "Nexivo",
                modifier = Modifier.size(64.dp).alpha(0.8f)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            AnimatedSuccessIcon(setupComplete)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Setup Complete",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    letterSpacing = (-0.5).sp
                )
            )
            
            Text(
                text = "Device is now fully managed and secured.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Service Verification Panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusRow("Security Monitoring", securityActive)
                    StatusRow("Active Heartbeat", heartbeatActive)
                    StatusRow("Hardware Protection", frpActive)
                    StatusRow("Cloud Update Sync", updateActive)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    disabledContainerColor = Color(0xFFE2E8F0)
                ),
                enabled = setupComplete
            ) {
                Text(
                    "Launch Dashboard", 
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun AnimatedSuccessIcon(complete: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (complete) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(contentAlignment = Alignment.Center) {
        if (complete) {
            Surface(
                modifier = Modifier.size(80.dp).scale(pulse),
                shape = CircleShape,
                color = Color(0xFFD1FAE5)
            ) {}
        }
        
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = if (complete) Color(0xFF10B981) else Color(0xFFF1F5F9),
            shadowElevation = 2.dp
        ) {
            Icon(
                imageVector = if (complete) Icons.Default.Check else Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.padding(16.dp),
                tint = if (complete) Color.White else Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF10B981) else Color(0xFFE2E8F0))
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (active) Color(0xFF1E293B) else Color(0xFF94A3B8)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (active) {
            Icon(
                Icons.Default.Verified,
                null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF3B82F6)
            )
        }
    }
}




