package com.microspace.payo.ui.activities.data

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.microspace.payo.data.models.registration.DeviceRegistrationRequest
import com.microspace.payo.data.repository.DeviceRegistrationRepository
import com.microspace.payo.services.data.DeviceDataCollector
import com.microspace.payo.services.reporting.ServerBugAndLogReporter
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import com.microspace.payo.ui.activities.registration.RegistrationSuccessActivity
import com.microspace.payo.utils.error.RegistrationErrorHandler
import com.microspace.payo.registration.DeviceRegistrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  Color palette
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
private object AppColors {
    val BluePrimary    = Color(0xFF1565C0)
    val BlueLight      = Color(0xFF1976D2)
    val BluePale       = Color(0xFFE3F2FD)
    val GreenPrimary   = Color(0xFF2E7D32)
    val GreenPale      = Color(0xFFC8E6C9)
    val AmberPrimary   = Color(0xFFF57F17)
    val AmberPale      = Color(0xFFFFF9C4)
    val RedPrimary     = Color(0xFFC62828)
    val RedPale        = Color(0xFFFFCDD2)
    val Gray100        = Color(0xFFF4F6F8)
    val Gray200        = Color(0xFFE8EAED)
    val Gray500        = Color(0xFF9AA0A6)
    val Gray600        = Color(0xFF80868B)
    val Gray700        = Color(0xFF5F6368)
    val Gray900        = Color(0xFF202124)
    val White          = Color(0xFFFFFFFF)
}

private object DeviceDataCollectionState {
    val isLoading        = mutableStateOf(false)
    val isSubmitting     = mutableStateOf(false)
    val errorMessage     = mutableStateOf<String?>(null)
    val successMessage   = mutableStateOf<String?>(null)
    val displayData      = mutableStateOf<DeviceRegistrationRequest?>(null)
    val progressPercent  = mutableStateOf(0)
    val submissionStatus = mutableStateOf("PENDING")
    val loanNumber       = mutableStateOf("")
}

class DeviceDataCollectionActivity : ComponentActivity() {
    private lateinit var registrationRepository: DeviceRegistrationRepository
    private lateinit var deviceDataCollector: DeviceDataCollector
    private var deviceData: DeviceRegistrationRequest? = null

    companion object {
        private const val TAG = "DeviceDataCollection"
        const val EXTRA_LOAN_NUMBER = "loan_number"
    }

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.fontScale = 1.0f
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registrationRepository = DeviceRegistrationRepository(this)
        deviceDataCollector    = DeviceDataCollector(this)
        
        val initialLoan = intent.getStringExtra(EXTRA_LOAN_NUMBER) ?: ""
        DeviceDataCollectionState.loanNumber.value = initialLoan

        if (initialLoan.isEmpty()) {
            finish()
            return
        }

        setContent {
            DeviceOwnerTheme {
                DeviceDataCollectionScreen(
                    loanNumber         = DeviceDataCollectionState.loanNumber.value,
                    onLoanNumberChange = { retryWithNewLoanNumber(it) },
                    onSubmit           = { submitDeviceData() },
                    isLoading          = DeviceDataCollectionState.isLoading.value,
                    isSubmitting       = DeviceDataCollectionState.isSubmitting.value,
                    errorMessage       = DeviceDataCollectionState.errorMessage.value,
                    data               = DeviceDataCollectionState.displayData.value,
                    submissionStatus   = DeviceDataCollectionState.submissionStatus.value,
                    progressPercent    = DeviceDataCollectionState.progressPercent.value,
                )
            }
        }
        collectAndEmit()
    }

    private fun collectAndEmit() {
        lifecycleScope.launch {
            try {
                DeviceDataCollectionState.isLoading.value       = true
                DeviceDataCollectionState.errorMessage.value    = null
                DeviceDataCollectionState.progressPercent.value = 30
                
                val currentLoan = DeviceDataCollectionState.loanNumber.value
                deviceData = withContext(Dispatchers.IO) {
                    deviceDataCollector.collectDeviceData(currentLoan)
                }
                DeviceDataCollectionState.displayData.value     = deviceData
                DeviceDataCollectionState.progressPercent.value = 100
                DeviceDataCollectionState.isLoading.value       = false
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting device data: ${e.message}", e)
                setErrorMessage("Error", "Failed to collect device information. Please try again.")
                DeviceDataCollectionState.isLoading.value       = false
                DeviceDataCollectionState.progressPercent.value = 0
            }
        }
    }

    private fun submitDeviceData() {
        if (deviceData == null) {
            Toast.makeText(this, "Please wait for information to be collected first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                DeviceDataCollectionState.isSubmitting.value     = true
                DeviceDataCollectionState.submissionStatus.value = "SUBMITTING"
                DeviceDataCollectionState.errorMessage.value     = null

                registrationRepository.saveRegistrationData(deviceData!!, "PENDING")

                val response = withContext(Dispatchers.IO) {
                    registrationRepository.registerDevice(deviceData!!)
                }

                if (response.isSuccessful) {
                    val body           = response.body()
                    val serverDeviceId = body?.extractDeviceId() ?: body?.deviceId

                    if (serverDeviceId.isNullOrBlank()) {
                        setErrorMessage("Registration Failed", "System did not return an identification number. Contact support.")
                        return@launch
                    }

                    // CRITICAL: Save registration data first
                    com.microspace.payo.data.DeviceIdProvider.saveDeviceId(this@DeviceDataCollectionActivity, serverDeviceId)
                    Log.i(TAG, "âœ… Saved correct device ID: $serverDeviceId")
                    
                    registrationRepository.updateRegistrationSuccessByLoan(
                        loanNumber     = DeviceDataCollectionState.loanNumber.value,
                        serverDeviceId = serverDeviceId,
                        status         = "SUCCESS"
                    )

                    // PERFORM WIFI CLEANUP & STATUS REPORTING
                    Log.d(TAG, "ðŸ§¹ Initiating WiFi cleanup and installation status reporting...")
                    val registrationManager = DeviceRegistrationManager(this@DeviceDataCollectionActivity)
                    registrationManager.markRegistrationComplete()
                    registrationManager.sendInstallationStatusWithLifecycle(
                        lifecycleScope = lifecycleScope,
                        onSuccess = { Log.d(TAG, "WiFi Cleanup and Status Sent Successfully") },
                        onFailure = { Log.e(TAG, "WiFi Cleanup or Status Send failed: $it") }
                    )

                    // Delay clearing temp files to ensure cleanup logic has access to SSID
                    delay(500)
                    Log.d(TAG, "ðŸ§¹ Clearing temporary registration data...")
                    try {
                        this@DeviceDataCollectionActivity.getSharedPreferences("device_registration", Context.MODE_PRIVATE).edit().clear().apply()
                        Log.d(TAG, "âœ… Temp data cleared successfully")
                    } catch (e: Exception) {
                        Log.w(TAG, "âš ï¸ Warning clearing temp data: ${e.message}")
                    }

                    DeviceDataCollectionState.submissionStatus.value = "SUCCESSFUL"
                    delay(700)
                    navigateToSuccess(serverDeviceId)
                } else {
                    handleServerError(response)
                }
            } catch (e: java.net.UnknownHostException) {
                setErrorMessage("Network", "No internet connection. Check data or WiFi and try again.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during submit: ${e.message}", e)
                setErrorMessage("Error", "Failed to complete registration. Please try again later.")
            } finally {
                DeviceDataCollectionState.isSubmitting.value = false
            }
        }
    }

    /**
     * Enhanced server error processing for standardized Django responses.
     */
    private fun handleServerError(response: retrofit2.Response<*>) {
        val currentLoan = DeviceDataCollectionState.loanNumber.value
        val errorInfo = RegistrationErrorHandler.parseError(
            response = response,
            context = this,
            loanNumber = currentLoan
        )
        RegistrationErrorHandler.logError(errorInfo, "Registration attempt for loan: $currentLoan")
        val customerMessage = RegistrationErrorHandler.buildCustomerErrorMessage(errorInfo)
        setErrorMessage("Registration Failed", customerMessage)
    }

    private fun setErrorMessage(title: String, msg: String) {
        DeviceDataCollectionState.errorMessage.value = msg
        DeviceDataCollectionState.submissionStatus.value = "ERROR"
    }

    private fun navigateToSuccess(deviceId: String) {
        try {
            val intent = Intent(this, RegistrationSuccessActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(RegistrationSuccessActivity.EXTRA_DEVICE_ID, deviceId)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error navigating to success screen: ${e.message}", e)
            Toast.makeText(this, "Failed to open success page", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retryWithNewLoanNumber(newLoanNumber: String) {
        DeviceDataCollectionState.loanNumber.value = newLoanNumber
        DeviceDataCollectionState.submissionStatus.value = "PENDING"
        DeviceDataCollectionState.isLoading.value        = true
        DeviceDataCollectionState.progressPercent.value  = 0
        DeviceDataCollectionState.displayData.value      = null
        
        lifecycleScope.launch {
            runCatching {
                com.microspace.payo.utils.cache.RegistrationCacheManager.clearAllRegistrationCaches(this@DeviceDataCollectionActivity)
            }
            collectAndEmit()
        }
    }
}

@Composable
fun DeviceDataCollectionScreen(
    loanNumber: String,
    onLoanNumberChange: (String) -> Unit = {},
    onSubmit: () -> Unit,
    isLoading: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    data: DeviceRegistrationRequest?,
    submissionStatus: String,
    progressPercent: Int,
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Gray100)) {
        Column(modifier = Modifier.fillMaxSize()) {
            DeviceAppBar()
            AnimatedProgressBar(percent = progressPercent)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    errorMessage?.let { ErrorAlert(message = it) }
                }

                Spacer(modifier = Modifier.height(4.dp))

                ContextCard(
                    loanNumber = loanNumber,
                    onLoanNumberChange = onLoanNumberChange,
                    submissionStatus = submissionStatus
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isLoading) {
                    SectionHeader("Device Identifiers", Icons.Outlined.Badge)
                    SkeletonCard(rows = 2)
                    Spacer(modifier = Modifier.height(20.dp))
                    SectionHeader("Hardware Details", Icons.Outlined.PhoneAndroid)
                    SkeletonCard(rows = 2)
                    Spacer(modifier = Modifier.height(20.dp))
                    SectionHeader("System Information", Icons.Outlined.Info)
                    SkeletonCard(rows = 4)
                } else if (data != null) {
                    DeviceDataContent(data = data)
                }

                Spacer(modifier = Modifier.height(110.dp))
            }
        }

        BottomActionBar(
            modifier     = Modifier.align(Alignment.BottomCenter),
            isSubmitting = isSubmitting,
            isEnabled    = data != null && !isLoading && !isSubmitting,
            onSubmit     = onSubmit,
        )
    }
}

@Composable
private fun DeviceAppBar() {
    Box(
        modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(AppColors.BluePrimary, AppColors.BlueLight))).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(AppColors.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PhoneAndroid, null, tint = AppColors.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Device Registration", style = MaterialTheme.typography.titleMedium.copy(color = AppColors.White))
                Text("Loan Application Portal", style = MaterialTheme.typography.bodySmall.copy(color = AppColors.White.copy(alpha = 0.70f)))
            }
        }
    }
}

@Composable
private fun AnimatedProgressBar(percent: Int) {
    val animated by animateFloatAsState(targetValue = percent / 100f, animationSpec = tween(600), label = "progress")
    Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(AppColors.BluePale)) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animated).background(Brush.horizontalGradient(listOf(AppColors.BlueLight, Color(0xFF42A5F5)))))
    }
}

@Composable
private fun ErrorAlert(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), 
        colors = CardDefaults.cardColors(containerColor = AppColors.RedPale), 
        border = BorderStroke(1.5.dp, Color(0xFFEF9A9A))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = AppColors.RedPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message, 
                style = MaterialTheme.typography.bodySmall.copy(color = AppColors.RedPrimary, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextCard(loanNumber: String, onLoanNumberChange: (String) -> Unit, submissionStatus: String) {
    var showDialog by remember { mutableStateOf(false) }
    var editedLoanNumber by remember { mutableStateOf(loanNumber) }
    val (statusColor, bgColor, statusText) = when (submissionStatus) {
        "SUBMITTING" -> Triple(AppColors.BlueLight,    AppColors.BluePale,  "Submitting...")
        "SUCCESSFUL" -> Triple(AppColors.GreenPrimary, AppColors.GreenPale, "Registered")
        "ERROR"      -> Triple(AppColors.RedPrimary,   AppColors.RedPale,   "Error")
        else         -> Triple(AppColors.AmberPrimary, AppColors.AmberPale, "Pending")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Edit Loan Number") },
            text = {
                OutlinedTextField(
                    value = editedLoanNumber, 
                    onValueChange = { editedLoanNumber = it }, 
                    label = { Text("Enter Loan Number") }, 
                    singleLine = true, 
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { if (editedLoanNumber.isNotBlank()) { onLoanNumberChange(editedLoanNumber); showDialog = false } }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AppColors.BluePale), border = BorderStroke(1.dp, Color(0xFFBFDBFE)), onClick = { showDialog = true }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("LOAN NUMBER", style = MaterialTheme.typography.labelSmall.copy(color = AppColors.BlueLight))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#$loanNumber", style = MaterialTheme.typography.headlineSmall.copy(color = AppColors.BluePrimary))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Edit, null, tint = AppColors.BlueLight.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = bgColor) {
                Text(statusText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall.copy(color = statusColor, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)) {
        Icon(icon, null, tint = AppColors.BlueLight, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall.copy(color = AppColors.Gray700))
    }
}

@Composable
private fun SkeletonCard(rows: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AppColors.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            repeat(rows) { idx ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppColors.Gray200)
                Box(modifier = Modifier.fillMaxWidth().height(20.dp).background(AppColors.Gray200))
            }
        }
    }
}

@Composable
private fun DeviceDataContent(data: DeviceRegistrationRequest) {
    val di = data.deviceInfo ?: emptyMap()
    val ai = data.androidInfo ?: emptyMap()
    val ii = data.imeiInfo ?: emptyMap()
    val brand = di.str("brand").ifBlank { di.str("manufacturer") }
    
    val imeiList = (ii["device_imeis"] as? List<*>)?.mapNotNull { it?.toString() }
    val imeisDisplay = if (imeiList.isNullOrEmpty()) "N/A" else imeiList.joinToString(", ")
    
    SectionHeader("Device Identifiers", Icons.Outlined.Badge)
    InfoCard(listOf(
        InfoRow(Icons.Outlined.PhoneAndroid, "IMEI", imeisDisplay, true), 
        InfoRow(Icons.Outlined.Numbers, "Serial", di.str("serial"), true)
    ))
    
    Spacer(modifier = Modifier.height(22.dp))
    SectionHeader("Hardware Details", Icons.Outlined.PhoneAndroid)
    InfoCard(listOf(InfoRow(Icons.Outlined.LocalOffer, "Brand", brand), InfoRow(Icons.Outlined.Devices, "Model", di.str("model"))))
    
    Spacer(modifier = Modifier.height(22.dp))
    SectionHeader("System Information", Icons.Outlined.Info)
    InfoCard(listOf(
        InfoRow(Icons.Outlined.Android, "Android", ai.str("version_release")),
        InfoRow(Icons.Outlined.Fingerprint, "Fingerprint", ai.str("device_fingerprint"), true)
    ))
}

private fun Map<String, Any?>.str(key: String): String = this[key]?.toString() ?: "N/A"
private data class InfoRow(val icon: ImageVector, val label: String, val value: String, val mono: Boolean = false)

@Composable
private fun InfoCard(rows: List<InfoRow>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.White), border = BorderStroke(1.dp, AppColors.Gray200)) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = AppColors.Gray200)
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(row.icon, null, tint = AppColors.BlueLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(row.label, style = MaterialTheme.typography.labelSmall.copy(color = AppColors.Gray600))
                    }
                    Text(
                        text = row.value, 
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AppColors.Gray900,
                            letterSpacing = if (row.mono) (-0.3).sp else 0.sp
                        ),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(modifier: Modifier, isSubmitting: Boolean, isEnabled: Boolean, onSubmit: () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = AppColors.White, shadowElevation = 12.dp) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onSubmit, 
                enabled = isEnabled, 
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AppColors.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Submitting...")
                } else {
                    Text("Register Device Now")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("By continuing, you agree to our terms and conditions.", style = MaterialTheme.typography.labelSmall.copy(color = AppColors.Gray500))
        }
    }
}




