package com.example.deviceowner.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.deviceowner.data.local.database.dao.BasicLoanInfo
import com.example.deviceowner.data.repository.DeviceRegistrationRepository
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.services.HeartbeatService
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var deviceOwnerManager: DeviceOwnerManager
    private lateinit var registrationRepository: DeviceRegistrationRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        deviceOwnerManager = DeviceOwnerManager(this)
        registrationRepository = DeviceRegistrationRepository(this)

        setContent {
            DeviceOwnerTheme {
                MainScreen()
            }
        }
    }
    
    @Composable
    fun MainScreen() {
        var loanInfo by remember { mutableStateOf<BasicLoanInfo?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var isDeviceOwner by remember { mutableStateOf(false) }
        var storedLoanNumber by remember { mutableStateOf<String?>(null) }
        
        // Load loan information
        LaunchedEffect(Unit) {
            lifecycleScope.launch {
                try {
                    // Check device owner status
                    isDeviceOwner = deviceOwnerManager.isDeviceOwner()
                    
                    // Load basic loan info from database
                    loanInfo = registrationRepository.getBasicLoanInfo()
                    
                    // Get stored loan number from registration data
                    storedLoanNumber = loanInfo?.loanNumber
                    
                    // Start heartbeat service if device is registered and is Device Owner
                    if (loanInfo != null && isDeviceOwner) {
                        try {
                            val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                            HeartbeatService.startService(this@MainActivity, deviceId)
                            Log.d(TAG, "Heartbeat service started from MainActivity")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to start heartbeat service: ${e.message}")
                        }
                    }
                    
                    isLoading = false
                    
                    Log.d(TAG, "Loan info loaded: ${loanInfo?.loanNumber}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading loan info: ${e.message}", e)
                    isLoading = false
                }
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Device Loan Manager",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Your device loan information",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                
                if (isLoading) {
                    // Loading State
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    loanInfo?.let { info ->
                        // Loan Information Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Loan Information",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                InfoRow("Loan Number", info.loanNumber)
                                
                                info.nextPaymentDate?.let {
                                    InfoRow("Next Payment", it)
                                }
                                
                                info.totalAmount?.let {
                                    InfoRow("Total Amount", formatCurrency(it))
                                }
                                
                                info.paidAmount?.let {
                                    InfoRow("Paid Amount", formatCurrency(it))
                                }
                                
                                info.remainingAmount?.let {
                                    InfoRow("Remaining Amount", formatCurrency(it))
                                }
                            }
                        }
                        
                        // Device Status Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDeviceOwner) 
                                    MaterialTheme.colorScheme.secondaryContainer 
                                else 
                                    MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDeviceOwner) Icons.Default.Security else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isDeviceOwner) 
                                            MaterialTheme.colorScheme.secondary 
                                        else 
                                            MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Device Status",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDeviceOwner) 
                                            MaterialTheme.colorScheme.secondary 
                                        else 
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                                
                                Text(
                                    text = if (isDeviceOwner) 
                                        "Device is properly secured and managed" 
                                    else 
                                        "Device management not active",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                    } ?: run {
                        // No loan information available
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = "No Loan Information",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                
                                Text(
                                    text = "Device is not registered or loan information is not available.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // View registration errors (DeviceOwner folder - helps understand cause and fix)
                OutlinedButton(
                    onClick = {
                        startActivity(Intent(this@MainActivity, RegistrationErrorViewerActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View registration errors")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SSL Certificate Extractor (for getting certificate pins)
                OutlinedButton(
                    onClick = {
                        startActivity(Intent(this@MainActivity, com.example.deviceowner.ui.activities.CertificateExtractorTestActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extract SSL Certificate Pin")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Local JSON Data Server Access
                OutlinedButton(
                    onClick = {
                        // Open browser to local data server
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://localhost:8080"))
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(this@MainActivity, "Local Data Server: http://localhost:8080", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Local Data Server")
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                // Footer
                Text(
                    text = "This device is managed under a loan agreement. Unauthorized modifications are prohibited.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    @Composable
    fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
    
    private fun formatCurrency(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
        return formatter.format(amount)
    }
}