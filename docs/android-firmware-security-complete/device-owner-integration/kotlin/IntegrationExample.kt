package com.yourcompany.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yourcompany.deviceowner.security.FirmwareSecurity
import com.yourcompany.deviceowner.security.activateSecurityModeAsync
import kotlinx.coroutines.launch

/**
 * Complete Integration Example
 * ============================
 * 
 * This shows how to integrate firmware security into your existing
 * Device Owner application. Copy relevant parts into your app.
 */

// 1. Device Admin Receiver (required for Device Owner)
class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Owner enabled")
    }
    
    companion object {
        private const val TAG = "MyDeviceAdminReceiver"
        
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, MyDeviceAdminReceiver::class.java)
        }
    }
}

// 2. Device Registration Activity
class DeviceRegistrationActivity : AppCompatActivity() {
    
    private val TAG = "DeviceRegistration"
    private lateinit var devicePolicyManager: DevicePolicyManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) 
            as DevicePolicyManager
        
        // Check if we're Device Owner
        if (!devicePolicyManager.isDeviceOwnerApp(packageName)) {
            Log.e(TAG, "App is not Device Owner!")
            // Handle error
            return
        }
        
        // Proceed with registration
        lifecycleScope.launch {
            registerDevice()
        }
    }
    
    private suspend fun registerDevice() {
        try {
            Log.i(TAG, "=== Starting Device Registration ===")
            
            // Step 1: Register with your server
            val deviceInfo = collectDeviceInfo()
            val response = yourApiService.registerDevice(deviceInfo)
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Server registration failed")
                return
            }
            
            val loanId = response.body()?.loanId ?: return
            Log.i(TAG, "✓ Registered with server. Loan ID: $loanId")
            
            // Step 2: Activate firmware security
            Log.i(TAG, "Activating firmware security...")
            val securityResult = FirmwareSecurity.activateSecurityModeAsync()
            
            securityResult.fold(
                onSuccess = {
                    Log.i(TAG, "✓ Firmware security activated")
                    
                    // Step 3: Verify security status
                    val status = FirmwareSecurity.checkSecurityStatus()
                    if (status != null && status.isFullySecured) {
                        Log.i(TAG, "✓ Device fully secured")
                        
                        // Step 4: Report security status to server
                        yourApiService.reportSecurityStatus(
                            loanId = loanId,
                            status = status.toServerReport()
                        )
                        
                        // Step 5: Save local state
                        saveRegistrationState(loanId, status)
                        
                        // Success!
                        onRegistrationComplete()
                    } else {
                        Log.w(TAG, "⚠ Security verification failed")
                        handlePartialSecurity(status)
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "✗ Security activation failed", error)
                    handleSecurityFailure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            handleRegistrationError(e)
        }
    }
    
    private fun collectDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            serialNumber = devicePolicyManager.serialNumber,
            imei = getImei(), // Implement based on your needs
            // ... other info
        )
    }
    
    private fun onRegistrationComplete() {
        Log.i(TAG, "=== Registration Complete ===")
        
        // Start monitoring service
        startSecurityMonitoringService()
        
        // Navigate to main screen
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    private fun handlePartialSecurity(status: FirmwareSecurity.SecurityStatus?) {
        // Decide how to handle partial security
        // Options:
        // 1. Allow but report to server
        // 2. Block registration until fully secured
        // 3. Provide manual remediation steps
        
        Log.w(TAG, "Partial security: $status")
        // Your logic here...
    }
    
    private fun handleSecurityFailure(error: Throwable) {
        // Handle security activation failure
        // This is critical - device may not be properly secured
        
        Log.e(TAG, "Security failure - device may be unsecured!")
        // Your error handling...
    }
    
    // ... other helper methods
}

// 3. Security Monitoring Service (runs in background)
class SecurityMonitoringService : Service() {
    
    private val TAG = "SecurityMonitoring"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Security monitoring started")
        
        // Start monitoring for violations
        scope.launch {
            FirmwareSecurity.monitorViolations(this@SecurityMonitoringService) { violation ->
                handleViolation(violation)
            }
        }
        
        return START_STICKY
    }
    
    private fun handleViolation(violation: FirmwareSecurity.Violation) {
        Log.w(TAG, "Security violation detected: ${violation.type}")
        
        // Actions you can take:
        // 1. Log to server
        yourApiService.reportViolation(violation)
        
        // 2. Send notification
        showViolationNotification(violation)
        
        // 3. Lock device if too many violations
        val status = FirmwareSecurity.checkSecurityStatus()
        if (status?.violations?.total ?: 0 > 10) {
            lockDevice("Too many security violations")
        }
        
        // 4. Record locally
        recordViolation(violation)
    }
    
    private fun lockDevice(reason: String) {
        Log.w(TAG, "Locking device: $reason")
        
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) 
            as DevicePolicyManager
        
        dpm.lockNow()
        
        // Optionally wipe if critical violation
        // dpm.wipeData(0)
    }
    
    // ... other methods
}

// 4. Main Application Class
class MyDeviceOwnerApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "App started")
        
        // Check security status on app start
        checkSecurityOnStartup()
    }
    
    private fun checkSecurityOnStartup() {
        val status = FirmwareSecurity.checkSecurityStatus()
        
        if (status == null) {
            Log.w(TAG, "Could not check security status")
            return
        }
        
        Log.i(TAG, "Security status:")
        Log.i(TAG, "  Bootloader: ${if (status.bootloaderLocked) "LOCKED" else "UNLOCKED"}")
        Log.i(TAG, "  Button blocking: ${if (status.buttonBlocking) "ACTIVE" else "INACTIVE"}")
        Log.i(TAG, "  Violations: ${status.violations.total}")
        
        if (!status.isFullySecured) {
            Log.w(TAG, "⚠ Device not fully secured!")
            // Handle appropriately...
        }
    }
    
    companion object {
        private const val TAG = "MyDeviceOwnerApp"
    }
}

// 5. Data Classes
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val serialNumber: String?,
    val imei: String?
)

// 6. API Service Interface
interface YourApiService {
    suspend fun registerDevice(info: DeviceInfo): Response<RegistrationResponse>
    suspend fun reportSecurityStatus(loanId: String, status: Map<String, Any>)
    suspend fun reportViolation(violation: FirmwareSecurity.Violation)
}

data class RegistrationResponse(
    val loanId: String,
    val success: Boolean
)
