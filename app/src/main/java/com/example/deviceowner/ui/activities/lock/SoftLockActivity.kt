package com.example.deviceowner.ui.activities.lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.ui.theme.DeviceOwnerTheme

/**
 * Soft Lock Activity - A "Better Warning" screen
 * This warns the user but allows them to dismiss and continue (Soft Lock)
 */
class SoftLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = intent.getStringExtra("lock_reason") ?: "Payment Reminder"
        
        setContent {
            DeviceOwnerTheme {
                SoftLockWarningScreen(reason = reason) {
                    finish() // User can dismiss the warning
                }
            }
        }
    }
}

@Composable
fun SoftLockWarningScreen(reason: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF9C4)), // Light Yellow Warning background
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFFFBC02D)
            )
            
            Text(
                text = "PAYMENT REMINDER",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Text(
                text = reason,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = Color.DarkGray
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("I UNDERSTAND", color = Color.White)
            }
        }
    }
}
