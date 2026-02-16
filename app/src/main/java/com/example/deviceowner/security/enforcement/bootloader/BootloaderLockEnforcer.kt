package com.example.deviceowner.security.enforcement.bootloader

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver
import com.example.deviceowner.control.RemoteDeviceControlManager

/**
 * BootloaderLockEnforcer - Ensures bootloader remains locked
 * 
 * Prevents:
 * - Bootloader unlock via fastboot
 * - Custom ROM installation
 * - Magisk/Xposed installation
 * - System partition modification
 */
class BootloaderLockEnforcer(private val context: Context) {
    
    companion object {
        private const val TAG = "BootloaderLockEnforcer"
    }
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, AdminReceiver::class.java)
    private val controlManager = RemoteDeviceControlManager(context)
    
    /**
     * Enforce bootloader lock
     */
    fun enforceBootloaderLock() {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not device owner - cannot enforce bootloader lock")
            return
        }
        
        try {
            Log.d(TAG, "🔒 Enforcing bootloader lock...")
            // Factory reset allowed – not restricted
            // 2. Disable safe boot (prevents recovery)
            dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_SAFE_BOOT)
            Log.d(TAG, "✓ Safe boot disabled")
            // 3. REMOVED: Disable USB debugging - allow developer options
            // 4. Monitor bootloader state
            startBootloaderMonitoring()
            Log.i(TAG, "✅ Bootloader lock enforced")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing bootloader lock: ${e.message}", e)
        }
    }
    
    /**
     * Monitor bootloader state and detect unlock / fastboot tampering.
     * When bootloader is unlocked (e.g. via fastboot), device is treated as security violation → hard lock (kiosk).
     */
    fun startBootloaderMonitoring() {
        Thread {
            var lastBootloaderState = getBootloaderState()
            
            while (true) {
                try {
                    val prefs = context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
                    val skipSecurityRestrictions = prefs.getBoolean("skip_security_restrictions", false)
                    
                    if (skipSecurityRestrictions) {
                        Log.d(TAG, "⏭️ Skipping bootloader check during registration")
                        Thread.sleep(30000)
                        continue
                    }
                    
                    // 1) Detect actual unlock: ro.boot.flash.locked / ro.boot.verifiedbootstate (e.g. after fastboot unlock)
                    if (isBootloaderUnlocked()) {
                        Log.e(TAG, "🚨 BOOTLOADER UNLOCKED - SECURITY VIOLATION (e.g. fastboot unlock)")
                        controlManager.applyHardLock(
                            reason = "Security violation: Bootloader is unlocked. Device locked.",
                            forceRestart = false,
                            forceFromServerOrMismatch = true
                        )
                        break
                    }
                    
                    // 2) Detect state change (e.g. different boot path / version after reboot)
                    val currentBootloaderState = getBootloaderState()
                    if (currentBootloaderState != lastBootloaderState && lastBootloaderState != "unknown") {
                        Log.e(TAG, "🚨 BOOTLOADER STATE CHANGED - UNLOCK/TAMPER DETECTED")
                        controlManager.applyHardLock(
                            reason = "Bootloader state changed. Security violation. Device locked.",
                            forceRestart = false,
                            forceFromServerOrMismatch = true
                        )
                        break
                    }
                    
                    lastBootloaderState = currentBootloaderState
                    Thread.sleep(15000) // Check every 15 seconds
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in bootloader monitoring: ${e.message}")
                }
            }
        }.start()
    }
    
    /**
     * True if bootloader is unlocked (e.g. via fastboot).
     * Uses same logic as DeviceOwnerManager: ro.boot.flash.locked and ro.boot.verifiedbootstate.
     */
    fun isBootloaderUnlocked(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java)
            val flashLocked = getMethod.invoke(null, "ro.boot.flash.locked") as? String ?: ""
            val verifiedBootState = getMethod.invoke(null, "ro.boot.verifiedbootstate") as? String ?: ""
            (flashLocked == "0" || verifiedBootState.equals("orange", ignoreCase = true))
        } catch (e: Exception) {
            Log.w(TAG, "Could not read bootloader state: ${e.message}")
            false
        }
    }
    
    /**
     * Get current bootloader identifier (version string) for change detection.
     */
    private fun getBootloaderState(): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            (method.invoke(null, "ro.bootloader") as? String) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
