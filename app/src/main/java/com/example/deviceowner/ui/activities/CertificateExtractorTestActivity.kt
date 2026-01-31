package com.example.deviceowner.ui.activities

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.SSLCertificateExtractor
import kotlinx.coroutines.launch

/**
 * Simple activity to extract SSL certificate pins
 * Use this to get the certificate pins for payoplan.com
 */
class CertificateExtractorTestActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "CertExtractorTest"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            DeviceOwnerTheme {
                CertificateExtractorScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CertificateExtractorScreen() {
        val scope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(false) }
        var certificatePin by remember { mutableStateOf("") }
        var certificateInfo by remember { mutableStateOf("") }
        var configurationCode by remember { mutableStateOf("") }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Text(
                text = "🔑 Certificate Pin Extractor",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Get SSL certificate pins for payoplan.com",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Extract Button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        try {
                            Log.d(TAG, "🔍 Starting certificate extraction...")
                            
                            val extractor = SSLCertificateExtractor()
                            
                            // Get certificate pin
                            val pin = extractor.getCertificatePinForConfiguration("payoplan.com")
                            certificatePin = pin
                            
                            // Get detailed info
                            val certInfo = extractor.extractCertificateInfo("payoplan.com")
                            
                            if (certInfo.error != null) {
                                certificateInfo = "❌ Error: ${certInfo.error}"
                                Toast.makeText(this@CertificateExtractorTestActivity, "Failed: ${certInfo.error}", Toast.LENGTH_LONG).show()
                            } else {
                                certificateInfo = buildString {
                                    appendLine("📜 Certificate Details:")
                                    appendLine("Subject: ${certInfo.subject}")
                                    appendLine("Issuer: ${certInfo.issuer}")
                                    appendLine("Valid From: ${certInfo.validFrom}")
                                    appendLine("Valid Until: ${certInfo.validUntil}")
                                    appendLine()
                                    appendLine("🔑 SHA-256 Pin: $pin")
                                }
                                
                                configurationCode = buildString {
                                    appendLine("// 1. Update network_security_config.xml:")
                                    appendLine("<pin digest=\"SHA-256\">$pin</pin>")
                                    appendLine()
                                    appendLine("// 2. Update ApiClient.kt:")
                                    appendLine(".add(\"payoplan.com\", \"sha256/$pin\")")
                                    appendLine(".add(\"api.payoplan.com\", \"sha256/$pin\")")
                                }
                                
                                Toast.makeText(this@CertificateExtractorTestActivity, "✅ Certificate extracted successfully!", Toast.LENGTH_LONG).show()
                            }
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Certificate extraction failed: ${e.message}", e)
                            certificateInfo = "❌ Error: ${e.message}"
                            Toast.makeText(this@CertificateExtractorTestActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Extract Certificate from payoplan.com")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Certificate Pin Result
            if (certificatePin.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "🎯 Certificate Pin (Copy This!)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SelectionContainer {
                            Text(
                                text = certificatePin,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color.Blue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Certificate Information
            if (certificateInfo.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "📋 Certificate Information",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = certificateInfo,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Configuration Code
            if (configurationCode.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "🔧 Configuration Code (Copy & Paste)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Blue
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SelectionContainer {
                            Text(
                                text = configurationCode,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
            
            // Instructions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📖 Instructions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = """
                        1. Click "Extract Certificate" button above
                        2. Copy the certificate pin from the green box
                        3. Replace placeholder pins in your configuration files:
                           • network_security_config.xml
                           • ApiClient.kt
                        4. Build and test your app
                        
                        The certificate pin ensures secure communication with payoplan.com and prevents man-in-the-middle attacks.
                        """.trimIndent(),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}