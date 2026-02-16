package com.example.deviceowner.ui.activities.provisioning.compatibility

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.R

/**
 * Activity displayed when device does NOT meet compatibility requirements.
 * Shows failure message and prevents provisioning from continuing.
 */
class CompatibilityFailureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_compatibility_check)

        // Get device info and issues from intent
        val issues = intent.getStringArrayExtra("issues") ?: emptyArray()
        val deviceBrand = intent.getStringExtra("device_brand") ?: "Unknown"
        val deviceModel = intent.getStringExtra("device_model") ?: "Unknown"
        val androidVersion = intent.getIntExtra("android_version", 0)

        // Update error message with specific issues
        val errorMessage = findViewById<TextView>(R.id.compatibility_error_message)
        val issuesText = if (issues.isNotEmpty()) {
            "Issues found:\n" + issues.joinToString("\n") { "• $it" }
        } else {
            "This device does not meet PAYO security requirements."
        }
        errorMessage.text = issuesText

        // Security standards link
        val securityStandardsLink = findViewById<TextView>(R.id.btn_security_standards)
        securityStandardsLink.setOnClickListener {
            // Open support or documentation link
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://support.payo.com/device-requirements"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // If no browser available, just show toast
            }
        }
    }

    override fun onBackPressed() {
        // Allow user to exit the app
        finishAffinity()
    }
}
