package com.microspace.payo.services.heartbeat

import android.content.Context
import android.content.Intent
import android.util.Log
import com.microspace.payo.control.RemoteDeviceControlManager
import com.microspace.payo.data.local.database.DeviceOwnerDatabase
import com.microspace.payo.data.local.database.entities.audit.SyncAuditEntity
import com.microspace.payo.data.models.heartbeat.HeartbeatResponse
import com.microspace.payo.security.crypto.EncryptionManager
import com.microspace.payo.services.lock.SoftLockOverlayService
import com.microspace.payo.utils.storage.PaymentDataManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HeartbeatResponseHandler v2.1 - PRODUCTION-READY IMPLEMENTATION WITH ROBUST AUDIT LOGGING
 * 
 * Securely stores audit state using EncryptedSharedPreferences.
 */
class HeartbeatResponseHandler_v2(private val context: Context) {
    
    private val TAG = "HeartbeatResponseHandler_v2"
    private val controlManager = RemoteDeviceControlManager(context)
    private val paymentDataManager = PaymentDataManager(context)
    private val auditPrefs = EncryptionManager(context).getEncryptedSharedPreferences("heartbeat_audit_secure")
    private val db = DeviceOwnerDatabase.getDatabase(context)
    
    private val isProcessing = AtomicBoolean(false)
    private val PROCESSING_TIMEOUT_MS = 30_000L
    
    companion object {
        const val ACTION_DISMISS_LOCK = "com.microspace.payo.DISMISS_LOCK_SCREEN"
    }
    
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
                Log.e(TAG, "❌ Fatal error: ${e.message}", e)
                logAuditToDb(
                    serverState = "ERROR",
                    before = controlManager.getLockState(),
                    after = controlManager.getLockState(),
                    action = "FATAL_ERROR",
                    details = e.message ?: "Unknown error"
                )
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    private suspend fun processResponseSafely(response: HeartbeatResponse) {
        savePaymentData(response)
        
        val isDeactivationRequested = response.isDeactivationRequested()
        val isServerLocked = response.isDeviceLocked()
        val isSoftLockRequested = response.isSoftLockRequested()
        val lockReason = response.getLockReason()
        val currentDeviceState = controlManager.getLockState()
        
        Log.i(TAG, "📡 HEARTBEAT SYNC: Deactivation=$isDeactivationRequested, Locked=$isServerLocked, SoftLock=$isSoftLockRequested, Device=$currentDeviceState")

        if (isDeactivationRequested) {
            handleDeactivation(lockReason, currentDeviceState)
            return
        }
        
        if (isServerLocked) {
            if (currentDeviceState == RemoteDeviceControlManager.LOCK_SOFT) {
                controlManager.unlockDevice()
                sendDismissBroadcast()
                delay(300)
            }
            handleHardLock(response, lockReason, currentDeviceState)
            return
        }
        
        if (isSoftLockRequested) {
            if (currentDeviceState == RemoteDeviceControlManager.LOCK_HARD) {
                controlManager.unlockDevice()
                sendDismissBroadcast()
                delay(300)
            }
            handleSoftLock(response, currentDeviceState)
            return
        }
        
        handleUnlock(currentDeviceState)
    }

    private suspend fun handleDeactivation(reason: String, currentState: String) {
        if (currentState != RemoteDeviceControlManager.LOCK_UNLOCKED) {
            controlManager.unlockDevice()
            sendDismissBroadcast()
        }
        
        controlManager.applyHardLock(
            reason = reason.ifBlank { "System deactivation in progress..." },
            lockType = RemoteDeviceControlManager.TYPE_DEACTIVATION,
            forceFromServerOrMismatch = true
        )
        
        logAuditToDb(
            serverState = "DEACTIVATION",
            before = currentState,
            after = RemoteDeviceControlManager.LOCK_HARD,
            action = "APPLY_DEACTIVATION_LOCK",
            details = "Deactivation reason: $reason"
        )
    }

    private suspend fun handleHardLock(response: HeartbeatResponse, reason: String, currentState: String) {
        val lockType = determineLockType(reason)
        val nextPayDate = response.getNextPaymentDateTime() ?: ""

        if (currentState == RemoteDeviceControlManager.LOCK_HARD) {
            val lastReason = auditPrefs.getString("last_lock_reason", "")
            if (reason != lastReason) {
                controlManager.showLockActivity(reason, lockType, nextPayDate)
                auditPrefs.edit().putString("last_lock_reason", reason).apply()
                
                logAuditToDb(
                    serverState = "HARD_LOCK",
                    before = currentState,
                    after = currentState,
                    action = "UPDATE_LOCK_DETAILS",
                    details = "Reason updated"
                )
            }
        } else {
            controlManager.applyHardLock(
                reason = reason,
                lockType = lockType,
                forceFromServerOrMismatch = true,
                nextPaymentDate = nextPayDate
            )
            auditPrefs.edit().putString("last_lock_reason", reason).apply()
            
            logAuditToDb(
                serverState = "HARD_LOCK",
                before = currentState,
                after = RemoteDeviceControlManager.LOCK_HARD,
                action = "APPLY_HARD_LOCK",
                details = "Applied reason: $reason",
                lockReason = reason
            )
        }
    }

    private suspend fun handleSoftLock(response: HeartbeatResponse, currentState: String) {
        val reminderMsg = response.actions?.reminder?.message ?: "Payment Reminder"
        val nextPaymentDate = response.getNextPaymentDateTime()

        if (currentState == RemoteDeviceControlManager.LOCK_SOFT) {
            val lastMessage = auditPrefs.getString("last_soft_lock_message", "")
            if (reminderMsg != lastMessage) {
                controlManager.applySoftLock(reason = reminderMsg, nextPaymentDate = nextPaymentDate)
                auditPrefs.edit().putString("last_soft_lock_message", reminderMsg).apply()
                
                logAuditToDb(
                    serverState = "SOFT_LOCK",
                    before = currentState,
                    after = currentState,
                    action = "UPDATE_SOFT_LOCK_MSG",
                    details = "Message updated"
                )
            }
        } else {
            controlManager.applySoftLock(reason = reminderMsg, nextPaymentDate = nextPaymentDate)
            auditPrefs.edit().putString("last_soft_lock_message", reminderMsg).apply()
            
            logAuditToDb(
                serverState = "SOFT_LOCK",
                before = currentState,
                after = RemoteDeviceControlManager.LOCK_SOFT,
                action = "APPLY_SOFT_LOCK",
                details = "Applied from state: $currentState",
                lockReason = reminderMsg
            )
        }
    }

    private suspend fun handleUnlock(currentState: String) {
        if (currentState == RemoteDeviceControlManager.LOCK_UNLOCKED) {
            logAuditToDb(
                serverState = "UNLOCKED",
                before = currentState,
                after = currentState,
                action = "NO_CHANGE",
                details = "Already unlocked"
            )
        } else {
            controlManager.unlockDevice()
            sendDismissBroadcast()
            try {
                SoftLockOverlayService.stop(context)
            } catch (_: Exception) {}
            
            auditPrefs.edit().clear().apply()
            
            logAuditToDb(
                serverState = "UNLOCKED",
                before = currentState,
                after = RemoteDeviceControlManager.LOCK_UNLOCKED,
                action = "TRIGGER_UNLOCK",
                details = "Automatic unlock (Mara Moja)"
            )
        }
    }

    private fun determineLockType(reason: String): String {
        val r = reason.lowercase()
        return when {
            r.contains("overdue") || r.contains("payment") || r.contains("installment") ->
                RemoteDeviceControlManager.TYPE_OVERDUE
            r.contains("deactivate") || r.contains("removal") ->
                RemoteDeviceControlManager.TYPE_DEACTIVATION
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

    private fun sendDismissBroadcast() {
        try {
            val intent = Intent(ACTION_DISMISS_LOCK).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    private suspend fun logAuditToDb(
        serverState: String,
        before: String,
        after: String,
        action: String,
        details: String,
        lockReason: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val audit = SyncAuditEntity(
                serverState = serverState,
                deviceStateBefore = before,
                deviceStateAfter = after,
                actionTaken = action,
                details = details,
                lockReason = lockReason
            )
            db.syncAuditDao().insert(audit)
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            db.syncAuditDao().pruneOldLogs(thirtyDaysAgo)
        } catch (_: Exception) {}
    }
}
