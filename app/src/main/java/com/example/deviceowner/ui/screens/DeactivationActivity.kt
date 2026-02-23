package com.example.deviceowner.ui.screens

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import java.text.SimpleDateFormat
import java.util.*

class DeactivationActivity : AppCompatActivity() {
    
    private val TAG = "DeactivationActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d(TAG, "")
            Log.d(TAG, "╔════════════════════════════════════════════════════════════╗")
            Log.d(TAG, "║ 🔓 DEACTIVATION ACTIVITY CREATED")
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
            val reason = intent.getStringExtra("reason") ?: "Device deactivation"
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val automatic = intent.getBooleanExtra("automatic", false)
            
            Log.d(TAG, "✅ Intent data extracted:")
            Log.d(TAG, "   Reason: $reason")
            Log.d(TAG, "   Automatic: $automatic")
            
            // Get deactivation info from SharedPreferences
            val prefs = getSharedPreferences("heartbeat_state", Context.MODE_PRIVATE)
            val deactivateRequested = prefs.getBoolean("deactivate_requested", false)
            val loanComplete = prefs.getBoolean("loan_complete", false)
            
            Log.d(TAG, "✅ Deactivation info retrieved:")
            Log.d(TAG, "   Deactivate Requested: $deactivateRequested")
            Log.d(TAG, "   Loan Complete: $loanComplete")
            
            // Format timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedTime = dateFormat.format(Date(timestamp))
            
            Log.d(TAG, "✅ Deactivation Activity Initialized")
            Log.d(TAG, "📱 Screen: Deactivation")
            Log.d(TAG, "🔓 Status: Device will be deactivated shortly")
            
            setContent {
                DeviceOwnerTheme {
                    // Placeholder - use the lock activity version instead
                    com.example.deviceowner.ui.activities.lock.system.DeactivationScreen(
                        onDeactivationComplete = { finish() }
                    )
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
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.w(TAG, "⚠️ Back button pressed - ignoring (device deactivating)")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onBackPressed() {
        Log.w(TAG, "⚠️ Back button pressed - ignoring (device deactivating)")
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
