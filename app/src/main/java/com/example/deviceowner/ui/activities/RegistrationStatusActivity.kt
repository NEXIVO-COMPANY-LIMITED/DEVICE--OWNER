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
 * 2. If registered -> Launch MainActivity (shows loan info)
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
        
        prefsManager = SharedPreferencesManager(this)
        registrationRepository = DeviceRegistrationRepository(this)
        
        // Check registration status from database
        lifecycleScope.launch {
            try {
                val isRegistered = registrationRepository.isDeviceRegistered()
                
                Log.d(TAG, "Registration status check: isRegistered = $isRegistered")
                
                if (isRegistered) {
                    // Device is successfully registered, go directly to main activity
                    Log.d(TAG, "Device is registered - launching MainActivity")
                    launchMainActivity()
                } else {
                    // Device needs registration, check if we have a saved loan number
                    val storedLoanNumber = registrationRepository.getStoredLoanNumber()
                    
                    if (storedLoanNumber != null) {
                        // We have a loan number but registration not completed
                        Log.d(TAG, "Found stored loan number but registration incomplete - launching registration flow")
                        launchRegistrationFlow()
                    } else {
                        // No loan number saved, start fresh registration
                        Log.d(TAG, "No registration data found - launching fresh registration")
                        launchRegistrationFlow()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking registration status: ${e.message}", e)
                // On error, default to registration flow
                launchRegistrationFlow()
            }
        }
    }
    
    private fun launchMainActivity() {
        val intent = Intent(this, com.example.deviceowner.presentation.activities.MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun launchRegistrationFlow() {
        val intent = Intent(this, DeviceRegistrationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}