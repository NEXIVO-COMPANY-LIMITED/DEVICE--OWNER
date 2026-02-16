package com.example.deviceowner.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * SOFT LOCK - PAYMENT REMINDER SCREEN
 *
 * Shown 1 day BEFORE payment due date.
 * Unlock: NO PASSWORD – user taps dismiss/Continue only.
 * Device is NOT locked; normal operation continues after dismiss.
 *
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
    val accentColor = Color(0xFFFFFFFF) // White text
    val bgColor = Color(0xFFFFD700)     // Yellow background
    val cardBgColor = Color(0xFFFFC700) // Darker yellow cards
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgColor, Color(0xFFFFB700)),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
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
            
            // ============ HEADER ============
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Clock icon
                Text(
                    text = "⏰",
                    fontSize = 64.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
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
            
            // ============ MAIN MESSAGE CARD ============
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                color = cardBgColor,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = accentColor.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Dear Customer,",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "You are reminded to settle your loan by ${LockScreenStrategy.formatDueDate(nextPaymentDate)}. PLEASE MAKE payment on time to stay connected.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    
                    Text(
                        text = "Thank you.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ============ DISMISS BUTTON ============
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text(
                    "I UNDERSTAND - CONTINUE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * HARD LOCK - PAYMENT OVERDUE SCREEN
 * 
 * Shown ON or AFTER payment due date
 * Purpose: Lock device until payment is made
 * Shows unlock button for PIN entry
 * Device is LOCKED - user cannot use device
 * 
 * Colors: Red background, White text
 */
@Composable
fun HardLockPaymentOverdueScreen(
    nextPaymentDate: String,
    unlockPassword: String?,
    shopName: String?,
    daysOverdue: Long,
    hoursOverdue: Long,
    onUnlockClick: () -> Unit = {}
) {
    val accentColor = Color(0xFFFFFFFF) // White text
    val bgColor = Color(0xFFCC0000)     // Red background
    val headerBgColor = Color(0xFF990000) // Darker red
    val cardBgColor = Color(0xFFBB0000) // Medium red
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgColor, Color(0xFF990000)),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // ============ HEADER BANNER ============
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                color = headerBgColor,
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Lock icon
                    Text(
                        text = "🔒",
                        fontSize = 72.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "PAYMENT OVERDUE",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        letterSpacing = 3.sp
                    )
                }
            }
            
            // ============ MAIN CONTENT ============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                
                // ============ MAIN MESSAGE CARD ============
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    color = cardBgColor,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = accentColor.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "DEAR CUSTOMER",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp
                        )
                        
                        Text(
                            text = "YOUR LOAN PAYMENT IS OVERDUE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        )
                        
                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            color = accentColor.copy(alpha = 0.3f),
                            thickness = 1.dp
                        )
                        
                        Text(
                            text = "Please settle your outstanding loan payment immediately to maintain access to your device. Failure to pay may result in permanent device lock.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // ============ UNLOCK BUTTON ============
            if (unlockPassword != null) {
                Button(
                    onClick = onUnlockClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text(
                        "UNLOCK WITH PIN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
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
 * 
 * Shown when security violation is detected
 * Purpose: Lock device due to security issues
 * Device is LOCKED - user cannot use device
 * 
 * Displays: Title, specific reason, shop name (if available), and clear call-to-action
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgColor, headerBgColor),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = headerBgColor,
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "🔒", fontSize = 64.sp, modifier = Modifier.padding(bottom = 4.dp))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 24.dp),
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "DEAR CUSTOMER",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            letterSpacing = 1.sp
                        )
                        Divider(color = accentColor.copy(alpha = 0.3f), thickness = 1.dp)
                        Text(
                            text = formattedReason,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "For your protection, this device has been locked and requires administrator verification to unlock.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor.copy(alpha = 0.95f),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Divider(color = accentColor.copy(alpha = 0.2f), thickness = 1.dp)
                        Text(
                            text = contactMessage,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
