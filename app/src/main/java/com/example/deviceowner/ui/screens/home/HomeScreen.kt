package com.example.deviceowner.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.data.repository.AuthRepository
import com.example.deviceowner.managers.HeartbeatDataManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    deviceId: String,
    loanId: String,
    onLogout: () -> Unit,
    onViewInstallationStatus: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    
    // Device status states
    var isServiceRunning by remember { mutableStateOf(true) }
    var lastHeartbeatTime by remember { mutableStateOf<String?>(null) }
    var heartbeatCount by remember { mutableStateOf(0) }
    var deviceStatus by remember { mutableStateOf("ACTIVE") }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Pulse animation for active status
    val pulseScale by animateFloatAsState(
        targetValue = if (isServiceRunning) 1.2f else 1f,
        animationSpec = tween(durationMillis = 1500),
        label = "pulseAnimation"
    )
    
    // Function to refresh device status
    val refreshDeviceStatus: () -> Unit = {
        scope.launch {
            isRefreshing = true
            try {
                val heartbeatManager = HeartbeatDataManager(context)
                val lastHeartbeat = heartbeatManager.getLastHeartbeatData()
                
                if (lastHeartbeat != null) {
                    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    lastHeartbeatTime = sdf.format(java.util.Date(lastHeartbeat.timestamp))
                    heartbeatCount++
                    deviceStatus = "ACTIVE"
                    isServiceRunning = true
                } else {
                    deviceStatus = "INITIALIZING"
                    isServiceRunning = false
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error refreshing status: ${e.message}")
                deviceStatus = "ERROR"
                isServiceRunning = false
            }
            isRefreshing = false
        }
    }
    
    // Load device status on screen load
    LaunchedEffect(Unit) {
        refreshDeviceStatus()
    }
    
    // Auto-refresh every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            refreshDeviceStatus()
        }
    }
    
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Device Dashboard") },
                actions = {
                    IconButton(onClick = {
                        refreshDeviceStatus()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.primary
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = colors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Status Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                color = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Indicator
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulse background
                        Surface(
                            modifier = Modifier
                                .size(100.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = 0.3f
                                },
                            shape = CircleShape,
                            color = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        ) {}
                        
                        // Main indicator
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = if (isServiceRunning) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = "Status",
                                    modifier = Modifier.size(50.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    
                    // Status Text
                    Text(
                        text = deviceStatus,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                    
                    Text(
                        text = if (isServiceRunning) "Device is protected and monitored" else "Initializing device protection",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Device Information Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                color = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    InfoRow(label = "Device ID", value = deviceId, isMonospace = true)
                    Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                    InfoRow(label = "Loan ID", value = loanId)
                }
            }
            
            // Heartbeat Status Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                color = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Heartbeat Status",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    InfoRow(
                        label = "Last Heartbeat",
                        value = lastHeartbeatTime ?: "Waiting for first heartbeat...",
                        isMonospace = false
                    )
                    Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                    InfoRow(
                        label = "Heartbeats Received",
                        value = "$heartbeatCount",
                        isMonospace = false
                    )
                    Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                    InfoRow(
                        label = "Service Status",
                        value = if (isServiceRunning) "Running" else "Stopped",
                        isMonospace = false
                    )
                }
            }
            
            // Features Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                color = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Active Features",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    FeatureRow("Device Monitoring", "Real-time heartbeat tracking")
                    FeatureRow("Security Protection", "Tampering detection enabled")
                    FeatureRow("Command Execution", "Remote management active")
                    FeatureRow("Data Verification", "Continuous verification running")
                }
            }
            
            // Installation Status & Logs Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                color = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Installation & Logs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    // View Installation Status Button
                    OutlinedButton(
                        onClick = onViewInstallationStatus,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, "Installation Status")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Installation Status")
                    }
                }
            }
            
            // Refresh Button
            Button(
                onClick = {
                    refreshDeviceStatus()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White
                ),
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Status", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            fontFamily = if (isMonospace) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun FeatureRow(
    title: String,
    description: String
) {
    val colors = MaterialTheme.colorScheme
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = colors.primary.copy(alpha = 0.2f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 14.sp
            )
        }
    }
}
