package com.example.deviceowner

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.deviceowner.managers.protection.RegistrationDataProtectionManager
import com.example.deviceowner.ui.screens.WelcomeScreen
import com.example.deviceowner.ui.screens.AgentLoginScreen
import com.example.deviceowner.ui.screens.DeviceScannerScreen
import com.example.deviceowner.ui.screens.DeviceInfoScreen
import com.example.deviceowner.ui.screens.SuccessScreen
import com.example.deviceowner.ui.screens.HomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set status bar icons to black
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        
        setContent {
            AppNavigation(this)
        }
    }
}

@Composable
fun AppNavigation(activity: MainActivity) {
    var currentScreen by remember { mutableStateOf<String?>(null) }
    var loanId by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var imeiList by remember { mutableStateOf(listOf<String>()) }
    var serialNumber by remember { mutableStateOf("") }
    
    // Check registration on app startup
    LaunchedEffect(Unit) {
        val protectionManager = RegistrationDataProtectionManager(activity)
        val startupScreen = protectionManager.getStartupScreen()
        
        if (startupScreen == "home") {
            // Load stored registration data
            val regData = protectionManager.getStoredRegistrationData()
            if (regData != null) {
                deviceId = regData.deviceId
                loanId = regData.loanId
                imeiList = regData.imeiList
                serialNumber = regData.serialNumber
                Log.d("MainActivity", "✓ Loaded stored registration: $deviceId")
            }
        }
        
        currentScreen = startupScreen
    }
    
    // Show loading while checking registration
    if (currentScreen == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    when (currentScreen) {
        "welcome" -> {
            WelcomeScreen(
                onGetStarted = {
                    currentScreen = "login"
                }
            )
        }
        "login" -> {
            AgentLoginScreen(
                onLoginSuccess = { loan ->
                    loanId = loan
                    currentScreen = "scanner"
                },
                onBackToWelcome = {
                    currentScreen = "welcome"
                }
            )
        }
        "scanner" -> {
            DeviceScannerScreen(
                loanId = loanId,
                onScanComplete = { imei, serial ->
                    Log.d("MainActivity", "Scanner complete - imei: $imei, serial: $serial")
                    imeiList = imei
                    serialNumber = serial
                    deviceId = imei.firstOrNull() ?: ""
                    Log.d("MainActivity", "Navigating to deviceInfo with - imeiList: $imeiList, serialNumber: $serialNumber")
                    currentScreen = "deviceInfo"
                },
                onBackToLogin = {
                    currentScreen = "login"
                }
            )
        }
        "deviceInfo" -> {
            DeviceInfoScreen(
                loanId = loanId,
                imeiList = imeiList,
                serialNumber = serialNumber,
                onNavigateBack = {
                    currentScreen = "scanner"
                },
                onNavigateToScanner = {
                    currentScreen = "scanner"
                },
                onRegistrationSuccess = { scannedDeviceId, scannedLoanId ->
                    deviceId = scannedDeviceId
                    loanId = scannedLoanId
                    currentScreen = "success"
                }
            )
        }
        "success" -> {
            SuccessScreen(
                deviceId = deviceId,
                loanId = loanId,
                onGoToHome = {
                    currentScreen = "home"
                }
            )
        }
        "home" -> {
            HomeScreen(
                deviceId = deviceId,
                loanId = loanId,
                onLogout = {
                    currentScreen = "welcome"
                }
            )
        }
    }
}