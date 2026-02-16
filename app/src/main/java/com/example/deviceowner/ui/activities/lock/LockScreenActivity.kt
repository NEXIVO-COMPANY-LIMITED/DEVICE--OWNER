package com.example.deviceowner.ui.activities.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.kiosk.PinManager
import com.example.deviceowner.ui.screens.lock.*

class LockScreenActivity : ComponentActivity() {

    companion object { private const val TAG = "LockScreenActivity" }

    private var dismissReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "LockScreenActivity created")
        registerDismissReceiver()
        val lockType = intent.getStringExtra("lock_type") ?: "UNKNOWN"
        Log.d(TAG, "Lock type: $lockType")
        setContent {
            MaterialTheme {
                Surface(color = Color.Black) {
                    when (lockType) {
                        "SOFT_LOCK_REMINDER" -> SoftLockReminderScreenWrapper()
                        "HARD_LOCK_PAYMENT" -> HardLockPaymentScreenWrapper()
                        "HARD_LOCK_SECURITY" -> HardLockSecurityScreenWrapper()
                        "DEACTIVATION" -> DeactivationScreenWrapper()
                        else -> { Log.e(TAG, "Unknown lock type: $lockType"); finish() }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterDismissReceiver()
    }

    private fun registerDismissReceiver() {
        dismissReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.deviceowner.DISMISS_LOCK_SCREEN") {
                    Log.d(TAG, "Dismiss broadcast received - closing lock screen")
                    finish()
                }
            }
        }
        val filter = IntentFilter("com.example.deviceowner.DISMISS_LOCK_SCREEN")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }
    }

    private fun unregisterDismissReceiver() {
        dismissReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { Log.w(TAG, "Error unregistering receiver: ${e.message}") }
        }
    }

    @Composable
    private fun SoftLockReminderScreenWrapper() {
        SoftLockReminderScreen(
            nextPaymentDate = intent.getStringExtra("next_payment_date") ?: "",
            daysUntilDue = intent.getLongExtra("days_until_due", 0L),
            hoursUntilDue = intent.getLongExtra("hours_until_due", 0L),
            minutesUntilDue = intent.getLongExtra("minutes_until_due", 0L),
            shopName = intent.getStringExtra("shop_name"),
            onDismiss = { finish() }
        )
    }

    @Composable
    private fun HardLockPaymentScreenWrapper() {
        var showPinEntry by remember { mutableStateOf(false) }
        var pinError by remember { mutableStateOf<String?>(null) }
        var isVerifying by remember { mutableStateOf(false) }
        val nextPaymentDate = intent.getStringExtra("next_payment_date") ?: ""
        val unlockPassword = intent.getStringExtra("unlock_password")
        val shopName = intent.getStringExtra("shop_name")
        val daysUntilDue = intent.getLongExtra("days_until_due", 0L)
        val hoursUntilDue = intent.getLongExtra("hours_until_due", 0L)
        if (showPinEntry) {
            PinEntryScreen(
                onPinSubmit = { enteredPin ->
                    isVerifying = true
                    pinError = null
                    if (PinManager.verifyPin(this@LockScreenActivity, enteredPin)) {
                        isVerifying = false
                        PinManager.clearPin(this@LockScreenActivity)
                        finish()
                    } else {
                        isVerifying = false
                        pinError = "Incorrect PIN. Please try again."
                    }
                },
                onCancel = { showPinEntry = false; pinError = null },
                isVerifying = isVerifying,
                errorMessage = pinError
            )
        } else {
            HardLockPaymentOverdueScreen(
                nextPaymentDate = nextPaymentDate,
                unlockPassword = unlockPassword,
                shopName = shopName,
                daysOverdue = if (daysUntilDue < 0) Math.abs(daysUntilDue) else 0L,
                hoursOverdue = if (hoursUntilDue < 0) Math.abs(hoursUntilDue) else 0L,
                onUnlockClick = { showPinEntry = true }
            )
        }
    }

    @Composable
    private fun HardLockSecurityScreenWrapper() {
        HardLockSecurityViolationScreen(
            reason = intent.getStringExtra("reason") ?: "Security violation detected",
            shopName = intent.getStringExtra("shop_name")
        )
    }

    @Composable
    private fun DeactivationScreenWrapper() {
        Surface(color = Color(0xFF0D0D0D)) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = "🔓", fontSize = 72.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "DEACTIVATING", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4CAF50))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Device Owner is being removed...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(color = Color(0xFF4CAF50), strokeWidth = 4.dp)
            }
        }
    }
}
