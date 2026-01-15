package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

/**
 * Manages app rollback operations
 * Feature 4.11: Agent Updates & Rollback
 * 
 * Responsibilities:
 * - Maintain previous stable APKs
 * - Automatic rollback on update failure
 * - Manual rollback via backend command
 * - Preserve app data during rollback
 */
class RollbackManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "rollback_manager",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    // Protected cache directory for rollback APKs
    private val protectedRollbackDir: File by lazy {
        val rollbackDir = File(context.cacheDir, "protected_rollback")
        if (!rollbackDir.exists()) {
            rollbackDir.mkdirs()
            setDirectoryProtection(rollbackDir)
        }
        rollbackDir
    }
    
    companion object {
        private const val TAG = "RollbackManager"
        private const val KEY_ROLLBACK_HISTORY = "rollback_history"
        private const val KEY_BACKUP_VERSIONS = "backup_versions"
        private const val MAX_BACKUPS = 3
        
        // Rollback file names
        private const val ROLLBACK_BACKUP_FILE = "rollback_backup_%s.apk"
        private const val ROLLBACK_METADATA_FILE = "rollback_metadata.json"
    }
    
    /**
     * Backup current APK for rollback
     */
    fun backupCurrentAPK(currentVersion: String): Boolean {
        return try {
            Log.d(TAG, "Backing up current APK: $currentVersion")
            
            val apkPath = context.packageCodePath
            val apkFile = File(apkPath)
            
            if (!apkFile.exists()) {
                Log.e(TAG, "Current APK not found: $apkPath")
                return false
            }
            
            // Create backup file
            val backupFile = File(protectedRollbackDir, ROLLBACK_BACKUP_FILE.format(currentVersion))
            apkFile.copyTo(backupFile, overwrite = true)
            
            // Store backup metadata
            val backupMetadata = RollbackBackup(
                version = currentVersion,
                backupPath = backupFile.absolutePath,
                backupTime = System.currentTimeMillis(),
                fileSize = backupFile.length(),
                sha256 = calculateSHA256(backupFile)
            )
            
            // Add to backup history
            val history = getBackupHistory().toMutableList()
            history.add(backupMetadata)
            
            // Keep only last MAX_BACKUPS
            if (history.size > MAX_BACKUPS) {
                val oldBackup = history.removeAt(0)
                File(oldBackup.backupPath).delete()
            }
            
            prefs.edit().putString(
                KEY_BACKUP_VERSIONS,
                gson.toJson(history)
            ).apply()
            
            Log.d(TAG, "✓ APK backed up successfully")
            Log.d(TAG, "  Path: ${backupFile.absolutePath}")
            Log.d(TAG, "  Size: ${backupFile.length()} bytes")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up APK: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get available rollback versions
     */
    fun getAvailableRollbackVersions(): List<RollbackBackup> {
        return getBackupHistory().filter { backup ->
            File(backup.backupPath).exists()
        }
    }
    
    /**
     * Perform rollback to specific version
     */
    fun rollbackToVersion(targetVersion: String): RollbackResult {
        return try {
            Log.d(TAG, "Performing rollback to version: $targetVersion")
            
            val backupHistory = getBackupHistory()
            val backup = backupHistory.find { it.version == targetVersion }
            
            if (backup == null) {
                Log.e(TAG, "Rollback backup not found for version: $targetVersion")
                return RollbackResult(
                    success = false,
                    error = "Rollback backup not found"
                )
            }
            
            val backupFile = File(backup.backupPath)
            if (!backupFile.exists()) {
                Log.e(TAG, "Rollback backup file not found: ${backup.backupPath}")
                return RollbackResult(
                    success = false,
                    error = "Rollback backup file not found"
                )
            }
            
            // Verify backup integrity
            val currentSHA256 = calculateSHA256(backupFile)
            if (currentSHA256 != backup.sha256) {
                Log.e(TAG, "Rollback backup integrity check failed")
                Log.e(TAG, "  Expected: ${backup.sha256}")
                Log.e(TAG, "  Got: $currentSHA256")
                return RollbackResult(
                    success = false,
                    error = "Rollback backup integrity check failed"
                )
            }
            
            Log.d(TAG, "✓ Rollback backup verified")
            Log.d(TAG, "  Version: ${backup.version}")
            Log.d(TAG, "  Size: ${backup.fileSize} bytes")
            
            RollbackResult(
                success = true,
                backupPath = backup.backupPath,
                targetVersion = targetVersion,
                backupTime = backup.backupTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing rollback: ${e.message}", e)
            RollbackResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Mark rollback as completed
     */
    fun markRollbackCompleted(targetVersion: String, success: Boolean) {
        try {
            Log.d(TAG, "Marking rollback completed: $targetVersion (success=$success)")
            
            val history = getRollbackHistory().toMutableList()
            val rollback = history.find { it.targetVersion == targetVersion }
            
            if (rollback != null) {
                rollback.status = if (success) "COMPLETED" else "FAILED"
                rollback.completedAt = System.currentTimeMillis()
                
                prefs.edit().putString(
                    KEY_ROLLBACK_HISTORY,
                    gson.toJson(history)
                ).apply()
                
                Log.d(TAG, "✓ Rollback status updated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking rollback completed: ${e.message}")
        }
    }
    
    /**
     * Get rollback history
     */
    fun getRollbackHistory(): List<RollbackRecord> {
        return try {
            val json = prefs.getString(KEY_ROLLBACK_HISTORY, null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<RollbackRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting rollback history: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Queue rollback operation
     */
    fun queueRollback(targetVersion: String, reason: String): Boolean {
        return try {
            Log.d(TAG, "Queueing rollback: $targetVersion (reason: $reason)")
            
            val history = getRollbackHistory().toMutableList()
            val rollback = RollbackRecord(
                targetVersion = targetVersion,
                reason = reason,
                queuedAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            
            history.add(rollback)
            
            prefs.edit().putString(
                KEY_ROLLBACK_HISTORY,
                gson.toJson(history)
            ).apply()
            
            Log.d(TAG, "✓ Rollback queued successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error queueing rollback: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get pending rollback
     */
    fun getPendingRollback(): RollbackRecord? {
        return getRollbackHistory().find { it.status == "PENDING" }
    }
    
    /**
     * Clear old backups
     */
    fun clearOldBackups() {
        try {
            Log.d(TAG, "Clearing old backups...")
            
            val backups = getBackupHistory()
            if (backups.size > MAX_BACKUPS) {
                val toDelete = backups.dropLast(MAX_BACKUPS)
                toDelete.forEach { backup ->
                    File(backup.backupPath).delete()
                    Log.d(TAG, "Deleted old backup: ${backup.version}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing old backups: ${e.message}")
        }
    }
    
    /**
     * Calculate SHA256 hash of file
     */
    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get backup history
     */
    private fun getBackupHistory(): List<RollbackBackup> {
        return try {
            val json = prefs.getString(KEY_BACKUP_VERSIONS, null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<RollbackBackup>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting backup history: ${e.message}")
            emptyList()
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
            Log.w(TAG, "Error setting directory protection: ${e.message}")
        }
    }
}

/**
 * Rollback backup metadata
 */
data class RollbackBackup(
    val version: String,
    val backupPath: String,
    val backupTime: Long,
    val fileSize: Long,
    val sha256: String
)

/**
 * Rollback operation record
 */
data class RollbackRecord(
    val targetVersion: String,
    val reason: String,
    val queuedAt: Long,
    var status: String = "PENDING",
    var completedAt: Long = 0
)

/**
 * Rollback operation result
 */
data class RollbackResult(
    val success: Boolean,
    val backupPath: String? = null,
    val targetVersion: String? = null,
    val backupTime: Long = 0,
    val error: String? = null
)
