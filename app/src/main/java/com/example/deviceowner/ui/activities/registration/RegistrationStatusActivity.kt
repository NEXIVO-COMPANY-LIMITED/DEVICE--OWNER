package com.example.deviceowner.ui.activities.registration

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.data.repository.DeviceRegistrationRepository
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.example.deviceowner.ui.activities.data.DeviceDataCollectionActivity
import com.example.deviceowner.ui.activities.provisioning.consent.DataPrivacyConsentActivity
import com.example.deviceowner.ui.activities.main.DeviceDetailActivity
import kotlinx.coroutines.launch

/**
 * Registration Status Activity - Determines which activity to launch
 *
 * Flow:
 * 1. Check if device is already registered successfully
 * 2. If registered -> Launch DeviceDetailActivity (Main dashboard)
 * 3. If not registered -> Launch Registration Flow
 */
class RegistrationStatusActivity : AppCompatActivity() {

    private lateinit var prefsManager: SharedPreferencesManager
    private lateinit var registrationRepository: DeviceRegistrationRepository

    companion object {
        private const val TAG = "RegistrationStatus"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure keyboard and touch working during registration
        val controlPrefs = getSharedPreferences("control_prefs", MODE_PRIVATE)
        controlPrefs.edit().putBoolean("skip_security_restrictions", true).apply()
        
        prefsManager = SharedPreferencesManager(this)
        registrationRepository = DeviceRegistrationRepository(this)

        lifecycleScope.launch {
            try {
                // 1. Consent Check
                if (!DataPrivacyConsentActivity.hasConsent(this@RegistrationStatusActivity)) {
                    launchDataPrivacyConsent()
                    return@launch
                }

                // 2. Check registration status
                var isRegistered = registrationRepository.isDeviceRegistered()
                
                // 3. Attempt restore if not found (prevents data loss on update)
                if (!isRegistered) {
                    if (registrationRepository.restoreRegistrationDataFromBackup()) {
                        isRegistered = true
                    }
                }

                // 4. If registered, go to the MAIN page (DeviceDetailActivity)
                if (isRegistered) {
                    Log.d(TAG, "Device registered - launching DeviceDetailActivity as main screen")
                    launchMainDashboard()
                    return@launch
                }

                // 5. Resume in-progress
                val inProgressLoanNumber = registrationRepository.getInProgressLoanNumber()
                if (!inProgressLoanNumber.isNullOrEmpty()) {
                    launchDataCollectionDirectly(inProgressLoanNumber)
                    return@launch
                }

                // 6. Fresh start
                launchRegistrationFlow()
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                launchRegistrationFlow()
            }
        }
    }

    private fun launchDataPrivacyConsent() {
        startActivity(Intent(this, DataPrivacyConsentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun launchMainDashboard() {
        startActivity(Intent(this, DeviceDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun launchRegistrationFlow() {
        startActivity(Intent(this, com.example.deviceowner.presentation.activities.DeviceRegistrationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun launchDataCollectionDirectly(loanNumber: String) {
        startActivity(Intent(this, DeviceDataCollectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(DeviceDataCollectionActivity.EXTRA_LOAN_NUMBER, loanNumber)
        })
        finish()
    }
}
