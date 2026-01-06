package com.deviceowner.logging

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * StructuredLogger provides structured logging with operation tracking
 */
class StructuredLogger(private val context: Context) {
    
    companion object {
        private const val TAG = "StructuredLog"
    }
    
    /**
     * Log informational message
     */
    fun logInfo(
        tag: String,
        message: String,
        operation: String = "",
        details: Map<String, String> = emptyMap()
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val detailsStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val operationStr = if (operation.isNotEmpty()) " | operation=$operation" else ""
        val logMessage = "[$timestamp] $message$operationStr${if (detailsStr.isNotEmpty()) " | $detailsStr" else ""}"
        Log.i(tag, logMessage)
    }
    
    /**
     * Log warning message
     */
    fun logWarning(
        tag: String,
        message: String,
        operation: String = "",
        details: Map<String, String> = emptyMap()
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val detailsStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val operationStr = if (operation.isNotEmpty()) " | operation=$operation" else ""
        val logMessage = "[$timestamp] $message$operationStr${if (detailsStr.isNotEmpty()) " | $detailsStr" else ""}"
        Log.w(tag, logMessage)
    }
    
    /**
     * Log error message
     */
    fun logError(
        tag: String,
        code: String,
        message: String,
        operation: String = "",
        cause: String? = null,
        details: Map<String, String> = emptyMap()
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val detailsStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val operationStr = if (operation.isNotEmpty()) " | operation=$operation" else ""
        val causeStr = if (cause != null) " | cause=$cause" else ""
        val logMessage = "[$timestamp] [$code] $message$operationStr$causeStr${if (detailsStr.isNotEmpty()) " | $detailsStr" else ""}"
        Log.e(tag, logMessage)
    }
}
