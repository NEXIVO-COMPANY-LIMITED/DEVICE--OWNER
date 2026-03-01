package com.microspace.payo.state

import android.content.Context
import android.util.Log

enum class LockState {
    UNLOCKED,
    SOFT_LOCKED,
    HARD_LOCKED,
    DEACTIVATING,
    DEACTIVATED
}

enum class LockReason {
    NONE,
    PAYMENT_OVERDUE,
    TAMPER_DETECTED,
    DEACTIVATION_REQUESTED,
    PAYMENT_REMINDER,
    SIM_CHANGE,
    UNKNOWN
}

/**
 * Centralized Device Lock State Manager
 */
class DeviceLockStateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LockStateManager"
        private const val PREFS = "lock_state_sync"
    }
    
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val listeners = mutableListOf<(LockState, LockReason) -> Unit>()
    
    fun getLockState(): LockState {
        val state = prefs.getString("current_state", LockState.UNLOCKED.name)
        return try {
            LockState.valueOf(state ?: LockState.UNLOCKED.name)
        } catch (e: Exception) {
            LockState.UNLOCKED
        }
    }
    
    fun getLockReason(): LockReason {
        val reason = prefs.getString("current_reason", LockReason.NONE.name)
        return try {
            LockReason.valueOf(reason ?: LockReason.NONE.name)
        } catch (e: Exception) {
            LockReason.NONE
        }
    }
    
    fun getLockDetails(): LockDetails {
        return LockDetails(
            state = getLockState(),
            reason = getLockReason(),
            timestamp = prefs.getLong("lock_timestamp", 0),
            message = prefs.getString("lock_message", "") ?: "",
            permanent = prefs.getBoolean("lock_permanent", false),
            kioskModeActive = prefs.getBoolean("kiosk_mode_active", false)
        )
    }
    
    fun updateLockState(
        newState: LockState,
        reason: LockReason,
        message: String = "",
        permanent: Boolean = false,
        kioskModeActive: Boolean = false
    ) {
        synchronized(this) {
            prefs.edit().apply {
                putString("current_state", newState.name)
                putString("current_reason", reason.name)
                putLong("lock_timestamp", System.currentTimeMillis())
                putString("lock_message", message)
                putBoolean("lock_permanent", permanent)
                putBoolean("kiosk_mode_active", kioskModeActive)
                apply()
            }
            notifyListeners(newState, reason)
        }
    }
    
    fun clearLockState() {
        updateLockState(LockState.UNLOCKED, LockReason.NONE)
    }
    
    fun addStateChangeListener(listener: (LockState, LockReason) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeStateChangeListener(listener: (LockState, LockReason) -> Unit) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners(state: LockState, reason: LockReason) {
        listeners.forEach { try { it(state, reason) } catch (_: Exception) {} }
    }
    
    data class LockDetails(
        val state: LockState,
        val reason: LockReason,
        val timestamp: Long,
        val message: String,
        val permanent: Boolean,
        val kioskModeActive: Boolean
    )
}




