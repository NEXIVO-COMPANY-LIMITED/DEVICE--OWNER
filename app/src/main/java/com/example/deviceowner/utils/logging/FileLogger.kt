package com.example.deviceowner.utils.logging

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * FileLogger - Comprehensive file-based logging system
 * 
 * Logs all Device Owner installation steps and app activities to a file
 * stored on the device for debugging and troubleshooting.
 * 
 * Log files are stored in: /Android/data/com.example.deviceowner/files/logs/
 */
class FileLogger private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FileLogger"
        private const val LOG_DIR_NAME = "logs"
        private const val LOG_FILE_PREFIX = "device_owner_install"
        private const val MAX_LOG_FILE_SIZE = 10 * 1024 * 1024 // 10MB per file
        private const val MAX_LOG_FILES = 10 // Keep last 10 log files
        
        @Volatile
        private var INSTANCE: FileLogger? = null
        
        fun getInstance(context: Context): FileLogger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileLogger(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private val lock = ReentrantLock()
    private var currentLogFile: File? = null
    private var logFileWriter: FileWriter? = null

    init {
        initializeLogDirectory()
        createNewLogFile()
    }

    /**
     * Initialize log directory on device storage
     */
    private fun initializeLogDirectory() {
        try {
            val logDir = getLogDirectory()
            if (!logDir.exists()) {
                val created = logDir.mkdirs()
                if (created) {
                    Log.d(TAG, "✓ Log directory created: ${logDir.absolutePath}")
                    writeToFile("SYSTEM", TAG, "Log directory initialized: ${logDir.absolutePath}")
                } else {
                    Log.e(TAG, "✗ Failed to create log directory")
                }
            } else {
                Log.d(TAG, "✓ Log directory exists: ${logDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing log directory: ${e.message}", e)
        }
    }

    /**
     * Get log directory path
     * Uses app-specific external storage for Android 10+, falls back to external storage
     */
    private fun getLogDirectory(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Use app-specific directory (no permission needed)
            File(context.getExternalFilesDir(null), LOG_DIR_NAME)
        } else {
            // Android 9 and below - Use external storage
            val externalDir = Environment.getExternalStorageDirectory()
            File(externalDir, "Android/data/${context.packageName}/files/$LOG_DIR_NAME")
        }
    }

    /**
     * Create a new log file with timestamp
     */
    private fun createNewLogFile() {
        lock.withLock {
            try {
                // Close existing file if open
                logFileWriter?.close()

                val logDir = getLogDirectory()
                val timestamp = fileDateFormat.format(Date())
                val fileName = "${LOG_FILE_PREFIX}_$timestamp.log"
                currentLogFile = File(logDir, fileName)

                logFileWriter = FileWriter(currentLogFile, true)
                
                val header = buildString {
                    appendLine("=".repeat(80))
                    appendLine("DEVICE OWNER INSTALLATION LOG")
                    appendLine("=".repeat(80))
                    appendLine("App Package: ${context.packageName}")
                    appendLine("Device Model: ${Build.MODEL}")
                    appendLine("Manufacturer: ${Build.MANUFACTURER}")
                    appendLine("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Log Started: ${dateFormat.format(Date())}")
                    appendLine("=".repeat(80))
                    appendLine()
                }
                
                logFileWriter?.write(header)
                logFileWriter?.flush()
                
                Log.d(TAG, "✓ New log file created: ${currentLogFile?.absolutePath}")
                
                // Clean up old log files
                cleanupOldLogFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating log file: ${e.message}", e)
            }
        }
    }

    /**
     * Clean up old log files, keeping only the most recent ones
     */
    private fun cleanupOldLogFiles() {
        try {
            val logDir = getLogDirectory()
            val logFiles = logDir.listFiles { file ->
                file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(".log")
            } ?: return

            if (logFiles.size > MAX_LOG_FILES) {
                // Sort by modification time (oldest first)
                logFiles.sortBy { it.lastModified() }
                
                // Delete oldest files
                val filesToDelete = logFiles.size - MAX_LOG_FILES
                for (i in 0 until filesToDelete) {
                    logFiles[i].delete()
                    Log.d(TAG, "Deleted old log file: ${logFiles[i].name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old log files: ${e.message}", e)
        }
    }

    /**
     * Write log entry to file
     */
    private fun writeToFile(level: String, tag: String, message: String, throwable: Throwable? = null) {
        lock.withLock {
            try {
                // Check if current file is too large
                currentLogFile?.let { file ->
                    if (file.length() > MAX_LOG_FILE_SIZE) {
                        Log.d(TAG, "Log file size exceeded, creating new file")
                        createNewLogFile()
                    }
                }

                val timestamp = dateFormat.format(Date())
                val logEntry = buildString {
                    append("[$timestamp] ")
                    append("[$level] ")
                    append("[$tag] ")
                    append(message)
                    
                    throwable?.let {
                        appendLine()
                        append("Exception: ${it.javaClass.simpleName}")
                        append("Message: ${it.message}")
                        appendLine()
                        append("Stack Trace:")
                        it.stackTrace.forEach { element ->
                            appendLine("  at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
                        }
                    }
                    
                    appendLine()
                }

                logFileWriter?.write(logEntry)
                logFileWriter?.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Error writing to log file: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error writing to log file: ${e.message}", e)
            }
        }
    }

    /**
     * Log debug message
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("DEBUG", tag, message)
    }

    /**
     * Log info message
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("INFO", tag, message)
    }

    /**
     * Log warning message
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToFile("WARN", tag, message)
    }

    /**
     * Log error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        writeToFile("ERROR", tag, message, throwable)
    }

    /**
     * Log critical/important message (for Device Owner installation steps)
     */
    fun critical(tag: String, message: String) {
        val criticalMessage = "*** CRITICAL: $message ***"
        Log.e(tag, criticalMessage)
        writeToFile("CRITICAL", tag, criticalMessage)
    }

    /**
     * Log Device Owner installation step
     */
    fun logInstallationStep(step: String, details: String = "") {
        val message = "INSTALLATION STEP: $step${if (details.isNotEmpty()) " - $details" else ""}"
        critical("DeviceOwnerInstall", message)
    }

    /**
     * Log provisioning phase
     */
    fun logProvisioningPhase(phase: String, status: String) {
        val message = "PROVISIONING PHASE: $phase - Status: $status"
        critical("Provisioning", message)
    }

    /**
     * Log policy enforcement
     */
    fun logPolicyEnforcement(policy: String, status: String) {
        val message = "POLICY ENFORCEMENT: $policy - Status: $status"
        critical("Policy", message)
    }

    /**
     * Log service status
     */
    fun logServiceStatus(serviceName: String, status: String) {
        val message = "SERVICE STATUS: $serviceName - Status: $status"
        i("Service", message)
    }

    /**
     * Get current log file path
     */
    fun getCurrentLogFilePath(): String? {
        return currentLogFile?.absolutePath
    }

    /**
     * Get log directory path
     */
    fun getLogDirectoryPath(): String {
        return getLogDirectory().absolutePath
    }

    /**
     * Get all log files
     */
    fun getAllLogFiles(): List<File> {
        return try {
            val logDir = getLogDirectory()
            logDir.listFiles { file ->
                file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting log files: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Read log file content
     */
    fun readLogFile(file: File): String? {
        return try {
            file.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading log file: ${e.message}", e)
            null
        }
    }

    /**
     * Close log file writer
     */
    fun close() {
        lock.withLock {
            try {
                logFileWriter?.close()
                logFileWriter = null
            } catch (e: Exception) {
                Log.e(TAG, "Error closing log file: ${e.message}", e)
            }
        }
    }
}
