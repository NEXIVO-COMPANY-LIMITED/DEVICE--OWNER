package com.example.deviceowner.ui.provisioning

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Activity to handle Android 12+ provisioning mode selection
 * Required for QR code provisioning on Android 12+
 * 
 * This activity responds to ACTION_GET_PROVISIONING_MODE intent
 * and returns the provisioning mode to the system.
 */
class ProvisioningModeActivity : Activity() {

    companion object {
        private const val TAG = "ProvisioningModeActivity"
        
        // Android 12+ provisioning intents - Using DevicePolicyManager constants if available
        // For Android < 12, these may not exist, so we define them manually
        const val ACTION_GET_PROVISIONING_MODE = "android.app.action.GET_PROVISIONING_MODE"
        const val EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES = 
            "android.app.extra.PROVISIONING_ALLOWED_PROVISIONING_MODES"
        const val EXTRA_PROVISIONING_MODE = "android.app.extra.PROVISIONING_MODE"
        
        // Provisioning mode values (from DevicePolicyManager)
        const val PROVISIONING_MODE_FULLY_MANAGED_DEVICE = 1
        const val PROVISIONING_MODE_MANAGED_PROFILE = 2
        
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ProvisioningModeActivity created")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            handleGetProvisioningMode()
        } else {
            // For Android < 12, just finish with RESULT_OK
            Log.d(TAG, "Android version < 12, finishing with RESULT_OK")
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun handleGetProvisioningMode() {
        val action = intent.action
        Log.d(TAG, "Handling intent action: $action")
        
        if (ACTION_GET_PROVISIONING_MODE == action) {
            try {
                // Get allowed provisioning modes from intent
                val allowedModes = intent.getIntegerArrayListExtra(EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
                Log.d(TAG, "Allowed provisioning modes: $allowedModes")
                
                // Return fully managed device mode if allowed, otherwise use first available mode
                val provisioningMode = if (allowedModes != null && allowedModes.isNotEmpty()) {
                    if (allowedModes.contains(PROVISIONING_MODE_FULLY_MANAGED_DEVICE)) {
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                    } else {
                        allowedModes[0] // Use first allowed mode
                    }
                } else {
                    // Default to fully managed device mode
                    PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                }
                
                Log.d(TAG, "Returning provisioning mode: $provisioningMode")
                
                // Set result with provisioning mode
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_PROVISIONING_MODE, provisioningMode)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling GET_PROVISIONING_MODE", e)
                setResult(Activity.RESULT_CANCELED)
            }
        } else {
            Log.w(TAG, "Unexpected intent action: $action")
            setResult(Activity.RESULT_CANCELED)
        }
        
        finish()
    }
}
