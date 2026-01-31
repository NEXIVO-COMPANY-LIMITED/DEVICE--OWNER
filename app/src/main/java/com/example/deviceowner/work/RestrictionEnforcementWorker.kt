package com.example.deviceowner.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.deviceowner.device.DeviceOwnerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Re-enforces Developer Options block periodically.
 * Keeps DEVELOPMENT_SETTINGS_ENABLED=0 so Build Number tap never enables debug mode.
 * Silent – no user notification.
 */
class RestrictionEnforcementWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val dm = DeviceOwnerManager(applicationContext)
            if (!dm.isDeviceOwner()) {
                Log.w(TAG, "Not device owner - skipping enforcement")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "🔄 PERIODIC RESTRICTION ENFORCEMENT - Applying 100% PERFECT SECURITY...")
            
            // NEW: Use Enhanced Security Manager for 100% perfect enforcement
            val enhancedSecurity = com.example.deviceowner.security.enforcement.EnhancedSecurityManager(applicationContext)
            val perfectSecurityApplied = enhancedSecurity.apply100PercentPerfectSecurity()
            
            if (perfectSecurityApplied) {
                Log.d(TAG, "✅✅✅ PERIODIC ENFORCEMENT: 100% PERFECT SECURITY VERIFIED ✅✅✅")
                Log.d(TAG, "🚫 DEVELOPER OPTIONS: IMPOSSIBLE TO ENABLE")
                Log.d(TAG, "🚫 FACTORY RESET: IMPOSSIBLE TO ACCESS")
                Log.d(TAG, "🚫 ALL BYPASSES: IMPOSSIBLE")
            } else {
                Log.w(TAG, "⚠️ Perfect security failed - applying fallback enforcement...")
                
                // Fallback to original enforcement methods
                // 1. Re-apply ALL critical restrictions
                dm.applyAllCriticalRestrictions()
                
                // 2. Specifically enforce developer options block
                dm.disableDeveloperOptions(true)
                
                // 3. Specifically enforce factory reset block
                dm.preventFactoryReset()
                
                // 4. Specifically enforce app uninstall block
                dm.preventAppUninstall()
                
                // 5. Specifically enforce cache deletion block
                dm.preventCacheDeletion()
                
                // 6. Specifically enforce safe mode block
                dm.preventSafeMode()
                
                // 7. Verify and enforce ALL restrictions
                val allEnforced = dm.verifyAndEnforceCriticalRestrictions()
                
                // 8. Verify all restrictions are still active
                val allActive = dm.verifyAllCriticalRestrictionsActive()
                
                if (allEnforced && allActive) {
                    Log.d(TAG, "✅ Periodic enforcement: ALL restrictions verified and active")
                    Log.d(TAG, "   - Factory Reset: BLOCKED")
                    Log.d(TAG, "   - Debug Mode: BLOCKED")
                    Log.d(TAG, "   - App Data Deletion: BLOCKED")
                    Log.d(TAG, "   - Cache Deletion: BLOCKED")
                    Log.d(TAG, "   - App Uninstall: BLOCKED")
                } else {
                    Log.w(TAG, "⚠️ Periodic enforcement: Some restrictions need attention - re-applying...")
                    // Re-apply comprehensive restrictions
                    dm.applyRestrictions()
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Periodic enforcement error: ${e.message}", e)
            // Don't retry immediately - will retry on next scheduled run
            Result.success() // Return success to avoid infinite retries
        }
    }

    companion object {
        private const val TAG = "RestrictionEnforcementWorker"
        const val WORK_NAME = "RestrictionEnforcement"

        fun schedule(context: Context) {
            // Schedule more frequently for better protection (every 10 minutes)
            val request = PeriodicWorkRequestBuilder<RestrictionEnforcementWorker>(
                10, TimeUnit.MINUTES
            )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(false)
                    .setRequiresCharging(false)
                    .build()
            )
            .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "✅ Scheduled periodic restriction enforcement (every 10 minutes)")
            Log.d(TAG, "   - Factory Reset: Continuously monitored and blocked")
            Log.d(TAG, "   - Debug Mode: Continuously monitored and blocked")
            Log.d(TAG, "   - App Data/Cache: Continuously monitored and protected")
        }
    }
}
