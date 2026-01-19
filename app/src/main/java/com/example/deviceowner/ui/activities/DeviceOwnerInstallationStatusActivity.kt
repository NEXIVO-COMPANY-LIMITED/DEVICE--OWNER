package com.example.deviceowner.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.deviceowner.ui.screens.DeviceOwnerInstallationStatusScreen

/**
 * Activity that displays Device Owner installation status
 * This activity is launched automatically after Device Owner initialization completes
 * Shows success or error based on installation result
 */
class DeviceOwnerInstallationStatusActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceOwnerInstallationStatusScreen(
                        onBack = {
                            // Finish activity and go back to launcher
                            finish()
                        }
                    )
                }
            }
        }
    }
}
