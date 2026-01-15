package com.example.deviceowner.managers

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.local.LocalDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prevents deletion of critical registration and heartbeat data
 * Intercepts and blocks any deletion attempts on protected data
 * Logs all deletion attempts for audit purposes
 */
class DataDeletionPreventionManager(private val context: Context) {
    
    private val localDataManager = LocalDataManager(context)
    private val auditLog = IdentifierAuditLog(context)
    private val TAG = "DataDeletionPreventionManager"
    
    /**
     * Attempt to delete registration - BLOCKED if protected
     * Returns false if deletion is prevented
     */
    suspend fun attemptDeleteRegistration(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = localDataManager.getRegistrationByDeviceId(deviceId)
            
            if (registration == null) {
                Log.w(TAG, "Registration not found: $deviceId")
                return@withContext false
            }
            
            if (registration.isProtected) {
                Log.e(TAG, "✗ DELETION BLOCKED: Protected registration cannot be deleted: $deviceId")
                
                // Log deletion attempt
                auditLog.logIncident(
                    type = "DELETION_ATTEMPT_BLOCKED",
                    severity = "HIGH",
                    details = "Attempt to delete protected registration: $deviceId"
                )
                
                return@withContext false
            }
            
            // Only delete if not protected
            localDataManager.deleteRegistration(deviceId)
            Log.d(TAG, "✓ Unprotected registration deleted: $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error attempting to delete registration: ${e.message}", e)
            false
        }
    }
    
    /**
     * Attempt to clear all registrations - BLOCKED if any protected exist
     * Returns false if deletion is prevented
     */
    suspend fun attemptClearAllRegistrations(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val hasProtected = localDataManager.hasProtectedRegistration()
            
            if (hasProtected) {
                Log.e(TAG, "✗ DELETION BLOCKED: Cannot clear all registrations - protected data exists")
                
                // Log deletion attempt
                auditLog.logIncident(
                    type = "BULK_DELETION_ATTEMPT_BLOCKED",
                    severity = "CRITICAL",
                    details = "Attempt to clear all registrations when protected data exists"
                )
                
                return@withContext false
            }
            
            // Only clear if no protected data
            localDataManager.clearAllRegistrations()
            Log.d(TAG, "✓ All unprotected registrations cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error attempting to clear registrations: ${e.message}", e)
            false
        }
    }
    
    /**
     * Verify registration data integrity
     * Checks if protected registration still exists and is valid
     */
    suspend fun verifyDataIntegrity(): DataIntegrityStatus = withContext(Dispatchers.IO) {
        return@withContext try {
            val registration = localDataManager.getLatestRegistration()
            
            if (registration == null) {
                Log.e(TAG, "✗ INTEGRITY CHECK FAILED: No registration data found")
                return@withContext DataIntegrityStatus(
                    isValid = false,
                    reason = "No registration data found",
                    severity = "CRITICAL"
                )
            }
            
            if (!registration.isActive) {
                Log.e(TAG, "✗ INTEGRITY CHECK FAILED: Registration is inactive")
                return@withContext DataIntegrityStatus(
                    isValid = false,
                    reason = "Registration is inactive",
                    severity = "HIGH"
                )
            }
            
            if (!registration.isProtected) {
                Log.e(TAG, "✗ INTEGRITY CHECK FAILED: Registration is not protected")
                return@withContext DataIntegrityStatus(
                    isValid = false,
                    reason = "Registration is not protected",
                    severity = "CRITICAL"
                )
            }
            
            Log.d(TAG, "✓ Data integrity verified: ${registration.device_id}")
            DataIntegrityStatus(
                isValid = true,
                reason = "Registration data is valid and protected",
                severity = "NONE"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying data integrity: ${e.message}", e)
            DataIntegrityStatus(
                isValid = false,
                reason = "Error during integrity check: ${e.message}",
                severity = "CRITICAL"
            )
        }
    }
    
    /**
     * Log data protection status
     */
    suspend fun logProtectionStatus() = withContext(Dispatchers.IO) {
        try {
            val registration = localDataManager.getLatestRegistration()
            val hasProtected = localDataManager.hasProtectedRegistration()
            
            val status = """
                Data Protection Status:
                - Protected Registration Exists: $hasProtected
                - Device ID: ${registration?.device_id ?: "NONE"}
                - Is Protected: ${registration?.isProtected ?: false}
                - Is Active: ${registration?.isActive ?: false}
                - Registration Date: ${registration?.registrationDate ?: "N/A"}
            """.trimIndent()
            
            Log.d(TAG, status)
            
            auditLog.logIncident(
                type = "PROTECTION_STATUS_CHECK",
                severity = "INFO",
                details = status
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error logging protection status: ${e.message}", e)
        }
    }
}

/**
 * Data class for data integrity check results
 */
data class DataIntegrityStatus(
    val isValid: Boolean,
    val reason: String,
    val severity: String // NONE, LOW, MEDIUM, HIGH, CRITICAL
)
