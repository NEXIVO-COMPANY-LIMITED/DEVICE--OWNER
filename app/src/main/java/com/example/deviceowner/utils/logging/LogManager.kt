package com.microspace.payo.utils.logging

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Comprehensive logging manager that captures all app processes
 * Creates separate log files for different components and processes
 */
object LogManager {
    
    private const val TAG = "LogManager"
    private const val LOG_FOLDER = "DeviceOwnerLogs"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    // Log categories
    enum class LogCategory(val folderName: String, val fileName: String) {
        DEVICE_REGISTRATION("registration", "device_registration"),
        DEVICE_OWNER("device_owner", "device_owner_operations"),
        API_CALLS("api", "api_requests_responses"),
        DEVICE_INFO("device_info", "device_information"),
        HEARTBEAT("heartbeat", "heartbeat_service"),
        SECURITY("security", "security_monitoring"),
        PROVISIONING("provisioning", "device_provisioning"),
        SYNC("sync", "offline_sync"),
        ERRORS("errors", "application_errors"),
        GENERAL("general", "general_logs"),
        JSON_LOGS("json_logs", "raw_json_data")
    }
    
    private lateinit var appContext: Context
    private lateinit var logBaseDir: File

    /**
     * Optional callback to send logs to server (e.g. sponsa_backend tech API).
     * Set from Application after initialize(). Called for ERROR and WARN only.
     * Signature: (category, logLevel, message, extraData)
     */
    @Volatile
    private var onRemoteLogCallback: ((LogCategory, String, String, Map<String, Any?>?) -> Unit)? = null

    fun setOnRemoteLogCallback(callback: ((LogCategory, String, String, Map<String, Any?>?) -> Unit)?) {
        onRemoteLogCallback = callback
    }

    /**
     * Initialize the logging system
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        setupLogDirectories()
        logInfo(LogCategory.GENERAL, "LogManager initialized", "System startup")
    }
    
    /**
     * Setup log directories structure
     */
    private fun setupLogDirectories() {
        try {
            // Create base log directory in app's external files directory
            logBaseDir = File(appContext.getExternalFilesDir(null), LOG_FOLDER)
            
            if (!logBaseDir.exists()) {
                logBaseDir.mkdirs()
            }
            
            // Create subdirectories for each category
            LogCategory.values().forEach { category ->
                val categoryDir = File(logBaseDir, category.folderName)
                if (!categoryDir.exists()) {
                    categoryDir.mkdirs()
                }
            }
            
            Log.d(TAG, "Log directories created at: ${logBaseDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup log directories: ${e.message}", e)
        }
    }
    
    /**
     * Specifically log raw JSON data for debugging
     */
    fun logJsonData(tag: String, json: String) {
        val entry = buildString {
            appendLine("--- NEW JSON SUBMISSION [$tag] ---")
            appendLine("Timestamp: ${dateFormat.format(Date())}")
            appendLine("Data:")
            appendLine(json)
            appendLine("--- END JSON ---")
            appendLine()
        }
        logInfo(LogCategory.JSON_LOGS, entry, tag)
    }

    /**
     * Log information message
     */
    fun logInfo(category: LogCategory, message: String, process: String = "") {
        writeLog(category, "INFO", message, process, null)
    }
    
    /**
     * Log warning message
     */
    fun logWarning(category: LogCategory, message: String, process: String = "") {
        writeLog(category, "WARN", message, process, null)
    }
    
    /**
     * Log error message
     */
    fun logError(category: LogCategory, message: String, process: String = "", throwable: Throwable? = null) {
        writeLog(category, "ERROR", message, process, throwable)
    }
    
    /**
     * Log debug message
     */
    fun logDebug(category: LogCategory, message: String, process: String = "") {
        writeLog(category, "DEBUG", message, process, null)
    }
    
    /**
     * Log API request/response
     */
    fun logApiCall(endpoint: String, method: String, requestBody: String?, responseCode: Int?, responseBody: String?, error: String? = null) {
        val logMessage = buildString {
            appendLine("=== API CALL ===")
            appendLine("Endpoint: $endpoint")
            appendLine("Method: $method")
            appendLine("Request Body: ${requestBody ?: "None"}")
            appendLine("Response Code: ${responseCode ?: "N/A"}")
            appendLine("Response Body: ${responseBody ?: "None"}")
            if (error != null) {
                appendLine("Error: $error")
            }
            appendLine("=== END API CALL ===")
        }
        
        if (error != null) {
            logError(LogCategory.API_CALLS, logMessage, "API_${method}_${endpoint.replace("/", "_")}")
        } else {
            logInfo(LogCategory.API_CALLS, logMessage, "API_${method}_${endpoint.replace("/", "_")}")
        }
    }
    
    /**
     * Log device registration process
     */
    fun logDeviceRegistration(step: String, data: Map<String, Any?>, success: Boolean = true, error: String? = null) {
        val logMessage = buildString {
            appendLine("=== DEVICE REGISTRATION: $step ===")
            appendLine("Timestamp: ${dateFormat.format(Date())}")
            appendLine("Success: $success")
            data.forEach { (key, value) ->
                appendLine("$key: $value")
            }
            if (error != null) {
                appendLine("Error: $error")
            }
            appendLine("=== END REGISTRATION STEP ===")
        }
        
        if (success) {
            logInfo(LogCategory.DEVICE_REGISTRATION, logMessage, "REGISTRATION_$step")
        } else {
            logError(LogCategory.DEVICE_REGISTRATION, logMessage, "REGISTRATION_$step")
        }
    }
    
    /**
     * Log device owner operations
     */
    fun logDeviceOwnerOperation(operation: String, result: Boolean, details: String = "") {
        val logMessage = buildString {
            appendLine("=== DEVICE OWNER OPERATION ===")
            appendLine("Operation: $operation")
            appendLine("Result: $result")
            appendLine("Details: $details")
            appendLine("Timestamp: ${dateFormat.format(Date())}")
            appendLine("=== END OPERATION ===")
        }
        
        if (result) {
            logInfo(LogCategory.DEVICE_OWNER, logMessage, "DO_$operation")
        } else {
            logError(LogCategory.DEVICE_OWNER, logMessage, "DO_$operation")
        }
    }
    
    /**
     * Log device information collection
     */
    fun logDeviceInfo(deviceData: Map<String, Any?>) {
        val logMessage = buildString {
            appendLine("=== DEVICE INFORMATION COLLECTED ===")
            appendLine("Timestamp: ${dateFormat.format(Date())}")
            deviceData.forEach { (key, value) ->
                appendLine("$key: $value")
            }
            appendLine("=== END DEVICE INFO ===")
        }
        
        logInfo(LogCategory.DEVICE_INFO, logMessage, "DEVICE_INFO_COLLECTION")
    }
    
    /**
     * Write log to file
     */
    private fun writeLog(category: LogCategory, level: String, message: String, process: String, throwable: Throwable?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!::logBaseDir.isInitialized) return@launch
                
                val categoryDir = File(logBaseDir, category.folderName)
                val today = fileNameFormat.format(Date())
                val logFile = File(categoryDir, "${category.fileName}_$today.log")
                
                val timestamp = dateFormat.format(Date())
                val processTag = if (process.isNotEmpty()) "[$process]" else ""
                
                val logEntry = buildString {
                    appendLine("$timestamp $level $processTag: $message")
                    
                    if (throwable != null) {
                        appendLine("Exception: ${throwable.javaClass.simpleName}")
                        appendLine("Message: ${throwable.message}")
                        appendLine("Stack trace:")
                        throwable.stackTrace.forEach { element ->
                            appendLine("  at $element")
                        }
                    }
                    appendLine("---")
                }
                
                // Also log to Android logcat
                when (level) {
                    "ERROR" -> Log.e("${category.name}$processTag", message, throwable)
                    "WARN" -> Log.w("${category.name}$processTag", message)
                    "INFO" -> Log.i("${category.name}$processTag", message)
                    "DEBUG" -> Log.d("${category.name}$processTag", message)
                }
                
                // Write to file
                FileWriter(logFile, true).use { writer ->
                    writer.write(logEntry)
                    writer.flush()
                }

                // Send ERROR and WARNING to server if callback set (sponsa_backend tech API)
                if (level == "ERROR" || level == "WARN") {
                    try {
                        val callback = onRemoteLogCallback
                        if (callback != null) {
                            val logLevelForServer = when (level) {
                                "ERROR" -> "Error"
                                "WARN" -> "Warning"
                                else -> "Normal"
                            }
                            val extra = mutableMapOf<String, Any?>()
                            if (process.isNotEmpty()) extra["process"] = process
                            throwable?.let { extra["exception"] = it.javaClass.simpleName }
                            callback(category, logLevelForServer, message, if (extra.isEmpty()) null else extra)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Remote log callback error: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get log files for a specific category
     */
    fun getLogFiles(category: LogCategory): List<File> {
        return try {
            val categoryDir = File(logBaseDir, category.folderName)
            categoryDir.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get log files: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get all log files
     */
    fun getAllLogFiles(): Map<LogCategory, List<File>> {
        return LogCategory.values().associateWith { getLogFiles(it) }
    }
    
    /**
     * Clear old log files (older than specified days)
     */
    fun clearOldLogs(daysToKeep: Int = 7) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
                
                LogCategory.values().forEach { category ->
                    val categoryDir = File(logBaseDir, category.folderName)
                    categoryDir.listFiles()?.forEach { file ->
                        if (file.lastModified() < cutoffTime) {
                            file.delete()
                            Log.d(TAG, "Deleted old log file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear old logs: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get log directory path
     */
    fun getLogDirectoryPath(): String {
        return if (::logBaseDir.isInitialized) logBaseDir.absolutePath else "Not initialized"
    }
    
    /**
     * Export logs as zip file
     */
    fun exportLogs(callback: (File?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zipFile = File(logBaseDir.parent, "DeviceOwnerLogs_${fileNameFormat.format(Date())}.zip")
                // Implementation for zipping logs would go here
                callback(zipFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs: ${e.message}", e)
                callback(null)
            }
        }
    }
}
