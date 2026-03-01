package com.microspace.payo.ui.activities.provisioning.mode

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Optimized ProvisioningModeActivity.
 * Fixed: Removed early storage access to prevent "corruption" during initial setup.
 * This activity handles the very first handshake with Android's provisioning system.
 */
class ProvisioningModeActivity : Activity() {

    companion object {
        private const val TAG = "ProvisioningModeActivity"
        const val ACTION_GET_PROVISIONING_MODE = "android.app.action.GET_PROVISIONING_MODE"
        const val EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES = "android.app.extra.PROVISIONING_ALLOWED_PROVISIONING_MODES"
        const val EXTRA_PROVISIONING_MODE = "android.app.extra.PROVISIONING_MODE"
        const val PROVISIONING_MODE_FULLY_MANAGED_DEVICE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // DO NOT access SharedPreferences or Databases here.
        // The system is in a highly sensitive state during the initial QR scan.
        
        Log.d(TAG, "ProvisioningModeActivity: Handshaking with system...")

        try {
            val resultIntent = Intent()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val allowedModes = intent.getIntegerArrayListExtra(EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
                val mode = if (allowedModes?.contains(PROVISIONING_MODE_FULLY_MANAGED_DEVICE) == true) {
                    PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                } else {
                    allowedModes?.firstOrNull() ?: PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                }
                resultIntent.putExtra(EXTRA_PROVISIONING_MODE, mode)
            } else {
                resultIntent.putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_FULLY_MANAGED_DEVICE)
            }

            setResult(Activity.RESULT_OK, resultIntent)
            Log.i(TAG, "Provisioning mode set successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in provisioning handshake: ${e.message}")
            setResult(Activity.RESULT_CANCELED)
        } finally {
            finish()
        }
    }
}




