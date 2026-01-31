package com.example.deviceowner.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enterprise-grade Diagnostic Logger.
 * Optimized for USB access: Stores logs in External Storage (Android/data/com.example.deviceowner/files/Logs)
 * so they can be retrieved via USB File Transfer on a PC.
 */
object DiagnosticLogger {
    private const val TAG = "DiagnosticLogger"
    private const val LOG_DIR = "Logs"
    private const val LOG_FILE_NAME = "device_admin_diagnostics.log"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Logs a message to both Logcat and the USB-accessible log file.
     */
    fun log(context: Context, level: String, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] [$level] [$tag]: $message ${throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""}\n"
        
        // 1. Output to Logcat for real-time debugging
        when (level) {
            "INFO" -> Log.i(tag, message)
            "WARN" -> Log.w(tag, message)
            "ERROR" -> Log.e(tag, message, throwable)
            else -> Log.d(tag, message)
        }

        // 2. Write to USB-accessible file
        try {
            // Use getExternalFilesDir so it is visible via USB MTP
            val externalDir = File(context.getExternalFilesDir(null), LOG_DIR)
            if (!externalDir.exists()) externalDir.mkdirs()
            
            val logFile = File(externalDir, LOG_FILE_NAME)
            FileOutputStream(logFile, true).use { fos ->
                fos.write(logLine.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to USB folder", e)
        }
    }

    fun info(context: Context, tag: String, message: String) = log(context, "INFO", tag, message)
    fun warn(context: Context, tag: String, message: String) = log(context, "WARN", tag, message)
    fun error(context: Context, tag: String, message: String, throwable: Throwable? = null) = log(context, "ERROR", tag, message, throwable)

    /**
     * Path where you can find the file on your PC:
     * Internal Storage > Android > data > com.example.deviceowner > files > Logs > device_admin_diagnostics.log
     */
    fun getLogFilePath(context: Context): String {
        return File(File(context.getExternalFilesDir(null), LOG_DIR), LOG_FILE_NAME).absolutePath
    }

    /**
     * Prepares a full report for debugging.
     */
    fun generateFullReport(context: Context): String {
        val sb = StringBuilder()
        sb.append("=== PAYOPLAN SECURITY REPORT ===\n")
        sb.append("Device: ${android.os.Build.MODEL}\n")
        sb.append("Serial: ${android.os.Build.getSerial()}\n")
        sb.append("\n--- LOGS ---\n")
        val logFile = File(File(context.getExternalFilesDir(null), LOG_DIR), LOG_FILE_NAME)
        if (logFile.exists()) sb.append(logFile.readText())
        return sb.toString()
    }
}
