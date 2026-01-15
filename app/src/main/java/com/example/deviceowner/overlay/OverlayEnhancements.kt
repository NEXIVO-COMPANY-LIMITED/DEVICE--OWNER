package com.example.deviceowner.overlay

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.example.deviceowner.managers.IdentifierAuditLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced overlay functionality for soft lock and hard lock
 * Feature 4.6: Pop-up Screens / Overlay UI - Improvements
 */

/**
 * Lock state enum
 */
enum class LockState {
    UNLOCKED,
    SOFT_LOCK,
    HARD_LOCK
}

/**
 * Overlay action callback interface
 */
interface OverlayActionCallback {
    suspend fun onAcknowledge(overlayId: String)
    suspend fun onPayNow(overlayId: String, amount: String)
    suspend fun onContactSupport(overlayId: String)
    suspend fun onEnterPin(overlayId: String, pin: String)
    suspend fun onDismiss(overlayId: String)
}

/**
 * Enhanced OverlayController with lock-state awareness
 * Manages lock state and ensures overlay behavior is consistent with device lock state
 */
class EnhancedOverlayController(private val context: Context) {

    companion object {
        private const val TAG = "EnhancedOverlayController"
        private const val PREFS_NAME = "enhanced_overlay_prefs"
        private const val KEY_CURRENT_LOCK_STATE = "current_lock_state"
    }

    private val auditLog = IdentifierAuditLog(context)
    private val overlayController = OverlayController(context)
    private val stateManager = OverlayStateManager(context)
    private var actionCallback: OverlayActionCallback? = null
    private var currentLockState: LockState = LockState.UNLOCKED
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Load persisted lock state on initialization
        currentLockState = stateManager.loadLockState()
        Log.d(TAG, "EnhancedOverlayController initialized with lock state: $currentLockState")
    }

    /**
     * Set action callback for overlay interactions
     */
    fun setActionCallback(callback: OverlayActionCallback) {
        this.actionCallback = callback
    }

    /**
     * Update current lock state and persist it
     * This is the single source of truth for device lock state
     */
    fun updateLockState(lockState: LockState) {
        if (currentLockState == lockState) {
            Log.d(TAG, "Lock state unchanged: $lockState")
            return
        }

        Log.w(TAG, "Lock state transition: $currentLockState → $lockState")
        this.currentLockState = lockState

        // Persist lock state
        stateManager.saveLockState(lockState)

        // Log state change
        auditLog.logAction(
            "LOCK_STATE_CHANGED",
            "Lock state changed to: $lockState"
        )
    }

    /**
     * Get current lock state
     */
    fun getCurrentLockState(): LockState = currentLockState

    /**
     * Validate overlay settings against lock state requirements
     * Returns validation result with any conflicts
     */
    private fun validateOverlayForLockState(overlay: OverlayData): OverlayValidationResult {
        return when (currentLockState) {
            LockState.HARD_LOCK -> {
                // Hard lock requires: blockAllInteractions=true, dismissible=false
                val isValid = overlay.blockAllInteractions && !overlay.dismissible
                if (!isValid) {
                    Log.w(TAG, "Overlay ${overlay.id} conflicts with HARD_LOCK state")
                    OverlayValidationResult(
                        isValid = false,
                        conflicts = listOf(
                            "HARD_LOCK requires blockAllInteractions=true",
                            "HARD_LOCK requires dismissible=false"
                        )
                    )
                } else {
                    OverlayValidationResult(isValid = true)
                }
            }
            LockState.SOFT_LOCK -> {
                // Soft lock requires: blockAllInteractions=false, dismissible=true
                val isValid = !overlay.blockAllInteractions && overlay.dismissible
                if (!isValid) {
                    Log.w(TAG, "Overlay ${overlay.id} conflicts with SOFT_LOCK state")
                    OverlayValidationResult(
                        isValid = false,
                        conflicts = listOf(
                            "SOFT_LOCK requires blockAllInteractions=false",
                            "SOFT_LOCK requires dismissible=true"
                        )
                    )
                } else {
                    OverlayValidationResult(isValid = true)
                }
            }
            LockState.UNLOCKED -> {
                // Unlocked state: no restrictions
                OverlayValidationResult(isValid = true)
            }
        }
    }

    /**
     * Show overlay with lock-state awareness
     * Automatically adapts overlay behavior based on current lock state
     * Enforces lock state requirements and logs conflicts
     */
    suspend fun showOverlayWithLockState(overlay: OverlayData) {
        withContext(Dispatchers.Main) {
            try {
                // Validate overlay against current lock state
                val validation = validateOverlayForLockState(overlay)
                if (!validation.isValid) {
                    Log.w(TAG, "Overlay validation failed: ${validation.conflicts}")
                    auditLog.logIncident(
                        type = "OVERLAY_VALIDATION_FAILED",
                        severity = "MEDIUM",
                        details = "Overlay ${overlay.id} conflicts with $currentLockState: ${validation.conflicts.joinToString(", ")}"
                    )
                }

                // Adapt overlay based on lock state
                val enhancedOverlay = when (currentLockState) {
                    LockState.SOFT_LOCK -> {
                        Log.d(TAG, "Adapting overlay for SOFT_LOCK state")
                        overlay.copy(
                            blockAllInteractions = false,
                            dismissible = true,
                            actionButtonText = overlay.actionButtonText  // Preserve original button text
                        )
                    }
                    LockState.HARD_LOCK -> {
                        Log.d(TAG, "Adapting overlay for HARD_LOCK state")
                        overlay.copy(
                            blockAllInteractions = true,
                            dismissible = false,
                            actionButtonText = overlay.actionButtonText  // Preserve original button text
                        )
                    }
                    LockState.UNLOCKED -> {
                        Log.d(TAG, "Showing overlay in UNLOCKED state (no adaptation)")
                        overlay  // No adaptation needed
                    }
                }

                overlayController.showOverlay(enhancedOverlay)

                auditLog.logAction(
                    "OVERLAY_SHOWN_WITH_LOCK_STATE",
                    "Overlay ${overlay.id} shown in $currentLockState state"
                )

                Log.d(TAG, "✓ Overlay ${overlay.id} displayed with lock-state awareness")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing overlay with lock state", e)
                auditLog.logIncident(
                    type = "OVERLAY_LOCK_STATE_ERROR",
                    severity = "HIGH",
                    details = "Failed to show overlay with lock state: ${e.message}"
                )
            }
        }
    }

    /**
     * Show soft lock overlay
     */
    suspend fun showSoftLockOverlay(
        title: String,
        message: String,
        reason: String
    ) {
        withContext(Dispatchers.Main) {
            try {
                updateLockState(LockState.SOFT_LOCK)

                val overlay = OverlayData(
                    id = "soft_lock_${System.currentTimeMillis()}",
                    type = OverlayType.SOFT_LOCK,
                    title = title,
                    message = message,
                    actionButtonText = "Acknowledge",
                    actionButtonColor = 0xFF4CAF50.toInt(),
                    dismissible = true,
                    blockAllInteractions = false,
                    priority = 10,
                    metadata = mapOf(
                        "reason" to reason,
                        "lock_type" to "SOFT"
                    )
                )

                showOverlayWithLockState(overlay)

                auditLog.logAction(
                    "SOFT_LOCK_OVERLAY_SHOWN",
                    "Soft lock overlay shown: $reason"
                )

                Log.d(TAG, "✓ Soft lock overlay shown")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing soft lock overlay", e)
                auditLog.logIncident(
                    type = "SOFT_LOCK_ERROR",
                    severity = "HIGH",
                    details = "Failed to show soft lock overlay: ${e.message}"
                )
            }
        }
    }

    /**
     * Show hard lock overlay
     */
    suspend fun showHardLockOverlay(
        title: String,
        message: String,
        reason: String
    ) {
        withContext(Dispatchers.Main) {
            try {
                updateLockState(LockState.HARD_LOCK)

                val overlay = OverlayData(
                    id = "hard_lock_${System.currentTimeMillis()}",
                    type = OverlayType.HARD_LOCK,
                    title = title,
                    message = message,
                    actionButtonText = "Contact Support",
                    actionButtonColor = 0xFFF44336.toInt(),
                    dismissible = false,
                    blockAllInteractions = true,
                    priority = 12,
                    metadata = mapOf(
                        "reason" to reason,
                        "lock_type" to "HARD"
                    )
                )

                showOverlayWithLockState(overlay)

                auditLog.logAction(
                    "HARD_LOCK_OVERLAY_SHOWN",
                    "Hard lock overlay shown: $reason"
                )

                Log.d(TAG, "✓ Hard lock overlay shown")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing hard lock overlay", e)
                auditLog.logIncident(
                    type = "HARD_LOCK_ERROR",
                    severity = "HIGH",
                    details = "Failed to show hard lock overlay: ${e.message}"
                )
            }
        }
    }

    /**
     * Handle overlay action with lock state awareness
     */
    suspend fun handleOverlayAction(
        overlayId: String,
        actionType: String,
        data: Map<String, String> = emptyMap()
    ) {
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Handling overlay action: $actionType for $overlayId in $currentLockState state")

                // Validate action is allowed in current lock state
                if (currentLockState == LockState.HARD_LOCK && actionType != "CONTACT_SUPPORT") {
                    Log.w(TAG, "Action $actionType not allowed in HARD_LOCK state")
                    auditLog.logIncident(
                        type = "OVERLAY_ACTION_BLOCKED",
                        severity = "MEDIUM",
                        details = "Action $actionType blocked in $currentLockState state"
                    )
                } else {
                    when (actionType) {
                        "ACKNOWLEDGE" -> actionCallback?.onAcknowledge(overlayId)
                        "PAY_NOW" -> {
                            val amount = data["amount"] ?: ""
                            actionCallback?.onPayNow(overlayId, amount)
                        }
                        "CONTACT_SUPPORT" -> actionCallback?.onContactSupport(overlayId)
                        "ENTER_PIN" -> {
                            val pin = data["pin"] ?: ""
                            actionCallback?.onEnterPin(overlayId, pin)
                        }
                        "DISMISS" -> {
                            if (currentLockState != LockState.HARD_LOCK) {
                                actionCallback?.onDismiss(overlayId)
                            } else {
                                Log.w(TAG, "Cannot dismiss overlay in HARD_LOCK state")
                            }
                        }
                    }
                }

                auditLog.logAction(
                    "OVERLAY_ACTION_HANDLED",
                    "Overlay action $actionType handled for $overlayId"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error handling overlay action", e)
                auditLog.logIncident(
                    type = "OVERLAY_ACTION_ERROR",
                    severity = "MEDIUM",
                    details = "Failed to handle overlay action: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear lock state and return to UNLOCKED
     * Should only be called after successful unlock
     */
    suspend fun clearLockState() {
        withContext(Dispatchers.Main) {
            try {
                Log.w(TAG, "Clearing lock state: $currentLockState → UNLOCKED")
                updateLockState(LockState.UNLOCKED)

                auditLog.logAction(
                    "LOCK_STATE_CLEARED",
                    "Device lock state cleared"
                )

                Log.d(TAG, "✓ Lock state cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing lock state", e)
            }
        }
    }
}

/**
 * Enhanced overlay view with hardware button interception
 */
class EnhancedOverlayView(
    context: Context,
    val overlay: OverlayData,
    private val onDismiss: (String) -> Unit,
    private val onAction: (String, String, Map<String, String>) -> Unit
) : View(context) {

    companion object {
        private const val TAG = "EnhancedOverlayView"
    }

    init {
        setupView()
    }

    private fun setupView() {
        // Set background based on lock type
        val backgroundColor = if (overlay.blockAllInteractions) {
            0xFF000000.toInt()  // Solid black for hard lock
        } else {
            0xCC000000.toInt()  // Semi-transparent for soft lock
        }
        setBackgroundColor(backgroundColor)

        // For hard lock: Consume all touch events
        if (overlay.blockAllInteractions) {
            setOnTouchListener { _, _ -> true }
        }
    }

    /**
     * Intercept hardware buttons for hard lock
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (overlay.blockAllInteractions) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_MENU -> {
                    Log.d(TAG, "Hardware button intercepted: $keyCode")
                    return true  // Consume event
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Intercept touch events for hard lock
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (overlay.blockAllInteractions) {
            return true  // Consume all touch events
        }
        return super.onTouchEvent(event)
    }
}

/**
 * Data class for overlay validation results
 */
data class OverlayValidationResult(
    val isValid: Boolean,
    val conflicts: List<String> = emptyList()
)

/**
 * Overlay state manager for tracking and persistence
 * Single source of truth for overlay and lock state persistence
 */
class OverlayStateManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayStateManager"
        private const val PREFS_NAME = "overlay_state_prefs"
        private const val KEY_LOCK_STATE = "current_lock_state"
        private const val KEY_ACTIVE_OVERLAYS = "active_overlays"
        private const val KEY_LAST_STATE_UPDATE = "last_state_update"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auditLog = IdentifierAuditLog(context)
    private val gson = com.google.gson.Gson()

    /**
     * Save lock state with timestamp
     */
    fun saveLockState(lockState: LockState) {
        try {
            synchronized(this) {
                prefs.edit().apply {
                    putString(KEY_LOCK_STATE, lockState.name)
                    putLong(KEY_LAST_STATE_UPDATE, System.currentTimeMillis())
                    apply()
                }
            }
            Log.d(TAG, "Lock state saved: $lockState")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lock state", e)
            auditLog.logIncident(
                type = "LOCK_STATE_SAVE_ERROR",
                severity = "MEDIUM",
                details = "Failed to save lock state: ${e.message}"
            )
        }
    }

    /**
     * Load lock state from persistence
     * Returns UNLOCKED if no state is persisted
     */
    fun loadLockState(): LockState {
        return try {
            synchronized(this) {
                val stateName = prefs.getString(KEY_LOCK_STATE, LockState.UNLOCKED.name)
                val lockState = LockState.valueOf(stateName ?: LockState.UNLOCKED.name)
                Log.d(TAG, "Lock state loaded: $lockState")
                lockState
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading lock state, defaulting to UNLOCKED", e)
            LockState.UNLOCKED
        }
    }

    /**
     * Get timestamp of last lock state update
     */
    fun getLastStateUpdateTime(): Long {
        return prefs.getLong(KEY_LAST_STATE_UPDATE, 0L)
    }

    /**
     * Save active overlays
     */
    fun saveActiveOverlays(overlays: List<OverlayData>) {
        try {
            synchronized(this) {
                val json = gson.toJson(overlays)
                prefs.edit().putString(KEY_ACTIVE_OVERLAYS, json).apply()
            }
            Log.d(TAG, "Active overlays saved: ${overlays.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving active overlays", e)
            auditLog.logIncident(
                type = "OVERLAY_SAVE_ERROR",
                severity = "MEDIUM",
                details = "Failed to save active overlays: ${e.message}"
            )
        }
    }

    /**
     * Load active overlays from persistence
     * Automatically filters expired overlays
     */
    fun loadActiveOverlays(): List<OverlayData> {
        return try {
            synchronized(this) {
                val json = prefs.getString(KEY_ACTIVE_OVERLAYS, null)
                if (json != null && json.isNotEmpty()) {
                    val overlays: Array<OverlayData> = gson.fromJson(
                        json,
                        Array<OverlayData>::class.java
                    )
                    val validOverlays = overlays.toList().filter { !it.isExpired() }
                    Log.d(TAG, "Active overlays loaded: ${validOverlays.size}/${overlays.size}")
                    validOverlays
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading active overlays", e)
            emptyList()
        }
    }

    /**
     * Clear all state
     */
    fun clearAllState() {
        try {
            synchronized(this) {
                prefs.edit().clear().apply()
            }
            Log.d(TAG, "All overlay state cleared")
            auditLog.logAction(
                "OVERLAY_STATE_CLEARED",
                "All overlay state cleared"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing overlay state", e)
        }
    }
}
