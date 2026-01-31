package com.example.deviceowner.utils.logging

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Process-specific logger that tracks individual operations
 * Each process gets its own log session with unique ID
 */
class ProcessLogger(
    private val processName: String,
    private val category: LogManager.LogCategory
) {
    
    private val sessionId = UUID.randomUUID().toString().take(8)
    private val startTime = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val steps = mutableListOf<ProcessStep>()
    
    data class ProcessStep(
        val stepName: String,
        val timestamp: Long,
        val success: Boolean,
        val data: Map<String, Any?>,
        val error: String? = null
    )
    
    init {
        LogManager.logInfo(category, "Process started: $processName [Session: $sessionId]", processName)
    }
    
    /**
     * Log a successful step in the process
     */
    fun logStep(stepName: String, data: Map<String, Any?> = emptyMap()) {
        val step = ProcessStep(stepName, System.currentTimeMillis(), true, data)
        steps.add(step)
        
        val message = buildString {
            appendLine("STEP: $stepName")
            appendLine("Session: $sessionId")
            appendLine("Duration: ${step.timestamp - startTime}ms")
            data.forEach { (key, value) ->
                appendLine("  $key: $value")
            }
        }
        
        LogManager.logInfo(category, message, "${processName}_$stepName")
    }
    
    /**
     * Log a failed step in the process
     */
    fun logError(stepName: String, error: String, data: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        val step = ProcessStep(stepName, System.currentTimeMillis(), false, data, error)
        steps.add(step)
        
        val message = buildString {
            appendLine("STEP FAILED: $stepName")
            appendLine("Session: $sessionId")
            appendLine("Duration: ${step.timestamp - startTime}ms")
            appendLine("Error: $error")
            data.forEach { (key, value) ->
                appendLine("  $key: $value")
            }
        }
        
        LogManager.logError(category, message, "${processName}_$stepName", throwable)
    }
    
    /**
     * Log process completion
     */
    fun logCompletion(success: Boolean, finalData: Map<String, Any?> = emptyMap()) {
        val totalDuration = System.currentTimeMillis() - startTime
        val successfulSteps = steps.count { it.success }
        val failedSteps = steps.count { !it.success }
        
        val message = buildString {
            appendLine("PROCESS COMPLETED: $processName")
            appendLine("Session: $sessionId")
            appendLine("Total Duration: ${totalDuration}ms")
            appendLine("Success: $success")
            appendLine("Steps Completed: $successfulSteps")
            appendLine("Steps Failed: $failedSteps")
            appendLine("Final Data:")
            finalData.forEach { (key, value) ->
                appendLine("  $key: $value")
            }
            appendLine("Step Summary:")
            steps.forEach { step ->
                val status = if (step.success) "✓" else "✗"
                val duration = step.timestamp - startTime
                appendLine("  $status ${step.stepName} (${duration}ms)")
                if (step.error != null) {
                    appendLine("    Error: ${step.error}")
                }
            }
        }
        
        if (success) {
            LogManager.logInfo(category, message, "${processName}_COMPLETION")
        } else {
            LogManager.logError(category, message, "${processName}_COMPLETION")
        }
    }
    
    /**
     * Get process summary
     */
    fun getSummary(): Map<String, Any> {
        return mapOf(
            "processName" to processName,
            "sessionId" to sessionId,
            "startTime" to dateFormat.format(Date(startTime)),
            "duration" to "${System.currentTimeMillis() - startTime}ms",
            "totalSteps" to steps.size,
            "successfulSteps" to steps.count { it.success },
            "failedSteps" to steps.count { !it.success },
            "steps" to steps.map { step ->
                mapOf(
                    "name" to step.stepName,
                    "success" to step.success,
                    "timestamp" to dateFormat.format(Date(step.timestamp)),
                    "data" to step.data,
                    "error" to step.error
                )
            }
        )
    }
}

/**
 * Factory for creating process loggers
 */
object ProcessLoggerFactory {
    
    /**
     * Create logger for device registration process
     */
    fun createRegistrationLogger(): ProcessLogger {
        return ProcessLogger("DEVICE_REGISTRATION", LogManager.LogCategory.DEVICE_REGISTRATION)
    }
    
    /**
     * Create logger for device owner operations
     */
    fun createDeviceOwnerLogger(): ProcessLogger {
        return ProcessLogger("DEVICE_OWNER_OPS", LogManager.LogCategory.DEVICE_OWNER)
    }
    
    /**
     * Create logger for API operations
     */
    fun createApiLogger(endpoint: String): ProcessLogger {
        return ProcessLogger("API_${endpoint.replace("/", "_")}", LogManager.LogCategory.API_CALLS)
    }
    
    /**
     * Create logger for device info collection
     */
    fun createDeviceInfoLogger(): ProcessLogger {
        return ProcessLogger("DEVICE_INFO_COLLECTION", LogManager.LogCategory.DEVICE_INFO)
    }
    
    /**
     * Create logger for heartbeat service
     */
    fun createHeartbeatLogger(): ProcessLogger {
        return ProcessLogger("HEARTBEAT_SERVICE", LogManager.LogCategory.HEARTBEAT)
    }
    
    /**
     * Create logger for security monitoring
     */
    fun createSecurityLogger(): ProcessLogger {
        return ProcessLogger("SECURITY_MONITORING", LogManager.LogCategory.SECURITY)
    }
    
    /**
     * Create logger for provisioning
     */
    fun createProvisioningLogger(): ProcessLogger {
        return ProcessLogger("DEVICE_PROVISIONING", LogManager.LogCategory.PROVISIONING)
    }
    
    /**
     * Create logger for sync operations
     */
    fun createSyncLogger(): ProcessLogger {
        return ProcessLogger("OFFLINE_SYNC", LogManager.LogCategory.SYNC)
    }
    
    /**
     * Create custom logger
     */
    fun createCustomLogger(processName: String, category: LogManager.LogCategory): ProcessLogger {
        return ProcessLogger(processName, category)
    }
}