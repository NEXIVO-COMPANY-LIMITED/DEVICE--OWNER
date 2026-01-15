package com.deviceowner.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.deviceowner.data.local.AppDatabase
import com.deviceowner.logging.StructuredLogger
import com.deviceowner.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Lock Reason Manager
 * 
 * Manages lock reason categorization, messaging, and analytics
 * Feature 4.4 Enhancement #2: Lock Reason Categorization
 * 
 * Features:
 * - Categorize lock reasons
 * - Generate user-friendly messages
 * - Track reason statistics
 * - Provide analytics
 * - Support custom messages
 * - Multi-language support ready
 */
class LockReasonManager(private val context: Context) {
    
    private val database = AppDatabase.getInstance(context)
    private val logger = StructuredLogger(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "lock_reason_manager",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "LockReasonManager"
        private const val KEY_SUPPORT_NUMBER = "support_number"
        private const val DEFAULT_SUPPORT_NUMBER = "1-800-SUPPORT"
        
        @Volatile
        private var instance: LockReasonManager? = null
        
        fun getInstance(context: Context): LockReasonManager {
            return instance ?: synchronized(this) {
                instance ?: LockReasonManager(context).also { instance = it }
            }
        }
    }
    
    /**
     * Get user-friendly message for lock reason
     * 
     * @param reason The lock reason
     * @param customDetails Optional custom details
     * @return User-friendly message
     */
    fun getLockReasonMessage(reason: LockReason, customDetails: String? = null): String {
        val message = reason.getMessage(customDetails)
        val supportMessage = getSupportMessage(reason)
        
        return "$message\n\n$supportMessage"
    }
    
    /**
     * Get support contact message
     */
    fun getSupportMessage(reason: LockReason): String {
        val supportNumber = prefs.getString(KEY_SUPPORT_NUMBER, DEFAULT_SUPPORT_NUMBER)
        return reason.getSupportMessage().replace("[SUPPORT_NUMBER]", supportNumber ?: DEFAULT_SUPPORT_NUMBER)
    }
    
    /**
     * Parse reason string and categorize
     * 
     * @param reasonText Free-form reason text
     * @return Categorized lock reason
     */
    fun categorizeReason(reasonText: String): LockReason {
        val lowerReason = reasonText.lowercase()
        
        return when {
            // Financial reasons
            lowerReason.contains("payment") && lowerReason.contains("overdue") -> 
                LockReason.PAYMENT_OVERDUE
            lowerReason.contains("loan") && lowerReason.contains("default") -> 
                LockReason.LOAN_DEFAULT
            lowerReason.contains("payment") || lowerReason.contains("overdue") -> 
                LockReason.PAYMENT_OVERDUE
            
            // Security reasons
            lowerReason.contains("tamper") || lowerReason.contains("tampering") -> 
                LockReason.DEVICE_TAMPERING
            lowerReason.contains("security") && lowerReason.contains("breach") -> 
                LockReason.SECURITY_BREACH
            lowerReason.contains("suspicious") -> 
                LockReason.SUSPICIOUS_ACTIVITY
            lowerReason.contains("unauthorized") -> 
                LockReason.UNAUTHORIZED_ACCESS
            lowerReason.contains("root") || lowerReason.contains("rooted") -> 
                LockReason.DEVICE_TAMPERING
            
            // Policy reasons
            lowerReason.contains("compliance") -> 
                LockReason.COMPLIANCE_VIOLATION
            lowerReason.contains("geofence") || lowerReason.contains("location") -> 
                LockReason.GEOFENCE_VIOLATION
            lowerReason.contains("contract") -> 
                LockReason.CONTRACT_BREACH
            
            // Administrative reasons
            lowerReason.contains("admin") -> 
                LockReason.ADMIN_ACTION
            lowerReason.contains("maintenance") -> 
                LockReason.ADMIN_ACTION
            lowerReason.contains("emergency") -> 
                LockReason.EMERGENCY_LOCK
            
            // Default
            else -> LockReason.OTHER
        }
    }
    
    /**
     * Create categorized lock command
     */
    fun createCategorizedLockCommand(
        deviceId: String,
        lockType: String,
        reasonText: String,
        customMessage: String? = null,
        adminId: String? = null,
        expiresAt: Long? = null,
        metadata: Map<String, String> = emptyMap()
    ): CategorizedLockCommand {
        val reason = categorizeReason(reasonText)
        
        return CategorizedLockCommand(
            id = UUID.randomUUID().toString(),
            deviceId = deviceId,
            lockType = lockType,
            reason = reason,
            description = reasonText,
            customMessage = customMessage,
            timestamp = System.currentTimeMillis(),
            adminId = adminId,
            expiresAt = expiresAt,
            metadata = metadata
        )
    }
    
    /**
     * Record lock reason for analytics
     */
    suspend fun recordLockReason(command: CategorizedLockCommand) {
        withContext(Dispatchers.IO) {
            try {
                // Save to preferences for quick access
                val key = "lock_reason_${command.reason.name}"
                val count = prefs.getInt(key, 0) + 1
                prefs.edit().putInt(key, count).apply()
                
                // Log for analytics
                logger.logInfo(
                    TAG,
                    "Lock reason recorded",
                    "lock_reason",
                    mapOf(
                        "reason" to command.reason.name,
                        "category" to command.reason.category,
                        "severity" to command.reason.severity.name,
                        "device_id" to command.deviceId
                    )
                )
                
                Log.d(TAG, "Lock reason recorded: ${command.reason.name} (count: $count)")
            } catch (e: Exception) {
                Log.e(TAG, "Error recording lock reason", e)
            }
        }
    }
    
    /**
     * Get statistics for a specific reason
     */
    suspend fun getReasonStatistics(reason: LockReason): LockReasonStatistics {
        return withContext(Dispatchers.IO) {
            try {
                val key = "lock_reason_${reason.name}"
                val count = prefs.getInt(key, 0)
                val lastOccurrence = prefs.getLong("${key}_last", 0L)
                
                LockReasonStatistics(
                    reason = reason,
                    count = count,
                    lastOccurrence = lastOccurrence,
                    averageDuration = 0L, // TODO: Calculate from lock history
                    totalDuration = 0L
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting reason statistics", e)
                LockReasonStatistics(
                    reason = reason,
                    count = 0,
                    lastOccurrence = 0L,
                    averageDuration = 0L,
                    totalDuration = 0L
                )
            }
        }
    }
    
    /**
     * Get all reason statistics
     */
    suspend fun getAllReasonStatistics(): List<LockReasonStatistics> {
        return withContext(Dispatchers.IO) {
            LockReason.values().map { reason ->
                getReasonStatistics(reason)
            }.sortedByDescending { it.count }
        }
    }
    
    /**
     * Get category summary
     */
    suspend fun getCategorySummary(): List<LockReasonCategorySummary> {
        return withContext(Dispatchers.IO) {
            try {
                val allStats = getAllReasonStatistics()
                
                // Group by category
                val grouped = allStats.groupBy { it.reason.category }
                
                grouped.map { (category, stats) ->
                    val totalLocks = stats.sumOf { it.count }
                    val mostCommon = stats.maxByOrNull { it.count }?.reason
                    val avgSeverity = stats.map { it.reason.severity.level }.average()
                    
                    LockReasonCategorySummary(
                        category = category,
                        totalLocks = totalLocks,
                        reasons = stats,
                        mostCommonReason = mostCommon,
                        averageSeverity = avgSeverity
                    )
                }.sortedByDescending { it.totalLocks }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting category summary", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get most common lock reason
     */
    suspend fun getMostCommonReason(): LockReason? {
        return withContext(Dispatchers.IO) {
            try {
                val stats = getAllReasonStatistics()
                stats.maxByOrNull { it.count }?.reason
            } catch (e: Exception) {
                Log.e(TAG, "Error getting most common reason", e)
                null
            }
        }
    }
    
    /**
     * Get reasons by severity
     */
    suspend fun getReasonsBySeverity(severity: LockSeverity): List<LockReasonStatistics> {
        return withContext(Dispatchers.IO) {
            try {
                val allStats = getAllReasonStatistics()
                allStats.filter { it.reason.severity == severity }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting reasons by severity", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get reasons by category
     */
    suspend fun getReasonsByCategory(category: String): List<LockReasonStatistics> {
        return withContext(Dispatchers.IO) {
            try {
                val allStats = getAllReasonStatistics()
                allStats.filter { it.reason.category == category }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting reasons by category", e)
                emptyList()
            }
        }
    }
    
    /**
     * Clear statistics
     */
    suspend fun clearStatistics() {
        withContext(Dispatchers.IO) {
            try {
                LockReason.values().forEach { reason ->
                    val key = "lock_reason_${reason.name}"
                    prefs.edit().remove(key).remove("${key}_last").apply()
                }
                Log.d(TAG, "Statistics cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing statistics", e)
            }
        }
    }
    
    /**
     * Set support number
     */
    fun setSupportNumber(number: String) {
        prefs.edit().putString(KEY_SUPPORT_NUMBER, number).apply()
        Log.d(TAG, "Support number updated: $number")
    }
    
    /**
     * Get support number
     */
    fun getSupportNumber(): String {
        return prefs.getString(KEY_SUPPORT_NUMBER, DEFAULT_SUPPORT_NUMBER) ?: DEFAULT_SUPPORT_NUMBER
    }
    
    /**
     * Get formatted lock message for overlay
     */
    fun getFormattedLockMessage(command: CategorizedLockCommand): String {
        val header = "🔒 DEVICE LOCKED"
        val reason = command.reason.displayMessage
        val details = command.customMessage?.let { "\n\n$it" } ?: ""
        val support = "\n\n${getSupportMessage(command.reason)}"
        val severity = "\n\nSeverity: ${command.reason.severity.name}"
        
        return "$header\n\n$reason$details$support$severity"
    }
    
    /**
     * Check if reason requires immediate action
     */
    fun requiresImmediateAction(reason: LockReason): Boolean {
        return reason.isUrgent()
    }
    
    /**
     * Get color for reason severity
     */
    fun getSeverityColor(reason: LockReason): String {
        return reason.severity.color
    }
}
