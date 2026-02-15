package com.example.deviceowner.ui.activities.provisioning

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.R
import com.example.deviceowner.device.DeviceOwnerCompatibilityChecker
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.ui.activities.RegistrationStatusActivity
import com.example.deviceowner.work.RestrictionEnforcementWorker
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity to show initialization progress and automatically proceed to registration.
 * Includes device compatibility check during initialization.
 */
class ProvisioningProgressActivity : AppCompatActivity() {

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvCurrentService: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provisioning_progress)

        tvCurrentService = findViewById(R.id.tv_current_service)

        // Block back during critical setup (API 33+ standard)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Disable back during critical setup
            }
        })

        // Start initialization process
        startInitialization()
    }

    private fun startInitialization() {
        lifecycleScope.launch {
            try {
                // 1. Initial delay for visual smooth transition
                updateStatus("Connecting to system services...")
                delay(1000)

                // 2. Check Device Compatibility
                updateStatus("Checking device compatibility...")
                val compatibilityChecker = DeviceOwnerCompatibilityChecker(this@ProvisioningProgressActivity)
                val compatibilityResult = compatibilityChecker.checkCompatibility()
                Log.d("ProvisioningProgress", "Compatibility check result: ${compatibilityResult.isCompatible}")
                Log.d("ProvisioningProgress", "Device Info: ${compatibilityResult.deviceInfo}")
                delay(1500)

                // 3. Navigate based on compatibility
                if (compatibilityResult.isCompatible) {
                    // Device meets requirements - show success screen
                    navigateToCompatibilitySuccess(compatibilityResult)
                } else {
                    // Device doesn't meet requirements - show failure screen
                    navigateToCompatibilityFailure(compatibilityResult)
                }

            } catch (e: Exception) {
                Log.e("ProvisioningProgress", "Initialization failed", e)
                updateStatus("Error: ${e.message}")
                delay(2000)
                navigateToCompatibilityFailure(null)
            }
        }
    }

    private fun updateStatus(status: String) {
        tvCurrentService.text = status
    }

    private fun navigateToCompatibilitySuccess(result: DeviceOwnerCompatibilityChecker.CompatibilityResult) {
        val intent = Intent(this, CompatibilitySuccessActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("device_brand", result.deviceInfo.brand)
            putExtra("device_model", result.deviceInfo.model)
            putExtra("android_version", result.deviceInfo.androidVersion)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToCompatibilityFailure(result: DeviceOwnerCompatibilityChecker.CompatibilityResult?) {
        val intent = Intent(this, CompatibilityFailureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (result != null) {
                putExtra("issues", result.issues.toTypedArray())
                putExtra("device_brand", result.deviceInfo.brand)
                putExtra("device_model", result.deviceInfo.model)
                putExtra("android_version", result.deviceInfo.androidVersion)
            }
        }
        startActivity(intent)
        finish()
    }

}
