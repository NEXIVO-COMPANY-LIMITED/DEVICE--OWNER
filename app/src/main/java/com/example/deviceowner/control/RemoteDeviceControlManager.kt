package com.example.deviceowner.control

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.monitoring.SecurityMonitorService
import com.example.deviceowner.services.lock.SoftLockOverlayService
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.lock.LockStateRecordEntity
import com.example.deviceowner.ui.activities.lock.HardLockActivity
import com.example.deviceowner.utils.constants.UserManagerConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remote lock control: hard lock (kiosk) vs soft lock (reminder).
 */
class RemoteDeviceControlManager(private val context: Context) {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "RemoteControl"
        private const val PREFS = "control_prefs"
        const val LOCK_UNLOCKED = "unlocked"
        const val LOCK_SOFT = "soft_lock"
        const val LOCK_HARD = "hard_lock"
    }
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, AdminReceiver::class.java)
    
    fun getLockState(): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("state", LOCK_UNLOCKED) ?: LOCK_UNLOCKED

    fun getLockStateForBoot(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val devCtx = context.createDeviceProtectedStorageContext()
                val state = devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("state", null)
                if (state != null) return state
            } catch (e: Exception) {
                Log.w(TAG, "Could not read lock state from device-protected: ${e.message}")
            }
        }
        return getLockState()
    }

    fun getLockReasonForBoot(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val devCtx = context.createDeviceProtectedStorageContext()
                val reason = devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("reason", null)
                if (reason != null) return reason
            } catch (_: Exception) { }
        }
        return getLockReason()
    }

    fun getLockTimestampForBoot(): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val devCtx = context.createDeviceProtectedStorageContext()
                val ts = devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getLong("lock_timestamp", 0L)
                if (ts > 0L) return ts
            } catch (_: Exception) { }
        }
        return getLockTimestamp()
    }

    fun isHardLocked(): Boolean = getLockState() == LOCK_HARD
    fun isLocked(): Boolean = getLockState() != LOCK_UNLOCKED
    fun getLockReason(): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("reason", "") ?: ""
    fun getLockTimestamp(): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lock_timestamp", 0L)

    fun applyHardLock(
        reason: String,
        forceRestart: Boolean = false,
        forceFromServerOrMismatch: Boolean = false,
        tamperType: String? = null,
        nextPaymentDate: String? = null,
        organizationName: String? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        Log.e(TAG, "🔒 APPLYING HARD LOCK: $reason")
        
        val skipSecurityRestrictions = prefs.getBoolean("skip_security_restrictions", false)
        if (skipSecurityRestrictions && !forceFromServerOrMismatch) {
            Log.w(TAG, "⚠️ Skipping hard lock during initial setup")
            return
        }
        
        if (!forceRestart && getLockState() == LOCK_HARD) {
            // Even if already locked, ensure activity is visible
            showHardLockActivity(reason, nextPaymentDate, organizationName)
            return
        }
        
        saveState(LOCK_HARD, reason)
        saveHardLockStateToDeviceProtected(reason)
        saveLockStateToDatabase(LOCK_HARD, reason, tamperType)

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "❌ Not Device Owner")
            return
        }
        
        try {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            dpm.setAutoTimeRequired(admin, true)
            suspendAllOtherPackages(true)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(admin, true)
                dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL)
            }
            
            val restrictions = arrayOf(
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
                UserManager.DISALLOW_USB_FILE_TRANSFER,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
            )
            
            for (restriction in restrictions) {
                try { dpm.addUserRestriction(admin, restriction) } catch (_: Exception) {}
            }

            try {
                Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
            } catch (_: Exception) {}
            
            showHardLockActivity(reason, nextPaymentDate, organizationName)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock: ${e.message}")
        }
    }

    private fun showHardLockActivity(reason: String, nextPaymentDate: String? = null, organizationName: String? = null) {
        Handler(Looper.getMainLooper()).post {
            // FIXED: REMOVED INCORRECT WindowManager FLAGS FROM INTENT
            val lockIntent = Intent(context, HardLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                
                putExtra("lock_reason", reason)
                putExtra("lock_timestamp", getLockTimestamp())
                nextPaymentDate?.let { putExtra("next_payment_date", it) }
                organizationName?.let { putExtra("organization_name", it) }
            }
            try {
                context.startActivity(lockIntent)
                Log.i(TAG, "✅ HardLockActivity triggered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HardLockActivity: ${e.message}")
            }
        }
    }

    fun unlockDevice() {
        Log.i(TAG, "🔓 UNLOCKING DEVICE")
        saveState(LOCK_UNLOCKED, "")
        saveHardLockStateToDeviceProtected("")
        saveLockStateToDatabase(LOCK_UNLOCKED, "Device Unlocked", null)

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                suspendAllOtherPackages(false)
                dpm.setLockTaskPackages(admin, emptyArray())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setStatusBarDisabled(admin, false)
                    dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE)
                }
                
                val restrictions = arrayOf(
                    UserManager.DISALLOW_SAFE_BOOT,
                    UserManager.DISALLOW_DEBUGGING_FEATURES,
                    UserManager.DISALLOW_USB_FILE_TRANSFER
                )
                for (restriction in restrictions) {
                    try { dpm.clearUserRestriction(admin, restriction) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unlocking: ${e.message}")
            }
        }
    }

    private fun saveState(state: String, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("state", state)
            putString("reason", reason)
            if (state != LOCK_UNLOCKED) {
                putLong("lock_timestamp", System.currentTimeMillis())
            }
            apply()
        }
    }

    private fun saveHardLockStateToDeviceProtected(reason: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val devCtx = context.createDeviceProtectedStorageContext()
                val state = if (reason.isEmpty()) LOCK_UNLOCKED else LOCK_HARD
                devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                    putString("state", state)
                    putString("reason", reason)
                    if (state == LOCK_HARD) {
                        putLong("lock_timestamp", System.currentTimeMillis())
                        putBoolean("hard_lock_requested", true)
                    } else {
                        putBoolean("hard_lock_requested", false)
                    }
                    apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving to device-protected storage: ${e.message}")
            }
        }
    }

    private fun saveLockStateToDatabase(state: String, reason: String, tamperType: String?) {
        ioScope.launch {
            try {
                val db = DeviceOwnerDatabase.getDatabase(context)
                if (state == LOCK_HARD) {
                    db.lockStateRecordDao().insert(
                        LockStateRecordEntity(
                            lockState = state,
                            reason = reason,
                            tamperType = tamperType,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    val latest = db.lockStateRecordDao().getLatestUnresolvedHardLock()
                    latest?.let {
                        db.lockStateRecordDao().update(it.copy(resolvedAt = System.currentTimeMillis()))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun suspendAllOtherPackages(suspend: Boolean) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val packages = context.packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { it != context.packageName && it != "com.android.settings" && it != "com.google.android.packageinstaller" }
        
        try {
            val result = dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspend)
            Log.d(TAG, "Package suspension result: ${result.size} packages failed")
        } catch (e: Exception) {
            Log.e(TAG, "Error suspending packages: ${e.message}")
        }
    }

    fun applySoftLock(
        reason: String,
        triggerAction: String? = null,
        nextPaymentDate: String? = null,
        organizationName: String? = null
    ) {
        Log.w(TAG, "🔒 APPLYING SOFT LOCK: $reason (trigger: $triggerAction)")
        
        saveState(LOCK_SOFT, reason)
        saveLockStateToDatabase(LOCK_SOFT, reason, null)
        
        try {
            // Start soft lock overlay service
            val intent = Intent(context, SoftLockOverlayService::class.java).apply {
                putExtra("lock_reason", reason)
                putExtra("trigger_action", triggerAction)
                nextPaymentDate?.let { putExtra("next_payment_date", it) }
                organizationName?.let { putExtra("organization_name", it) }
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

    fun terminateDeviceOwnership() {
        Log.e(TAG, "🔴 TERMINATING DEVICE OWNERSHIP")
        
        try {
            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "Not device owner, cannot terminate")
                return
            }
            
            // Clear all restrictions and unlock
            unlockDevice()
            
            // Clear device owner
            dpm.clearDeviceOwnerApp(context.packageName)
            
            Log.i(TAG, "✅ Device ownership terminated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error terminating device ownership: ${e.message}", e)
        }
    }

    fun checkAndEnforceLockStateFromBoot() {
        val state = getLockStateForBoot()
        if (state == LOCK_HARD) {
            applyHardLock(getLockReasonForBoot(), forceRestart = true)
        }
    }
}
