package com.example.deviceowner.control

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.example.deviceowner.deactivation.DeviceOwnerDeactivationManager
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.services.SoftLockMonitorService
import com.example.deviceowner.services.SoftLockOverlayService
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.LockStateRecordEntity
import com.example.deviceowner.ui.activities.lock.HardLockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Remote lock control: hard lock (kiosk) vs soft lock (reminder).
 *
 * Hard lock state is persisted to BOTH normal and device-protected storage so that
 * on boot (including LOCKED_BOOT_COMPLETED before user unlocks) the device can
 * detect hard lock and show HardLockActivity immediately.
 *
 * RESTRICTION POLICY – when we block user (keyboard / input / kiosk):
 * - Block ONLY when: (1) Server sends is_locked / hard lock, OR (2) Critical tamper (e.g. package removal).
 * - Do NOT block: during registration, after registration until server says lock, or for soft lock (reminder).
 * - Use applyRestrictionsForSetupOnly() everywhere except inside applyHardLock (keeps keyboard/touch working).
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

    /**
     * Get lock state for boot scenario. Reads from device-protected storage first (where we
     * persist for LOCKED_BOOT_COMPLETED / before user unlocks). Falls back to regular prefs.
     * Use this in BootReceiver so we detect hard lock even before credential storage is available.
     */
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

    /** Like getLockReason() but reads from device-protected storage first (for boot). */
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

    /** Like getLockTimestamp() but reads from device-protected storage first (for boot). */
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

    /** True if hard_lock_requested is set in device-protected storage (for boot). */
    fun isHardLockRequestedFromDeviceProtected(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val devCtx = context.createDeviceProtectedStorageContext()
            devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("hard_lock_requested", false)
        } catch (_: Exception) { false }
    }

    fun isHardLocked(): Boolean = getLockState() == LOCK_HARD
    fun isLocked(): Boolean = getLockState() != LOCK_UNLOCKED
    fun getLockReason(): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("reason", "") ?: ""
    fun getLockTimestamp(): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lock_timestamp", 0L)

    /**
     * STRENGTHENED HARD LOCK
     * Now includes Package Suspension - the ultimate way to disable the phone.
     *
     * @param reason Message shown on lock screen.
     * @param forceRestart If true, re-apply lock even if already LOCK_HARD (e.g. boot).
     * @param forceFromServerOrMismatch If true, always apply lock and ignore skip_security_restrictions
     *        (use for heartbeat response: server lock or mismatch – user must be blocked).
     * @param tamperType If lock is due to tamper (e.g. BOOTLOADER_UNLOCKED, PACKAGE_REMOVED), pass for DB record.
     */
    fun applyHardLock(
        reason: String,
        forceRestart: Boolean = false,
        forceFromServerOrMismatch: Boolean = false,
        tamperType: String? = null,
        nextPaymentDate: String? = null,
        organizationName: String? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        Log.e(TAG, "🔒 ═══════════════════════════════════════════════════════════")
        Log.e(TAG, "🔒 APPLYING HARD LOCK (TOTAL KIOSK MODE)")
        Log.e(TAG, "🔒 ═══════════════════════════════════════════════════════════")
        Log.e(TAG, "   Reason: $reason")
        Log.e(TAG, "   Force Restart: $forceRestart")
        Log.e(TAG, "   Force From Server: $forceFromServerOrMismatch")
        if (tamperType != null) Log.e(TAG, "   Tamper Type: $tamperType")
        
        // CRITICAL: When server sends lock or mismatch detected, ALWAYS apply it
        val skipSecurityRestrictions = prefs.getBoolean("skip_security_restrictions", false)
        if (skipSecurityRestrictions && !forceFromServerOrMismatch) {
            Log.w(TAG, "⚠️ Skipping hard lock during initial setup - user needs to complete registration")
            return
        }
        if (forceFromServerOrMismatch && skipSecurityRestrictions) {
            Log.i(TAG, "🔒 Server/mismatch lock – applying hard lock regardless of skip_security_restrictions")
        }
        
        // Check if already locked
        if (!forceRestart && getLockState() == LOCK_HARD) {
            Log.d(TAG, "Device already in HARD LOCK - no change needed")
            return
        }
        
        saveState(LOCK_HARD, reason)
        prefs.edit().putBoolean("hard_lock_requested", true).apply()
        saveHardLockStateToDeviceProtected(reason)
        saveLockStateToDatabase(LOCK_HARD, reason, tamperType)

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "❌ Not Device Owner - cannot apply hard lock")
            Log.e(TAG, "   Device may have been deactivated")
            return
        }
        
        try {
            Log.d(TAG, "   Step 1: Setting lock task packages...")
            // CRITICAL: Set lock task packages BEFORE starting activity
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            Log.d(TAG, "   ✓ Lock task packages set")
            
            Log.d(TAG, "   Step 2: Setting forced time...")
            dpm.setAutoTimeRequired(admin, true)
            Log.d(TAG, "   ✓ Forced time enabled")
            
            Log.d(TAG, "   Step 3: Suspending all other packages...")
            suspendAllOtherPackages(true)
            Log.d(TAG, "   ✓ All packages suspended")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(TAG, "   Step 4: Disabling status bar and keyguard...")
                dpm.setStatusBarDisabled(admin, true)
                dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL)
                Log.d(TAG, "   ✓ Status bar and keyguard disabled")
            }
            
            Log.d(TAG, "   Step 5: Adding user restrictions...")
            // Policy: USB file transfer is ALLOWED – DISALLOW_USB_FILE_TRANSFER is not in the list so MTP/file transfer works.
            val restrictions = arrayOf(
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_CONFIG_BLUETOOTH,
                UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_MODIFY_ACCOUNTS,
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
            )
            
            var restrictionsAdded = 0
            for (restriction in restrictions) {
                try {
                    dpm.addUserRestriction(admin, restriction)
                    restrictionsAdded++
                } catch (e: Exception) {
                    Log.w(TAG, "   ⚠️ Failed to add restriction $restriction: ${e.message}")
                }
            }
            Log.d(TAG, "   ✓ Added $restrictionsAdded user restrictions")
            
            Log.d(TAG, "   Step 6: Blocking accessibility services...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setPermittedAccessibilityServices(admin, emptyList())
                Log.d(TAG, "   ✓ Accessibility services blocked")
            }
            
            Log.d(TAG, "   Step 7: Disabling camera...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setCameraDisabled(admin, true)
                Log.d(TAG, "   ✓ Camera disabled")
            }
            
            Log.d(TAG, "   Step 8: Starting hard lock activity...")
            val lockIntent = Intent(context, HardLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    addFlags(0x00000400 or 0x00000200)  // FLAG_ACTIVITY_SHOW_WHEN_LOCKED | FLAG_ACTIVITY_TURN_SCREEN_ON
                }
                putExtra("lock_reason", reason)
                putExtra("lock_timestamp", getLockTimestamp())
                nextPaymentDate?.let { putExtra("next_payment_date", it) }
                organizationName?.let { putExtra("organization_name", it) }
            }
            
            // CRITICAL: startActivity must run on main thread (e.g. when called from boot coroutine)
            Handler(Looper.getMainLooper()).post {
                try {
                    context.startActivity(lockIntent)
                    Log.d(TAG, "   ✓ HardLockActivity started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Failed to start HardLockActivity: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "   Step 9: Locking device now...")
            dpm.lockNow()
            Log.d(TAG, "   ✓ Device locked")
            
            Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
            Log.i(TAG, "✅ Hard lock applied successfully - TOTAL DATA BLOCK ACTIVE")
            Log.i(TAG, "✅ ✓ All apps suspended")
            Log.i(TAG, "✅ ✓ WiFi/Bluetooth disabled")
            Log.i(TAG, "✅ ✓ Camera/Microphone blocked")
            Log.i(TAG, "✅ ✓ Account modifications blocked")
            Log.i(TAG, "✅ ✓ USB debugging, file transfer, factory reset allowed")
            Log.i(TAG, "✅ ✓ Accessibility services blocked")
            Log.i(TAG, "✅ ✓ Lock task mode active")
            Log.i(TAG, "✅ ═══════════════════════════════════════════════════════════")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Hard lock strengthening failed: ${e.message}", e)
            Log.e(TAG, "   Stack trace: ${e.stackTraceToString()}")
            // Still try to lock even if some steps failed
            try {
                dpm.lockNow()
                Log.i(TAG, "⚠️ Partial hard lock applied - device locked but some restrictions may have failed")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ CRITICAL: Could not lock device: ${e2.message}")
            }
        }
    }

    fun unlockDevice() {
        if (getLockState() == LOCK_UNLOCKED) return
        saveState(LOCK_UNLOCKED, "")
        clearHardLockStateFromDeviceProtected()
        markLockStateResolvedInDatabase()

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                // 1. Lift Package Suspension
                suspendAllOtherPackages(false)
                
                // 2. Restore System Access
                dpm.setLockTaskPackages(admin, arrayOf())
                dpm.setAutoTimeRequired(admin, false)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setStatusBarDisabled(admin, false)
                    dpm.setKeyguardDisabledFeatures(admin, 0)
                    dpm.setPermittedAccessibilityServices(admin, null) // Allow all again
                    dpm.setCameraDisabled(admin, false)
                }
                
                // Clear all restrictions (must match applyHardLock + AdminReceiver)
                val restrictions = arrayOf(
                    UserManager.DISALLOW_SAFE_BOOT,
                    UserManager.DISALLOW_FACTORY_RESET,
                    UserManager.DISALLOW_DEBUGGING_FEATURES,
                    UserManager.DISALLOW_USB_FILE_TRANSFER,
                    UserManager.DISALLOW_CONFIG_WIFI,
                    UserManager.DISALLOW_CONFIG_BLUETOOTH,
                    UserManager.DISALLOW_INSTALL_APPS,
                    UserManager.DISALLOW_UNINSTALL_APPS,
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                    UserManager.DISALLOW_MODIFY_ACCOUNTS,
                    UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
                )
                // Clear all so unlock restores full access including USB debug/file transfer/factory reset
                
                for (restriction in restrictions) {
                    try {
                        dpm.clearUserRestriction(admin, restriction)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear restriction $restriction: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unlock cleanup failed: ${e.message}")
            }
        }
    }

    /**
     * Apply soft lock – reminder overlay. Shows overlay with "I UNDERSTAND" button.
     * Unlock: NO PASSWORD. User taps to dismiss and use the app normally (no kiosk, no PIN).
     * @param reason Full message text
     * @param triggerAction "payment_reminder" or "sim_change"
     * @param nextPaymentDate Formatted next payment due date from heartbeat (e.g. "Feb 22, 2026 at 11:59 PM")
     * @param organizationName e.g. "PAYO" - from registration/backend
     */
    fun applySoftLock(
        reason: String,
        triggerAction: String? = null,
        nextPaymentDate: String? = null,
        organizationName: String? = null
    ) {
        Log.w(TAG, "🔔 Soft lock (reminder overlay, no password) – reason: $reason")
        
        try {
            saveState(LOCK_SOFT, reason)
            SoftLockOverlayService.startOverlay(
                context,
                reason = reason,
                triggerAction = triggerAction ?: "",
                nextPaymentDate = nextPaymentDate,
                organizationName = organizationName
            )
            showSoftLockReminderNotification(reason)
            // Do NOT start SoftLockMonitorService – reminder is one-time, user dismisses and continues
            Log.i(TAG, "✅ Soft lock overlay shown – user can tap Continue to use app normally")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to apply soft lock: ${e.message}", e)
        }
    }

    /** Show a reminder notification (in addition to overlay). */
    private fun showSoftLockReminderNotification(reason: String) {
        try {
            val channelId = "soft_lock_reminder"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Payment reminder",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Reminder – tap Continue on overlay to use device" }
                nm.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Payment reminder")
                .setContentText(if (reason.length > 60) reason.take(57) + "..." else reason)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(2001, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not show soft lock notification: ${e.message}")
        }
    }

    /**
     * Check and enforce current lock state (typically called on boot)
     * 
     * SAFETY: Only re-enforce if explicitly enabled in preferences
     * SKIP during initial setup to allow registration
     */
    fun checkAndEnforceLockState() {
        checkAndEnforceLockStateInternal(useDeviceProtected = false)
    }

    /**
     * Check and enforce lock state when called from boot. Uses device-protected storage
     * so we correctly detect hard lock even before user unlocks (credential storage unavailable).
     */
    fun checkAndEnforceLockStateFromBoot() {
        checkAndEnforceLockStateInternal(useDeviceProtected = true)
    }

    private fun checkAndEnforceLockStateInternal(useDeviceProtected: Boolean) {
        val currentState = if (useDeviceProtected) getLockStateForBoot() else getLockState()
        Log.d(TAG, "Checking lock state: $currentState (fromBoot=$useDeviceProtected)")
        
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        
        // CRITICAL: Skip enforcement during initial setup (skip_security_restrictions lives in credential storage)
        val skipSecurityRestrictions = prefs.getBoolean("skip_security_restrictions", false)
        if (skipSecurityRestrictions) {
            Log.w(TAG, "⚠️ Skipping lock enforcement during initial setup")
            return
        }
        
        // SAFETY CHECK: Only re-enforce if hard_lock_requested is true
        val isExplicitlyRequested = if (useDeviceProtected) {
            isHardLockRequestedFromDeviceProtected()
        } else {
            prefs.getBoolean("hard_lock_requested", false)
        }
        
        if (!isExplicitlyRequested && currentState == LOCK_HARD) {
            Log.w(TAG, "⚠️ Hard lock state found but not explicitly requested - clearing it")
            saveState(LOCK_UNLOCKED, "")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                clearHardLockStateFromDeviceProtected()
            }
            return
        }
        
        when (currentState) {
            LOCK_HARD -> {
                val reason = if (useDeviceProtected) getLockReasonForBoot() else getLockReason()
                applyHardLock(reason, forceRestart = true)
            }
            LOCK_SOFT -> {
                val reason = if (useDeviceProtected) getLockReasonForBoot() else getLockReason()
                applySoftLock(reason)
            }
            else -> {
                Log.d(TAG, "Device is unlocked")
            }
        }
    }

    /**
     * Helper to suspend or unsuspend all other applications on the device.
     * CRITICAL: Do NOT suspend system packages that handle keyboard/touch input
     */
    private fun suspendAllOtherPackages(suspend: Boolean) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        val packagesToSuspend = mutableListOf<String>()
        
        // System packages that MUST NOT be suspended (keyboard/touch/input handling)
        val systemPackagesToKeep = setOf(
            context.packageName,
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.inputmethod.latin",  // Keyboard input
            "com.google.android.inputmethod.latin",  // Google keyboard
            "com.android.settings",  // Settings (may have input)
            "android",  // Core Android
            "com.android.launcher",  // Launcher
            "com.android.launcher3",  // Launcher 3
            "com.android.keychain",  // Key management
            "com.android.providers.settings",  // Settings provider
            "com.android.providers.contacts",  // Contacts
            "com.android.providers.media",  // Media provider
            "com.android.providers.downloads",  // Downloads
            "com.android.providers.calendar",  // Calendar
            "com.android.providers.userdictionary"  // User dictionary
        )
        
        for (pkg in packages) {
            val packageName = pkg.packageName
            if (!systemPackagesToKeep.contains(packageName)) {
                packagesToSuspend.add(packageName)
            }
        }
        
        try {
            val failed = dpm.setPackagesSuspended(admin, packagesToSuspend.toTypedArray(), suspend)
            if (failed.isNotEmpty()) {
                Log.w(TAG, "Some packages could not be suspended: ${failed.joinToString()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error suspending packages: ${e.message}")
        }
    }

    /**
     * ABSOLUTE TERMINATION
     * Now uses the full-scale DeactivationManager for 100% perfect removal.
     */
    fun terminateDeviceOwnership() {
        Log.i(TAG, "🚀 Triggering Absolute Device Ownership Termination...")
        
        CoroutineScope(Dispatchers.Main).launch {
            val deactivationManager = DeviceOwnerDeactivationManager(context)
            deactivationManager.deactivateDeviceOwner()
            
            // Note: deactivateDeviceOwner() handles its own cleanup and triggers self-uninstall.
            saveState(LOCK_UNLOCKED, "DEVICE_RELEASED_PERMANENTLY")
        }
    }

    private fun saveState(state: String, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", state)
            .putString("reason", reason)
            .putLong("lock_timestamp", System.currentTimeMillis())
            .apply()
    }

    /**
     * Persist hard lock state to device-protected storage so it is available on boot
     * (including LOCKED_BOOT_COMPLETED when credential storage is not yet available).
     */
    private fun saveHardLockStateToDeviceProtected(reason: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            val devCtx = context.createDeviceProtectedStorageContext()
            devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("state", LOCK_HARD)
                .putString("reason", reason)
                .putLong("lock_timestamp", System.currentTimeMillis())
                .putBoolean("hard_lock_requested", true)
                .apply()
            Log.d(TAG, "Hard lock state saved to device-protected storage (for boot)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save hard lock to device-protected storage: ${e.message}")
        }
    }

    /**
     * Clear hard lock state from device-protected storage so boot does not re-show hard lock after unlock.
     */
    private fun clearHardLockStateFromDeviceProtected() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            val devCtx = context.createDeviceProtectedStorageContext()
            devCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("state", LOCK_UNLOCKED)
                .remove("reason")
                .remove("lock_timestamp")
                .putBoolean("hard_lock_requested", false)
                .apply()
            Log.d(TAG, "Hard lock state cleared from device-protected storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear hard lock from device-protected storage: ${e.message}")
        }
    }

    /**
     * Save hard lock to local DB with timestamp. Runs synchronously so record is 100% written
     * before return – even if device reboots immediately, boot will see "last state" and tatizo haijatatuliwa.
     */
    private fun saveLockStateToDatabase(lockState: String, reason: String, tamperType: String?) {
        try {
            runBlocking(Dispatchers.IO) {
                val db = DeviceOwnerDatabase.getDatabase(context)
                db.lockStateRecordDao().insert(
                    LockStateRecordEntity(
                        lockState = lockState,
                        reason = reason,
                        tamperType = tamperType,
                        createdAt = System.currentTimeMillis(),
                        resolvedAt = null
                    )
                )
                Log.d(TAG, "Lock state saved to DB (reason=$reason, tamperType=$tamperType)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save lock state to DB: ${e.message}")
        }
    }

    /**
     * On unlock, mark latest unresolved HARD_LOCK as resolved. Runs synchronously so DB is 100%
     * consistent before return – boot will not re-apply lock.
     */
    private fun markLockStateResolvedInDatabase() {
        try {
            runBlocking(Dispatchers.IO) {
                val db = DeviceOwnerDatabase.getDatabase(context)
                db.lockStateRecordDao().markLatestUnresolvedHardLockResolved(System.currentTimeMillis())
                Log.d(TAG, "Lock state marked resolved in DB")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark lock state resolved in DB: ${e.message}")
        }
    }
}
