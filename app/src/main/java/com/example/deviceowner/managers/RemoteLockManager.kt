package com.example.deviceowner.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.example.deviceowner.models.*
import com.example.deviceowner.overlay.OverlayController
import com.example.deviceowner.overlay.OverlayData
import com.example.deviceowner.overlay.OverlayType
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.security.PINVerificationManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Manages remote lock/unlock functionality
 * Feature 4.4: Remote Lock/Unlock
 */
class RemoteLockManager(private val context: Context) {

    companion object {
        private const val TAG = "RemoteLockManager"
        private const val PREFS_NAME = "remote_lock_prefs"
        private const val KEY_ACTIVE_LOCKS = "active_locks"
        private const val KEY_LOCK_QUEUE = "lock_queue"
        private const val KEY_UNLOCK_QUEUE = "unlock_queue"
        private const val KEY_LOCK_HISTORY = "lock_history"
        private const val MAX_HISTORY_SIZE = 100
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val auditLog = IdentifierAuditLog(context)
    private val overlayController = OverlayController(context)
    private val deviceIdentifier = DeviceIdentifier(context)
    private val pinVerificationManager = PINVerificationManager(context)

    /**
     * Apply lock to device
     */
    fun applyLock(lock: DeviceLock): Boolean {
        return try {
            Log.w(TAG, "Applying ${lock.lockType} lock - Reason: ${lock.lockReason}")

            // Save lock to persistent storage
            saveLock(lock)

            // Display overlay based on lock type
            displayLockOverlay(lock)

            // Apply lock based on type
            when (lock.lockType) {
                LockType.SOFT -> applySoftLock(lock)
                LockType.HARD -> applyHardLock(lock)
                LockType.PERMANENT -> applyPermanentLock(lock)
            }

            // Log action
            auditLog.logAction(
                "LOCK_APPLIED",
                "Lock applied: ${lock.lockType} - ${lock.lockReason}"
            )

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying lock", e)
            auditLog.logIncident(
                type = "LOCK_APPLICATION_ERROR",
                severity = "HIGH",
                details = "Failed to apply lock: ${e.message}"
            )
            false
        }
    }

    /**
     * Apply soft lock (warning overlay, device usable)
     */
    private fun applySoftLock(lock: DeviceLock) {
        Log.d(TAG, "Applying soft lock")
        // Soft lock just displays overlay, device remains usable
        // Overlay will be shown by displayLockOverlay()
    }

    /**
     * Apply hard lock (full device lock, no interaction)
     */
    private fun applyHardLock(lock: DeviceLock) {
        Log.d(TAG, "Applying hard lock")
        try {
            if (isDeviceOwner()) {
                devicePolicyManager.lockNow()
                Log.d(TAG, "Device locked via DPM")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock", e)
        }
    }

    /**
     * Apply permanent lock (repossession lock, backend unlock only)
     */
    private fun applyPermanentLock(lock: DeviceLock) {
        Log.d(TAG, "Applying permanent lock")
        try {
            if (isDeviceOwner()) {
                // Set strong password requirement
                devicePolicyManager.resetPassword("LOCKED", 0)
                // Lock device
                devicePolicyManager.lockNow()
                Log.d(TAG, "Permanent lock applied - backend unlock required")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying permanent lock", e)
        }
    }

    /**
     * Display lock overlay
     */
    private fun displayLockOverlay(lock: DeviceLock) {
        try {
            val overlayType = when (lock.lockType) {
                LockType.SOFT -> OverlayType.WARNING_MESSAGE
                LockType.HARD -> OverlayType.LOCK_NOTIFICATION
                LockType.PERMANENT -> OverlayType.COMPLIANCE_ALERT
            }

            val overlay = OverlayData(
                id = lock.lockId,
                type = overlayType,
                title = "Device Locked",
                message = lock.message,
                actionButtonText = "OK",
                actionButtonColor = 0xFF2196F3.toInt(),
                priority = 12,
                dismissible = lock.lockType == LockType.SOFT,
                expiryTime = lock.expiresAt
            )

            overlayController.showOverlay(overlay)
            Log.d(TAG, "Lock overlay displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying lock overlay", e)
        }
    }

    /**
     * Unlock device with PIN verification
     */
    fun unlockWithPin(lockId: String, pin: String): Boolean {
        return try {
            val lock = getLock(lockId) ?: return false

            // Check if PIN is required
            if (!lock.pinRequired) {
                Log.w(TAG, "PIN not required for this lock")
                return false
            }

            // Verify PIN
            if (!verifyPin(pin, lock.pinHash ?: "")) {
                Log.w(TAG, "Invalid PIN")
                incrementUnlockAttempts(lockId)
                return false
            }

            // Unlock device
            removeLock(lockId)
            auditLog.logAction("UNLOCK_SUCCESS", "Device unlocked with PIN")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking with PIN", e)
            false
        }
    }

    /**
     * Verify PIN against hash
     */
    private fun verifyPin(pin: String, pinHash: String): Boolean {
        val hash = hashPin(pin)
        return hash == pinHash
    }

    /**
     * Hash PIN using SHA-256
     */
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Increment unlock attempts
     */
    private fun incrementUnlockAttempts(lockId: String) {
        val lock = getLock(lockId) ?: return
        val updated = lock.copy(unlockAttempts = lock.unlockAttempts + 1)

        if (updated.unlockAttempts >= updated.maxAttempts) {
            Log.w(TAG, "Max unlock attempts exceeded")
            auditLog.logIncident(
                type = "UNLOCK_ATTEMPTS_EXCEEDED",
                severity = "HIGH",
                details = "Lock: $lockId"
            )
        }

        saveLock(updated)
    }

    /**
     * Queue lock command for offline execution
     */
    fun queueLockCommand(command: LockCommand): Boolean {
        return try {
            val queue = getLockQueue().toMutableList()
            queue.add(command)
            saveLockQueue(queue)
            Log.d(TAG, "Lock command queued: ${command.commandId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error queueing lock command", e)
            false
        }
    }

    /**
     * Queue unlock command for offline execution
     */
    fun queueUnlockCommand(command: UnlockCommand): Boolean {
        return try {
            val queue = getUnlockQueue().toMutableList()
            queue.add(command)
            saveUnlockQueue(queue)
            Log.d(TAG, "Unlock command queued: ${command.commandId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error queueing unlock command", e)
            false
        }
    }

    /**
     * Process queued lock commands
     */
    fun processQueuedLockCommands() {
        try {
            val queue = getLockQueue()
            for (command in queue) {
                val lock = DeviceLock(
                    lockId = command.commandId,
                    deviceId = command.deviceId,
                    lockType = command.lockType,
                    lockStatus = LockStatus.ACTIVE,
                    lockReason = command.reason,
                    message = command.message,
                    expiresAt = command.expiresAt,
                    pinRequired = command.pin != null,
                    pinHash = command.pin?.let { hashPin(it) }
                )
                applyLock(lock)
            }
            saveLockQueue(emptyList())
            Log.d(TAG, "Processed ${queue.size} queued lock commands")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queued lock commands", e)
        }
    }

    /**
     * Process queued unlock commands
     */
    fun processQueuedUnlockCommands() {
        try {
            val queue = getUnlockQueue()
            for (command in queue) {
                removeLock(command.lockId)
            }
            saveUnlockQueue(emptyList())
            Log.d(TAG, "Processed ${queue.size} queued unlock commands")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queued unlock commands", e)
        }
    }

    /**
     * Save lock to persistent storage
     */
    private fun saveLock(lock: DeviceLock) {
        val locks = getActiveLocks().toMutableMap()
        locks[lock.lockId] = lock
        prefs.edit().putString(KEY_ACTIVE_LOCKS, gson.toJson(locks)).apply()
    }

    /**
     * Get lock by ID
     */
    fun getLock(lockId: String): DeviceLock? {
        return try {
            val locks = getActiveLocks()
            locks[lockId]
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lock", e)
            null
        }
    }

    /**
     * Get all active locks
     */
    fun getActiveLocks(): Map<String, DeviceLock> {
        return try {
            val json = prefs.getString(KEY_ACTIVE_LOCKS, "{}")
            gson.fromJson(json, Map::class.java) as? Map<String, DeviceLock> ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active locks", e)
            emptyMap()
        }
    }

    /**
     * Remove lock
     */
    fun removeLock(lockId: String): Boolean {
        return try {
            val locks = getActiveLocks().toMutableMap()
            locks.remove(lockId)
            prefs.edit().putString(KEY_ACTIVE_LOCKS, gson.toJson(locks)).apply()
            Log.d(TAG, "Lock removed: $lockId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing lock", e)
            false
        }
    }

    /**
     * Get lock queue
     */
    private fun getLockQueue(): List<LockCommand> {
        return try {
            val json = prefs.getString(KEY_LOCK_QUEUE, "[]")
            gson.fromJson(json, Array<LockCommand>::class.java).toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lock queue", e)
            emptyList()
        }
    }

    /**
     * Save lock queue
     */
    private fun saveLockQueue(queue: List<LockCommand>) {
        prefs.edit().putString(KEY_LOCK_QUEUE, gson.toJson(queue)).apply()
    }

    /**
     * Get unlock queue
     */
    private fun getUnlockQueue(): List<UnlockCommand> {
        return try {
            val json = prefs.getString(KEY_UNLOCK_QUEUE, "[]")
            gson.fromJson(json, Array<UnlockCommand>::class.java).toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unlock queue", e)
            emptyList()
        }
    }

    /**
     * Save unlock queue
     */
    private fun saveUnlockQueue(queue: List<UnlockCommand>) {
        prefs.edit().putString(KEY_UNLOCK_QUEUE, gson.toJson(queue)).apply()
    }

    /**
     * Check if device is owner
     */
    private fun isDeviceOwner(): Boolean {
        return try {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
}
