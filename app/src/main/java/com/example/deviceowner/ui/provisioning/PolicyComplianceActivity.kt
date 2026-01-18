package com.example.deviceowner.ui.provisioning

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Activity to handle Android 12+ policy compliance during provisioning
 * Required for QR code provisioning on Android 12+
 * 
 * This activity responds to ACTION_ADMIN_POLICY_COMPLIANCE intent
 * and confirms policy compliance to the system.
 */
class PolicyComplianceActivity : Activity() {

    companion object {
        private const val TAG = "PolicyComplianceActivity"
        
        // Android 12+ provisioning intents
        const val ACTION_ADMIN_POLICY_COMPLIANCE = "android.app.action.ADMIN_POLICY_COMPLIANCE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "PolicyComplianceActivity created")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            handlePolicyCompliance()
        } else {
            // For Android < 12, just finish with RESULT_OK
            Log.d(TAG, "Android version < 12, finishing with RESULT_OK")
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun handlePolicyCompliance() {
        val action = intent.action
        Log.d(TAG, "Handling intent action: $action")
        
        if (ACTION_ADMIN_POLICY_COMPLIANCE == action) {
            try {
                Log.d(TAG, "Policy compliance confirmed")
                // Return RESULT_OK to confirm policy compliance
                setResult(Activity.RESULT_OK)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling policy compliance", e)
                setResult(Activity.RESULT_CANCELED)
            }
        } else {
            Log.w(TAG, "Unexpected intent action: $action")
            setResult(Activity.RESULT_CANCELED)
        }
        
        finish()
    }
}
