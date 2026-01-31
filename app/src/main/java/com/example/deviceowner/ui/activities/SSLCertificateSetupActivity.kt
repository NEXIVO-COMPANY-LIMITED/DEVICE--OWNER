package com.example.deviceowner.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.deviceowner.security.ssl.DeviceOwnerSSLManager
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.SSLCertificateExtractor
import kotlinx.coroutines.launch

/**
 * SSL Certificate Setup Activity for Device Owner
 * Helps configure and validate SSL certificates for maximum security
 */
class SSLCertificateSetupActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            DeviceOwnerTheme {
                SSLCertificateSetupScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SSLCertificateSetupScreen() {
        val scope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(false) }
        var certificateInfo by remember { mutableStateOf<com.example.deviceowner.utils.CertificateInfo?>(null) }
        var sslResult by remember { mutableStateOf<com.example.deviceowner.security.ssl.SSLSecurityResult?>(null) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "🔒 SSL Certificate Setup",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "Configure perfect SSL security for Device Owner mode",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val extractor = SSLCertificateExtractor()
                                certificateInfo = extractor.extractCertificateInfo("payoplan.com")
                            } catch (e: Exception) {
                                Toast.makeText(this@SSLCertificateSetupActivity, "Failed to extract certificate: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Extract Certificate")
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val sslManager = DeviceOwnerSSLManager(this@SSLCertificateSetupActivity)
                                sslResult = sslManager.initializeSSLSecurity()
                            } catch (e: Exception) {
                                Toast.makeText(this@SSLCertificateSetupActivity, "SSL initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test SSL Security")
                }
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            // Certificate Information
            certificateInfo?.let { certInfo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "📜 Certificate Information",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        if (certInfo.error != null) {
                            Text(
                                text = "❌ Error: ${certInfo.error}",
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                        } else {
                            CertificateInfoItem("Subject", certInfo.subject)
                            CertificateInfoItem("Issuer", certInfo.issuer)
                            CertificateInfoItem("Valid From", certInfo.validFrom)
                            CertificateInfoItem("Valid Until", certInfo.validUntil)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "🔑 SHA-256 Certificate Pin:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            
                            SelectionContainer {
                                Text(
                                    text = certInfo.leafCertificatePin,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color.Blue,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // SSL Security Result
            sslResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "🔒 SSL Security Analysis",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        if (result.error != null) {
                            Text(
                                text = "❌ Error: ${result.error}",
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                        } else {
                            SecurityStatusItem("Initialization", result.initializationSuccessful)
                            SecurityStatusItem("SSL Configuration", result.sslConfigurationValid)
                            SecurityStatusItem("Device Owner Compatible", result.deviceOwnerCompatible)
                            SecurityStatusItem("Overall Security", result.isSecure)
                            
                            if (result.tlsVersion.isNotEmpty()) {
                                Text(
                                    text = "🔐 TLS Version: ${result.tlsVersion}",
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Security Recommendations
            sslResult?.securityRecommendations?.let { recommendations ->
                if (recommendations.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "💡 Security Recommendations",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn {
                                items(recommendations) { recommendation ->
                                    Text(
                                        text = recommendation,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun CertificateInfoItem(label: String, value: String) {
        if (value.isNotEmpty()) {
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = "$label:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = value,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
    
    @Composable
    fun SecurityStatusItem(label: String, status: Boolean) {
        Row(
            modifier = Modifier.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (status) "✅" else "❌",
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$label: ${if (status) "PASSED" else "FAILED"}",
                fontSize = 14.sp,
                color = if (status) Color.Green else Color.Red
            )
        }
    }
}