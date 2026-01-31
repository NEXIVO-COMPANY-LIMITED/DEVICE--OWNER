package com.example.deviceowner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalContext
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.ui.activities.LogViewerActivity
import com.example.deviceowner.utils.logging.LogManager

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var deviceOwnerManager: DeviceOwnerManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        deviceOwnerManager = DeviceOwnerManager(this)
        
        // Check and restore registration data if app was reinstalled
        checkAndRestoreRegistrationData()

        setContent {
            MaterialTheme {
                DeviceStatusScreen()
            }
        }
    }
    
    /**
     * Check if registration data needs to be restored from backup
     * This handles app reinstallation scenarios
     */
    private fun checkAndRestoreRegistrationData() {
        lifecycleScope.launch {
            try {
                val registrationRepository = com.example.deviceowner.data.repository.DeviceRegistrationRepository(this@MainActivity)
                val registrationBackup = com.example.deviceowner.data.local.RegistrationDataBackup(this@MainActivity)
                
                // Check if device is already registered
                val isRegistered = registrationRepository.isDeviceRegistered()
                
                if (!isRegistered) {
                    // Check if backup exists
                    val hasBackup = registrationBackup.hasBackup()
                    
                    if (hasBackup) {
                        Log.d(TAG, "Registration backup found - attempting to restore")
                        
                        val restored = registrationBackup.restoreRegistrationData()
                        
                        if (restored) {
                            Log.d(TAG, "Registration data restored successfully from backup")
                            
                            // Get backup info for logging
                            val backupInfo = registrationBackup.getBackupInfo()
                            Log.d(TAG, "Restored device ID: ${backupInfo["deviceId"]}")
                            Log.d(TAG, "Restored loan number: ${backupInfo["loanNumber"]}")
                            
                            // Show notification to user
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Device registration restored from backup",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Log.w(TAG, "Failed to restore registration data from backup")
                        }
                    } else {
                        Log.d(TAG, "No registration backup found - device needs to be registered")
                    }
                } else {
                    Log.d(TAG, "Device is already registered - no restore needed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking/restoring registration data: ${e.message}", e)
            }
        }
    }
    
    @Composable
    fun DeviceStatusScreen() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8FAFC)
        ) {
            DisabledRegistrationScreen()
        }
    }
    
    @Composable
    fun DisabledRegistrationScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFFFFE4B5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(48.dp), tint = Color(0xFFFF9800))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Device Registration Disabled", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "All device registration and data collection functionality has been removed from this application.",
                textAlign = TextAlign.Center,
                color = Color(0xFF64748B),
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Security Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("System Security", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(16.dp))
                    SecurityItem(Icons.Default.Shield, "Enterprise Protection Active", Color(0xFF10B981))
                    SecurityItem(Icons.Default.Lock, "Hardware Lock Policy Enforced", Color(0xFF10B981))
                    SecurityItem(Icons.Default.Block, "Device Registration Disabled", Color(0xFFEF4444))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Log Viewer Button
            OutlinedButton(
                onClick = {
                    startActivity(Intent(this@MainActivity, LogViewerActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Article, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Logs", fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Soft Lock Test Button
            OutlinedButton(
                onClick = {
                    startActivity(Intent(this@MainActivity, com.example.deviceowner.ui.activities.SoftLockTestActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Soft Lock Violations", fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Device registration, data collection, and server communication have been completely removed. " +
                "Only basic device owner security restrictions remain active.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }

    @Composable
    fun SecurityItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = color)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, fontSize = 13.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
        }
    }
}