package com.microspace.payo.ui.activities.overlay

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microspace.payo.security.monitoring.sim.SIMChangeDetector
import com.microspace.payo.security.monitoring.sim.SIMStatus
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import kotlinx.coroutines.delay

class SIMChangeOverlayActivity : ComponentActivity() {

    private lateinit var simDetector: SIMChangeDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SECURE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        simDetector = SIMChangeDetector(this)

        setContent {
            DeviceOwnerTheme {
                SIMChangeOverlayScreen(
                    simStatus = simDetector.getSIMStatus(),
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun SIMChangeOverlayScreen(
    simStatus: SIMStatus,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableStateOf(15) }
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

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infinite.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse))

    Box(Modifier.fillMaxSize().background(Color(0xFF1A0404))) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(visible = showContent, enter = fadeIn() + slideInVertically { it / 2 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(140.dp).clip(CircleShape).background(Color.White.copy(0.05f)))
                        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(72.dp).alpha(pulseAlpha))
                    }

                    Spacer(Modifier.height(32.dp))
                    Text("SIM CARD CHANGED", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
                    Text("Unauthorized SIM detected", fontSize = 15.sp, color = Color.White.copy(0.6f), modifier = Modifier.padding(top = 8.dp))

                    Spacer(Modifier.height(40.dp))
                    
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White.copy(0.08f), border = BorderStroke(1.dp, Color.White.copy(0.1f))) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SECURITY NOTICE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.5f), letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(16.dp))
                            SIMInfoRow("REGISTERED", simStatus.originalSIM.operatorName, Icons.Default.CheckCircle, Color(0xFF4CAF50))
                            Spacer(Modifier.height(16.dp))
                            Icon(Icons.Default.SwapVert, null, tint = Color.White.copy(0.3f))
                            Spacer(Modifier.height(16.dp))
                            SIMInfoRow("UNAUTHORIZED", simStatus.currentSIM.operatorName, Icons.Default.Error, Color(0xFFE94560))
                        }
                    }

                    Spacer(Modifier.height(40.dp))
                    Text("This event has been logged and reported.", fontSize = 12.sp, color = Color.White.copy(0.5f), textAlign = TextAlign.Center)
                    
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                        Text("DISMISS ALERT (${countdown}s)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SIMInfoRow(label: String, value: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
    }
}
