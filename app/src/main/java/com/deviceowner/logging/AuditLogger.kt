package com.deviceowner.logging

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuditLogger handles audit logging for security-sensitive operations
 */
class AuditLogger(private val context: Context) {
    
    companion object {
        private const val TAG = "AuditLog"
    }
    
    /**
     * Log an informational event
     */
    fun log(tag: String, message: String, details: Map<String, String> = emptyMap()) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val detailsStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val logMessage = "[$timestamp] $message${if (detailsStr.isNotEmpty()) " | $detailsStr" else ""}"
        Log.i(tag, logMessage)
    }
    
    /**
     * Log a warning event
     */
    fun warn(tag: String, message: String, details: Map<String, String> = emptyMap()) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val detailsStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val logMessage = "[$timestamp] $message${if (detailsStr.isNotEmpty()) " | $detailsStr" else ""}"
        Log.w(tag, logMessage)
    }
    
    /**
     * Log an error event
     */
    fun error(tag: String, message: String, details: String = "") {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val logMessage = "[$timestamp] $message${if (details.isNotEmpty()) " | $details" else ""}"
        Log.e(tag, logMessage)
    }
}
