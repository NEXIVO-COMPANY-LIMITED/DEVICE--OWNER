package com.example.deviceowner.ui.activities.lock.system

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.data.DeviceIdProvider
import com.example.deviceowner.services.reporting.ServerBugAndLogReporter
import com.example.deviceowner.ui.activities.lock.base.BaseLockActivity
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DeactGreen = Color(0xFF4CAF50)
private val DeactGreenDim = Color(0x1A4CAF50)
private val DeactGreenMid = Color(0x334CAF50)
private val DeactGreenGlow = Color(0x804CAF50)
private val DeactDarkBg = Color(0xFF0D0D0D)
private val LogBg = Color(0x73000000)
private val LogBorder = Color(0x1A4CAF50)
private val LogDone = Color(0xD94CAF50)
private val LogPending = Color(0x734CAF50)

class DeactivationActivity : BaseLockActivity() {

    private val controlManager by lazy { RemoteDeviceControlManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DeviceOwnerTheme {
                DeactivationScreen(
                    onDeactivationComplete = { startDeactivationFlow() }
                )
            }
        }
        
        startLockTaskMode()
        acquireWakeLock("deviceowner:deactivation")
    }

    private fun startDeactivationFlow() {
        val deviceId = DeviceIdProvider.getDeviceId(this)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Send SUCCESS Log to Server BEFORE clearing DO
                Log.i("Deactivation", "Reporting success to server...")
                ServerBugAndLogReporter.postLog(
                    logType = "deactivation_success",
                    logLevel = "Info",
                    message = "Device deactivation sequence completed successfully. Removing Device Owner.",
                    extraData = mapOf("status" to "success")
                )
                
                // Wait a bit for log to be sent
                delay(2000)

                // 2. Perform Clean-up
                controlManager.clearAllPoliciesAndRestrictions()
                
                // 3. Final Deactivation
                launch(Dispatchers.Main) {
                    performFinalDeactivation()
                }
            } catch (e: Exception) {
                Log.e("Deactivation", "Deactivation failed: ${e.message}")
                ServerBugAndLogReporter.postLog(
                    logType = "deactivation_failed",
                    logLevel = "Error",
                    message = "Deactivation failed: ${e.message}",
                    extraData = mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }

    private fun performFinalDeactivation() {
        try {
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.clearDeviceOwnerApp(packageName)
                Log.i("Deactivation", "✅ Device owner removed successfully")
            }
            stopLockTask()
            finishAndRemoveTask()
        } catch (e: Exception) {
            Log.e("Deactivation", "Error in final step: ${e.message}")
        }
    }
}

@Composable
fun DeactivationScreen(onDeactivationComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var logLine0Done by remember { mutableStateOf(false) }
    var logLine1Done by remember { mutableStateOf(false) }
    var logLine2Done by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            while (progress < 1f) {
                delay(120)
                progress = minOf(1f, progress + (Math.random() * 0.025f).toFloat())
                if (progress > 0.30f && !logLine0Done) logLine0Done = true
                if (progress > 0.65f && !logLine1Done) logLine1Done = true
                if (progress >= 1f && !logLine2Done) { logLine2Done = true; isDone = true }
            }
            delay(1500)
            onDeactivationComplete()
        }
    }

    val breathe by rememberInfiniteTransition(label = "").animateFloat(0.5f, 1f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "")
    val floatY by rememberInfiniteTransition(label = "").animateFloat(0f, -12f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "")
    val rotation by rememberInfiniteTransition(label = "").animateFloat(0f, 360f, infiniteRepeatable(tween(750, easing = LinearEasing)), label = "")

    Box(Modifier.fillMaxSize().background(DeactDarkBg).drawBehind {
        drawCircle(DeactGreen.copy(0.08f * breathe), radius = size.width * 1.2f, center = Offset(size.width / 2f, size.height / 2f))
    }) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).padding(top = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(130.dp).offset(y = floatY.dp), contentAlignment = Alignment.Center) {
                Text("🔓", fontSize = 72.sp)
            }

            Spacer(Modifier.height(32.dp))
            Text("DEACTIVATING", fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DeactGreen, letterSpacing = 4.sp)
            Text("System owner removal in progress…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White.copy(0.3f))

            Spacer(Modifier.height(48.dp))
            Column(Modifier.fillMaxWidth().widthIn(max = 300.dp)) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)).background(DeactGreenDim)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Brush.horizontalGradient(listOf(DeactGreen, DeactGreenGlow))))
                }
                Text("${(progress * 100).toInt()}%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DeactGreenGlow, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), textAlign = TextAlign.End)
            }

            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(modifier = Modifier.size(40.dp).rotate(rotation), color = DeactGreen, strokeWidth = 3.dp, trackColor = DeactGreenMid)

            Spacer(Modifier.height(40.dp))
            Surface(modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp), shape = RoundedCornerShape(12.dp), color = LogBg, border = BorderStroke(1.dp, LogBorder)) {
                Column(Modifier.padding(16.dp)) {
                    DeactLine("Revoking admin privileges…", logLine0Done)
                    DeactLine("Removing device policies…", logLine1Done)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isDone) "✓" else "·", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (isDone) LogDone else LogPending, modifier = Modifier.width(20.dp))
                        Text(if (isDone) "COMPLETE — Reporting to server" else "Finalizing deactivation", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (isDone) LogDone else LogPending)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeactLine(text: String, done: Boolean) {
    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (done) "✓" else "·", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (done) LogDone else LogPending, modifier = Modifier.width(20.dp))
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (done) LogDone else LogPending)
    }
}
