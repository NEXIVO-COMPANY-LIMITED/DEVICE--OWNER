package com.example.deviceowner.services.heartbeat

import android.content.Context
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.data.DeviceIdProvider
import com.example.deviceowner.data.models.heartbeat.HeartbeatResponse
import com.example.deviceowner.data.models.tamper.TamperEventRequest
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.data.remote.RetrofitClient
import com.example.deviceowner.data.remote.ApiService
import com.example.deviceowner.utils.storage.PaymentDataManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HeartbeatResponseHandler - STATE-DRIVEN IMPLEMENTATION (v4.5)
 * 
 * ✅ ABSOLUTE PRIORITY: Deactivation overrides EVERYTHING.
 * ✅ Automatic Recovery: Syncs local lock state with server every 10 seconds.
 */
class HeartbeatResponseHandler(private val context: Context) {
    
    private val TAG = "HeartbeatResponseHandler"
    private val controlManager = RemoteDeviceControlManager(context)
    private val paymentDataManager = PaymentDataManager(context)
    private val auditPrefs = context.getSharedPreferences("heartbeat_audit", Context.MODE_PRIVATE)
    
    private val isProcessing = AtomicBoolean(false)
    private val PROCESSING_TIMEOUT_MS = 30_000L
    
    fun handle(response: HeartbeatResponse) {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.w(TAG, "⚠️ Handler busy, skipping response")
            return
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeoutOrNull(PROCESSING_TIMEOUT_MS) {
                    processResponseSafely(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fatal error in handler: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    private suspend fun processResponseSafely(response: HeartbeatResponse) {
        // Save metadata first
        savePaymentData(response)
        
        // 1. DETERMINE GLOBAL STATE FROM SERVER
        val isDeactivationRequested = response.isDeactivationRequested()
        val isServerLocked = response.isDeviceLocked()
        val lockReason = response.getLockReason()
        val currentLocalState = controlManager.getLockState()
        
        Log.i(TAG, "📡 SYNC [DEACT=$isDeactivationRequested | LOCKED=$isServerLocked | LOCAL=$currentLocalState]")

        try {
            // ==========================================================
            // PRIORITY 1: DEACTIVATION (THE MASTER OVERRIDE)
            // ==========================================================
            if (isDeactivationRequested) {
                Log.w(TAG, "🔓 CRITICAL: DEACTIVATION REQUESTED. OVERRIDING ALL LOCKS.")
                
                // A. Force Unlock EVERYTHING (Apps, Restrictions, LockTask)
                if (currentLocalState != RemoteDeviceControlManager.LOCK_UNLOCKED) {
                    Log.d(TAG, "Cleaning up locks for deactivation...")
                    controlManager.unlockDevice()
                }
                
                // B. Launch Deactivation Screen
                controlManager.applyHardLock(
                    reason = lockReason.ifBlank { "System deactivation in progress..." },
                    lockType = RemoteDeviceControlManager.TYPE_DEACTIVATION,
                    forceFromServerOrMismatch = true
                )
                return // TERMINATE: No further lock checks
            }
            
            // ==========================================================
            // PRIORITY 2: HARD LOCK
            // ==========================================================
            if (isServerLocked) {
                val lockType = determineLockType(lockReason)
                
                // Apply if not already locked or if reason is significant
                if (currentLocalState != RemoteDeviceControlManager.LOCK_HARD) {
                    Log.e(TAG, "🔒 SERVER COMMAND: APPLY HARD LOCK ($lockType)")
                    controlManager.applyHardLock(
                        reason = lockReason,
                        lockType = lockType,
                        forceFromServerOrMismatch = true,
                        nextPaymentDate = response.getNextPaymentDateTime()
                    )
                }
                return
            }

            // ==========================================================
            // PRIORITY 3: RECOVERY / UNLOCK (Server says NOT locked)
            // ==========================================================
            if (!isServerLocked && currentLocalState == RemoteDeviceControlManager.LOCK_HARD) {
                Log.i(TAG, "✅ SERVER RECOVERY: Releasing local lock...")
                controlManager.unlockDevice()
                return
            }

            // ==========================================================
            // PRIORITY 4: SOFT LOCK (Only if not hard locked)
            // ==========================================================
            if (response.isSoftLockRequested()) {
                val reminderMsg = response.actions?.reminder?.message ?: "Payment Reminder"
                if (currentLocalState != RemoteDeviceControlManager.LOCK_SOFT) {
                    Log.w(TAG, "🔔 APPLYING SOFT LOCK REMINDER")
                    controlManager.applySoftLock(
                        reason = reminderMsg,
                        nextPaymentDate = response.getNextPaymentDateTime()
                    )
                }
            } else if (currentLocalState == RemoteDeviceControlManager.LOCK_SOFT) {
                Log.i(TAG, "🔓 Removing soft lock reminder.")
                controlManager.unlockDevice()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing actions: ${e.message}")
        }
    }

    private fun determineLockType(reason: String): String {
        val r = reason.lowercase()
        return when {
            r.contains("overdue") || r.contains("payment") || r.contains("installment") || r.contains("loan") -> 
                RemoteDeviceControlManager.TYPE_OVERDUE
            else -> RemoteDeviceControlManager.TYPE_TAMPER
        }
    }

    private fun savePaymentData(response: HeartbeatResponse) {
        response.nextPayment?.let { payment ->
            paymentDataManager.saveNextPaymentInfo(
                dateTime = payment.dateTime,
                unlockPassword = payment.unlockPassword ?: payment.unlockingPassword,
                amount = null,
                currency = "TZS"
            )
        }
    }
}
