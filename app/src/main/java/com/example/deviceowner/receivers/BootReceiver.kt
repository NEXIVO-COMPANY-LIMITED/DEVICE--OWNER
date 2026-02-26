package com.microspace.payo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.microspace.payo.control.RemoteDeviceControlManager
import com.microspace.payo.core.SilentDeviceOwnerManager
import com.microspace.payo.data.DeviceIdProvider
import com.microspace.payo.device.DeviceOwnerManager
import com.microspace.payo.security.mode.CompleteSilentMode

import com.microspace.payo.security.monitoring.tamper.TamperBootChecker
import com.microspace.payo.services.heartbeat.HeartbeatService
import com.microspace.payo.services.heartbeat.HeartbeatWorker
import com.microspace.payo.utils.storage.SharedPreferencesManager
import com.microspace.payo.security.enforcement.adb.AdbBlocker
import com.microspace.payo.security.enforcement.bootloader.BootloaderLockEnforcer
import com.microspace.payo.security.enforcement.input.PowerButtonBlocker
import com.microspace.payo.security.enforcement.monitor.EnhancedSecurityMonitor
import com.microspace.payo.security.enforcement.policy.EnhancedSecurityManager
import com.microspace.payo.update.scheduler.UpdateScheduler
import com.microspace.payo.ui.activities.lock.payment.PaymentOverdueActivity
import com.microspace.payo.ui.activities.lock.security.SecurityViolationActivity
import com.microspace.payo.ui.activities.lock.system.DeactivationActivity
import com.microspace.payo.ui.activities.lock.system.HardLockGenericActivity
import kotlinx.coroutines.*

/**
 * Boot Receiver - Optimized for Direct Boot and Persistent Hard Lock.
 * Ensures the device remains locked and services start even before first unlock.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "📱 Received boot action: $action")
        
        // We handle both normal boot and Direct Boot (before first unlock)
        val isBootAction = action == Intent.ACTION_BOOT_COMPLETED || 
                          action == Intent.ACTION_LOCKED_BOOT_COMPLETED || 
                          action == "android.intent.action.QUICKBOOT_POWERON"
        
        if (isBootAction) {
            // Important: Use Device Protected Storage context for Direct Boot compatibility
            val workingContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else context

            scope.launch {
                try {
                    // Start services immediately on boot
                    initializeOnBoot(workingContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during boot initialization", e)
                }
            }
        }
    }

    private suspend fun initializeOnBoot(context: Context) {
        val controlManager = RemoteDeviceControlManager(context)
        val prefsManager = SharedPreferencesManager(context)
        val deviceOwnerManager = DeviceOwnerManager(context)
        
        // Use the new Direct-Boot aware registration check
        val isRegistered = prefsManager.isDeviceRegistered()
        Log.d(TAG, "Registration status on boot: $isRegistered")

        if (isRegistered) {
            // 1. Enforce Lock State First
            controlManager.checkAndEnforceLockStateFromBoot()
            val effectiveLockState = controlManager.getLockStateForBoot()

            if (effectiveLockState == RemoteDeviceControlManager.LOCK_HARD) {
                Log.e(TAG, "🚨 Device is HARD LOCKED - Re-displaying Lock Screen...")
                launchCorrectLockActivity(context, controlManager)
                
                // Re-apply security layers that might have been reset
                try {
                    PowerButtonBlocker(context).apply {
                        blockPowerButtonCombinations()
                        enablePowerButtonInterception()
                    }
                    AdbBlocker(context).disableAdbAndUsbDebugging()
                    BootloaderLockEnforcer(context).enforceBootloaderLock()
                    EnhancedSecurityMonitor(context).startContinuousMonitoring()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply security layers: ${e.message}")
                }
            }

            // 2. Start Services (Heartbeat, Remote Management, etc.)
            startAllServicesForRegisteredDevice(context)

            // 3. Security checks (only if Device Owner)
            if (deviceOwnerManager.isDeviceOwner()) {
                try {
                    TamperBootChecker.runTamperCheck(context)
                    CompleteSilentMode(context).enableCompleteSilentMode()
                    EnhancedSecurityManager(context).verifyFactoryResetBlocked()
                    SilentDeviceOwnerManager(context).verifySilentRestrictionsIntact()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in security initialization: ${e.message}")
                }
            }

            // 4. Schedule updates
            try {
                UpdateScheduler.schedulePeriodicChecks(context)
            } catch (_: Exception) { }
        }
    }

    private fun launchCorrectLockActivity(context: Context, controlManager: RemoteDeviceControlManager) {
        val lockReason = controlManager.getLockReasonForBoot().ifEmpty { "Device Locked" }
        val lockType = controlManager.getLockTypeForBoot()
        val lockTimestamp = controlManager.getLockTimestamp().let { 
            if (it <= 0L) System.currentTimeMillis() else it 
        }

        val activityClass = when (lockType) {
            RemoteDeviceControlManager.TYPE_OVERDUE -> PaymentOverdueActivity::class.java
            RemoteDeviceControlManager.TYPE_DEACTIVATION -> DeactivationActivity::class.java
            RemoteDeviceControlManager.TYPE_TAMPER -> SecurityViolationActivity::class.java
            else -> HardLockGenericActivity::class.java
        }

        val intent = Intent(context, activityClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra("lock_reason", lockReason)
            putExtra("lock_type", lockType)
            putExtra("lock_timestamp", lockTimestamp)
            putExtra("from_boot", true)
        }

        try {
            context.startActivity(intent)
            Log.i(TAG, "✅ Lock activity ${activityClass.simpleName} triggered from boot")
            
            // Loop to ensure lock screen stays on top if system tries to hide it during boot
            scope.launch {
                val intervals = listOf(3000L, 7000L, 15000L)
                for (delayTime in intervals) {
                    delay(delayTime)
                    if (controlManager.getLockStateForBoot() == RemoteDeviceControlManager.LOCK_HARD) {
                        Log.d(TAG, "Reinforcing lock screen...")
                        context.startActivity(intent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch lock activity: ${e.message}")
        }
    }

    private fun startAllServicesForRegisteredDevice(context: Context) {
        val appContext = context.applicationContext
        try {
            val deviceId = DeviceIdProvider.getDeviceId(appContext)
            Log.d(TAG, "Starting services for device: $deviceId")
            
            if (!deviceId.isNullOrBlank()) {
                // 1. Heartbeat Service (Essential for real-time tracking)
                HeartbeatService.start(appContext, deviceId)
                
                // 2. Monitoring Services
                com.microspace.payo.monitoring.SecurityMonitorService.startService(appContext, deviceId)
                com.microspace.payo.services.remote.RemoteManagementService.startService(appContext, deviceId)
                com.microspace.payo.services.security.FirmwareSecurityMonitorService.startService(appContext)
                
                // 3. Data Worker (Backup periodic task)
                HeartbeatWorker.enqueue(appContext)

                // 4. Soft Lock Monitor (if applicable)
                if (RemoteDeviceControlManager(appContext).getLockStateForBoot() == RemoteDeviceControlManager.LOCK_SOFT) {
                    com.microspace.payo.services.lock.SoftLockMonitorService.startMonitoring(appContext)
                }
            } else {
                Log.e(TAG, "Cannot start services: Device ID is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services: ${e.message}")
        }
    }
}
