package com.microspace.payo.state

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Lock State Transition Manager
 * 
 * Handles smooth transitions between lock states.
 * Prevents rapid state changes from causing UI glitches.
 * 
 * Features:
 * - Transition queue
 * - Debouncing
 * - Callbacks
 * - Logging
 */
class LockStateTransitionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TransitionManager"
        private const val TRANSITION_DEBOUNCE_MS = 500L
    }
    
    private val stateManager = DeviceLockStateManager(context)
    private val transitionQueue = mutableListOf<StateTransition>()
    private var isProcessingTransition = false
    private var lastTransitionTime = 0L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    data class StateTransition(
        val fromState: LockState,
        val toState: LockState,
        val reason: LockReason,
        val callback: (() -> Unit)? = null
    )
    
    // Queue a state transition
    fun queueTransition(
        toState: LockState,
        reason: LockReason,
        callback: (() -> Unit)? = null
    ) {
        val currentState = stateManager.getLockState()
        
        if (currentState == toState) {
            Log.d(TAG, "⏭️ Skipping transition - already in state: $toState")
            return
        }
        
        val transition = StateTransition(currentState, toState, reason, callback)
        transitionQueue.add(transition)
        
        Log.d(TAG, "📋 Transition queued: $currentState → $toState")
        Log.d(TAG, "   Queue size: ${transitionQueue.size}")
        
        processNextTransition()
    }
    
    // Process transitions one at a time
    private fun processNextTransition() {
        if (isProcessingTransition || transitionQueue.isEmpty()) {
            return
        }
        
        val now = System.currentTimeMillis()
        if (now - lastTransitionTime < TRANSITION_DEBOUNCE_MS) {
            Log.d(TAG, "⏳ Debouncing transition...")
            scope.launch {
                delay(TRANSITION_DEBOUNCE_MS)
                processNextTransition()
            }
            return
        }
        
        isProcessingTransition = true
        val transition = transitionQueue.removeAt(0)
        
        Log.d(TAG, "▶️ Processing transition: ${transition.fromState} → ${transition.toState}")
        
        // Apply transition
        stateManager.updateLockState(
            newState = transition.toState,
            reason = transition.reason,
            message = "Transitioning to ${transition.toState}",
            permanent = transition.toState == LockState.DEACTIVATED
        )
        
        // Call callback
        try {
            transition.callback?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Callback error: ${e.message}")
        }
        
        lastTransitionTime = now
        isProcessingTransition = false
        
        Log.d(TAG, "✅ Transition completed")
        
        // Process next transition
        if (transitionQueue.isNotEmpty()) {
            scope.launch {
                delay(100)
                processNextTransition()
            }
        }
    }
    
    // Get queue size
    fun getQueueSize(): Int = transitionQueue.size
    
    // Clear queue
    fun clearQueue() {
        transitionQueue.clear()
        Log.d(TAG, "🗑️ Transition queue cleared")
    }
    
    // Cleanup
    fun cleanup() {
        scope.cancel()
        transitionQueue.clear()
        Log.d(TAG, "✅ TransitionManager cleaned up")
    }
}
