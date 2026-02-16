package com.example.deviceowner.ui.activities.provisioning.compatibility

import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.R

/**
 * Activity to show compatibility check failures during provisioning.
 * Displays "Device Not Supported" with options to see security standards.
 */
class DeviceCompatibilityCheckActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_compatibility_check)

        // Setup the "PAYO Security Standards" click listener
        findViewById<TextView>(R.id.btn_security_standards).setOnClickListener {
            showSecurityStandardsDialog()
        }
    }

    /**
     * Shows a dialog listing the specific PAYO security requirements.
     */
    private fun showSecurityStandardsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("PAYO Security Standards")
        
        val standards = """
            To ensure data integrity and device security, PAYO requires the following:
            
            • Device: Any manufacturer/model
            • OS Version: Android 12 or higher
            
            If your device does not meet these rules, installation is prohibited.
        """.trimIndent()

        builder.setMessage(standards)
        builder.setPositiveButton("I Understand") { dialog, _ ->
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.show()
    }
    
}
