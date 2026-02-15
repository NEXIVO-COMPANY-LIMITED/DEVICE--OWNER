package com.example.deviceowner.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import java.util.Locale
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.deactivation.DeviceOwnerDeactivationManager
import com.example.deviceowner.deactivation.DeactivationResult
import com.example.deviceowner.registration.DeviceRegistrationManager
import com.example.deviceowner.update.GitHubUpdateManager

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var deviceOwnerManager: DeviceOwnerManager
    private lateinit var deactivationManager: DeviceOwnerDeactivationManager
    private lateinit var registrationManager: DeviceRegistrationManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        deviceOwnerManager = DeviceOwnerManager(this)
        deactivationManager = DeviceOwnerDeactivationManager(this)
        registrationManager = DeviceRegistrationManager(this)
        
        // Disable Developer Mode and Debugging Features
        disableDeveloperMode()
        
        // Check and restore registration data if app was reinstalled
        checkAndRestoreRegistrationData()
        
        // Send installation status to backend after successful setup
        sendInstallationStatusToBackend()

        setContent {
            MaterialTheme {
                DeviceStatusScreen()
            }
        }
    }
    
    /**
     * Disables Developer Options and Debugging features.
     * Prevents the user from becoming a developer by clicking the build number.
     */
    private fun disableDeveloperMode() {
        if (deviceOwnerManager.isDeviceOwner()) {
            // Apply restriction and force settings off
            val success = deviceOwnerManager.disableDeveloperOptions(true)
            
            // Also apply system-level restrictions to hide the options from Settings app
            deviceOwnerManager.applySManager.disableDeveloperOptions(true)
            
            // Also apply system-level restrictions to hide the options from Settings app
            deviceOwnerManager.applySilentCompanyRestrictions()
            
            if (success) {
                Log.d(TAG, "✅ Developer mode and debugging disabled")
            } else {
                Log.e(TAG, "❌ Failed to fully disable developer mode")
            }
        }
    }

    private fun checkAndRestoreRegistrationData() {
        lifecycleScope.launch {
            try {
                val registrationRepository = com.example.deviceowner.data.repository.DeviceRegistrationRepository(this@MainActivity)
                val registrationBackup = com.example.deviceowner.data.local.RegistrationDataBackup(this@MainActivity)
                
                if (!registrationRepository.isDeviceRegistered()) {
                    if (registrationBackup.hasBackup()) {
                        if (registrationBackup.restoreRegistrationData()) {
                            android.widget.Toast.makeText(this@MainActivity, "Registration restored", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restore error: ${e.message}")
            }
        }
    }
    
    @Composable
    fun DeviceStatusScreen() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF1F5F9)
        ) {
            MainScreen()
        }
    }
    
    @Composable
    fun MainScreen() {
        var isDeactivating by remember { mutableStateOf(false) }
        var isCheckingUpdate by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Device Owner Icon
            Icon(
                Icons.Default.AdminPanelSettings, 
                contentDescription = null, 
                modifier = Modifier.size(80.dp), 
                tint = Color(0xFF0F172A)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Device Owner", 
                fontSize = 22.sp, 
                fontWeight = FontWeight.Bold, 
                color = Color(0xFF0F172A)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Device Management Active",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Status", fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Device Owner is active and managing this device.", color = Color(0xFF475569), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Developer Options Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CodeOff, null, tint = Color(0xFF64748B))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Developer Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Disabled & Blocked", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = Color(0xFF94A3B8))
                }
            }

            // Check for Updates Card - GitHub auto-update
            if (deviceOwnerManager.isDeviceOwner()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCheckingUpdate) {
                            isCheckingUpdate = true
                            lifecycleScope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        GitHubUpdateManager(context).checkAndUpdate()
                                    } catch (e: Exception) {
                                        android.util.Log.e(TAG, "Update check failed: ${e.message}", e)
                                    }
                                }
                                isCheckingUpdate = false
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SystemUpdate, null, tint = Color(0xFF64748B))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Check for Updates", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Auto-update from GitHub every 6 hours",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp), tint = Color(0xFF94A3B8))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // FRP Status Card - Enterprise Factory Reset Protection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(context, FrpStatusActivity::class.java))
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, null, tint = Color(0xFF64748B))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FRP Protection", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Enterprise Factory Reset Protection", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp), tint = Color(0xFF94A3B8))
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))

            // DEACTIVATION BUTTON - RETURNS PHONE TO NORMAL STATE
            if (deviceOwnerManager.isDeviceOwner()) {
                Button(
                    onClick = {
                        isDeactivating = true
                        lifecycleScope.launch {
                            val result = deactivationManager.deactivateDeviceOwner()
                            isDeactivating = false
                            if (result is DeactivationResult.Success) {
                                Toast.makeText(context, "✅ Deactivated Successfully. Phone returned to normal.", Toast.LENGTH_LONG).show()
                                finish() // Close app
                            } else {
                                Toast.makeText(context, "❌ Deactivation Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    enabled = !isDeactivating
                ) {
                    if (isDeactivating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("REMOVE DEVICE OWNER (NORMAL STATE)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    /**
     * Send installation status to backend after successful device registration
     * This notifies the server that Device Owner has been activated successfully
     * 
     * The backend expects:
     * - POST /api/devices/mobile/{device_id}/installation-status/
     * - Body: { "completed": true, "reason": "Device Owner activated successfully", "timestamp": "ISO8601" }
     * - Response: { "success": true, "message": "...", "device_id": "...", "installation_status": "completed", "completed_at": "ISO8601" }
     */
    private fun sendInstallationStatusToBackend() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🔍 Checking if device is registered before sending installation status...")
                
                // Step 1: Check if device is registered
                val registrationRepository = com.example.deviceowner.data.repository.DeviceRegistrationRepository(this@MainActivity)
                
                if (!registrationRepository.isDeviceRegistered()) {
                    Log.d(TAG, "⏳ Device not yet registered - installation status will be sent after registration")
                    return@launch
                }
                
                Log.d(TAG, "✅ Device is registered - proceeding with installation status send")
                
                // Step 2: Get device ID from stored registration
                val registrationData = registrationRepository.getCompleteRegistrationData()
                val deviceId = registrationData?.deviceId
                if (deviceId.isNullOrEmpty() || deviceId.equals("unknown", ignoreCase = true)) {
                    Log.e(TAG, "❌ Device ID is missing or unknown - cannot send installation status")
                    return@launch
                }
                
                Log.d(TAG, "📱 Device ID: $deviceId")
                
                // Step 3: Check if installation status was already sent
                if (registrationManager.hasInstallationStatusBeenSent()) {
                    Log.d(TAG, "✅ Installation status already sent previously - skipping")
                    return@launch
                }
                
                Log.d(TAG, "📤 Sending installation status to backend...")
                
                // Step 4: Send installation status with lifecycle scope
                registrationManager.sendInstallationStatusWithLifecycle(
                    lifecycleScope = lifecycleScope,
                    onSuccess = {
                        Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
                        Log.i(TAG, "✅ Installation status sent successfully to backend")
                        Log.i(TAG, "✅ Device Owner activation confirmed on server")
                        Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
                        
                        Toast.makeText(
                            this@MainActivity,
                            "✅ Installation status reported to server",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ Failed to send installation status: $error")
                        Log.e(TAG, "   The device will retry on next heartbeat")
                        // Don't show error toast - this is a background operation
                        // The app should continue to work even if this fails
                        // Heartbeat will retry sending this status
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in sendInstallationStatusToBackend: ${e.message}", e)
                Log.e(TAG, "   Stack trace: ${e.stackTraceToString()}")
            }
        }
    }

    /**
     * Security Violation Hard Lock Screen
     * Displayed when device is NOT Device Owner (demo/testing mode)
     * Shows realistic hardlock screen for security violation
     */
    @Composable
    fun SecurityViolationHardLockScreen() {
        val accentColor = Color(0xFFD32F2F)
        val bgColor = Color(0xFF0D0D0D)
        val headerBgColor = Color(0xFF1A1A1A)
        val cardBgColor = Color(0xFF2A2A2A)
        
        // Get current timestamp
        val lockTime = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date())
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bgColor, Color(0xFF000000)),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // ============ HEADER BANNER ============
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp),
                    color = headerBgColor,
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Large Lock Icon
                        Text(
                            text = "🔒",
                            fontSize = 72.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Main Title
                        Text(
                            text = "SECURITY VIOLATION",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            textAlign = TextAlign.Center,
                            letterSpacing = 3.sp
                        )
                        
                        // Subtitle
                        Text(
                            text = "DEVICE LOCKED",
                            fontSize = 20.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Timestamp
                        Text(
                            text = "Locked: $lockTime",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                
                // ============ MAIN CONTENT AREA ============
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    
                    // ============ SECURITY VIOLATION ALERT ============
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        color = cardBgColor,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 12.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = accentColor.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Alert Icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠️",
                                    fontSize = 24.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Text(
                                    text = "SECURITY VIOLATION DETECTED",
                                    fontSize = 14.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp
                                )
                            }
                            
                            // Violation Details
                            Text(
                                text = "Unauthorized access attempt detected\n\nThis device has been locked for security purposes. Contact your administrator for assistance.",
                                fontSize = 14.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 22.sp
                            )
                        }
                    }
                    
                    
                    // ============ ADMIN CONTACT CARD ============
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        color = Color(0xFF1F3A3F),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 12.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color(0xFF00BCD4).copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "📞 ADMINISTRATOR CONTACT",
                                fontSize = 11.sp,
                                color = Color(0xFF00BCD4),
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            
                            Text(
                                text = "This device is under administrative control. For assistance, contact your device administrator immediately.",
                                fontSize = 12.sp,
                                color = Color(0xFFBDBDBD),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
