package com.example.deviceowner.ui.activities.provisioning.compatibility

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.R

/**
 * Activity to show compatibility check success during provisioning.
 * Allows the user to proceed with the device installation.
 */
class DeviceCompatibilitySuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_compatibility_success)

        // Setup the "CONTINUE INSTALLATION" button click listener
        findViewById<Button>(R.id.btn_continue_provisioning).setOnClickListener {
            // Signal back to the system that we've accepted the provisioning mode
            // In a real flow, this might need to pass data back to ProvisioningModeActivity
            // But since this is a separate activity launched, we use it to confirm the user's intent.
            //
            // To continue provisioning after showing this screen, we typically return to
            // the activity that handles the system's provisioning intent.
            finishWithResult(Activity.RESULT_OK)
        }
    }


    private fun finishWithResult(resultCode: Int) {
        val resultIntent = Intent()
        // Add any necessary provisioning mode extras here if needed
        setResult(resultCode, resultIntent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent accidental exit
        super.onBackPressed()
    }
}
