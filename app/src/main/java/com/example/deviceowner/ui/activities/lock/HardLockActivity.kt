package com.example.deviceowner.ui.activities.lock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * Hard Lock Activity - Kiosk Mode Screen
 * Displays when device is in hard lock mode
 * Blocks all user interaction
 */
class HardLockActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "HardLockActivity"
    }
    
    private lateinit var controlManager: RemoteDeviceControlManager
    private var backPressedCallback: OnBackPressedCallback? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        controlManager = RemoteDeviceControlManager(this)
        
        // Block all back button presses (Android 13+)
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Completely block back button - do nothing
                Log.d(TAG, "Back button pressed - completely blocked")
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback!!)
        
        // Enter lock task mode (kiosk mode) - MUST be done first
        enterLockTaskMode()
        
        // Make activity fullscreen and immersive
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Additional flags to prevent any interaction
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        
        // Hide system UI completely
        hideSystemUI()
        
        val lockReason = intent.getStringExtra("lock_reason") ?: "Device locked by administrator"
        
        setContent {
            DeviceOwnerTheme {
                HardLockScreen(lockReason = lockReason)
            }
        }
    }
    
    /**
     * Enter lock task mode (kiosk mode)
     */
    private fun enterLockTaskMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                if (!activityManager.isInLockTaskMode) {
                    startLockTask()
                    Log.d(TAG, "Lock task mode started")
                } else {
                    Log.d(TAG, "Already in lock task mode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enter lock task mode", e)
            }
        }
    }
    
    /**
     * Hide system UI completely
     */
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        
        // For Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.hide(android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = 
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Re-enter lock task mode if not active
        enterLockTaskMode()
        
        // Re-hide system UI
        hideSystemUI()
        
        // Check if device is still locked
        if (!controlManager.isHardLocked()) {
            Log.d(TAG, "Device no longer hard locked - finishing activity")
            finish()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Ensure we stay in lock task mode even when paused
        enterLockTaskMode()
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-hide system UI when window gains focus
            hideSystemUI()
            // Re-enter lock task mode
            enterLockTaskMode()
        }
    }
    
    // Block all back button presses (for older Android versions)
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Completely block - do nothing
        Log.d(TAG, "Back button pressed - completely blocked")
    }
    
    // Block ALL key events - comprehensive blocking
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "Key down: $keyCode")
        
        // Only allow power button (system requirement)
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            return super.onKeyDown(keyCode, event)
        }
        
        // Block ALL other keys
        return true
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "Key up: $keyCode")
        
        // Block home button
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            Log.d(TAG, "Home button blocked")
            return true
        }
        
        // Block menu button
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            Log.d(TAG, "Menu button blocked")
            return true
        }
        
        // Block recent apps button
        if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            Log.d(TAG, "Recent apps button blocked")
            return true
        }
        
        // Block back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "Back button blocked")
            return true
        }
        
        // Block volume buttons
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Log.d(TAG, "Volume button blocked")
            return true
        }
        
        // Block all other keys except power
        if (keyCode != KeyEvent.KEYCODE_POWER) {
            return true
        }
        
        return super.onKeyUp(keyCode, event)
    }
    
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "Key long press: $keyCode")
        
        // Block all long presses except power
        if (keyCode != KeyEvent.KEYCODE_POWER) {
            return true
        }
        
        return super.onKeyLongPress(keyCode, event)
    }
    
    // Block all touch events that might trigger navigation
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Allow touch events for the lock screen itself
        // But prevent any gestures that might exit
        return super.dispatchTouchEvent(ev)
    }
    
    // Prevent new activities from being started
    override fun startActivity(intent: Intent?) {
        // Only allow starting HardLockActivity or system activities
        if (intent?.component?.className == HardLockActivity::class.java.name) {
            super.startActivity(intent)
        } else {
            Log.w(TAG, "Blocked attempt to start activity: ${intent?.component?.className}")
        }
    }
    
    // Prevent activity from being finished
    override fun finish() {
        // Only allow finish if device is unlocked
        if (!controlManager.isHardLocked()) {
            super.finish()
        } else {
            Log.w(TAG, "Blocked attempt to finish activity - device still locked")
        }
    }
}

@Composable
fun HardLockScreen(lockReason: String) {
    val context = LocalContext.current
    val controlManager = remember { RemoteDeviceControlManager(context) }
    
    val lockTimestamp = remember { controlManager.getLockTimestamp() }
    val timeFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(lockTimestamp) {
        if (lockTimestamp > 0L) {
            timeFormat.format(java.util.Date(lockTimestamp))
        } else {
            "Unknown"
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A),
                        Color(0xFF000000)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Lock Icon
            Surface(
                modifier = Modifier.size(120.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color(0xFFD32F2F).copy(alpha = 0.2f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFFD32F2F)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                text = "DEVICE LOCKED",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )
            
            // Subtitle
            Text(
                text = "This device is currently locked",
                fontSize = 18.sp,
                color = Color(0xFFBDBDBD),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Lock Reason Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = Color(0xFF2C2C2C),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Lock Reason",
                        fontSize = 14.sp,
                        color = Color(0xFFBDBDBD),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = lockReason,
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            // Lock Time Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = Color(0xFF2C2C2C),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Locked At",
                        fontSize = 14.sp,
                        color = Color(0xFFBDBDBD),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = formattedTime,
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Warning Message
            Text(
                text = "This device is in kiosk mode." +
                        "\nAll user interactions are disabled." +
                        "\nPlease contact your administrator.",
                fontSize = 14.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
