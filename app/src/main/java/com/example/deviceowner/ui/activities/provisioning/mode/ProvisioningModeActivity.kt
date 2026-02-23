package com.example.deviceowner.ui.activities.provisioning.mode

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
        Log.d(TAG, "=========================================")
        Log.d(TAG, "ProvisioningModeActivity created")
        Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "=========================================")

        // CRITICAL: Check device compatibility BEFORE provisioning
        Log.d(TAG, "🔍 Checking device compatibility...")
        val validator = com.example.deviceowner.provisioning.ProvisioningValidator(this)
        val isCompatible = validator.validateBeforeProvisioning()

        if (!isCompatible) {
            Log.e(TAG, "❌ Device is NOT compatible - blocking provisioning")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        Log.i(TAG, "✅ Device is compatible - proceeding with provisioning")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                handleGetProvisioningMode()
            } else {
                // For Android < 12, provisioning mode selection is not required
                // But we should still return a result to allow provisioning to continue
                Log.d(TAG, "Android version < 12, returning fully managed device mode")
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_FULLY_MANAGED_DEVICE)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in ProvisioningModeActivity", e)
            // Always return fully managed device mode as fallback
            try {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_FULLY_MANAGED_DEVICE)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error creating result intent", e2)
                setResult(Activity.RESULT_OK)
            }
            finish()
        }
    }

    private fun handleGetProvisioningMode() {
        val action = intent.action
        Log.d(TAG, "Handling intent action: $action")

        try {
            if (ACTION_GET_PROVISIONING_MODE == action) {
                // Get allowed provisioning modes from intent
                val allowedModes = intent.getIntegerArrayListExtra(EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
                Log.d(TAG, "Allowed provisioning modes: $allowedModes")

                // Return fully managed device mode if allowed, otherwise use first available mode
                val provisioningMode = if (allowedModes != null && allowedModes.isNotEmpty()) {
                    if (allowedModes.contains(PROVISIONING_MODE_FULLY_MANAGED_DEVICE)) {
                        Log.d(TAG, "✓ Fully managed device mode is allowed - using it")
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                    } else {
                        Log.w(TAG, "⚠ Fully managed device mode NOT allowed. Allowed modes: $allowedModes")
                        allowedModes[0] // Use first allowed mode
                    }
                } else {
                    // Default to fully managed device mode
                    Log.d(TAG, "No allowed modes specified - defaulting to fully managed device mode")
                    PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                }

                Log.d(TAG, "=========================================")
                Log.d(TAG, "Returning provisioning mode: $provisioningMode")
                Log.d(TAG, "=========================================")

                // Set result with provisioning mode
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_PROVISIONING_MODE, provisioningMode)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } else {
                Log.w(TAG, "Unexpected intent action: $action")
                // Return fully managed device mode as fallback
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_FULLY_MANAGED_DEVICE)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR handling GET_PROVISIONING_MODE", e)
            // Always return fully managed device mode as fallback
            try {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_FULLY_MANAGED_DEVICE)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error creating fallback result intent", e2)
                setResult(Activity.RESULT_OK)
            }
        }

        finish()
    }
}
