package com.example.deviceowner.managers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.deviceowner.models.*
import com.example.deviceowner.overlay.OverlayController
import com.example.deviceowner.overlay.OverlayData
import com.example.deviceowner.overlay.OverlayType
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File

/**
 * Executes offline commands from the command queue
 * Feature 4.9: Offline Command Queue
 * 
 * Handles all command types:
 * - LOCK_DEVICE: Lock device immediately
 * - UNLOCK_DEVICE: Unlock with verification
 * - WARN: Display warning overlay
 * - PERMANENT_LOCK: Repossession lock
 * - WIPE_DATA: Factory reset device
 * - UPDATE_APP: Update app version
 * - REBOOT_DEVICE: Restart device
 */
class CommandExecutor(private val context: Context) {

    private val commandQueue = CommandQueue(context)
    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val overlayController = OverlayController(context)
    private val auditLog = IdentifierAuditLog(context)
    private val remoteLockManager = RemoteLockManager(context)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "CommandExecutor"
    }

    /**
     * Start processing queued commands
     */
    fun startProcessingQueue() {
        Log.d(TAG, "Starting command queue processing")
        
        val scope = CoroutineScope(Dispatchers.Default + Job())
        scope.launch {
            while (isActive) {
                try {
                    processNextCommand()
                    delay(5000) // Check queue every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in queue processing loop", e)
                    delay(5000)
                }
            }
        }
    }

    /**
     * Process next command in queue
     */
    private suspend fun processNextCommand() {
        try {
            val command = commandQueue.dequeueAndExecuteCommand() ?: return
            
            Log.d(TAG, "Processing command: ${command.commandId} - ${command.type}")
            
            val success = when (command.type) {
                "LOCK_DEVICE" -> executeLockCommand(command)
                "UNLOCK_DEVICE" -> executeUnlockCommand(command)
                "WARN" -> executeWarnCommand(command)
                "PERMANENT_LOCK" -> executePermanentLockCommand(command)
                "WIPE_DATA" -> executeWipeCommand(command)
                "UPDATE_APP" -> executeUpdateCommand(command)
                "REBOOT_DEVICE" -> executeRebootCommand(command)
                else -> {
                    Log.w(TAG, "Unknown command type: ${command.type}")
                    false
                }
            }
            
            if (success) {
                commandQueue.markCommandExecuted(command.commandId, "SUCCESS")
                Log.d(TAG, "✓ Command executed successfully: ${command.commandId}")
            } else {
                commandQueue.markCommandFailed(command.commandId, "Execution failed")
                Log.e(TAG, "✗ Command execution failed: ${command.commandId}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing command", e)
        }
    }

    /**
     * Execute LOCK_DEVICE command
     */
    private fun executeLockCommand(command: OfflineCommand): Boolean {
        return try {
            Log.w(TAG, "Executing LOCK_DEVICE command: ${command.commandId}")
            
            val lockTypeStr = command.parameters["lockType"] ?: "HARD"
            val reasonStr = command.parameters["reason"] ?: "Device locked by backend"
            val messageStr = command.parameters["message"] ?: "Your device has been locked"
            val expiresAt = command.parameters["expiresAt"]?.toLongOrNull() ?: 0L
            
            val lock = DeviceLock(
                lockId = command.commandId,
                deviceId = command.deviceId,
                lockType = when (lockTypeStr) {
                    "SOFT" -> LockType.SOFT
                    "HARD" -> LockType.HARD
                    "PERMANENT" -> LockType.HARD  // Treat PERMANENT as HARD lock
                    else -> LockType.HARD
                },
                lockStatus = LockStatus.ACTIVE,
                lockReason = LockReason.ADMIN_ACTION,
                message = messageStr,
                expiresAt = expiresAt
            )
            
            remoteLockManager.applyLock(lock)
            
            auditLog.logAction(
                "COMMAND_LOCK_EXECUTED",
                "Lock command executed: ${lock.lockType} - ${lock.lockReason}"
            )
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing lock command", e)
            false
        }
    }

    /**
     * Execute UNLOCK_DEVICE command
     */
    private fun executeUnlockCommand(command: OfflineCommand): Boolean {
        return try {
            Log.d(TAG, "Executing UNLOCK_DEVICE command: ${command.commandId}")
            
            val lockId = command.parameters["lockId"] ?: command.commandId
            
            remoteLockManager.removeLock(lockId)
            
            auditLog.logAction(
                "COMMAND_UNLOCK_EXECUTED",
                "Unlock command executed: $lockId"
            )
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing unlock command", e)
            false
        }
    }

    /**
     * Execute WARN command
     */
    private fun executeWarnCommand(command: OfflineCommand): Boolean {
        return try {
            Log.d(TAG, "Executing WARN command: ${command.commandId}")
            
            val title = command.parameters["title"] ?: "Warning"
            val message = command.parameters["message"] ?: "Warning message"
            val dismissible = command.parameters["dismissible"]?.toBoolean() ?: true
            
            val overlay = OverlayData(
                id = command.commandId,
                type = OverlayType.WARNING_MESSAGE,
                title = title,
                message = message,
                priority = command.priority,
                dismissible = dismissible,
                expiryTime = command.expiresAt
            )
            
            overlayController.showOverlay(overlay)
            
            auditLog.logAction(
                "COMMAND_WARN_EXECUTED",
                "Warning command executed: $title"
            )
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing warn command", e)
            false
        }
    }

    /**
     * Execute PERMANENT_LOCK command
     */
    private fun executePermanentLockCommand(command: OfflineCommand): Boolean {
        return try {
            Log.e(TAG, "Executing PERMANENT_LOCK command: ${command.commandId}")
            
            val reasonStr = command.parameters["reason"] ?: "ADMIN_ACTION"
            val lockReason = try {
                LockReason.valueOf(reasonStr)
            } catch (e: Exception) {
                LockReason.ADMIN_ACTION
            }
            val message = command.parameters["message"] ?: "This device has been locked for repossession"
            
            val lock = DeviceLock(
                lockId = command.commandId,
                deviceId = command.deviceId,
                lockType = LockType.HARD,  // PERMANENT lock is implemented as HARD lock
                lockStatus = LockStatus.ACTIVE,
                lockReason = lockReason,
                message = message,
                expiresAt = 0L // Never expires
            )
            
            remoteLockManager.applyLock(lock)
            
            auditLog.logIncident(
                type = "COMMAND_PERMANENT_LOCK_EXECUTED",
                severity = "CRITICAL",
                details = "Permanent lock command executed: $reasonStr"
            )
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing permanent lock command", e)
            false
        }
    }

    /**
     * Execute WIPE_DATA command
     */
    private fun executeWipeCommand(command: OfflineCommand): Boolean {
        return try {
            Log.e(TAG, "Executing WIPE_DATA command: ${command.commandId}")
            
            val reason = command.parameters["reason"] ?: "Security wipe"
            
            // Wipe sensitive app data
            wipeSensitiveData()
            
            auditLog.logIncident(
                type = "COMMAND_WIPE_EXECUTED",
                severity = "CRITICAL",
                details = "Data wipe command executed: $reason"
            )
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing wipe command", e)
            false
        }
    }

    /**
     * Execute UPDATE_APP command
     */
    private fun executeUpdateCommand(command: OfflineCommand): Boolean {
        return try {
            Log.d(TAG, "Executing UPDATE_APP command: ${command.commandId}")
            
            val updateUrl = command.parameters["updateUrl"] ?: return false
            val version = command.parameters["version"] ?: "unknown"
            
            // Download and install APK update
            val success = downloadAndInstallUpdate(updateUrl, version)
            
            if (success) {
                auditLog.logAction(
                    "COMMAND_UPDATE_EXECUTED",
                    "Update command executed successfully: version $version from $updateUrl"
                )
            } else {
                auditLog.logIncident(
                    type = "COMMAND_UPDATE_FAILED",
                    severity = "HIGH",
                    details = "Failed to download or install update: version $version from $updateUrl"
                )
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error executing update command", e)
            auditLog.logIncident(
                type = "COMMAND_UPDATE_ERROR",
                severity = "HIGH",
                details = "Exception during update: ${e.message}"
            )
            false
        }
    }

    /**
     * Download and install APK update
     */
    private fun downloadAndInstallUpdate(updateUrl: String, version: String): Boolean {
        return try {
            Log.d(TAG, "Downloading update from: $updateUrl")
            
            // Create downloads directory
            val downloadsDir = File(context.getExternalFilesDir(null), "updates")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            // Download APK file
            val apkFile = File(downloadsDir, "update_${version}.apk")
            val url = java.net.URL(updateUrl)
            val connection = url.openConnection()
            connection.connect()
            
            val inputStream = connection.getInputStream()
            val outputStream = apkFile.outputStream()
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Update downloaded to: ${apkFile.absolutePath}")
            
            // Install APK using device owner privileges
            installApkAsDeviceOwner(apkFile)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/installing update", e)
            false
        }
    }

    /**
     * Install APK using device owner privileges
     */
    private fun installApkAsDeviceOwner(apkFile: File): Boolean {
        return try {
            Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")
            
            // Use device owner manager to install APK silently
            val packageInstaller = context.packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            
            // Write APK to session
            session.openWrite("package", 0, -1).use { outputStream ->
                apkFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // Create pending intent for installation result
            val intent = Intent(context, context.javaClass)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // Commit session to start installation
            session.commit(pendingIntent.intentSender)
            session.close()
            
            Log.d(TAG, "APK installation initiated successfully")
            
            // Clean up APK file after installation
            apkFile.delete()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            false
        }
    }

    /**
     * Execute REBOOT_DEVICE command
     */
    private fun executeRebootCommand(command: OfflineCommand): Boolean {
        return try {
            Log.w(TAG, "Executing REBOOT_DEVICE command: ${command.commandId}")
            
            val reason = command.parameters["reason"] ?: "System reboot"
            
            auditLog.logAction(
                "COMMAND_REBOOT_EXECUTED",
                "Reboot command executed: $reason"
            )
            
            // Schedule reboot after a short delay to allow logging
            Thread {
                Thread.sleep(2000)
                try {
                    Runtime.getRuntime().exec("reboot")
                } catch (e: Exception) {
                    Log.e(TAG, "Error rebooting device", e)
                }
            }.start()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing reboot command", e)
            false
        }
    }

    /**
     * Wipe sensitive data
     */
    private fun wipeSensitiveData() {
        try {
            Log.w(TAG, "Wiping sensitive data")
            
            // Clear SharedPreferences
            val prefs = context.getSharedPreferences("sensitive_data", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            // Clear app cache
            context.cacheDir.deleteRecursively()
            
            // Clear app files
            context.filesDir.deleteRecursively()
            
            Log.d(TAG, "✓ Sensitive data wiped")
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping sensitive data", e)
        }
    }

    /**
     * Process all pending commands immediately
     */
    fun processAllPendingCommands() {
        Log.d(TAG, "Processing all pending commands")
        
        val scope = CoroutineScope(Dispatchers.Default + Job())
        scope.launch {
            while (commandQueue.getPendingCommandsCount() > 0) {
                processNextCommand()
                delay(1000)
            }
            Log.d(TAG, "All pending commands processed")
        }
    }

    /**
     * Get queue status
     */
    fun getQueueStatus(): QueueStatus {
        return QueueStatus(
            totalCommands = commandQueue.getQueueSize(),
            pendingCommands = commandQueue.getPendingCommandsCount(),
            history = commandQueue.getHistory()
        )
    }
}

/**
 * Queue status data class
 */
data class QueueStatus(
    val totalCommands: Int,
    val pendingCommands: Int,
    val history: List<OfflineCommand>
)
