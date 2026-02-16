package com.example.deviceowner.ui.activities.provisioning.consent

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.presentation.activities.DeviceRegistrationActivity
import com.example.deviceowner.ui.theme.DeviceOwnerTheme

/**
 * Data Privacy Consent screen - shown on first app launch after install.
 * User must accept terms before proceeding to loan number input and registration.
 */
class DataPrivacyConsentActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PrivacyConsent"
        const val PREFS_CONSENT = "data_privacy_consent"
        const val KEY_CONSENT_GIVEN = "consent_given"

        /** Check if user has already given consent. */
        fun hasConsent(context: android.content.Context): Boolean {
            return context.getSharedPreferences(PREFS_CONSENT, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_CONSENT_GIVEN, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        setContent {
            DeviceOwnerTheme {
                DataPrivacyConsentScreen(
                    onAccept = { markConsentAndProceed() },
                    onDecline = {
                        Log.w(TAG, "User declined privacy consent.")
                        Toast.makeText(this, "Setup cannot continue without consent.", Toast.LENGTH_LONG).show()
                        finishAffinity()
                    }
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@DataPrivacyConsentActivity, "Please Accept or Decline to proceed.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun markConsentAndProceed() {
        Log.i(TAG, "User accepted privacy consent. Proceeding to loan number input.")
        getSharedPreferences(PREFS_CONSENT, MODE_PRIVATE).edit()
            .putBoolean(KEY_CONSENT_GIVEN, true)
            .apply()

        val intent = Intent(this, DeviceRegistrationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

}

@Composable
private fun DataPrivacyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8FAFC),
                        Color(0xFFF1F5F9)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -20 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Privacy & Data Collection",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "PAYO requires certain device information to provide secure management services.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Info cards
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 30 })
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Information We Collect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        ConsentItem(
                            icon = Icons.Default.Info,
                            title = "Device Identifiers",
                            description = "IMEI, Serial Number, and hardware IDs to uniquely identify your device."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ConsentItem(
                            icon = Icons.Default.Info,
                            title = "System & Software",
                            description = "Android version, build number, and security patch levels."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ConsentItem(
                            icon = Icons.Default.Info,
                            title = "Management Status",
                            description = "Connectivity status and security policy compliance data."
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(visible = visible, enter = fadeIn()) {
                Text(
                    "These identifiers link this device to your account, enable security updates, and ensure compliance with PAYO policies.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Actions
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically(initialOffsetY = { 40 })) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("I Accept & Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = onDecline,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Decline & Exit",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConsentItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF4F46E5)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
            Text(
                description,
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                lineHeight = 20.sp
            )
        }
    }
}
