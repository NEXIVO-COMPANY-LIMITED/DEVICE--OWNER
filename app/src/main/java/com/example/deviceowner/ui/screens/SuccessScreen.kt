package com.example.deviceowner.ui.screens

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import com.example.deviceowner.managers.DeviceOwnerProvisioning

@Composable
fun SuccessScreen(
    deviceId: String,
    loanId: String,
    onGoToHome: () -> Unit
) {
    val context = LocalContext.current
    
    // Initialize device owner on screen load
    LaunchedEffect(Unit) {
        initializeDeviceOwner(context)
    }
    val colors = MaterialTheme.colorScheme

    // Entrance animation
    val entrance by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "successEntrance"
    )

    // Scale animation for checkmark
    val checkScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "checkScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .graphicsLayer {
                    alpha = entrance
                    translationY = (1f - entrance) * 40f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            // ===================== CONTENT =====================
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                // Success Checkmark
                Surface(
                    shape = CircleShape,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        },
                    color = colors.primary
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.background(colors.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            modifier = Modifier.size(80.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Title
                Text(
                    text = "Device Registered Successfully!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp
                    ),
                    color = colors.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subtitle
                Text(
                    text = "Your device has been successfully registered and is ready to use",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Device Information Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Device ID
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Device ID",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface.copy(alpha = 0.7f)
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = colors.background
                            ) {
                                Text(
                                    text = deviceId,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onBackground,
                                    modifier = Modifier.padding(12.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }

                        Divider(color = colors.onSurface.copy(alpha = 0.1f))

                        // Loan ID
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Loan ID",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = loanId,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onBackground
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Features List
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Available Features",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )

                    FeatureItem("Device Monitoring", "Real-time device status tracking")
                    FeatureItem("Security Protection", "Advanced tampering detection")
                    FeatureItem("Offline Support", "Works without internet connection")
                    FeatureItem("Command Execution", "Remote device management")
                }
            }

            // ===================== BOTTOM =====================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Button(
                    onClick = onGoToHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Go to Home",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Device is now protected and monitored",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FeatureItem(
    title: String,
    description: String
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    clip = true
                },
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
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = colors.onBackground.copy(alpha = 0.6f),
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Initialize device owner features after successful registration
 */
private fun initializeDeviceOwner(context: Context) {
    val manager = DeviceOwnerManager(context)
    val provisioning = DeviceOwnerProvisioning(context)
    
    // Log provisioning status
    provisioning.logProvisioningStatus()
    
    // Check if device owner is connected
    val isDeviceOwner = manager.isDeviceOwner()
    
    // If already device owner, initialize features
    if (isDeviceOwner) {
        manager.initializeDeviceOwner()
        android.util.Log.d("SuccessScreen", "✓ Device Owner Connected - Features Initialized")
    } else {
        android.util.Log.w("SuccessScreen", "⚠ Device Owner Not Connected")
    }
    
    // Start HeartbeatVerificationService for continuous device monitoring
    startHeartbeatService(context)
}

/**
 * Start the HeartbeatVerificationService to begin sending heartbeat data to backend
 */
private fun startHeartbeatService(context: Context) {
    try {
        val heartbeatIntent = android.content.Intent(context, com.example.deviceowner.services.HeartbeatVerificationService::class.java)
        context.startService(heartbeatIntent)
        android.util.Log.d("SuccessScreen", "✓ HeartbeatVerificationService started successfully")
    } catch (e: Exception) {
        android.util.Log.e("SuccessScreen", "✗ Failed to start HeartbeatVerificationService: ${e.message}", e)
    }
}
