package com.example.deviceowner.ui.activities.provisioning.compatibility

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.R
import com.example.deviceowner.ui.activities.registration.RegistrationStatusActivity

/**
 * Activity displayed when device meets all compatibility requirements.
 * Shows success message and allows user to proceed to registration.
 */
class CompatibilitySuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_compatibility_success)

        // Get device info from intent
        val deviceBrand = intent.getStringExtra("device_brand") ?: "Unknown"
        val deviceModel = intent.getStringExtra("device_model") ?: "Unknown"
        val androidVersion = intent.getIntExtra("android_version", 0)

        // Continue button - proceed to registration
        val btnContinue = findViewById<Button>(R.id.btn_continue_provisioning)
        btnContinue.setOnClickListener {
            navigateToRegistration()
        }
    }

    private fun navigateToRegistration() {
        val intent = Intent(this, RegistrationStatusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent going back during provisioning flow
        // User must complete registration or exit app
    }
}
