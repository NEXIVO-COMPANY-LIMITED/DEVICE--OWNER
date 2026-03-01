package com.microspace.payo.ui.activities.lock.system

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
import com.microspace.payo.control.RemoteDeviceControlManager
import com.microspace.payo.data.DeviceIdProvider
import com.microspace.payo.services.reporting.ServerBugAndLogReporter
import com.microspace.payo.ui.activities.lock.base.BaseLockActivity
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import com.microspace.payo.services.heartbeat.HeartbeatService
import com.microspace.payo.services.heartbeat.HeartbeatWorker
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
    private var deactivationStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DeviceOwnerTheme {
                DeactivationScreen(
                    onDeactivationComplete = { 
                        if (!deactivationStarted) {
                            deactivationStarted = true
                            startDeactivationFlow() 
                        }
                    }
                )
            }
        }
        
        startLockTaskMode()
        acquireWakeLock("deviceowner:deactivation")
    }

    private fun startDeactivationFlow() {
        val deviceId = DeviceIdProvider.getDeviceId(this)
        
        // CRITICAL: Stop relaunching logic in BaseLockActivity
        isExitingForced = true
        
        // 1. Stop all heartbeat and monitoring services IMMEDIATELY
        Log.w("Deactivation", "ðŸ›‘ Stopping all monitoring services to prevent loop")
        HeartbeatService.stop(this)
        HeartbeatWorker.stop(this)
        
        // 2. Clear ALL possible deactivation/lock flags from ALL shared preferences
        // This ensures that if the app restarts, it won't think it's still supposed to deactivate or lock
        val prefsToClear = listOf(
            "heartbeat_state",
            "control_prefs",
            "device_deactivation",
            "device_lock",
            "device_lock_state",
            "heartbeat_response"
        )
        
        prefsToClear.forEach { name ->
            try {
                getSharedPreferences(name, MODE_PRIVATE).edit().clear().apply()
                Log.d("Deactivation", "âœ“ Cleared prefs: $name")
            } catch (e: Exception) {
                Log.e("Deactivation", "Error clearing $name: ${e.message}")
            }
        }
        
        // Set state to unlocked explicitly
        try {
            getSharedPreferences("control_prefs", MODE_PRIVATE).edit().putString("state", "unlocked").apply()
        } catch (e: Exception) {}

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 3. Send SUCCESS Log to Server BEFORE clearing DO
                Log.i("Deactivation", "Reporting success to server...")
                ServerBugAndLogReporter.postLog(
                    logType = "deactivation_success",
                    logLevel = "Info",
                    message = "Device deactivation sequence completed successfully for $deviceId. Removing Device Owner.",
                    extraData = mapOf("status" to "success")
                )
                
                // Wait for log to be sent
                delay(2000)

                // 4. Perform Clean-up (Unsuspend apps, clear restrictions)
                controlManager.clearAllPoliciesAndRestrictions()
                
                // 5. Final Deactivation
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
            Log.i("Deactivation", "Final Step: Clearing Device Owner status")
            
            // Exit LockTask mode before clearing DO
            try { stopLockTask() } catch (e: Exception) {}
            
            if (dpm.isDeviceOwnerApp(packageName)) {
                // This is the point of no return
                dpm.clearDeviceOwnerApp(packageName)
                Log.i("Deactivation", "âœ… Device owner removed successfully")
            }
            
            Log.i("Deactivation", "ðŸ Deactivation complete. Exiting activity.")
            
            // Use finishAndRemoveTask to clear from recents
            finishAndRemoveTask()
            
            // Force exit process to ensure all background loops and threads are killed immediately
            // This prevents any "zombie" services from trying to restart the activity
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
            
        } catch (e: Exception) {
            Log.e("Deactivation", "Error in final step: ${e.message}")
            finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    
    override fun onBackPressed() {
        // Disable back button during deactivation
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
            // Speed up the visual progress slightly for better UX
            while (progress < 1f) {
                delay(100)
                progress = minOf(1f, progress + (Math.random() * 0.04f).toFloat())
                if (progress > 0.30f && !logLine0Done) logLine0Done = true
                if (progress > 0.65f && !logLine1Done) logLine1Done = true
                if (progress >= 1f && !logLine2Done) { logLine2Done = true; isDone = true }
            }
            delay(1000)
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
                Text("ðŸ”“", fontSize = 72.sp)
            }

            Spacer(Modifier.height(32.dp))
            Text("DEACTIVATING", fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DeactGreen, letterSpacing = 4.sp)
            Text("System owner removal in progressâ€¦", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White.copy(0.3f))

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
                    DeactLine("Revoking admin privilegesâ€¦", logLine0Done)
                    DeactLine("Removing device policiesâ€¦", logLine1Done)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isDone) "âœ“" else "Â·", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (isDone) LogDone else LogPending, modifier = Modifier.width(20.dp))
                        Text(if (isDone) "COMPLETE â€” Reporting to server" else "Finalizing deactivation", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (isDone) LogDone else LogPending)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeactLine(text: String, done: Boolean) {
    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (done) "âœ“" else "Â·", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (done) LogDone else LogPending, modifier = Modifier.width(20.dp))
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (done) LogDone else LogPending)
    }
}




