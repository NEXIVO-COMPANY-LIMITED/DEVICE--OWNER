package com.example.deviceowner.services

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.DeviceOwnerManager
import com.example.deviceowner.managers.RollbackManager
import com.example.deviceowner.managers.UpdateManager
import com.example.deviceowner.receivers.AdminReceiver
import kotlinx.coroutines.*
import java.io.File

/**
 * Service for silent update installation
 * Feature 4.11: Agent Updates & Rollback
 * 
 * Responsibilities:
 * - Install updates with Device Owner privilege
 * - Preserve app data during update
 * - Verify installation success
 * - Sync status to backend
 * - Handle installation failures with automatic rollback
 */
class UpdateInstallationService : Service() {
    
    private lateinit var updateManager: UpdateManager
    private lateinit var rollbackManager: RollbackManager
    private lateinit var deviceOwnerManager: DeviceOwnerManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        private const val TAG = "UpdateInstallationService"
        private const val INSTALLATION_TIMEOUT_MS = 300000L // 5 minutes
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "UpdateInstallationService created")
        
        updateManager = UpdateManager(this)
        rollbackManager = RollbackManager(this)
        deviceOwnerManager = DeviceOwnerManager(this)
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "UpdateInstallationService started")
        
        serviceScope.launch {
            processPendingUpdates()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Process pending updates
     */
    private suspend fun processPendingUpdates() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing pending updates...")
                
                val pendingUpdate = updateManager.getPendingUpdate()
                
                if (pendingUpdate != null) {
                    Log.d(TAG, "Found pending update: ${pendingUpdate.targetVersion}")
                    
                    // Backup current APK before installation
                    val currentVersion = updateManager.getCurrentVersion()
                    rollbackManager.backupCurrentAPK(currentVersion)
                    
                    // Install update
                    val installResult = installUpdate(pendingUpdate.filePath, pendingUpdate.targetVersion)
                    
                    if (installResult.success) {
                        Log.d(TAG, "✓ Update installed successfully")
                        updateManager.clearPendingUpdate()
                    } else {
                        Log.e(TAG, "✗ Update installation failed: ${installResult.error}")
                        
                        // Attempt automatic rollback
                        performAutomaticRollback(currentVersion)
                    }
                } else {
                    Log.d(TAG, "No pending updates")
                }
                
                // Process pending rollbacks
                val pendingRollback = rollbackManager.getPendingRollback()
                if (pendingRollback != null) {
                    Log.d(TAG, "Found pending rollback: ${pendingRollback.targetVersion}")
                    performRollback(pendingRollback.targetVersion)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing pending updates: ${e.message}", e)
            }
        }
    }
    
    /**
     * Install update with Device Owner privilege
     */
    private suspend fun installUpdate(filePath: String, targetVersion: String): InstallationResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Installing update: $targetVersion")
                Log.d(TAG, "  File: $filePath")
                
                val updateFile = File(filePath)
                if (!updateFile.exists()) {
                    Log.e(TAG, "Update file not found: $filePath")
                    return@withContext InstallationResult(
                        success = false,
                        error = "Update file not found"
                    )
                }
                
                // Check Device Owner privilege
                if (!deviceOwnerManager.isDeviceOwner()) {
                    Log.e(TAG, "App is not device owner - cannot install update")
                    return@withContext InstallationResult(
                        success = false,
                        error = "Not device owner"
                    )
                }
                
                // Install package using Device Owner privilege
                val installResult = installPackageAsDeviceOwner(updateFile)
                
                if (installResult) {
                    Log.d(TAG, "✓ Update installation initiated")
                    
                    // Wait for installation to complete
                    val verifyResult = verifyInstallation(targetVersion)
                    
                    if (verifyResult) {
                        Log.d(TAG, "✓ Installation verified")
                        InstallationResult(success = true)
                    } else {
                        Log.e(TAG, "✗ Installation verification failed")
                        InstallationResult(
                            success = false,
                            error = "Installation verification failed"
                        )
                    }
                } else {
                    Log.e(TAG, "✗ Installation failed")
                    InstallationResult(
                        success = false,
                        error = "Installation failed"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error installing update: ${e.message}", e)
                InstallationResult(
                    success = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * Install package as Device Owner
     */
    private fun installPackageAsDeviceOwner(apkFile: File): Boolean {
        return try {
            Log.d(TAG, "Installing package as Device Owner: ${apkFile.absolutePath}")
            
            // Use Device Owner privilege to install package
            // This would typically use PackageInstaller or similar mechanism
            // For now, we'll use a simplified approach
            
            val packageInstaller = packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            
            // Copy APK to session
            apkFile.inputStream().use { input ->
                session.openWrite("package", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Commit session
            val intent = Intent(this, UpdateInstallationService::class.java)
            val pendingIntent = android.app.PendingIntent.getService(
                this,
                sessionId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            session.close()
            
            Log.d(TAG, "✓ Package installation initiated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error installing package: ${e.message}", e)
            false
        }
    }
    
    /**
     * Verify installation success
     */
    private suspend fun verifyInstallation(targetVersion: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verifying installation: $targetVersion")
                
                // Wait for installation to complete (with timeout)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < INSTALLATION_TIMEOUT_MS) {
                    val currentVersion = updateManager.getCurrentVersion()
                    
                    if (currentVersion == targetVersion) {
                        Log.d(TAG, "✓ Installation verified: $currentVersion")
                        return@withContext true
                    }
                    
                    delay(1000) // Check every second
                }
                
                Log.e(TAG, "Installation verification timeout")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying installation: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Perform automatic rollback on installation failure
     */
    private suspend fun performAutomaticRollback(previousVersion: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Performing automatic rollback to: $previousVersion")
                
                val rollbackResult = rollbackManager.rollbackToVersion(previousVersion)
                
                if (rollbackResult.success) {
                    Log.d(TAG, "✓ Rollback initiated")
                    
                    // Install rollback APK
                    val installResult = installUpdate(
                        rollbackResult.backupPath!!,
                        previousVersion
                    )
                    
                    if (installResult.success) {
                        Log.d(TAG, "✓ Rollback completed successfully")
                        rollbackManager.markRollbackCompleted(previousVersion, true)
                    } else {
                        Log.e(TAG, "✗ Rollback installation failed")
                        rollbackManager.markRollbackCompleted(previousVersion, false)
                    }
                } else {
                    Log.e(TAG, "✗ Rollback failed: ${rollbackResult.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing automatic rollback: ${e.message}", e)
            }
        }
    }
    
    /**
     * Perform manual rollback
     */
    private suspend fun performRollback(targetVersion: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Performing manual rollback to: $targetVersion")
                
                val rollbackResult = rollbackManager.rollbackToVersion(targetVersion)
                
                if (rollbackResult.success) {
                    Log.d(TAG, "✓ Rollback initiated")
                    
                    // Install rollback APK
                    val installResult = installUpdate(
                        rollbackResult.backupPath!!,
                        targetVersion
                    )
                    
                    if (installResult.success) {
                        Log.d(TAG, "✓ Rollback completed successfully")
                        rollbackManager.markRollbackCompleted(targetVersion, true)
                    } else {
                        Log.e(TAG, "✗ Rollback installation failed")
                        rollbackManager.markRollbackCompleted(targetVersion, false)
                    }
                } else {
                    Log.e(TAG, "✗ Rollback failed: ${rollbackResult.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing manual rollback: ${e.message}", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "UpdateInstallationService destroyed")
    }
}

/**
 * Installation result
 */
data class InstallationResult(
    val success: Boolean,
    val error: String? = null
)
