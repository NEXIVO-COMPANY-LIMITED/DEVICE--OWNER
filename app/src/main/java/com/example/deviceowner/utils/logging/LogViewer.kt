package com.example.deviceowner.utils.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility for viewing and sharing log files
 */
object LogViewer {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Get formatted log content for display
     */
    fun getFormattedLogContent(category: LogManager.LogCategory, maxLines: Int = 1000): String {
        val logFiles = LogManager.getLogFiles(category)
        if (logFiles.isEmpty()) {
            return "No log files found for ${category.name}"
        }
        
        val latestFile = logFiles.first()
        return try {
            val lines = latestFile.readLines()
            val displayLines = if (lines.size > maxLines) {
                lines.takeLast(maxLines)
            } else {
                lines
            }
            
            buildString {
                appendLine("=== ${category.name} LOGS ===")
                appendLine("File: ${latestFile.name}")
                appendLine("Size: ${latestFile.length()} bytes")
                appendLine("Last Modified: ${dateFormat.format(Date(latestFile.lastModified()))}")
                appendLine("Showing: ${displayLines.size} lines")
                appendLine("=".repeat(50))
                appendLine()
                displayLines.forEach { line ->
                    appendLine(line)
                }
            }
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }
    
    /**
     * Get log summary for all categories
     */
    fun getLogSummary(): String {
        return buildString {
            appendLine("=== DEVICE OWNER LOGS SUMMARY ===")
            appendLine("Generated: ${dateFormat.format(Date())}")
            appendLine("Log Directory: ${LogManager.getLogDirectoryPath()}")
            appendLine()
            
            LogManager.LogCategory.values().forEach { category ->
                val files = LogManager.getLogFiles(category)
                appendLine("${category.name}:")
                appendLine("  Files: ${files.size}")
                if (files.isNotEmpty()) {
                    val totalSize = files.sumOf { it.length() }
                    val latestFile = files.first()
                    appendLine("  Total Size: ${formatFileSize(totalSize)}")
                    appendLine("  Latest: ${latestFile.name}")
                    appendLine("  Last Modified: ${dateFormat.format(Date(latestFile.lastModified()))}")
                }
                appendLine()
            }
        }
    }
    
    /**
     * Share log files via intent
     */
    fun shareLogs(context: Context, category: LogManager.LogCategory? = null) {
        try {
            val filesToShare = if (category != null) {
                LogManager.getLogFiles(category)
            } else {
                LogManager.getAllLogFiles().values.flatten()
            }
            
            if (filesToShare.isEmpty()) {
                return
            }
            
            val uris = filesToShare.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            
            val intent = Intent().apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Device Owner Logs")
                putExtra(Intent.EXTRA_TEXT, "Device Owner application logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "Share Logs"))
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.ERRORS, "Failed to share logs: ${e.message}", "LOG_SHARING", e)
        }
    }
    
    /**
     * Get recent errors summary
     */
    fun getRecentErrors(hours: Int = 24): String {
        val cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
        val errorFiles = LogManager.getLogFiles(LogManager.LogCategory.ERRORS)
        
        return buildString {
            appendLine("=== RECENT ERRORS (Last $hours hours) ===")
            appendLine("Generated: ${dateFormat.format(Date())}")
            appendLine()
            
            errorFiles.forEach { file ->
                if (file.lastModified() > cutoffTime) {
                    try {
                        val lines = file.readLines()
                        val recentLines = lines.filter { line ->
                            // Simple timestamp parsing - adjust as needed
                            line.contains("ERROR")
                        }
                        
                        if (recentLines.isNotEmpty()) {
                            appendLine("File: ${file.name}")
                            appendLine("Errors: ${recentLines.size}")
                            appendLine("---")
                            recentLines.take(10).forEach { line ->
                                appendLine(line)
                            }
                            appendLine()
                        }
                    } catch (e: Exception) {
                        appendLine("Error reading ${file.name}: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * Clear all logs
     */
    fun clearAllLogs(): Boolean {
        return try {
            LogManager.getAllLogFiles().values.flatten().forEach { file ->
                file.delete()
            }
            LogManager.logInfo(LogManager.LogCategory.GENERAL, "All logs cleared", "LOG_MANAGEMENT")
            true
        } catch (e: Exception) {
            LogManager.logError(LogManager.LogCategory.ERRORS, "Failed to clear logs: ${e.message}", "LOG_MANAGEMENT", e)
            false
        }
    }
    
    /**
     * Get log statistics
     */
    fun getLogStatistics(): Map<String, Any> {
        val allFiles = LogManager.getAllLogFiles()
        val totalFiles = allFiles.values.sumOf { it.size }
        val totalSize = allFiles.values.flatten().sumOf { it.length() }
        
        return mapOf(
            "totalFiles" to totalFiles,
            "totalSize" to formatFileSize(totalSize),
            "categories" to allFiles.mapValues { (_, files) ->
                mapOf(
                    "fileCount" to files.size,
                    "totalSize" to formatFileSize(files.sumOf { it.length() }),
                    "latestFile" to (files.firstOrNull()?.name ?: "None")
                )
            },
            "logDirectory" to LogManager.getLogDirectoryPath(),
            "generatedAt" to dateFormat.format(Date())
        )
    }
}