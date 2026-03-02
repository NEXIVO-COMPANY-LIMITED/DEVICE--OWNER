package com.microspace.payo.ui.activities.provisioning.mode

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Optimized ProvisioningModeActivity.
 * Fixed: Added safety checks and explicit intent extras for Android 12+ handshake.
 */
class ProvisioningModeActivity : Activity() {

    companion object {
        private const val TAG = "ProvisioningMode"
        const val ACTION_GET_PROVISIONING_MODE = "android.app.action.GET_PROVISIONING_MODE"
        const val EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES = "android.app.extra.PROVISIONING_ALLOWED_PROVISIONING_MODES"
        const val EXTRA_PROVISIONING_MODE = "android.app.extra.PROVISIONING_MODE"
        const val PROVISIONING_MODE_FULLY_MANAGED_DEVICE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val resultIntent = Intent()
            
            // Get the modes allowed by the system (e.g., Work Profile vs. Fully Managed)
            val allowedModes = intent?.getIntegerArrayListExtra(EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
            
            // Mode 1 is PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            val selectedMode = if (allowedModes?.contains(1) == true) {
                1
            } else {
                allowedModes?.firstOrNull() ?: 1
            }
            
            resultIntent.putExtra(EXTRA_PROVISIONING_MODE, selectedMode)
            setResult(Activity.RESULT_OK, resultIntent)
            
            Log.d(TAG, "Provisioning mode selected: $selectedMode from allowed: $allowedModes")
        } catch (e: Exception) {
            // Fallback to avoid setup block
            setResult(Activity.RESULT_OK)
            Log.e(TAG, "Provisioning mode handshake error: ${e.message}")
        } finally {
            finish()
        }
    }
}
