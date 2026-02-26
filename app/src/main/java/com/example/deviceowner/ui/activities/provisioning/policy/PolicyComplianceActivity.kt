package com.microspace.payo.ui.activities.provisioning.policy

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import kotlinx.coroutines.delay

/**
 * Activity to handle Android 12+ policy compliance during provisioning
 * Required for QR code provisioning on Android 12+
 */
class PolicyComplianceActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PolicyComplianceActivity"
        const val ACTION_ADMIN_POLICY_COMPLIANCE = "android.app.action.ADMIN_POLICY_COMPLIANCE"
        private const val ORGANIZATION_NAME = "PAYO"
    }

    private var resultSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "========================================")
        Log.d(TAG, "PolicyComplianceActivity created")
        Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "========================================")

        val action = intent.action
        Log.d(TAG, "Intent action: $action")

        // Prevent back button from canceling provisioning
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed - ignoring to prevent provisioning cancellation")
            }
        })

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ACTION_ADMIN_POLICY_COMPLIANCE == action) {
                    Log.d(TAG, "Showing policy compliance UI for user confirmation")
                    Log.d(TAG, "NOTE: Diagnostic data agreement withdrawn is NORMAL")
                    setContent {
                        DeviceOwnerTheme {
                            PolicyComplianceScreen(onContinue = { handleContinue() })
                        }
                    }
                } else {
                    Log.w(TAG, "Unexpected intent action: $action")
                    setResult(Activity.RESULT_OK)
                    resultSet = true
                    finish()
                }
            } else {
                Log.d(TAG, "Android version < 12, finishing with RESULT_OK")
                setResult(Activity.RESULT_OK)
                resultSet = true
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in PolicyComplianceActivity", e)
            setResult(Activity.RESULT_OK)
            resultSet = true
            finish()
        }
    }

    private fun handleContinue() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "User confirmed policy compliance")
        Log.d(TAG, "Continuing provisioning...")
        Log.d(TAG, "NOTE: Diagnostic data withdrawal message is normal")
        Log.d(TAG, "========================================")
        try {
            setResult(Activity.RESULT_OK)
            resultSet = true
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing PolicyComplianceActivity", e)
            try {
                setResult(Activity.RESULT_OK)
                resultSet = true
            } catch (e2: Exception) {
                Log.e(TAG, "Error setting result: ${e2.message}")
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PolicyComplianceActivity destroyed")
        try {
            if (!resultSet) {
                Log.d(TAG, "Setting RESULT_OK as fallback")
                setResult(Activity.RESULT_OK)
                resultSet = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting result in onDestroy: ${e.message}")
        }
    }
}

@Composable
private fun PolicyComplianceScreen(onContinue: () -> Unit) {
    var isProcessing by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
            // Header
            PolicyHeader()

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(animationSpec = tween(400, delayMillis = 200)) +
                            slideInVertically(initialOffsetY = { it / 4 })
                ) {
                    Column {
                        // Warning Card
                        WarningCard()
                        Spacer(modifier = Modifier.height(14.dp))

                        // Info Card
                        InfoCard()
                        Spacer(modifier = Modifier.height(14.dp))

                        // Policy List
                        PolicyListCard()
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // Terms Agreement Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF1976D2),
                            uncheckedColor = Color(0xFF94A3B8)
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "I agree to the terms and acknowledge that PAYO will manage this device according to the policies listed above.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        isProcessing = true
                        onContinue()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = agreedToTerms && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2),
                        disabledContainerColor = Color(0xFFBBDEFB)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Continue Provisioning", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (agreedToTerms) {
                        "By clicking Continue, you confirm your agreement to these policies."
                    } else {
                        "Please agree to the terms and conditions to continue."
                    },
                    fontSize = 11.sp,
                    color = if (agreedToTerms) Color(0xFF94A3B8) else Color(0xFFF59E0B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 14.sp,
                    fontWeight = if (agreedToTerms) FontWeight.Normal else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PolicyHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1976D2),
                            Color(0xFF0288D1)
                        )
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.animateContentSize()
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Admin Policy Compliance",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Device Provisioning Setup",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Important Notice",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Diagnostic Data Collection Disabled",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF78350F)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This is normal and expected for Android Enterprise devices. The \"diagnostic data agreement withdrawn\" message indicates that diagnostic data collection has been disabled by policy.",
                    fontSize = 11.sp,
                    color = Color(0xFF78350F),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This does not indicate a provisioning failure. Click \"Continue\" to proceed.",
                    fontSize = 11.sp,
                    color = Color(0xFF78350F),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Policy Compliance Review",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your device is being configured with Android Enterprise policies to ensure security and compliance. Please review the following policies that will be applied to your device.",
                fontSize = 12.sp,
                color = Color(0xFF475569),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "These policies are required for device management by PAYO and cannot be modified by the user.",
                fontSize = 12.sp,
                color = Color(0xFF475569),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun PolicyListCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            PolicyItem(
                icon = Icons.Default.CheckCircle,
                title = "Device Security Enabled",
                description = "Security policies will be enforced by PAYO to protect your device and data"
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFCBD5E1))
            Spacer(modifier = Modifier.height(10.dp))

            PolicyItem(
                icon = Icons.Default.CheckCircle,
                title = "Diagnostic Data Disabled",
                description = "Android diagnostic data collection has been disabled as per enterprise policy requirements"
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFCBD5E1))
            Spacer(modifier = Modifier.height(10.dp))

            PolicyItem(
                icon = Icons.Default.CheckCircle,
                title = "Remote Management Active",
                description = "Your device will be managed remotely by PAYO administrators"
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFCBD5E1))
            Spacer(modifier = Modifier.height(10.dp))

            PolicyItem(
                icon = Icons.Default.CheckCircle,
                title = "App Restrictions Applied",
                description = "Certain apps and features may be restricted based on PAYO organizational policies"
            )
        }
    }
}

@Composable
private fun PolicyItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF16A34A),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFF475569),
                lineHeight = 16.sp
            )
        }
    }
}
