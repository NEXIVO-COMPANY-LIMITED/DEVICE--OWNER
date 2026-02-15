package com.example.deviceowner.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.data.models.DeviceRegistrationRequest
import com.example.deviceowner.data.repository.DeviceRegistrationRepository
import com.example.deviceowner.services.DeviceDataCollector
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceDataCollectionActivity : ComponentActivity() {

    private lateinit var registrationRepository: DeviceRegistrationRepository
    private lateinit var deviceDataCollector: DeviceDataCollector
    private var loanNumber: String = ""
    private var deviceData: DeviceRegistrationRequest? = null

    companion object {
        private const val TAG = "DeviceDataCollection"
        const val EXTRA_LOAN_NUMBER = "loan_number"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registrationRepository = DeviceRegistrationRepository(this)
        deviceDataCollector = DeviceDataCollector(this)
        loanNumber = intent.getStringExtra(EXTRA_LOAN_NUMBER) ?: ""

        if (loanNumber.isEmpty()) {
            Log.e(TAG, "Loan number not provided")
            finish()
            return
        }

        setContent {
            DeviceOwnerTheme {
                DeviceDataCollectionScreen(
                    loanNumber = loanNumber,
                    onSubmit = { submitDeviceData() }
                )
            }
        }

        collectAndEmit()
    }

    private fun collectAndEmit() {
        lifecycleScope.launch {
            try {
                // Load data silently in background without showing loading UI
                DeviceDataCollectionState.isLoading.value = false
                DeviceDataCollectionState.errorMessage.value = null

                deviceData = withContext(Dispatchers.IO) {
                    deviceDataCollector.collectDeviceData(loanNumber)
                }

                DeviceDataCollectionState.displayData.value = deviceData
                DeviceDataCollectionState.progressPercent.value = 100
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting device data: ${e.message}", e)
                DeviceDataCollectionState.errorMessage.value = e.message ?: "Unknown error"
                DeviceDataCollectionState.isLoading.value = false
            }
        }
    }

    private fun submitDeviceData() {
        if (deviceData == null) {
            Toast.makeText(this, "Device data not collected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                DeviceDataCollectionState.isSubmitting.value = true
                DeviceDataCollectionState.submissionStatus.value = "SUBMITTING"
                DeviceDataCollectionState.errorMessage.value = null

                registrationRepository.saveRegistrationData(deviceData!!, "PENDING")

                val response = withContext(Dispatchers.IO) {
                    registrationRepository.registerDevice(deviceData!!)
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    val serverDeviceId = body?.extractDeviceId() ?: body?.deviceId ?: android.provider.Settings.Secure.getString(
                        contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown"

                    getSharedPreferences("device_registration", MODE_PRIVATE).edit()
                        .putString("device_id", serverDeviceId).commit()

                    // Update DB to SUCCESS so resume flow goes to success screen on next launch
                    registrationRepository.updateRegistrationSuccessByLoan(
                        loanNumber = deviceData!!.loanNumber ?: "",
                        serverDeviceId = serverDeviceId,
                        status = "SUCCESS"
                    )

                    val successMessage = body?.message ?: "Device registered successfully!"
                    DeviceDataCollectionState.successMessage.value = successMessage
                    DeviceDataCollectionState.submissionStatus.value = "SUCCESSFUL"
                    Toast.makeText(this@DeviceDataCollectionActivity, successMessage, Toast.LENGTH_LONG).show()

                    delay(1500)
                    navigateToSuccess()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    val errorMessage = "Registration failed: HTTP ${response.code()}"
                    DeviceDataCollectionState.errorMessage.value = errorMessage
                    DeviceDataCollectionState.submissionStatus.value = "ERROR"
                    Toast.makeText(this@DeviceDataCollectionActivity, "Error: ${response.code()}", Toast.LENGTH_LONG).show()

                    // Log error to Django logs API
                    logRegistrationError(errorMessage, errorBody)
                }

                DeviceDataCollectionState.isSubmitting.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting device data: ${e.message}", e)
                val errorMessage = e.message ?: "Unknown error"
                DeviceDataCollectionState.errorMessage.value = errorMessage
                DeviceDataCollectionState.submissionStatus.value = "ERROR"
                DeviceDataCollectionState.isSubmitting.value = false
                Toast.makeText(this@DeviceDataCollectionActivity, "Error: $errorMessage", Toast.LENGTH_LONG).show()

                // Log error to Django logs API
                logRegistrationError("Registration Exception", errorMessage)
            }
        }
    }

    private fun logRegistrationError(errorTitle: String, errorDetails: String) {
        lifecycleScope.launch {
            try {
                val deviceId = getSharedPreferences("device_registration", MODE_PRIVATE)
                    .getString("device_id", null) ?: android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"

                val logRequest = com.example.deviceowner.data.models.DeviceLogRequest(
                    deviceId = deviceId,
                    logType = "registration",
                    message = "$errorTitle: $errorDetails",
                    logLevel = "Error"
                )

                withContext(Dispatchers.IO) {
                    registrationRepository.apiClient.postDeviceLog(logRequest)
                }

                Log.d(TAG, "✅ Registration error logged to Django API")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log registration error: ${e.message}", e)
            }
        }
    }

    private fun navigateToSuccess() {
        startActivity(Intent(this, RegistrationSuccessActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}

private object DeviceDataCollectionState {
    val isLoading = mutableStateOf(false)
    val isSubmitting = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val successMessage = mutableStateOf<String?>(null)
    val displayData = mutableStateOf<DeviceRegistrationRequest?>(null)
    val progressPercent = mutableStateOf(0)
    val currentProgressStep = mutableStateOf("")
    val submissionStatus = mutableStateOf("PENDING") // PENDING, SUBMITTING, SUCCESSFUL, ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDataCollectionScreen(
    loanNumber: String,
    onSubmit: () -> Unit
) {
    val isLoading by DeviceDataCollectionState.isLoading
    val isSubmitting by DeviceDataCollectionState.isSubmitting
    val errorMessage by DeviceDataCollectionState.errorMessage
    val data by DeviceDataCollectionState.displayData
    val submissionStatus by DeviceDataCollectionState.submissionStatus
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1976D2),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Device",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Device Info",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Content - Show data immediately when available
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Error Alert
                errorMessage?.let { msg ->
                    AlertCard(
                        icon = Icons.Default.ErrorOutline,
                        iconTint = Color(0xFFC62828),
                        backgroundColor = Color(0xFFFFEBEE),
                        borderColor = Color(0xFFF44336),
                        message = msg
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Show data if available, otherwise show nothing (silent loading)
                if (data != null) {
                    DeviceDataContent(data = data!!, loanNumber = loanNumber, submissionStatus = submissionStatus)
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
            shadowElevation = 8.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = data != null && !isLoading && !isSubmitting,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2),
                        disabledContainerColor = Color(0xFFBBDEFB)
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registering...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Register", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = "By submitting, you agree to our policies.",
                    fontSize = 10.sp,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    icon: ImageVector,
    iconTint: Color,
    backgroundColor: Color,
    borderColor: Color,
    title: String? = null,
    message: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                title?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iconTint
                    )
                }
                Text(
                    text = message,
                    fontSize = 11.sp,
                    color = iconTint,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 3.dp,
            color = Color(0xFF1976D2)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Collecting Device Info",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF333333)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Please wait...",
            fontSize = 12.sp,
            color = Color(0xFF999999),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DeviceDataContent(data: DeviceRegistrationRequest, loanNumber: String, submissionStatus: String) {
    val di = data.deviceInfo ?: emptyMap()
    val ai = data.androidInfo ?: emptyMap()
    val ii = data.imeiInfo ?: emptyMap()
    val si = data.storageInfo ?: emptyMap()

    val manufacturer = (di["manufacturer"] as? String) ?: "N/A"
    val model = (di["model"] as? String) ?: "N/A"
    val serialNumber = (di["serial_number"] as? String) ?: (di["serial"] as? String) ?: "N/A"
    val imeis = (ii["device_imeis"] as? List<*>)?.joinToString(", ") { it?.toString() ?: "" } ?: "N/A"
    val osVersion = (ai["version_release"] as? String) ?: "N/A"
    val sdkVersion = (ai["version_sdk_int"] as? Number)?.toString() ?: (ai["version_sdk_int"] as? String) ?: "N/A"
    val totalStorage = (si["total_storage"] as? String) ?: "N/A"
    val installedRam = (si["installed_ram"] as? String) ?: "N/A"

    Column(modifier = Modifier.fillMaxWidth()) {
        // Context Card
        DataCard(
            title = "CONTEXT",
            icon = null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Loan Application #$loanNumber",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937),
                    modifier = Modifier.weight(1f)
                )
                
                // Dynamic Status Badge
                val (statusColor, statusBgColor, statusText) = when (submissionStatus) {
                    "PENDING" -> Triple(Color(0xFFF57F17), Color(0xFFFFF9C4), "PENDING")
                    "SUBMITTING" -> Triple(Color(0xFF1976D2), Color(0xFFE3F2FD), "SUBMITTING")
                    "SUCCESSFUL" -> Triple(Color(0xFF2E7D32), Color(0xFFC8E6C9), "SUCCESSFUL")
                    "ERROR" -> Triple(Color(0xFFC62828), Color(0xFFFFCDD2), "ERROR")
                    else -> Triple(Color(0xFFF57F17), Color(0xFFFFF9C4), "PENDING")
                }
                
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBgColor
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hardware Details Table
        DataCard(
            title = "Hardware Details",
            icon = Icons.Default.PhoneAndroid
        ) {
            DataTable(
                rows = listOf(
                    "Manufacturer" to manufacturer,
                    "Model" to model,
                    "Serial Number" to serialNumber,
                    "IMEI" to imeis
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Health Table
        DataCard(
            title = "System Health",
            icon = Icons.Default.Settings
        ) {
            DataTable(
                rows = listOf(
                    "OS Version" to osVersion,
                    "SDK Level" to "API $sdkVersion",
                    "Total Storage" to totalStorage,
                    "Installed RAM" to installedRam
                )
            )
        }
    }
}

@Composable
private fun DataCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (icon != null) Color(0xFF1F2937) else Color(0xFF999999),
                letterSpacing = if (icon != null) 0.sp else 0.3.sp
            )
        }

        // Content
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            content()
        }
    }
}

@Composable
private fun DataTable(rows: List<Pair<String, String>>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF999999),
                    modifier = Modifier.weight(0.4f)
                )
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (index < rows.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE5E7EB)
                )
            }
        }
    }
}
