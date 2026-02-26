package com.microspace.payo.control

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microspace.payo.receivers.AdminReceiver
import com.microspace.payo.ui.activities.lock.security.SecurityViolationActivity

/**
 * HardLockManager - Applies system-level hard locks to device
 */
class HardLockManager(private val context: Context) {
    
    companion object {
        private const val TAG = "HardLockManager"
        const val LOCK_PREF_NAME = "device_locks"
        const val LOCK_TYPE_KEY = "lock_type"
        const val LOCK_REASON_KEY = "lock_reason"
        const val LOCK_TIMESTAMP_KEY = "lock_timestamp"
        const val LOCK_SOURCE_KEY = "lock_source"
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    fun applyHardLock(
        reason: String,
        recommendation: String? = null,
        source: String = "local"
    ) {
        Log.w(TAG, "🔒 Applying HARD LOCK - Source: $source, Reason: $reason")
        
        try {
            saveLockState("hard", reason, source)
            
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                Log.e(TAG, "⚠️ Device admin not active - showing overlay only")
                showLockOverlay(reason, recommendation)
                return
            }
            
            Log.d(TAG, "🔐 Calling DevicePolicyManager.lockNow()")
            devicePolicyManager.lockNow()
            
            showLockOverlay(reason, recommendation)
            
            Log.i(TAG, "✅ Hard lock applied successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying hard lock: ${e.message}", e)
            showLockOverlay(reason, recommendation)
        }
    }
    
    private fun showLockOverlay(reason: String, recommendation: String?) {
        Log.d(TAG, "📱 Showing lock overlay")
        
        try {
            // Route to SecurityViolationActivity for security reasons
            val activityClass = SecurityViolationActivity::class.java

            val intent = Intent(context, activityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
                putExtra("is_locked", true)
                putExtra("lock_reason", reason)
                putExtra("recommendation", recommendation)
                putExtra("lock_type", RemoteDeviceControlManager.TYPE_TAMPER)
                putExtra("lock_timestamp", System.currentTimeMillis())
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing lock overlay: ${e.message}", e)
        }
    }
    
    fun isDeviceHardLocked(): Boolean {
        return getLockType() == "hard"
    }
    
    fun getLockType(): String? {
        val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(LOCK_TYPE_KEY, null)
    }
    
    fun getLockReason(): String? {
        val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(LOCK_REASON_KEY, null)
    }
    
    fun getLockTimestamp(): Long {
        val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getLong(LOCK_TIMESTAMP_KEY, 0L)
    }
    
    fun getLockSource(): String? {
        val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(LOCK_SOURCE_KEY, null)
    }
    
    private fun saveLockState(lockType: String, reason: String, source: String) {
        val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString(LOCK_TYPE_KEY, lockType)
            putString(LOCK_REASON_KEY, reason)
            putLong(LOCK_TIMESTAMP_KEY, System.currentTimeMillis())
            putString(LOCK_SOURCE_KEY, source)
            apply()
        }
    }
    
    fun clearLockState() {
        val sharedPref = context.getSharedPreferences(LOCK_PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            remove(LOCK_TYPE_KEY)
            remove(LOCK_REASON_KEY)
            remove(LOCK_TIMESTAMP_KEY)
            remove(LOCK_SOURCE_KEY)
            apply()
        }
    }
    
    fun releaseHardLock() {
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            clearLockState()
        }
    }
}
