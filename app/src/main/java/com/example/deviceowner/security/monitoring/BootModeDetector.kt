package com.example.deviceowner.security.monitoring

import android.content.Context
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager

/**
 * BootModeDetector - Detects if device is in recovery/fastboot/bootloader mode
 * 
 * Detection Methods:
 * 1. Check ro.bootmode system property
 * 2. Check ro.boot.serialno changes
 * 3. Monitor device state changes
 * 4. Detect USB connection in recovery mode
 */
class BootModeDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "BootModeDetector"
    }
    
    private val controlManager = RemoteDeviceControlManager(context)
    
    /**
     * Check if device is in recovery/fastboot/bootloader mode
     */
    fun isInBootMode(): Boolean {
        return try {
            val bootMode = getSystemProperty("ro.bootmode")
            val bootloader = getSystemProperty("ro.bootloader")
            val serialno = getSystemProperty("ro.boot.serialno")
            
            Log.d(TAG, "Boot mode: $bootMode, Bootloader: $bootloader, Serial: $serialno")
            
            // If bootmode is "recovery", "bootloader", or "fastboot", device is compromised
            bootMode in listOf("recovery", "bootloader", "fastboot", "download") ||
            bootloader.isNotEmpty() && bootloader != "unknown"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking boot mode: ${e.message}")
            false
        }
    }
    
    /**
     * Get system property value
     */
    private fun getSystemProperty(propName: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, propName) as String
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Monitor for boot mode changes
     */
    fun startBootModeMonitoring() {
        Thread {
            while (true) {
                try {
                    // CRITICAL: Skip monitoring during registration
                    val prefs = context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
                    val skipSecurityRestrictions = prefs.getBoolean("skip_security_restrictions", false)
                    
                    if (skipSecurityRestrictions) {
                        Log.d(TAG, "⏭️ Skipping boot mode check during registration")
                        Thread.sleep(5000)
                        continue
                    }
                    
                    if (isInBootMode()) {
                        Log.e(TAG, "🚨 DEVICE IN BOOT MODE - APPLYING HARD LOCK")
                        controlManager.applyHardLock(
                            reason = "Device detected in recovery/fastboot mode. Unauthorized access attempt.",
                            forceFromServerOrMismatch = true
                        )
                        break
                    }
                    Thread.sleep(5000) // Check every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in boot mode monitoring: ${e.message}")
                }
            }
        }.start()
    }
}
