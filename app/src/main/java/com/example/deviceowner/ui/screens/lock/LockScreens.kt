package com.microspace.payo.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)

/**
 * SOFT LOCK - PAYMENT REMINDER SCREEN
 *
 * Shown 1 day BEFORE payment due date.
 * Colors: Yellow background, White text
 */
@Composable
fun SoftLockReminderScreen(
    nextPaymentDate: String,
    daysUntilDue: Long,
    hoursUntilDue: Long,
    minutesUntilDue: Long,
    shopName: String?,
    onDismiss: () -> Unit
) {
    val accentColor = Color(0xFFFFFFFF)
    val bgColor = Color(0xFFFFD700)
    val cardBgColor = Color(0xFFFFC700)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgColor, Color(0xFFFFB700))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "⏰", fontSize = 64.sp)
                Text(
                    text = "PAYMENT REMINDER",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                color = cardBgColor,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, accentColor.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Attention:",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    
                    Text(
                        text = "Your upcoming payment installment is due on ${LockScreenStrategy.formatDueDate(nextPaymentDate)}. To maintain uninterrupted device service, please ensure your payment is completed.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                    
                    Text(
                        text = "Thank you.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("I UNDERSTAND - CONTINUE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * HARD LOCK - PAYMENT OVERDUE SCREEN
 * 
 * Includes professional PIN input for offline unlock.
 */
@Composable
fun HardLockPaymentOverdueScreen(
    nextPaymentDate: String,
    unlockPassword: String?,
    shopName: String?,
    daysOverdue: Long,
    hoursOverdue: Long,
    onUnlockAttempt: (String) -> Boolean
) {
    val accentColor = Color(0xFFFFFFFF)
    val bgColor = Color(0xFFCC0000)
    val headerBgColor = Color(0xFF990000)
    val cardBgColor = Color(0xFFBB0000)
    
    var pin by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColor, Color(0xFF990000))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER
            Surface(modifier = Modifier.fillMaxWidth(), color = headerBgColor) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🔒", fontSize = 72.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "PAYMENT OVERDUE",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        letterSpacing = 2.sp
                    )
                }
            }
            
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // WARNING CARD
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBgColor,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, accentColor.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "DEVICE LOCKED",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your loan payment is overdue. Please settle your installment immediately to restore device access.",
                            fontSize = 14.sp,
                            color = accentColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }

                // PIN INPUT SECTION - PERFECT & CLEAN
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Enter Secure Unlock PIN",
                            color = accentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = pin,
                            onValueChange = { 
                                if (it.length <= 8) {
                                    pin = it
                                    errorMsg = null 
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("Enter PIN code", color = accentColor.copy(alpha = 0.5f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = accentColor.copy(alpha = 0.5f),
                                focusedTextColor = accentColor,
                                unfocusedTextColor = accentColor,
                                cursorColor = accentColor
                            )
                        )

                        if (errorMsg != null) {
                            Text(
                                text = errorMsg!!,
                                color = Color.Yellow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (pin.isBlank()) return@Button
                                isVerifying = true
                                val success = onUnlockAttempt(pin)
                                if (!success) {
                                    errorMsg = "Incorrect PIN. Please check and try again."
                                    isVerifying = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isVerifying && pin.isNotBlank()
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentColor, strokeWidth = 3.dp)
                            } else {
                                Icon(Icons.Default.VpnKey, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("VALIDATE & UNLOCK", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                        }
                    }
                }
                
                Text(
                    text = "Contact support if you do not have your unlock code.",
                    fontSize = 12.sp,
                    color = accentColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Formats technical security reason into user-friendly display text
 */
private fun formatSecurityReason(reason: String): String {
    return when {
        reason.contains("Bootloader", ignoreCase = true) -> "Bootloader is unlocked."
        reason.contains("USB", ignoreCase = true) || reason.contains("ADB", ignoreCase = true) -> "USB debugging or ADB is enabled."
        reason.contains("Developer", ignoreCase = true) -> "Developer mode is enabled."
        reason.contains("Root", ignoreCase = true) -> "Unauthorized system modifications detected."
        reason.contains("TAMPER", ignoreCase = true) -> "Device tampering detected."
        reason.contains("Device Owner removed", ignoreCase = true) -> "Critical security breach detected."
        reason.contains("FIRMWARE", ignoreCase = true) -> "Firmware security violation."
        reason.contains("Custom ROM", ignoreCase = true) -> "Unauthorized operating system detected."
        reason.isNotBlank() -> reason
        else -> "Unauthorized modifications or security violations detected."
    }
}

/**
 * HARD LOCK - SECURITY VIOLATION SCREEN
 */
@Composable
fun HardLockSecurityViolationScreen(
    reason: String,
    shopName: String?
) {
    val accentColor = Color(0xFFFFFFFF)
    val bgColor = Color(0xFFCC0000)
    val headerBgColor = Color(0xFF990000)
    val cardBgColor = Color(0xFFBB0000)
    val formattedReason = formatSecurityReason(reason)
    val contactMessage = if (!shopName.isNullOrBlank()) {
        "Please visit $shopName for assistance to unlock your device."
    } else {
        "Please visit your provider or shop for assistance to unlock your device."
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColor, headerBgColor)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), color = headerBgColor) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "🔒", fontSize = 64.sp)
                    Text(
                        text = "SECURITY VIOLATION",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "DEVICE LOCKED",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBgColor,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, accentColor.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = "ATTENTION", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor, letterSpacing = 1.sp)
                        Divider(color = accentColor.copy(alpha = 0.3f), thickness = 1.dp)
                        Text(text = formattedReason, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = accentColor, textAlign = TextAlign.Center)
                        Text(text = "For your protection, this device has been locked and requires administrator verification to unlock.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accentColor.copy(alpha = 0.95f), textAlign = TextAlign.Center, lineHeight = 22.sp)
                        Divider(color = accentColor.copy(alpha = 0.2f), thickness = 1.dp)
                        Text(text = contactMessage, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = accentColor, textAlign = TextAlign.Center, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}
