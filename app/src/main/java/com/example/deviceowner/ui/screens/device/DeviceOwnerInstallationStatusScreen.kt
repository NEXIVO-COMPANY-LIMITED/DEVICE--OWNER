package com.example.deviceowner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.managers.DeviceOwnerManager
import com.example.deviceowner.managers.device.ProvisioningStatusTracker
import com.example.deviceowner.utils.logging.FileLogger
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen to display Device Owner installation status
 * Shows success when installation completes, error when it fails
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceOwnerInstallationStatusScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val statusTracker = ProvisioningStatusTracker(context)
    val deviceOwnerManager = DeviceOwnerManager(context)
    val fileLogger = FileLogger.getInstance(context)
    
    var installationStatus by remember { mutableStateOf<InstallationStatus?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Auto-refresh status every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            checkInstallationStatus(statusTracker, deviceOwnerManager, fileLogger) { status ->
                installationStatus = status
            }
            lastRefreshTime = System.currentTimeMillis()
            delay(2000) // Refresh every 2 seconds
        }
    }
    
    // Check status on initial load
    LaunchedEffect(Unit) {
        checkInstallationStatus(statusTracker, deviceOwnerManager, fileLogger) { status ->
            installationStatus = status
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Owner Installation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            checkInstallationStatus(statusTracker, deviceOwnerManager, fileLogger) { status ->
                                installationStatus = status
                                isRefreshing = false
                            }
                        }
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
        ) {
            when (val status = installationStatus) {
                null -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "Checking installation status...",
                                fontSize = 16.sp,
                                color = colors.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                is InstallationStatus.Success -> {
                    SuccessInstallationView(
                        status = status,
                        colors = colors
                    )
                }
                is InstallationStatus.Failed -> {
                    FailedInstallationView(
                        status = status,
                        onRetry = {
                            // Retry logic can be added here
                        },
                        colors = colors
                    )
                }
                is InstallationStatus.InProgress -> {
                    InProgressInstallationView(
                        status = status,
                        colors = colors
                    )
                }
                is InstallationStatus.NotStarted -> {
                    NotStartedView(
                        colors = colors
                    )
                }
            }
            
            // Last refresh time indicator
            Text(
                text = "Last updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastRefreshTime))}",
                fontSize = 12.sp,
                color = colors.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Check installation status and return appropriate status object
 */
private fun checkInstallationStatus(
    statusTracker: ProvisioningStatusTracker,
    deviceOwnerManager: DeviceOwnerManager,
    fileLogger: FileLogger,
    onStatusUpdate: (InstallationStatus) -> Unit
) {
    try {
        val provisioningStatus = statusTracker.getProvisioningStatus()
        val isDeviceOwner = deviceOwnerManager.isDeviceOwner()
        val isDeviceAdmin = deviceOwnerManager.isDeviceAdmin()
        
        when {
            // Success: Provisioning completed and device owner is active
            provisioningStatus.completed && isDeviceOwner && isDeviceAdmin -> {
                onStatusUpdate(
                    InstallationStatus.Success(
                        phase = provisioningStatus.currentPhase,
                        duration = provisioningStatus.duration,
                        message = "Device Owner installation completed successfully!",
                        details = listOf(
                            "Device Owner: Active",
                            "Device Admin: Active",
                            "Phase: ${provisioningStatus.currentPhase}",
                            "Duration: ${provisioningStatus.duration / 1000.0}s"
                        )
                    )
                )
            }
            // Failed: Provisioning completed but device owner is not active
            provisioningStatus.completed && !isDeviceOwner -> {
                onStatusUpdate(
                    InstallationStatus.Failed(
                        phase = provisioningStatus.currentPhase,
                        errorMessage = "Device Owner installation failed!",
                        errorDetails = listOf(
                            "Device Owner: Not Active",
                            "Device Admin: ${if (isDeviceAdmin) "Active" else "Not Active"}",
                            "Last Status: ${provisioningStatus.lastStatus}",
                            "Phase: ${provisioningStatus.currentPhase}"
                        ),
                        canRetry = true
                    )
                )
            }
            // In Progress: Provisioning started but not completed
            provisioningStatus.started && !provisioningStatus.completed -> {
                onStatusUpdate(
                    InstallationStatus.InProgress(
                        phase = provisioningStatus.currentPhase,
                        lastStatus = provisioningStatus.lastStatus,
                        duration = System.currentTimeMillis() - provisioningStatus.duration
                    )
                )
            }
            // Not Started: No provisioning detected
            else -> {
                onStatusUpdate(
                    InstallationStatus.NotStarted(
                        message = "Device Owner installation has not started yet."
                    )
                )
            }
        }
    } catch (e: Exception) {
        onStatusUpdate(
            InstallationStatus.Failed(
                phase = "Unknown",
                errorMessage = "Error checking installation status: ${e.message}",
                errorDetails = listOf(
                    "Exception: ${e.javaClass.simpleName}",
                    "Message: ${e.message ?: "Unknown error"}"
                ),
                canRetry = true
            )
        )
    }
}

/**
 * Sealed class for installation status
 */
sealed class InstallationStatus {
    data class Success(
        val phase: String,
        val duration: Long,
        val message: String,
        val details: List<String>
    ) : InstallationStatus()
    
    data class Failed(
        val phase: String,
        val errorMessage: String,
        val errorDetails: List<String>,
        val canRetry: Boolean = false
    ) : InstallationStatus()
    
    data class InProgress(
        val phase: String,
        val lastStatus: String,
        val duration: Long
    ) : InstallationStatus()
    
    data class NotStarted(
        val message: String
    ) : InstallationStatus()
}

/**
 * Success installation view
 */
@Composable
private fun SuccessInstallationView(
    status: InstallationStatus.Success,
    colors: ColorScheme
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Success Icon
        Surface(
            shape = CircleShape,
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            color = Color(0xFF4CAF50),
            shadowElevation = 16.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Success",
                    modifier = Modifier.size(80.dp),
                    tint = Color.White
                )
            }
        }
        
        // Success Message
        Text(
            text = "Installation Successful!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = status.message,
            fontSize = 16.sp,
            color = colors.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        // Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Installation Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                
                status.details.forEach { detail ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            "Check",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = detail,
                            fontSize = 14.sp,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }
        
        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Logs can be viewed directly on device - no need for in-app viewer
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Failed installation view
 */
@Composable
private fun FailedInstallationView(
    status: InstallationStatus.Failed,
    onRetry: () -> Unit,
    colors: ColorScheme
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Error Icon
        Surface(
            shape = CircleShape,
            modifier = Modifier.size(120.dp),
            color = Color(0xFFF44336),
            shadowElevation = 16.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Error,
                    "Error",
                    modifier = Modifier.size(80.dp),
                    tint = Color.White
                )
            }
        }
        
        // Error Message
        Text(
            text = "Installation Failed",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = status.errorMessage,
            fontSize = 16.sp,
            color = colors.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        // Error Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Error Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                
                status.errorDetails.forEach { detail ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            "Warning",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = detail,
                            fontSize = 14.sp,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }
        
        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Logs can be viewed directly on device - no need for in-app viewer
            
            if (status.canRetry) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, "Retry")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry Installation")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * In progress installation view
 */
@Composable
private fun InProgressInstallationView(
    status: InstallationStatus.InProgress,
    colors: ColorScheme
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        // Progress Indicator
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                    },
                strokeWidth = 8.dp,
                color = colors.primary
            )
            Icon(
                Icons.Default.Build,
                "Installing",
                modifier = Modifier.size(48.dp),
                tint = colors.primary
            )
        }
        
        Text(
            text = "Installation In Progress",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Phase: ${status.phase}",
            fontSize = 16.sp,
            color = colors.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Status: ${status.lastStatus}",
            fontSize = 14.sp,
            color = colors.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Please wait while Device Owner is being installed...",
            fontSize = 14.sp,
            color = colors.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Not started view
 */
@Composable
private fun NotStartedView(
    colors: ColorScheme
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Info,
            "Info",
            modifier = Modifier.size(64.dp),
            tint = colors.primary.copy(alpha = 0.7f)
        )
        
        Text(
            text = "Installation Not Started",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Device Owner installation has not been initiated yet.",
            fontSize = 14.sp,
            color = colors.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
