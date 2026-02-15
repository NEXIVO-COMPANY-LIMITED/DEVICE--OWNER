package com.example.deviceowner.ui.activities

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
import com.example.deviceowner.ui.screens.*

/**
 * Lock Screen Activity
 * 
 * Displays lock screens based on heartbeat response:
 * - SOFT_LOCK_REMINDER: Payment reminder (1 day before due)
 * - HARD_LOCK_PAYMENT: Payment overdue (on or after due date)
 * - HARD_LOCK_SECURITY: Security violation
 * - DEACTIVATION: Device deactivation in progress
 * 
 * For HARD_LOCK screens, this activity is shown with FLAG_ACTIVITY_NO_HISTORY
 * and FLAG_ACTIVITY_CLEAR_TOP to prevent user from navigating away.
 */
class LockScreenActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "LockScreenActivity"
    }
    
    private var dismissReceiver: BroadcastReceiver? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "LockScreenActivity created")
        
        // Register broadcast receiver to dismiss lock screen
        registerDismissReceiver()
        
        // Get lock type from intent
        val lockType = intent.getStringExtra("lock_type") ?: "UNKNOWN"
        
        Log.d(TAG, "Lock type: $lockType")
        
        setContent {
            MaterialTheme {
                Surface(color = Color.Black) {
                    when (lockType) {
                        "SOFT_LOCK_REMINDER" -> {
                            SoftLockReminderScreenWrapper()
                        }
                        "HARD_LOCK_PAYMENT" -> {
                            HardLockPaymentScreenWrapper()
                        }
                        "HARD_LOCK_SECURITY" -> {
                            HardLockSecurityScreenWrapper()
                        }
                        "DEACTIVATION" -> {
                            DeactivationScreenWrapper()
                        }
                        else -> {
                            Log.e(TAG, "Unknown lock type: $lockType")
                            finish()
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterDismissReceiver()
    }
    
    /**
     * Register broadcast receiver to dismiss lock screen
     */
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
    
    /**
     * Unregister broadcast receiver
     */
    private fun unregisterDismissReceiver() {
        dismissReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
    }
    
    /**
     * Soft Lock Reminder Screen Wrapper
     * Extracts data from intent and displays reminder screen
     */
    @Composable
    private fun SoftLockReminderScreenWrapper() {
        val nextPaymentDate = intent.getStringExtra("next_payment_date") ?: ""
        val daysUntilDue = intent.getLongExtra("days_until_due", 0L)
        val hoursUntilDue = intent.getLongExtra("hours_until_due", 0L)
        val minutesUntilDue = intent.getLongExtra("minutes_until_due", 0L)
        val shopName = intent.getStringExtra("shop_name")
        
        SoftLockReminderScreen(
            nextPaymentDate = nextPaymentDate,
            daysUntilDue = daysUntilDue,
            hoursUntilDue = hoursUntilDue,
            minutesUntilDue = minutesUntilDue,
            shopName = shopName,
            onDismiss = {
                Log.d(TAG, "Soft lock reminder dismissed by user")
                finish()
            }
        )
    }
    
    /**
     * Hard Lock Payment Screen Wrapper
     * Extracts data from intent and displays payment overdue screen
     * Handles PIN entry flow
     */
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
                    
                    // Verify PIN against local database
                    if (PinManager.verifyPin(this@LockScreenActivity, enteredPin)) {
                        Log.i(TAG, "✅ PIN verified successfully - unlocking device")
                        isVerifying = false
                        
                        // Clear PIN and close lock screen
                        PinManager.clearPin(this@LockScreenActivity)
                        finish()
                    } else {
                        Log.w(TAG, "❌ PIN verification failed")
                        isVerifying = false
                        pinError = "Incorrect PIN. Please try again."
                    }
                },
                onCancel = {
                    Log.d(TAG, "PIN entry cancelled")
                    showPinEntry = false
                    pinError = null
                },
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
                onUnlockClick = {
                    Log.d(TAG, "Unlock button clicked - showing PIN entry screen")
                    showPinEntry = true
                }
            )
        }
    }
    
    /**
     * Hard Lock Security Screen Wrapper
     * Extracts data from intent and displays security violation screen
     */
    @Composable
    private fun HardLockSecurityScreenWrapper() {
        val reason = intent.getStringExtra("reason") ?: "Security violation detected"
        val shopName = intent.getStringExtra("shop_name")
        
        HardLockSecurityViolationScreen(
            reason = reason,
            shopName = shopName
        )
    }
    
    /**
     * Deactivation Screen Wrapper
     * Shows deactivation in progress
     */
    @Composable
    private fun DeactivationScreenWrapper() {
        DeactivationInProgressScreen()
    }
    
    /**
     * Deactivation In Progress Screen
     */
    @Composable
    private fun DeactivationInProgressScreen() {
        Surface(color = Color(0xFF0D0D0D)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🔓",
                    fontSize = 72.sp
                )
                Spacer(
                    modifier = Modifier.height(24.dp)
                )
                Text(
                    text = "DEACTIVATING",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF4CAF50)
                )
                Spacer(
                    modifier = Modifier.height(16.dp)
                )
                Text(
                    text = "Device Owner is being removed...",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(
                    modifier = Modifier.height(32.dp)
                )
                CircularProgressIndicator(
                    color = Color(0xFF4CAF50),
                    strokeWidth = 4.dp
                )
            }
        }
    }
}
