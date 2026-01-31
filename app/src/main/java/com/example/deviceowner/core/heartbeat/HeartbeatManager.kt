package com.example.deviceowner.core.heartbeat

import android.content.Context
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.data.models.DeviceRegistrationRequest
import com.example.deviceowner.data.models.HeartbeatRequest
import com.example.deviceowner.data.models.HeartbeatResponse
import com.example.deviceowner.data.remote.ApiClient
import com.example.deviceowner.data.repository.DeviceRegistrationRepository
import com.example.deviceowner.core.device.DeviceDataCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HeartbeatManager(private val context: Context) {
    
    private val apiClient = ApiClient()
    private val deviceDataCollector = DeviceDataCollector(context)
    private val registrationRepository = DeviceRegistrationRepository(context)
    private val controlManager = RemoteDeviceControlManager(context)
    
    companion object {
        private const val TAG = "HeartbeatManager"
    }
    
    /**
     * Send heartbeat with current device data
     * Uses same model as registration (DeviceRegistrationRequest)
     */
    suspend fun sendHeartbeat(): HeartbeatResponse? = withContext(Dispatchers.IO) {
        try {
            // Get device registration data to get device ID and loan number
            val registrationData = registrationRepository.getCompleteRegistrationData()
            if (registrationData == null) {
                Log.w(TAG, "No registration data found - cannot send heartbeat")
                return@withContext null
            }
            
            val deviceId = registrationData.deviceId
            
            Log.d(TAG, "Preparing heartbeat for device: $deviceId")
            
            // Collect current device data using same model as registration
            // NOTE: loan_number is NOT included in heartbeat (only in registration)
            val heartbeatData = deviceDataCollector.collectHeartbeatData(deviceId = deviceId)
            
            Log.d(TAG, "Sending heartbeat to server...")
            Log.d(TAG, "Heartbeat data fields:")
            Log.d(TAG, "  - Device ID: ${heartbeatData.deviceId}")
            Log.d(TAG, "  - Manufacturer: ${heartbeatData.manufacturer}")
            Log.d(TAG, "  - Model: ${heartbeatData.model}")
            Log.d(TAG, "  - Android ID: ${heartbeatData.androidId}")
            
            // Send heartbeat to server using same model as registration
            val response = apiClient.sendHeartbeat(deviceId, heartbeatData)
            
            if (response.isSuccessful) {
                val heartbeatResponse = response.body()
                Log.d(TAG, "Heartbeat sent successfully")
                
                // Process server response
                heartbeatResponse?.let { processHeartbeatResponse(it) }
                
                return@withContext heartbeatResponse
            } else {
                Log.e(TAG, "Heartbeat failed: ${response.code()} - ${response.message()}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending heartbeat: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Send heartbeat asynchronously (fire and forget)
     */
    fun sendHeartbeatAsync(onComplete: ((HeartbeatResponse?) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = sendHeartbeat()
            onComplete?.let { callback ->
                withContext(Dispatchers.Main) {
                    callback(response)
                }
            }
        }
    }
    
    /**
     * Process server response and take appropriate actions
     * Enhanced with comprehensive security validation
     */
    private suspend fun processHeartbeatResponse(response: HeartbeatResponse) {
        try {
            Log.d(TAG, "Processing heartbeat response...")
            
            // ENHANCED: Validate device data integrity first
            validateDeviceDataIntegrity(response)
            
            // ENHANCED: Check for security status changes
            validateSecurityStatusChanges(response)
            
            // CRITICAL: Check for management status and lock instructions
            response.management?.let { management ->
                val isLocked = management.isLocked == true
                val blockReason = management.blockReason ?: "Device locked by administrator"
                val currentLockState = controlManager.getLockState()
                
                Log.d(TAG, "Server management status - isLocked: $isLocked, reason: $blockReason")
                Log.d(TAG, "Current device lock state: $currentLockState")
                
                if (isLocked && !controlManager.isLocked()) {
                    // Server wants device locked, but device is currently unlocked
                    Log.w(TAG, "Server requesting device lock: $blockReason")
                    
                    // Determine lock type based on reason or severity
                    val lockType = determineLockType(blockReason, response.tamperIndicators)
                    
                    withContext(Dispatchers.Main) {
                        when (lockType) {
                            "hard" -> {
                                Log.e(TAG, "Applying HARD LOCK due to server instruction")
                                controlManager.applyHardLock(blockReason)
                            }
                            "soft" -> {
                                Log.w(TAG, "Applying SOFT LOCK due to server instruction")
                                val triggerAction = when {
                                    blockReason.contains("payment", ignoreCase = true) && blockReason.contains("overdue", ignoreCase = true) ->
                                        "PAYMENT_OVERDUE: Server detected overdue payment and restricted device access until payment is received."
                                    blockReason.contains("payment", ignoreCase = true) ->
                                        "PAYMENT_REMINDER: Server initiated payment reminder due to upcoming due date."
                                    blockReason.contains("security", ignoreCase = true) ->
                                        "SECURITY_VIOLATION: Server detected unauthorized security modifications on this device."
                                    else ->
                                        "SERVER_RESTRICTION: Device access restricted by management server: $blockReason"
                                }
                                controlManager.applySoftLock(blockReason, triggerAction)
                            }
                        }
                    }
                } else if (!isLocked && controlManager.isLocked()) {
                    // Server wants device unlocked, but device is currently locked
                    Log.i(TAG, "Server requesting device unlock")
                    
                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "Unlocking device due to server instruction")
                        controlManager.unlockDevice()
                    }
                }
            }
            
            // Process additional instructions from server
            response.instructions?.let { instructions ->
                if (instructions.isNotEmpty()) {
                    Log.d(TAG, "Received ${instructions.size} instructions from server")
                    instructions.forEach { instruction ->
                        Log.d(TAG, "Processing server instruction: $instruction")
                        processServerInstruction(instruction, response)
                    }
                }
            }
            
            // ENHANCED: Process tamper indicators with immediate action
            processTamperIndicators(response.tamperIndicators)
            
            // Log changes detected
            if (response.changesDetected == true) {
                response.changedFields?.let { fields ->
                    Log.d(TAG, "Device changes detected in fields: ${fields.joinToString(", ")}")
                    
                    // ENHANCED: Check if critical fields changed
                    validateCriticalFieldChanges(fields, response)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing heartbeat response: ${e.message}", e)
        }
    }
    
    /**
     * ENHANCED: Validate device data integrity against server expectations
     */
    private suspend fun validateDeviceDataIntegrity(response: HeartbeatResponse) {
        try {
            Log.d(TAG, "Validating device data integrity...")
            
            // Get current device data
            val registrationData = registrationRepository.getCompleteRegistrationData()
            if (registrationData == null) {
                Log.w(TAG, "No local registration data for integrity check")
                return
            }
            
            // Check for critical data mismatches
            val mismatches = mutableListOf<String>()
            
            // Validate device identity fields
            response.expectedDeviceData?.let { expected ->
                if (expected.serialNumber != null && expected.serialNumber != registrationData.serialNumber) {
                    mismatches.add("Serial Number mismatch: expected ${expected.serialNumber}, got ${registrationData.serialNumber}")
                }
                
                if (expected.deviceId != null && expected.deviceId != registrationData.deviceId) {
                    mismatches.add("Device ID mismatch: expected ${expected.deviceId}, got ${registrationData.deviceId}")
                }
                
                if (expected.loanNumber != null && expected.loanNumber != registrationData.loanNumber) {
                    mismatches.add("Loan Number mismatch: expected ${expected.loanNumber}, got ${registrationData.loanNumber}")
                }
            }
            
            // If critical mismatches found, trigger security lock
            if (mismatches.isNotEmpty()) {
                Log.e(TAG, "CRITICAL DATA MISMATCHES DETECTED:")
                mismatches.forEach { mismatch ->
                    Log.e(TAG, "  - $mismatch")
                }
                
                withContext(Dispatchers.Main) {
                    val triggerAction = "DATA_MISMATCH: Critical device data mismatches detected. Device identity verification failed."
                    val reason = "Device data integrity violation: ${mismatches.joinToString("; ")}"
                    controlManager.applyHardLock(reason)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating device data integrity: ${e.message}", e)
        }
    }
    
    /**
     * ENHANCED: Validate security status changes
     */
    private suspend fun validateSecurityStatusChanges(response: HeartbeatResponse) {
        try {
            Log.d(TAG, "Validating security status changes...")
            
            response.securityStatus?.let { securityStatus ->
                val violations = mutableListOf<String>()
                
                // Check for security violations
                if (securityStatus.isRooted == true) {
                    violations.add("Device rooting detected")
                }
                
                if (securityStatus.hasCustomRom == true) {
                    violations.add("Custom ROM detected")
                }
                
                if (securityStatus.isDebuggingEnabled == true) {
                    violations.add("USB debugging enabled")
                }
                
                if (securityStatus.isDeveloperModeEnabled == true) {
                    violations.add("Developer options enabled")
                }
                
                if (securityStatus.hasUnknownSources == true) {
                    violations.add("Unknown sources installation enabled")
                }
                
                if (securityStatus.bootloaderUnlocked == true) {
                    violations.add("Bootloader unlocked")
                }
                
                // Check security score threshold
                val securityScore = securityStatus.securityScore ?: 100
                if (securityScore < 70) {
                    violations.add("Security score too low: $securityScore")
                }
                
                // If violations found, take action
                if (violations.isNotEmpty()) {
                    Log.w(TAG, "SECURITY STATUS VIOLATIONS DETECTED:")
                    violations.forEach { violation ->
                        Log.w(TAG, "  - $violation")
                    }
                    
                    withContext(Dispatchers.Main) {
                        val triggerAction = when {
                            violations.any { it.contains("rooting") } -> "ROOT_DETECTION"
                            violations.any { it.contains("Custom ROM") } -> "CUSTOM_ROM_DETECTION"
                            violations.any { it.contains("debugging") } -> "USB_DEBUG_ATTEMPT"
                            violations.any { it.contains("developer") } -> "DEVELOPER_MODE_ATTEMPT"
                            else -> "SECURITY_VIOLATION"
                        }
                        
                        val reason = "Security status violations: ${violations.joinToString("; ")}"
                        
                        // Critical violations trigger hard lock
                        if (violations.any { it.contains("rooting") || it.contains("Custom ROM") || it.contains("bootloader") }) {
                            controlManager.applyHardLock(reason)
                        } else {
                            controlManager.applySoftLock(reason, triggerAction)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating security status: ${e.message}", e)
        }
    }
    
    /**
     * ENHANCED: Process tamper indicators with immediate protective action
     */
    private suspend fun processTamperIndicators(tamperIndicators: List<String>?) {
        tamperIndicators?.let { indicators ->
            if (indicators.isNotEmpty()) {
                Log.w(TAG, "Tamper indicators detected: ${indicators.joinToString(", ")}")
                
                // Categorize tamper indicators by severity
                val criticalTamper = indicators.filter { 
                    it.contains("rooted", ignoreCase = true) || 
                    it.contains("custom", ignoreCase = true) ||
                    it.contains("bootloader", ignoreCase = true) ||
                    it.contains("system", ignoreCase = true) ||
                    it.contains("firmware", ignoreCase = true)
                }
                
                val moderateTamper = indicators.filter {
                    it.contains("debug", ignoreCase = true) ||
                    it.contains("developer", ignoreCase = true) ||
                    it.contains("unknown", ignoreCase = true)
                }
                
                if (criticalTamper.isNotEmpty() && !controlManager.isLocked()) {
                    Log.e(TAG, "CRITICAL TAMPER DETECTED - applying immediate hard lock")
                    withContext(Dispatchers.Main) {
                        val triggerAction = when {
                            criticalTamper.any { it.contains("rooted", ignoreCase = true) } ->
                                "ROOT_DETECTION: Device rooting has been detected. Rooted devices are strictly prohibited."
                            criticalTamper.any { it.contains("custom", ignoreCase = true) } ->
                                "CUSTOM_ROM_DETECTION: Custom ROM installation detected. Please restore original firmware."
                            criticalTamper.any { it.contains("bootloader", ignoreCase = true) } ->
                                "SECURITY_VIOLATION: Bootloader modification detected. This violates security policies."
                            else ->
                                "SECURITY_VIOLATION: Critical security violations detected: ${criticalTamper.joinToString(", ")}"
                        }
                        controlManager.applyHardLock("Critical tamper detected: ${criticalTamper.joinToString(", ")}")
                    }
                } else if (moderateTamper.isNotEmpty() && !controlManager.isLocked()) {
                    Log.w(TAG, "MODERATE TAMPER DETECTED - applying soft lock")
                    withContext(Dispatchers.Main) {
                        val triggerAction = when {
                            moderateTamper.any { it.contains("debug", ignoreCase = true) } -> "USB_DEBUG_ATTEMPT"
                            moderateTamper.any { it.contains("developer", ignoreCase = true) } -> "DEVELOPER_MODE_ATTEMPT"
                            else -> "SECURITY_VIOLATION"
                        }
                        val reason = "Security violations detected: ${moderateTamper.joinToString(", ")}"
                        controlManager.applySoftLock(reason, triggerAction)
                    }
                }
            }
        }
    }
    
    /**
     * ENHANCED: Validate critical field changes
     */
    private suspend fun validateCriticalFieldChanges(changedFields: List<String>, response: HeartbeatResponse) {
        try {
            val criticalFields = listOf(
                "serialNumber", "deviceId", "loanNumber", "imei", "androidId",
                "manufacturer", "model", "buildNumber", "securityPatch"
            )
            
            val criticalChanges = changedFields.filter { field ->
                criticalFields.any { critical -> field.contains(critical, ignoreCase = true) }
            }
            
            if (criticalChanges.isNotEmpty()) {
                Log.e(TAG, "CRITICAL FIELD CHANGES DETECTED: ${criticalChanges.joinToString(", ")}")
                
                withContext(Dispatchers.Main) {
                    val triggerAction = "DATA_MISMATCH: Critical device fields have changed unexpectedly. This may indicate device tampering or replacement."
                    val reason = "Critical device data changes detected: ${criticalChanges.joinToString(", ")}"
                    controlManager.applyHardLock(reason)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating critical field changes: ${e.message}", e)
        }
    }
    
    /**
     * Determine lock type based on reason and tamper indicators
     */
    private fun determineLockType(reason: String, tamperIndicators: List<String>?): String {
        val reasonLower = reason.lowercase()
        
        // Hard lock conditions
        val hardLockKeywords = listOf(
            "security", "tamper", "violation", "breach", "unauthorized", 
            "rooted", "custom", "bootloader", "critical", "fraud"
        )
        
        // Soft lock conditions  
        val softLockKeywords = listOf(
            "payment", "overdue", "reminder", "warning", "notice"
        )
        
        // Check for critical tamper indicators
        val hasCriticalTamper = tamperIndicators?.any { 
            it.contains("rooted", ignoreCase = true) || 
            it.contains("custom", ignoreCase = true) ||
            it.contains("bootloader", ignoreCase = true)
        } == true
        
        return when {
            hasCriticalTamper -> "hard"
            hardLockKeywords.any { reasonLower.contains(it) } -> "hard"
            softLockKeywords.any { reasonLower.contains(it) } -> "soft"
            else -> "soft" // Default to soft lock
        }
    }
    
    /**
     * Process individual server instructions
     */
    private suspend fun processServerInstruction(instruction: String, response: HeartbeatResponse) {
        when (instruction.lowercase()) {
            "lock_device", "hard_lock" -> {
                Log.w(TAG, "Hard lock instruction received")
                val reason = response.management?.blockReason ?: "Device locked by administrator"
                withContext(Dispatchers.Main) {
                    controlManager.applyHardLock(reason)
                }
            }
            "soft_lock", "payment_reminder" -> {
                Log.w(TAG, "Soft lock instruction received")
                val reason = response.management?.blockReason ?: "Payment reminder"
                val triggerAction = when {
                    reason.contains("overdue", ignoreCase = true) -> 
                        "PAYMENT_OVERDUE: Your payment is overdue and device access has been limited until payment is received."
                    reason.contains("reminder", ignoreCase = true) -> 
                        "PAYMENT_REMINDER: This is a reminder that your payment is due soon. Please make payment to avoid restrictions."
                    else -> 
                        "SERVER_RESTRICTION: Device access has been limited by the management server due to account status."
                }
                withContext(Dispatchers.Main) {
                    controlManager.applySoftLock(reason, triggerAction)
                }
            }
            "unlock_device", "unlock" -> {
                Log.i(TAG, "Unlock instruction received")
                withContext(Dispatchers.Main) {
                    controlManager.unlockDevice()
                }
            }
            "wipe_device", "factory_reset" -> {
                Log.e(TAG, "Device wipe instruction received")
                // TODO: Implement device wiping functionality
                // This should be implemented with extreme caution
            }
            "update_policy" -> {
                Log.d(TAG, "Policy update instruction received")
                // TODO: Implement policy updates
            }
            "set_bios_password" -> {
                Log.d(TAG, "BIOS password instruction received (not applicable for Android)")
            }
            else -> {
                Log.d(TAG, "Unknown instruction received: $instruction")
            }
        }
    }
    
    /**
     * Check if device is registered and can send heartbeats
     */
    suspend fun canSendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        try {
            registrationRepository.isDeviceRegistered()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking registration status: ${e.message}")
            false
        }
    }
}