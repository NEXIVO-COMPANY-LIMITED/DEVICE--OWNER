package com.example.deviceowner.presentation.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.utils.helpers.SharedPreferencesManager

/**
 * Registration Status Activity - Determines which activity to launch
 * 
 * Flow:
 * 1. Check if device is already registered
 * 2. If registered -> Launch MainActivity
 * 3. If not registered -> Launch DeviceRegistrationActivity
 */
class RegistrationStatusActivity : AppCompatActivity() {
    
    private lateinit var prefsManager: SharedPreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefsManager = SharedPreferencesManager(this)
        
        // Determine which activity to launch
        if (prefsManager.isRegistrationCompleted()) {
            // Device is already registered, go to main activity
            launchMainActivity()
        } else {
            // Device needs registration, go to registration flow
            launchRegistrationFlow()
        }
    }
    
    private fun launchMainActivity() {
        val intent = Intent(this, com.example.deviceowner.presentation.activities.MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun launchRegistrationFlow() {
        val intent = Intent(this, DeviceRegistrationActivity::class.java)
        startActivity(intent)
        finish()
    }
}