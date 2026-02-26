package com.microspace.payo.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PIN Entry Screen for Hard Lock Unlock
 *
 * User enters PIN via visible numeric keypad (0-9)
 * PIN is NOT displayed on screen (shown as dots)
 * System verifies against local database
 * If correct, device unlocks automatically
 */
@Composable
fun PinEntryScreen(
    onPinSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    isVerifying: Boolean = false,
    errorMessage: String? = null
) {
    var pinInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFFCC0000)  // Red background
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
            Text(
                text = "🔑 ENTER PIN",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )

            Text(
                text = "Enter your unlock PIN to access device",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ============ PIN DISPLAY (DOTS) ============
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color(0xFF770000),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "•".repeat(pinInput.length),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    letterSpacing = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }

            // ============ ERROR MESSAGE ============
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color(0xFF550000),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "❌ $errorMessage",
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============ NUMERIC KEYPAD ============
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
                    NumericKeyButton("1", pinInput, { pinInput = it })
                    NumericKeyButton("2", pinInput, { pinInput = it })
                    NumericKeyButton("3", pinInput, { pinInput = it })
                }

                // Row 2: 4 5 6
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumericKeyButton("4", pinInput, { pinInput = it })
                    NumericKeyButton("5", pinInput, { pinInput = it })
                    NumericKeyButton("6", pinInput, { pinInput = it })
                }

                // Row 3: 7 8 9
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumericKeyButton("7", pinInput, { pinInput = it })
                    NumericKeyButton("8", pinInput, { pinInput = it })
                    NumericKeyButton("9", pinInput, { pinInput = it })
                }

                // Row 4: 0 Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumericKeyButton("0", pinInput, { pinInput = it }, modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (pinInput.isNotEmpty()) {
                                pinInput = pinInput.dropLast(1)
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

            Spacer(modifier = Modifier.weight(1f))

            // ============ ACTION BUTTONS ============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Submit Button
                Button(
                    onClick = {
                        if (pinInput.isNotEmpty()) {
                            onPinSubmit(pinInput)
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
                    enabled = pinInput.isNotEmpty() && !isVerifying
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

                // Cancel Button
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF999999)
                    ),
                    enabled = !isVerifying
                ) {
                    Text(
                        "CANCEL",
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
    currentPin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier.weight(1f)
) {
    Button(
        onClick = {
            if (currentPin.length < 12) {
                onPinChange(currentPin + number)
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
