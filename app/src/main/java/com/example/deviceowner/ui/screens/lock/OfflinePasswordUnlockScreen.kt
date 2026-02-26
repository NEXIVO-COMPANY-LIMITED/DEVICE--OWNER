package com.microspace.payo.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Offline Password Unlock Screen for Hard Lock
 *
 * Shown when device is hard-locked due to payment overdue and user is offline.
 * User enters password that was sent via heartbeat during last online connection.
 *
 * Features:
 * - Numeric keypad (0-9) for easy input
 * - Password displayed as dots for security
 * - Backspace to delete characters
 * - Submit button to verify password
 * - Error message if password is incorrect
 * - Retry counter to prevent brute force
 *
 * Password Verification:
 * - Compares entered password with password stored locally
 * - If correct → unlock device
 * - If incorrect → show error, allow retry
 * - After 3 failed attempts → show contact support message
 */
@Composable
fun OfflinePasswordUnlockScreen(
    onPasswordSubmit: (String) -> Unit,
    onContactSupport: () -> Unit,
    isVerifying: Boolean = false,
    errorMessage: String? = null,
    attemptCount: Int = 0,
    maxAttempts: Int = 3
) {
    var passwordInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var localErrorMessage by remember { mutableStateOf(errorMessage) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            showError = true
            localErrorMessage = errorMessage
        }
    }

    val accentColor = Color(0xFFFFFFFF) // White text
    val bgColor = Color(0xFFCC0000)     // Red background
    val headerBgColor = Color(0xFF990000) // Darker red
    val cardBgColor = Color(0xFFBB0000) // Medium red

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(bgColor, Color(0xFF990000)),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ============ HEADER ============
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔐",
                    fontSize = 64.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "ENTER PASSWORD",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Enter the password sent to your phone",
                    fontSize = 14.sp,
                    color = accentColor.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============ PASSWORD DISPLAY (DOTS) ============
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color(0xFF770000),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = accentColor.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "•".repeat(passwordInput.length),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }

            // ============ ERROR MESSAGE ============
            if (showError && localErrorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color(0xFF550000),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "❌ $localErrorMessage",
                        fontSize = 12.sp,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // ============ ATTEMPT COUNTER ============
            if (attemptCount > 0) {
                Text(
                    text = "Attempts: $attemptCount / $maxAttempts",
                    fontSize = 12.sp,
                    color = if (attemptCount >= maxAttempts - 1) Color(0xFFFFFF00) else accentColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ============ NUMERIC KEYPAD ============
            if (attemptCount < maxAttempts) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: 1 2 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumericKeyButton("1", passwordInput, { passwordInput = it })
                        NumericKeyButton("2", passwordInput, { passwordInput = it })
                        NumericKeyButton("3", passwordInput, { passwordInput = it })
                    }

                    // Row 2: 4 5 6
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumericKeyButton("4", passwordInput, { passwordInput = it })
                        NumericKeyButton("5", passwordInput, { passwordInput = it })
                        NumericKeyButton("6", passwordInput, { passwordInput = it })
                    }

                    // Row 3: 7 8 9
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumericKeyButton("7", passwordInput, { passwordInput = it })
                        NumericKeyButton("8", passwordInput, { passwordInput = it })
                        NumericKeyButton("9", passwordInput, { passwordInput = it })
                    }

                    // Row 4: 0 Backspace
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumericKeyButton("0", passwordInput, { passwordInput = it }, modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                if (passwordInput.isNotEmpty()) {
                                    passwordInput = passwordInput.dropLast(1)
                                    showError = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B6B)
                            ),
                            enabled = !isVerifying
                        ) {
                            Text(
                                "⌫",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ============ ACTION BUTTONS ============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (attemptCount < maxAttempts) {
                    // Submit Button
                    Button(
                        onClick = {
                            if (passwordInput.isNotEmpty()) {
                                onPasswordSubmit(passwordInput)
                                passwordInput = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                        ),
                        enabled = passwordInput.isNotEmpty() && !isVerifying
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "UNLOCK DEVICE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // Max attempts reached - show contact support
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        color = Color(0xFF550000),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = accentColor.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "⚠️ Maximum attempts reached",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Please contact support to unlock your device",
                                fontSize = 12.sp,
                                color = accentColor.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Contact Support Button
                Button(
                    onClick = onContactSupport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text(
                        "📞 CONTACT SUPPORT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Numeric Key Button for Keypad
 */
@Composable
private fun RowScope.NumericKeyButton(
    number: String,
    currentPassword: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier.weight(1f)
) {
    Button(
        onClick = {
            if (currentPassword.length < 20) {
                onPasswordChange(currentPassword + number)
            }
        },
        modifier = modifier
            .height(60.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF770000)
        )
    ) {
        Text(
            number,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
