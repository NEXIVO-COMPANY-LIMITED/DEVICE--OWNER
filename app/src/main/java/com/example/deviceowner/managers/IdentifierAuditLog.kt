package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class AuditEntry(
    val id: Int = 0,
    val type: String,
    val severity: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""
)

data class AuditAction(
    val id: Int = 0,
    val action: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

class IdentifierAuditLog(private val context: Context) {

    companion object {
        private const val TAG = "IdentifierAuditLog"
        private const val PREFS_NAME = "identifier_audit_log"
        private const val KEY_AUDIT_ENTRIES = "audit_entries"
        private const val KEY_MISMATCH_HISTORY = "mismatch_history"
        private const val KEY_ACTIONS = "actions"
        private const val MAX_ENTRIES = 1000
    }

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Log a mismatch incident
     */
    fun logMismatch(details: MismatchDetails) {
        try {
            val entry = AuditEntry(
                type = "MISMATCH",
                severity = details.severity,
                description = details.description,
                timestamp = details.timestamp,
                details = "Type: ${details.type}, Stored: ${details.storedValue}, Current: ${details.currentValue}"
            )
            
            addAuditEntry(entry)
            addMismatchToHistory(details)
            
            Log.d(TAG, "Mismatch logged: ${details.type}")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging mismatch", e)
        }
    }

    /**
     * Log an incident
     */
    fun logIncident(type: String, severity: String, details: String) {
        try {
            val entry = AuditEntry(
                type = type,
                severity = severity,
                description = "Incident: $type",
                details = details
            )
            
            addAuditEntry(entry)
            
            Log.d(TAG, "Incident logged: $type (Severity: $severity)")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging incident", e)
        }
    }

    /**
     * Log an action
     */
    fun logAction(action: String, description: String) {
        try {
            val auditAction = AuditAction(
                action = action,
                description = description
            )
            
            addAction(auditAction)
            
            Log.d(TAG, "Action logged: $action - $description")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging action", e)
        }
    }

    /**
     * Log verification result
     */
    fun logVerification(result: String, details: String) {
        try {
            val entry = AuditEntry(
                type = "VERIFICATION",
                severity = if (result == "PASS") "INFO" else "WARNING",
                description = "Verification: $result",
                details = details
            )
            
            addAuditEntry(entry)
            
            Log.d(TAG, "Verification logged: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging verification", e)
        }
    }

    /**
     * Add audit entry to log
     */
    private fun addAuditEntry(entry: AuditEntry) {
        try {
            val entries = getAuditEntries().toMutableList()
            
            // Add new entry
            entries.add(entry)
            
            // Keep only last MAX_ENTRIES
            if (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
            
            // Save to preferences
            val json = entries.joinToString("|") { entryToJson(it) }
            sharedPreferences.edit().putString(KEY_AUDIT_ENTRIES, json).apply()
            
            Log.d(TAG, "Audit entry added: ${entry.type}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding audit entry", e)
        }
    }

    /**
     * Add mismatch to history
     */
    private fun addMismatchToHistory(details: MismatchDetails) {
        try {
            val history = getMismatchHistory().toMutableList()
            
            // Add new mismatch
            history.add(details)
            
            // Keep only last 100 mismatches
            if (history.size > 100) {
                history.removeAt(0)
            }
            
            // Save to preferences
            val json = history.joinToString("|") { mismatchToJson(it) }
            sharedPreferences.edit().putString(KEY_MISMATCH_HISTORY, json).apply()
            
            Log.d(TAG, "Mismatch added to history")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to mismatch history", e)
        }
    }

    /**
     * Add action to log
     */
    private fun addAction(action: AuditAction) {
        try {
            val actions = getActions().toMutableList()
            
            // Add new action
            actions.add(action)
            
            // Keep only last 500 actions
            if (actions.size > 500) {
                actions.removeAt(0)
            }
            
            // Save to preferences
            val json = actions.joinToString("|") { actionToJson(it) }
            sharedPreferences.edit().putString(KEY_ACTIONS, json).apply()
            
            Log.d(TAG, "Action added to log")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding action", e)
        }
    }

    /**
     * Get all audit entries
     */
    fun getAuditEntries(): List<AuditEntry> {
        return try {
            val json = sharedPreferences.getString(KEY_AUDIT_ENTRIES, "") ?: ""
            if (json.isEmpty()) return emptyList()
            
            json.split("|").mapNotNull { jsonToEntry(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audit entries", e)
            emptyList()
        }
    }

    /**
     * Get mismatch history
     */
    fun getMismatchHistory(): List<MismatchDetails> {
        return try {
            val json = sharedPreferences.getString(KEY_MISMATCH_HISTORY, "") ?: ""
            if (json.isEmpty()) return emptyList()
            
            json.split("|").mapNotNull { jsonToMismatch(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting mismatch history", e)
            emptyList()
        }
    }

    /**
     * Get all actions
     */
    fun getActions(): List<AuditAction> {
        return try {
            val json = sharedPreferences.getString(KEY_ACTIONS, "") ?: ""
            if (json.isEmpty()) return emptyList()
            
            json.split("|").mapNotNull { jsonToAction(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting actions", e)
            emptyList()
        }
    }

    /**
     * Get audit trail summary
     */
    fun getAuditTrailSummary(): String {
        return try {
            val entries = getAuditEntries()
            val mismatches = getMismatchHistory()
            val actions = getActions()
            
            val summary = """
                ===== AUDIT TRAIL SUMMARY =====
                Total Entries: ${entries.size}
                Total Mismatches: ${mismatches.size}
                Total Actions: ${actions.size}
                
                Recent Entries (Last 5):
                ${entries.takeLast(5).joinToString("\n") { 
                    "${dateFormat.format(Date(it.timestamp))} - ${it.type} (${it.severity}): ${it.description}"
                }}
                
                Recent Mismatches (Last 5):
                ${mismatches.takeLast(5).joinToString("\n") { 
                    "${dateFormat.format(Date(it.timestamp))} - ${it.type}: ${it.description}"
                }}
                
                Recent Actions (Last 5):
                ${actions.takeLast(5).joinToString("\n") { 
                    "${dateFormat.format(Date(it.timestamp))} - ${it.action}: ${it.description}"
                }}
            """.trimIndent()
            
            Log.d(TAG, summary)
            summary
        } catch (e: Exception) {
            Log.e(TAG, "Error generating audit trail summary", e)
            "Error generating summary"
        }
    }

    /**
     * Clear all audit logs
     * PERMANENTLY PROTECTED: Cannot be cleared in any mode
     */
    fun clearAllLogs() {
        try {
            Log.e(TAG, "✗ Cannot clear audit logs - data protection permanently enabled")
            logIncident(
                type = "CLEAR_LOGS_BLOCKED",
                severity = "CRITICAL",
                details = "Attempt to clear audit logs blocked - data is permanently protected"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling clear logs request", e)
        }
    }

    /**
     * Clear mismatch history
     * PERMANENTLY PROTECTED: Cannot be cleared in any mode
     */
    fun clearMismatchHistory() {
        try {
            Log.e(TAG, "✗ Cannot clear mismatch history - data protection permanently enabled")
            logIncident(
                type = "CLEAR_HISTORY_BLOCKED",
                severity = "CRITICAL",
                details = "Attempt to clear mismatch history blocked - data is permanently protected"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling clear history request", e)
        }
    }

    /**
     * Export audit trail as string
     */
    suspend fun exportAuditTrail(): String {
        return withContext(Dispatchers.Default) {
            try {
                val entries = getAuditEntries()
                val mismatches = getMismatchHistory()
                val actions = getActions()
                
                val export = StringBuilder()
                export.append("===== AUDIT TRAIL EXPORT =====\n")
                export.append("Exported: ${dateFormat.format(Date())}\n\n")
                
                export.append("===== AUDIT ENTRIES (${entries.size}) =====\n")
                entries.forEach {
                    export.append("${dateFormat.format(Date(it.timestamp))} | ${it.type} | ${it.severity} | ${it.description}\n")
                }
                
                export.append("\n===== MISMATCHES (${mismatches.size}) =====\n")
                mismatches.forEach {
                    export.append("${dateFormat.format(Date(it.timestamp))} | ${it.type} | ${it.severity} | ${it.description}\n")
                }
                
                export.append("\n===== ACTIONS (${actions.size}) =====\n")
                actions.forEach {
                    export.append("${dateFormat.format(Date(it.timestamp))} | ${it.action} | ${it.description}\n")
                }
                
                export.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting audit trail", e)
                "Error exporting audit trail"
            }
        }
    }

    // ==================== JSON Serialization ====================

    private fun entryToJson(entry: AuditEntry): String {
        return "${entry.type}|${entry.severity}|${entry.description}|${entry.timestamp}|${entry.details}"
    }

    private fun jsonToEntry(json: String): AuditEntry? {
        return try {
            val parts = json.split("|")
            if (parts.size >= 4) {
                AuditEntry(
                    type = parts[0],
                    severity = parts[1],
                    description = parts[2],
                    timestamp = parts[3].toLong(),
                    details = if (parts.size > 4) parts[4] else ""
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun mismatchToJson(mismatch: MismatchDetails): String {
        return "${mismatch.type}|${mismatch.description}|${mismatch.storedValue}|${mismatch.currentValue}|${mismatch.timestamp}|${mismatch.severity}"
    }

    private fun jsonToMismatch(json: String): MismatchDetails? {
        return try {
            val parts = json.split("|")
            if (parts.size >= 6) {
                MismatchDetails(
                    type = MismatchType.valueOf(parts[0]),
                    description = parts[1],
                    storedValue = parts[2],
                    currentValue = parts[3],
                    timestamp = parts[4].toLong(),
                    severity = parts[5]
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun actionToJson(action: AuditAction): String {
        return "${action.action}|${action.description}|${action.timestamp}"
    }

    private fun jsonToAction(json: String): AuditAction? {
        return try {
            val parts = json.split("|")
            if (parts.size >= 3) {
                AuditAction(
                    action = parts[0],
                    description = parts[1],
                    timestamp = parts[2].toLong()
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
