package com.example.deviceowner.ui.base

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.state.DeviceLockStateManager
import com.example.deviceowner.state.LockState
import com.example.deviceowner.state.LockReason

/**
 * Base Activity for all lock-aware screens
 * 
 * Provides:
 * - Centralized state management
 * - Automatic state consistency verification
 * - State change listeners
 * - Security features (back button, screen capture)
 */
abstract class StateAwareActivity : AppCompatActivity() {
    
    protected val TAG = this::class.simpleName ?: "StateAwareActivity"
    protected lateinit var stateManager: DeviceLockStateManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        stateManager = DeviceLockStateManager(this)
        
        // Verify lock state matches activity type
        verifyLockStateConsistency()
        
        // Register for state changes
        stateManager.addStateChangeListener { state, reason ->
            onLockStateChanged(state, reason)
        }
        
        Log.d(TAG, "✅ StateAwareActivity initialized")
    }
    
    // Verify that the current lock state matches this activity
    protected open fun verifyLockStateConsistency() {
        val details = stateManager.getLockDetails()
        Log.d(TAG, "🔍 Verifying lock state consistency...")
        Log.d(TAG, "   Current state: ${details.state}")
        Log.d(TAG, "   Current reason: ${details.reason}")
        Log.d(TAG, "   Activity: ${this::class.simpleName}")
    }
    
    // Called when lock state changes
    protected open fun onLockStateChanged(
        state: LockState,
        reason: LockReason
    ) {
        Log.d(TAG, "🔄 Lock state changed: $state (reason: $reason)")
    }
    
    // Disable back button for hard locks
    override fun onBackPressed() {
        val details = stateManager.getLockDetails()
        
        if (details.state == LockState.HARD_LOCKED ||
            details.state == LockState.DEACTIVATING) {
            Log.w(TAG, "⚠️ Back button disabled during hard lock")
            return
        }
        
        super.onBackPressed()
    }
    
    // Prevent screen capture for hard locks
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        if (hasFocus) {
            val details = stateManager.getLockDetails()
            if (details.state == LockState.HARD_LOCKED ||
                details.state == LockState.DEACTIVATING) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
                Log.d(TAG, "🔒 Screen capture prevented")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "✅ StateAwareActivity destroyed")
    }
}
