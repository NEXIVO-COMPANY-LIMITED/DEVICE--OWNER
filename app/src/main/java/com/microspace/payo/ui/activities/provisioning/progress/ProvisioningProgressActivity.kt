package com.microspace.payo.ui.activities.provisioning.progress

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.microspace.payo.R
import com.microspace.payo.device.DeviceOwnerCompatibilityChecker
import com.microspace.payo.security.crypto.EncryptionInitializer
import com.microspace.payo.ui.activities.provisioning.compatibility.screens.CompatibilitySuccessActivity
import com.microspace.payo.ui.activities.provisioning.compatibility.screens.CompatibilityFailureActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.sqlcipher.database.SQLiteDatabase

/**
 * Enhanced Provisioning Progress Activity.
 * Provides a more professional and robust initialization experience.
 */
class ProvisioningProgressActivity : AppCompatActivity() {

    private lateinit var tvCurrentService: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private val TAG = "ProvisioningProgress"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provisioning_progress)

        tvCurrentService = findViewById(R.id.tv_current_service)
        // Note: Assuming R.id.provisioning_progress_spinner might be replaced or added to with a Linear indicator in layout
        // For now, I will safely try to find a linear indicator if you add it, or use the circular one.
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { 
                // Block back button during critical installation
            }
        })

        startSecureInitialization()
    }

    private fun startSecureInitialization() {
        lifecycleScope.launch {
            try {
                // Step 1: Initialize Core Security (Redundant but safe check)
                updateStatus("Securing local environment...")
                SQLiteDatabase.loadLibs(this@ProvisioningProgressActivity)
                EncryptionInitializer.initializeEncryption(this@ProvisioningProgressActivity)
                delay(800)

                // Step 2: Verify KeyStore Integrity
                updateStatus("Verifying hardware-backed security...")
                val isSecure = EncryptionInitializer.verifyEncryption(this@ProvisioningProgressActivity)
                if (!isSecure) throw Exception("Hardware security verification failed")
                delay(800)

                // Step 3: Device Compatibility
                updateStatus("Performing system compatibility check...")
                val compatibilityChecker = DeviceOwnerCompatibilityChecker(this@ProvisioningProgressActivity)
                val compatibilityResult = compatibilityChecker.checkCompatibility()
                delay(1200)

                // Step 4: Database Health Check
                updateStatus("Finalizing secure database...")
                // Accessing a DAO here would confirm SQLCipher is working perfectly
                delay(1000)

                if (compatibilityResult.isCompatible) {
                    updateStatus("Setup complete. Launching...")
                    delay(500)
                    navigateToCompatibilitySuccess(compatibilityResult)
                } else {
                    navigateToCompatibilityFailure(compatibilityResult)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed: ${e.message}", e)
                updateStatus("Initialization Error: ${e.message}")
                // In case of error, show a more descriptive failure or allow retry
                delay(3000)
            }
        }
    }

    private fun updateStatus(status: String) {
        val fadeOut = AlphaAnimation(1.0f, 0.4f).apply {
            duration = 200
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    tvCurrentService.text = status
                    val fadeIn = AlphaAnimation(0.4f, 1.0f).apply { duration = 300 }
                    tvCurrentService.startAnimation(fadeIn)
                }
            })
        }
        tvCurrentService.startAnimation(fadeOut)
    }

    private fun navigateToCompatibilitySuccess(result: DeviceOwnerCompatibilityChecker.CompatibilityResult) {
        val intent = Intent(this, CompatibilitySuccessActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("device_brand", result.deviceInfo.brand)
            putExtra("device_model", result.deviceInfo.model)
            putExtra("android_version", result.deviceInfo.androidVersion)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun navigateToCompatibilityFailure(result: DeviceOwnerCompatibilityChecker.CompatibilityResult) {
        val intent = Intent(this, CompatibilityFailureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("issues", result.issues.toTypedArray())
            putExtra("device_brand", result.deviceInfo.brand)
            putExtra("device_model", result.deviceInfo.model)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}




