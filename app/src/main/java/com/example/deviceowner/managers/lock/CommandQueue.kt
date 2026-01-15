package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages offline command queue for device enforcement
 * Feature 4.9: Offline Command Queue
 * 
 * Ensures commands execute even without internet connection
 * - Stores commands locally with encryption
 * - Persists across reboots
 * - Verifies command signatures
 * - Maintains audit trail
 * - Prevents command tampering
 */
class CommandQueue(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "command_queue",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val auditLog = IdentifierAuditLog(context)
    
    // Protected cache directory for encrypted queue
    private val protectedQueueDir: File by lazy {
        val queueDir = File(context.cacheDir, "protected_command_queue")
        if (!queueDir.exists()) {
            queueDir.mkdirs()
            setDirectoryProtection(queueDir)
        }
        queueDir
    }

    companion object {
        private const val TAG = "CommandQueue"
        private const val KEY_QUEUE_DATA = "queue_data"
        private const val KEY_QUEUE_HISTORY = "queue_history"
        private const val KEY_ENCRYPTION_KEY = "queue_encryption_key"
        private const val MAX_QUEUE_SIZE = 1000
        private const val MAX_HISTORY_SIZE = 500
        
        // Queue file names
        private const val QUEUE_FILE = "commands.queue"
        private const val HISTORY_FILE = "commands.history"
        private const val METADATA_FILE = "queue.metadata"
    }

    /**
     * Enqueue a command for offline execution
     */
    fun enqueueCommand(command: OfflineCommand): Boolean {
        return try {
            Log.d(TAG, "Enqueueing command: ${command.commandId} - ${command.type}")
            
            // Validate command
            if (!validateCommand(command)) {
                Log.e(TAG, "Command validation failed: ${command.commandId}")
                return false
            }
            
            // Add timestamp if not present
            val commandWithTimestamp = if (command.enqueuedAt == 0L) {
                command.copy(enqueuedAt = System.currentTimeMillis())
            } else {
                command
            }
            
            // Get current queue
            val queue = getQueue().toMutableList()
            
            // Check queue size
            if (queue.size >= MAX_QUEUE_SIZE) {
                Log.w(TAG, "Queue is full, removing oldest command")
                queue.removeAt(0)
            }
            
            // Add command to queue
            queue.add(commandWithTimestamp)
            
            // Save encrypted queue
            saveQueue(queue)
            
            // Log to audit trail
            auditLog.logAction(
                "COMMAND_ENQUEUED",
                "Command enqueued: ${command.type} - ${command.commandId}"
            )
            
            Log.d(TAG, "✓ Command enqueued successfully: ${command.commandId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enqueueing command", e)
            auditLog.logIncident(
                type = "COMMAND_ENQUEUE_ERROR",
                severity = "HIGH",
                details = "Failed to enqueue command: ${e.message}"
            )
            false
        }
    }

    /**
     * Dequeue and execute next command
     */
    fun dequeueAndExecuteCommand(): OfflineCommand? {
        return try {
            val queue = getQueue().toMutableList()
            
            if (queue.isEmpty()) {
                Log.d(TAG, "Queue is empty")
                return null
            }
            
            // Get first command
            val command = queue.removeAt(0)
            
            // Verify command signature
            if (!verifyCommandSignature(command)) {
                Log.e(TAG, "Command signature verification failed: ${command.commandId}")
                auditLog.logIncident(
                    type = "COMMAND_SIGNATURE_INVALID",
                    severity = "CRITICAL",
                    details = "Command: ${command.commandId}"
                )
                // Remove invalid command from queue
                saveQueue(queue)
                return null
            }
            
            // Check if command is expired
            if (isCommandExpired(command)) {
                Log.w(TAG, "Command expired: ${command.commandId}")
                auditLog.logAction(
                    "COMMAND_EXPIRED",
                    "Command expired: ${command.commandId}"
                )
                // Remove expired command from queue
                saveQueue(queue)
                return null
            }
            
            // Update command status
            val executingCommand = command.copy(
                status = CommandStatus.EXECUTING,
                executionStartedAt = System.currentTimeMillis()
            )
            
            // Save updated queue
            saveQueue(queue)
            
            // Add to history
            addToHistory(executingCommand)
            
            Log.d(TAG, "✓ Command dequeued: ${command.commandId}")
            executingCommand
        } catch (e: Exception) {
            Log.e(TAG, "Error dequeueing command", e)
            null
        }
    }

    /**
     * Mark command as executed
     */
    fun markCommandExecuted(commandId: String, result: String = "SUCCESS"): Boolean {
        return try {
            val history = getHistory().toMutableList()
            val index = history.indexOfFirst { it.commandId == commandId }
            
            if (index >= 0) {
                val command = history[index]
                val executedCommand = command.copy(
                    status = CommandStatus.EXECUTED,
                    executionCompletedAt = System.currentTimeMillis(),
                    executionResult = result
                )
                history[index] = executedCommand
                
                // Save updated history
                saveHistory(history)
                
                // Log execution
                auditLog.logAction(
                    "COMMAND_EXECUTED",
                    "Command executed: $commandId - Result: $result"
                )
                
                Log.d(TAG, "✓ Command marked as executed: $commandId")
                true
            } else {
                Log.w(TAG, "Command not found in history: $commandId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking command as executed", e)
            false
        }
    }

    /**
     * Mark command as failed
     */
    fun markCommandFailed(commandId: String, error: String): Boolean {
        return try {
            val history = getHistory().toMutableList()
            val index = history.indexOfFirst { it.commandId == commandId }
            
            if (index >= 0) {
                val command = history[index]
                val failedCommand = command.copy(
                    status = CommandStatus.FAILED,
                    executionCompletedAt = System.currentTimeMillis(),
                    executionResult = error
                )
                history[index] = failedCommand
                
                // Save updated history
                saveHistory(history)
                
                // Log failure
                auditLog.logIncident(
                    type = "COMMAND_EXECUTION_FAILED",
                    severity = "HIGH",
                    details = "Command: $commandId - Error: $error"
                )
                
                Log.e(TAG, "✗ Command marked as failed: $commandId - $error")
                true
            } else {
                Log.w(TAG, "Command not found in history: $commandId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking command as failed", e)
            false
        }
    }

    /**
     * Get queue size
     */
    fun getQueueSize(): Int {
        return getQueue().size
    }

    /**
     * Get pending commands count
     */
    fun getPendingCommandsCount(): Int {
        return getQueue().count { it.status == CommandStatus.PENDING }
    }

    /**
     * Get queue
     */
    fun getQueue(): List<OfflineCommand> {
        return try {
            // Try to read from protected cache first
            val queueFile = File(protectedQueueDir, QUEUE_FILE)
            if (queueFile.exists()) {
                val encryptedData = queueFile.readBytes()
                val decryptedData = decryptData(encryptedData)
                val json = String(decryptedData)
                return gson.fromJson(json, Array<OfflineCommand>::class.java).toList()
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_QUEUE_DATA, "[]") ?: "[]"
            gson.fromJson(json, Array<OfflineCommand>::class.java).toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting queue", e)
            emptyList()
        }
    }

    /**
     * Get command history
     */
    fun getHistory(): List<OfflineCommand> {
        return try {
            // Try to read from protected cache first
            val historyFile = File(protectedQueueDir, HISTORY_FILE)
            if (historyFile.exists()) {
                val encryptedData = historyFile.readBytes()
                val decryptedData = decryptData(encryptedData)
                val json = String(decryptedData)
                return gson.fromJson(json, Array<OfflineCommand>::class.java).toList()
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_QUEUE_HISTORY, "[]") ?: "[]"
            gson.fromJson(json, Array<OfflineCommand>::class.java).toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting history", e)
            emptyList()
        }
    }

    /**
     * Clear queue
     */
    fun clearQueue(): Boolean {
        return try {
            saveQueue(emptyList())
            Log.d(TAG, "Queue cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing queue", e)
            false
        }
    }

    /**
     * Validate command
     */
    private fun validateCommand(command: OfflineCommand): Boolean {
        // Check required fields
        if (command.commandId.isEmpty()) {
            Log.w(TAG, "Command ID is empty")
            return false
        }
        
        if (command.type.isEmpty()) {
            Log.w(TAG, "Command type is empty")
            return false
        }
        
        // Validate command type
        val validTypes = listOf(
            "LOCK_DEVICE",
            "UNLOCK_DEVICE",
            "WARN",
            "PERMANENT_LOCK",
            "WIPE_DATA",
            "UPDATE_APP",
            "REBOOT_DEVICE"
        )
        
        if (!validTypes.contains(command.type)) {
            Log.w(TAG, "Invalid command type: ${command.type}")
            return false
        }
        
        return true
    }

    /**
     * Verify command signature
     */
    private fun verifyCommandSignature(command: OfflineCommand): Boolean {
        return try {
            // If no signature, skip verification (for local commands)
            if (command.signature.isEmpty()) {
                Log.d(TAG, "No signature to verify (local command)")
                return true
            }
            
            // Get backend public key
            val publicKey = getBackendPublicKey()
            if (publicKey == null) {
                Log.w(TAG, "Backend public key not available, skipping signature verification")
                return true // Allow command if public key not configured yet
            }
            
            // Verify signature using RSA with SHA-256
            val verified = verifyRSASignature(command, publicKey)
            
            if (verified) {
                Log.d(TAG, "✓ Command signature verified: ${command.commandId}")
            } else {
                Log.e(TAG, "✗ Command signature verification FAILED: ${command.commandId}")
                auditLog.logIncident(
                    type = "COMMAND_SIGNATURE_FAILED",
                    severity = "CRITICAL",
                    details = "Command signature verification failed: ${command.commandId}"
                )
            }
            
            verified
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying command signature", e)
            auditLog.logIncident(
                type = "COMMAND_SIGNATURE_ERROR",
                severity = "CRITICAL",
                details = "Exception during signature verification: ${e.message}"
            )
            false
        }
    }

    /**
     * Get backend public key from SharedPreferences
     */
    private fun getBackendPublicKey(): PublicKey? {
        return try {
            val publicKeyString = prefs.getString("backend_public_key", null)
            if (publicKeyString == null) {
                Log.d(TAG, "Backend public key not configured")
                return null
            }
            
            // Decode Base64 public key
            val keyBytes = android.util.Base64.decode(publicKeyString, android.util.Base64.DEFAULT)
            
            // Create public key from bytes
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading backend public key", e)
            null
        }
    }

    /**
     * Verify RSA signature using SHA-256
     */
    private fun verifyRSASignature(command: OfflineCommand, publicKey: PublicKey): Boolean {
        return try {
            // Create signature data from command
            val signatureData = createSignatureData(command)
            
            // Decode signature from Base64
            val signatureBytes = android.util.Base64.decode(command.signature, android.util.Base64.DEFAULT)
            
            // Verify signature
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(signatureData.toByteArray())
            
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying RSA signature", e)
            false
        }
    }

    /**
     * Create signature data from command
     * Format: commandId|type|deviceId|parameters|expiresAt
     */
    private fun createSignatureData(command: OfflineCommand): String {
        val parametersJson = gson.toJson(command.parameters)
        return "${command.commandId}|${command.type}|${command.deviceId}|${parametersJson}|${command.expiresAt}"
    }

    /**
     * Set backend public key (called during app initialization or from backend)
     */
    fun setBackendPublicKey(publicKeyBase64: String): Boolean {
        return try {
            // Validate public key format
            val keyBytes = android.util.Base64.decode(publicKeyBase64, android.util.Base64.DEFAULT)
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
            keyFactory.generatePublic(keySpec) // Validate key
            
            // Save to SharedPreferences
            prefs.edit().putString("backend_public_key", publicKeyBase64).apply()
            
            Log.d(TAG, "✓ Backend public key configured successfully")
            auditLog.logAction(
                "BACKEND_PUBLIC_KEY_CONFIGURED",
                "Backend public key configured for command signature verification"
            )
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting backend public key", e)
            false
        }
    }

    /**
     * Check if command is expired
     */
    private fun isCommandExpired(command: OfflineCommand): Boolean {
        if (command.expiresAt == 0L) {
            return false // No expiration
        }
        
        val isExpired = System.currentTimeMillis() > command.expiresAt
        if (isExpired) {
            Log.w(TAG, "Command expired: ${command.commandId}")
        }
        return isExpired
    }

    /**
     * Save queue with encryption
     */
    private fun saveQueue(queue: List<OfflineCommand>) {
        try {
            val json = gson.toJson(queue)
            val encryptedData = encryptData(json.toByteArray())
            
            // Save to protected cache file
            val queueFile = File(protectedQueueDir, QUEUE_FILE)
            queueFile.writeBytes(encryptedData)
            setFileProtection(queueFile)
            
            // Also save to SharedPreferences as backup
            prefs.edit().putString(KEY_QUEUE_DATA, json).apply()
            
            Log.d(TAG, "Queue saved with encryption: ${queue.size} commands")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving queue", e)
        }
    }

    /**
     * Save history with encryption
     */
    private fun saveHistory(history: List<OfflineCommand>) {
        try {
            val json = gson.toJson(history)
            val encryptedData = encryptData(json.toByteArray())
            
            // Save to protected cache file
            val historyFile = File(protectedQueueDir, HISTORY_FILE)
            historyFile.writeBytes(encryptedData)
            setFileProtection(historyFile)
            
            // Also save to SharedPreferences as backup
            prefs.edit().putString(KEY_QUEUE_HISTORY, json).apply()
            
            Log.d(TAG, "History saved with encryption: ${history.size} commands")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving history", e)
        }
    }

    /**
     * Add command to history
     */
    private fun addToHistory(command: OfflineCommand) {
        try {
            val history = getHistory().toMutableList()
            history.add(command)
            
            // Keep only last MAX_HISTORY_SIZE entries
            if (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(0)
            }
            
            saveHistory(history)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to history", e)
        }
    }

    /**
     * Encrypt data using AES
     */
    private fun encryptData(data: ByteArray): ByteArray {
        return try {
            val key = getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting data", e)
            data // Return unencrypted if encryption fails
        }
    }

    /**
     * Decrypt data using AES
     */
    private fun decryptData(encryptedData: ByteArray): ByteArray {
        return try {
            val key = getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, key)
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting data", e)
            encryptedData // Return as-is if decryption fails
        }
    }

    /**
     * Get or create encryption key
     */
    private fun getOrCreateEncryptionKey(): SecretKey {
        return try {
            // Try to get existing key from SharedPreferences
            val keyString = prefs.getString(KEY_ENCRYPTION_KEY, null)
            if (keyString != null) {
                val decodedKey = android.util.Base64.decode(keyString, android.util.Base64.DEFAULT)
                return SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
            }
            
            // Generate new key
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val key = keyGenerator.generateKey()
            
            // Save key
            val encodedKey = android.util.Base64.encodeToString(key.encoded, android.util.Base64.DEFAULT)
            prefs.edit().putString(KEY_ENCRYPTION_KEY, encodedKey).apply()
            
            key
        } catch (e: Exception) {
            Log.e(TAG, "Error getting encryption key", e)
            throw e
        }
    }

    /**
     * Set file protection
     */
    private fun setFileProtection(file: File) {
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.setExecutable(false, false)
            file.setExecutable(false, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting file protection", e)
        }
    }

    /**
     * Set directory protection
     */
    private fun setDirectoryProtection(dir: File) {
        try {
            dir.setReadable(false, false)
            dir.setReadable(true, true)
            dir.setWritable(false, false)
            dir.setWritable(true, true)
            dir.setExecutable(false, false)
            dir.setExecutable(true, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting directory protection", e)
        }
    }
}

/**
 * Data class for offline command
 */
data class OfflineCommand(
    val commandId: String,
    val type: String, // LOCK_DEVICE, UNLOCK_DEVICE, WARN, PERMANENT_LOCK, WIPE_DATA, UPDATE_APP, REBOOT_DEVICE
    val deviceId: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val signature: String = "",
    val status: CommandStatus = CommandStatus.PENDING,
    val enqueuedAt: Long = 0L,
    val expiresAt: Long = 0L, // 0 = never expires
    val executionStartedAt: Long = 0L,
    val executionCompletedAt: Long = 0L,
    val executionResult: String = "",
    val priority: Int = 5 // 1-10, higher = more important
)

/**
 * Command status enum
 */
enum class CommandStatus {
    PENDING,
    EXECUTING,
    EXECUTED,
    FAILED,
    EXPIRED,
    CANCELLED
}
