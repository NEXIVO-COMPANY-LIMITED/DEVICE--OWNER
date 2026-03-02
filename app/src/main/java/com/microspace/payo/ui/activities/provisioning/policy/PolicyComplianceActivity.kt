package com.microspace.payo.ui.activities.provisioning.policy

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
 * Activity to handle Android 12+ policy compliance during provisioning.
 * Fixed: Added explicit result handling and more robust lifecycle management.
 */
class PolicyComplianceActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PolicyCompliance"
        const val ACTION_ADMIN_POLICY_COMPLIANCE = "android.app.action.ADMIN_POLICY_COMPLIANCE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CRITICAL: Send RESULT_OK immediately to satisfy the system handshake
        // This must happen FIRST before ANY other operations
        setResult(Activity.RESULT_OK)
        Log.d(TAG, "✅ Policy compliance handshake: RESULT_OK sent")
        
        // Close immediately - Setup Wizard needs to continue
        finish()
    }
}

@Composable
private fun PolicyComplianceScreen(onContinue: () -> Unit) {
    var isProcessing by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }

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
                WarningCard()
                Spacer(modifier = Modifier.height(14.dp))
                InfoCard()
                Spacer(modifier = Modifier.height(14.dp))
                PolicyListCard()
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Bottom Action Bar
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "I acknowledge that PAYO will manage this device for security compliance.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }

                Button(
                    onClick = {
                        isProcessing = true
                        onContinue()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = agreedToTerms && !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Continue Setup", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyHeader() {
    Box(
        modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1976D2), Color(0xFF0288D1)))).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Security Compliance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "This device will be managed by PAYO. You will see a prompt about diagnostic data; this is normal for enterprise setup.",
                fontSize = 12.sp,
                color = Color(0xFF78350F)
            )
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Policy Review", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Your device is being configured with security policies to protect company data.", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PolicyListCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PolicyItem("Remote Management Active")
            PolicyItem("Device Security Hardening")
            PolicyItem("Restricted App Installation")
        }
    }
}

@Composable
private fun PolicyItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 13.sp)
    }
}
