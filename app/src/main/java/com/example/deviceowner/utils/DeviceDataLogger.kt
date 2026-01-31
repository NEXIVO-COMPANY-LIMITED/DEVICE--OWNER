package com.example.deviceowner.utils

import android.content.Context
import android.util.Log
import com.example.deviceowner.services.DeviceDataCollector
import com.example.deviceowner.utils.logging.LogManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Device Data Logger - Stores all real device information locally on phone
 * Creates JSON files with complete device data for testing and verification
 */
class DeviceDataLogger(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceDataLogger"
        private const val DEVICE_DATA_FOLDER = "DeviceData"
        private const val REGISTRATION_DATA_FOLDER = "RegistrationData"
    }
    
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    /**
     * Log complete device data to JSON file
     * This creates a permanent record of all device information
     */
    suspend fun logDeviceData(triggerEvent: String = "MANUAL_COLLECTION") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔍 Collecting and logging complete device data...")
                
                // Create device data directory
                val deviceDataDir = File(context.getExternalFilesDir(null), DEVICE_DATA_FOLDER)
                if (!deviceDataDir.exists()) {
                    deviceDataDir.mkdirs()
                }
                
                // Collect comprehensive device data
                val deviceCollector = DeviceDataCollector(context)
                val deviceData = deviceCollector.collectDeviceDataForLocalServer()
                
                // Add logging metadata
                val completeData = mapOf(
                    "collection_info" to mapOf(
                        "timestamp" to System.currentTimeMillis(),
                        "formatted_time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        "trigger_event" to triggerEvent,
                        "collection_source" to "DeviceDataLogger",
                        "data_version" to "1.0"
                    ),
                    "device_data" to deviceData
                )
                
                // Create filename with timestamp
                val timestamp = dateFormat.format(Date())
                val filename = "device_data_$timestamp.json"
                val deviceDataFile = File(deviceDataDir, filename)
                
                // Write to JSON file
                FileWriter(deviceDataFile).use { writer ->
                    gson.toJson(completeData, writer)
                }
                
                // Also create a "latest" file for easy access
                val latestFile = File(deviceDataDir, "device_data_latest.json")
                FileWriter(latestFile).use { writer ->
                    gson.toJson(completeData, writer)
                }
                
                // Log to LogManager
                LogManager.logDeviceInfo(deviceData as Map<String, Any?>)
                
                Log.d(TAG, "✅ Device data logged successfully:")
                Log.d(TAG, "   File: ${deviceDataFile.absolutePath}")
                Log.d(TAG, "   Size: ${deviceDataFile.length()} bytes")
                Log.d(TAG, "   Latest: ${latestFile.absolutePath}")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to log device data: ${e.message}", e)
                LogManager.logError(LogManager.LogCategory.DEVICE_INFO, "Failed to log device data: ${e.message}", "DEVICE_DATA_LOGGING", e)
            }
        }
    }
    
    /**
     * Log device registration attempt with all data
     */
    suspend fun logRegistrationAttempt(
        loanNumber: String,
        registrationData: Map<String, Any?>,
        success: Boolean,
        serverResponse: String? = null,
        error: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "📝 Logging registration attempt...")
                
                // Create registration data directory
                val registrationDir = File(context.getExternalFilesDir(null), REGISTRATION_DATA_FOLDER)
                if (!registrationDir.exists()) {
                    registrationDir.mkdirs()
                }
                
                // Create comprehensive registration log
                val registrationLog = mapOf(
                    "registration_info" to mapOf(
                        "timestamp" to System.currentTimeMillis(),
                        "formatted_time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        "loan_number" to loanNumber,
                        "success" to success,
                        "attempt_number" to getRegistrationAttemptNumber(loanNumber)
                    ),
                    "device_data" to registrationData,
                    "server_response" to mapOf(
                        "response_body" to serverResponse,
                        "error_message" to error,
                        "response_received" to (serverResponse != null)
                    ),
                    "network_info" to mapOf(
                        "base_url" to com.example.deviceowner.BuildConfig.BASE_URL,
                        "ssl_enabled" to true,
                        "certificate_pinning" to true
                    )
                )
                
                // Create filename with timestamp and status
                val timestamp = dateFormat.format(Date())
                val status = if (success) "SUCCESS" else "FAILED"
                val filename = "registration_${status}_${timestamp}.json"
                val registrationFile = File(registrationDir, filename)
                
                // Write to JSON file
                FileWriter(registrationFile).use { writer ->
                    gson.toJson(registrationLog, writer)
                }
                
                // Also create a "latest_attempt" file
                val latestFile = File(registrationDir, "latest_registration_attempt.json")
                FileWriter(latestFile).use { writer ->
                    gson.toJson(registrationLog, writer)
                }
                
                // Log to LogManager
                LogManager.logDeviceRegistration(
                    step = if (success) "REGISTRATION_SUCCESS" else "REGISTRATION_FAILED",
                    data = registrationData,
                    success = success,
                    error = error
                )
                
                Log.d(TAG, "✅ Registration attempt logged:")
                Log.d(TAG, "   File: ${registrationFile.absolutePath}")
                Log.d(TAG, "   Status: $status")
                Log.d(TAG, "   Loan Number: $loanNumber")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to log registration attempt: ${e.message}", e)
                LogManager.logError(LogManager.LogCategory.DEVICE_REGISTRATION, "Failed to log registration: ${e.message}", "REGISTRATION_LOGGING", e)
            }
        }
    }
    
    /**
     * Create daily device data snapshot
     */
    suspend fun createDailySnapshot() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "📸 Creating daily device data snapshot...")
                
                val snapshotDir = File(context.getExternalFilesDir(null), "DailySnapshots")
                if (!snapshotDir.exists()) {
                    snapshotDir.mkdirs()
                }
                
                // Collect device data
                val deviceCollector = DeviceDataCollector(context)
                val deviceData = deviceCollector.collectDeviceDataForLocalServer()
                
                // Add snapshot metadata
                val snapshotData = mapOf(
                    "snapshot_info" to mapOf(
                        "date" to dayFormat.format(Date()),
                        "timestamp" to System.currentTimeMillis(),
                        "formatted_time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        "snapshot_type" to "DAILY_AUTOMATIC"
                    ),
                    "device_data" to deviceData,
                    "app_status" to mapOf(
                        "is_device_owner" to com.example.deviceowner.device.DeviceOwnerManager(context).isDeviceOwner(),
                        "is_registered" to com.example.deviceowner.utils.SharedPreferencesManager(context).isDeviceRegistered(),
                        "base_url" to com.example.deviceowner.BuildConfig.BASE_URL
                    )
                )
                
                // Create filename with date
                val today = dayFormat.format(Date())
                val filename = "daily_snapshot_$today.json"
                val snapshotFile = File(snapshotDir, filename)
                
                // Write to JSON file
                FileWriter(snapshotFile).use { writer ->
                    gson.toJson(snapshotData, writer)
                }
                
                Log.d(TAG, "✅ Daily snapshot created: ${snapshotFile.absolutePath}")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create daily snapshot: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get all stored device data files
     */
    fun getStoredDeviceDataFiles(): List<File> {
        return try {
            val deviceDataDir = File(context.getExternalFilesDir(null), DEVICE_DATA_FOLDER)
            if (deviceDataDir.exists()) {
                deviceDataDir.listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device data files: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get all registration attempt files
     */
    fun getRegistrationAttemptFiles(): List<File> {
        return try {
            val registrationDir = File(context.getExternalFilesDir(null), REGISTRATION_DATA_FOLDER)
            if (registrationDir.exists()) {
                registrationDir.listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting registration files: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get latest device data as JSON string
     */
    fun getLatestDeviceDataJson(): String? {
        return try {
            val latestFile = File(context.getExternalFilesDir(null), "$DEVICE_DATA_FOLDER/device_data_latest.json")
            if (latestFile.exists()) {
                latestFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading latest device data: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get storage directory paths
     */
    fun getStorageInfo(): Map<String, String> {
        return mapOf(
            "device_data_dir" to File(context.getExternalFilesDir(null), DEVICE_DATA_FOLDER).absolutePath,
            "registration_data_dir" to File(context.getExternalFilesDir(null), REGISTRATION_DATA_FOLDER).absolutePath,
            "daily_snapshots_dir" to File(context.getExternalFilesDir(null), "DailySnapshots").absolutePath,
            "logs_dir" to LogManager.getLogDirectoryPath()
        )
    }
    
    /**
     * Get registration attempt number for a loan number
     */
    private fun getRegistrationAttemptNumber(loanNumber: String): Int {
        return try {
            val registrationDir = File(context.getExternalFilesDir(null), REGISTRATION_DATA_FOLDER)
            if (registrationDir.exists()) {
                val attempts = registrationDir.listFiles()?.count { file ->
                    file.name.contains(loanNumber) && file.name.endsWith(".json")
                } ?: 0
                attempts + 1
            } else {
                1
            }
        } catch (e: Exception) {
            1
        }
    }
    
    /**
     * Clean old data files (keep last N days)
     */
    fun cleanOldDataFiles(daysToKeep: Int = 30) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
                
                // Clean device data files
                val deviceDataDir = File(context.getExternalFilesDir(null), DEVICE_DATA_FOLDER)
                deviceDataDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime && !file.name.contains("latest")) {
                        file.delete()
                        Log.d(TAG, "Deleted old device data file: ${file.name}")
                    }
                }
                
                // Clean registration files
                val registrationDir = File(context.getExternalFilesDir(null), REGISTRATION_DATA_FOLDER)
                registrationDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime && !file.name.contains("latest")) {
                        file.delete()
                        Log.d(TAG, "Deleted old registration file: ${file.name}")
                    }
                }
                
                Log.d(TAG, "✅ Old data files cleaned (kept last $daysToKeep days)")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning old files: ${e.message}", e)
            }
        }
    }
}