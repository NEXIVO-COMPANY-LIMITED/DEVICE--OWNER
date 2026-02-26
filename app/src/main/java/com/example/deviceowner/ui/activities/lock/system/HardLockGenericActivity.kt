package com.microspace.payo.ui.activities.lock.system

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microspace.payo.ui.activities.lock.base.BaseLockActivity
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import java.text.SimpleDateFormat
import java.util.*

private val GreyBg = Color(0xFF1F2937)
private val GreyCard = Color(0xFF374151)

class HardLockGenericActivity : BaseLockActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reason = intent.getStringExtra("lock_reason") ?: "Access Restricted"
        val lockedAt = intent.getLongExtra("lock_timestamp", System.currentTimeMillis())

        setContent {
            DeviceOwnerTheme {
                HardLockGenericScreen(
                    reason = reason,
                    lockedAt = lockedAt
                )
            }
        }
        
        startLockTaskMode()
        acquireWakeLock("deviceowner:generic_lock")
    }
}

@Composable
fun HardLockGenericScreen(
    reason: String,
    lockedAt: Long
) {
    val formattedTime = remember(lockedAt) {
        SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(lockedAt))
    }

    Box(Modifier.fillMaxSize().background(GreyBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔒", fontSize = 66.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                "DEVICE LOCKED",
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = Color.White, letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Access Restricted",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(0.5f), letterSpacing = 0.6.sp
            )
            Spacer(Modifier.height(32.dp))

            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = GreyCard
            ) {
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
                        reason,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, textAlign = TextAlign.Center,
                        lineHeight = 22.sp, modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        "Please contact your service provider to restore device access.",
                        fontSize = 12.sp, color = Color.White.copy(0.8f),
                        textAlign = TextAlign.Center, lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "Locked: $formattedTime",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, color = Color.White.copy(0.32f), letterSpacing = 0.4.sp
            )
        }
    }
}
