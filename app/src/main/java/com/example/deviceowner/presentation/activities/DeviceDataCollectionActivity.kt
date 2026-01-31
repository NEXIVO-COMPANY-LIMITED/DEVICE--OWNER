package com.example.deviceowner.presentation.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.example.deviceowner.data.models.DeviceRegistrationRequest
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.data.remote.api.ServerReturnedHtmlException
import com.example.deviceowner.data.repository.DeviceRegistrationRepository
import com.example.deviceowner.presentation.activities.MainActivity
import com.example.deviceowner.security.firmware.FirmwareSecurity
import com.example.deviceowner.services.DeviceDataCollector
import com.example.deviceowner.services.FirmwareSecurityMonitorService
import com.example.deviceowner.services.HeartbeatService
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.RegistrationErrorLogger
import com.example.deviceowner.utils.SharedPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceDataCollectionActivity : ComponentActivity() {
    
    private lateinit var deviceDataCollector: DeviceDataCollector
    private lateinit var apiClient: ApiClient
    private lateinit var prefsManager: SharedPreferencesManager
    private lateinit var registrationRepository: DeviceRegistrationRepository
    private lateinit var registrationBackup: com.example.deviceowner.data.local.RegistrationDataBackup
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val BACKGROUND_LOCATION_REQUEST_CODE = 1002
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        private val BACKGROUND_LOCATION_PERMISSION = arrayOf(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        deviceDataCollector = DeviceDataCollector(this)
        apiClient = ApiClient()
        prefsManager = SharedPreferencesManager(this)
        registrationRepository = DeviceRegistrationRepository(this)
        registrationBackup = com.example.deviceowner.data.local.RegistrationDataBackup(this)
        
        Log.d("DeviceRegistration", "Starting device registration - Device Owner status not required")
        
        // CRITICAL: Check if device is already registered
        lifecycleScope.launch {
            if (registrationRepository.isDeviceRegistered()) {
                Log.w("DeviceRegistration", "Device already registered - blocking access to registration page")
                Toast.makeText(this@DeviceDataCollectionActivity, "Device is already registered", Toast.LENGTH_SHORT).show()
                
                // Redirect to main activity
                val intent = Intent(this@DeviceDataCollectionActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return@launch
            }
            
            // Try to restore registration data from backup if app was reinstalled
            Log.d("DeviceRegistration", "Checking for registration data backup...")
            if (registrationRepository.hasRegistrationBackup()) {
                Log.d("DeviceRegistration", "Found registration backup - attempting restore...")
                val restoreSuccess = registrationRepository.restoreRegistrationDataFromBackup()
                
                if (restoreSuccess) {
                    Log.d("DeviceRegistration", "Registration data restored from backup successfully")
                    Toast.makeText(this@DeviceDataCollectionActivity, "Registration data restored from backup", Toast.LENGTH_LONG).show()
                    
                    // Redirect to main activity since device is now registered
                    val intent = Intent(this@DeviceDataCollectionActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    return@launch
                } else {
                    Log.w("DeviceRegistration", "Failed to restore registration data from backup")
                }
            }
            
            // Get stored loan number (from previous input or intent)
            val storedLoanNumber = registrationRepository.getStoredLoanNumber()
            val intentLoanNumber = intent.getStringExtra("loan_number")
            val loanNumber = storedLoanNumber ?: intentLoanNumber ?: ""
            
            Log.d("DeviceRegistration", "Using loan number: $loanNumber (stored: $storedLoanNumber, intent: $intentLoanNumber)")
            
            if (loanNumber.isBlank()) {
                Toast.makeText(this@DeviceDataCollectionActivity, "No loan number available. Please restart registration.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            
            // Set up UI with the loan number
            setContent {
                DeviceOwnerTheme {
                    DeviceDataCollectionScreen(
                        loanNumber = loanNumber,
                        onPermissionsNeeded = { requestPermissions() },
                        onDataCollected = { data -> 
                            collectDeviceData(loanNumber) { collectedData ->
                                data.value = collectedData
                            }
                        },
                        onSubmitData = { data ->
                            submitDeviceRegistration(data)
                        }
                    )
                }
            }
        }
        
        // Check permissions on start
        if (!hasAllPermissions()) {
            requestPermissions()
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun hasLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }
    
    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older versions
        }
    }
    
    private fun requestPermissions() {
        Log.d("DeviceDataCollection", "Requesting permissions for better location accuracy")
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }
    
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d("DeviceDataCollection", "Requesting background location permission")
            ActivityCompat.requestPermissions(this, BACKGROUND_LOCATION_PERMISSION, BACKGROUND_LOCATION_REQUEST_CODE)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val deniedPermissions = mutableListOf<String>()
                val grantedLocationPermissions = mutableListOf<String>()
                
                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        deniedPermissions.add(permissions[i])
                    } else {
                        // Check if location permissions were granted
                        if (permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION || 
                            permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION) {
                            grantedLocationPermissions.add(permissions[i])
                        }
                    }
                }
                
                // Handle location permissions specifically
                if (grantedLocationPermissions.isNotEmpty()) {
                    Log.d("DeviceDataCollection", "Location permissions granted: $grantedLocationPermissions")
                    Toast.makeText(this, "Location permissions granted! Location data will be collected.", Toast.LENGTH_LONG).show()
                    
                    // Request background location if foreground location was granted and we're on Android 10+
                    if (hasLocationPermissions() && !hasBackgroundLocationPermission()) {
                        requestBackgroundLocationPermission()
                    }
                }
                
                if (deniedPermissions.isNotEmpty()) {
                    Log.w("DeviceDataCollection", "Some permissions denied: $deniedPermissions")
                    
                    val locationDenied = deniedPermissions.any { 
                        it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION 
                    }
                    
                    val message = when {
                        locationDenied -> {
                            "Location permissions were denied. Location data will not be collected, but you can still register the device."
                        }
                        deniedPermissions.all { it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION } -> {
                            "Location permissions denied. Device registration will continue without location data."
                        }
                        else -> {
                            "Some permissions were denied. Device data collection may be limited, but you can still register if IMEI/Serial is available."
                        }
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                } else {
                    Log.d("DeviceDataCollection", "All basic permissions granted")
                    Toast.makeText(this, "All permissions granted! Full device data will be collected.", Toast.LENGTH_SHORT).show()
                }
            }
            
            BACKGROUND_LOCATION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("DeviceDataCollection", "Background location permission granted")
                    Toast.makeText(this, "Background location granted for better accuracy!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("DeviceDataCollection", "Background location permission denied (non-critical)")
                    Toast.makeText(this, "Background location denied, but foreground location will still work.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun collectDeviceData(loanNumber: String, onResult: (DeviceRegistrationRequest?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Validate loan number before proceeding
                if (loanNumber.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DeviceDataCollectionActivity, "Invalid loan number", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return@launch
                }
                
                Log.d("DeviceRegistration", "Starting data collection for loan: $loanNumber")
                
                // Collect device data first (this will work even without Device Owner)
                val data = deviceDataCollector.collectDeviceData(loanNumber)
                
                Log.d("DeviceRegistration", "Data collection completed successfully")
                
                withContext(Dispatchers.Main) {
                    onResult(data)
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DeviceDataCollectionActivity, "Permission denied: ${e.message}", Toast.LENGTH_LONG).show()
                    onResult(null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DeviceDataCollectionActivity, "Failed to collect device data: ${e.message}", Toast.LENGTH_LONG).show()
                    onResult(null)
                }
            }
        }
    }
    
    // Helper functions to extract nested map values
    private fun getDeviceId(data: DeviceRegistrationRequest): String? = 
        (data.deviceInfo?.get("device_id") as? String)
    
    private fun getAndroidId(data: DeviceRegistrationRequest): String? = 
        (data.deviceInfo?.get("android_id") as? String)
    
    private fun getBootloader(data: DeviceRegistrationRequest): String? = 
        (data.deviceInfo?.get("bootloader") as? String)
    
    private fun getModel(data: DeviceRegistrationRequest): String? = 
        (data.deviceInfo?.get("model") as? String)
    
    private fun getManufacturer(data: DeviceRegistrationRequest): String? = 
        (data.deviceInfo?.get("manufacturer") as? String)
    
    private fun getFingerprint(data: DeviceRegistrationRequest): String? = 
        (data.deviceInfo?.get("fingerprint") as? String)
    
    private fun getBuildId(data: DeviceRegistrationRequest): String? = 
        (data.androidInfo?.get("build_id") as? String)
    
    private fun getBuildType(data: DeviceRegistrationRequest): String? = 
        (data.androidInfo?.get("build_type") as? String)

    private fun submitDeviceRegistration(data: DeviceRegistrationRequest) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // STEP 0: Pre-submission validation and logging
                Log.d("DeviceRegistration", "=== STARTING DEVICE REGISTRATION ===")
                Log.d("DeviceRegistration", "Device ID: ${getDeviceId(data) ?: "null (will be assigned by server)"}")
                Log.d("DeviceRegistration", "Loan Number: ${data.loanNumber}")
                Log.d("DeviceRegistration", "Manufacturer: ${getManufacturer(data)}")
                Log.d("DeviceRegistration", "Model: ${getModel(data)}")
                Log.d("DeviceRegistration", "Android ID Length: ${getAndroidId(data)?.length ?: 0}")
                Log.d("DeviceRegistration", "Bootloader Length: ${getBootloader(data)?.length ?: 0}")
                
                // Validate critical field lengths before submission
                val validationErrors = mutableListOf<String>()
                
                val androidId = getAndroidId(data)
                if (androidId != null && androidId.length > 100) {
                    validationErrors.add("Android ID too long: ${androidId.length} chars (max 100)")
                }
                val bootloader = getBootloader(data)
                if (bootloader != null && bootloader.length > 100) {
                    validationErrors.add("Bootloader too long: ${bootloader.length} chars (max 100)")
                }
                
                if (validationErrors.isNotEmpty()) {
                    Log.e("DeviceRegistration", "Pre-submission validation failed:")
                    validationErrors.forEach { Log.e("DeviceRegistration", "  - $it") }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DeviceDataCollectionActivity, 
                            "Data validation failed: ${validationErrors.first()}", 
                            Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                // STEP 1: Save registration data to local database first
                Log.d("DeviceRegistration", "Saving registration data to local database...")
                registrationRepository.saveRegistrationData(data, "PENDING")
                
                // STEP 1.5: Log device data for testing (before sending to server)
                try {
                    val deviceDataLogger = com.example.deviceowner.utils.DeviceDataLogger(this@DeviceDataCollectionActivity)
                    deviceDataLogger.logDeviceData("BEFORE_REGISTRATION")
                    Log.d("DeviceRegistration", "Device data logged for testing")
                } catch (e: Exception) {
                    Log.w("DeviceRegistration", "Failed to log device data: ${e.message}")
                }
                
                // STEP 2: Submit to server
                Log.d("DeviceRegistration", "Submitting registration to server...")
                val response = apiClient.registerDevice(data)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        
                        // CRITICAL: Extract server-generated device ID
                        val serverDeviceId = responseBody?.deviceId
                        
                        if (serverDeviceId == null) {
                            Log.e("DeviceRegistration", "Server did not return device ID in response")
                            Toast.makeText(this@DeviceDataCollectionActivity, "Registration failed: Server did not assign device ID", Toast.LENGTH_LONG).show()
                            return@withContext
                        }
                        
                        Log.d("DeviceRegistration", "Server assigned device ID: $serverDeviceId")
                        
                        // Update the data object with server-generated device ID
                        val updatedData = data.copy(deviceId = serverDeviceId)
                        // Log successful registration attempt
                        try {
                            val deviceDataLogger = com.example.deviceowner.utils.DeviceDataLogger(this@DeviceDataCollectionActivity)
                            val deviceDataMap = mapOf(
                                "loan_number" to data.loanNumber,
                                "device_id" to (getDeviceId(data) ?: "null"),
                                "model" to (getModel(data) ?: "null"),
                                "manufacturer" to (getManufacturer(data) ?: "null")
                            )
                            deviceDataLogger.logRegistrationAttempt(
                                loanNumber = data.loanNumber,
                                registrationData = deviceDataMap,
                                success = true,
                                serverResponse = response.body()?.toString()
                            )
                            Log.d("DeviceRegistration", "Successful registration logged")
                        } catch (e: Exception) {
                            Log.w("DeviceRegistration", "Failed to log successful registration: ${e.message}")
                        }
                        
                        // Firmware security: activate and start monitor (docs/android-firmware-security-complete, deviceowner-firmware-integration-complete)
                        try {
                            val firmwareActivated = FirmwareSecurity.activateSecurityMode()
                            Log.d("DeviceRegistration", "Firmware security activated: $firmwareActivated")
                            startService(Intent(this@DeviceDataCollectionActivity, FirmwareSecurityMonitorService::class.java))
                        } catch (e: Exception) {
                            Log.w("DeviceRegistration", "Firmware security setup failed (non-fatal): ${e.message}")
                        }
                        
                        // Apply network restrictions after successful registration
                        try {
                            val enhancedSecurity = com.example.deviceowner.security.enforcement.EnhancedSecurityManager(this@DeviceDataCollectionActivity)
                            val networkRestrictionsApplied = enhancedSecurity.applyNetworkRestrictionsAfterRegistration()
                            Log.d("DeviceRegistration", "Network restrictions applied: $networkRestrictionsApplied")
                            
                            // Also apply Device Owner network restrictions
                            val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(this@DeviceDataCollectionActivity)
                            deviceOwnerManager.applyPostRegistrationNetworkRestrictions()
                            
                            // Mark registration as completed to enable network restrictions
                            getSharedPreferences("device_registration", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("registration_completed", true)
                                .apply()
                                
                        } catch (e: Exception) {
                            Log.w("DeviceRegistration", "Network restrictions setup failed (non-fatal): ${e.message}")
                        }
                        // STEP 3: Update local database with success status and server device ID
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val responseString = if (responseBody != null) {
                                    try {
                                        Gson().toJson(responseBody)
                                    } catch (e: Exception) {
                                        Log.w("DeviceRegistration", "Failed to serialize response: ${e.message}")
                                        responseBody.toString()
                                    }
                                } else {
                                    "Registration successful - no response body"
                                }
                                
                                // Save registration with server-generated device ID
                                registrationRepository.saveRegistrationData(updatedData, "SUCCESS")
                                registrationRepository.updateRegistrationStatus(
                                    deviceId = serverDeviceId,
                                    status = "SUCCESS",
                                    serverResponse = responseString
                                )
                                
                                // CRITICAL: Backup registration data for app reinstallation
                                val backupSuccess = registrationBackup.backupRegistrationData()
                                if (backupSuccess) {
                                    Log.d("DeviceRegistration", "Registration data backed up successfully")
                                } else {
                                    Log.w("DeviceRegistration", "Failed to backup registration data")
                                }
                            } catch (e: Exception) {
                                Log.e("DeviceRegistration", "Error updating registration status: ${e.message}")
                            }
                        }
                        
                        // STEP 4: Update preferences with server device ID
                        prefsManager.setDeviceRegistered(true)
                        prefsManager.saveLoanNumber(updatedData.loanNumber)
                        
                        // STEP 5: Start heartbeat service with server-generated device ID
                        try {
                            HeartbeatService.startService(this@DeviceDataCollectionActivity, serverDeviceId)
                            Log.d("DeviceRegistration", "Heartbeat service started with server device ID: $serverDeviceId")
                        } catch (e: Exception) {
                            Log.w("DeviceRegistration", "Failed to start heartbeat service: ${e.message}")
                        }
                        
                        Toast.makeText(this@DeviceDataCollectionActivity, "Device registered successfully! Monitoring started.", Toast.LENGTH_LONG).show()
                        Log.d("DeviceRegistration", "Registration completed successfully")
                        
                        // STEP 6: Navigate to main activity (no more access to registration page)
                        val intent = Intent(this@DeviceDataCollectionActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // Log failed registration attempt
                        try {
                            val deviceDataLogger = com.example.deviceowner.utils.DeviceDataLogger(this@DeviceDataCollectionActivity)
                            val deviceDataMap = mapOf(
                                "loan_number" to data.loanNumber,
                                "device_id" to (getDeviceId(data) ?: "null"),
                                "model" to (getModel(data) ?: "null"),
                                "manufacturer" to (getManufacturer(data) ?: "null"),
                                "response_code" to response.code()
                            )
                            val errorBody = try {
                                response.errorBody()?.string() ?: "Registration failed - no error details"
                            } catch (e: Exception) {
                                "Registration failed - error reading response: ${e.message}"
                            }
                            deviceDataLogger.logRegistrationAttempt(
                                loanNumber = data.loanNumber,
                                registrationData = deviceDataMap,
                                success = false,
                                serverResponse = errorBody,
                                error = "HTTP ${response.code()}: Registration failed"
                            )
                            Log.d("DeviceRegistration", "Failed registration logged")
                        } catch (e: Exception) {
                            Log.w("DeviceRegistration", "Failed to log failed registration: ${e.message}")
                        }
                        
                        // STEP 6: Update local database and save error HTML to folder
                        val errorBody = try {
                            response.errorBody()?.string() ?: "Registration failed - no error details"
                        } catch (e: Exception) {
                            Log.w("DeviceRegistration", "Failed to read error body: ${e.message}")
                            "Registration failed - error reading response: ${e.message}"
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val deviceIdForError = getDeviceId(data) ?: "null_device_id"
                                registrationRepository.updateRegistrationStatus(
                                    deviceId = deviceIdForError,
                                    status = "FAILED",
                                    serverResponse = errorBody
                                )
                                // Save error to DeviceOwner/RegistrationErrors folder as HTML
                                RegistrationErrorLogger.logRegistrationError(
                                    context = this@DeviceDataCollectionActivity,
                                    errorType = "HTTP ${response.code()}",
                                    errorMessage = response.message(),
                                    deviceId = deviceIdForError,
                                    loanNumber = data.loanNumber,
                                    serverResponse = errorBody
                                )
                            } catch (e: Exception) {
                                Log.e("DeviceRegistration", "Error updating failed registration status: ${e.message}")
                            }
                        }
                        Toast.makeText(this@DeviceDataCollectionActivity, "Registration failed: ${response.message()}. Error saved to DeviceOwner/RegistrationErrors.", Toast.LENGTH_LONG).show()
                        Log.e("DeviceRegistration", "Registration failed: ${response.message()}")
                    }
                }
            } catch (e: ServerReturnedHtmlException) {
                // Server returned HTML (e.g. 404/500 page) instead of JSON — detected before Retrofit parse
                CoroutineScope(Dispatchers.IO).launch {
                    val deviceIdForError = getDeviceId(data) ?: "null_device_id"
                    registrationRepository.updateRegistrationStatus(
                        deviceId = deviceIdForError,
                        status = "ERROR",
                        serverResponse = "HTTP ${e.httpCode}: Server returned HTML. ${e.bodyPreview.take(500)}"
                    )
                    RegistrationErrorLogger.logRegistrationError(
                        context = this@DeviceDataCollectionActivity,
                        errorType = "ServerReturnedHtml",
                        errorMessage = e.message ?: "Server returned HTML instead of JSON (HTTP ${e.httpCode})",
                        throwable = e,
                        deviceId = deviceIdForError,
                        loanNumber = data.loanNumber,
                        serverResponse = e.bodyPreview
                    )
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@DeviceDataCollectionActivity,
                        "Server returned an HTML page instead of JSON. Check BASE_URL and API endpoint. Error saved to DeviceOwner/RegistrationErrors.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("DeviceRegistration", "Server returned HTML instead of JSON (HTTP ${e.httpCode}): ${e.bodyPreview.take(200)}", e)
                }
            } catch (e: ClassCastException) {
                // Log ClassCastException with device data
                try {
                    val deviceDataLogger = com.example.deviceowner.utils.DeviceDataLogger(this@DeviceDataCollectionActivity)
                    val deviceDataMap = mapOf(
                        "loan_number" to data.loanNumber,
                        "device_id" to (getDeviceId(data) ?: "null"),
                        "model" to (getModel(data) ?: "null"),
                        "manufacturer" to (getManufacturer(data) ?: "null"),
                        "error_type" to "ClassCastException",
                        "error_message" to e.message
                    )
                    deviceDataLogger.logRegistrationAttempt(
                        loanNumber = data.loanNumber,
                        registrationData = deviceDataMap,
                        success = false,
                        error = "ClassCastException: ${e.message}"
                    )
                    Log.d("DeviceRegistration", "ClassCastException logged with device data")
                } catch (logError: Exception) {
                    Log.w("DeviceRegistration", "Failed to log ClassCastException: ${logError.message}")
                }
                
                // Server returned 200 but JSON format doesn't match DeviceRegistrationResponse (e.g. different API version)
                CoroutineScope(Dispatchers.IO).launch {
                    val deviceIdForError = getDeviceId(data) ?: "null_device_id"
                    registrationRepository.updateRegistrationStatus(
                        deviceId = deviceIdForError,
                        status = "ERROR",
                        serverResponse = "ClassCastException: ${e.message}"
                    )
                    // Save error to DeviceOwner/RegistrationErrors folder as HTML (cause + how to fix)
                    RegistrationErrorLogger.logRegistrationError(
                        context = this@DeviceDataCollectionActivity,
                        errorType = "ClassCastException",
                        errorMessage = e.message ?: "Server response format mismatch",
                        throwable = e,
                        deviceId = deviceIdForError,
                        loanNumber = data.loanNumber
                    )
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@DeviceDataCollectionActivity,
                        "Server returned unexpected format. Error saved to DeviceOwner/RegistrationErrors. Open app to view.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("DeviceRegistration", "ClassCastException during registration (server response format mismatch): ${e.message}", e)
                }
            } catch (e: Exception) {
                // STEP 7: Enhanced error handling with detailed diagnosis
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val errorMessage = when (e) {
                            is java.net.UnknownHostException -> {
                                Log.e("DeviceRegistration", "DNS/Network error: ${e.message}")
                                "Network error: Cannot reach server. Check internet connection and DNS settings."
                            }
                            is java.net.ConnectException -> {
                                Log.e("DeviceRegistration", "Connection error: ${e.message}")
                                "Connection error: Server is not responding. Check server status and network."
                            }
                            is java.net.SocketTimeoutException -> {
                                Log.e("DeviceRegistration", "Timeout error: ${e.message}")
                                "Timeout error: Server took too long to respond. Try again or check network speed."
                            }
                            is retrofit2.HttpException -> {
                                Log.e("DeviceRegistration", "HTTP error ${e.code()}: ${e.message()}")
                                val errorBody = try { e.response()?.errorBody()?.string() } catch (ex: Exception) { null }
                                Log.e("DeviceRegistration", "HTTP error body: $errorBody")
                                "HTTP error ${e.code()}: ${e.message()}. Check server logs."
                            }
                            is com.google.gson.JsonSyntaxException -> {
                                Log.e("DeviceRegistration", "JSON parsing error: ${e.message}")
                                "Data format error: Server returned invalid response format."
                            }
                            is SecurityException -> {
                                Log.e("DeviceRegistration", "Permission error: ${e.message}")
                                "Permission error: ${e.message}"
                            }
                            is ClassCastException -> {
                                Log.e("DeviceRegistration", "Data type error: ${e.message}")
                                "Data type error: Server returned unexpected data format. Check API version."
                            }
                            is ServerReturnedHtmlException -> {
                                Log.e("DeviceRegistration", "Server returned HTML (HTTP ${e.httpCode}): ${e.bodyPreview.take(200)}")
                                "Server returned HTML instead of JSON (HTTP ${e.httpCode}). Check BASE_URL and API endpoint."
                            }
                            else -> {
                                Log.e("DeviceRegistration", "Unknown error: ${e.javaClass.simpleName} - ${e.message}")
                                when {
                                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                                        "No internet connection. Check network and try again."
                                    e.message?.contains("timeout", ignoreCase = true) == true ->
                                        "Connection timed out. Check network and try again."
                                    e.message?.contains("Failed to connect", ignoreCase = true) == true ->
                                        "Could not connect to server. Check connection and try again."
                                    e.message?.contains("SSL", ignoreCase = true) == true ->
                                        "SSL/Security error. Check server certificate."
                                    else ->
                                        "Registration error: ${e.message ?: "Unknown error occurred"}"
                                }
                            }
                        }
                        
                        val deviceIdForError = getDeviceId(data) ?: "null_device_id"
                        registrationRepository.updateRegistrationStatus(
                            deviceId = deviceIdForError,
                            status = "ERROR",
                            serverResponse = errorMessage
                        )
                        
                        val errorTypeLabel = when (e) {
                            is java.net.UnknownHostException -> "UnknownHostException"
                            is java.net.ConnectException -> "ConnectException"
                            is java.net.SocketTimeoutException -> "SocketTimeoutException"
                            is retrofit2.HttpException -> "HTTP ${e.code()}"
                            is com.google.gson.JsonSyntaxException -> "JsonSyntaxException"
                            is SecurityException -> "SecurityException"
                            is ClassCastException -> "ClassCastException"
                            is ServerReturnedHtmlException -> "ServerReturnedHtml"
                            else -> e.javaClass.simpleName
                        }
                        
                        // Enhanced error logging with device data context
                        RegistrationErrorLogger.logRegistrationError(
                            context = this@DeviceDataCollectionActivity,
                            errorType = errorTypeLabel,
                            errorMessage = e.message ?: errorMessage,
                            throwable = e,
                            deviceId = deviceIdForError,
                            loanNumber = data.loanNumber,
                            additionalInfo = mapOf(
                                "android_id_length" to (getAndroidId(data)?.length?.toString() ?: "null"),
                                "bootloader_length" to (getBootloader(data)?.length?.toString() ?: "null"),
                                "has_location" to ((data.locationInfo?.get("latitude") != null && data.locationInfo?.get("longitude") != null).toString())
                            )
                        )
                        
                        Log.e("DeviceRegistration", "Registration error: $errorMessage", e)
                    } catch (dbError: Exception) {
                        Log.e("DeviceRegistration", "Failed to update error status in database: ${dbError.message}")
                    }
                }
                withContext(Dispatchers.Main) {
                    val userMessage = when (e) {
                        is java.net.UnknownHostException -> "Cannot connect to server. Please check your internet connection and try again."
                        is java.net.ConnectException -> "Server is not responding. Please try again later."
                        is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
                        is SecurityException -> "Permission denied: ${e.message}"
                        is ClassCastException -> "Data format error. Please contact support."
                        is ServerReturnedHtmlException -> "Server returned HTML page instead of JSON. Check BASE_URL and API endpoint."
                        is retrofit2.HttpException -> "Server error (${e.code()}). Please try again or contact support."
                        else -> when {
                            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                                "No internet connection. Check network and try again."
                            e.message?.contains("timeout", ignoreCase = true) == true ->
                                "Connection timed out. Check network and try again."
                            e.message?.contains("Failed to connect", ignoreCase = true) == true ->
                                "Could not connect to server. Check connection and try again."
                            else ->
                                "Registration failed: ${e.message ?: "Unknown error"}"
                        }
                    }
                    Toast.makeText(
                        this@DeviceDataCollectionActivity,
                        "$userMessage Error details saved to DeviceOwner/RegistrationErrors.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("DeviceRegistration", "Error during registration: ${e.message}", e)
                }
            }
        }
    }
}

@Composable
fun DeviceDataCollectionScreen(
    loanNumber: String,
    onPermissionsNeeded: () -> Unit,
    onDataCollected: (MutableState<DeviceRegistrationRequest?>) -> Unit,
    onSubmitData: (DeviceRegistrationRequest) -> Unit
) {
    var deviceData by remember { mutableStateOf<DeviceRegistrationRequest?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showValidationError by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val deviceDataState = remember { mutableStateOf<DeviceRegistrationRequest?>(null) }
    
    // Start data collection
    LaunchedEffect(Unit) {
        onDataCollected(deviceDataState)
        
        // Add timeout mechanism
        kotlinx.coroutines.delay(30000) // 30 seconds timeout
        if (deviceDataState.value == null && isLoading) {
            isLoading = false
        }
    }
    
    // Update UI when data is collected
    LaunchedEffect(deviceDataState.value) {
        deviceDataState.value?.let { data ->
            deviceData = data
            isLoading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Device Registration",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Review your device information before registration",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            
            if (isLoading) {
                // Loading State
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Collecting device information...",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                deviceData?.let { data ->
                    // Loan Information Section
                    InfoSection(
                        title = "Loan Information",
                        items = listOf(
                            "Loan Number" to loanNumber
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Device Information Section
                    InfoSection(
                        title = "Device Information",
                        items = listOf(
                            "Device ID" to (data.deviceId ?: "Will be assigned by server"),
                            "Manufacturer" to (data.manufacturer ?: "Unknown"),
                            "Model" to (data.model ?: "Unknown"),
                            "Android ID" to (data.androidId ?: "Not Available")
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // System Information Section
                    InfoSection(
                        title = "System Information",
                        items = listOf(
                            "Build ID" to (data.buildId ?: "Unknown"),
                            "Build Type" to (data.buildType ?: "Unknown"),
                            "Fingerprint" to (data.fingerprint ?: "Unknown"),
                            "Bootloader" to (data.bootloader ?: "Unknown")
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Submit Button
                    Button(
                        onClick = {
                            // Log device identifiers for debugging
                            val hasAndroidId = !data.androidId.isNullOrBlank()
                            
                            Log.d("DeviceRegistration", "Proceeding with registration")
                            Log.d("DeviceRegistration", "Available identifiers - AndroidID: $hasAndroidId")
                            
                            // Always allow registration - Device Owner status not required
                            isSubmitting = true
                            onSubmitData(data)
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Registering Device...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Register Device",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Help Text
                    Text(
                        text = "By registering this device, you agree to the terms and conditions of the loan agreement.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun InfoSection(
    title: String,
    items: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Title
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Items
        items.forEach { (label, value) ->
            InfoItem(
                label = label,
                value = value
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Divider
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    }
}