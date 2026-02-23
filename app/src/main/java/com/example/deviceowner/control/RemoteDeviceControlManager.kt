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
import com.example.deviceowner.services.lock.SoftLockOverlayService
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.lock.LockStateRecordEntity
import com.example.deviceowner.ui.activities.lock.payment.PaymentOverdueActivity
import com.example.deviceowner.ui.activities.lock.payment.SoftLockReminderActivity
import com.example.deviceowner.ui.activities.lock.security.SecurityViolationActivity
import com.example.deviceowner.ui.activities.lock.system.DeactivationActivity
import com.example.deviceowner.ui.activities.lock.system.HardLockGenericActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remote lock control: manages specialized lock activities and enforcement.
 * v5.0 - Enhanced Deactivation and Kiosk Mode Cleanup.
 */
class RemoteDeviceControlManager(private val context: Context) {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "RemoteControl"
        private const val PREFS = "control_prefs"
        const val LOCK_UNLOCKED = "unlocked"
        const val LOCK_SOFT = "soft_lock"
        const val LOCK_HARD = "hard_lock"
        
        const val TYPE_OVERDUE = "OVERDUE"
        const val TYPE_TAMPER = "TAMPER"
        const val TYPE_DEACTIVATION = "DEACTIVATION"
        
        private val ALLOWED_PACKAGES = arrayOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )
    }
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, AdminReceiver::class.java)
    
    fun getLockState(): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("state", LOCK_UNLOCKED) ?: LOCK_UNLOCKED

    fun getLockType(): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("lock_type", "") ?: ""

    fun isHardLocked(): Boolean = getLockState() == LOCK_HARD
    fun isSoftLocked(): Boolean = getLockState() == LOCK_SOFT
    fun isLocked(): Boolean = getLockState() != LOCK_UNLOCKED

    fun getLockStateForBoot(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val devCtx = context.createDeviceProtectedStorageContext()
                return devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("state", LOCK_UNLOCKED) ?: LOCK_UNLOCKED
            } catch (_: Exception) { }
        }
        return getLockState()
    }

    fun getLockReasonForBoot(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val devCtx = context.createDeviceProtectedStorageContext()
                return devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("reason", "") ?: ""
            } catch (_: Exception) { }
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("reason", "") ?: ""
    }

    fun getLockTypeForBoot(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val devCtx = context.createDeviceProtectedStorageContext()
                return devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("lock_type", "") ?: ""
            } catch (_: Exception) { }
        }
        return getLockType()
    }

    fun getLockTimestamp(): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lock_timestamp", 0L)

    fun applyHardLock(
        reason: String,
        lockType: String = TYPE_TAMPER,
        forceRestart: Boolean = false,
        forceFromServerOrMismatch: Boolean = false,
        tamperType: String? = null,
        nextPaymentDate: String? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Log.e(TAG, "🔒 APPLYING HARD LOCK ($lockType): $reason")
        
        saveState(LOCK_HARD, reason, lockType)
        saveHardLockStateToDeviceProtected(reason, lockType)
        saveLockStateToDatabase(LOCK_HARD, reason, tamperType ?: if (lockType == TYPE_TAMPER) "SYSTEM_MODIFIED" else null)

        showLockActivity(reason, lockType, nextPaymentDate)

        ioScope.launch {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    val lockTaskPackages = arrayOf(context.packageName) + ALLOWED_PACKAGES
                    dpm.setLockTaskPackages(admin, lockTaskPackages)
                    suspendAllOtherPackages(true)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        dpm.setStatusBarDisabled(admin, true)
                        dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL)
                    }
                    applyRestrictions(reason)
                } catch (e: Exception) {
                    Log.e(TAG, "DPM apply error: ${e.message}")
                }
            }
        }
    }

    fun unlockDevice() {
        Log.i(TAG, "🔓 UNLOCKING DEVICE")
        saveState(LOCK_UNLOCKED, "", "")
        saveHardLockStateToDeviceProtected("", "")
        saveLockStateToDatabase(LOCK_UNLOCKED, "Device Unlocked", null)

        ioScope.launch {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                clearAllPoliciesAndRestrictions()
            }
        }
    }

    /**
     * Critical cleanup for deactivation. Must be called while still Device Owner.
     */
    suspend fun clearAllPoliciesAndRestrictions() = withContext(Dispatchers.IO) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return@withContext
        
        Log.w(TAG, "🛠 CLEANUP: Removing all policies and restrictions")
        try {
            // 1. Unsuspend all packages (Most important!)
            suspendAllOtherPackages(false)
            
            // 2. Clear LockTask settings
            dpm.setLockTaskPackages(admin, emptyArray())
            
            // 3. Reset UI features
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(admin, false)
                dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE)
            }
            
            // 4. Clear all user restrictions
            val restrictions = arrayOf(
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
                UserManager.DISALLOW_USB_FILE_TRANSFER,
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_REMOVE_USER,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
                UserManager.DISALLOW_OUTGOING_CALLS,
                UserManager.DISALLOW_SMS,
                UserManager.DISALLOW_INSTALL_APPS
            )
            for (restriction in restrictions) {
                try { dpm.clearUserRestriction(admin, restriction) } catch (_: Exception) {}
            }
            
            Log.i(TAG, "✅ Cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed: ${e.message}")
        }
    }

    private fun applyRestrictions(reason: String) {
        val restrictions = mutableListOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
        )
        
        if (!reason.contains("Developer", true) && !reason.contains("ADB", true)) {
            restrictions.add(UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
        
        for (restriction in restrictions) {
            try { dpm.addUserRestriction(admin, restriction) } catch (_: Exception) {}
        }
    }

    fun showLockActivity(reason: String, lockType: String, nextPaymentDate: String?) {
        Handler(Looper.getMainLooper()).post {
            val activityClass = when (lockType) {
                TYPE_OVERDUE -> PaymentOverdueActivity::class.java
                TYPE_DEACTIVATION -> DeactivationActivity::class.java
                TYPE_TAMPER -> SecurityViolationActivity::class.java
                else -> HardLockGenericActivity::class.java
            }
            
            val intent = Intent(context, activityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("lock_reason", reason)
                putExtra("lock_type", lockType)
                putExtra("lock_timestamp", getLockTimestamp())
                nextPaymentDate?.let { putExtra("next_payment_date", it) }
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    fun applySoftLock(reason: String, nextPaymentDate: String? = null) {
        saveState(LOCK_SOFT, reason, "REMINDER")
        Handler(Looper.getMainLooper()).post {
            val intent = Intent(context, SoftLockReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("next_payment_date", nextPaymentDate)
                putExtra("lock_reason", reason)
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    private fun saveState(state: String, reason: String, lockType: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("state", state)
            putString("reason", reason)
            putString("lock_type", lockType)
            if (state != LOCK_UNLOCKED) putLong("lock_timestamp", System.currentTimeMillis())
            apply()
        }
    }

    private fun saveHardLockStateToDeviceProtected(reason: String, lockType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val devCtx = context.createDeviceProtectedStorageContext()
                val state = if (reason.isEmpty()) LOCK_UNLOCKED else LOCK_HARD
                devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                    putString("state", state)
                    putString("reason", reason)
                    putString("lock_type", lockType)
                    apply()
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveLockStateToDatabase(state: String, reason: String, tamperType: String?) {
        ioScope.launch {
            try {
                val db = DeviceOwnerDatabase.getDatabase(context)
                if (state == LOCK_HARD) {
                    db.lockStateRecordDao().insert(LockStateRecordEntity(lockState = state, reason = reason, tamperType = tamperType, createdAt = System.currentTimeMillis()))
                } else {
                    val latest = db.lockStateRecordDao().getLatestUnresolvedHardLock()
                    latest?.let { db.lockStateRecordDao().update(it.copy(resolvedAt = System.currentTimeMillis())) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun suspendAllOtherPackages(suspend: Boolean) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val packages = context.packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { pkg -> pkg != context.packageName && !ALLOWED_PACKAGES.contains(pkg) }
        try {
            dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspend)
        } catch (_: Exception) {}
    }

    fun checkAndEnforceLockStateFromBoot() {
        val state = getLockStateForBoot()
        if (state == LOCK_HARD) {
            applyHardLock(reason = getLockReasonForBoot(), lockType = getLockTypeForBoot())
        }
    }
}
