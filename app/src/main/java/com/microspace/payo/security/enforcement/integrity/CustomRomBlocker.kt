package com.microspace.payo.security.enforcement.integrity

import android.content.Context
import android.util.Log
import com.microspace.payo.control.RemoteDeviceControlManager
import java.io.File

/**
 * CustomRomBlocker - Detects and prevents custom ROM installation
 * 
 * Detects:
 * - Magisk installation
 * - Xposed framework
 * - Custom recovery
 * - Root access
 * - System partition modifications
 */
class CustomRomBlocker(private val context: Context) {
    
    companion object {
        private const val TAG = "CustomRomBlocker"
    }
    
    private val controlManager = RemoteDeviceControlManager(context)
    
    /**
     * Start monitoring for custom ROM/root indicators
     */
    fun startCustomRomMonitoring() {
        Thread {
            while (true) {
                try {
                    // CRITICAL: Skip monitoring during registration
                    val prefs = context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
                    val skipSecurityRestrictions = prefs.getBoolean("skip_security_restrictions", false)
                    
                    if (skipSecurityRestrictions) {
                        Log.d(TAG, "â­ï¸ Skipping custom ROM check during registration")
                        Thread.sleep(60000)
                        continue
                    }
                    
                    if (isCustomRomDetected()) {
                        Log.e(TAG, "ðŸš¨ CUSTOM ROM DETECTED - APPLYING HARD LOCK")
                        controlManager.applyHardLock(
                            reason = "Custom ROM or root access detected. Device locked for security.",
                            forceFromServerOrMismatch = true
                        )
                        break
                    }
                    
                    Thread.sleep(60000) // Check every 60 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in custom ROM monitoring: ${e.message}")
                }
            }
        }.start()
    }
    
    /**
     * Detect if custom ROM is installed
     */
    private fun isCustomRomDetected(): Boolean {
        return isMagiskInstalled() ||
               isXposedInstalled() ||
               isRootAccessAvailable() ||
               isCustomRecoveryInstalled() ||
               isBuildPropModified()
    }
    
    /**
     * Check for Magisk installation
     */
    private fun isMagiskInstalled(): Boolean {
        return try {
            File("/data/adb/magisk").exists() ||
            File("/data/adb/magisk.db").exists() ||
            File("/system/xbin/su").exists() ||
            File("/system/bin/su").exists()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check for Xposed framework
     */
    private fun isXposedInstalled(): Boolean {
        return try {
            File("/data/data/de.robv.android.xposed.installer").exists() ||
            File("/system/framework/XposedBridge.jar").exists()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check for root access
     */
    private fun isRootAccessAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.inputStream.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check for custom recovery
     */
    private fun isCustomRecoveryInstalled(): Boolean {
        return try {
            val recoveryPath = File("/recovery")
            recoveryPath.exists() && recoveryPath.isDirectory
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if build.prop was modified
     */
    private fun isBuildPropModified(): Boolean {
        return try {
            val buildProp = File("/system/build.prop")
            val lastModified = buildProp.lastModified()
            val bootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
            
            // If build.prop was modified after boot, it's suspicious
            lastModified > bootTime
        } catch (e: Exception) {
            false
        }
    }
}




