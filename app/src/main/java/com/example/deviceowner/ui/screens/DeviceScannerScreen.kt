package com.example.deviceowner.ui.screens

import android.Manifest
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deviceowner.utils.ValidationUtils
import com.deviceowner.utils.ErrorHandler
import com.example.deviceowner.camera.LiveBarcodeScanner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DeviceScannerScreen(
    loanId: String,
    onScanComplete: (imeiList: List<String>, serialNumber: String) -> Unit,
    onBackToLogin: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var imeiList by remember { mutableStateOf(listOf("")) }
    var serialNumber by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showScanner by remember { mutableStateOf(false) }
    var scanType by remember { mutableStateOf(ScanType.IMEI) }
    var currentImeiIndex by remember { mutableStateOf(0) }
    var lastScanValue by remember { mutableStateOf<String?>(null) }
    var lastScanTime by remember { mutableStateOf(0L) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val onBarcodeDetected: (String) -> Unit = { barcode ->
        val now = System.currentTimeMillis()
        if (barcode != lastScanValue || now - lastScanTime >= 500) {
            lastScanValue = barcode
            lastScanTime = now

            when (scanType) {
                ScanType.IMEI -> {
                    imeiList = imeiList.toMutableList().apply {
                        set(currentImeiIndex, barcode)
                    }
                    statusMessage = "IMEI ${currentImeiIndex + 1} scanned successfully"
                }
                ScanType.SERIAL -> {
                    serialNumber = barcode
                    statusMessage = "Serial Number scanned successfully"
                }
            }
            showScanner = false
        }
    }

    /* ===== SCANNER DIALOG ===== */
    if (showScanner) {
        when (cameraPermissionState.status) {
            PermissionStatus.Granted -> {
                ScannerDialog(
                    title = when (scanType) {
                        ScanType.IMEI -> "Scan IMEI ${currentImeiIndex + 1}"
                        ScanType.SERIAL -> "Scan Serial Number"
                    },
                    onClose = { showScanner = false },
                    onDetected = onBarcodeDetected
                )
            }
            is PermissionStatus.Denied -> {
                if ((cameraPermissionState.status as PermissionStatus.Denied).shouldShowRationale) {
                    CameraPermissionRationaleDialog(
                        onConfirm = {
                            cameraPermissionState.launchPermissionRequest()
                        },
                        onDismiss = {
                            showScanner = false
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .imePadding()
    ) {
        /* ===== BACK BUTTON (FIXED TOP) ===== */
        IconButton(
            onClick = onBackToLogin,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .zIndex(10f)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = colors.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        /* ===== SCROLLABLE CENTER CONTENT ===== */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            /* ===== TITLE SECTION ===== */
            Text(
                text = "Scan Device",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Scan IMEI and Serial Number to register device",
                fontSize = 16.sp,
                color = colors.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(40.dp))

            /* ===== INFO CARD ===== */
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E88E5)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Device Information",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                "Loan ID: $loanId",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                    
                    Divider(
                        color = Color.White.copy(alpha = 0.3f),
                        thickness = 1.5.dp
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InfoRequirement("Tap fields to scan (manual input disabled)", false, isOnBlue = true)
                        InfoRequirement("First IMEI and Serial Number are required", true, isOnBlue = true)
                        InfoRequirement("Add multiple IMEIs for eSIM devices", false, isOnBlue = true)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            /* ===== STATUS MESSAGE ===== */
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            statusMessage ?: "",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            /* ===== ERROR MESSAGE ===== */
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            errorMessage ?: "",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            /* ===== PROGRESS CARD ===== */
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProgressIndicatorCard(
                        imeiFilled = imeiList.firstOrNull()?.isNotBlank() == true,
                        serialFilled = serialNumber.isNotBlank(),
                        colors = colors
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            /* ===== FORM FIELDS ===== */
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // IMEI Fields
                imeiList.forEachIndexed { index, imei ->
                    ScanInputField(
                        label = if (index == 0) "IMEI Number (Required)" else "IMEI Number ${index + 1} (Optional)",
                        value = imei,
                        icon = Icons.Default.Phone,
                        onClick = {
                            currentImeiIndex = index
                            scanType = ScanType.IMEI
                            showScanner = true
                        },
                        onClear = {
                            imeiList = imeiList.toMutableList().apply {
                                if (index == 0) {
                                    set(index, "")
                                } else {
                                    removeAt(index)
                                }
                            }
                        },
                        colors = colors
                    )
                }

                // Add Another IMEI Button (only shows when last IMEI is filled)
                if (imeiList.last().isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            imeiList = imeiList + ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            colors.primary
                        )
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Another IMEI", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                ScanInputField(
                    label = "Serial Number",
                    value = serialNumber,
                    icon = Icons.Default.Build,
                    onClick = {
                        scanType = ScanType.SERIAL
                        showScanner = true
                    },
                    onClear = { serialNumber = "" },
                    colors = colors
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val validImeis = imeiList.filter { it.isNotBlank() }
                        
                        // Validate IMEI and Serial Number
                        val imeiValidation = if (validImeis.isNotEmpty()) {
                            ValidationUtils.validateIMEI(validImeis.first())
                        } else {
                            Pair(false, "EMPTY_FIELD")
                        }
                        
                        val serialValidation = ValidationUtils.validateSerialNumber(serialNumber)

                        when {
                            !imeiValidation.first -> {
                                errorMessage = ErrorHandler.getErrorMessage(imeiValidation.second)
                                statusMessage = null
                            }
                            !serialValidation.first -> {
                                errorMessage = ErrorHandler.getErrorMessage(serialValidation.second)
                                statusMessage = null
                            }
                            else -> {
                                errorMessage = null
                                Log.d("DeviceScannerScreen", "Passing data - imeiList: $validImeis, serialNumber: $serialNumber")
                                scope.launch {
                                    delay(400)
                                    onScanComplete(validImeis, serialNumber)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = imeiList.firstOrNull()?.isNotBlank() == true && serialNumber.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = Color.White,
                        disabledContainerColor = colors.primary.copy(alpha = 0.5f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Continue", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ----------------------------- UI COMPONENTS ----------------------------- */

@Composable
private fun InfoRequirement(text: String, error: Boolean = false, isOnBlue: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = "• $text",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = when {
            isOnBlue && error -> Color(0xFFFFCDD2)
            isOnBlue -> Color.White.copy(alpha = 0.95f)
            error -> colors.error
            else -> colors.onBackground
        }
    )
}

@Composable
private fun ProgressIndicatorCard(
    imeiFilled: Boolean,
    serialFilled: Boolean,
    colors: androidx.compose.material3.ColorScheme
) {
    val completed = listOf(imeiFilled, serialFilled).count { it }
    val progress = completed / 2f

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Scan Progress",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                color = colors.onSurface
            )
            Text(
                "${(progress * 100).toInt()}%",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = colors.primary
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            trackColor = colors.onSurface.copy(alpha = 0.08f),
            color = colors.primary
        )
    }
}

@Composable
private fun ScanInputField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onClear: () -> Unit,
    colors: androidx.compose.material3.ColorScheme
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    icon,
                    null,
                    tint = colors.primary,
                    modifier = Modifier.size(26.dp)
                )

                Column(Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text(
                            "Tap to scan",
                            fontSize = 16.sp,
                            color = colors.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            value,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                    }
                }

                if (value.isNotBlank()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            tint = colors.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerDialog(
    title: String,
    onClose: () -> Unit,
    onDetected: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {

            LiveBarcodeScanner(
                modifier = Modifier.fillMaxSize(),
                onBarcodeDetected = onDetected,
                isEnabled = true
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }

            Text(
                title,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CameraPermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera Permission Required") },
        text = { Text("This app needs camera access to scan QR codes and device identifiers. Please grant camera permission to continue.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class ScanType {
    IMEI,
    SERIAL
}
