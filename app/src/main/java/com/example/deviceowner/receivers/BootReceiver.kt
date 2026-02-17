package com.example.deviceowner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.core.SilentDeviceOwnerManager
import com.example.deviceowner.core.frp.manager.FrpManager
import com.example.deviceowner.frp.CompleteFRPManager
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.lock.LockStateRecordEntity
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.security.monitoring.tamper.TamperBootChecker
import com.example.deviceowner.security.mode.CompleteSilentMode
import com.example.deviceowner.security.monitoring.sim.SIMChangeDetector
import com.example.deviceowner.utils.storage.SharedPreferencesManager
import com.example.deviceowner.security.enforcement.adb.AdbBlocker
import com.example.deviceowner.security.enforcement.bootloader.BootloaderLockEnforcer
import com.example.deviceowner.security.enforcement.input.PowerButtonBlocker
import com.example.deviceowner.security.enforcement.monitor.EnhancedSecurityMonitor
import com.example.deviceowner.security.enforcement.policy.EnhancedSecurityManager
import com.example.deviceowner.security.monitoring.boot.BootModeDetector
import com.example.deviceowner.update.scheduler.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Boot Receiver - Enhanced with 7-Layer Security + Persistent Hard Lock
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received boot action: $action")
        
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
                    // Delay slightly to ensure system stability before showing lock screen
                    if (isLockedBootCompleted) delay(2000) else delay(1000)
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

        // ========== 1. TAMPER CHECK FIRST ==========
        if (isRegistered && deviceOwnerManager.isDeviceOwner()) {
            val tamperDetected = TamperBootChecker.runTamperCheck(context)
            if (tamperDetected) {
                Log.e(TAG, "🚨 Tamper detected on boot – device blocked totally (hard lock).")
            }
        }

        // Restore hard lock from device_lock_state if user had powered off while locked (e.g. offline lock)
        if (isRegistered) {
            val lockPrefs = context.getSharedPreferences("device_lock_state", Context.MODE_PRIVATE)
            val deviceLocked = lockPrefs.getBoolean("device_locked", false)
            val offlineLock = lockPrefs.getBoolean("offline_lock", false)
            val savedReason = lockPrefs.getString("lock_reason", null)
            if ((deviceLocked || offlineLock) && savedReason != null && savedReason.isNotBlank()) {
                if (controlManager.getLockStateForBoot() != RemoteDeviceControlManager.LOCK_HARD) {
                    Log.i(TAG, "🔒 Restoring hard lock from device_lock_state (offline/power-off) – reason=$savedReason")
                    controlManager.applyHardLock(savedReason, forceRestart = true)
                }
            }
        }
        controlManager.checkAndEnforceLockStateFromBoot()
        val effectiveLockState = controlManager.getLockStateForBoot()

        // ========== INPUT-BLOCKING SECURITY ONLY WHEN HARD LOCKED ==========
        if (isRegistered && effectiveLockState == RemoteDeviceControlManager.LOCK_HARD) {
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
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing security layers: ${e.message}", e)
            }
        }
        
        // ========== ORIGINAL BOOT INITIALIZATION ==========
        if (isRegistered) {
            val regPrefs = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            val deviceId = regPrefs.getString("device_id", null)

            if (!deviceId.isNullOrBlank()) {
                context.getSharedPreferences("device_data", Context.MODE_PRIVATE).edit()
                    .putString("device_id_for_heartbeat", deviceId).apply()
                prefsManager.setDeviceIdForHeartbeat(deviceId)
                prefsManager.setHeartbeatEnabled(true)
            }
        }
        
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.applyRestrictionsForSetupOnly()
            
            try {
                CompleteSilentMode(context).enableCompleteSilentMode()
            } catch (e: Exception) {
                Log.e(TAG, "Silent mode failed on boot: ${e.message}")
            }

            if (isRegistered) {
                try {
                    val simDetector = SIMChangeDetector(context)
                    simDetector.initialize()
                    simDetector.checkForSIMChange()
                } catch (e: Exception) {
                    Log.e(TAG, "SIM detection failed on boot: ${e.message}")
                }

                try {
                    EnhancedSecurityManager(context)
                        .verifyFactoryResetBlocked()
                } catch (e: Exception) {
                    Log.e(TAG, "Factory reset verification failed on boot: ${e.message}")
                }

                try {
                    CompleteFRPManager(context).verifyFRPStillActive()
                } catch (e: Exception) {
                    Log.e(TAG, "Complete FRP verification failed on boot: ${e.message}")
                }

                try {
                    SilentDeviceOwnerManager(context).verifySilentRestrictionsIntact()
                } catch (e: Exception) {
                    Log.e(TAG, "Silent restrictions verification failed on boot: ${e.message}")
                }
            }
        }

        if (!prefsManager.isDeviceRegistered()) {
            context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE).edit().putBoolean("skip_security_restrictions", true).apply()
            try { com.example.deviceowner.services.lock.SoftLockOverlayService.stopOverlay(context) } catch (_: Exception) { }
        } else {
            if (effectiveLockState == RemoteDeviceControlManager.LOCK_HARD) {
                val lockReason = controlManager.getLockReasonForBoot().ifEmpty { "Device Locked" }
                var lockTimestamp = controlManager.getLockTimestampForBoot()
                if (lockTimestamp <= 0L) lockTimestamp = System.currentTimeMillis()

                try {
                    val db = DeviceOwnerDatabase.getDatabase(context.applicationContext)
                    val unresolved = withContext(Dispatchers.IO) {
                        db.lockStateRecordDao().getLatestUnresolvedHardLock()
                    }
                    if (unresolved != null) {
                        lockTimestamp = unresolved.createdAt
                    }
                } catch (dbEx: Exception) {
                    Log.w(TAG, "DB unavailable on boot: ${dbEx.message}")
                }

                val intent = Intent(context, com.example.deviceowner.ui.activities.lock.HardLockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("lock_reason", lockReason)
                    putExtra("lock_timestamp", lockTimestamp)
                    putExtra("from_boot", true)
                }

                try {
                    withContext(Dispatchers.Main) {
                        context.startActivity(intent)
                    }
                    Log.i(TAG, "✅ Hard lock activity started on boot (reason=$lockReason)")
                    // Second start after 5s so lock screen stays on top
                    scope.launch {
                        delay(5000)
                        withContext(Dispatchers.Main) {
                            context.startActivity(intent)
                        }
                        Log.d(TAG, "✅ Hard lock re-started (5s) – blocking continues")
                    }
                    // Third start after 15s so block continues indefinitely even if system briefly shows other UI
                    scope.launch {
                        delay(15000)
                        withContext(Dispatchers.Main) {
                            if (controlManager.getLockStateForBoot() == RemoteDeviceControlManager.LOCK_HARD) {
                                context.startActivity(intent)
                                Log.d(TAG, "✅ Hard lock re-started (15s) – display continues blocking")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting HardLockActivity on boot: ${e.message}", e)
                }
            }
            
            startAllServicesForRegisteredDevice(context)
        }

        if (deviceOwnerManager.isDeviceOwner()) {
            try {
                UpdateScheduler.schedulePeriodicChecks(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule update checks on boot: ${e.message}")
            }
        }
    }

    private fun startAllServicesForRegisteredDevice(context: Context) {
        val appContext = context.applicationContext
        val deviceId = appContext.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
            .getString("device_id", null)
            ?: appContext.getSharedPreferences("device_data", Context.MODE_PRIVATE)
                .getString("device_id_for_heartbeat", null)
        
        try {
            com.example.deviceowner.monitoring.SecurityMonitorService.startService(appContext)
            if (!deviceId.isNullOrBlank()) {
                com.example.deviceowner.services.remote.RemoteManagementService.startService(appContext, deviceId)
            }
            com.example.deviceowner.services.security.FirmwareSecurityMonitorService.startService(appContext)
            com.example.deviceowner.services.data.LocalDataServerService.startService(appContext)
            
            val lockState = RemoteDeviceControlManager(appContext).getLockStateForBoot()
            if (lockState == RemoteDeviceControlManager.LOCK_SOFT) {
                com.example.deviceowner.services.lock.SoftLockMonitorService.startMonitoring(appContext)
            }
            Log.i(TAG, "✅ All services started automatically on boot")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services on boot: ${e.message}")
        }
    }
}
