package com.example.deviceowner.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.R
import com.example.deviceowner.data.repository.DeviceRegistrationRepository
import com.example.deviceowner.presentation.activities.MainActivity
import com.example.deviceowner.utils.SharedPreferencesManager
import kotlinx.coroutines.launch

class DeviceRegistrationActivity : AppCompatActivity() {
    
    private lateinit var etLoanNumber: EditText
    private lateinit var btnContinue: Button
    private lateinit var prefsManager: SharedPreferencesManager
    private lateinit var registrationRepository: DeviceRegistrationRepository
    
    companion object {
        private const val TAG = "DeviceRegistrationActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_registration)
        
        prefsManager = SharedPreferencesManager(this)
        registrationRepository = DeviceRegistrationRepository(this)
        
        // CRITICAL: Check if device is already registered
        lifecycleScope.launch {
            if (registrationRepository.isDeviceRegistered()) {
                Log.d(TAG, "Device is already registered, redirecting to MainActivity")
                Toast.makeText(this@DeviceRegistrationActivity, "Device is already registered", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
                return@launch
            }
            
            // Check if we have a stored loan number from previous attempt
            val storedLoanNumber = registrationRepository.getStoredLoanNumber()
            if (storedLoanNumber != null) {
                Log.d(TAG, "Found stored loan number: $storedLoanNumber, pre-filling field")
                etLoanNumber.setText(storedLoanNumber)
            }
        }
        
        initViews()
        setupClickListeners()
    }
    
    private fun initViews() {
        etLoanNumber = findViewById(R.id.etLoanNumber)
        btnContinue = findViewById(R.id.btnValidateLoan)
    }
    
    private fun setupClickListeners() {
        btnContinue.setOnClickListener {
            val loanNumber = etLoanNumber.text.toString().trim()
            if (validateLoanInput(loanNumber)) {
                proceedToDataReview(loanNumber)
            }
        }
        
        // HIDDEN DEBUG TRIGGER: Long press to open network diagnostics
        btnContinue.setOnLongClickListener {
            try {
                Toast.makeText(this, "Launching Network Diagnostics...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, com.example.deviceowner.ui.activities.NetworkDiagnosticActivity::class.java)
                startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error launching diagnostics", e)
                false
            }
        }
    }
    
    private fun validateLoanInput(loanNumber: String): Boolean {
        return when {
            loanNumber.isEmpty() -> {
                Toast.makeText(this, "Please enter loan number", Toast.LENGTH_SHORT).show()
                false
            }
            loanNumber.length < 3 -> {
                Toast.makeText(this, "Loan number must be at least 3 characters", Toast.LENGTH_SHORT).show()
                false
            }
            else -> {
                prefsManager.saveLoanNumber(loanNumber)
                true
            }
        }
    }
    
    private fun proceedToDataReview(loanNumber: String) {
        // Save loan number immediately for future use
        lifecycleScope.launch {
            try {
                // Pass null as deviceId since server will assign it
                registrationRepository.saveLoanNumberForRegistration(loanNumber, null)
                Log.d(TAG, "Loan number saved to database: $loanNumber")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving loan number: ${e.message}", e)
            }
        }
        
        val intent = Intent(this, com.example.deviceowner.presentation.activities.DeviceDataCollectionActivity::class.java).apply {
            putExtra("loan_number", loanNumber)
        }
        startActivity(intent)
        finish()
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}