package com.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.deviceowner.logging.AuditLogger
import com.deviceowner.manager.UpdateManager
import kotlinx.coroutines.*

/**
 * UpdateCheckService handles periodic update checking
 * Integrated with HeartbeatVerificationService
 * Feature 4.11: Agent Updates & Rollback
 */
class UpdateCheckService : Service() {
    
    private lateinit var updateManager: UpdateManager
    private lateinit var auditLogger: AuditLogger
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    companion object {
        private const val TAG = "UpdateCheckService"
        private const val UPDATE_CHECK_INTERVAL = 3600000L // 1 hour
    }
    
    override fun onCreate() {
        super.onCreate()
        updateManager = UpdateManager(this)
        auditLogger = AuditLogger(this)
        
        auditLogger.log(TAG, "UpdateCheckService created", emptyMap())
        
        // Start periodic update checking
        startUpdateChecking()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        auditLogger.log(TAG, "UpdateCheckService started", emptyMap())
        
        // Check for pending updates immediately
        checkForPendingUpdates()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Start periodic update checking
     */
    private fun startUpdateChecking() {
        scope.launch {
            while (isActive) {
                try {
                    checkForUpdates()
                    delay(UPDATE_CHECK_INTERVAL)
                } catch (e: Exception) {
                    auditLogger.error(TAG, "Update check error", e.message ?: "Unknown error")
                    delay(UPDATE_CHECK_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Check for available updates
     */
    private fun checkForUpdates() {
        updateManager.checkForUpdates { updateInfo ->
            if (updateInfo != null) {
                auditLogger.log(TAG, "Update available", mapOf(
                    "targetVersion" to updateInfo.targetVersion,
                    "updateType" to updateInfo.updateType,
                    "critical" to updateInfo.critical.toString()
                ))
                
                // For critical updates, queue immediately
                if (updateInfo.critical) {
                    updateManager.queueUpdate(updateInfo.targetVersion, "CRITICAL")
                }
            }
        }
    }
    
    /**
     * Check for pending updates and apply if offline
     */
    private fun checkForPendingUpdates() {
        updateManager.getPendingUpdates { pendingUpdates ->
            if (pendingUpdates.isNotEmpty()) {
                auditLogger.log(TAG, "Found pending updates", mapOf(
                    "count" to pendingUpdates.size.toString()
                ))
                
                // Process pending updates
                for (update in pendingUpdates) {
                    processPendingUpdate(update)
                }
            }
        }
    }
    
    /**
     * Process a pending update
     */
    private fun processPendingUpdate(updateInfo: com.deviceowner.models.UpdateInfo) {
        scope.launch {
            try {
                auditLogger.log(TAG, "Processing pending update", mapOf(
                    "targetVersion" to updateInfo.targetVersion,
                    "priority" to updateInfo.priority
                ))
                
                // Download update
                updateManager.downloadUpdate(
                    updateInfo.targetVersion,
                    onProgress = { progress ->
                        auditLogger.log(TAG, "Download progress", mapOf(
                            "targetVersion" to updateInfo.targetVersion,
                            "progress" to "$progress%"
                        ))
                    },
                    onComplete = { success, apkPath ->
                        if (success && apkPath != null) {
                            // Install update
                            updateManager.installUpdate(
                                apkPath,
                                updateInfo.targetVersion
                            ) { installSuccess, error ->
                                if (installSuccess) {
                                    auditLogger.log(TAG, "Update installed", mapOf(
                                        "targetVersion" to updateInfo.targetVersion
                                    ))
                                } else {
                                    auditLogger.error(TAG, "Update installation failed", error ?: "Unknown error")
                                    
                                    // Attempt rollback on failure
                                    attemptRollback(updateInfo.targetVersion)
                                }
                            }
                        } else {
                            auditLogger.error(TAG, "Update download failed", "Download incomplete")
                        }
                    }
                )
            } catch (e: Exception) {
                auditLogger.error(TAG, "Process pending update error", e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Attempt automatic rollback on update failure
     */
    private fun attemptRollback(failedVersion: String) {
        updateManager.getRollbackVersions { rollbackVersions ->
            if (rollbackVersions.isNotEmpty()) {
                val previousVersion = rollbackVersions.firstOrNull()
                if (previousVersion != null) {
                    auditLogger.warn(TAG, "Attempting automatic rollback", mapOf(
                        "failedVersion" to failedVersion,
                        "rollbackVersion" to previousVersion.version
                    ))
                    
                    updateManager.rollback(
                        previousVersion.version,
                        "Automatic rollback due to update failure"
                    ) { success, error ->
                        if (success) {
                            auditLogger.log(TAG, "Rollback successful", mapOf(
                                "rollbackVersion" to previousVersion.version
                            ))
                        } else {
                            auditLogger.error(TAG, "Rollback failed", error ?: "Unknown error")
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        updateManager.cleanup()
        auditLogger.log(TAG, "UpdateCheckService destroyed", emptyMap())
    }
}
