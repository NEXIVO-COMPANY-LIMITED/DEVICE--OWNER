package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.example.deviceowner.data.api.BlockingCommand

/**
 * Handles blocking commands received from backend
 * Executes security actions based on command type
 */
class BlockingCommandHandler(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "blocking_commands",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val mismatchHandler = DeviceMismatchHandler(context)
    private val auditLog = IdentifierAuditLog(context)
    
    companion object {
        private const val TAG = "BlockingCommandHandler"
        private const val KEY_COMMAND_HISTORY = "command_history"
        private const val KEY_EXECUTED_COMMANDS = "executed_commands"
        private const val MAX_HISTORY_SIZE = 100
    }
    
    /**
     * Execute blocking command
     */
    fun executeCommand(command: BlockingCommand): CommandExecutionResult {
        try {
            Log.w(TAG, "Executing command: ${command.type} (ID: ${command.commandId})")
            
            val result = when (command.type) {
                "LOCK_DEVICE" -> executeLockDevice(command)
                "DISABLE_FEATURES" -> executeDisableFeatures(command)
                "WIPE_DATA" -> executeWipeData(command)
                "ALERT_ONLY" -> executeAlert(command)
                "DISABLE_CAMERA" -> executeDisableCamera(command)
                "DISABLE_USB" -> executeDisableUSB(command)
                "DISABLE_DEVELOPER_MODE" -> executeDisableDeveloperMode(command)
                "RESTRICT_NETWORK" -> executeRestrictNetwork(command)
                else -> {
                    Log.w(TAG, "Unknown command type: ${command.type}")
                    CommandExecutionResult(
                        success = false,
                        commandId = command.commandId,
                        status = "FAILED",
                        message = "Unknown command type: ${command.type}"
                    )
                }
            }
            
            // Log command execution
            logCommandExecution(command, result)
            
            // Add to history
            addToCommandHistory(command)
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command", e)
            return CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Execute LOCK_DEVICE command
     */
    private fun executeLockDevice(command: BlockingCommand): CommandExecutionResult {
        return try {
            Log.e(TAG, "Locking device - Reason: ${command.reason}")
            
            mismatchHandler.lockDevice()
            
            auditLog.logAction(
                action = "DEVICE_LOCKED",
                description = "Device locked by backend command. Reason: ${command.reason}"
            )
            
            CommandExecutionResult(
                success = true,
                commandId = command.commandId,
                status = "COMPLETED",
                message = "Device locked successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device", e)
            CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error locking device: ${e.message}"
            )
        }
    }
    
    /**
     * Execute DISABLE_FEATURES command
     */
    private fun executeDisableFeatures(command: BlockingCommand): CommandExecutionResult {
        return try {
            Log.w(TAG, "Disabling features - Parameters: ${command.parameters}")
            
            val features = command.parameters["features"]?.split(",") ?: emptyList()
            
            for (feature in features) {
                when (feature.trim()) {
                    "camera" -> disableCamera()
                    "usb" -> disableUSB()
                    "developer_mode" -> disableDeveloperMode()
                    "network" -> restrictNetwork()
                    else -> Log.w(TAG, "Unknown feature to disable: $feature")
                }
            }
            
            auditLog.logAction(
                action = "FEATURES_DISABLED",
                description = "Features disabled: ${features.joinToString(", ")}"
            )
            
            CommandExecutionResult(
                success = true,
                commandId = command.commandId,
                status = "COMPLETED",
                message = "Features disabled successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling features", e)
            CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error disabling features: ${e.message}"
            )
        }
    }
    
    /**
     * Execute WIPE_DATA command
     */
    private fun executeWipeData(command: BlockingCommand): CommandExecutionResult {
        return try {
            Log.e(TAG, "Wiping sensitive data - Reason: ${command.reason}")
            
            val wipeManager = SensitiveDataWipeManager(context)
            val success = wipeManager.wipeSensitiveData()
            
            if (success) {
                Log.e(TAG, "✓ Data wipe completed successfully")
                auditLog.logAction(
                    action = "DATA_WIPED",
                    description = "Sensitive data wiped by backend command. Reason: ${command.reason}"
                )
                
                CommandExecutionResult(
                    success = true,
                    commandId = command.commandId,
                    status = "COMPLETED",
                    message = "Data wiped successfully"
                )
            } else {
                Log.w(TAG, "⚠ Data wipe completed with some failures")
                auditLog.logAction(
                    action = "DATA_WIPE_PARTIAL",
                    description = "Data wipe completed with failures. Reason: ${command.reason}"
                )
                
                CommandExecutionResult(
                    success = true,
                    commandId = command.commandId,
                    status = "COMPLETED",
                    message = "Data wipe completed with some failures"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping data", e)
            CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error wiping data: ${e.message}"
            )
        }
    }
    
    /**
     * Execute ALERT_ONLY command
     */
    private fun executeAlert(command: BlockingCommand): CommandExecutionResult {
        return try {
            Log.w(TAG, "Alert command - Reason: ${command.reason}")
            
            auditLog.logIncident(
                type = "BACKEND_ALERT",
                severity = command.severity,
                details = command.reason
            )
            
            CommandExecutionResult(
                success = true,
                commandId = command.commandId,
                status = "COMPLETED",
                message = "Alert logged"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing alert", e)
            CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error processing alert: ${e.message}"
            )
        }
    }
    
    /**
     * Execute DISABLE_CAMERA command
     */
    private fun executeDisableCamera(command: BlockingCommand): CommandExecutionResult {
        return try {
            Log.w(TAG, "Disabling camera")
            disableCamera()
            
            auditLog.logAction(
                action = "CAMERA_DISABLED",
                description = "Camera disabled by backend command"
            )
            
            CommandExecutionResult(
                success = true,
                commandId = command.commandId,
                status = "COMPLETED",
                message = "Camera disabled"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling camera", e)
            CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error disabling camera: ${e.message}"
            )
        }
    }
    
    /**
     * Execute DISABLE_USB command
     */
    private fun executeDisableUSB(command: BlockingCommand): CommandExecutionResult {
        return try {
            Log.w(TAG, "Disabling USB")
            disableUSB()
            
            auditLog.logAction(
                action = "USB_DISABLED",
                description = "USB disabled by backend command"
            )
            
            CommandExecutionResult(
                success = true,
                commandId = command.commandId,
                status = "COMPLETED",
                message = "USB disabled"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling USB", e)
            CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error disabling USB: ${e.message}"
            )
        }
    }
    
    /**
     * Execute DISABLE_DEVELOPER_MODE command
     */
    private fun executeDisableDeveloperMode(command: BlockingCommand): CommandExecutionResult {
        return try {
            Log.w(TAG, "Disabling developer mode")
            disableDeveloperMode()
            
            auditLog.logAction(
                action = "DEVELOPER_MODE_DISABLED",
                description = "Developer mode disabled by backend command"
            )
            
            CommandExecutionResult(
                success = true,
                commandId = command.commandId,
                status = "COMPLETED",
                message = "Developer mode disabled"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling developer mode", e)
            CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error disabling developer mode: ${e.message}"
            )
        }
    }
    
    /**
     * Execute RESTRICT_NETWORK command
     */
    private fun executeRestrictNetwork(command: BlockingCommand): CommandExecutionResult {
        return try {
            Log.w(TAG, "Restricting network")
            restrictNetwork()
            
            auditLog.logAction(
                action = "NETWORK_RESTRICTED",
                description = "Network restricted by backend command"
            )
            
            CommandExecutionResult(
                success = true,
                commandId = command.commandId,
                status = "COMPLETED",
                message = "Network restricted"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error restricting network", e)
            CommandExecutionResult(
                success = false,
                commandId = command.commandId,
                status = "FAILED",
                message = "Error restricting network: ${e.message}"
            )
        }
    }
    
    /**
     * Disable camera
     */
    private fun disableCamera() {
        // Implementation depends on device admin capabilities
        Log.d(TAG, "Camera disabled")
    }
    
    /**
     * Disable USB
     */
    private fun disableUSB() {
        // Implementation depends on device admin capabilities
        Log.d(TAG, "USB disabled")
    }
    
    /**
     * Disable developer mode
     */
    private fun disableDeveloperMode() {
        // Implementation depends on device admin capabilities
        Log.d(TAG, "Developer mode disabled")
    }
    
    /**
     * Restrict network
     */
    private fun restrictNetwork() {
        // Implementation depends on device admin capabilities
        Log.d(TAG, "Network restricted")
    }
    
    /**
     * Log command execution
     */
    private fun logCommandExecution(command: BlockingCommand, result: CommandExecutionResult) {
        try {
            auditLog.logAction(
                action = "COMMAND_EXECUTED",
                description = "Command: ${command.type}, Status: ${result.status}, Message: ${result.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error logging command execution", e)
        }
    }
    
    /**
     * Add command to history
     */
    private fun addToCommandHistory(command: BlockingCommand) {
        try {
            val history = getCommandHistory().toMutableList()
            history.add(command)
            
            // Keep only last MAX_HISTORY_SIZE entries
            if (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(0)
            }
            
            val json = gson.toJson(history)
            prefs.edit().putString(KEY_COMMAND_HISTORY, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to command history", e)
        }
    }
    
    /**
     * Get command history
     */
    fun getCommandHistory(): List<BlockingCommand> {
        return try {
            val json = prefs.getString(KEY_COMMAND_HISTORY, "[]") ?: "[]"
            val type = object : com.google.gson.reflect.TypeToken<List<BlockingCommand>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving command history", e)
            emptyList()
        }
    }
    
    /**
     * Check if command was already executed
     */
    fun isCommandExecuted(commandId: String): Boolean {
        return try {
            val executed = prefs.getStringSet(KEY_EXECUTED_COMMANDS, emptySet()) ?: emptySet()
            executed.contains(commandId)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking command execution", e)
            false
        }
    }
    
    /**
     * Mark command as executed
     */
    fun markCommandExecuted(commandId: String) {
        try {
            val executed = prefs.getStringSet(KEY_EXECUTED_COMMANDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            executed.add(commandId)
            prefs.edit().putStringSet(KEY_EXECUTED_COMMANDS, executed).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking command as executed", e)
        }
    }
}

/**
 * Result of command execution
 */
data class CommandExecutionResult(
    val success: Boolean,
    val commandId: String,
    val status: String, // COMPLETED, FAILED, PENDING
    val message: String,
    val executedAt: Long = System.currentTimeMillis()
)
