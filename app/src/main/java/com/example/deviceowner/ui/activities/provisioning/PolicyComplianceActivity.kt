package com.example.deviceowner.ui.activities.provisioning

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import com.example.deviceowner.R

/**
 * Activity to handle Android 12+ policy compliance during provisioning
 * Required for QR code provisioning on Android 12+
 * 
 * This activity responds to ACTION_ADMIN_POLICY_COMPLIANCE intent
 * and confirms policy compliance to the system.
 * 
 * IMPORTANT: This activity MUST show a UI and wait for user confirmation.
 * Finishing immediately will cause provisioning to fail.
 * 
 * DIAGNOSTIC DATA WITHDRAWAL MESSAGE:
 * If you see the message "Based on the admin policy set for your phone,
 * the following agreement has been withdrawn: Sending of Diagnostic Data",
 * this is NORMAL and expected behavior for Android Enterprise devices.
 * 
 * This message appears because:
 * - Android Enterprise Recommended (AER) policies require diagnostic data
 *   collection to be disabled during Device Owner provisioning
 * - Even if the user tries to enable diagnostic data, the policy overrides it
 * - This is by design for compliance with Android Enterprise recommendations
 * 
 * This message does NOT indicate a provisioning failure. Provisioning will
 * continue normally after the user clicks "Continue" on this screen.
 */
class PolicyComplianceActivity : Activity() {

    companion object {
        private const val TAG = "PolicyComplianceActivity"
        
        // Android 12+ provisioning intents
        const val ACTION_ADMIN_POLICY_COMPLIANCE = "android.app.action.ADMIN_POLICY_COMPLIANCE"
    }
    
    // Flag to track if result has been set
    private var resultSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "PolicyComplianceActivity created")
        Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "========================================")
        
        val action = intent.action
        Log.d(TAG, "Intent action: $action")
        
        // Log all intent extras for debugging
        val extras = intent.extras
        if (extras != null) {
            Log.d(TAG, "Intent extras keys: ${extras.keySet()}")
            for (key in extras.keySet()) {
                val value = extras.get(key)
                Log.d(TAG, "  Extra[$key] = $value (${value?.javaClass?.simpleName})")
            }
        } else {
            Log.d(TAG, "No intent extras")
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ACTION_ADMIN_POLICY_COMPLIANCE == action) {
                    // Show UI and wait for user confirmation
                    Log.d(TAG, "Showing policy compliance UI for user confirmation")
                    Log.d(TAG, "NOTE: If you see 'diagnostic data agreement withdrawn' message,")
                    Log.d(TAG, "this is NORMAL and expected for Android Enterprise devices.")
                    Log.d(TAG, "It means diagnostic data collection is disabled by policy.")
                    Log.d(TAG, "Provisioning will continue normally after clicking Continue.")
                    setContentView(R.layout.activity_policy_compliance)
                    setupContinueButton()
                } else {
                    Log.w(TAG, "Unexpected intent action: $action")
                    // For unexpected actions, still return OK to allow provisioning to continue
                    Log.d(TAG, "Returning RESULT_OK to allow provisioning to continue")
                    setResult(Activity.RESULT_OK)
                    resultSet = true
                    finish()
                }
            } else {
                // For Android < 12, just finish with RESULT_OK
                Log.d(TAG, "Android version < 12, finishing with RESULT_OK")
                setResult(Activity.RESULT_OK)
                resultSet = true
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in PolicyComplianceActivity", e)
            // Always return OK to prevent blocking provisioning
            setResult(Activity.RESULT_OK)
            resultSet = true
            finish()
        }
    }

    private fun setupContinueButton() {
        try {
            val continueButton = findViewById<Button>(R.id.continue_button)
            if (continueButton == null) {
                Log.e(TAG, "CRITICAL: Continue button not found in layout!")
                // Still return OK to allow provisioning
                setResult(Activity.RESULT_OK)
                resultSet = true
                finish()
                return
            }
            
            continueButton.setOnClickListener {
                Log.d(TAG, "========================================")
                Log.d(TAG, "User confirmed policy compliance")
                Log.d(TAG, "Continuing provisioning...")
                Log.d(TAG, "NOTE: Diagnostic data withdrawal message is normal")
                Log.d(TAG, "and does not indicate a provisioning failure.")
                Log.d(TAG, "========================================")
                
                try {
                    // Return RESULT_OK to confirm policy compliance
                    // This allows provisioning to continue even if diagnostic data
                    // collection was withdrawn by policy (which is expected behavior)
                    setResult(Activity.RESULT_OK)
                    resultSet = true
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Error finishing PolicyComplianceActivity", e)
                    // Even on error, return OK to prevent blocking provisioning
                    try {
                        setResult(Activity.RESULT_OK)
                        resultSet = true
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error setting result: ${e2.message}")
                    }
                    finish()
                }
            }
            
            Log.d(TAG, "Continue button setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up continue button", e)
            // Always return OK to prevent blocking
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onBackPressed() {
        // Prevent back button from canceling provisioning
        // User must click Continue to proceed
        Log.d(TAG, "Back button pressed - ignoring to prevent provisioning cancellation")
        // Do not call super.onBackPressed() to prevent cancellation
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PolicyComplianceActivity destroyed")
        // Ensure we always return OK if activity is destroyed without explicit result
        // This prevents provisioning from being blocked if activity is destroyed unexpectedly
        try {
            if (!resultSet) {
                Log.d(TAG, "Activity finishing without explicit result - setting RESULT_OK as fallback")
                setResult(Activity.RESULT_OK)
                resultSet = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting result in onDestroy: ${e.message}")
        }
    }
}
