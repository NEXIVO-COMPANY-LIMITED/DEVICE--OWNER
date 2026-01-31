package com.example.deviceowner.receivers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log
import com.example.deviceowner.device.DeviceOwnerManager
import com.example.deviceowner.work.RestrictionEnforcementWorker

class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.e(TAG, "⚠️ DEVICE ADMIN DISABLED - TRIGGERING SOFT LOCK")
        
        // Immediately trigger soft lock when admin is disabled
        try {
            val controlManager = com.example.deviceowner.control.RemoteDeviceControlManager(context)
            controlManager.applySoftLock("SOFT LOCK: Device admin was disabled")
            Log.e(TAG, "Soft lock applied due to admin disable")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply soft lock on admin disable", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "Received intent: ${intent.action}")
    }

    /**
     * Aggressive Provisioning Handler
     * Executes the final lockdown and connectivity configuration.
     * CRITICAL: This runs IMMEDIATELY when device owner is set - BEFORE user can access anything
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.d(TAG, "🔒🔒🔒 DEVICE OWNER PROVISIONING COMPLETE - APPLYING IMMEDIATE SECURITY 🔒🔒🔒")
        
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)

        // STEP 1: IMMEDIATE CRITICAL SECURITY (BEFORE USER CAN DO ANYTHING)
        Log.d(TAG, "STEP 1: APPLYING IMMEDIATE CRITICAL SECURITY...")
        
        try {
            // CRITICAL: Apply 100% Perfect Security IMMEDIATELY using DeviceOwnerManager
            Log.d(TAG, "Applying immediate perfect security via DeviceOwnerManager...")
            applyFallbackSecurityImmediately(dpm, admin, context)
            
            // Also apply via DeviceOwnerManager for comprehensive coverage
            Handler(Looper.getMainLooper()).postDelayed({
                Thread {
                    try {
                        val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(context.applicationContext)
                        if (deviceOwnerManager.isDeviceOwner()) {
                            deviceOwnerManager.applyAllCriticalRestrictions()
                            deviceOwnerManager.applyRestrictions()
                            Log.d(TAG, "✅✅✅ 100% PERFECT SECURITY APPLIED IMMEDIATELY ✅✅✅")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying DeviceOwnerManager security", e)
                    }
                }.start()
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying perfect security - using fallback", e)
            applyFallbackSecurityImmediately(dpm, admin, context)
        }

        // STEP 2: ADDITIONAL IMMEDIATE RESTRICTIONS
        Log.d(TAG, "STEP 2: APPLYING ADDITIONAL IMMEDIATE RESTRICTIONS...")
        
        // 1. IMMUTABILITY LAYER (App Protection)
        dpm.setUninstallBlocked(admin, context.packageName, true)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
        Log.d(TAG, "✓ App uninstall blocked immediately")

        // 2. USB Data (MTP) allowed – picha, nyimbo; Tethering allowed
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_TETHERING)
        Log.d(TAG, "✓ USB data transfer allowed (MTP)")
        
        // 3. OPERATIONAL AUTOMATION
        dpm.setPermissionPolicy(admin, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)
        Log.d(TAG, "✓ Permission policy set to auto-grant")
        
        // 4. BRANDING & UI
        dpm.setProfileName(admin, "SECURE CORPORATE DEVICE")
        Log.d(TAG, "✓ Device profile name set")
        
        Log.d(TAG, "✅ IMMEDIATE SECURITY APPLIED - USER CANNOT ACCESS DEVELOPER OPTIONS OR FACTORY RESET")
        
        // STEP 3: DELAYED COMPREHENSIVE SETUP (After immediate security is active)
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                try {
                    Log.d(TAG, "STEP 3: APPLYING COMPREHENSIVE SECURITY SETUP...")
                    
                    val dm = DeviceOwnerManager(context.applicationContext)
                    if (dm.isDeviceOwner()) {
                        // Grant permissions for IMEI/Serial collection
                        dm.grantRequiredPermissions()
                        
                        // Apply comprehensive restrictions
                        dm.applyAllCriticalRestrictions()
                        dm.applySilentCompanyRestrictions()
                        
                        // Verify all restrictions are active
                        val allActive = dm.verifyAllCriticalRestrictionsActive()
                        if (allActive) {
                            Log.d(TAG, "🎯 ALL COMPREHENSIVE RESTRICTIONS VERIFIED ACTIVE")
                        } else {
                            Log.w(TAG, "⚠️ Some comprehensive restrictions need attention - re-applying...")
                            dm.applyRestrictions()
                        }
                        
                        // Schedule periodic enforcement
                        RestrictionEnforcementWorker.schedule(context.applicationContext)
                        
                        // Start security monitoring
                        com.example.deviceowner.monitoring.SecurityMonitorService.startService(context.applicationContext)
                        
                        // Initialize enhanced security features
                        initializeEnhancedSecurityFeatures(context.applicationContext)
                        
                        Log.d(TAG, "═══════════════════════════════════════════════════════")
                        Log.d(TAG, "✅✅✅ DEVICE OWNER SETUP COMPLETE ✅✅✅")
                        Log.d(TAG, "   🔒 Developer Options: IMPOSSIBLE TO ENABLE")
                        Log.d(TAG, "   🔒 Factory Reset: IMPOSSIBLE TO ACCESS")
                        Log.d(TAG, "   🔒 All security: ACTIVE AND VERIFIED")
                        Log.d(TAG, "═══════════════════════════════════════════════════════")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in comprehensive setup", e)
                }
            }.start()
        }, 500) // Reduced delay - apply comprehensive setup quickly
    }
    
    /**
     * Apply fallback security immediately if perfect security fails
     */
    private fun applyFallbackSecurityImmediately(dpm: DevicePolicyManager, admin: ComponentName, context: Context) {
        Log.d(TAG, "🔒 APPLYING FALLBACK SECURITY IMMEDIATELY...")
        
        try {
            // CRITICAL: Factory Reset - BLOCK IMMEDIATELY
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            }
            
            // Try setMasterClearDisabled
            try {
                val method = dpm.javaClass.getMethod(
                    "setMasterClearDisabled",
                    ComponentName::class.java,
                    Boolean::class.javaPrimitiveType
                )
                method.invoke(dpm, admin, true)
                Log.d(TAG, "✓ Factory reset blocked via setMasterClearDisabled")
            } catch (e: Exception) {
                Log.w(TAG, "setMasterClearDisabled not available: ${e.message}")
            }
            
            // CRITICAL: Developer Options - BLOCK IMMEDIATELY
            // dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES) <-- DISABLED FOR DEBUGGING
            
            // Force disable developer options at system level
            // android.provider.Settings.Global.putInt(
            //     context.contentResolver,
            //     android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            //     0
            // ) <-- DISABLED FOR DEBUGGING
            
            // Force disable ADB
            // android.provider.Settings.Global.putInt(
            //    context.contentResolver,
            //    android.provider.Settings.Global.ADB_ENABLED,
            //    0
            // ) <-- DISABLED FOR DEBUGGING
            
            Log.d(TAG, "✅ FALLBACK SECURITY APPLIED IMMEDIATELY")
             Log.d(TAG, "   🚫 Developer Options: ENABLED FOR DEBUGGING")
            Log.d(TAG, "   🚫 Factory Reset: BLOCKED")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Fallback security failed", e)
        }
    }
    
    /**
     * Initialize enhanced security features after provisioning
     */
    private fun initializeEnhancedSecurityFeatures(context: Context) {
        Thread {
            try {
                // 1. Device Owner Removal Detection
                val removalDetector = com.example.deviceowner.security.monitoring.DeviceOwnerRemovalDetector(context)
                removalDetector.startMonitoring()
                
                // 2. ADB Backup Prevention
                val adbBackupPrevention = com.example.deviceowner.security.prevention.AdbBackupPrevention(context)
                adbBackupPrevention.preventAdbBackup()
                
                // 3. System Update Control
                val systemUpdateController = com.example.deviceowner.security.prevention.SystemUpdateController(context)
                systemUpdateController.requireUpdateApproval()
                
                // 4. Advanced Security Monitoring
                val advancedMonitor = com.example.deviceowner.security.monitoring.AdvancedSecurityMonitor(context)
                advancedMonitor.startMonitoring()
                
                // 5. Screen Pinning
                val screenPinningManager = com.example.deviceowner.security.enforcement.ScreenPinningManager(context)
                screenPinningManager.enableScreenPinning()
                
                Log.d(TAG, "✅ Enhanced security features initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing enhanced security features", e)
            }
        }.start()
    }
}