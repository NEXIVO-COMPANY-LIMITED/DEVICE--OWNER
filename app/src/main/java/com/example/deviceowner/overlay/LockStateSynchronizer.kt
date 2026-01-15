package com.example.deviceowner.overlay

import android.content.Context
import android.util.Log
import com.example.deviceowner.managers.IdentifierAuditLog
import com.example.deviceowner.models.LockType
import com.example.deviceowner.models.DeviceLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lock State Synchronizer
 * Bridges LockType (backend classification) and LockState (device state)
 * Ensures consistency between the two systems
 * 
 * Feature 4.6: Lock State Integration & Synchronization
 */
class LockStateSynchronizer(private val context: Context) {

    companion object {
        private const val TAG = "LockStateSynchronizer"
        private const val PREFS_NAME = "lock_state_sync_prefs"
        private const val KEY_ACTIVE_LOCKS = "active_locks"
    }

    private val auditLog = IdentifierAuditLog(context)
    private val enhancedController = EnhancedOverlayController(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    /**
     * Synchronize lock state based on active locks
     * Determines current device lock state from all active locks
     * 
     * Priority: HARD_LOCK > SOFT_LOCK > UNLOCKED
     */
    suspend fun syncLockState(activeLocks: Map<String, DeviceLock>) {
        withContext(Dispatchers.Main) {
            try {
                val newLockState = determineLockState(activeLocks)
                val currentLockState = enhancedController.getCurrentLockState()

                if (currentLockState != newLockState) {
                    Log.w(TAG, "Lock state mismatch detected: $currentLockState → $newLockState")
                    Log.w(TAG, "Active locks: ${activeLocks.size} (${activeLocks.values.map { it.lockType }.distinct()})")

                    enhancedController.updateLockState(newLockState)

                    auditLog.logAction(
                        "LOCK_STATE_SYNCHRONIZED",
                        "Lock state synchronized: $currentLockState → $newLockState (${activeLocks.size} active locks)"
                    )
                } else {
                    Log.d(TAG, "Lock state consistent: $currentLockState")
                }

                // Persist active locks for recovery
                persistActiveLocks(activeLocks)
            } catch (e: Exception) {
                Log.e(TAG, "Error synchronizing lock state", e)
                auditLog.logIncident(
                    type = "LOCK_STATE_SYNC_ERROR",
                    severity = "HIGH",
                    details = "Failed to synchronize lock state: ${e.message}"
                )
            }
        }
    }

    /**
     * Determine lock state from active locks
     * 
     * Rules:
     * - If any HARD lock exists → HARD_LOCK
     * - Else if any SOFT lock exists → SOFT_LOCK
     * - Else → UNLOCKED
     */
    private fun determineLockState(activeLocks: Map<String, DeviceLock>): LockState {
        return when {
            activeLocks.values.any { it.lockType == LockType.HARD } -> {
                Log.d(TAG, "Determined lock state: HARD_LOCK (${activeLocks.count { it.value.lockType == LockType.HARD }} hard locks)")
                LockState.HARD_LOCK
            }
            activeLocks.values.any { it.lockType == LockType.SOFT } -> {
                Log.d(TAG, "Determined lock state: SOFT_LOCK (${activeLocks.count { it.value.lockType == LockType.SOFT }} soft locks)")
                LockState.SOFT_LOCK
            }
            else -> {
                Log.d(TAG, "Determined lock state: UNLOCKED (no active locks)")
                LockState.UNLOCKED
            }
        }
    }

    /**
     * Persist active locks for recovery on app restart
     */
    private fun persistActiveLocks(activeLocks: Map<String, DeviceLock>) {
        try {
            synchronized(this) {
                val json = gson.toJson(activeLocks)
                prefs.edit().putString(KEY_ACTIVE_LOCKS, json).apply()
            }
            Log.d(TAG, "Active locks persisted: ${activeLocks.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting active locks", e)
        }
    }

    /**
     * Restore active locks from persistence
     * Called on app initialization to recover lock state
     */
    fun restoreActiveLocks(): Map<String, DeviceLock> {
        return try {
            synchronized(this) {
                val json = prefs.getString(KEY_ACTIVE_LOCKS, null)
                if (json != null && json.isNotEmpty()) {
                    val typeToken = object : com.google.gson.reflect.TypeToken<Map<String, DeviceLock>>() {}.type
                    val locks: Map<String, DeviceLock> = gson.fromJson(json, typeToken)
                    Log.d(TAG, "Active locks restored: ${locks.size}")
                    locks
                } else {
                    emptyMap()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring active locks", e)
            emptyMap()
        }
    }

    /**
     * Clear all lock state
     * Called after successful unlock
     */
    suspend fun clearAllLocks() {
        withContext(Dispatchers.Main) {
            try {
                Log.w(TAG, "Clearing all locks")
                enhancedController.clearLockState()

                synchronized(this) {
                    prefs.edit().clear().apply()
                }

                auditLog.logAction(
                    "ALL_LOCKS_CLEARED",
                    "All locks cleared and device unlocked"
                )

                Log.d(TAG, "✓ All locks cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing all locks", e)
            }
        }
    }

    /**
     * Validate lock state consistency
     * Returns validation result with any inconsistencies
     */
    fun validateLockStateConsistency(activeLocks: Map<String, DeviceLock>): LockStateValidationResult {
        try {
            val expectedLockState = determineLockState(activeLocks)
            val currentLockState = enhancedController.getCurrentLockState()

            val isConsistent = expectedLockState == currentLockState
            val issues = mutableListOf<String>()

            if (!isConsistent) {
                issues.add("Lock state mismatch: expected $expectedLockState but got $currentLockState")
            }

            // Check for conflicting lock types
            val hardLocks = activeLocks.values.filter { it.lockType == LockType.HARD }
            val softLocks = activeLocks.values.filter { it.lockType == LockType.SOFT }

            if (hardLocks.isNotEmpty() && softLocks.isNotEmpty()) {
                issues.add("Conflicting lock types: ${hardLocks.size} HARD locks and ${softLocks.size} SOFT locks")
            }

            // Check for expired locks
            val expiredLocks = activeLocks.values.filter { it.expiresAt != null && it.expiresAt < System.currentTimeMillis() }
            if (expiredLocks.isNotEmpty()) {
                issues.add("${expiredLocks.size} expired locks still active")
            }

            return LockStateValidationResult(
                isConsistent = isConsistent,
                expectedLockState = expectedLockState,
                currentLockState = currentLockState,
                activeLockCount = activeLocks.size,
                issues = issues
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error validating lock state consistency", e)
            return LockStateValidationResult(
                isConsistent = false,
                issues = listOf("Validation error: ${e.message}")
            )
        }
    }
}

/**
 * Data class for lock state validation results
 */
data class LockStateValidationResult(
    val isConsistent: Boolean,
    val expectedLockState: LockState? = null,
    val currentLockState: LockState? = null,
    val activeLockCount: Int = 0,
    val issues: List<String> = emptyList()
)
