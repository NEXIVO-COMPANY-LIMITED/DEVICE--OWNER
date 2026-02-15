package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.core.SilentDeviceOwnerManager
import com.example.deviceowner.core.frp.FrpManager
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.LockStateRecordEntity
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.security.TamperBootChecker
import com.example.deviceowner.security.CompleteSilentMode
import com.example.deviceowner.security.SIMChangeDetector
import com.example.deviceowner.utils.SharedPreferencesManager
import com.example.deviceowner.security.enforcement.*
import com.example.deviceowner.security.monitoring.BootModeDetector
import com.example.deviceowner.update.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Boot Receiver - Enhanced with 7-Layer Security + Persistent Hard Lock
 *
 * Ensures the app applies security restrictions and re-enforces lock state after device reboots.
 * Automatically starts the heartbeat monitoring silently in the background.
 *
 * PERSISTENT HARD LOCK: When device was hard locked and is switched off, on boot we read lock
 * state from device-protected storage (see RemoteDeviceControlManager.saveHardLockStateToDeviceProtected),
 * re-apply kiosk mode and restrictions, and show HardLockActivity immediately so the user sees
 * the lock screen before anything else. Lock survives reboot.
 *
 * Security Layers Initialized:
 * 1. Power Button Blocking
 * 2. Boot Mode Detection
 * 3. ADB Blocking
 * 4. Bootloader Lock Enforcement
 * 5. Custom ROM Detection
 * 6. Continuous Security Monitoring
 * 7. Automatic Hard Lock on Violation
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        val isBootCompleted = action == Intent.ACTION_BOOT_COMPLETED
        val isLockedBootCompleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        } else {
            false
        }
        val isQuickBoot = action == "android.intent.action.QUICKBOOT_POWERON"
        
        if (isBootCompleted || isLockedBootCompleted || isQuickBoot) {
            val workingContext = if (isLockedBootCompleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }

            scope.launch {
                try {
                    initializeOnBoot(workingContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during boot initialization", e)
                }
            }
        }
    }

    private suspend fun initializeOnBoot(context: Context) {
        val deviceOwnerManager = DeviceOwnerManager(context)
        val controlManager = RemoteDeviceControlManager(context)
        val prefsManager = SharedPreferencesManager(context)
        val isRegistered = prefsManager.isDeviceRegistered()

        Log.d(TAG, "📱 Device booted - initializing (registered=$isRegistered)...")

        // ========== 1. TAMPER CHECK FIRST – kama tatizo inablock totally, kisha inaendelea na mambo mengine ==========
        // Device ikiwa inawaka: check kama tamper/security iko poa. Kama ikidetect kuna tatizo → block totally (hard lock).
        // Baada ya muda SecurityMonitorService inacheki tena; ikigundua tatizo inablock totally again.
        if (isRegistered && deviceOwnerManager.isDeviceOwner()) {
            val tamperDetected = TamperBootChecker.runTamperCheck(context)
            if (tamperDetected) {
                Log.e(TAG, "🚨 Tamper detected on boot – device blocked totally (hard lock). Tatizo haijatatuliwa.")
            }
        }

        // ========== INPUT-BLOCKING SECURITY ONLY WHEN HARD LOCKED ==========
        // PowerButtonBlocker/AdbBlocker/Keyguard/LockTask block keyboard and kiosk the device.
        // Run them ONLY when lock state is LOCK_HARD (server or tamper). Never for setup or soft lock.
        // Use getLockStateForBoot() to read from device-protected storage (survives before user unlocks).
        val lockState = controlManager.getLockStateForBoot()
        if (isRegistered && lockState == RemoteDeviceControlManager.LOCK_HARD) {
            try {
                Log.d(TAG, "🔒 Device hard locked – applying full security layers...")
                val powerButtonBlocker = PowerButtonBlocker(context)
                powerButtonBlocker.blockPowerButtonCombinations()
                powerButtonBlocker.enablePowerButtonInterception()
                val adbBlocker = AdbBlocker(context)
                adbBlocker.disableAdbAndUsbDebugging()
                val bootloaderEnforcer = BootloaderLockEnforcer(context)
                bootloaderEnforcer.enforceBootloaderLock()
                val securityMonitor = EnhancedSecurityMonitor(context)
                securityMonitor.startContinuousMonitoring()
                Log.i(TAG, "✅ Full security layers applied (hard lock only)")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing security layers: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "🔓 No hard lock – skipping input-blocking security (keyboard/touch stay enabled)")
        }
        
        // ========== ORIGINAL BOOT INITIALIZATION ==========
        
        // 1. SILENT HEARTBEAT START (when app is hidden, heartbeat continues after reboot)
        if (isRegistered) {
            val regPrefs = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            val deviceId = regPrefs.getString("device_id", null)

            if (!deviceId.isNullOrBlank()) {
                context.getSharedPreferences("device_data", Context.MODE_PRIVATE).edit()
                    .putString("device_id_for_heartbeat", deviceId).apply()
                prefsManager.setDeviceIdForHeartbeat(deviceId)
                prefsManager.setHeartbeatEnabled(true)
                Log.i(TAG, "✅ device_id_for_heartbeat set after boot – 30s heartbeat when SecurityMonitorService starts")
            }
        }
        
        // 2. Always use setup-only restrictions on boot (no keyboard block). Full restrictions only inside applyHardLock.
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.applyRestrictionsForSetupOnly()
            Log.d(TAG, "🔒 Setup-only restrictions on boot (keyboard/touch stay enabled until server hard lock)")

            // Complete Silent Mode: Re-apply on boot to ensure management messages stay hidden
            try {
                CompleteSilentMode(context).enableCompleteSilentMode()
                Log.d(TAG, "Silent mode re-applied on boot")
            } catch (e: Exception) {
                Log.e(TAG, "Silent mode failed on boot: ${e.message}", e)
            }

            // SIM Change Detection: Check on boot if SIM was changed while device was off
            if (isRegistered) {
                try {
                    val simDetector = SIMChangeDetector(context)
                    simDetector.initialize()
                    val result = simDetector.checkForSIMChange()
                    if (result.changed) {
                        Log.w(TAG, "SIM was changed while device was off - overlay shown")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SIM detection failed on boot: ${e.message}", e)
                }

                // Enterprise FRP: Verify policy integrity on boot
                try {
                    val frpManager = FrpManager(context)
                    if (frpManager.getFrpStatus().enabled && !frpManager.verifyFrpPolicy()) {
                        Log.w(TAG, "FRP policy verification failed on boot")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "FRP verification failed on boot: ${e.message}", e)
                }

                // Factory reset: Verify blocked on boot (GAP FIX - periodic verification)
                try {
                    com.example.deviceowner.security.enforcement.EnhancedSecurityManager(context)
                        .verifyFactoryResetBlocked()
                } catch (e: Exception) {
                    Log.e(TAG, "Factory reset verification failed on boot: ${e.message}", e)
                }

                // Silent restrictions: Verify all intact, re-apply missing (no user messages)
                try {
                    SilentDeviceOwnerManager(context).verifySilentRestrictionsIntact()
                } catch (e: Exception) {
                    Log.e(TAG, "Silent restrictions verification failed on boot: ${e.message}", e)
                }
            }
        }

        if (!prefsManager.isDeviceRegistered()) {
            context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE).edit().putBoolean("skip_security_restrictions", true).apply()
            context.getSharedPreferences("device_owner_prefs", Context.MODE_PRIVATE).edit().putBoolean("skip_security_restrictions", true).apply()
            try { com.example.deviceowner.services.SoftLockOverlayService.stopOverlay(context) } catch (_: Exception) { }
        } else {
            // Re-enforce lock state only: LOCK_HARD → applyHardLock (kiosk); LOCK_SOFT → reminder.
            // Use fromBoot variant so we read from device-protected storage (before user unlocks).
            controlManager.checkAndEnforceLockStateFromBoot()
            
            // If device is hard locked, ensure HardLockActivity is visible on boot.
            // Use DB (application context = same DB we wrote to) for last lock time and tatizo haijatatuliwa.
            if (lockState == RemoteDeviceControlManager.LOCK_HARD) {
                try {
                    val appContext = context.applicationContext
                    val db = DeviceOwnerDatabase.getDatabase(appContext)
                    var unresolved = withContext(Dispatchers.IO) {
                        db.lockStateRecordDao().getLatestUnresolvedHardLock()
                    }
                    val lockReason = controlManager.getLockReasonForBoot().ifEmpty { "Device Locked" }
                    var lockTimestamp = unresolved?.createdAt ?: controlManager.getLockTimestampForBoot()
                    // Backfill: if prefs say hard lock but DB has no record (e.g. first boot after upgrade), insert so "last state" is never lost
                    if (unresolved == null && lockTimestamp > 0L) {
                        withContext(Dispatchers.IO) {
                            db.lockStateRecordDao().insert(
                                LockStateRecordEntity(
                                    lockState = RemoteDeviceControlManager.LOCK_HARD,
                                    reason = lockReason,
                                    tamperType = null,
                                    createdAt = lockTimestamp,
                                    resolvedAt = null
                                )
                            )
                            Log.d(TAG, "Backfilled lock state in DB (createdAt=$lockTimestamp) – tatizo haijatatuliwa, keeping hard lock")
                        }
                        unresolved = withContext(Dispatchers.IO) { db.lockStateRecordDao().getLatestUnresolvedHardLock() }
                    }
                    if (unresolved != null) {
                        Log.d(TAG, "Last lock from DB: hard_lock at ${unresolved.createdAt}, tamperType=${unresolved.tamperType}, tatizo haijatatuliwa – keeping hard lock")
                    }
                    if (lockTimestamp <= 0L) lockTimestamp = System.currentTimeMillis()
                    val intent = Intent(context, com.example.deviceowner.ui.activities.lock.HardLockActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                                Intent.FLAG_ACTIVITY_NO_HISTORY
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            addFlags(0x00000400 or 0x00000200)  // FLAG_ACTIVITY_SHOW_WHEN_LOCKED | FLAG_ACTIVITY_TURN_SCREEN_ON
                        }
                        putExtra("lock_reason", lockReason)
                        putExtra("lock_timestamp", if (lockTimestamp > 0L) lockTimestamp else System.currentTimeMillis())
                        putExtra("from_boot", true)
                    }
                    // CRITICAL: startActivity MUST run on main thread; show-when-locked ensures screen appears over keyguard
                    withContext(Dispatchers.Main) {
                        context.applicationContext.startActivity(intent)
                    }
                    Log.i(TAG, "✅ Hard lock activity shown on boot (reason=$lockReason)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing hard lock on boot: ${e.message}", e)
                }
            }
            
            // Start ALL services automatically at the same time for registered devices
            startAllServicesForRegisteredDevice(context)
        }

        // Auto-update: reschedule periodic checks when Device Owner
        if (deviceOwnerManager.isDeviceOwner()) {
            try {
                UpdateScheduler.schedulePeriodicChecks(context)
                Log.d(TAG, "Update checks rescheduled after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule update checks on boot: ${e.message}", e)
            }
        }
    }

    /**
     * Start all monitoring/management services at the same time for registered devices.
     * Called when device powers on after being switched off – ensures services run without opening the app.
     */
    private fun startAllServicesForRegisteredDevice(context: Context) {
        val appContext = context.applicationContext
        val deviceId = appContext.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            .getString("device_id", null)
            ?: appContext.getSharedPreferences("device_data", Context.MODE_PRIVATE)
                .getString("device_id_for_heartbeat", null)
        if (deviceId.isNullOrBlank()) {
            Log.w(TAG, "No device_id – skipping RemoteManagementService")
        }
        try {
            // 1. Security Monitor (heartbeat, tamper, developer mode check)
            com.example.deviceowner.monitoring.SecurityMonitorService.startService(appContext)
            Log.d(TAG, "✅ SecurityMonitorService started")
            // 2. Remote Management (polls for lock/unlock commands from server)
            if (!deviceId.isNullOrBlank()) {
                com.example.deviceowner.services.RemoteManagementService.startService(appContext, deviceId)
                Log.d(TAG, "✅ RemoteManagementService started")
            }
            // 3. Firmware Security Monitor (bootloader, recovery, EDL detection)
            com.example.deviceowner.services.FirmwareSecurityMonitorService.startService(appContext)
            Log.d(TAG, "✅ FirmwareSecurityMonitorService started")
            // 4. Local Data Server (serves device data via HTTP)
            com.example.deviceowner.services.LocalDataServerService.startService(appContext)
            Log.d(TAG, "✅ LocalDataServerService started")
            // 5. Soft Lock Monitor (when device is in soft lock – monitors for bypass attempts)
            val lockState = RemoteDeviceControlManager(appContext).getLockStateForBoot()
            if (lockState == RemoteDeviceControlManager.LOCK_SOFT) {
                com.example.deviceowner.services.SoftLockMonitorService.startMonitoring(appContext)
                Log.d(TAG, "✅ SoftLockMonitorService started (soft lock active)")
            }
            Log.i(TAG, "✅ All services started automatically on boot (registered device)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services on boot: ${e.message}", e)
        }
    }
}
