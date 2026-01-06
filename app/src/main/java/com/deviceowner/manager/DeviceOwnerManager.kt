package com.deviceowner.manager

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.deviceowner.logging.StructuredLogger
import java.io.File

/**
 * DeviceOwnerManager handles Device Owner operations
 */
class DeviceOwnerManager(private val context: Context) {
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val logger = StructuredLogger(context)
    
    companion object {
        private const val TAG = "DeviceOwnerManager"
        private var instance: DeviceOwnerManager? = null
        
        fun getInstance(context: Context): DeviceOwnerManager {
            return instance ?: DeviceOwnerManager(context).also { instance = it }
        }
    }
    
    /**
     * Check if app is Device Owner
     */
    fun isDeviceOwner(): Boolean {
        return try {
            val adminComponent = ComponentName(context, context.packageName)
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get logger instance
     */
    fun getLogger(): StructuredLogger {
        return logger
    }
    
    /**
     * Lock device
     */
    fun lockDevice(): Boolean {
        return try {
            if (!isDeviceOwner()) {
                return false
            }
            devicePolicyManager.lockNow()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Install package silently using Device Owner privilege
     */
    fun installPackageSilently(apkPath: String): Boolean {
        return try {
            if (!isDeviceOwner()) {
                return false
            }
            
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                return false
            }
            
            // Use PackageInstaller for silent installation
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            
            // Write APK to session
            apkFile.inputStream().use { input ->
                session.openWrite("app.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Create intent for installation callback
            val intent = Intent(context, context.javaClass)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Commit session
            session.commit(pendingIntent.intentSender)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Uninstall package silently using Device Owner privilege
     */
    fun uninstallPackageSilently(packageName: String): Boolean {
        return try {
            if (!isDeviceOwner()) {
                return false
            }
            
            val packageInstaller = context.packageManager.packageInstaller
            
            // Create intent for uninstall callback
            val intent = Intent(context, context.javaClass)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
