package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.CommandExecutor
import com.example.deviceowner.managers.CommandQueue
import kotlinx.coroutines.*

/**
 * Service for managing offline command queue processing
 * Feature 4.9: Offline Command Queue
 * 
 * - Processes queued commands periodically
 * - Survives app crashes and restarts
 * - Ensures commands execute even without internet
 */
class CommandQueueService : Service() {

    private lateinit var commandQueue: CommandQueue
    private lateinit var commandExecutor: CommandExecutor
    private var processingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val TAG = "CommandQueueService"
        private const val PROCESSING_INTERVAL = 5000L // Check queue every 5 seconds
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CommandQueueService created")
        
        commandQueue = CommandQueue(this)
        commandExecutor = CommandExecutor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CommandQueueService started")
        
        // Start processing queue
        startQueueProcessing()
        
        // Return START_STICKY to ensure service restarts if killed
        return START_STICKY
    }

    /**
     * Start processing command queue
     */
    private fun startQueueProcessing() {
        if (processingJob?.isActive == true) {
            Log.d(TAG, "Queue processing already running")
            return
        }
        
        processingJob = serviceScope.launch {
            Log.d(TAG, "Queue processing started")
            
            try {
                while (isActive) {
                    try {
                        val queueSize = commandQueue.getQueueSize()
                        val pendingCount = commandQueue.getPendingCommandsCount()
                        
                        if (pendingCount > 0) {
                            Log.d(TAG, "Processing queue: $pendingCount pending commands out of $queueSize total")
                            
                            // Process one command
                            val command = commandQueue.dequeueAndExecuteCommand()
                            if (command != null) {
                                Log.d(TAG, "Executing command: ${command.commandId} - ${command.type}")
                                
                                // Execute command
                                val success = executeCommand(command)
                                
                                if (success) {
                                    commandQueue.markCommandExecuted(command.commandId, "SUCCESS")
                                    Log.d(TAG, "✓ Command executed: ${command.commandId}")
                                } else {
                                    commandQueue.markCommandFailed(command.commandId, "Execution failed")
                                    Log.e(TAG, "✗ Command failed: ${command.commandId}")
                                }
                            }
                        }
                        
                        delay(PROCESSING_INTERVAL)
                    } catch (e: CancellationException) {
                        // Expected when service is destroyed, don't log as error
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in queue processing", e)
                        delay(PROCESSING_INTERVAL)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Queue processing cancelled")
            }
        }
    }

    /**
     * Execute a command
     */
    private suspend fun executeCommand(command: com.example.deviceowner.managers.OfflineCommand): Boolean {
        return try {
            when (command.type) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get or create RemoteLockManager
     */
    private fun getRemoteLockManager(): com.example.deviceowner.managers.RemoteLockManager {
        return com.example.deviceowner.managers.RemoteLockManager(this)
    }
    
    /**
     * Get or create OverlayController
     */
    private fun getOverlayController(): com.example.deviceowner.overlay.OverlayController {
        return com.example.deviceowner.overlay.OverlayController(this)
    }

    /**
     * Execute LOCK_DEVICE command
     */
    private fun executeLockCommand(command: com.example.deviceowner.managers.OfflineCommand): Boolean {
        return try {
            Log.w(TAG, "Executing LOCK_DEVICE: ${command.commandId}")
            
            val lockTypeStr = command.parameters["lockType"] ?: "HARD"
            val message = command.parameters["message"] ?: "Your device has been locked"
            
            val remoteLockManager = com.example.deviceowner.managers.RemoteLockManager(this)
            val lock = com.example.deviceowner.models.DeviceLock(
                lockId = command.commandId,
                deviceId = command.deviceId,
                lockType = when (lockTypeStr) {
                    "SOFT" -> com.example.deviceowner.models.LockType.SOFT
                    "HARD" -> com.example.deviceowner.models.LockType.HARD
                    "PERMANENT" -> com.example.deviceowner.models.LockType.PERMANENT
                    else -> com.example.deviceowner.models.LockType.HARD
                },
                lockStatus = com.example.deviceowner.models.LockStatus.ACTIVE,
                lockReason = com.example.deviceowner.models.LockReason.ADMIN_ACTION,
                message = message,
                expiresAt = command.expiresAt
            )
            
            remoteLockManager.applyLock(lock)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing lock command", e)
            false
        }
    }

    /**
     * Execute UNLOCK_DEVICE command
     */
    private fun executeUnlockCommand(command: com.example.deviceowner.managers.OfflineCommand): Boolean {
        return try {
            Log.d(TAG, "Executing UNLOCK_DEVICE: ${command.commandId}")
            
            val lockId = command.parameters["lockId"] ?: command.commandId
            val remoteLockManager = com.example.deviceowner.managers.RemoteLockManager(this)
            remoteLockManager.removeLock(lockId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing unlock command", e)
            false
        }
    }

    /**
     * Execute WARN command
     */
    private fun executeWarnCommand(command: com.example.deviceowner.managers.OfflineCommand): Boolean {
        return try {
            Log.d(TAG, "Executing WARN: ${command.commandId}")
            
            val title = command.parameters["title"] ?: "Warning"
            val message = command.parameters["message"] ?: "Warning message"
            val dismissible = command.parameters["dismissible"]?.toBoolean() ?: true
            
            val overlayController = com.example.deviceowner.overlay.OverlayController(this)
            val overlay = com.example.deviceowner.overlay.OverlayData(
                id = command.commandId,
                type = com.example.deviceowner.overlay.OverlayType.WARNING_MESSAGE,
                title = title,
                message = message,
                priority = command.priority,
                dismissible = dismissible,
                expiryTime = command.expiresAt
            )
            
            overlayController.showOverlay(overlay)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing warn command", e)
            false
        }
    }

    /**
     * Execute PERMANENT_LOCK command
     */
    private fun executePermanentLockCommand(command: com.example.deviceowner.managers.OfflineCommand): Boolean {
        return try {
            Log.e(TAG, "Executing PERMANENT_LOCK: ${command.commandId}")
            
            val message = command.parameters["message"] ?: "This device has been locked"
            
            val remoteLockManager = com.example.deviceowner.managers.RemoteLockManager(this)
            val lock = com.example.deviceowner.models.DeviceLock(
                lockId = command.commandId,
                deviceId = command.deviceId,
                lockType = com.example.deviceowner.models.LockType.PERMANENT,
                lockStatus = com.example.deviceowner.models.LockStatus.ACTIVE,
                lockReason = com.example.deviceowner.models.LockReason.ADMIN_ACTION,
                message = message,
                expiresAt = 0L
            )
            
            remoteLockManager.applyLock(lock)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing permanent lock command", e)
            false
        }
    }

    /**
     * Execute WIPE_DATA command
     */
    private fun executeWipeCommand(command: com.example.deviceowner.managers.OfflineCommand): Boolean {
        return try {
            Log.e(TAG, "Executing WIPE_DATA: ${command.commandId}")
            
            // Wipe sensitive data
            wipeSensitiveData()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing wipe command", e)
            false
        }
    }

    /**
     * Execute UPDATE_APP command
     */
    private fun executeUpdateCommand(command: com.example.deviceowner.managers.OfflineCommand): Boolean {
        return try {
            Log.d(TAG, "Executing UPDATE_APP: ${command.commandId}")
            
            // TODO: Implement app update mechanism
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing update command", e)
            false
        }
    }

    /**
     * Execute REBOOT_DEVICE command
     */
    private fun executeRebootCommand(command: com.example.deviceowner.managers.OfflineCommand): Boolean {
        return try {
            Log.w(TAG, "Executing REBOOT_DEVICE: ${command.commandId}")
            
            Thread {
                Thread.sleep(2000)
                try {
                    Runtime.getRuntime().exec("reboot")
                } catch (e: Exception) {
                    Log.e(TAG, "Error rebooting", e)
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
            
            val prefs = getSharedPreferences("sensitive_data", MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            cacheDir.deleteRecursively()
            filesDir.deleteRecursively()
            
            Log.d(TAG, "✓ Sensitive data wiped")
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping data", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CommandQueueService destroyed")
        processingJob?.cancel()
        serviceScope.cancel()
    }
}
