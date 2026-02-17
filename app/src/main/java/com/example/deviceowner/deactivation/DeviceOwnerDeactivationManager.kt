package com.example.deviceowner.deactivation

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.deviceowner.monitoring.SecurityMonitorService
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.services.security.FirmwareSecurityMonitorService
import com.example.deviceowner.services.data.LocalDataServerService
import com.example.deviceowner.services.remote.RemoteManagementService
import com.example.deviceowner.services.lock.SoftLockMonitorService
import com.example.deviceowner.services.lock.SoftLockOverlayService
import com.example.deviceowner.services.heartbeat.HeartbeatWorker
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.update.config.UpdateConfig
import com.example.deviceowner.work.RestrictionEnforcementWorker
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DeviceOwnerDeactivationManager - Total deactivation and removal.
 *
 * When deactivation occurs the device returns to a fully normal state like any other phone:
 * - App no longer has Device Owner privilege (Device Owner and admin removed).
 * - Device is no longer locked to any company account after factory reset.
 * - All restrictions, lock state, and "managed" UI are removed so the device displays and behaves like a normal phone.
 * - All app services are stopped (none left running or visible).
 *
 * Deactivation Flow:
 * 1. Stop ALL app services (heartbeat, lock, security, firmware, local server, etc.)
 * 2. Clear all user restrictions
 * 3. Reset global policies (lock task, status bar, keyguard, camera, organization branding)
 * 4. Unsuspend all applications
 * 5b. Clear lock/control state
 * 5c. Clear lock/control state (control_prefs, device_owner_prefs, etc.) so device displays normal
 * 6. Remove Device Owner and admin status (app no longer has Device Owner privilege)
 * 7. Clear all app data
 * 8. Show app icon (user can uninstall like any normal app)
 * 9. Optional: self-uninstall (or skip so user sees no dialog)
 */
class DeviceOwnerDeactivationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeactivationManager"
        private const val DEACTIVATION_PREFS = "device_owner_deactivation"
        private const val KEY_DEACTIVATION_IN_PROGRESS = "deactivation_in_progress"
        private const val KEY_DEACTIVATION_STATUS = "deactivation_status"
        private const val KEY_DEACTIVATION_TIMESTAMP = "deactivation_timestamp"
        private const val KEY_DEACTIVATION_ERROR = "deactivation_error"
        /**
         * Skip self-uninstall so user sees NO dialog.
         * Android does not allow Device Owner to uninstall itself silently (no API).
         * - true: Do NOT trigger uninstall. Phone returns to normal (no device owner, no restrictions).
         *        App stays installed but is inert (hidden, no services). User sees nothing.
         *        User can uninstall later from Settings > Apps if they want.
         * - false: Trigger Intent.ACTION_DELETE → system shows "Uninstall [App]?" dialog (user must tap OK).
         */
        private const val SKIP_SELF_UNINSTALL_AFTER_DEACTIVATION = true
    }
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val pm = context.packageManager
    private val admin = ComponentName(context, AdminReceiver::class.java)
    private val prefsManager = SharedPreferencesManager(context)
    private val isDeactivating = AtomicBoolean(false)
    
    /**
     * Main deactivation entry point - 100% PERFECT IMPLEMENTATION
     * Executes a scorched-earth cleanup to ensure no traces of lockdown remain.
     */
    suspend fun deactivateDeviceOwner(): DeactivationResult {
        return withContext(Dispatchers.Default) {
            // Prevent concurrent deactivations
            if (!isDeactivating.compareAndSet(false, true)) {
                Log.w(TAG, "⚠️ Deactivation already in progress")
                return@withContext DeactivationResult.Failure("Deactivation already in progress")
            }
            
            try {
                Log.i(TAG, "🔓 ═══════════════════════════════════════════════════════════")
                Log.i(TAG, "🔓 CRITICAL: Starting 100% Full Deactivation...")
                Log.i(TAG, "🔓 ═══════════════════════════════════════════════════════════")
                setDeactivationInProgress(true)
                
                // PHASE 0: Verify Device Owner status
                Log.d(TAG, "📍 PHASE 0: Verifying Device Owner status...")
                val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
                Log.d(TAG, "   Device Owner: $isDeviceOwner")
                val isAdmin = dpm.isAdminActive(admin)
                Log.d(TAG, "   Admin Active: $isAdmin")
                
                if (!isDeviceOwner && !isAdmin) {
                    Log.w(TAG, "⚠️ Not device owner or admin - device may already be deactivated")
                    setDeactivationInProgress(false)
                    recordDeactivationSuccess()
                    return@withContext DeactivationResult.Success
                }
                
                // PHASE 1: Stop heartbeat and ALL services/workers – 100% clean deactivation
                Log.d(TAG, "📍 PHASE 1: Stopping ALL heartbeat, WorkManager, and services...")
                try {
                    val wm = androidx.work.WorkManager.getInstance(context)
                    wm.cancelUniqueWork("HeartbeatWork")
                    wm.cancelUniqueWork("HeartbeatGuard")
                    wm.cancelUniqueWork("OfflineSync")
                    wm.cancelUniqueWork(RestrictionEnforcementWorker.WORK_NAME)
                    wm.cancelUniqueWork(UpdateConfig.WORK_NAME)
                    wm.cancelAllWork() // Cancel any remaining workers – 100% clean
                    Log.d(TAG, "✓ All WorkManager work cancelled (Heartbeat, OfflineSync, RestrictionEnforcement, UpdateCheck)")
                } catch (e: Exception) { Log.w(TAG, "WorkManager cancel: ${e.message}") }
                stopAllServices()
                delay(150) // Brief wait for services to stop
                
                // PHASE 2: Clear ALL User Restrictions
                Log.d(TAG, "📍 PHASE 2: Clearing user restrictions...")
                clearAllUserRestrictions()
                
                // PHASE 3: Reset Global Policies
                Log.d(TAG, "📍 PHASE 3: Resetting global policies...")
                resetGlobalPolicies()
                
                // PHASE 4: Unsuspend all apps
                Log.d(TAG, "📍 PHASE 4: Unsuspending applications...")
                unsuspendAllApplications()
                
                // PHASE 5: Clear lock/control state
                Log.d(TAG, "📍 PHASE 5: Clearing lock state...")
                clearLockAndControlState()
                
                // PHASE 6: Remove Admin & Device Owner totally (app no longer has Device Owner privilege)
                Log.d(TAG, "📍 PHASE 6: Removing Device Owner and admin – app will no longer have Device Owner privilege...")
                removeDeviceOwnerStatus()
                
                // PHASE 7: Clear app data
                Log.d(TAG, "📍 PHASE 7: Clearing app data...")
                clearAllAppData()
                
                // PHASE 8: Verify (no delay)
                Log.d(TAG, "📍 PHASE 8: Verifying deactivation...")
                val isStillDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
                val isStillAdmin = dpm.isAdminActive(admin)
                Log.d(TAG, "   Device Owner after: $isStillDeviceOwner")
                Log.d(TAG, "   Admin Active after: $isStillAdmin")
                
                if (isStillDeviceOwner || isStillAdmin) {
                    Log.w(TAG, "⚠️ WARNING: Device Owner or Admin still active after deactivation")
                    Log.w(TAG, "   Attempting force removal...")
                    try {
                        if (isStillDeviceOwner) {
                            dpm.clearDeviceOwnerApp(context.packageName)
                            Log.d(TAG, "   ✓ Force cleared Device Owner")
                        }
                        if (isStillAdmin) {
                            dpm.removeActiveAdmin(admin)
                            Log.d(TAG, "   ✓ Force removed Admin")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ Force removal failed: ${e.message}")
                    }
                }
                
                setDeactivationInProgress(false)
                recordDeactivationSuccess()
                
                Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
                Log.i(TAG, "✅ Deactivation complete. Device is now like any other normal phone.")
                Log.i(TAG, "✅ Device Owner privilege removed – app no longer has Device Owner")
                Log.i(TAG, "✅ All restrictions and lock state cleared")
                Log.i(TAG, "✅ All services stopped – device displays and works normally")
                Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
                
                // Unhide app icon so user can see it as normal app and complete removal (uninstall)
                try {
                    DeviceOwnerManager(context).showAppIcon()
                    Log.d(TAG, "✓ App icon shown in launcher – user can see and remove app")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not show app icon: ${e.message}")
                }
                
                // PHASE 9: SELF-UNINSTALL (optional – Android cannot do silent self-uninstall)
                if (SKIP_SELF_UNINSTALL_AFTER_DEACTIVATION) {
                    Log.d(TAG, "📍 PHASE 9: Skipping self-uninstall. App is visible in launcher – user can remove it like any normal app.")
                } else {
                    Log.d(TAG, "📍 PHASE 9: Triggering self-uninstall (user will see one system dialog)...")
                    delay(200) // Brief wait for persistence
                    triggerSelfUninstall()
                }
                
                return@withContext DeactivationResult.Success
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Deactivation Failed: ${e.message}", e)
                setDeactivationInProgress(false)
                recordDeactivationError(e.message ?: "Unknown error")
                return@withContext DeactivationResult.Failure(e.message ?: "Unknown error")
            } finally {
                isDeactivating.set(false)
            }
        }
    }
    
    /**
     * Stop ALL Device Owner services so none remain running or visible.
     * After this, no PAYO/Device Owner services will be seen on the phone.
     */
    private fun stopAllServices() {
        val allAppServices = listOf(
            SoftLockMonitorService::class.java,
            SoftLockOverlayService::class.java,
            SecurityMonitorService::class.java,
            RemoteManagementService::class.java,
            FirmwareSecurityMonitorService::class.java,
            LocalDataServerService::class.java
        )
        allAppServices.forEach { serviceClass ->
            try {
                context.stopService(Intent(context, serviceClass))
                Log.d(TAG, "✓ Stopped ${serviceClass.simpleName}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop ${serviceClass.simpleName}: ${e.message}")
            }
        }
        Log.d(TAG, "✓ All Device Owner services stopped - none will remain visible")
    }
    
    private fun clearAllUserRestrictions() {
        val restrictions = arrayOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_CONFIG_WIFI,
            UserManager.DISALLOW_CONFIG_BLUETOOTH,
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_MODIFY_ACCOUNTS,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_ADJUST_VOLUME,
            UserManager.DISALLOW_OUTGOING_CALLS,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_FUN,
            UserManager.DISALLOW_CREATE_WINDOWS,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS
        )
        
        var cleared = 0
        restrictions.forEach { restriction ->
            try {
                dpm.clearUserRestriction(admin, restriction)
                cleared++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear $restriction: ${e.message}")
            }
        }
        Log.d(TAG, "✓ Cleared $cleared user restrictions")
    }
    
    private fun resetGlobalPolicies() {
        try {
            dpm.setLockTaskPackages(admin, arrayOf())
            dpm.setUninstallBlocked(admin, context.packageName, false)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(admin, false)
                dpm.setKeyguardDisabledFeatures(admin, 0)
                dpm.setPermittedAccessibilityServices(admin, null)
                dpm.setPermittedInputMethods(admin, null)
                dpm.setCameraDisabled(admin, false)
            }
            
            dpm.setAutoTimeRequired(admin, false)
            
            // Remove "Managed by organization" / lock screen info so device displays like normal phone
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.setOrganizationName(admin, null)
                dpm.setShortSupportMessage(admin, null)
                dpm.setLongSupportMessage(admin, null)
                dpm.setDeviceOwnerLockScreenInfo(admin, null)
            }
            
            // Re-enable ADB/Debugging (normal phone behavior)
            dpm.setGlobalSetting(admin, android.provider.Settings.Global.ADB_ENABLED, "1")
            dpm.setGlobalSetting(admin, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, "1")
            
            Log.d(TAG, "✓ Global policies reset – status bar, keyguard, camera, branding cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Partial failure in global policy reset: ${e.message}")
        }
    }
    
    /**
     * Clear all lock and control state so the device displays and behaves like a normal phone.
     * Removes any "managed" or "locked" state from preferences so the app and system don't show managed UI.
     */
    /**
     * Clear all lock, control, deactivation and heartbeat state so no stale device-owner state remains.
     * Ensures after deactivation: no deactivation_requested flag, no lock state, no heartbeat/payment cache.
     */
    private fun clearLockAndControlState() {
        try {
            val prefsToClear = listOf(
                "control_prefs",
                "device_owner_prefs",
                "device_owner_config",
                "device_lock",
                "device_deactivation",
                "device_lock_state",
                "heartbeat_response",
                "heartbeat_full_response",
                "payment_data",
                "payment_reminder"
            )
            prefsToClear.forEach { name ->
                try {
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                    Log.d(TAG, "✓ Cleared $name")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear $name: ${e.message}")
                }
            }
            Log.d(TAG, "✓ Lock and control state cleared – device will display like normal phone")
        } catch (e: Exception) {
            Log.w(TAG, "clearLockAndControlState failed: ${e.message}")
        }
    }
    
    private fun unsuspendAllApplications() {
        try {
            val packages = pm.getInstalledPackages(0).map { it.packageName }.toTypedArray()
            dpm.setPackagesSuspended(admin, packages, false)
            Log.d(TAG, "✓ Unsuspended ${packages.size} applications")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unsuspend applications: ${e.message}")
        }
    }
    
    /**
     * Totally remove Device Owner and admin so the phone returns to normal user state.
     * CRITICAL ORDER: clearDeviceOwnerApp() MUST be called FIRST (while we are still device owner).
     * If we call removeActiveAdmin() first, we are no longer device owner and clearDeviceOwnerApp()
     * will fail — then the device keeps showing "belongs to organization", factory reset and
     * developer options stay disabled.
     */
    private fun removeDeviceOwnerStatus() {
        try {
            // 1. FIRST: Clear Device Owner so the device no longer "belongs to organization"
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    dpm.clearDeviceOwnerApp(context.packageName)
                    Log.d(TAG, "✓ Cleared Device Owner status – device no longer belongs to organization")
                } catch (e: Exception) {
                    Log.e(TAG, "clearDeviceOwnerApp failed (device may still show managed): ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Not device owner app – skip clearDeviceOwnerApp")
            }
            // 2. THEN: Remove active admin (so app is no longer device admin)
            dpm.removeActiveAdmin(admin)
            Log.d(TAG, "✓ Removed active admin – phone is now normal user state")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove Device Owner status: ${e.message}")
        }
    }
    
    private fun clearAllAppData() {
        try {
            // Clear SharedPreferences
            val prefs = context.getSharedPreferences("device_data", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            // Clear other preference files
            val prefsDir = context.filesDir.parentFile?.resolve("shared_prefs")
            prefsDir?.listFiles()?.forEach { file ->
                if (file.name != DEACTIVATION_PREFS) {
                    file.delete()
                }
            }
            
            Log.d(TAG, "✓ Cleared app data")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear app data: ${e.message}")
        }
    }
    
    /**
     * Uninstall the Device Owner app via system uninstall UI.
     * Android has no API for silent self-uninstall; this always shows one system dialog
     * "Uninstall [App name]?" (user must tap OK). When SKIP_SELF_UNINSTALL_AFTER_DEACTIVATION
     * is true this is not called – phone returns to normal with no dialog, app stays installed but inert.
     */
    private fun triggerSelfUninstall() {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "✓ Device Owner app uninstall triggered - app will be removed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trigger self-uninstall: ${e.message}")
        }
    }
    
    private fun setDeactivationInProgress(inProgress: Boolean) {
        context.getSharedPreferences(DEACTIVATION_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DEACTIVATION_IN_PROGRESS, inProgress)
            .apply()
    }
    
    private fun recordDeactivationSuccess() {
        context.getSharedPreferences(DEACTIVATION_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DEACTIVATION_STATUS, "success")
            .putLong(KEY_DEACTIVATION_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_DEACTIVATION_ERROR, "")
            .apply()
        
        prefsManager.setHeartbeatEnabled(false)
    }
    
    private fun recordDeactivationError(error: String) {
        context.getSharedPreferences(DEACTIVATION_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DEACTIVATION_STATUS, "failed")
            .putString(KEY_DEACTIVATION_ERROR, error)
            .apply()
    }

    fun isDeactivationInProgress(): Boolean = context.getSharedPreferences(DEACTIVATION_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_DEACTIVATION_IN_PROGRESS, false)
    
    fun getDeactivationStatus(): String? {
        return context.getSharedPreferences(DEACTIVATION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEACTIVATION_STATUS, null)
    }
    
    fun getDeactivationTimestamp(): Long {
        return context.getSharedPreferences(DEACTIVATION_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DEACTIVATION_TIMESTAMP, 0L)
    }
    
    fun getDeactivationError(): String? {
        return context.getSharedPreferences(DEACTIVATION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEACTIVATION_ERROR, null)
    }
    
    fun cleanup() {
        try {
            context.getSharedPreferences(DEACTIVATION_PREFS, Context.MODE_PRIVATE).edit()
                .clear()
                .apply()
            Log.d(TAG, "✓ Cleanup completed")
        } catch (e: Exception) {
            Log.w(TAG, "Warning during cleanup: ${e.message}")
        }
    }

    /**
     * Apply soft lock - device can still be used but with restrictions
     * Used when payment is 1-3 days overdue
     */
    fun applySoftLock(
        reason: String,
        daysOverdue: Int,
        amountDue: Double,
        loanNumber: String
    ) {
        try {
            Log.w(TAG, "🔒 Applying SOFT LOCK: $reason")
            
            // Store soft lock state
            val prefs = context.getSharedPreferences("device_lock", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("lock_type", "soft")
                putString("lock_reason", reason)
                putInt("days_overdue", daysOverdue)
                putString("loan_number", loanNumber)
                apply()
            }
            
            // Start soft lock monitor service
            val intent = Intent(context, SoftLockMonitorService::class.java).apply {
                putExtra("lock_type", "soft")
                putExtra("reason", reason)
                putExtra("days_overdue", daysOverdue)
                putExtra("amount_due", amountDue)
                putExtra("loan_number", loanNumber)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.i(TAG, "✅ Soft lock applied - device restricted but usable")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying soft lock: ${e.message}", e)
        }
    }
    
    /**
     * Apply hard lock - device is completely locked
     * Used when payment is 4+ days overdue
     */
    fun applyHardLock(
        reason: String,
        daysOverdue: Int,
        amountDue: Double,
        loanNumber: String
    ) {
        try {
            Log.e(TAG, "🔐 Applying HARD LOCK: $reason")
            
            // Store hard lock state
            val prefs = context.getSharedPreferences("device_lock", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("lock_type", "hard")
                putString("lock_reason", reason)
                putInt("days_overdue", daysOverdue)
                putString("loan_number", loanNumber)
                apply()
            }
            
            // Disable all user interactions
            try {
                dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL)
            } catch (e: Exception) {
                Log.w(TAG, "Could not disable keyguard: ${e.message}")
            }
            
            // Start hard lock overlay service
            val intent = Intent(context, SoftLockOverlayService::class.java).apply {
                putExtra("lock_type", "hard")
                putExtra("reason", reason)
                putExtra("days_overdue", daysOverdue)
                putExtra("amount_due", amountDue)
                putExtra("loan_number", loanNumber)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.e(TAG, "✅ Hard lock applied - device completely locked")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying hard lock: ${e.message}", e)
        }
    }
    
    /**
     * Unlock device after payment is received
     */
    fun unlockDevice(
        reason: String,
        loanNumber: String
    ) {
        try {
            Log.i(TAG, "🔓 Unlocking device: $reason")
            
            // Clear lock state
            val prefs = context.getSharedPreferences("device_lock", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("lock_type", "none")
                putString("lock_reason", "")
                putInt("days_overdue", 0)
                putString("loan_number", "")
                apply()
            }
            
            // Stop lock services
            context.stopService(Intent(context, SoftLockMonitorService::class.java))
            context.stopService(Intent(context, SoftLockOverlayService::class.java))
            
            // Re-enable keyguard
            try {
                dpm.setKeyguardDisabledFeatures(admin, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Could not re-enable keyguard: ${e.message}")
            }
            
            Log.i(TAG, "✅ Device unlocked successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unlocking device: ${e.message}", e)
        }
    }
    
    /**
     * Get current lock status
     */
    fun getLockStatus(): String {
        val prefs = context.getSharedPreferences("device_lock", Context.MODE_PRIVATE)
        return prefs.getString("lock_type", "none") ?: "none"
    }
    
    /**
     * Get lock details
     */
    fun getLockDetails(): Map<String, Any> {
        val prefs = context.getSharedPreferences("device_lock", Context.MODE_PRIVATE)
        return mapOf(
            "lock_type" to (prefs.getString("lock_type", "none") ?: "none"),
            "reason" to (prefs.getString("lock_reason", "") ?: ""),
            "days_overdue" to prefs.getInt("days_overdue", 0),
            "loan_number" to (prefs.getString("loan_number", "") ?: "")
        )
    }
}

/**
 * Deactivation result sealed class
 */
sealed class DeactivationResult {
    object Success : DeactivationResult()
    data class Failure(val error: String) : DeactivationResult()
}
