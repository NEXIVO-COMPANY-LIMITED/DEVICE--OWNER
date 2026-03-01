package com.microspace.payo.ui.activities.provisioning.compatibility.screens

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.microspace.payo.R
import com.microspace.payo.ui.activities.registration.RegistrationStatusActivity

/**
 * Polished Compatibility Success Screen.
 * Displays device details and allows proceeding to the next step.
 */
class CompatibilitySuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_compatibility_success)

        val brand = intent.getStringExtra("device_brand") ?: "Device"
        val model = intent.getStringExtra("device_model") ?: "Certified"
        
        // Update UI with verified info if views exist
        findViewById<TextView>(R.id.tv_device_name)?.text = "$brand $model"

        findViewById<Button>(R.id.btn_continue_provisioning).setOnClickListener {
            val intent = Intent(this, RegistrationStatusActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onBackPressed() {
        // Stay in the flow
    }
}




