package com.example.deviceowner.ui.activities.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactSupport
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
import com.example.deviceowner.data.models.SoftLockType
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Soft Lock Activity - Customized for Each Detection Type
 * Shows specific messages and UI based on what action triggered the lock
 * Each violation type has its own unique screen design and messaging
 */
class EnhancedSoftLockActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "EnhancedSoftLock"
        
        fun startSoftLock(context: Context, reason: String, triggerAction: String = "") {
            val intent = Intent(context, EnhancedSoftLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("lock_reason", reason)
                putExtra("trigger_action", triggerAction)
            }
            context.startActivity(intent)
        }
    }
    
    private lateinit var controlManager: RemoteDeviceControlManager
    private var backPressedCallback: OnBackPressedCallback? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        controlManager = RemoteDeviceControlManager(this)
        
        // Block back button presses
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back button blocked - soft lock active")
                // Do nothing - keep the screen active
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback!!)
        
        // Make activity fullscreen and prevent dismissal
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        
        val lockReason = intent.getStringExtra("lock_reason") ?: "Device access restricted"
        val triggerAction = intent.getStringExtra("trigger_action") ?: ""
        
        // Determine specific lock type based on trigger action and reason
        val lockType = SoftLockType.fromTriggerAction(triggerAction, lockReason)
        
        setContent {
            DeviceOwnerTheme {
                CustomizedSoftLockScreen(
                    lockType = lockType,
                    lockReason = lockReason,
                    triggerAction = triggerAction,
                    onCancel = { handleCancelAttempt() },
                    onContactSupport = { handleContactSupport() }
                )
            }
        }
    }
    
    private fun handleCancelAttempt() {
        // Check if soft lock can be dismissed
        if (controlManager.getLockState() != RemoteDeviceControlManager.LOCK_SOFT) {
            Log.d(TAG, "Soft lock dismissed - no longer in soft lock state")
            finish()
        } else {
            Log.w(TAG, "Cancel attempt blocked - device still in soft lock state")
            // The UI will show a message that dismissal is not allowed
        }
    }
    
    private fun handleContactSupport() {
        Log.d(TAG, "User requested support contact")
        // TODO: Implement contact support functionality
        // Could open dialer with support number, email app, etc.
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if device is still in soft lock
        if (controlManager.getLockState() != RemoteDeviceControlManager.LOCK_SOFT) {
            Log.d(TAG, "Device no longer in soft lock - finishing activity")
            finish()
        }
    }
    
    // Prevent activity from being finished by system
    override fun finish() {
        if (controlManager.getLockState() == RemoteDeviceControlManager.LOCK_SOFT) {
            Log.w(TAG, "Finish blocked - device still in soft lock")
            return
        }
        super.finish()
    }
}

@Composable
fun CustomizedSoftLockScreen(
    lockType: SoftLockType,
    lockReason: String,
    triggerAction: String,
    onCancel: () -> Unit,
    onContactSupport: () -> Unit
) {
    val context = LocalContext.current
    val controlManager = remember { RemoteDeviceControlManager(context) }
    
    var canDismiss by remember { mutableStateOf(false) }
    var showDismissalMessage by remember { mutableStateOf(false) }
    
    // Check if soft lock can be dismissed
    LaunchedEffect(Unit) {
        canDismiss = controlManager.getLockState() != RemoteDeviceControlManager.LOCK_SOFT
    }
    
    val lockTimestamp = remember { controlManager.getLockTimestamp() }
    val timeFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(lockTimestamp) {
        if (lockTimestamp > 0L) {
            timeFormat.format(Date(lockTimestamp))
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Customized Icon with lock type color
            Surface(
                modifier = Modifier.size(140.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = lockType.color.copy(alpha = 0.2f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = lockType.icon,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = lockType.color
                    )
                }
            }
            
            // Customized Title
            Text(
                text = lockType.title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 1.5.sp
            )
            
            // Primary Message (Specific to lock type)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = lockType.color.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = lockType.primaryMessage,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = lockType.color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(20.dp),
                    lineHeight = 24.sp
                )
            }
            
            // Warning Message (Specific to lock type)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C2C2C)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Warning",
                        fontSize = 14.sp,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = lockType.warningMessage,
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
            
            // Action Advice (Specific to lock type)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Important Notice",
                        fontSize = 14.sp,
                        color = Color(0xFFBDBDBD),
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = lockType.actionAdvice,
                        fontSize = 14.sp,
                        color = Color(0xFFE0E0E0),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
            
            // Server Reason (if different from primary message)
            if (lockReason != lockType.primaryMessage && lockReason.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2C)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Additional Details",
                            fontSize = 14.sp,
                            color = Color(0xFFBDBDBD),
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = lockReason,
                            fontSize = 14.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Time Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C2C2C)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Restricted at: $formattedTime",
                    fontSize = 12.sp,
                    color = Color(0xFFBDBDBD),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // Dismissal Message (shown when user tries to cancel but can't)
            if (showDismissalMessage) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFD32F2F).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "This restriction cannot be dismissed until the issue is resolved. Please follow the guidance above or contact support.",
                        fontSize = 14.sp,
                        color = Color(0xFFFF5722),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Action Buttons (Customized based on lock type)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Contact Support Button
                Button(
                    onClick = onContactSupport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactSupport,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SUPPORT",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Customized Action Button
                Button(
                    onClick = {
                        if (canDismiss) {
                            onCancel()
                        } else {
                            showDismissalMessage = true
                            // Hide message after 5 seconds
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(5000)
                                showDismissalMessage = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canDismiss) Color(0xFF4CAF50) else lockType.color.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (canDismiss) "DISMISS" else lockType.buttonText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Footer Warning
            Text(
                text = "This screen will remain active until the restriction is resolved.\nPlease follow the instructions above to avoid further restrictions.",
                fontSize = 12.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}