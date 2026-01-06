package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.managers.RemovalAttemptTracker
import com.example.deviceowner.managers.IdentifierAuditLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Real-time package removal receiver
 * Detects unauthorized app removal attempts immediately
 * Provides instant response to removal threats
 */
class PackageRemovalReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "PackageRemovalReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            // Check if this is a package removal action
            if (intent.action != Intent.ACTION_PACKAGE_REMOVED) {
                Log.d(TAG, "Ignoring non-removal action: ${intent.action}")
                return
            }
            
            // Get the package name from the intent
            val packageName = intent.data?.schemeSpecificPart
            
            if (packageName == null) {
                Log.w(TAG, "Package name not found in intent")
                return
            }
            
            // Check if it's our app being removed
            if (packageName == context.packageName) {
                Log.e(TAG, "CRITICAL: Real-time package removal detected for our app!")
                
                // Log the incident immediately
                val auditLog = IdentifierAuditLog(context)
                auditLog.logIncident(
                    type = "REAL_TIME_REMOVAL_DETECTED",
                    severity = "CRITICAL",
                    details = "Package removal broadcast received for: $packageName"
                )
                
                // Handle removal attempt asynchronously
                handleRemovalAttempt(context)
            } else {
                Log.d(TAG, "Package removal detected for different app: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in PackageRemovalReceiver", e)
        }
    }
    
    /**
     * Handle removal attempt asynchronously
     */
    private fun handleRemovalAttempt(context: Context) {
        GlobalScope.launch {
            try {
                Log.e(TAG, "Handling unauthorized removal attempt...")
                
                val removalTracker = RemovalAttemptTracker(context)
                val attemptNumber = removalTracker.recordRemovalAttempt()
                
                if (attemptNumber > 0) {
                    Log.e(TAG, "✓ Removal attempt #$attemptNumber recorded and queued for backend")
                } else {
                    Log.e(TAG, "✗ Failed to record removal attempt")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling removal attempt", e)
            }
        }
    }
}
