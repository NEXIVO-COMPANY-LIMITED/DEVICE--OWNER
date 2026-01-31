package com.example.deviceowner.security.enforcement

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

/**
 * App Whitelist Manager
 * Only allows specific apps to run
 * All other apps are hidden/blocked
 */
class AppWhitelistManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AppWhitelistManager"
    }
    
    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    
    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)
    
    /**
     * Set app whitelist - only these apps will be visible
     */
    fun setAppWhitelist(allowedPackages: List<String>): Boolean {
        return try {
            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "Cannot set app whitelist - not device owner")
                return false
            }
            
            Log.d(TAG, "🔒 Setting app whitelist (${allowedPackages.size} apps)...")
            
            // Always include our own app
            val whitelist = (allowedPackages + context.packageName).distinct()
            
            // Get all installed packages
            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledPackages(0)
            
            var hiddenCount = 0
            var visibleCount = 0
            
            for (packageInfo in installedPackages) {
                val packageName = packageInfo.packageName
                
                // Skip system packages that shouldn't be hidden
                if (isSystemPackage(packageName)) {
                    continue
                }
                
                val shouldBeVisible = whitelist.contains(packageName)
                
                try {
                    devicePolicyManager.setApplicationHidden(adminComponent, packageName, !shouldBeVisible)
                    
                    if (shouldBeVisible) {
                        visibleCount++
                    } else {
                        hiddenCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set visibility for $packageName: ${e.message}")
                }
            }
            
            Log.d(TAG, "✅ App whitelist applied:")
            Log.d(TAG, "   - Visible apps: $visibleCount")
            Log.d(TAG, "   - Hidden apps: $hiddenCount")
            Log.d(TAG, "   - Whitelist: ${whitelist.joinToString(", ")}")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting app whitelist", e)
            false
        }
    }
    
    /**
     * Add app to whitelist
     */
    fun addToWhitelist(packageName: String): Boolean {
        return try {
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, false)
            Log.d(TAG, "✓ Added to whitelist: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to whitelist: $packageName", e)
            false
        }
    }
    
    /**
     * Remove app from whitelist (hide it)
     */
    fun removeFromWhitelist(packageName: String): Boolean {
        return try {
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
            Log.d(TAG, "✓ Removed from whitelist: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from whitelist: $packageName", e)
            false
        }
    }
    
    /**
     * Clear whitelist (make all apps visible)
     */
    fun clearWhitelist(): Boolean {
        return try {
            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledPackages(0)
            
            for (packageInfo in installedPackages) {
                try {
                    devicePolicyManager.setApplicationHidden(adminComponent, packageInfo.packageName, false)
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
            
            Log.d(TAG, "✅ Whitelist cleared - all apps visible")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing whitelist", e)
            false
        }
    }
    
    /**
     * Check if package is a system package that shouldn't be hidden
     */
    private fun isSystemPackage(packageName: String): Boolean {
        val systemPackages = listOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.gms",
            "com.google.android.gsf"
        )
        
        return systemPackages.contains(packageName) || 
               packageName.startsWith("com.android.") ||
               packageName.startsWith("android.")
    }
}
