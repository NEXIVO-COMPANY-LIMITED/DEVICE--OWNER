package com.example.deviceowner.ui.activities.lock.payment

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

private val Amber       = Color(0xFFF57C00)
private val AmberGold   = Color(0xFFFBBF24)
private val AmberLight  = Color(0x17F57C00)
private val AmberMid    = Color(0x33F57C00)
private val Gray50      = Color(0xFFF9FAFB)
private val Gray200     = Color(0xFFE5E7EB)
private val Gray400     = Color(0xFF9CA3AF)
private val Gray500     = Color(0xFF6B7280)
private val Gray900     = Color(0xFF111827)

class SoftLockReminderActivity : BaseLockActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nextPayDate = intent.getStringExtra("next_payment_date") ?: ""
        
        // Calculate remaining time precisely
        val remaining = calculateRemainingTime(nextPayDate)

        setContent {
            DeviceOwnerTheme {
                SoftLockReminderScreen(
                    nextPaymentDate  = nextPayDate,
                    daysUntilDue     = remaining.days,
                    hoursUntilDue    = remaining.hours,
                    minutesUntilDue  = remaining.minutes,
                    onDismiss        = { finish() }
                )
            }
        }
        acquireWakeLock("deviceowner:soft_lock")
    }

    private fun calculateRemainingTime(dateStr: String): TimeRemaining {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val targetDate = sdf.parse(dateStr) ?: return TimeRemaining(0, 0, 0)
            val diff = targetDate.time - System.currentTimeMillis()
            if (diff <= 0) return TimeRemaining(0, 0, 0)
            
            val days = diff / (24 * 60 * 60 * 1000)
            val hours = (diff / (60 * 60 * 1000)) % 24
            val minutes = (diff / (60 * 1000)) % 60
            TimeRemaining(days, hours, minutes)
        } catch (_: Exception) {
            TimeRemaining(0, 0, 0)
        }
    }

    private data class TimeRemaining(val days: Long, val hours: Long, val minutes: Long)
}

@Composable
fun SoftLockReminderScreen(
    nextPaymentDate: String,
    daysUntilDue: Long,
    hoursUntilDue: Long,
    minutesUntilDue: Long,
    onDismiss: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "soft")
    val pillAlpha by infinite.animateFloat(1f, 0.2f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pill")
    val rippleScale by infinite.animateFloat(1f, 1.5f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "rScale")
    val rippleAlpha by infinite.animateFloat(0.55f, 0f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "rAlpha")
    val floatY by infinite.animateFloat(0f, -6f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "floatY")

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(top = 72.dp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(999.dp), color = AmberLight) {
                Row(modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Amber).alpha(pillAlpha))
                    Text("Payment Reminder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber, letterSpacing = 0.4.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            Box(Modifier.size(120.dp).offset(y = floatY.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).scale(rippleScale).clip(CircleShape).background(AmberMid.copy(alpha = rippleAlpha)))
                Box(Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AmberGold, Amber), Offset(0f, 0f), Offset(96f, 96f))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Schedule, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }

            Spacer(Modifier.height(28.dp))

            Text("Upcoming Payment", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Gray900, textAlign = TextAlign.Center, letterSpacing = (-0.4).sp)
            Spacer(Modifier.height(12.dp))
            Text("Your next payment is due soon. Please ensure you have sufficient funds to avoid device restrictions.", fontSize = 15.sp, color = Gray500, textAlign = TextAlign.Center, lineHeight = 24.sp, modifier = Modifier.widthIn(max = 320.dp))

            Spacer(Modifier.height(44.dp))

            Text("DUE IN", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Gray400, letterSpacing = 2.2.sp)
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                SoftCountdownUnit(daysUntilDue, "Days")
                SoftCountdownColon()
                SoftCountdownUnit(hoursUntilDue, "Hours")
                SoftCountdownColon()
                SoftCountdownUnit(minutesUntilDue, "Mins")
            }

            Spacer(Modifier.height(44.dp))

            Surface(modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp), shape = RoundedCornerShape(14.dp), color = Gray50, border = BorderStroke(1.dp, Gray200)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Due date", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray500)
                    Text(nextPaymentDate, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Gray900)
                }
            }

            Spacer(Modifier.height(36.dp))

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp).height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Amber)) {
                Text("Dismiss Reminder", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun SoftCountdownUnit(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
        Text(value.toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, fontSize = 50.sp, fontWeight = FontWeight.SemiBold, color = Amber, letterSpacing = (-3).sp, lineHeight = 50.sp)
        Spacer(Modifier.height(5.dp))
        Text(label.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Gray400, letterSpacing = 1.6.sp)
    }
}

@Composable
private fun SoftCountdownColon() {
    Text(":", fontFamily = FontFamily.Monospace, fontSize = 38.sp, fontWeight = FontWeight.Thin, color = Gray200, modifier = Modifier.width(22.dp).padding(bottom = 16.dp), textAlign = TextAlign.Center)
}
