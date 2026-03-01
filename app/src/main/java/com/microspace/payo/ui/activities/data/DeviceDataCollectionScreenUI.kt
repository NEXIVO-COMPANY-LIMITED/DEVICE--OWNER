package com.microspace.payo.ui.activities.data

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microspace.payo.data.models.registration.DeviceRegistrationRequest
import com.microspace.payo.ui.components.ErrorMessageCard

/**
 * Device data collection screen with editable loan number
 * Loan number displays as text, becomes editable when clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDataCollectionScreenWithEditableLoan(
    loanNumber: String,
    onLoanNumberChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean = false,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    data: DeviceRegistrationRequest? = null,
    submissionStatus: String = "PENDING"
) {
    val scrollState = rememberScrollState()
    var isEditingLoan by remember { mutableStateOf(false) }
    var editableLoanNumber by remember { mutableStateOf(loanNumber) }
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1976D2),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Device",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Device Registration",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    
                    // Loan Number - Display or Edit
                    if (isEditingLoan) {
                        // Edit Mode
                        OutlinedTextField(
                            value = editableLoanNumber,
                            onValueChange = { editableLoanNumber = it.trim().uppercase() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = "Loan",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (editableLoanNumber.isNotEmpty() && editableLoanNumber != loanNumber) {
                                                onLoanNumberChange(editableLoanNumber)
                                            }
                                            isEditingLoan = false
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Save",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editableLoanNumber = loanNumber
                                            isEditingLoan = false
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        // Display Mode - Click to Edit
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable { isEditingLoan = true },
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = "Loan",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Loan Number",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = loanNumber,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Error Message
                errorMessage?.let { msg ->
                    ErrorMessageCard(
                        message = msg,
                        isRetryable = true
                    )
                }

                // Device Data Display
                if (data != null) {
                    DeviceDataDisplayCard(data = data)
                }

                // Loading State
                if (isLoading) {
                    LoadingCard()
                }

                Spacer(modifier = Modifier.height(100.dp))
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = data != null && !isLoading && !isSubmitting && !isEditingLoan,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2),
                        disabledContainerColor = Color(0xFFBDBDBD)
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registering...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Submit",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Register Device")
                    }
                }
                
                if (submissionStatus == "ERROR") {
                    Text(
                        text = "ðŸ’¡ Tip: Click on loan number to edit and try again",
                        fontSize = 12.sp,
                        color = Color(0xFF1976D2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Card displaying collected device data
 */
@Composable
fun DeviceDataDisplayCard(data: DeviceRegistrationRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "âœ… Device Information Collected",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2E7D32)
            )
            
            val deviceInfo = data.deviceInfo ?: emptyMap()
            val androidInfo = data.androidInfo ?: emptyMap()
            val storageInfo = data.storageInfo ?: emptyMap()
            
            // Device Details
            DeviceInfoRow("Device", "${deviceInfo["manufacturer"]} ${deviceInfo["model"]}")
            DeviceInfoRow("OS Version", androidInfo["version_release"]?.toString() ?: "Unknown")
            DeviceInfoRow("RAM", storageInfo["installed_ram"]?.toString() ?: "Unknown")
            DeviceInfoRow("Storage", storageInfo["total_storage"]?.toString() ?: "Unknown")
            
            Divider(modifier = Modifier.fillMaxWidth())
            
            Text(
                text = "Ready to register. Click 'Register Device' to proceed.",
                fontSize = 12.sp,
                color = Color(0xFF616161),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

/**
 * Device info row
 */
@Composable
fun DeviceInfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF757575)
        )
        Text(
            text = value ?: "N/A",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF212121)
        )
    }
}

/**
 * Loading card
 */
@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color(0xFF1976D2),
                strokeWidth = 3.dp
            )
            Text(
                text = "Collecting device information...",
                fontSize = 13.sp,
                color = Color(0xFF616161)
            )
        }
    }
}




