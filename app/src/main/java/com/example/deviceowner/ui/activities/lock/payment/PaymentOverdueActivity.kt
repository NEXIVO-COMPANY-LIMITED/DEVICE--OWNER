package com.example.deviceowner.ui.activities.lock.payment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.services.payment.PaymentLockManager
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.example.deviceowner.ui.activities.lock.base.BaseLockActivity
import kotlinx.coroutines.launch

private val RedCard     = Color(0xFFE94560)
private val RedLight    = Color(0x17E94560)
private val RedMid      = Color(0x29E94560)
private val Gray200     = Color(0xFFE5E7EB)
private val Gray500     = Color(0xFF6B7280)
private val Gray900     = Color(0xFF111827)
private val SlateBlue   = Color(0xFF94A3B8)

class PaymentOverdueActivity : BaseLockActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nextPayDate = intent.getStringExtra("next_payment_date")
        val daysOverdue = intent.getLongExtra("days_overdue", 0L)
        val hoursOverdue = intent.getLongExtra("hours_overdue", 0L)
        val supportContact = intent.getStringExtra("support_contact") ?: SharedPreferencesManager(this).getSupportContact() ?: ""

        setContent {
            DeviceOwnerTheme {
                HardLockPaymentOverdueScreen(
                    nextPaymentDate = nextPayDate,
                    daysOverdue = daysOverdue,
                    hoursOverdue = hoursOverdue,
                    onUnlockAttempt = { code -> PaymentLockManager(this).verifyOfflineUnlockPassword(code) },
                    onContactSupport = { openSupport(supportContact) }
                )
            }
        }
        
        startLockTaskMode()
        acquireWakeLock("deviceowner:payment_lock")
    }

    private fun openSupport(contact: String) {
        val uri = when {
            contact.isBlank()                              -> Uri.parse("tel:")
            contact.startsWith("tel:")                     -> Uri.parse(contact)
            contact.startsWith("mailto:")                  -> Uri.parse(contact)
            contact.contains("@") && !contact.contains(" ") -> Uri.parse("mailto:$contact")
            else -> Uri.parse(if (contact.contains("://")) contact else "https://$contact")
        }
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Contact support"
                )
            )
        }
    }
}

@Composable
fun HardLockPaymentOverdueScreen(
    nextPaymentDate: String?,
    daysOverdue: Long,
    hoursOverdue: Long,
    onUnlockAttempt: (String) -> Boolean,
    onContactSupport: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var code              by remember { mutableStateOf("") }
    var codeVisible       by remember { mutableStateOf(false) }
    var isVerifying       by remember { mutableStateOf(false) }
    var verificationState by remember { mutableStateOf<Boolean?>(null) }

    val infinite = rememberInfiniteTransition(label = "pay")

    val badgePulse by infinite.animateFloat(
        1f, 0.2f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "badge"
    )
    val rippleScale by infinite.animateFloat(
        1f, 1.5f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "rScale"
    )
    val rippleAlpha by infinite.animateFloat(
        0.5f, 0f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "rAlpha"
    )
    val floatY by infinite.animateFloat(
        0f, -6f,
        infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY"
    )

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Red badge ──────────────────────────────────────
            Surface(shape = RoundedCornerShape(999.dp), color = RedLight) {
                Row(
                    Modifier.padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(RedCard).alpha(badgePulse)
                    )
                    Text(
                        "Payment Overdue",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedCard
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Floating red lock icon ─────────────────────────
            Box(
                Modifier.size(116.dp).offset(y = floatY.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(116.dp).scale(rippleScale).clip(CircleShape)
                        .background(RedMid.copy(alpha = rippleAlpha))
                )
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(
                        Brush.linearGradient(
                            listOf(Color(0xFFf27d91), RedCard),
                            Offset.Zero, Offset(88f, 88f)
                        )
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Service Interrupted",
                fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                color = Gray900, letterSpacing = (-0.4).sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Your device is locked because the scheduled payment has not been received. Please complete your payment to restore access.",
                fontSize = 15.sp, color = Gray500,
                textAlign = TextAlign.Center, lineHeight = 24.sp,
                modifier = Modifier.widthIn(max = 340.dp)
            )

            Spacer(Modifier.height(28.dp))

            // ── Overdue chip ───────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0x0FE94560),
                border = BorderStroke(1.dp, Color(0x2EE94560))
            ) {
                Column(
                    Modifier.padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "OVERDUE SINCE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp, fontWeight = FontWeight.Medium,
                        color = RedCard.copy(alpha = 0.65f),
                        letterSpacing = 1.8.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        nextPaymentDate ?: "Payment Date",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        color = RedCard, letterSpacing = (-0.5).sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Input label ────────────────────────────────────
            Text(
                "OFFLINE UNLOCK CODE",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp, color = SlateBlue,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // ── Code input ─────────────────────────────────────
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.filter { c -> c.isDigit() }
                    verificationState = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Enter unlock code", color = SlateBlue, fontSize = 14.sp, fontWeight = FontWeight.Normal)
                },
                singleLine = true,
                visualTransformation = if (codeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                trailingIcon = {
                    IconButton(onClick = { codeVisible = !codeVisible }) {
                        Icon(
                            if (codeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (codeVisible) "Hide code" else "Show code",
                            tint = SlateBlue
                        )
                    }
                },
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFFAFBFD),
                    focusedContainerColor   = Color.White,
                    unfocusedBorderColor    = Gray200,
                    focusedBorderColor      = RedCard
                )
            )

            Spacer(Modifier.height(10.dp))

            // ── Unlock button ──────────────────────────────────
            Button(
                onClick = {
                    if (code.isEmpty()) return@Button
                    isVerifying = true
                    verificationState = null
                    scope.launch {
                        val ok = onUnlockAttempt(code)
                        verificationState = ok
                        isVerifying = false
                    }
                },
                enabled = code.isNotEmpty() && !isVerifying,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = RedCard,
                    disabledContainerColor = Gray200
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Verifying…", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("RESTORE ACCESS", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.8.sp)
                }
            }

            // ── Verification result ────────────────────────────
            AnimatedVisibility(visible = verificationState != null) {
                verificationState?.let { ok ->
                    Text(
                        if (ok) "✓ Unlock successful! Restoring device access…"
                        else    "✗ Invalid code. Please try again.",
                        color = if (ok) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Contact support ────────────────────────────────
            OutlinedButton(
                onClick = onContactSupport,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(2.dp, RedCard.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedCard)
            ) {
                Icon(Icons.Default.Phone, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("CONTACT SUPPORT", fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.5.sp)
            }
        }
    }
}
