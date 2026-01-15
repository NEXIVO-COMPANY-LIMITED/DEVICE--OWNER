package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * Manages secure wiping of sensitive data from device
 * Clears:
 * - SharedPreferences
 * - Cache directory
 * - Files directory
 * - Databases
 * - Temporary files
 */
class SensitiveDataWipeManager(private val context: Context) {

    companion object {
        private const val TAG = "SensitiveDataWipeManager"
        
        // SharedPreferences to wipe
        private val SHARED_PREFS_TO_WIPE = listOf(
            "identifier_prefs",
            "blocking_commands",
            "heartbeat_data",
            "command_queue",
            "mismatch_alerts",
            "audit_log",
            "device_profile",
            "boot_verification",
            "registration_data",
            "payment_data",
            "loan_data",
            "lock_status",
            "device_owner_prefs",
            "security_prefs",
            "app_preferences"
        )
        
        // Directories to wipe
        private val DIRECTORIES_TO_WIPE = listOf(
            "cache",
            "files",
            "databases",
            "shared_prefs",
            "no_backup"
        )
    }

    private val auditLog = IdentifierAuditLog(context)

    /**
     * Wipe all sensitive data from device
     * Returns true if successful, false otherwise
     */
    fun wipeSensitiveData(): Boolean {
        return try {
            Log.w(TAG, "Starting sensitive data wipe...")
            
            val results = mutableMapOf<String, Boolean>()
            
            // Wipe SharedPreferences
            results["sharedPreferences"] = wipeSharedPreferences()
            
            // Wipe cache directory
            results["cache"] = wipeCacheDirectory()
            
            // Wipe files directory
            results["files"] = wipeFilesDirectory()
            
            // Wipe databases
            results["databases"] = wipeDatabases()
            
            // Wipe temporary files
            results["tempFiles"] = wipeTempFiles()
            
            // Log the wipe operation
            logWipeOperation(results)
            
            val allSuccessful = results.values.all { it }
            
            if (allSuccessful) {
                Log.w(TAG, "✓ Sensitive data wipe completed successfully")
                auditLog.logAction(
                    "DATA_WIPE_COMPLETED",
                    "All sensitive data wiped successfully"
                )
            } else {
                Log.w(TAG, "⚠ Sensitive data wipe completed with some failures: $results")
                auditLog.logAction(
                    "DATA_WIPE_PARTIAL",
                    "Sensitive data wipe completed with failures: $results"
                )
            }
            
            allSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error during sensitive data wipe", e)
            auditLog.logAction(
                "DATA_WIPE_FAILED",
                "Error during data wipe: ${e.message}"
            )
            false
        }
    }

    /**
     * Wipe all SharedPreferences
     */
    private fun wipeSharedPreferences(): Boolean {
        return try {
            Log.d(TAG, "Wiping SharedPreferences...")
            var successCount = 0
            var failureCount = 0
            
            // Wipe known SharedPreferences
            for (prefName in SHARED_PREFS_TO_WIPE) {
                try {
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    Log.d(TAG, "✓ Cleared SharedPreferences: $prefName")
                    successCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear SharedPreferences: $prefName", e)
                    failureCount++
                }
            }
            
            // Wipe default SharedPreferences
            try {
                val defaultPrefs = context.getSharedPreferences(
                    "${context.packageName}_preferences",
                    Context.MODE_PRIVATE
                )
                defaultPrefs.edit().clear().apply()
                Log.d(TAG, "✓ Cleared default SharedPreferences")
                successCount++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear default SharedPreferences", e)
                failureCount++
            }
            
            Log.d(TAG, "SharedPreferences wipe: $successCount successful, $failureCount failed")
            failureCount == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping SharedPreferences", e)
            false
        }
    }

    /**
     * Wipe cache directory
     */
    private fun wipeCacheDirectory(): Boolean {
        return try {
            Log.d(TAG, "Wiping cache directory...")
            
            val cacheDir = context.cacheDir
            val success = deleteDirectoryContents(cacheDir)
            
            if (success) {
                Log.d(TAG, "✓ Cache directory wiped")
            } else {
                Log.w(TAG, "⚠ Failed to completely wipe cache directory")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping cache directory", e)
            false
        }
    }

    /**
     * Wipe files directory
     */
    private fun wipeFilesDirectory(): Boolean {
        return try {
            Log.d(TAG, "Wiping files directory...")
            
            val filesDir = context.filesDir
            val success = deleteDirectoryContents(filesDir)
            
            if (success) {
                Log.d(TAG, "✓ Files directory wiped")
            } else {
                Log.w(TAG, "⚠ Failed to completely wipe files directory")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping files directory", e)
            false
        }
    }

    /**
     * Wipe databases
     */
    private fun wipeDatabases(): Boolean {
        return try {
            Log.d(TAG, "Wiping databases...")
            
            val databasesDir = context.databaseList()
            var successCount = 0
            var failureCount = 0
            
            for (dbName in databasesDir) {
                try {
                    val success = context.deleteDatabase(dbName)
                    if (success) {
                        Log.d(TAG, "✓ Deleted database: $dbName")
                        successCount++
                    } else {
                        Log.w(TAG, "Failed to delete database: $dbName")
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting database: $dbName", e)
                    failureCount++
                }
            }
            
            Log.d(TAG, "Databases wipe: $successCount successful, $failureCount failed")
            failureCount == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping databases", e)
            false
        }
    }

    /**
     * Wipe temporary files
     */
    private fun wipeTempFiles(): Boolean {
        return try {
            Log.d(TAG, "Wiping temporary files...")
            
            var successCount = 0
            var failureCount = 0
            
            // Wipe no_backup directory
            try {
                val noBackupDir = File(context.noBackupFilesDir.absolutePath)
                if (noBackupDir.exists()) {
                    val success = deleteDirectoryContents(noBackupDir)
                    if (success) {
                        Log.d(TAG, "✓ No-backup directory wiped")
                        successCount++
                    } else {
                        Log.w(TAG, "Failed to wipe no-backup directory")
                        failureCount++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error wiping no-backup directory", e)
                failureCount++
            }
            
            // Wipe external cache directory if available
            try {
                val externalCacheDir = context.externalCacheDir
                if (externalCacheDir != null && externalCacheDir.exists()) {
                    val success = deleteDirectoryContents(externalCacheDir)
                    if (success) {
                        Log.d(TAG, "✓ External cache directory wiped")
                        successCount++
                    } else {
                        Log.w(TAG, "Failed to wipe external cache directory")
                        failureCount++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error wiping external cache directory", e)
                failureCount++
            }
            
            Log.d(TAG, "Temporary files wipe: $successCount successful, $failureCount failed")
            failureCount == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping temporary files", e)
            false
        }
    }

    /**
     * Recursively delete directory contents
     */
    private fun deleteDirectoryContents(directory: File): Boolean {
        return try {
            if (!directory.exists()) {
                Log.d(TAG, "Directory does not exist: ${directory.absolutePath}")
                return true
            }
            
            if (!directory.isDirectory) {
                Log.w(TAG, "Path is not a directory: ${directory.absolutePath}")
                return false
            }
            
            val files = directory.listFiles() ?: return true
            var allDeleted = true
            
            for (file in files) {
                try {
                    if (file.isDirectory) {
                        // Recursively delete subdirectories
                        if (!deleteDirectoryContents(file)) {
                            allDeleted = false
                        }
                        if (!file.delete()) {
                            Log.w(TAG, "Failed to delete directory: ${file.absolutePath}")
                            allDeleted = false
                        }
                    } else {
                        // Delete file
                        if (!file.delete()) {
                            Log.w(TAG, "Failed to delete file: ${file.absolutePath}")
                            allDeleted = false
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting file: ${file.absolutePath}", e)
                    allDeleted = false
                }
            }
            
            allDeleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting directory contents", e)
            false
        }
    }

    /**
     * Wipe specific SharedPreferences
     */
    fun wipeSharedPreferences(prefName: String): Boolean {
        return try {
            Log.d(TAG, "Wiping SharedPreferences: $prefName")
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.d(TAG, "✓ Cleared SharedPreferences: $prefName")
            auditLog.logAction("PREFS_WIPED", "SharedPreferences wiped: $prefName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping SharedPreferences: $prefName", e)
            false
        }
    }

    /**
     * Wipe specific database
     */
    fun wipeDatabase(dbName: String): Boolean {
        return try {
            Log.d(TAG, "Wiping database: $dbName")
            val success = context.deleteDatabase(dbName)
            if (success) {
                Log.d(TAG, "✓ Deleted database: $dbName")
                auditLog.logAction("DATABASE_WIPED", "Database wiped: $dbName")
            } else {
                Log.w(TAG, "Failed to delete database: $dbName")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping database: $dbName", e)
            false
        }
    }

    /**
     * Wipe specific file
     */
    fun wipeFile(filePath: String): Boolean {
        return try {
            Log.d(TAG, "Wiping file: $filePath")
            val file = File(filePath)
            if (file.exists()) {
                val success = file.delete()
                if (success) {
                    Log.d(TAG, "✓ Deleted file: $filePath")
                    auditLog.logAction("FILE_WIPED", "File wiped: $filePath")
                } else {
                    Log.w(TAG, "Failed to delete file: $filePath")
                }
                success
            } else {
                Log.d(TAG, "File does not exist: $filePath")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping file: $filePath", e)
            false
        }
    }

    /**
     * Get wipe status summary
     */
    fun getWipeStatusSummary(): Map<String, Any> {
        return try {
            mapOf<String, Any>(
                "timestamp" to System.currentTimeMillis(),
                "sharedPreferencesCount" to SHARED_PREFS_TO_WIPE.size,
                "directoriesCount" to DIRECTORIES_TO_WIPE.size,
                "databasesCount" to (context.databaseList()?.size ?: 0),
                "status" to "ready"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting wipe status", e)
            mapOf<String, Any>("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    /**
     * Log wipe operation details
     */
    private fun logWipeOperation(results: Map<String, Boolean>) {
        try {
            val summary = results.entries.joinToString(", ") { (key, value) ->
                "$key: ${if (value) "✓" else "✗"}"
            }
            Log.d(TAG, "Wipe operation summary: $summary")
            auditLog.logAction("DATA_WIPE_SUMMARY", summary)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging wipe operation", e)
        }
    }
}
