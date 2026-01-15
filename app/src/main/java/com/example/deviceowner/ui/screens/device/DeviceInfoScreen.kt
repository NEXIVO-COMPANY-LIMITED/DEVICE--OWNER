package com.example.deviceowner.ui.screens

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.deviceowner.utils.ValidationUtils
import com.deviceowner.utils.ErrorHandler
import com.deviceowner.utils.DeviceUtils
import com.deviceowner.utils.DeviceInfo
import com.example.deviceowner.data.repository.AuthRepository
import com.example.deviceowner.data.api.ApiResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    loanId: String,
    imeiList: List<String> = emptyList(),
    serialNumber: String = "",
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onRegistrationSuccess: (deviceId: String, loanId: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    
    // Log incoming parameters for debugging
    LaunchedEffect(Unit) {
        Log.d("DeviceInfoScreen", "Received - loanId: $loanId, imeiList: $imeiList, serialNumber: $serialNumber")
    }
    
    // Safely collect device info with error handling - only once
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var deviceFingerprint by remember { mutableStateOf("unknown_fingerprint") }
    var isDeviceInfoLoading by remember { mutableStateOf(true) }
    var deviceInfoError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        isDeviceInfoLoading = true
        deviceInfoError = null
        try {
            deviceInfo = DeviceUtils.collectDeviceInfo(context)
            Log.d("DeviceInfoScreen", "Device info collected successfully: $deviceInfo")
        } catch (e: Exception) {
            Log.e("DeviceInfoScreen", "Error collecting device info: ${e.message}", e)
            deviceInfoError = "Failed to collect device info: ${e.message}"
            deviceInfo = null
        }
        
        try {
            deviceFingerprint = DeviceUtils.generateDeviceFingerprint(context)
            Log.d("DeviceInfoScreen", "Device fingerprint generated successfully")
        } catch (e: Exception) {
            Log.e("DeviceInfoScreen", "Error generating fingerprint: ${e.message}", e)
            deviceFingerprint = "unknown_fingerprint"
        }
        
        isDeviceInfoLoading = false
    }
    
    val repository = remember { AuthRepository(context) }
    val scope = rememberCoroutineScope()

    var editableLoanId by remember { mutableStateOf(loanId) }
    var isEditingLoanInfo by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            TopAppBar(
                title = { Text("Device Information") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF5F5F5),
                    titleContentColor = colors.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Device Information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Loan Information Card (Editable)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                color = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Loan Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        IconButton(
                            onClick = { isEditingLoanInfo = !isEditingLoanInfo },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isEditingLoanInfo) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditingLoanInfo) "Save" else "Edit",
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isEditingLoanInfo) {
                        EditableFieldCard(
                            value = editableLoanId,
                            onValueChange = { editableLoanId = it },
                            label = "Loan ID",
                            icon = Icons.Default.CreditCard,
                            colorScheme = colors
                        )
                    } else {
                        ReadOnlyFieldCard(
                            label = "Loan ID",
                            value = editableLoanId,
                            icon = Icons.Default.CreditCard
                        )
                    }
                }
            }

            // Scanned Device Data Section
            InfoCard(title = "Scanned Device Data") {
                DataRow(label = "Android ID", value = deviceInfo?.androidId ?: "N/A")
                DataRow(label = "Serial Number", value = serialNumber.ifBlank { "Not scanned" })
                if (imeiList.isNotEmpty()) {
                    imeiList.forEachIndexed { index, imei ->
                        DataRow(
                            label = "IMEI ${index + 1}",
                            value = imei,
                            isLast = index == imeiList.size - 1
                        )
                    }
                } else {
                    DataRow(label = "IMEI", value = "Not scanned", isLast = true)
                }
            }

            // Device Info
            if (deviceInfo != null) {
                InfoCard(title = "Device Information") {
                    DataRow(label = "Manufacturer", value = deviceInfo?.manufacturer ?: "N/A")
                    DataRow(label = "Model", value = deviceInfo?.model ?: "N/A")
                    DataRow(label = "OS Version", value = "Android ${deviceInfo?.osVersion ?: "N/A"}")
                    DataRow(label = "SDK Level", value = "API ${deviceInfo?.sdkVersion ?: "N/A"}")
                    DataRow(label = "Build Number", value = deviceInfo?.buildNumber?.toString() ?: "N/A", isLast = true)
                }

                // Hardware Info
                InfoCard(title = "Hardware Specifications") {
                    DataRow(label = "Total Storage", value = deviceInfo?.totalStorage ?: "N/A")
                    DataRow(label = "Total RAM", value = deviceInfo?.totalRAM ?: "N/A", isLast = true)
                }

                // Battery Info
                InfoCard(title = "Battery Information") {
                    DataRow(label = "Battery Capacity", value = deviceInfo?.batteryInfo?.capacity ?: "N/A")
                    DataRow(
                        label = "Battery Technology",
                        value = deviceInfo?.batteryInfo?.technology ?: "N/A",
                        isLast = true
                    )
                }

                // Security & Fingerprint
                InfoCard(title = "Security & Fingerprint") {
                    DataRow(
                        label = "Device Fingerprint",
                        value = deviceFingerprint,
                        isMonospace = true,
                        isLast = true
                    )
                }
            }

            // Status Messages
            if (successMessage != null) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = successMessage ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (deviceInfoError != null) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = deviceInfoError ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Register Device Button
            Button(
                onClick = {
                    // Check if device info is available
                    if (deviceInfo == null) {
                        errorMessage = "Device information is still loading. Please wait..."
                        successMessage = null
                        return@Button
                    }
                    
                    // Validate loan info before registration
                    val loanIdValidation = ValidationUtils.validateLoanId(editableLoanId)

                    when {
                        !loanIdValidation.first -> {
                            errorMessage = ErrorHandler.getErrorMessage(loanIdValidation.second)
                            successMessage = null
                        }
                        imeiList.isEmpty() || imeiList.first().isBlank() -> {
                            errorMessage = "Please scan IMEI before registering device"
                            successMessage = null
                        }
                        serialNumber.isBlank() -> {
                            errorMessage = "Please scan Serial Number before registering device"
                            successMessage = null
                        }
                        else -> {
                            isLoading = true
                            errorMessage = null
                            successMessage = null
                            
                            scope.launch {
                                try {
                                    Log.d("DeviceInfoScreen", "=== STARTING DEVICE REGISTRATION ===")
                                    Log.d("DeviceInfoScreen", "Loan ID: $editableLoanId")
                                    Log.d("DeviceInfoScreen", "Device ID (IMEI): ${imeiList.firstOrNull()}")
                                    Log.d("DeviceInfoScreen", "IMEI List: $imeiList")
                                    Log.d("DeviceInfoScreen", "Serial Number: $serialNumber")
                                    Log.d("DeviceInfoScreen", "Device Info: $deviceInfo")
                                    
                                    // Call AuthRepository with all comparison data
                                    val result = repository.registerDevice(
                                        loanId = editableLoanId,
                                        deviceId = imeiList.firstOrNull() ?: "",
                                        imeiList = imeiList.filter { it.isNotBlank() },
                                        serialNumber = serialNumber,
                                        deviceFingerprint = deviceFingerprint,
                                        // Extended device information
                                        androidId = deviceInfo?.androidId,
                                        manufacturer = deviceInfo?.manufacturer,
                                        model = deviceInfo?.model,
                                        osVersion = deviceInfo?.osVersion,
                                        sdkVersion = deviceInfo?.sdkVersion,
                                        buildNumber = deviceInfo?.buildNumber,
                                        totalStorage = deviceInfo?.totalStorage,
                                        installedRam = deviceInfo?.totalRAM,
                                        totalStorageBytes = deviceInfo?.totalStorageBytes,
                                        totalRamBytes = deviceInfo?.totalRAMBytes,
                                        simSerialNumber = deviceInfo?.simNetworkInfo?.simSerialNumber,
                                        batteryCapacity = deviceInfo?.batteryInfo?.capacity,
                                        batteryTechnology = deviceInfo?.batteryInfo?.technology,
                                        // Comparison data
                                        bootloader = android.os.Build.BOOTLOADER,
                                        hardware = android.os.Build.HARDWARE,
                                        product = android.os.Build.PRODUCT,
                                        device = android.os.Build.DEVICE,
                                        brand = android.os.Build.BRAND,
                                        securityPatchLevel = android.os.Build.VERSION.SECURITY_PATCH,
                                        systemUptime = android.os.SystemClock.uptimeMillis(),
                                        batteryLevel = 0,  // Battery level not available in deviceInfo
                                        installedAppsHash = getInstalledAppsHash(context),
                                        systemPropertiesHash = getSystemPropertiesHash(),
                                        // Tamper status
                                        isDeviceRooted = false,
                                        isUSBDebuggingEnabled = false,
                                        isDeveloperModeEnabled = false,
                                        isBootloaderUnlocked = false,
                                        isCustomROM = false
                                    )
                                    
                                    when (result) {
                                        is ApiResult.Success -> {
                                            Log.d("DeviceInfoScreen", "✓ Device registered successfully!")
                                            successMessage = "Device registered successfully!"
                                            errorMessage = null
                                            
                                            // Navigate to success screen after a short delay
                                            scope.launch {
                                                kotlinx.coroutines.delay(1000)
                                                onRegistrationSuccess(
                                                    imeiList.firstOrNull() ?: "",
                                                    editableLoanId
                                                )
                                            }
                                        }
                                        is ApiResult.Error -> {
                                            Log.e("DeviceInfoScreen", "✗ Registration failed: ${result.message}")
                                            errorMessage = result.message
                                            successMessage = null
                                        }
                                        else -> {
                                            Log.e("DeviceInfoScreen", "✗ Unknown error occurred")
                                            errorMessage = "Unknown error occurred"
                                            successMessage = null
                                        }
                                    }
                                    isLoading = false
                                } catch (e: Exception) {
                                    Log.e("DeviceInfoScreen", "✗ Exception during registration: ${e.message}", e)
                                    errorMessage = "Error: ${e.message}"
                                    successMessage = null
                                    isLoading = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                    disabledContainerColor = colors.primary.copy(alpha = 0.5f)
                ),
                enabled = !isLoading && deviceInfo != null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register Device", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EditableFieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    colorScheme: ColorScheme
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = Color(0xFFDDDDDD),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color(0xFFFAFAFA),
                cursorColor = colorScheme.primary
            )
        )
    }
}

@Composable
private fun ReadOnlyFieldCard(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFFAFAFA),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDDDDD))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        color = Color.White,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    isLast: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                fontFamily = if (isMonospace) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        if (!isLast) {
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
        }
    }
}

/**
 * Get hash of installed apps for registration
 */
private fun getInstalledAppsHash(context: android.content.Context): String {
    return try {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        val appList = packages.map { it.packageName }.sorted().joinToString(",")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(appList.toByteArray())
        hash.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }
}

/**
 * Get hash of system properties for registration
 */
private fun getSystemPropertiesHash(): String {
    return try {
        val properties = StringBuilder()
        properties.append(android.os.Build.FINGERPRINT)
        properties.append(android.os.Build.DEVICE)
        properties.append(android.os.Build.PRODUCT)
        properties.append(android.os.Build.HARDWARE)
        properties.append(android.os.Build.BOOTLOADER)
        
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(properties.toString().toByteArray())
        hash.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }
}
