package com.example.deviceowner.state

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

/**
 * Lock State Validator
 * 
 * Validates lock state consistency and recovers from invalid states.
 * 
 * Features:
 * - State validation
 * - Issue detection
 * - Automatic recovery
 * - Logging
 */
class LockStateValidator(private val context: Context) {
    
    companion object {
        private const val TAG = "LockStateValidator"
    }
    
    private val stateManager = DeviceLockStateManager(context)
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, AdminReceiver::class.java)
    
    // Validate lock state consistency
    fun validateLockState(): ValidationResult {
        val details = stateManager.getLockDetails()
        val issues = mutableListOf<String>()
        
        Log.d(TAG, "🔍 Validating lock state...")
        
        // Check 1: Kiosk mode consistency
        val isKioskModeEnabled = try {
            dpm.isLockTaskPermitted(admin.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Could not check kiosk mode: ${e.message}")
            false
        }
        
        if (details.kioskModeActive && !isKioskModeEnabled) {
            issues.add("Kiosk mode marked active but not enabled in DPM")
        }
        
        // Check 2: Hard lock without kiosk mode
        if (details.state == LockState.HARD_LOCKED && 
            !details.kioskModeActive) {
            issues.add("Hard lock state without kiosk mode active")
        }
        
        // Check 3: Permanent lock with unlock reason
        if (details.permanent && details.reason == LockReason.PAYMENT_OVERDUE) {
            issues.add("Permanent lock with payment overdue reason (should be tamper or deactivation)")
        }
        
        // Check 4: Timestamp validity
        if (details.timestamp > System.currentTimeMillis()) {
            issues.add("Lock timestamp is in the future")
        }
        
        // Check 5: Deactivated state consistency
        if (details.state == LockState.DEACTIVATED && 
            !details.permanent) {
            issues.add("Deactivated state should be permanent")
        }
        
        // Check 6: Soft lock should not be permanent
        if (details.state == LockState.SOFT_LOCKED && 
            details.permanent) {
            issues.add("Soft lock should not be permanent")
        }
        
        Log.d(TAG, "✅ Validation complete. Issues found: ${issues.size}")
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            details = details
        )
    }
    
    // Recover from invalid state
    fun recoverFromInvalidState(): Boolean {
        val validation = validateLockState()
        
        if (validation.isValid) {
            Log.d(TAG, "✅ Lock state is valid")
            return true
        }
        
        Log.w(TAG, "⚠️ Lock state is invalid. Issues found:")
        validation.issues.forEach { Log.w(TAG, "   - $it") }
        
        // Attempt recovery
        try {
            when {
                validation.details.state == LockState.HARD_LOCKED &&
                !validation.details.kioskModeActive -> {
                    Log.i(TAG, "🔧 Recovering: Re-enabling kiosk mode...")
                    stateManager.updateLockState(
                        newState = LockState.HARD_LOCKED,
                        reason = validation.details.reason,
                        message = "Recovered from invalid state",
                        permanent = validation.details.permanent,
                        kioskModeActive = true
                    )
                    return true
                }
                
                validation.details.permanent && 
                validation.details.reason == LockReason.PAYMENT_OVERDUE -> {
                    Log.i(TAG, "🔧 Recovering: Correcting lock reason...")
                    stateManager.updateLockState(
                        newState = validation.details.state,
                        reason = LockReason.TAMPER_DETECTED,
                        message = "Recovered from invalid state",
                        permanent = true,
                        kioskModeActive = validation.details.kioskModeActive
                    )
                    return true
                }
                
                validation.details.state == LockState.DEACTIVATED &&
                !validation.details.permanent -> {
                    Log.i(TAG, "🔧 Recovering: Marking deactivated as permanent...")
                    stateManager.updateLockState(
                        newState = LockState.DEACTIVATED,
                        reason = validation.details.reason,
                        message = "Recovered from invalid state",
                        permanent = true,
                        kioskModeActive = validation.details.kioskModeActive
                    )
                    return true
                }
                
                validation.details.state == LockState.SOFT_LOCKED &&
                validation.details.permanent -> {
                    Log.i(TAG, "🔧 Recovering: Removing permanent flag from soft lock...")
                    stateManager.updateLockState(
                        newState = LockState.SOFT_LOCKED,
                        reason = validation.details.reason,
                        message = "Recovered from invalid state",
                        permanent = false,
                        kioskModeActive = validation.details.kioskModeActive
                    )
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Recovery failed: ${e.message}")
            return false
        }
        
        return false
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>,
        val details: DeviceLockStateManager.LockDetails
    )
}
