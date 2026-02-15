package com.example.deviceowner.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.data.repository.DeviceRegistrationRepository
import com.example.deviceowner.utils.SharedPreferencesManager
import kotlinx.coroutines.launch

/**
 * Registration Status Activity - Determines which activity to launch
 * 
 * Flow:
 * 1. Check if device is already registered successfully in database
 * 2. If registered -> Launch RegistrationSuccessActivity (Shows success state and runs heartbeat)
 * 3. If not registered -> Launch DeviceRegistrationActivity (input loan number)
 */
class RegistrationStatusActivity : AppCompatActivity() {
    
    private lateinit var prefsManager: SharedPreferencesManager
    private lateinit var registrationRepository: DeviceRegistrationRepository
    
    companion object {
        private const val TAG = "RegistrationStatus"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CRITICAL: Check Device Owner status (for logging only; registration flow runs in both cases)
        val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(this)
        val isDeviceOwner = deviceOwnerManager.isDeviceOwner()
        if (!isDeviceOwner) {
            Log.w(TAG, "⚠️ Not device owner yet – showing registration flow (loan number → device info → success)")
        }

        // CRITICAL: Keep keyboard and touch working – no lock/overlay during registration
        val controlPrefs = getSharedPreferences("control_prefs", MODE_PRIVATE)
        controlPrefs.edit().putBoolean("skip_security_restrictions", true).apply()
        getSharedPreferences("device_owner_prefs", MODE_PRIVATE).edit().putBoolean("skip_security_restrictions", true).apply()
        com.example.deviceowner.services.SoftLockOverlayService.stopOverlay(this)
        Log.d(TAG, "🔓 skip_security_restrictions set – overlay stopped – keyboard and touch enabled for registration")
        
        prefsManager = SharedPreferencesManager(this)
        registrationRepository = DeviceRegistrationRepository(this)
        
        // Check registration status from database
        lifecycleScope.launch {
            try {
                // Step 1: If user hasn't accepted privacy consent (first install), show consent screen first
                if (!com.example.deviceowner.ui.activities.provisioning.DataPrivacyConsentActivity.hasConsent(this@RegistrationStatusActivity)) {
                    Log.d(TAG, "First install - showing Data Privacy Consent")
                    launchDataPrivacyConsent()
                    return@launch
                }

                // Step 2: If device is fully registered, go to success
                val isRegistered = registrationRepository.isDeviceRegistered()
                val deviceIdFromPrefs = getSharedPreferences("device_registration", MODE_PRIVATE)
                    .getString("device_id", null)
                
                Log.d(TAG, "Device ID from prefs: $deviceIdFromPrefs, isRegistered: $isRegistered")
                
                if (isRegistered && !deviceIdFromPrefs.isNullOrEmpty()) {
                    Log.d(TAG, "Device registered - launching RegistrationSuccessActivity")
                    launchSuccessActivity()
                    return@launch
                }
                
                // Step 3: Check for in-progress registration (user entered loan number, closed app)
                val inProgressLoanNumber = registrationRepository.getInProgressLoanNumber()
                if (!inProgressLoanNumber.isNullOrEmpty()) {
                    Log.d(TAG, "Resuming registration with saved loan number: $inProgressLoanNumber")
                    launchDataCollectionDirectly(inProgressLoanNumber)
                    return@launch
                }
                
                // Step 4: Fresh start - show loan number input
                Log.d(TAG, "No in-progress registration - launching loan number input")
                launchRegistrationFlow()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking registration status: ${e.message}", e)
                launchRegistrationFlow()
            }
        }
    }
    
    private fun launchDataPrivacyConsent() {
        val intent = Intent(this, com.example.deviceowner.ui.activities.provisioning.DataPrivacyConsentActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun launchSuccessActivity() {
        val intent = Intent(this, RegistrationSuccessActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun launchRegistrationFlow() {
        val intent = Intent(this, com.example.deviceowner.presentation.activities.DeviceRegistrationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /** Resume registration: go directly to DeviceDataCollectionActivity with saved loan number. */
    private fun launchDataCollectionDirectly(loanNumber: String) {
        val intent = Intent(this, DeviceDataCollectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra(DeviceDataCollectionActivity.EXTRA_LOAN_NUMBER, loanNumber)
        startActivity(intent)
        finish()
    }
}
