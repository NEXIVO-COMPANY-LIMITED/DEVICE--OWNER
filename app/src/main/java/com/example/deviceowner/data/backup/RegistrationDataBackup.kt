package com.example.deviceowner.data.backup

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.CompleteDeviceRegistrationEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles backup and restore of device registration data
 * Ensures registration data persists even after app reinstallation
 */
class RegistrationDataBackup(private val context: Context) {
    
    companion object {
        private const val TAG = "RegistrationDataBackup"
        private const val BACKUP_DIR = "registration_backup"
        private const val BACKUP_FILE = "device_registration_backup.json"
    }
    
    private val gson = Gson()
    private val database = DeviceOwnerDatabase.getDatabase(context)
    private val dao = database.completeDeviceRegistrationDao()
    
    /**
     * Backup registration data to local storage
     * Called after successful registration
     */
    suspend fun backupRegistrationData() = withContext(Dispatchers.IO) {
        try {
            val registrationData = dao.getSuccessfulRegistration()
            
            if (registrationData != null) {
                val backupFile = getBackupFile()
                
                // Create backup directory if it doesn't exist
                backupFile.parentFile?.mkdirs()
                
                // Convert entity to JSON and save
                val jsonData = gson.toJson(registrationData)
                backupFile.writeText(jsonData)
                
                Log.d(TAG, "Registration data backed up successfully")
                Log.d(TAG, "Backup file: ${backupFile.absolutePath}")
                Log.d(TAG, "Device ID: ${registrationData.deviceId}")
                Log.d(TAG, "Loan Number: ${registrationData.loanNumber}")
                
                return@withContext true
            } else {
                Log.w(TAG, "No successful registration data to backup")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up registration data: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Restore registration data from backup
     * Called during app startup if no local registration exists
     */
    suspend fun restoreRegistrationData(): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupFile = getBackupFile()
            
            if (!backupFile.exists()) {
                Log.d(TAG, "No backup file found at: ${backupFile.absolutePath}")
                return@withContext false
            }
            
            // Read backup file
            val jsonData = backupFile.readText()
            val restoredData = gson.fromJson(jsonData, CompleteDeviceRegistrationEntity::class.java)
            
            if (restoredData != null) {
                // Check if data already exists in database
                val existingData = dao.getRegistrationByDeviceId(restoredData.deviceId)
                
                if (existingData == null) {
                    // Insert restored data into database
                    dao.insertRegistration(restoredData)
                    
                    Log.d(TAG, "Registration data restored successfully from backup")
                    Log.d(TAG, "Device ID: ${restoredData.deviceId}")
                    Log.d(TAG, "Loan Number: ${restoredData.loanNumber}")
                    Log.d(TAG, "Registration Status: ${restoredData.registrationStatus}")
                    
                    return@withContext true
                } else {
                    Log.d(TAG, "Registration data already exists in database, skipping restore")
                    return@withContext false
                }
            } else {
                Log.e(TAG, "Failed to parse backup file")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring registration data: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Get backup file path
     */
    private fun getBackupFile(): File {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        return File(backupDir, BACKUP_FILE)
    }
    
    /**
     * Check if backup exists
     */
    suspend fun hasBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupFile = getBackupFile()
            val exists = backupFile.exists() && backupFile.length() > 0
            
            if (exists) {
                Log.d(TAG, "Backup file exists: ${backupFile.absolutePath}")
            }
            
            return@withContext exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking backup: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Get backup file info for debugging
     */
    suspend fun getBackupInfo(): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val backupFile = getBackupFile()
            
            if (!backupFile.exists()) {
                return@withContext mapOf(
                    "exists" to false,
                    "path" to backupFile.absolutePath
                )
            }
            
            val jsonData = backupFile.readText()
            val restoredData = gson.fromJson(jsonData, CompleteDeviceRegistrationEntity::class.java)
            
            return@withContext mapOf(
                "exists" to true,
                "path" to backupFile.absolutePath,
                "size" to backupFile.length(),
                "lastModified" to backupFile.lastModified(),
                "deviceId" to restoredData?.deviceId,
                "loanNumber" to restoredData?.loanNumber,
                "registrationStatus" to restoredData?.registrationStatus,
                "registeredAt" to restoredData?.registeredAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting backup info: ${e.message}")
            return@withContext mapOf(
                "error" to e.message
            )
        }
    }
    
    /**
     * Clear backup file (for testing or reset)
     */
    suspend fun clearBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupFile = getBackupFile()
            
            if (backupFile.exists()) {
                val deleted = backupFile.delete()
                
                if (deleted) {
                    Log.d(TAG, "Backup file deleted successfully")
                } else {
                    Log.w(TAG, "Failed to delete backup file")
                }
                
                return@withContext deleted
            } else {
                Log.d(TAG, "No backup file to delete")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing backup: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Export backup data as JSON string (for debugging/support)
     */
    suspend fun exportBackupAsJson(): String? = withContext(Dispatchers.IO) {
        try {
            val backupFile = getBackupFile()
            
            if (backupFile.exists()) {
                return@withContext backupFile.readText()
            } else {
                Log.w(TAG, "No backup file to export")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting backup: ${e.message}")
            return@withContext null
        }
    }
}
