package com.microspace.payo.ui.activities.provisioning.compatibility.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.microspace.payo.device.DeviceOwnerCompatibilityChecker
import com.microspace.payo.ui.activities.provisioning.compatibility.screens.CompatibilitySuccessActivity
import com.microspace.payo.ui.activities.provisioning.compatibility.screens.CompatibilityFailureActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Controller Activity for Device Compatibility Checks.
 * Automatically routes to Success or Failure screens based on system validation.
 */
class DeviceCompatibilityCheckActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CompatibilityCheck"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val compatibilityChecker = DeviceOwnerCompatibilityChecker(this)
        val result = compatibilityChecker.checkCompatibility()

        if (result.isCompatible) {
            Log.i(TAG, "✅ Device is compatible. Routing to success.")
            navigateToSuccess(result)
        } else {
            Log.e(TAG, "❌ Device NOT compatible. Routing to failure.")
            navigateToFailure(result)
        }
    }

    private fun navigateToSuccess(result: DeviceOwnerCompatibilityChecker.CompatibilityResult) {
        val intent = Intent(this, CompatibilitySuccessActivity::class.java).apply {
            putExtra("device_brand", result.deviceInfo.brand)
            putExtra("device_model", result.deviceInfo.model)
            putExtra("android_version", result.deviceInfo.androidVersion)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToFailure(result: DeviceOwnerCompatibilityChecker.CompatibilityResult) {
        val intent = Intent(this, CompatibilityFailureActivity::class.java).apply {
            putExtra("issues", result.issues.toTypedArray())
            putExtra("device_brand", result.deviceInfo.brand)
            putExtra("device_model", result.deviceInfo.model)
        }
        startActivity(intent)
        finish()
    }
}
