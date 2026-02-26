package com.microspace.payo.ui.screens

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import java.text.SimpleDateFormat
import java.util.*

class PaymentOverdueActivity : AppCompatActivity() {
    
    private val TAG = "PaymentOverdueActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d(TAG, "")
            Log.d(TAG, "╔════════════════════════════════════════════════════════════╗")
            Log.d(TAG, "║ 💰 PAYMENT OVERDUE ACTIVITY CREATED")
            Log.d(TAG, "║ Timestamp: ${System.currentTimeMillis()}")
            Log.d(TAG, "╚════════════════════════════════════════════════════════════╝")
            
            // Set window flags for security
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
            
            // Prevent screen capture
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Extract intent data
            val reason = intent.getStringExtra("reason") ?: "Payment overdue"
            val lockType = intent.getStringExtra("lock_type") ?: "OVERDUE"
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val automatic = intent.getBooleanExtra("automatic", false)
            
            Log.d(TAG, "✅ Intent data extracted:")
            Log.d(TAG, "   Reason: $reason")
            Log.d(TAG, "   Lock Type: $lockType")
            Log.d(TAG, "   Automatic: $automatic")
            
            // Get payment info from SharedPreferences
            val prefs = getSharedPreferences("heartbeat_state", Context.MODE_PRIVATE)
            val lockReason = prefs.getString("lock_reason", "")
            val lockTime = prefs.getLong("lock_time", 0L)
            val isOverdue = prefs.getBoolean("is_overdue_lock", false)
            
            Log.d(TAG, "✅ Lock info retrieved:")
            Log.d(TAG, "   Lock Reason: $lockReason")
            Log.d(TAG, "   Is Overdue: $isOverdue")
            
            // Format timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedTime = dateFormat.format(Date(lockTime))
            
            Log.d(TAG, "✅ Payment Overdue Activity Initialized")
            Log.d(TAG, "📱 Screen: Payment Overdue")
            Log.d(TAG, "🔓 Unlock: Possible after payment")
            
            setContent {
                DeviceOwnerTheme {
                    // Placeholder - use the lock activity version instead
                    com.microspace.payo.ui.activities.lock.payment.PaymentOverdueActivity()
                }
            }
            
            recordSuccess("ACTIVITY_CREATED", reason)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate: ${e.message}", e)
            Log.e(TAG, e.stackTraceToString())
            recordError("ACTIVITY_CREATION_FAILED", e.message ?: "Unknown error")
            finish()
        }
    }
    
    private fun handlePayNow() {
        try {
            Log.d(TAG, "💳 Pay Now clicked")
            recordSuccess("PAY_NOW_CLICKED", "User initiated payment")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in handlePayNow: ${e.message}")
            recordError("PAY_NOW_FAILED", e.message ?: "Unknown error")
        }
    }
    
    private fun handleContactSupport() {
        try {
            Log.d(TAG, "📞 Contact Support clicked")
            recordSuccess("CONTACT_SUPPORT_CLICKED", "User requested support")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in handleContactSupport: ${e.message}")
            recordError("CONTACT_SUPPORT_FAILED", e.message ?: "Unknown error")
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.w(TAG, "⚠️ Back button pressed - ignoring (device locked)")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onBackPressed() {
        Log.w(TAG, "⚠️ Back button pressed - ignoring (device locked)")
    }
    
    override fun onDestroy() {
        try {
            Log.d(TAG, "🔄 Activity destroyed")
            recordSuccess("ACTIVITY_DESTROYED", "Normal cleanup")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onDestroy: ${e.message}")
        }
        super.onDestroy()
    }
    
    private fun recordSuccess(action: String, details: String) {
        try {
            val auditPrefs = getSharedPreferences("activity_audit", Context.MODE_PRIVATE)
            auditPrefs.edit().apply {
                putString("last_action", action)
                putString("last_action_details", details)
                putLong("last_action_time", System.currentTimeMillis())
                putString("last_action_status", "SUCCESS")
                apply()
            }
            Log.d(TAG, "📝 Audit: $action - $details")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to record audit: ${e.message}")
        }
    }
    
    private fun recordError(action: String, error: String) {
        try {
            val auditPrefs = getSharedPreferences("activity_audit", Context.MODE_PRIVATE)
            auditPrefs.edit().apply {
                putString("last_error_action", action)
                putString("last_error_message", error)
                putLong("last_error_time", System.currentTimeMillis())
                apply()
            }
            Log.e(TAG, "📝 Audit Error: $action - $error")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to record error audit: ${e.message}")
        }
    }
}
