package com.example.deviceowner.presentation.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.helpers.LoanNumberValidator

class DeviceRegistrationActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeviceOwnerTheme {
                LoanNumberInputScreen(
                    onLoanNumberSubmitted = { loanNumber ->
                        navigateToDataCollection(loanNumber)
                    }
                )
            }
        }
    }
    
    private fun navigateToDataCollection(loanNumber: String) {
        val intent = Intent(this, DeviceDataCollectionActivity::class.java)
        intent.putExtra("loan_number", loanNumber)
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanNumberInputScreen(
    onLoanNumberSubmitted: (String) -> Unit
) {
    var loanNumber by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Real-time validation
    LaunchedEffect(loanNumber) {
        val validation = LoanNumberValidator.validateLoanNumber(loanNumber)
        isValid = validation.isValid
        errorMessage = validation.message
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Device Registration",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Subtitle
            Text(
                text = "Enter your loan number to register this device",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Loan Number Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Loan Number",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = loanNumber,
                        onValueChange = { newValue ->
                            // Auto-format as user types
                            loanNumber = LoanNumberValidator.formatLoanNumber(newValue)
                        },
                        label = { Text("LN-YYYYMMDD-NNNNN") },
                        placeholder = { Text("LN-20260128-00001") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        isError = loanNumber.isNotEmpty() && !isValid,
                        supportingText = {
                            if (loanNumber.isNotEmpty() && !isValid) {
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (isValid) {
                                Text(
                                    text = "✓ Valid loan number",
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Format Info
                    Text(
                        text = "Format: LN-YYYYMMDD-NNNNN\n• LN: Loan prefix\n• YYYYMMDD: Date (e.g., 20260128)\n• NNNNN: 5-digit number",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 16.sp
                    )
                }
            }
            
            // Submit Button
            Button(
                onClick = { onLoanNumberSubmitted(loanNumber) },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Continue to Device Data Collection",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Help Text
            Text(
                text = "Your device information will be collected and linked to this loan number for security purposes.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}